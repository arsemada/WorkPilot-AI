package com.workpilot.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrganizationSlugTest {

    @Test
    void slugifyNormalizesOrganizationNames() {
        assertEquals("acme-ops", OrganizationService.slugify("  Acme Ops! "));
        assertEquals("org", OrganizationService.slugify("***"));
    }
}
