CREATE TABLE documents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(5000),
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    extracted_text VARCHAR(20000),
    uploaded_by_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_documents_size CHECK (size_bytes >= 0),
    CONSTRAINT chk_documents_content_type CHECK (content_type IN (
        'application/pdf',
        'text/plain',
        'text/csv',
        'text/markdown',
        'image/png',
        'image/jpeg',
        'image/webp',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    ))
);

CREATE INDEX idx_documents_organization_id ON documents(organization_id);
CREATE INDEX idx_documents_organization_created ON documents(organization_id, created_at);
