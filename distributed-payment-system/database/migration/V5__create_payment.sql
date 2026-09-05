CREATE TYPE payment_status AS ENUM (
    'CREATED',
    'PROCESSING',
    'SUCCESS',
    'FAILED',
    'UNKNOWN'
);

CREATE TYPE payment_type AS ENUM (
    'PAYMENT',
    'REFUND',
    'REVERSAL',
    'ADJUSTMENT'
);

CREATE TABLE payment (
    payment_id UUID PRIMARY KEY,

    merchant_id UUID NOT NULL,

    amount DECIMAL(12,2) NOT NULL,

    currency CHAR(3) DEFAULT 'INR',

    status payment_status NOT NULL,

    idempotency_key VARCHAR(100) NOT NULL,

    customer_ref VARCHAR(120),

    order_ref VARCHAR(120),

    created_at TIMESTAMPTZ NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL,

    payment_type payment_type NOT NULL,

    description TEXT,

    CONSTRAINT fk_payment_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT chk_payment_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_payment_merchant_id
    ON payment(merchant_id);
