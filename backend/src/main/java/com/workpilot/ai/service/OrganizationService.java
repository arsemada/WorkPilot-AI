package com.workpilot.ai.service;

import com.workpilot.ai.domain.Organization;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.OrganizationRole;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.User;
import com.workpilot.ai.dto.AddMemberRequest;
import com.workpilot.ai.dto.CreateOrganizationRequest;
import com.workpilot.ai.dto.MembershipResponse;
import com.workpilot.ai.dto.OrganizationResponse;
import com.workpilot.ai.dto.UpdateMemberRoleRequest;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ConflictException;
import com.workpilot.ai.exception.ForbiddenException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.OrganizationMembershipRepository;
import com.workpilot.ai.repository.OrganizationRepository;
import com.workpilot.ai.repository.UserRepository;
import com.workpilot.ai.security.OrganizationAccessService;
import com.workpilot.ai.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccessService;
    private final WorkflowCatalog workflowCatalog;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository,
            OrganizationAccessService organizationAccessService,
            WorkflowCatalog workflowCatalog) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.organizationAccessService = organizationAccessService;
        this.workflowCatalog = workflowCatalog;
    }

    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        User owner = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String slug = request.slug() == null || request.slug().isBlank() ? null : request.slug().trim();
        Organization organization = createForOwner(owner, request.name().trim(), slug);
        return OrganizationResponse.fromEntity(organization);
    }

    @Transactional
    public Organization createForOwner(User owner, String name, String requestedSlug) {
        String slug = requestedSlug == null || requestedSlug.isBlank()
                ? uniqueSlug(slugify(name))
                : requestedSlug.trim();
        if (organizationRepository.existsBySlug(slug)) {
            throw new ConflictException("An organization with this slug already exists");
        }
        Organization organization = organizationRepository.save(new Organization(name.trim(), slug));
        membershipRepository.save(new OrganizationMembership(organization, owner, OrganizationRole.OWNER));
        workflowCatalog.ensure(organization);
        return organization;
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listForCurrentUser() {
        return membershipRepository.findAllWithOrganizationByUserId(SecurityUtils.currentUserId()).stream()
                .map(membership -> OrganizationResponse.fromEntity(membership.getOrganization()))
                .sorted(Comparator.comparing(OrganizationResponse::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID organizationId) {
        OrganizationMembership membership = organizationAccessService.requirePermission(organizationId, Permission.ORG_READ);
        return OrganizationResponse.fromEntity(membership.getOrganization());
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMembers(UUID organizationId) {
        organizationAccessService.requirePermission(organizationId, Permission.MEMBER_READ);
        return membershipRepository.findAllWithUserByOrganizationId(organizationId).stream()
                .map(MembershipResponse::fromEntity)
                .sorted(Comparator.comparing(MembershipResponse::userEmail))
                .toList();
    }

    @Transactional
    public MembershipResponse addMember(UUID organizationId, AddMemberRequest request) {
        OrganizationMembership actor = organizationAccessService.requirePermission(organizationId, Permission.MEMBER_INVITE);
        if (!actor.getRole().canManageRole(request.role())) {
            throw new ForbiddenException("You cannot assign the " + request.role() + " role");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account exists for this email"));
        if (!user.isActive()) {
            throw new BadRequestException("This account cannot be added to the organization");
        }
        if (membershipRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new ConflictException("This account is already a member of the organization");
        }

        OrganizationMembership membership = membershipRepository.save(
                new OrganizationMembership(actor.getOrganization(), user, request.role()));
        return MembershipResponse.fromEntity(membership);
    }

    @Transactional
    public MembershipResponse updateMemberRole(UUID organizationId, UUID userId, UpdateMemberRoleRequest request) {
        OrganizationMembership actor = organizationAccessService.requirePermission(organizationId, Permission.MEMBER_UPDATE);
        OrganizationMembership target = membershipRepository
                .findWithDetailsByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (!actor.getRole().canManageRole(target.getRole()) || !actor.getRole().canManageRole(request.role())) {
            throw new ForbiddenException("You cannot change this member's role");
        }
        if (target.getRole() == OrganizationRole.OWNER
                && request.role() != OrganizationRole.OWNER
                && membershipRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER) <= 1) {
            throw new BadRequestException("The organization must keep at least one owner");
        }

        target.setRole(request.role());
        return MembershipResponse.fromEntity(target);
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID userId) {
        OrganizationMembership actor = organizationAccessService.requirePermission(organizationId, Permission.MEMBER_REMOVE);
        OrganizationMembership target = membershipRepository
                .findWithDetailsByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (!actor.getRole().canManageRole(target.getRole())) {
            throw new ForbiddenException("You cannot remove this member");
        }
        if (target.getRole() == OrganizationRole.OWNER
                && membershipRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER) <= 1) {
            throw new BadRequestException("The organization must keep at least one owner");
        }
        membershipRepository.delete(target);
    }

    private String uniqueSlug(String base) {
        String candidate = base;
        int suffix = 2;
        while (organizationRepository.existsBySlug(candidate)) {
            String ending = "-" + suffix++;
            String trimmed = base.substring(0, Math.min(base.length(), 100 - ending.length()));
            candidate = trimmed.replaceAll("-$", "") + ending;
        }
        return candidate;
    }

    static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() < 2) {
            slug = "org";
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-$", "");
        }
        return slug;
    }
}
