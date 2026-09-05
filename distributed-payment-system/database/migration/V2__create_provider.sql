CREATE TYPE provider_status AS ENUM (
    'ACTIVE',
    'DOWN'
);

CREATE TABLE provider (
    provider_id UUID PRIMARY KEY,
    provider_name VARCHAR(50) NOT NULL UNIQUE,
    provider_code VARCHAR(50) NOT NULL UNIQUE,
    priority INT NOT NULL,
    status provider_status NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);