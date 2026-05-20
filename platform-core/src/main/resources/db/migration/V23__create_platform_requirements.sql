-- Canonical requirement storage — platform is the source of truth.
-- Synced from JIRA, Linear, GitHub Issues, etc. via IntegrationAdapters.
-- Uses ltree for fast ancestor/descendant queries on the hierarchy.

CREATE EXTENSION IF NOT EXISTS ltree;

CREATE TABLE platform_requirements (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    integration_config_id   UUID        REFERENCES project_integration_configs(id),
    external_id             VARCHAR(200),                    -- JIRA-123, LIN-456, etc.
    title                   TEXT        NOT NULL,
    description             TEXT,
    acceptance_criteria     JSONB       NOT NULL DEFAULT '[]', -- [{ref, text}]
    issue_type              VARCHAR(20) NOT NULL DEFAULT 'STORY', -- EPIC, STORY, TASK, SUBTASK, DEFECT, SPIKE
    status                  VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority                VARCHAR(20),
    labels                  TEXT[]      NOT NULL DEFAULT '{}',
    parent_id               UUID        REFERENCES platform_requirements(id),
    path                    LTREE,
    depth                   INT         NOT NULL DEFAULT 0,
    synced_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_req_project_id     ON platform_requirements(project_id);
CREATE INDEX idx_req_path_gist      ON platform_requirements USING GIST(path);
CREATE INDEX idx_req_external       ON platform_requirements(project_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_req_parent_id      ON platform_requirements(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_req_status         ON platform_requirements(project_id, status);
