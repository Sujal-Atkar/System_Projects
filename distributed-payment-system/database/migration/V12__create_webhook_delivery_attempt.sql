CREATE TABLE webhook_delivery_attempt (
    delivery_attempt_id UUID PRIMARY KEY,

    webhook_id UUID NOT NULL,

    attempt_number INT NOT NULL,

    status_code INT,

    response_time_ms INT,

    error_message TEXT,

    attempted_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_webhook_delivery_attempt_webhook
        FOREIGN KEY (webhook_id)
        REFERENCES webhook_delivery(webhook_id),

    CONSTRAINT uq_webhook_delivery_attempt_number
        UNIQUE (webhook_id, attempt_number)
);
