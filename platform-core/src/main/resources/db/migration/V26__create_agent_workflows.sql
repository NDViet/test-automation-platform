-- Agent workflow lifecycle — one row per end-to-end agentic session triggered by a webhook,
-- schedule, or manual request.

CREATE TABLE agent_workflows (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    trigger_type            VARCHAR(30) NOT NULL,   -- WEBHOOK, SCHEDULE, MANUAL, API_CALL
    trigger_source          VARCHAR(40),            -- IntegrationType name
    trigger_ref             JSONB       NOT NULL DEFAULT '{}',  -- TriggerRef fields
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING → RUNNING → AWAITING_REVIEW → COMPLETED | FAILED
    total_input_tokens      INT         NOT NULL DEFAULT 0,
    total_output_tokens     INT         NOT NULL DEFAULT 0,
    total_cost_cents        DECIMAL(10,4) NOT NULL DEFAULT 0,
    error_message           TEXT,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wf_project_id      ON agent_workflows(project_id);
CREATE INDEX idx_wf_status          ON agent_workflows(project_id, status);
CREATE INDEX idx_wf_created_at      ON agent_workflows(created_at DESC);
