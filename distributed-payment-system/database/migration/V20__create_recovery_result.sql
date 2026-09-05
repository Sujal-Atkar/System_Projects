CREATE TYPE recovery_result_outcome AS ENUM (
    'RECOVERED',
    'NOT_RECOVERED',
    'PARTIALLY_RECOVERED'
);

CREATE TABLE recovery_result (
    result_id UUID PRIMARY KEY,

    action_id UUID NOT NULL UNIQUE
        REFERENCES recovery_action(action_id),

    outcome recovery_result_outcome NOT NULL,

    recovered_amount DECIMAL(12,2) NOT NULL
        CHECK (recovered_amount >= 0),

    currency CHAR(3) NOT NULL,

    resulting_payment_id UUID
        REFERENCES payment(payment_id),

    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_recovery_result_resulting_payment_id
    ON recovery_result(resulting_payment_id);
