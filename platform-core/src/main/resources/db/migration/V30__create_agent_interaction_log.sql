-- Captures agent inputs and outputs after human review for RAG / project learning.
-- Partitioned by month; old partitions exported to cold storage and dropped after 90 days.
-- Approved / edited outputs are indexed into OpenSearch for few-shot RAG retrieval.

CREATE TABLE agent_interaction_log (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    project_id          UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    session_id          UUID        NOT NULL,
    task_type           VARCHAR(50) NOT NULL,
    input_hash          VARCHAR(64),            -- SHA-256 of normalized input for dedup
    input_summary       TEXT        NOT NULL,   -- brief description of the prompt context
    output_blob_key     VARCHAR(500),           -- object storage key for large outputs
    output_blob_hash    VARCHAR(64),
    output_preview      VARCHAR(500),           -- first 500 chars for quick display
    human_decision      VARCHAR(20),            -- APPROVED, REJECTED, EDIT
    approved_blob_key   VARCHAR(500),           -- object storage key for human-corrected output
    quality_rating      SMALLINT    CHECK (quality_rating BETWEEN 1 AND 5),
    indexed_in_os       BOOLEAN     NOT NULL DEFAULT false,
    input_tokens        INT         NOT NULL DEFAULT 0,
    output_tokens       INT         NOT NULL DEFAULT 0,
    cost_cents          DECIMAL(8,4) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Create initial partition for current and next month
CREATE TABLE agent_interaction_log_2026_05
    PARTITION OF agent_interaction_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE agent_interaction_log_2026_06
    PARTITION OF agent_interaction_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE INDEX idx_ail_project_task   ON agent_interaction_log(project_id, task_type);
CREATE INDEX idx_ail_not_indexed    ON agent_interaction_log(project_id, created_at DESC)
    WHERE indexed_in_os = false AND human_decision IN ('APPROVED', 'EDIT');
