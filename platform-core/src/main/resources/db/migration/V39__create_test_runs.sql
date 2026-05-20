CREATE TABLE test_runs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    release_version  VARCHAR(100),
    environment      VARCHAR(50)  NOT NULL DEFAULT 'STAGING',
    status           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    triggered_by     VARCHAR(200),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tr_project ON test_runs(project_id);
CREATE INDEX idx_tr_project_status ON test_runs(project_id, status);
