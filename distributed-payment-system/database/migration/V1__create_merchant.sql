CREATE TYPE merchant_status AS ENUM (
    'ACTIVE',
    'SUSPENDED'
);

CREATE TABLE merchant (
    merchant_id UUID PRIMARY KEY,
    merchant_name VARCHAR(150) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    webhook_url TEXT,
    status merchant_status NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    phone VARCHAR(20)
);