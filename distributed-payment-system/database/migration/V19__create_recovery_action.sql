CREATE TYPE recovery_action_type AS ENUM (
    'RETRY',
    'REROUTE',
    'NOTIFY'
);

CREATE TYPE recovery_action_status AS ENUM (
    'PENDING',
    'EXECUTING',
    'SUCCESS',
    'FAILED',
    'CANCELLED'
);

CREATE TABLE recovery_action (
    action_id UUID PRIMARY KEY,

    decision_id UUID NOT NULL
        REFERENCES recovery_decision(decision_id),

    payment_id UUID NOT NULL
        REFERENCES payment(payment_id),

    risk_id UUID NOT NULL
        REFERENCES revenue_risk(risk_id),

    action_type recovery_action_type NOT NULL,

    target_provider_id UUID
        REFERENCES provider(provider_id),

    status recovery_action_status NOT NULL,

    attempt_number INT NOT NULL
        CHECK (attempt_number > 0),

    executed_at TIMESTAMPTZ,

    completed_at TIMESTAMPTZ,

    failure_reason TEXT
);

CREATE UNIQUE INDEX uq_recovery_action_executing_risk
    ON recovery_action(risk_id)
    WHERE status = 'EXECUTING';

CREATE INDEX idx_recovery_action_decision_id
    ON recovery_action(decision_id);

CREATE INDEX idx_recovery_action_payment_id
    ON recovery_action(payment_id);

CREATE INDEX idx_recovery_action_risk_id
    ON recovery_action(risk_id);

CREATE INDEX idx_recovery_action_target_provider_id
    ON recovery_action(target_provider_id);
