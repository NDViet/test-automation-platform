-- Epic A T7: link a failure cluster to its in-flight targeted retest run.
-- When that run's executions finish, the cluster auto-resolves (all pass) or reopens.
ALTER TABLE failure_cluster ADD COLUMN retest_run_id UUID;

CREATE INDEX idx_failure_cluster_retest_run ON failure_cluster (retest_run_id);
