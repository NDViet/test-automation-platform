CREATE TABLE test_case_results (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID         NOT NULL REFERENCES test_executions(id),
    test_id         VARCHAR(500) NOT NULL,
    display_name    VARCHAR(500),
    class_name      VARCHAR(300),
    method_name     VARCHAR(200),
    tags            TEXT[],
    status          VARCHAR(20)  NOT NULL,
    duration_ms     BIGINT,
    failure_message TEXT,
    stack_trace     TEXT,
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_tcr_execution_id ON test_case_results(execution_id);
CREATE INDEX idx_tcr_test_id       ON test_case_results(test_id);
CREATE INDEX idx_tcr_status        ON test_case_results(status);
CREATE INDEX idx_tcr_test_project  ON test_case_results(test_id, execution_id);
