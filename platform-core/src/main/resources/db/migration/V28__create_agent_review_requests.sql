-- Human-in-the-loop review requests produced by ReviewGateway.
-- One row per artifact set awaiting human decision (approve / reject / edit / defer).

CREATE TABLE agent_review_requests (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id         UUID        NOT NULL REFERENCES agent_workflows(id) ON DELETE CASCADE,
    step_id             UUID        NOT NULL REFERENCES agent_workflow_steps(id),
    channel             VARCHAR(20) NOT NULL,   -- SLACK, PORTAL, GITHUB_PR, AUTO
    destination         TEXT        NOT NULL,   -- Slack channel, portal project ID, repo
    artifact_manifest   JSONB,
    summary             TEXT,
    checkpoint_id       VARCHAR(200),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING → APPROVED | REJECTED | EDITED | DEFERRED | EXPIRED
    decision            VARCHAR(20),
    decided_by          VARCHAR(200),
    decision_payload    TEXT,
    expires_at          TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '48 hours'),
    deferred_until      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at          TIMESTAMPTZ
);

CREATE INDEX idx_rev_workflow_id    ON agent_review_requests(workflow_id);
CREATE INDEX idx_rev_status         ON agent_review_requests(status);
CREATE INDEX idx_rev_expires_at     ON agent_review_requests(expires_at) WHERE status = 'PENDING';
