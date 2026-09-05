CREATE TYPE risk_type AS ENUM (
    'PAYMENT_FAILURE',
    'CHECKOUT_DROP',
    'OTHER'
);

CREATE TYPE revenue_risk_status AS ENUM (
    'DETECTED',
    'IN_RECOVERY',
    'RECOVERED',
    'LOST',
    'EXPIRED'
);

CREATE TABLE revenue_risk (
    risk_id UUID PRIMARY KEY,

    merchant_id UUID NOT NULL
        REFERENCES merchant(merchant_id),

    payment_id UUID NOT NULL
        REFERENCES payment(payment_id),

    risk_type risk_type NOT NULL,

    risk_amount DECIMAL(12,2) NOT NULL
        CHECK (risk_amount > 0),

    currency CHAR(3) NOT NULL,

    risk_score DECIMAL(5,4) NOT NULL
        CHECK (risk_score >= 0 AND risk_score <= 1),

    reason TEXT NOT NULL,

    status revenue_risk_status NOT NULL,

    detected_at TIMESTAMPTZ NOT NULL,

    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_revenue_risk_merchant_id
    ON revenue_risk(merchant_id);

CREATE INDEX idx_revenue_risk_payment_id
    ON revenue_risk(payment_id);
    