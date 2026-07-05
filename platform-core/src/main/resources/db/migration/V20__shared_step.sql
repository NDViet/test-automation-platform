-- Epic E: shared/reusable test steps. A case step may reference a shared step; the shared content is
-- resolved at read time, so editing the shared step updates every case that references it.
CREATE TABLE shared_step (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    action          TEXT        NOT NULL,
    expected_result TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shared_step_project ON shared_step (project_id);

ALTER TABLE test_case_steps ADD COLUMN shared_step_id UUID;
