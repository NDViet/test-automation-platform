-- Epic A: failure clustering. Groups recurring failures by a normalized signature so they can be
-- triaged, linked to one tracker defect, suppressed as a known issue, and retested.

CREATE TABLE failure_cluster (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source             VARCHAR(20) NOT NULL,                    -- AUTOMATED | MANUAL
    signature_hash     VARCHAR(64) NOT NULL,
    test_ref           VARCHAR(500),
    title              VARCHAR(500),
    sample_message     TEXT,
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',     -- OPEN | KNOWN_ISSUE | RESOLVED
    defect_external_id VARCHAR(64),
    defect_url         VARCHAR(500),
    defect_title       VARCHAR(500),
    defect_state       VARCHAR(60),
    occurrences        INT         NOT NULL DEFAULT 0,
    first_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_failure_cluster UNIQUE (project_id, signature_hash)
);
CREATE INDEX idx_failure_cluster_project_status ON failure_cluster(project_id, status);

CREATE TABLE failure_occurrence (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id   UUID        NOT NULL REFERENCES failure_cluster(id) ON DELETE CASCADE,
    execution_id UUID        NOT NULL,
    run_id       UUID,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_failure_occurrence UNIQUE (cluster_id, execution_id)
);
CREATE INDEX idx_failure_occurrence_cluster ON failure_occurrence(cluster_id);
