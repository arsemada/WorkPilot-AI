package com.workpilot.ai.service;

import com.workpilot.ai.config.AiProperties;
import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.DocumentChunk;
import com.workpilot.ai.domain.IndexingStatus;
import com.workpilot.ai.domain.ProcessingStatus;
import com.workpilot.ai.exception.AiUnavailableException;
import com.workpilot.ai.repository.DocumentChunkRepository;
import com.workpilot.ai.repository.DocumentRepository;
import com.workpilot.ai.service.ai.EmbeddingClient;
import com.workpilot.ai.service.ai.OpenAiEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);
    private static final int BATCH_SIZE = 16;
    private static final String FAILED = "Embedding failed.";
    private static final String DIMENSIONS = "The embedding dimensions do not match the vector index.";

    private final TaskExecutor executor;
    private final TransactionTemplate transactionTemplate;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final DocumentChunker chunker;
    private final AiProperties aiProperties;
    private final JdbcTemplate jdbcTemplate;

    public DocumentIndexer(
            @Qualifier("documentProcessingExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager,
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            EmbeddingClient embeddingClient,
            AiProperties aiProperties,
            JdbcTemplate jdbcTemplate) {
        this.executor = executor;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.chunker = new DocumentChunker();
        this.aiProperties = aiProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enqueue(UUID organizationId, UUID documentId) {
        executor.execute(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> index(organizationId, documentId));
            } catch (RuntimeException exception) {
                log.warn("Document indexing stopped for {}", documentId);
                markFailed(organizationId, documentId, safeMessage(exception));
            }
        });
    }

    private void index(UUID organizationId, UUID documentId) {
        Document document = documentRepository.findByIdAndOrganizationId(documentId, organizationId).orElse(null);
        if (document == null || document.getProcessingStatus() != ProcessingStatus.COMPLETED) {
            return;
        }
        String text = document.getExtractedText();
        if (text == null || text.isBlank()) {
            chunkRepository.deleteByDocumentId(documentId);
            document.setIndexingStatus(IndexingStatus.SKIPPED);
            document.setIndexingError(null);
            document.setIndexedModel(null);
            document.setIndexedModelVersion(null);
            document.setIndexedDimensions(null);
            documentRepository.save(document);
            return;
        }
        if (usesPgVector() && aiProperties.getEmbeddingDimensions() != OpenAiEmbeddingClient.INDEX_DIMENSIONS) {
            throw new AiUnavailableException(DIMENSIONS);
        }
        document.setIndexingStatus(IndexingStatus.PROCESSING);
        document.setIndexingError(null);
        documentRepository.saveAndFlush(document);

        List<DocumentChunker.TextChunk> pieces = chunker.split(text);
        chunkRepository.deleteByDocumentId(documentId);
        chunkRepository.flush();
        if (pieces.isEmpty()) {
            document.setIndexingStatus(IndexingStatus.SKIPPED);
            documentRepository.save(document);
            return;
        }

        List<float[]> vectors = embed(pieces);
        if (vectors.size() != pieces.size()) {
            throw new AiUnavailableException(FAILED);
        }
        List<DocumentChunk> stored = new ArrayList<>();
        Instant createdAt = Instant.now();
        for (int index = 0; index < pieces.size(); index++) {
            float[] vector = vectors.get(index);
            if (vector.length != aiProperties.getEmbeddingDimensions()) {
                throw new AiUnavailableException(DIMENSIONS);
            }
            DocumentChunker.TextChunk piece = pieces.get(index);
            String json = VectorMath.toJson(vector);
            if (json.length() > 40000) {
                throw new AiUnavailableException(FAILED);
            }
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(UUID.randomUUID());
            chunk.setDocument(document);
            chunk.setOrganizationId(document.scopedOrganizationId());
            chunk.setChunkIndex(piece.index());
            chunk.setContent(limit(piece.content(), 2000));
            chunk.setPageNumber(piece.pageNumber());
            chunk.setSectionLabel(piece.sectionLabel());
            chunk.setEmbeddingModel(aiProperties.getEmbeddingModel());
            chunk.setEmbeddingModelVersion(aiProperties.getEmbeddingModelVersion());
            chunk.setEmbeddingDimensions(vector.length);
            chunk.setEmbeddingJson(json);
            chunk.setCreatedAt(createdAt);
            stored.add(chunk);
        }
        chunkRepository.saveAll(stored);
        chunkRepository.flush();
        writeVectors(stored, vectors);

        document.setIndexingStatus(IndexingStatus.COMPLETED);
        document.setIndexingError(null);
        document.setIndexedModel(aiProperties.getEmbeddingModel());
        document.setIndexedModelVersion(aiProperties.getEmbeddingModelVersion());
        document.setIndexedDimensions(aiProperties.getEmbeddingDimensions());
        documentRepository.save(document);
    }

    private List<float[]> embed(List<DocumentChunker.TextChunk> pieces) {
        List<float[]> vectors = new ArrayList<>();
        for (int start = 0; start < pieces.size(); start += BATCH_SIZE) {
            int end = Math.min(pieces.size(), start + BATCH_SIZE);
            List<String> inputs = new ArrayList<>();
            for (int index = start; index < end; index++) {
                inputs.add(pieces.get(index).content());
            }
            vectors.addAll(embeddingClient.embed(inputs));
        }
        return vectors;
    }

    private void writeVectors(List<DocumentChunk> chunks, List<float[]> vectors) {
        if (!usesPgVector()) {
            return;
        }
        for (int index = 0; index < chunks.size(); index++) {
            jdbcTemplate.update(
                    "UPDATE document_chunks SET embedding = cast(? as vector) WHERE id = ?",
                    VectorMath.toJson(vectors.get(index)),
                    chunks.get(index).getId());
        }
    }

    private boolean usesPgVector() {
        return "pgvector".equalsIgnoreCase(aiProperties.getVectorStore());
    }

    private void markFailed(UUID organizationId, UUID documentId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            Document document = documentRepository.findByIdAndOrganizationId(documentId, organizationId).orElse(null);
            if (document == null) {
                return;
            }
            chunkRepository.deleteByDocumentId(documentId);
            document.setIndexingStatus(IndexingStatus.FAILED);
            document.setIndexingError(limit(message, 300));
            document.setIndexedModel(null);
            document.setIndexedModelVersion(null);
            document.setIndexedDimensions(null);
            documentRepository.save(document);
        });
    }

    private static String safeMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AiUnavailableException unavailable && unavailable.getMessage() != null) {
                return unavailable.getMessage();
            }
            current = current.getCause();
        }
        return FAILED;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
