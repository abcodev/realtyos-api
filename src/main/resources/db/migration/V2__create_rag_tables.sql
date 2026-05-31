CREATE TABLE rag_document (
                              id BIGSERIAL PRIMARY KEY,
                              title VARCHAR(255),
                              content TEXT NOT NULL,
                              apartment_name VARCHAR(255),
                              region VARCHAR(255),
                              created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE rag_embedding (
                               id BIGSERIAL PRIMARY KEY,
                               document_id BIGINT NOT NULL REFERENCES rag_document(id),
                               embedding VECTOR(1536) NOT NULL,
                               created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_rag_embedding_vector
    ON rag_embedding
    USING hnsw (embedding vector_cosine_ops);