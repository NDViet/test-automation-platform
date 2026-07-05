-- Epic C: scheduled CI dispatch. A run schedule fires a GitHub Actions workflow_dispatch on an
-- interval; the poller selects enabled schedules whose next_run_at is due, dispatches, and advances
-- next_run_at. inputs holds the workflow_dispatch inputs as a JSON string.
CREATE TABLE run_schedules (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name               VARCHAR(200) NOT NULL,
    repo_full_name     VARCHAR(300) NOT NULL,
    workflow_id        BIGINT       NOT NULL,
    ref                VARCHAR(200) NOT NULL DEFAULT 'main',
    inputs             TEXT,
    interval_minutes   INT          NOT NULL,
    next_run_at        TIMESTAMPTZ,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    last_dispatched_at TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_run_schedules_due ON run_schedules (enabled, next_run_at);
CREATE INDEX idx_run_schedules_project ON run_schedules (project_id);
