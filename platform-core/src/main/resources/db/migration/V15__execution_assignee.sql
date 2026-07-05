-- Epic C: execution ownership. An execution can be assigned to a user (distinct from the free-text
-- executed_by audit string), powering a per-tester "My Queue".
ALTER TABLE test_case_executions
    ADD COLUMN assignee_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_tce_assignee ON test_case_executions (assignee_user_id);
