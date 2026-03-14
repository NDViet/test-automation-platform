CREATE TABLE issue_tracker_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id         VARCHAR(500) NOT NULL,
    project_id      UUID NOT NULL REFERENCES projects(id),
    tracker_type    VARCHAR(20) NOT NULL,
    issue_key       VARCHAR(50) NOT NULL,
    issue_url       VARCHAR(1000),
    issue_status    VARCHAR(50),
    issue_type      VARCHAR(50),
    linked_at       TIMESTAMP NOT NULL DEFAULT now(),
    last_synced_at  TIMESTAMP,
    CONSTRAINT uq_link_test_project_tracker UNIQUE (test_id, project_id, tracker_type)
);

CREATE INDEX idx_itl_test_id    ON issue_tracker_links(test_id);
CREATE INDEX idx_itl_project_id ON issue_tracker_links(project_id);
CREATE INDEX idx_itl_issue_key  ON issue_tracker_links(issue_key);
