package com.workpilot.ai.web;

import com.workpilot.ai.dto.AddMemberRequest;
import com.workpilot.ai.dto.CreateOrganizationRequest;
import com.workpilot.ai.dto.MembershipResponse;
import com.workpilot.ai.dto.OrganizationResponse;
import com.workpilot.ai.dto.UpdateMemberRoleRequest;
import com.workpilot.ai.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "Organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an organization owned by the current user")
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.create(request);
    }

    @GetMapping
    @Operation(summary = "List organizations the current user belongs to")
    public List<OrganizationResponse> list() {
        return organizationService.listForCurrentUser();
    }

    @GetMapping("/{organizationId}")
    @Operation(summary = "Get an organization the current user belongs to")
    public OrganizationResponse get(@PathVariable UUID organizationId) {
        return organizationService.get(organizationId);
    }

    @GetMapping("/{organizationId}/members")
    @Operation(summary = "List members of an organization")
    public List<MembershipResponse> members(@PathVariable UUID organizationId) {
        return organizationService.listMembers(organizationId);
    }

    @PostMapping("/{organizationId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an existing account to an organization")
    public MembershipResponse addMember(
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddMemberRequest request) {
        return organizationService.addMember(organizationId, request);
    }

    @PatchMapping("/{organizationId}/members/{userId}")
    @Operation(summary = "Change a member's role")
    public MembershipResponse updateMemberRole(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return organizationService.updateMemberRole(organizationId, userId, request);
    }

    @DeleteMapping("/{organizationId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member from an organization")
    public void removeMember(@PathVariable UUID organizationId, @PathVariable UUID userId) {
        organizationService.removeMember(organizationId, userId);
    }
}
