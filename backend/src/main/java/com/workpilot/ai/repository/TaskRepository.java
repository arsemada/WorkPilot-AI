package com.workpilot.ai.repository;

import com.workpilot.ai.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.organization.id = :organizationId")
    Optional<Task> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
