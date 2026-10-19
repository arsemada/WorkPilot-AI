package com.workpilot.ai.security;

import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.OrganizationScoped;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.exception.ForbiddenException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.OrganizationMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tenant gate for organization-owned data.
 * Callers that are not members of the path organization receive a not-found response,
 * so the API does not reveal organizations they cannot access.
 */
@Service
public class OrganizationAccessService {

    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationAccessService(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationMembership requireMembership(UUID organizationId) {
        return membershipRepository
                .findWithDetailsByOrganizationIdAndUserId(organizationId, SecurityUtils.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    @Transactional(readOnly = true)
    public OrganizationMembership requirePermission(UUID organizationId, Permission permission) {
        OrganizationMembership membership = requireMembership(organizationId);
        if (!membership.getRole().hasPermission(permission)) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
        return membership;
    }

    /**
     * Use this for every future organization-owned entity.
     * The entity must implement {@link OrganizationScoped}. A resource stored under a
     * different organization is reported as not found.
     */
    @Transactional(readOnly = true)
    public void requireResourceInOrganization(UUID pathOrganizationId, OrganizationScoped resource, Permission permission) {
        OrganizationMembership membership = requireMembership(pathOrganizationId);
        if (resource == null || resource.scopedOrganizationId() == null
                || !pathOrganizationId.equals(resource.scopedOrganizationId())) {
            throw new ResourceNotFoundException("Resource not found");
        }
        if (!membership.getRole().hasPermission(permission)) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
    }
}
