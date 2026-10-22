package com.workpilot.ai.repository;

import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.OrganizationRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    @Query("SELECT m FROM OrganizationMembership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.organization.id = :organizationId AND m.user.id = :userId")
    Optional<OrganizationMembership> findWithDetailsByOrganizationIdAndUserId(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    @Query("SELECT m FROM OrganizationMembership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.user.id = :userId")
    List<OrganizationMembership> findAllWithOrganizationByUserId(@Param("userId") UUID userId);

    @Query("SELECT m FROM OrganizationMembership m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.organization.id = :organizationId")
    List<OrganizationMembership> findAllWithUserByOrganizationId(@Param("organizationId") UUID organizationId);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    long countByOrganizationIdAndRole(UUID organizationId, OrganizationRole role);

    @Query("SELECT COUNT(m) FROM OrganizationMembership m WHERE m.organization.id = :organizationId")
    long countMembers(@Param("organizationId") UUID organizationId);
}
