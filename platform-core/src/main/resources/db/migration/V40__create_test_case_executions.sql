CREATE TABLE test_case_executions (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    test_run_id    UUID        NOT NULL REFERENCES test_runs(id) ON DELETE CASCADE,
    test_case_id   UUID        NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    actual_result  TEXT,
    notes          TEXT,
    executed_by    VARCHAR(200),
    executed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (test_run_id, test_case_id)
);
CREATE INDEX idx_tce_run ON test_case_executions(test_run_id);
CREATE INDEX idx_tce_run_status ON test_case_executions(test_run_id, status);
