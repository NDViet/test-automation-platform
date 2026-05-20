-- Five-tier traceability graph edges.
-- Covers cross-tier links (COVERED_BY, AUTOMATED_BY, RAN_IN, MONITORED_BY, FOUND_BY)
-- and same-tier links (PARENT_OF, LINKED_TO) with optional subtype.

CREATE TABLE platform_traceability_edges (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID            NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_id         UUID            NOT NULL,
    from_tier       VARCHAR(20)     NOT NULL,
    to_id           UUID            NOT NULL,
    to_tier         VARCHAR(20)     NOT NULL,
    edge_type       VARCHAR(30)     NOT NULL,
    link_subtype    VARCHAR(30),
    confidence      DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    metadata        JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_confidence CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_trace_from         ON platform_traceability_edges(project_id, from_id, from_tier);
CREATE INDEX idx_trace_to           ON platform_traceability_edges(project_id, to_id, to_tier);
CREATE INDEX idx_trace_type         ON platform_traceability_edges(project_id, edge_type);
CREATE UNIQUE INDEX idx_trace_unique
    ON platform_traceability_edges(project_id, from_id, to_id, edge_type)
    WHERE link_subtype IS NULL;
