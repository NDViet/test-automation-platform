-- Epic B: enforcing release quality gates. Per-project thresholds; a release carries the evaluated
-- verdict and any audited override so the state->RELEASED transition can be blocked.

CREATE TABLE quality_gate (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID          NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    min_pass_rate           NUMERIC(4,3)  NOT NULL DEFAULT 0.900,  -- 0..1
    min_coverage_pct        NUMERIC(5,2)  NOT NULL DEFAULT 0,      -- 0..100
    max_open_blockers       INT           NOT NULL DEFAULT 0,
    max_critical_flaky      INT           NOT NULL DEFAULT 0,
    require_suites_executed BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_quality_gate_project UNIQUE (project_id)
);

-- Release-scoped gate verdict + audited override trail.
ALTER TABLE sot_releases ADD COLUMN gate_status         VARCHAR(20); -- PASS | FAIL | OVERRIDDEN
ALTER TABLE sot_releases ADD COLUMN gate_evaluated_at   TIMESTAMPTZ;
ALTER TABLE sot_releases ADD COLUMN gate_override_reason TEXT;
ALTER TABLE sot_releases ADD COLUMN gate_overridden_by  VARCHAR(255);
