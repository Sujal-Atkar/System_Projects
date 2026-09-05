CREATE TABLE payment_event (
    event_id UUID PRIMARY KEY,

    payment_id UUID NOT NULL,

    event_type VARCHAR(100) NOT NULL,

    event_version INT NOT NULL,

    event_payload JSONB NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_payment_event_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id),

    CONSTRAINT uq_payment_event_version
        UNIQUE (payment_id, event_version)
);

CREATE INDEX idx_payment_event_payment_id
    ON payment_event(payment_id);
