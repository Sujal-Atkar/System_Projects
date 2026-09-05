CREATE TYPE outbox_event_status AS ENUM (
    'PENDING',
    'PUBLISHED',
    'FAILED'
);

CREATE TABLE outbox_event (
    outbox_event_id UUID PRIMARY KEY,

    aggregate_type VARCHAR(50) NOT NULL,

    aggregate_id UUID NOT NULL,

    event_type VARCHAR(100) NOT NULL,

    event_key VARCHAR(255) NOT NULL,

    payload JSONB NOT NULL,

    status outbox_event_status NOT NULL,

    retry_count INT NOT NULL DEFAULT 0
        CHECK (retry_count >= 0),

    available_at TIMESTAMPTZ NOT NULL,

    published_at TIMESTAMPTZ,

    last_error TEXT,

    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_outbox_event_status_available_at
    ON outbox_event(status, available_at);

CREATE INDEX idx_outbox_event_aggregate
    ON outbox_event(aggregate_type, aggregate_id);
    