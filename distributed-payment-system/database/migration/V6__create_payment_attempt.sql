CREATE TYPE payment_attempt_status AS ENUM (
    'PROCESSING',
    'SUCCESS',
    'FAILED',
    'TIMEOUT'
);

CREATE TABLE payment_attempt (
    attempt_id UUID PRIMARY KEY,

    payment_id UUID NOT NULL,

    provider_id UUID NOT NULL,

    attempt_number INT NOT NULL,

    amount DECIMAL(12,2) NOT NULL,

    currency CHAR(3) NOT NULL,

    status payment_attempt_status NOT NULL,

    provider_reference VARCHAR(120),

    error_code VARCHAR(100),

    failure_reason TEXT,

    request_payload JSONB,

    response_payload JSONB,

    latency_ms INT,

    requested_at TIMESTAMPTZ NOT NULL,

    responded_at TIMESTAMPTZ,

    CONSTRAINT fk_payment_attempt_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id),

    CONSTRAINT fk_payment_attempt_provider
        FOREIGN KEY (provider_id)
        REFERENCES provider(provider_id),

    CONSTRAINT uq_payment_attempt_number
        UNIQUE (payment_id, attempt_number),

    CONSTRAINT uq_payment_attempt_provider_reference
        UNIQUE (provider_id, provider_reference)
);