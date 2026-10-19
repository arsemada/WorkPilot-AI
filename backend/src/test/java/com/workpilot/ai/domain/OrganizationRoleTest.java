package com.workpilot.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationRoleTest {

    @Test
    void ownerHasEveryPermission() {
        for (Permission permission : Permission.values()) {
            assertTrue(OrganizationRole.OWNER.hasPermission(permission));
        }
    }

    @Test
    void adminCanManageMembersAndResourcesButNotOwnership() {
        assertTrue(OrganizationRole.ADMIN.hasPermission(Permission.ORG_READ));
        assertTrue(OrganizationRole.ADMIN.hasPermission(Permission.ORG_UPDATE));
        assertTrue(OrganizationRole.ADMIN.hasPermission(Permission.MEMBER_INVITE));
        assertTrue(OrganizationRole.ADMIN.hasPermission(Permission.MEMBER_UPDATE));
        assertTrue(OrganizationRole.ADMIN.hasPermission(Permission.MEMBER_REMOVE));
        assertTrue(OrganizationRole.ADMIN.hasPermission(Permission.RESOURCE_DELETE));
        assertFalse(OrganizationRole.ADMIN.hasPermission(Permission.ORG_DELETE));
        assertFalse(OrganizationRole.ADMIN.hasPermission(Permission.ORG_TRANSFER));
    }

    @Test
    void memberCanReadAndUpdateResourcesButCannotAdministerTheOrganization() {
        assertTrue(OrganizationRole.MEMBER.hasPermission(Permission.ORG_READ));
        assertTrue(OrganizationRole.MEMBER.hasPermission(Permission.MEMBER_READ));
        assertTrue(OrganizationRole.MEMBER.hasPermission(Permission.RESOURCE_CREATE));
        assertTrue(OrganizationRole.MEMBER.hasPermission(Permission.RESOURCE_READ));
        assertTrue(OrganizationRole.MEMBER.hasPermission(Permission.RESOURCE_UPDATE));
        assertFalse(OrganizationRole.MEMBER.hasPermission(Permission.RESOURCE_DELETE));
        assertFalse(OrganizationRole.MEMBER.hasPermission(Permission.MEMBER_INVITE));
        assertFalse(OrganizationRole.MEMBER.hasPermission(Permission.ORG_UPDATE));
    }

    @Test
    void roleManagementFollowsOwnerAdminMemberHierarchy() {
        assertTrue(OrganizationRole.OWNER.canManageRole(OrganizationRole.OWNER));
        assertTrue(OrganizationRole.OWNER.canManageRole(OrganizationRole.ADMIN));
        assertTrue(OrganizationRole.OWNER.canManageRole(OrganizationRole.MEMBER));

        assertFalse(OrganizationRole.ADMIN.canManageRole(OrganizationRole.OWNER));
        assertFalse(OrganizationRole.ADMIN.canManageRole(OrganizationRole.ADMIN));
        assertTrue(OrganizationRole.ADMIN.canManageRole(OrganizationRole.MEMBER));

        assertFalse(OrganizationRole.MEMBER.canManageRole(OrganizationRole.OWNER));
        assertFalse(OrganizationRole.MEMBER.canManageRole(OrganizationRole.ADMIN));
        assertFalse(OrganizationRole.MEMBER.canManageRole(OrganizationRole.MEMBER));
    }
}
