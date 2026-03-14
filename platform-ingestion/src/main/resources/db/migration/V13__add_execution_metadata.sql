-- Execution metadata: parallel/sequential mode, thread count, and suite name
ALTER TABLE test_executions
    ADD COLUMN execution_mode VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN parallelism    INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN suite_name     VARCHAR(500) NOT NULL DEFAULT '';

CREATE INDEX idx_te_execution_mode ON test_executions(execution_mode);
CREATE INDEX idx_te_ci_provider    ON test_executions(ci_provider);
