CREATE TYPE api_credential_environment AS ENUM (
    'TEST',
    'LIVE'
);

CREATE TYPE api_credential_status AS ENUM (
    'ACTIVE',
    'REVOKED'
);

CREATE TABLE api_credential (
    credential_id UUID PRIMARY KEY,

    merchant_id UUID NOT NULL,

    key_id VARCHAR(100) NOT NULL UNIQUE,

    secret_hash VARCHAR(255) NOT NULL,

    environment api_credential_environment NOT NULL,

    status api_credential_status NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    expires_at TIMESTAMPTZ,

    last_used_at TIMESTAMPTZ,

    CONSTRAINT fk_api_credential_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchant(merchant_id),

    CONSTRAINT chk_api_credential_expiry
        CHECK (
            expires_at IS NULL
            OR expires_at >= created_at
        )
);

CREATE INDEX idx_api_credential_merchant_id
    ON api_credential(merchant_id);
