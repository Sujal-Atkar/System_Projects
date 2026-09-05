CREATE TYPE idempotency_record_status AS ENUM (
    'RESERVED',
    'COMPLETED',
    'FAILED',
    'EXPIRED'
);

CREATE TABLE idempotency_record (
    idempotency_id UUID PRIMARY KEY,

    merchant_id UUID NOT NULL,

    idempotency_key VARCHAR(100) NOT NULL,

    payment_id UUID NOT NULL UNIQUE,

    request_hash VARCHAR(128) NOT NULL,

    response_status INT,

    created_at TIMESTAMPTZ NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    response_payload JSONB,

    status idempotency_record_status NOT NULL,

    CONSTRAINT fk_idempotency_record_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT fk_idempotency_record_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id),

    CONSTRAINT uq_idempotency_merchant_key
        UNIQUE (merchant_id, idempotency_key),

    CONSTRAINT chk_idempotency_expiry
        CHECK (expires_at >= created_at)
);
