CREATE TYPE actor_type AS ENUM (
    'MERCHANT',
    'SYSTEM',
    'ADMIN'
);

CREATE TABLE audit_log (
    audit_id UUID PRIMARY KEY,

    merchant_id UUID
        REFERENCES merchant(merchant_id),

    actor_type actor_type NOT NULL,

    actor_id UUID,

    action VARCHAR(100) NOT NULL,

    resource_type VARCHAR(100) NOT NULL,

    resource_id UUID,

    metadata JSONB,

    ip_address VARCHAR(45),

    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_log_merchant_id
    ON audit_log(merchant_id);

CREATE INDEX idx_audit_log_resource
    ON audit_log(resource_type, resource_id);

CREATE INDEX idx_audit_log_created_at
    ON audit_log(created_at);
