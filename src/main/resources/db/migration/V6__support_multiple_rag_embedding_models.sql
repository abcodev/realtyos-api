DROP INDEX IF EXISTS idx_rag_embedding_vector;
DROP INDEX IF EXISTS uq_rag_embedding_document;

ALTER TABLE rag_embedding
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20),
    ADD COLUMN IF NOT EXISTS model VARCHAR(100),
    ADD COLUMN IF NOT EXISTS dimension INTEGER;

UPDATE rag_embedding
SET provider = COALESCE(provider, 'OPENAI'),
    model = COALESCE(model, 'text-embedding-3-small'),
    dimension = COALESCE(dimension, 1536)
WHERE provider IS NULL
   OR model IS NULL
   OR dimension IS NULL;

ALTER TABLE rag_embedding
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN model SET NOT NULL,
    ALTER COLUMN dimension SET NOT NULL,
    ALTER COLUMN embedding TYPE vector USING embedding::vector;

CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_embedding_document_provider_model
    ON rag_embedding (document_id, provider, model);

CREATE INDEX IF NOT EXISTS idx_rag_embedding_openai_text_embedding_3_small_vector
    ON rag_embedding
    USING hnsw ((embedding::vector(1536)) vector_cosine_ops)
    WHERE provider = 'OPENAI'
      AND model = 'text-embedding-3-small'
      AND dimension = 1536;
