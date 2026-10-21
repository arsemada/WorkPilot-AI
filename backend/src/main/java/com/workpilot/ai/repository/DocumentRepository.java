package com.workpilot.ai.repository;

import com.workpilot.ai.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.organization.id = :organizationId")
    Optional<Document> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
