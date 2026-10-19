ALTER TABLE organization_memberships
    ADD CONSTRAINT chk_organization_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'));
