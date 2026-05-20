-- Canonical test case registry — created by agents or synced from TestRail/Xray/Qase.
-- Linked to requirements via platform_traceability_edges.

CREATE TABLE platform_test_cases (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    integration_config_id   UUID        REFERENCES project_integration_configs(id),
    external_id             VARCHAR(200),
    title                   TEXT        NOT NULL,
    ac_refs                 TEXT[]      NOT NULL DEFAULT '{}',
    coverage_status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, NEEDS_UPDATE, OBSOLETE, ARCHIVED
    created_by              VARCHAR(10) NOT NULL DEFAULT 'HUMAN',   -- AGENT, HUMAN
    agent_session_id        UUID,
    last_result             VARCHAR(20),
    last_executed_at        TIMESTAMPTZ,
    has_automation          BOOLEAN     NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tc_project_id      ON platform_test_cases(project_id);
CREATE INDEX idx_tc_coverage_status ON platform_test_cases(project_id, coverage_status);
CREATE INDEX idx_tc_external        ON platform_test_cases(project_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_tc_has_automation  ON platform_test_cases(project_id, has_automation);
