package com.workpilot.ai.service;

import com.workpilot.ai.config.AiProperties;
import com.workpilot.ai.domain.DocumentChunk;
import com.workpilot.ai.repository.DocumentChunkRepository;
import com.workpilot.ai.service.ai.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentRetrieval {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetrieval.class);

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final AiProperties aiProperties;
    private final JdbcTemplate jdbcTemplate;

    public DocumentRetrieval(
            DocumentChunkRepository chunkRepository,
            EmbeddingClient embeddingClient,
            AiProperties aiProperties,
            JdbcTemplate jdbcTemplate) {
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.aiProperties = aiProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetrievedPassage> retrieve(UUID organizationId, String question) {
        try {
            return search(organizationId, question);
        } catch (RuntimeException exception) {
            log.warn("Document retrieval was skipped");
            return List.of();
        }
    }

    private List<RetrievedPassage> search(UUID organizationId, String question) {
        String model = aiProperties.getEmbeddingModel();
        String version = aiProperties.getEmbeddingModelVersion();
        long indexed = chunkRepository.countByOrganizationIdAndEmbeddingModelAndEmbeddingModelVersion(
                organizationId, model, version);
        if (indexed == 0) {
            return List.of();
        }
        List<float[]> embedded = embeddingClient.embed(List.of(question));
        if (embedded.isEmpty() || embedded.get(0).length != aiProperties.getEmbeddingDimensions()) {
            return List.of();
        }
        float[] query = embedded.get(0);
        List<RetrievedPassage> matches = usesPgVector()
                ? searchPgVector(organizationId, model, version, query)
                : searchJson(organizationId, model, version, query);
        return matches.stream()
                .filter(passage -> passage.distance() <= aiProperties.getMaxCosineDistance())
                .sorted(Comparator.comparingDouble(RetrievedPassage::distance).thenComparingInt(RetrievedPassage::chunkIndex))
                .limit(Math.max(1, aiProperties.getRetrievalLimit()))
                .toList();
    }

    private List<RetrievedPassage> searchJson(UUID organizationId, String model, String version, float[] query) {
        List<RetrievedPassage> matches = new ArrayList<>();
        for (DocumentChunk chunk : chunkRepository.findIndexed(organizationId, model, version)) {
            float[] vector = VectorMath.fromJson(chunk.getEmbeddingJson());
            if (vector.length != query.length) {
                continue;
            }
            matches.add(new RetrievedPassage(
                    chunk.getDocument().getId(),
                    chunk.getDocument().getTitle(),
                    chunk.getChunkIndex(),
                    chunk.getPageNumber(),
                    chunk.getSectionLabel(),
                    chunk.getContent(),
                    VectorMath.cosineDistance(query, vector)));
        }
        return matches;
    }

    private List<RetrievedPassage> searchPgVector(UUID organizationId, String model, String version, float[] query) {
        String literal = VectorMath.toJson(query);
        return jdbcTemplate.query(
                """
                SELECT c.document_id, d.title, c.chunk_index, c.page_number, c.section_label, c.content,
                       (c.embedding <=> cast(? as vector)) AS distance
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE c.organization_id = ?
                  AND d.organization_id = ?
                  AND c.embedding_model = ?
                  AND c.embedding_model_version = ?
                  AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> cast(? as vector)
                LIMIT ?
                """,
                (resultSet, row) -> new RetrievedPassage(
                        resultSet.getObject("document_id", UUID.class),
                        resultSet.getString("title"),
                        resultSet.getInt("chunk_index"),
                        (Integer) resultSet.getObject("page_number"),
                        resultSet.getString("section_label"),
                        resultSet.getString("content"),
                        resultSet.getDouble("distance")),
                literal,
                organizationId,
                organizationId,
                model,
                version,
                literal,
                Math.max(1, aiProperties.getRetrievalLimit()));
    }

    private boolean usesPgVector() {
        return "pgvector".equalsIgnoreCase(aiProperties.getVectorStore());
    }
}
