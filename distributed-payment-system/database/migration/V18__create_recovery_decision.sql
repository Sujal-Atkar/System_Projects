CREATE TYPE decision_type AS ENUM (
    'RETRY',
    'REROUTE',
    'NOTIFY',
    'NO_ACTION'
);

CREATE TABLE recovery_decision (
    decision_id UUID PRIMARY KEY,

    risk_id UUID NOT NULL
        REFERENCES revenue_risk(risk_id),

    decision_type decision_type NOT NULL,

    reasoning TEXT NOT NULL,

    confidence_score DECIMAL(5,4)
        CHECK (
            confidence_score IS NULL
            OR (confidence_score >= 0 AND confidence_score <= 1)
        ),

    model_version VARCHAR(100),

    bounded BOOLEAN NOT NULL,

    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_recovery_decision_risk_id
    ON recovery_decision(risk_id);
    