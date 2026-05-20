-- Per-project, per-tier integration config.
-- Replaces/extends the old per-team integration_configs for the agentic platform.
-- One row per (project, integration_type) combination; credentials in encrypted JSONB.

CREATE TABLE project_integration_configs (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    tier                VARCHAR(20) NOT NULL,               -- REQUIREMENT, TEST_CASE, AUTOMATED_TEST, EXECUTION, MONITOR
    integration_type    VARCHAR(40) NOT NULL,               -- IntegrationType enum value
    display_name        VARCHAR(100) NOT NULL,
    sync_direction      VARCHAR(15) NOT NULL DEFAULT 'INBOUND', -- INBOUND, OUTBOUND, BIDIRECTIONAL
    connection_params   JSONB       NOT NULL DEFAULT '{}',  -- base_url, project_key, workspace_id, etc.
    field_mappings      JSONB       NOT NULL DEFAULT '{}',  -- external field → platform field
    filter_config       JSONB       NOT NULL DEFAULT '{}',  -- JQL, suite IDs, path patterns, etc.
    enabled             BOOLEAN     NOT NULL DEFAULT true,
    last_synced_at      TIMESTAMPTZ,
    consecutive_errors  INT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pic_project_type UNIQUE (project_id, integration_type)
);

CREATE INDEX idx_pic_project_id   ON project_integration_configs(project_id);
CREATE INDEX idx_pic_tier         ON project_integration_configs(project_id, tier);
CREATE INDEX idx_pic_enabled      ON project_integration_configs(project_id, enabled);
