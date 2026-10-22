ALTER TABLE documents ADD COLUMN storage_key VARCHAR(80);
ALTER TABLE documents ADD COLUMN processing_status VARCHAR(20);
ALTER TABLE documents ADD COLUMN processing_error VARCHAR(300);

UPDATE documents
SET storage_key = CAST(organization_id AS VARCHAR(36)) || '/' || CAST(id AS VARCHAR(36)),
    processing_status = 'COMPLETED';

ALTER TABLE documents ALTER COLUMN storage_key SET NOT NULL;
ALTER TABLE documents ALTER COLUMN processing_status SET NOT NULL;

ALTER TABLE documents ADD CONSTRAINT chk_documents_processing_status
    CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));

CREATE INDEX idx_documents_org_processing ON documents(organization_id, processing_status);
