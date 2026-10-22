-- flyway:executeInTransaction=false
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE document_chunks ADD COLUMN embedding vector(1536);

CREATE INDEX idx_document_chunks_embedding
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
