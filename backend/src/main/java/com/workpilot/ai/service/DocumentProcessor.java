package com.workpilot.ai.service;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.IndexingStatus;
import com.workpilot.ai.domain.ProcessingStatus;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public DocumentProcessor(
            @Qualifier("documentProcessingExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager,
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DocumentTextExtractor textExtractor,
            DocumentIndexer documentIndexer) {
        this.executor = executor;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.textExtractor = textExtractor;
        this.documentIndexer = documentIndexer;
    }

    public void enqueue(UUID organizationId, UUID documentId) {
        executor.execute(() -> {
            boolean index = false;
            try {
                Boolean ready = transactionTemplate.execute(status -> process(organizationId, documentId));
                index = Boolean.TRUE.equals(ready);
            } catch (RuntimeException exception) {
                log.warn("Document processing stopped for {}", documentId);
                markFailed(organizationId, documentId, EXTRACTION_FAILED);
            }
            if (index) {
                try {
                    documentIndexer.enqueue(organizationId, documentId);
                } catch (RuntimeException exception) {
                    log.warn("Document indexing could not be queued for {}", documentId);
                }
            }
        });
    }

    private boolean process(UUID organizationId, UUID documentId) {
        Document document = documentRepository.findByIdAndOrganizationId(documentId, organizationId).orElse(null);
        if (document == null || document.getProcessingStatus() != ProcessingStatus.PENDING) {
            return false;
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
            return false;
        }
        documentRepository.save(document);
        return document.getProcessingStatus() == ProcessingStatus.COMPLETED
                && document.getExtractedText() != null
                && !document.getExtractedText().isBlank();
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
