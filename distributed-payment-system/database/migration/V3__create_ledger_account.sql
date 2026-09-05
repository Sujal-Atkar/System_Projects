CREATE TYPE ledger_account_type AS ENUM (
    'MERCHANT',
    'PLATFORM',
    'PROVIDER',
    'CUSTOMER'
);

CREATE TYPE ledger_account_status AS ENUM (
    'ACTIVE',
    'FROZEN',
    'CLOSED'
);

CREATE TABLE ledger_account (
    account_id UUID PRIMARY KEY,

    merchant_id UUID,

    account_name VARCHAR(100) NOT NULL,

    account_type ledger_account_type NOT NULL,

    currency CHAR(3) NOT NULL,

    status ledger_account_status NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_ledger_account_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT chk_ledger_account_merchant_required
        CHECK (
            (account_type = 'MERCHANT' AND merchant_id IS NOT NULL)
            OR (account_type <> 'MERCHANT')
        )
);

CREATE INDEX idx_ledger_account_merchant_id
    ON ledger_account(merchant_id);
