CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    content VARCHAR(2000) NOT NULL,
    page_number INTEGER,
    section_label VARCHAR(200),
    embedding_model VARCHAR(80) NOT NULL,
    embedding_model_version VARCHAR(40) NOT NULL,
    embedding_dimensions INTEGER NOT NULL,
    embedding_json VARCHAR(40000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_document_chunks_order UNIQUE (document_id, chunk_index),
    CONSTRAINT chk_document_chunks_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_document_chunks_dimensions CHECK (embedding_dimensions > 0)
);

CREATE INDEX idx_document_chunks_org_model ON document_chunks(organization_id, embedding_model, embedding_model_version);

ALTER TABLE documents ADD COLUMN indexing_status VARCHAR(20);
ALTER TABLE documents ADD COLUMN indexing_error VARCHAR(300);
ALTER TABLE documents ADD COLUMN indexed_model VARCHAR(80);
ALTER TABLE documents ADD COLUMN indexed_model_version VARCHAR(40);
ALTER TABLE documents ADD COLUMN indexed_dimensions INTEGER;

UPDATE documents
SET indexing_status = CASE
    WHEN extracted_text IS NOT NULL AND LENGTH(TRIM(extracted_text)) > 0 THEN 'PENDING'
    ELSE 'SKIPPED'
END;

ALTER TABLE documents ALTER COLUMN indexing_status SET NOT NULL;
ALTER TABLE documents ADD CONSTRAINT chk_documents_indexing_status CHECK (indexing_status IN (
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED',
    'SKIPPED'
));

ALTER TABLE conversation_messages ADD COLUMN document_support BOOLEAN;

CREATE TABLE message_sources (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES conversation_messages(id) ON DELETE CASCADE,
    document_id UUID REFERENCES documents(id) ON DELETE SET NULL,
    document_title VARCHAR(200) NOT NULL,
    chunk_index INTEGER NOT NULL,
    page_number INTEGER,
    section_label VARCHAR(200),
    snippet VARCHAR(400) NOT NULL
);

CREATE INDEX idx_message_sources_message ON message_sources(message_id);
