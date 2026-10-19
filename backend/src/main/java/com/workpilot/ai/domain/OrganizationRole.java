package com.workpilot.ai.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Roles within an organization and their assigned permissions.
 */
public enum OrganizationRole {
    OWNER(EnumSet.allOf(Permission.class)),
    ADMIN(EnumSet.of(
            Permission.ORG_READ,
            Permission.ORG_UPDATE,
            Permission.MEMBER_READ,
            Permission.MEMBER_INVITE,
            Permission.MEMBER_UPDATE,
            Permission.MEMBER_REMOVE,
            Permission.RESOURCE_CREATE,
            Permission.RESOURCE_READ,
            Permission.RESOURCE_UPDATE,
            Permission.RESOURCE_DELETE
    )),
    MEMBER(EnumSet.of(
            Permission.ORG_READ,
            Permission.MEMBER_READ,
            Permission.RESOURCE_CREATE,
            Permission.RESOURCE_READ,
            Permission.RESOURCE_UPDATE
    ));

    private final Set<Permission> permissions;

    OrganizationRole(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * Role hierarchy: OWNER > ADMIN > MEMBER.
     * Evaluates if this role can manage (assign or modify) the target role.
     */
    public boolean canManageRole(OrganizationRole targetRole) {
        if (this == OWNER) {
            return true;
        }
        if (this == ADMIN) {
            return targetRole == MEMBER;
        }
        return false;
    }
}
