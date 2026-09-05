CREATE TYPE ledger_entry_type AS ENUM (
    'DEBIT',
    'CREDIT'
);

CREATE TABLE ledger_entry (
    entry_id UUID PRIMARY KEY,

    transaction_id UUID NOT NULL,

    account_id UUID NOT NULL,

    entry_type ledger_entry_type NOT NULL,

    amount DECIMAL(12,2) NOT NULL,

    currency CHAR(3) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_ledger_entry_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES ledger_transaction(transaction_id),

    CONSTRAINT fk_ledger_entry_account
        FOREIGN KEY (account_id)
        REFERENCES ledger_account(account_id),

    CONSTRAINT chk_ledger_entry_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entry_transaction_id
    ON ledger_entry(transaction_id);

CREATE INDEX idx_ledger_entry_account_id
    ON ledger_entry(account_id);
