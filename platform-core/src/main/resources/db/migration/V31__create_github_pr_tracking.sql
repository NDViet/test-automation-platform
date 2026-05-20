-- Tracks the last-seen head SHA for each open GitHub PR per project.
-- Used by the polling service to detect only commit-driven changes, ignoring
-- noise events (comments, labels, CI reruns) that bump updated_at without
-- changing the code under analysis.
CREATE TABLE github_pr_tracking (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id       UUID         NOT NULL,
    repo_full_name   VARCHAR(200) NOT NULL,
    pr_number        INTEGER      NOT NULL,
    head_sha         VARCHAR(40)  NOT NULL,
    pr_url           VARCHAR(500),
    last_triggered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    triggered_workflow_id UUID,

    CONSTRAINT uq_github_pr_tracking UNIQUE (project_id, repo_full_name, pr_number)
);

CREATE INDEX idx_gpt_project ON github_pr_tracking (project_id);
-- Lets the polling service quickly mark stale records (closed/merged PRs) for cleanup
CREATE INDEX idx_gpt_last_triggered ON github_pr_tracking (last_triggered_at);
