package com.workpilot.ai.repository;

import com.workpilot.ai.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    void deleteByDocumentId(UUID documentId);

    long countByOrganizationIdAndEmbeddingModelAndEmbeddingModelVersion(
            UUID organizationId,
            String embeddingModel,
            String embeddingModelVersion);

    @Query("""
            select chunk from DocumentChunk chunk
            join fetch chunk.document document
            where chunk.organizationId = :organizationId
              and document.organization.id = :organizationId
              and chunk.embeddingModel = :model
              and chunk.embeddingModelVersion = :version
            """)
    List<DocumentChunk> findIndexed(
            @Param("organizationId") UUID organizationId,
            @Param("model") String model,
            @Param("version") String version);
}
