CREATE TABLE test_executions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id        VARCHAR(200) NOT NULL UNIQUE,
    project_id    UUID         NOT NULL REFERENCES projects(id),
    branch        VARCHAR(200),
    commit_sha    VARCHAR(40),
    environment   VARCHAR(50)  DEFAULT 'unknown',
    trigger_type  VARCHAR(20),
    source_format VARCHAR(30)  NOT NULL,
    ci_provider   VARCHAR(30),
    ci_run_url    VARCHAR(1000),
    total_tests   INT          NOT NULL DEFAULT 0,
    passed        INT          NOT NULL DEFAULT 0,
    failed        INT          NOT NULL DEFAULT 0,
    skipped       INT          NOT NULL DEFAULT 0,
    broken        INT          NOT NULL DEFAULT 0,
    duration_ms   BIGINT,
    executed_at   TIMESTAMP    NOT NULL,
    ingested_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_te_project_id   ON test_executions(project_id);
CREATE INDEX idx_te_branch        ON test_executions(branch);
CREATE INDEX idx_te_executed_at   ON test_executions(executed_at DESC);
