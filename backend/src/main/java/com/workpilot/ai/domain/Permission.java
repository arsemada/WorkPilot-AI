package com.workpilot.ai.domain;

/**
 * Granular permissions mapped to organization roles.
 */
public enum Permission {
    ORG_READ,
    ORG_UPDATE,
    ORG_DELETE,
    ORG_TRANSFER,
    MEMBER_READ,
    MEMBER_INVITE,
    MEMBER_UPDATE,
    MEMBER_REMOVE,
    RESOURCE_CREATE,
    RESOURCE_READ,
    RESOURCE_UPDATE,
    RESOURCE_DELETE
}
