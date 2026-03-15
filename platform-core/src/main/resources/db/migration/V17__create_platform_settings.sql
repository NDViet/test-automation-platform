-- Platform-wide key-value settings (AI provider config, feature flags, etc.)
CREATE TABLE IF NOT EXISTS platform_settings (
    key         VARCHAR(200) PRIMARY KEY,
    value       TEXT,
    description VARCHAR(500),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default AI settings
INSERT INTO platform_settings (key, value, description) VALUES
    ('ai.enabled',   'false',         'Enable AI-powered failure analysis'),
    ('ai.provider',  'anthropic',     'AI provider: anthropic or openai'),
    ('ai.model',     'claude-sonnet-4-6', 'Model name for the selected provider'),
    ('ai.api-key',   '',              'API key for the selected AI provider (stored encrypted in prod)')
ON CONFLICT (key) DO NOTHING;
