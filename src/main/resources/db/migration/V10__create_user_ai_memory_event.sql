CREATE TABLE user_ai_memory_event (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    query TEXT NOT NULL,
    region VARCHAR(100),
    apartment_name VARCHAR(255),
    min_price BIGINT,
    max_price BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_ai_memory_event_user_created
    ON user_ai_memory_event (user_id, created_at DESC);

CREATE INDEX idx_user_ai_memory_event_user_region
    ON user_ai_memory_event (user_id, region);
