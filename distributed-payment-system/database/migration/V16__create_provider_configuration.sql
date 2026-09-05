CREATE TYPE provider_configuration_environment AS ENUM (
    'TEST',
    'LIVE'
);

CREATE TABLE provider_configuration (
    configuration_id UUID PRIMARY KEY,

    provider_id UUID NOT NULL
        REFERENCES provider(provider_id),

    environment provider_configuration_environment NOT NULL,

    endpoint_url TEXT NOT NULL,

    credential_reference VARCHAR(255) NOT NULL,

    timeout_ms INT NOT NULL
        CHECK (timeout_ms > 0),

    max_retries INT NOT NULL
        CHECK (max_retries >= 0),

    enabled BOOLEAN NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL,

    UNIQUE (provider_id, environment)
);
