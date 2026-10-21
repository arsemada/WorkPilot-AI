package com.workpilot.ai.web;

import com.workpilot.ai.dto.DocumentPageResponse;
import com.workpilot.ai.dto.DocumentResponse;
import com.workpilot.ai.dto.UpdateDocumentRequest;
import com.workpilot.ai.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a document into an organization")
    public DocumentResponse create(
            @PathVariable UUID organizationId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file) {
        return documentService.create(organizationId, title, description, file);
    }

    @GetMapping
    @Operation(summary = "List documents in an organization")
    public DocumentPageResponse list(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return documentService.list(organizationId, q, page, size, sort, direction);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get document metadata")
    public DocumentResponse get(@PathVariable UUID organizationId, @PathVariable UUID documentId) {
        return documentService.get(organizationId, documentId);
    }

    @GetMapping("/{documentId}/content")
    @Operation(summary = "Download a document file")
    public ResponseEntity<Resource> download(@PathVariable UUID organizationId, @PathVariable UUID documentId) {
        return documentService.download(organizationId, documentId);
    }

    @PatchMapping("/{documentId}")
    @Operation(summary = "Update document title or description")
    public DocumentResponse update(
            @PathVariable UUID organizationId,
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request) {
        return documentService.update(organizationId, documentId, request);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document and its stored file")
    public void delete(@PathVariable UUID organizationId, @PathVariable UUID documentId) {
        documentService.delete(organizationId, documentId);
    }
}
