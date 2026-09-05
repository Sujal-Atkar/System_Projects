CREATE TYPE ledger_transaction_type AS ENUM (
    'PAYMENT',
    'REFUND',
    'FEE',
    'REVERSAL',
    'ADJUSTMENT'
);

CREATE TYPE ledger_transaction_status AS ENUM (
    'POSTED',
    'REVERSED'
);

CREATE TABLE ledger_transaction (
    transaction_id UUID PRIMARY KEY,

    payment_id UUID,

    transaction_type ledger_transaction_type NOT NULL,

    amount DECIMAL(12,2) NOT NULL,

    reference_id VARCHAR(100) NOT NULL,

    currency CHAR(3) NOT NULL,

    status ledger_transaction_status NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_ledger_transaction_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id),

    CONSTRAINT chk_ledger_transaction_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_ledger_transaction_payment_id
    ON ledger_transaction(payment_id);
