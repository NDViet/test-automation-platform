-- V8 — Staging table for AI-generated test cases pending human review.
--
-- Generation now writes PROPOSED rows here instead of straight into platform_test_cases; a proposal
-- becomes a DRAFT test case only when the user accepts it. This is the foundation for per-case
-- accept / reject / refine before anything enters the catalog (see SPEC.md).
CREATE TABLE generated_test_case_proposals (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id            UUID NOT NULL REFERENCES agent_workflows(id) ON DELETE CASCADE,
    project_id             UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    ordinal                INT  NOT NULL,
    title                  TEXT NOT NULL,
    description            TEXT,
    preconditions          TEXT,
    expected_result        TEXT,
    priority               VARCHAR(20),
    -- Raw value the model returned (may not be a valid UUID); parsed on accept.
    source_requirement_id  VARCHAR(200),
    -- Ordered steps [{action, expectedResult, notes}] as JSON; expanded into test_case_steps on accept.
    steps_json             TEXT NOT NULL DEFAULT '[]',
    status                 VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
                              CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED')),
    -- Set on accept: the platform_test_cases row created from this proposal.
    accepted_test_case_id  UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workflow_id, ordinal)
);

CREATE INDEX idx_gtcp_workflow ON generated_test_case_proposals (workflow_id);
CREATE INDEX idx_gtcp_project_status ON generated_test_case_proposals (project_id, status);
