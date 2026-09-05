CREATE TYPE reconciliation_attempt_result AS ENUM (
    'SUCCESS',
    'FAILED',
    'STILL_UNKNOWN'
);

CREATE TABLE reconciliation_attempt (
    reconciliation_attempt_id UUID PRIMARY KEY,

    reconciliation_id UUID NOT NULL,

    attempt_number INT NOT NULL,

    requested_at TIMESTAMPTZ NOT NULL,

    response_at TIMESTAMPTZ,

    provider_status VARCHAR(50),

    result reconciliation_attempt_result NOT NULL,

    error_message TEXT,

    CONSTRAINT fk_reconciliation_attempt_case
        FOREIGN KEY (reconciliation_id)
        REFERENCES reconciliation_case(reconciliation_id),

    CONSTRAINT uq_reconciliation_attempt_number
        UNIQUE (reconciliation_id, attempt_number)
);
