CREATE TYPE reconciliation_case_status AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'RESOLVED',
    'FAILED'
);

CREATE TYPE reconciliation_reason AS ENUM (
    'PROVIDER_TIMEOUT',
    'UNKNOWN_RESPONSE',
    'STATUS_MISMATCH'
);

CREATE TYPE reconciliation_resolved_status AS ENUM (
    'SUCCESS',
    'FAILED'
);

CREATE TABLE reconciliation_case (
    reconciliation_id UUID PRIMARY KEY,

    payment_id UUID NOT NULL,

    attempt_id UUID NOT NULL,

    status reconciliation_case_status NOT NULL,

    reason reconciliation_reason NOT NULL,

    provider_status VARCHAR(50),

    resolved_status reconciliation_resolved_status,

    retry_count INT NOT NULL DEFAULT 0,

    next_check_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,

    resolved_at TIMESTAMPTZ,

    CONSTRAINT fk_reconciliation_case_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id),

    CONSTRAINT fk_reconciliation_case_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES payment_attempt(attempt_id),

    CONSTRAINT chk_reconciliation_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT uq_reconciliation_case_attempt_id
        UNIQUE (attempt_id)
);

CREATE INDEX idx_reconciliation_case_payment_id
    ON reconciliation_case(payment_id);

CREATE INDEX idx_reconciliation_case_check
    ON reconciliation_case(status, next_check_at);
