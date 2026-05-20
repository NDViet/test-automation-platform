-- One row per node task execution within a workflow.
-- Tracks token usage, artifact manifest, and timing per step.

CREATE TABLE agent_workflow_steps (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id         UUID        NOT NULL REFERENCES agent_workflows(id) ON DELETE CASCADE,
    node_id             UUID        NOT NULL,
    node_type           VARCHAR(30) NOT NULL,
    task_type           VARCHAR(50) NOT NULL,
    sequence_order      INT         NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING → RUNNING → COMPLETED | AWAITING_REVIEW | FAILED | PARTIAL
    input_tokens        INT         NOT NULL DEFAULT 0,
    output_tokens       INT         NOT NULL DEFAULT 0,
    cost_cents          DECIMAL(8,4) NOT NULL DEFAULT 0,
    artifact_manifest   JSONB,
    summary             TEXT,
    error_code          VARCHAR(50),
    error_message       TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wfs_workflow_id    ON agent_workflow_steps(workflow_id);
CREATE INDEX idx_wfs_status         ON agent_workflow_steps(workflow_id, status);
