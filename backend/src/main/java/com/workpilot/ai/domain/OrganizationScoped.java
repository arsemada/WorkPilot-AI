package com.workpilot.ai.domain;

import java.util.UUID;

/**
 * Marker for records that belong to one organization.
 * Future entities must expose their organization id and be checked through
 * {@code OrganizationAccessService} before they are returned or changed.
 */
public interface OrganizationScoped {

    UUID scopedOrganizationId();
}
