ALTER TABLE rag_document
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS document_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now();

UPDATE rag_document
SET content_hash = md5(content)
WHERE content_hash IS NULL;

ALTER TABLE rag_document
    ALTER COLUMN content_hash SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rag_document_content_hash
    ON rag_document (content_hash);
