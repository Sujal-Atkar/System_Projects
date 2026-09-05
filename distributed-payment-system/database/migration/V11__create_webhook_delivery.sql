CREATE TYPE webhook_delivery_status AS ENUM (
    'PENDING',
    'DELIVERED',
    'FAILED',
    'EXHAUSTED'
);

CREATE TABLE webhook_delivery (
    webhook_id UUID PRIMARY KEY,

    merchant_id UUID NOT NULL,

    payment_id UUID,

    event_type VARCHAR(100) NOT NULL,

    payload JSONB NOT NULL,

    destination_url TEXT NOT NULL,

    status webhook_delivery_status NOT NULL,

    attempt_count INT NOT NULL DEFAULT 0,

    next_retry_at TIMESTAMPTZ,

    last_attempt_at TIMESTAMPTZ,

    delivered_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_webhook_delivery_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT fk_webhook_delivery_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id),

    CONSTRAINT chk_webhook_delivery_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_webhook_delivery_merchant_id
    ON webhook_delivery(merchant_id);

CREATE INDEX idx_webhook_delivery_payment_id
    ON webhook_delivery(payment_id);

CREATE INDEX idx_webhook_delivery_retry
    ON webhook_delivery(status, next_retry_at);
