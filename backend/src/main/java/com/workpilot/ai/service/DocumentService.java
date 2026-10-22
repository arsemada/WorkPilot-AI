package com.workpilot.ai.service;

import com.workpilot.ai.config.DocumentStorageProperties;
import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.ProcessingStatus;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.dto.DocumentPageResponse;
import com.workpilot.ai.dto.DocumentResponse;
import com.workpilot.ai.dto.DocumentStatusResponse;
import com.workpilot.ai.dto.UpdateDocumentRequest;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.DocumentRepository;
import com.workpilot.ai.repository.DocumentSpecifications;
import com.workpilot.ai.security.OrganizationAccessService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 200;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_MAGIC = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ZIP_MAGIC = new byte[] {0x50, 0x4B, 0x03, 0x04};
    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "title", "originalFileName", "sizeBytes");
    private static final Map<String, String> EXTENSION_TYPES = Map.of(
            "pdf", "application/pdf",
            "txt", "text/plain",
            "csv", "text/csv",
            "md", "text/markdown",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Set<String> TEXT_TYPES = Set.of("text/plain", "text/csv", "text/markdown");

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DocumentStorageProperties storageProperties;
    private final OrganizationAccessService accessService;
    private final DocumentProcessor documentProcessor;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DocumentStorageProperties storageProperties,
            OrganizationAccessService accessService,
            DocumentProcessor documentProcessor) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.storageProperties = storageProperties;
        this.accessService = accessService;
        this.documentProcessor = documentProcessor;
    }

    @Transactional
    public DocumentResponse create(UUID organizationId, String title, String description, MultipartFile file) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_CREATE);
        String cleanTitle = requireTitle(title);
        String cleanDescription = cleanDescription(description);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file");
        }
        String fileName = fileName(file.getOriginalFilename());
        String contentType = contentTypeFor(fileName);
        byte[] bytes = readBytes(file);
        if (bytes.length == 0) {
            throw new BadRequestException("Choose a file");
        }
        if (bytes.length > storageProperties.getMaxBytes()) {
            throw new BadRequestException("File must be 10 MB or smaller");
        }
        assertContentMatches(contentType, bytes);

        UUID documentId = UUID.randomUUID();
        String storageKey = DocumentStorage.storageKey(organizationId, documentId);
        documentStorage.write(storageKey, bytes);
        Document document = new Document();
        document.setId(documentId);
        document.setOrganization(membership.getOrganization());
        document.setUploadedBy(membership.getUser());
        document.setTitle(cleanTitle);
        document.setDescription(cleanDescription);
        document.setOriginalFileName(fileName);
        document.setContentType(contentType);
        document.setSizeBytes(bytes.length);
        document.setStorageKey(storageKey);
        document.setProcessingStatus(ProcessingStatus.PENDING);
        try {
            Document saved = documentRepository.save(document);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentProcessor.enqueue(organizationId, documentId);
                }
            });
            return DocumentResponse.fromEntity(saved);
        } catch (RuntimeException exception) {
            documentStorage.delete(storageKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse list(
            UUID organizationId,
            String query,
            int page,
            int size,
            String sort,
            String direction) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        String search = query == null ? null : query.trim();
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text must be at most 200 characters");
        }
        if (!SORT_FIELDS.contains(sort)) {
            throw new BadRequestException("Unsupported sort field");
        }
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new BadRequestException("Sort direction must be asc or desc");
        }

        Specification<Document> specification = DocumentSpecifications.inOrganization(organizationId);
        if (search != null && !search.isEmpty()) {
            specification = specification.and(DocumentSpecifications.textSearch(search));
        }
        Page<Document> result = documentRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(new Sort.Order(sortDirection, sort))));
        List<DocumentResponse> items = result.getContent().stream().map(DocumentResponse::fromEntity).toList();
        return new DocumentPageResponse(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID organizationId, UUID documentId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        return DocumentResponse.fromEntity(requireDocument(organizationId, documentId));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(UUID organizationId, UUID documentId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        Document document = requireDocument(organizationId, documentId);
        Resource resource = documentStorage.read(document.getStorageKey());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(document.getSizeBytes())
                .body(resource);
    }

    @Transactional
    public DocumentResponse update(UUID organizationId, UUID documentId, UpdateDocumentRequest request) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_UPDATE);
        Document document = requireDocument(organizationId, documentId);
        if (request.isTitlePresent()) {
            document.setTitle(requireTitle(request.getTitle()));
        }
        if (request.isDescriptionPresent()) {
            document.setDescription(cleanDescription(request.getDescription()));
        }
        return DocumentResponse.fromEntity(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse status(UUID organizationId, UUID documentId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        return DocumentStatusResponse.fromEntity(requireDocument(organizationId, documentId));
    }

    @Transactional
    public DocumentResponse retry(UUID organizationId, UUID documentId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_UPDATE);
        Document document = requireDocument(organizationId, documentId);
        if (document.getProcessingStatus() != ProcessingStatus.FAILED) {
            throw new BadRequestException("Only a failed document can be retried");
        }
        document.setProcessingStatus(ProcessingStatus.PENDING);
        document.setProcessingError(null);
        document.setExtractedText(null);
        Document saved = documentRepository.save(document);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentProcessor.enqueue(organizationId, documentId);
            }
        });
        return DocumentResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(UUID organizationId, UUID documentId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_DELETE);
        Document document = requireDocument(organizationId, documentId);
        String storageKey = document.getStorageKey();
        documentRepository.delete(document);
        documentStorage.delete(storageKey);
    }

    private Document requireDocument(UUID organizationId, UUID documentId) {
        return documentRepository.findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("Title must be at most 200 characters");
        }
        return trimmed;
    }

    private static String cleanDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException("Description must be at most 5000 characters");
        }
        return trimmed;
    }

    private static String fileName(String original) {
        if (original == null || original.isBlank()) {
            throw new BadRequestException("The file needs a name");
        }
        String name = original.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("\r", "").replace("\n", "").replace("\"", "");
        if (name.isBlank() || name.contains("..")) {
            throw new BadRequestException("The file needs a name");
        }
        for (int index = 0; index < name.length(); index++) {
            if (name.charAt(index) < 32) {
                throw new BadRequestException("The file needs a name");
            }
        }
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name;
    }

    private static void assertContentMatches(String contentType, byte[] bytes) {
        boolean matches = switch (contentType) {
            case "application/pdf" -> startsWith(bytes, PDF_MAGIC);
            case "image/png" -> startsWith(bytes, PNG_MAGIC);
            case "image/jpeg" -> startsWith(bytes, JPEG_MAGIC);
            case "image/webp" -> isWebp(bytes);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> startsWith(bytes, ZIP_MAGIC);
            default -> TEXT_TYPES.contains(contentType) && isPlainText(bytes);
        };
        if (!matches) {
            throw new BadRequestException("This file does not match its extension");
        }
    }

    private static boolean isPlainText(byte[] bytes) {
        if (startsWith(bytes, PDF_MAGIC) || startsWith(bytes, PNG_MAGIC) || startsWith(bytes, JPEG_MAGIC)
                || startsWith(bytes, ZIP_MAGIC) || isWebp(bytes)) {
            return false;
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.indexOf('\0') < 0;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String contentTypeFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BadRequestException("This file type is not supported");
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = EXTENSION_TYPES.get(extension);
        if (contentType == null) {
            throw new BadRequestException("This file type is not supported");
        }
        return contentType;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("The file could not be read", exception);
        }
    }
}
