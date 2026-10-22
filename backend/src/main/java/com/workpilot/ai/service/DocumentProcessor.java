package com.workpilot.ai.service;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.IndexingStatus;
import com.workpilot.ai.domain.ProcessingStatus;
import com.workpilot.ai.domain.WorkflowEventType;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);
    private static final String EXTRACTION_FAILED = "Text extraction failed";
    private static final String FILE_MISSING = "The stored file is missing";

    private final TaskExecutor executor;
    private final TransactionTemplate transactionTemplate;
    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DocumentTextExtractor textExtractor;
    private final DocumentIndexer documentIndexer;
    private final WorkflowExecutionService executionService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentProcessor(
            @Qualifier("documentProcessingExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager,
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DocumentTextExtractor textExtractor,
            DocumentIndexer documentIndexer,
            WorkflowExecutionService executionService,
            ApplicationEventPublisher eventPublisher) {
        this.executor = executor;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.textExtractor = textExtractor;
        this.documentIndexer = documentIndexer;
        this.executionService = executionService;
        this.eventPublisher = eventPublisher;
    }

    public void enqueue(UUID organizationId, UUID documentId) {
        enqueue(organizationId, documentId, null);
    }

    public void enqueue(UUID organizationId, UUID documentId, UUID executionId) {
        executor.execute(() -> {
            DocumentRunResult result;
            UUID uploaderId = null;
            try {
                Held held = transactionTemplate.execute(status -> process(organizationId, documentId));
                result = held == null
                        ? new DocumentRunResult(DocumentRunResult.Kind.FAILED, EXTRACTION_FAILED, false)
                        : held.result();
                uploaderId = held == null ? null : held.uploaderId();
            } catch (RuntimeException exception) {
                log.warn("Document processing stopped for {}", documentId);
                markFailed(organizationId, documentId, EXTRACTION_FAILED);
                result = new DocumentRunResult(DocumentRunResult.Kind.FAILED, EXTRACTION_FAILED, false);
            }
            if (result.summarize()) {
                try {
                    documentIndexer.enqueue(organizationId, documentId);
                } catch (RuntimeException exception) {
                    log.warn("Document indexing could not be queued for {}", documentId);
                }
            }
            executionService.finishDocument(executionId, result);
            if (result.summarize()) {
                eventPublisher.publishEvent(new WorkflowTrigger(
                        organizationId,
                        WorkflowEventType.DOCUMENT_PROCESSED,
                        uploaderId,
                        null,
                        null,
                        documentId,
                        null,
                        1));
            }
        });
    }

    private Held process(UUID organizationId, UUID documentId) {
        Document document = documentRepository.findByIdAndOrganizationId(documentId, organizationId).orElse(null);
        if (document == null) {
            return new Held(null, new DocumentRunResult(DocumentRunResult.Kind.FAILED, "The document is missing.", false));
        }
        if (document.getProcessingStatus() != ProcessingStatus.PENDING) {
            if (document.getProcessingStatus() == ProcessingStatus.COMPLETED) {
                return new Held(uploaderId(document), new DocumentRunResult(
                        DocumentRunResult.Kind.SKIPPED, "Document processing had already finished.", false));
            }
            return new Held(uploaderId(document), new DocumentRunResult(
                    DocumentRunResult.Kind.FAILED, "Document processing did not start.", false));
        }
        document.setProcessingStatus(ProcessingStatus.PROCESSING);
        document.setProcessingError(null);
        documentRepository.saveAndFlush(document);
        try {
            byte[] bytes = documentStorage.readBytes(document.getStorageKey());
            document.setExtractedText(textExtractor.extract(document.getContentType(), bytes));
            document.setProcessingStatus(ProcessingStatus.COMPLETED);
            document.setProcessingError(null);
            if (document.getExtractedText() == null || document.getExtractedText().isBlank()) {
                document.setIndexingStatus(IndexingStatus.SKIPPED);
            } else {
                document.setIndexingStatus(IndexingStatus.PENDING);
            }
        } catch (ResourceNotFoundException exception) {
            document.setExtractedText(null);
            document.setProcessingStatus(ProcessingStatus.FAILED);
            document.setProcessingError(FILE_MISSING);
        } catch (RuntimeException exception) {
            log.warn("Text extraction failed for {}", documentId);
            document.setExtractedText(null);
            document.setProcessingStatus(ProcessingStatus.FAILED);
            document.setProcessingError(EXTRACTION_FAILED);
        }
        if (documentRepository.findByIdAndOrganizationId(documentId, organizationId).isEmpty()) {
            return new Held(null, new DocumentRunResult(DocumentRunResult.Kind.FAILED, "The document is missing.", false));
        }
        documentRepository.save(document);
        boolean extracted = document.getProcessingStatus() == ProcessingStatus.COMPLETED
                && document.getExtractedText() != null
                && !document.getExtractedText().isBlank();
        if (document.getProcessingStatus() == ProcessingStatus.FAILED) {
            String message = document.getProcessingError() == null ? EXTRACTION_FAILED : document.getProcessingError();
            return new Held(uploaderId(document), new DocumentRunResult(DocumentRunResult.Kind.FAILED, message, false));
        }
        return new Held(uploaderId(document), new DocumentRunResult(
                DocumentRunResult.Kind.SUCCEEDED, "Document processing finished.", extracted));
    }

    private static UUID uploaderId(Document document) {
        return document.getUploadedBy() == null ? null : document.getUploadedBy().getId();
    }

    private record Held(UUID uploaderId, DocumentRunResult result) {
    }

    private void markFailed(UUID organizationId, UUID documentId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            Document document = documentRepository.findByIdAndOrganizationId(documentId, organizationId).orElse(null);
            if (document == null) {
                return;
            }
            document.setExtractedText(null);
            document.setProcessingStatus(ProcessingStatus.FAILED);
            document.setProcessingError(message);
            documentRepository.save(document);
        });
    }
}
