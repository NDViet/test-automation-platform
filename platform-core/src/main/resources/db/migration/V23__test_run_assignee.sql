-- Run-level default assignee: new executions inherit it, and changing it fills any still-unassigned
-- executions in the run.
ALTER TABLE test_runs ADD COLUMN assignee_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
