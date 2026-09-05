INSERT INTO provider (provider_id, provider_name, provider_code, priority, status, created_at, updated_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Provider A', 'PROVIDER_A', 1, 'ACTIVE', NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222222', 'Provider B', 'PROVIDER_B', 2, 'ACTIVE', NOW(), NOW())
ON CONFLICT (provider_code) DO UPDATE SET
    provider_name = EXCLUDED.provider_name,
    priority = EXCLUDED.priority,
    status = EXCLUDED.status,
    updated_at = NOW();
