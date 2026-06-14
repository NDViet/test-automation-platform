-- =====================================================================================
-- Test Automation Platform — consolidated initial schema (baseline).
--
-- This single baseline replaces the previous incremental migrations (former V1–V63).
-- It represents the final cumulative schema: all later ALTERs folded into their
-- CREATE TABLEs, the org-first hierarchy (Organization → Project → Team) applied,
-- JSONB column types applied, and one-time data backfills/seeds omitted (a fresh
-- deployment starts empty; create Organizations/Projects/Teams via the portal or API).
--
-- Existing databases are baselined at this version (Flyway baselineOnMigrate), so it is
-- NOT re-run against a populated schema. Fresh databases run it to create everything.
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS ltree;

-- =====================================================================================
-- Tenancy: Organization → Project → Team  (+ RBAC)
-- =====================================================================================

CREATE TABLE organizations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL UNIQUE,
    slug       VARCHAR(50)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id     UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL,
    repo_url   VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_org_slug UNIQUE (org_id, slug)
);
CREATE INDEX idx_projects_org_id ON projects(org_id);

CREATE TABLE teams (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_team_project_slug UNIQUE (project_id, slug)
);
CREATE INDEX idx_teams_project_id ON teams(project_id);

-- RBAC role assignments. team_id NULL = organization-wide role (ORG_ADMIN / VIEWER).
CREATE TABLE team_members (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    VARCHAR(200) NOT NULL,
    team_id    UUID         REFERENCES teams(id) ON DELETE CASCADE,  -- NULL = org-wide
    role       VARCHAR(20)  NOT NULL,
    granted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    granted_by VARCHAR(200),
    CONSTRAINT chk_tm_role CHECK (role IN ('ORG_ADMIN', 'TEAM_ADMIN', 'TEAM_MEMBER', 'VIEWER'))
);
CREATE UNIQUE INDEX uq_tm_user_team_role
    ON team_members(user_id, COALESCE(team_id, '00000000-0000-0000-0000-000000000000'), role);
CREATE INDEX idx_tm_user ON team_members(user_id);
CREATE INDEX idx_tm_team ON team_members(team_id);

-- =====================================================================================
-- Ingestion: executions & results
-- =====================================================================================

CREATE TABLE test_executions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id         VARCHAR(200) NOT NULL UNIQUE,
    project_id     UUID         NOT NULL REFERENCES projects(id),
    branch         VARCHAR(200),
    commit_sha     VARCHAR(40),
    environment    VARCHAR(50)  DEFAULT 'unknown',
    trigger_type   VARCHAR(20),
    source_format  VARCHAR(30)  NOT NULL,
    ci_provider    VARCHAR(30),
    ci_run_url     VARCHAR(1000),
    total_tests    INT          NOT NULL DEFAULT 0,
    passed         INT          NOT NULL DEFAULT 0,
    failed         INT          NOT NULL DEFAULT 0,
    skipped        INT          NOT NULL DEFAULT 0,
    broken         INT          NOT NULL DEFAULT 0,
    duration_ms    BIGINT,
    executed_at    TIMESTAMP    NOT NULL,
    ingested_at    TIMESTAMP    NOT NULL DEFAULT now(),
    execution_mode VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    parallelism    INTEGER      NOT NULL DEFAULT 0,
    suite_name     VARCHAR(500) NOT NULL DEFAULT '',
    test_type      VARCHAR(20)  NOT NULL DEFAULT 'FUNCTIONAL'
);
CREATE INDEX idx_te_project_id        ON test_executions(project_id);
CREATE INDEX idx_te_branch            ON test_executions(branch);
CREATE INDEX idx_te_executed_at       ON test_executions(executed_at DESC);
CREATE INDEX idx_te_execution_mode    ON test_executions(execution_mode);
CREATE INDEX idx_te_ci_provider       ON test_executions(ci_provider);
CREATE INDEX idx_te_test_type         ON test_executions(test_type);
CREATE INDEX idx_te_project_test_type ON test_executions(project_id, test_type);

CREATE TABLE test_case_results (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID         NOT NULL REFERENCES test_executions(id),
    test_id         VARCHAR(500) NOT NULL,
    display_name    VARCHAR(500),
    class_name      VARCHAR(300),
    method_name     VARCHAR(200),
    tags            JSONB,
    status          VARCHAR(20)  NOT NULL,
    duration_ms     BIGINT,
    failure_message TEXT,
    stack_trace     TEXT,
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_tcr_execution_id ON test_case_results(execution_id);
CREATE INDEX idx_tcr_test_id      ON test_case_results(test_id);
CREATE INDEX idx_tcr_status       ON test_case_results(status);
CREATE INDEX idx_tcr_test_project ON test_case_results(test_id, execution_id);

CREATE TABLE flakiness_scores (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id         VARCHAR(500)   NOT NULL,
    project_id      UUID           NOT NULL REFERENCES projects(id),
    score           DECIMAL(5, 4)  NOT NULL,
    classification  VARCHAR(20)    NOT NULL,
    total_runs      INT            NOT NULL,
    failure_count   INT            NOT NULL,
    failure_rate    DECIMAL(5, 4)  NOT NULL,
    last_failed_at  TIMESTAMP,
    last_passed_at  TIMESTAMP,
    computed_at     TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT uq_flakiness_test_project UNIQUE (test_id, project_id)
);
CREATE INDEX idx_flakiness_project_score ON flakiness_scores(project_id, score DESC);
CREATE INDEX idx_flakiness_score         ON flakiness_scores(score DESC);

CREATE TABLE performance_metrics (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    execution_id        UUID        NOT NULL,
    avg_ms              DOUBLE PRECISION,
    min_ms              DOUBLE PRECISION,
    median_ms           DOUBLE PRECISION,
    max_ms              DOUBLE PRECISION,
    p90_ms              DOUBLE PRECISION,
    p95_ms              DOUBLE PRECISION,
    p99_ms              DOUBLE PRECISION,
    requests_total      BIGINT,
    requests_per_second DOUBLE PRECISION,
    error_rate          DOUBLE PRECISION,
    vus_max             INTEGER,
    duration_ms         BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_performance_metrics PRIMARY KEY (id),
    CONSTRAINT fk_pm_execution FOREIGN KEY (execution_id) REFERENCES test_executions(id) ON DELETE CASCADE,
    CONSTRAINT uq_pm_execution UNIQUE (execution_id)
);
CREATE INDEX idx_pm_execution_id ON performance_metrics(execution_id);

-- =====================================================================================
-- Failure analysis (AI)
-- =====================================================================================

CREATE TABLE failure_analyses (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id              VARCHAR(500)     NOT NULL,
    project_id           UUID             NOT NULL REFERENCES projects(id),
    test_case_result_id  UUID             REFERENCES test_case_results(id),
    category             VARCHAR(30)      NOT NULL,
    confidence           DOUBLE PRECISION NOT NULL,
    root_cause           VARCHAR(500),
    detailed_analysis    TEXT,
    suggested_fix        TEXT,
    is_flaky_candidate   BOOLEAN          NOT NULL DEFAULT false,
    affected_component   VARCHAR(200),
    model_version        VARCHAR(50),
    analysed_at          TIMESTAMP        NOT NULL DEFAULT now(),
    input_tokens         INTEGER          NOT NULL DEFAULT 0,
    output_tokens        INTEGER          NOT NULL DEFAULT 0,
    analysis_status      VARCHAR(20)      NOT NULL DEFAULT 'SUCCESS',
    error_message        TEXT
);
CREATE INDEX idx_fa_test_id       ON failure_analyses(test_id);
CREATE INDEX idx_fa_project_id    ON failure_analyses(project_id);
CREATE INDEX idx_fa_category      ON failure_analyses(category);
CREATE INDEX idx_fa_analysed_at   ON failure_analyses(analysed_at);
CREATE INDEX idx_fa_model_version ON failure_analyses(model_version);
CREATE INDEX idx_fa_status        ON failure_analyses(analysis_status);

-- =====================================================================================
-- Alerting, API keys, audit
-- =====================================================================================

CREATE TABLE alert_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name   VARCHAR(200) NOT NULL,
    severity    VARCHAR(20)  NOT NULL,
    message     TEXT         NOT NULL,
    team_id     VARCHAR(200),
    project_id  VARCHAR(200),
    run_id      VARCHAR(200),
    channels    VARCHAR(200),
    delivered   BOOLEAN      NOT NULL DEFAULT false,
    fired_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_ah_project_id ON alert_history(project_id);
CREATE INDEX idx_ah_fired_at   ON alert_history(fired_at);
CREATE INDEX idx_ah_severity   ON alert_history(severity);

CREATE TABLE api_keys (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200)  NOT NULL,
    key_hash      VARCHAR(64)   NOT NULL UNIQUE,
    key_prefix    VARCHAR(10)   NOT NULL,
    team_id       UUID          NOT NULL REFERENCES teams(id),
    revoked       BOOLEAN       NOT NULL DEFAULT false,
    expires_at    TIMESTAMP,
    last_used_at  TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_ak_key_hash ON api_keys(key_hash);
CREATE        INDEX idx_ak_team_id  ON api_keys(team_id);

CREATE TABLE audit_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type        VARCHAR(50)  NOT NULL,
    actor_key_id      UUID         REFERENCES api_keys(id),
    actor_key_prefix  VARCHAR(10),
    team_id           UUID         REFERENCES teams(id),
    resource_type     VARCHAR(50),
    resource_id       VARCHAR(500),
    details           TEXT,
    client_ip         VARCHAR(50),
    outcome           VARCHAR(20),
    occurred_at       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_ae_event_type   ON audit_events(event_type);
CREATE INDEX idx_ae_actor_key_id ON audit_events(actor_key_id);
CREATE INDEX idx_ae_occurred_at  ON audit_events(occurred_at);
CREATE INDEX idx_ae_team_id      ON audit_events(team_id);

-- =====================================================================================
-- Platform settings (global key/value) + seeded AI defaults
-- =====================================================================================

CREATE TABLE platform_settings (
    key         VARCHAR(200) PRIMARY KEY,
    value       TEXT,
    description VARCHAR(500),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO platform_settings (key, value, description) VALUES
    ('ai.enabled',  'false',             'Enable AI-powered failure analysis'),
    ('ai.provider', 'anthropic',         'AI provider: anthropic or openai'),
    ('ai.model',    'claude-sonnet-4-6', 'Model name for the selected provider'),
    ('ai.api-key',  '',                  'API key for the selected AI provider (stored encrypted in prod)')
ON CONFLICT (key) DO NOTHING;

-- Per-team / per-project setting overrides (SettingResolver deep-merges PROJECT > TEAM > ORG).
CREATE TABLE scoped_settings (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    scope      VARCHAR(10)  NOT NULL,           -- TEAM, PROJECT
    scope_id   UUID         NOT NULL,
    key        VARCHAR(200) NOT NULL,
    value      TEXT,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_ss_scope CHECK (scope IN ('TEAM', 'PROJECT')),
    CONSTRAINT uq_ss_scope_key UNIQUE (scope, scope_id, key)
);
CREATE INDEX idx_ss_lookup ON scoped_settings(scope, scope_id, key);

-- =====================================================================================
-- Test Impact Analysis
-- =====================================================================================

CREATE TABLE test_coverage_mappings (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    test_case_id  TEXT        NOT NULL,
    class_name    TEXT        NOT NULL,
    method_name   TEXT,
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_coverage UNIQUE (project_id, test_case_id, class_name)
);
CREATE INDEX idx_coverage_project_class ON test_coverage_mappings(project_id, class_name);
CREATE INDEX idx_coverage_project_test  ON test_coverage_mappings(project_id, test_case_id);

CREATE TABLE tia_events (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    project_id        UUID        NOT NULL,
    changed_classes   INTEGER     NOT NULL DEFAULT 0,
    total_tests       INTEGER     NOT NULL DEFAULT 0,
    selected_tests    INTEGER     NOT NULL DEFAULT 0,
    uncovered_classes INTEGER     NOT NULL DEFAULT 0,
    reduction_pct     DOUBLE PRECISION,
    risk_level        VARCHAR(10) NOT NULL,
    branch            VARCHAR(500),
    triggered_by      VARCHAR(50) NOT NULL DEFAULT 'api',
    queried_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_tia_events PRIMARY KEY (id),
    CONSTRAINT fk_tia_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT chk_tia_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);
CREATE INDEX idx_tia_project_time ON tia_events(project_id, queried_at DESC);
CREATE INDEX idx_tia_risk_time    ON tia_events(risk_level, queried_at DESC);

CREATE TABLE impact_analyses (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id             UUID NOT NULL,
    name                   VARCHAR(255) NOT NULL DEFAULT 'Impact Analysis',
    status                 VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    linked_prs             JSONB NOT NULL DEFAULT '[]',
    linked_requirement_ids JSONB NOT NULL DEFAULT '[]',
    suggestions            JSONB,
    summary                TEXT,
    workflow_id            UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ia_project ON impact_analyses(project_id);

-- =====================================================================================
-- Integrations (legacy per-team) + per-project config + unified encrypted credentials
-- =====================================================================================

CREATE TABLE integration_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id         UUID NOT NULL REFERENCES teams(id),
    tracker_type    VARCHAR(20) NOT NULL,
    base_url        VARCHAR(500),
    project_key     VARCHAR(50),
    config_json     JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_team_tracker UNIQUE (team_id, tracker_type)
);
CREATE INDEX idx_ic_team_id ON integration_configs(team_id);

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

-- Per-project, per-tier integration behaviour (multiple per type allowed).
CREATE TABLE project_integration_configs (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    tier                VARCHAR(20)  NOT NULL,
    integration_type    VARCHAR(40)  NOT NULL,
    display_name        VARCHAR(100) NOT NULL,
    sync_direction      VARCHAR(15)  NOT NULL DEFAULT 'INBOUND',
    connection_params   JSONB        NOT NULL DEFAULT '{}',
    field_mappings      JSONB        NOT NULL DEFAULT '{}',
    filter_config       JSONB        NOT NULL DEFAULT '{}',
    enabled             BOOLEAN      NOT NULL DEFAULT true,
    last_synced_at      TIMESTAMPTZ,
    consecutive_errors  INT          NOT NULL DEFAULT 0,
    repo_type           VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_pic_project_id ON project_integration_configs(project_id);
CREATE INDEX idx_pic_tier       ON project_integration_configs(project_id, tier);
CREATE INDEX idx_pic_enabled    ON project_integration_configs(project_id, enabled);

-- Unified, scoped, encrypted credentials. Resolution: ORG -> TEAM -> PROJECT
-- (precedence PROJECT > TEAM > ORG). scope_id is the org/team/project id at each scope.
CREATE TABLE integration_credentials (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    scope              VARCHAR(10)  NOT NULL,
    scope_id           UUID         NOT NULL,
    integration_type   VARCHAR(40)  NOT NULL,
    display_name       VARCHAR(100) NOT NULL,
    base_url           VARCHAR(500),
    connection_params  JSONB        NOT NULL DEFAULT '{}',
    secret_ciphertext  TEXT,                                  -- v1:base64(iv||ct||tag), AES-256-GCM
    enabled            BOOLEAN      NOT NULL DEFAULT true,
    created_by         VARCHAR(200),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_intcred_scope    CHECK (scope IN ('ORG', 'TEAM', 'PROJECT')),
    CONSTRAINT chk_intcred_scope_id CHECK (scope_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_intcred_scoped ON integration_credentials(scope, scope_id, integration_type);
CREATE INDEX idx_intcred_lookup ON integration_credentials(integration_type, enabled);
CREATE INDEX idx_intcred_scope  ON integration_credentials(scope, scope_id);

-- Persisted mapping-rule overrides (Mapping Suggester). PROJECT -> ORG -> built-in default.
CREATE TABLE mapping_rulesets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope       VARCHAR(16)  NOT NULL CHECK (scope IN ('ORG', 'PROJECT')),
    scope_id    UUID         NOT NULL,
    rules_json  TEXT         NOT NULL,
    updated_by  VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_mapping_ruleset_scope ON mapping_rulesets(scope, scope_id);

-- Baseline of an upstream work-item-type schema, for drift detection.
CREATE TABLE integration_schema_snapshots (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id            UUID         NOT NULL,
    integration_type      VARCHAR(64)  NOT NULL,
    ado_project           VARCHAR(200) NOT NULL,
    work_item_type        VARCHAR(200) NOT NULL,
    fields_json           TEXT         NOT NULL,
    state_categories_json TEXT         NOT NULL,
    fingerprint           VARCHAR(64)  NOT NULL,
    captured_by           VARCHAR(100),
    captured_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_schema_snapshot
    ON integration_schema_snapshots(project_id, integration_type, ado_project, work_item_type);

-- =====================================================================================
-- Requirements (source of truth), test cases (TCM), traceability
-- =====================================================================================

CREATE TABLE platform_requirements (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id            UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    integration_config_id UUID        REFERENCES project_integration_configs(id),
    external_id           VARCHAR(200),
    title                 TEXT        NOT NULL,
    description           TEXT,
    acceptance_criteria   JSONB       NOT NULL DEFAULT '[]',
    issue_type            VARCHAR(20) NOT NULL DEFAULT 'STORY',
    status                VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority              VARCHAR(20),
    labels                TEXT[]      NOT NULL DEFAULT '{}',
    parent_id             UUID        REFERENCES platform_requirements(id),
    path                  LTREE,
    depth                 INT         NOT NULL DEFAULT 0,
    version_hash          VARCHAR(64),
    prev_version_hash     VARCHAR(64),
    change_summary        TEXT,
    raw_upstream          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    area_path             VARCHAR(1000),
    iteration_path        VARCHAR(1000),
    assigned_to           VARCHAR(400),
    history_rev           INT,
    created_date          TIMESTAMPTZ,
    synced_at             TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_req_project_id ON platform_requirements(project_id);
CREATE INDEX idx_req_path_gist  ON platform_requirements USING GIST(path);
CREATE INDEX idx_req_parent_id  ON platform_requirements(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_req_status     ON platform_requirements(project_id, status);
CREATE UNIQUE INDEX idx_req_external_unique
    ON platform_requirements(project_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_req_area         ON platform_requirements(project_id, area_path);
CREATE INDEX idx_req_iteration    ON platform_requirements(project_id, iteration_path);
CREATE INDEX idx_req_assignee     ON platform_requirements(project_id, assigned_to);
CREATE INDEX idx_req_created_date  ON platform_requirements(project_id, created_date DESC);

-- Hierarchical test plans / suites (tree + plan type).
CREATE TABLE test_suites (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    parent_id   UUID         REFERENCES test_suites(id) ON DELETE SET NULL,
    plan_type   VARCHAR(40),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ts_project        ON test_suites(project_id);
CREATE INDEX idx_ts_parent         ON test_suites(parent_id);
CREATE INDEX idx_ts_project_active ON test_suites(project_id, is_active);

CREATE TABLE platform_test_cases (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id                  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    integration_config_id       UUID        REFERENCES project_integration_configs(id),
    external_id                 VARCHAR(200),
    title                       TEXT        NOT NULL,
    ac_refs                     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    coverage_status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by                  VARCHAR(10) NOT NULL DEFAULT 'HUMAN',
    agent_session_id            UUID,
    last_result                 VARCHAR(20),
    last_executed_at            TIMESTAMPTZ,
    has_automation              BOOLEAN     NOT NULL DEFAULT false,
    suite_id                    UUID        REFERENCES test_suites(id) ON DELETE SET NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    priority                    VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    description                 TEXT,
    preconditions               TEXT,
    expected_result             TEXT,
    source_requirement_id       UUID        REFERENCES platform_requirements(id) ON DELETE SET NULL,
    automation_status           VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    automation_pr_url           TEXT,
    automation_github_config_id UUID,
    linked_requirement_ids      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    automation_workflow_id      UUID,
    last_updated_by_analysis_id UUID,
    updated_by                  VARCHAR(30) NOT NULL DEFAULT 'HUMAN',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tc_project_id      ON platform_test_cases(project_id);
CREATE INDEX idx_tc_coverage_status ON platform_test_cases(project_id, coverage_status);
CREATE INDEX idx_tc_external        ON platform_test_cases(project_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_tc_has_automation  ON platform_test_cases(project_id, has_automation);
CREATE INDEX idx_ptc_suite          ON platform_test_cases(project_id, suite_id);
CREATE INDEX idx_ptc_status         ON platform_test_cases(project_id, status);

CREATE TABLE test_case_steps (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id     UUID NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    step_number      INT  NOT NULL,
    action           TEXT NOT NULL,
    expected_result  TEXT,
    notes            TEXT,
    UNIQUE (test_case_id, step_number)
);
CREATE INDEX idx_tcs_test_case ON test_case_steps(test_case_id);

-- Kiwi-style parametrized testing: properties expand into one execution per combination.
CREATE TABLE test_case_properties (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id UUID         NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    value        VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tcp_case ON test_case_properties(test_case_id);

CREATE TABLE test_case_tags (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id UUID         NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tct_case_name UNIQUE (test_case_id, name)
);
CREATE INDEX idx_tct_case ON test_case_tags(test_case_id);
CREATE INDEX idx_tct_name ON test_case_tags(name);

CREATE TABLE platform_traceability_edges (
    id              UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID             NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_id         UUID             NOT NULL,
    from_tier       VARCHAR(20)      NOT NULL,
    to_id           UUID             NOT NULL,
    to_tier         VARCHAR(20)      NOT NULL,
    edge_type       VARCHAR(30)      NOT NULL,
    link_subtype    VARCHAR(30),
    confidence      DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    metadata        JSONB            NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT chk_confidence CHECK (confidence >= 0.0 AND confidence <= 1.0)
);
CREATE INDEX idx_trace_from ON platform_traceability_edges(project_id, from_id, from_tier);
CREATE INDEX idx_trace_to   ON platform_traceability_edges(project_id, to_id, to_tier);
CREATE INDEX idx_trace_type ON platform_traceability_edges(project_id, edge_type);
CREATE UNIQUE INDEX idx_trace_unique
    ON platform_traceability_edges(project_id, from_id, to_id, edge_type)
    WHERE link_subtype IS NULL;

-- =====================================================================================
-- Manual test runs / executions / environments
-- =====================================================================================

CREATE TABLE environments (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_env_project_name UNIQUE (project_id, name)
);
CREATE INDEX idx_env_project ON environments(project_id);

CREATE TABLE environment_properties (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id UUID         NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    value          VARCHAR(500) NOT NULL
);
CREATE INDEX idx_envprop_env ON environment_properties(environment_id);

CREATE TABLE test_runs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    release_version  VARCHAR(100),
    environment      VARCHAR(50)  NOT NULL DEFAULT 'STAGING',
    environment_id   UUID         REFERENCES environments(id) ON DELETE SET NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    triggered_by     VARCHAR(200),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tr_project        ON test_runs(project_id);
CREATE INDEX idx_tr_project_status ON test_runs(project_id, status);

CREATE TABLE test_case_executions (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    test_run_id    UUID        NOT NULL REFERENCES test_runs(id) ON DELETE CASCADE,
    test_case_id   UUID        NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    actual_result  TEXT,
    notes          TEXT,
    executed_by    VARCHAR(200),
    executed_at    TIMESTAMPTZ,
    property_combo VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tce_run        ON test_case_executions(test_run_id);
CREATE INDEX idx_tce_run_status ON test_case_executions(test_run_id, status);
CREATE UNIQUE INDEX uq_tce_run_case_combo
    ON test_case_executions(test_run_id, test_case_id, COALESCE(property_combo, ''));

CREATE TABLE test_execution_properties (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_execution_id UUID         NOT NULL REFERENCES test_case_executions(id) ON DELETE CASCADE,
    name                   VARCHAR(100) NOT NULL,
    value                  VARCHAR(500) NOT NULL
);
CREATE INDEX idx_tep_exec ON test_execution_properties(test_case_execution_id);

-- =====================================================================================
-- Releases / source-of-truth test plans / token budgets
-- =====================================================================================

CREATE TABLE sot_releases (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name         TEXT        NOT NULL,
    release_type VARCHAR(20) NOT NULL DEFAULT 'VERSION',
    external_id  TEXT,
    target_date  DATE,
    state        VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
CREATE INDEX idx_rel_project ON sot_releases(project_id, state);

CREATE TABLE sot_release_requirements (
    release_id     UUID NOT NULL REFERENCES sot_releases(id) ON DELETE CASCADE,
    requirement_id UUID NOT NULL REFERENCES platform_requirements(id) ON DELETE CASCADE,
    added_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (release_id, requirement_id)
);
CREATE INDEX idx_relreq_req ON sot_release_requirements(requirement_id);

CREATE TABLE sot_test_plans (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id     UUID         REFERENCES sot_releases(id) ON DELETE CASCADE,
    project_id     UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    state          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    coverage_score NUMERIC(4,3),
    risk_level     VARCHAR(10),
    generated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_plan_release ON sot_test_plans(release_id);
CREATE INDEX idx_plan_project ON sot_test_plans(project_id);

CREATE TABLE sot_test_plan_items (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID        NOT NULL REFERENCES sot_test_plans(id) ON DELETE CASCADE,
    test_case_id     UUID        NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    requirement_ids  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    execution_type   VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    priority         VARCHAR(20) NOT NULL DEFAULT 'SHOULD_RUN',
    result           VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN',
    executed_at      TIMESTAMPTZ,
    UNIQUE (plan_id, test_case_id)
);
CREATE INDEX idx_item_plan ON sot_test_plan_items(plan_id, result);

CREATE TABLE agent_token_budgets (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID          NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    budget_month        VARCHAR(7)    NOT NULL,
    max_input_tokens    BIGINT        NOT NULL DEFAULT 10000000,
    used_input_tokens   BIGINT        NOT NULL DEFAULT 0,
    max_output_tokens   BIGINT        NOT NULL DEFAULT 2000000,
    used_output_tokens  BIGINT        NOT NULL DEFAULT 0,
    max_cost_cents      NUMERIC(10,2) NOT NULL DEFAULT 5000.00,
    used_cost_cents     NUMERIC(10,2) NOT NULL DEFAULT 0,
    hard_limit          BOOLEAN       NOT NULL DEFAULT false,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (project_id, budget_month)
);
CREATE INDEX idx_budget_project ON agent_token_budgets(project_id, budget_month);

-- =====================================================================================
-- Agentic workflows
-- =====================================================================================

CREATE TABLE agent_workflows (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID          NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    trigger_type        VARCHAR(30)   NOT NULL,
    trigger_source      VARCHAR(40),
    trigger_ref         JSONB         NOT NULL DEFAULT '{}',
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    total_input_tokens  INT           NOT NULL DEFAULT 0,
    total_output_tokens INT           NOT NULL DEFAULT 0,
    total_cost_cents    DECIMAL(10,4) NOT NULL DEFAULT 0,
    error_message       TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_wf_project_id ON agent_workflows(project_id);
CREATE INDEX idx_wf_status     ON agent_workflows(project_id, status);
CREATE INDEX idx_wf_created_at ON agent_workflows(created_at DESC);

CREATE TABLE agent_workflow_steps (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id         UUID         NOT NULL REFERENCES agent_workflows(id) ON DELETE CASCADE,
    node_id             UUID         NOT NULL,
    node_type           VARCHAR(30)  NOT NULL,
    task_type           VARCHAR(50)  NOT NULL,
    sequence_order      INT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    input_tokens        INT          NOT NULL DEFAULT 0,
    output_tokens       INT          NOT NULL DEFAULT 0,
    cost_cents          DECIMAL(8,4) NOT NULL DEFAULT 0,
    artifact_manifest   JSONB,
    summary             TEXT,
    error_code          VARCHAR(50),
    error_message       TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wfs_workflow_id ON agent_workflow_steps(workflow_id);
CREATE INDEX idx_wfs_status      ON agent_workflow_steps(workflow_id, status);

CREATE TABLE agent_review_requests (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id         UUID        NOT NULL REFERENCES agent_workflows(id) ON DELETE CASCADE,
    step_id             UUID        NOT NULL REFERENCES agent_workflow_steps(id),
    channel             VARCHAR(20) NOT NULL,
    destination         TEXT        NOT NULL,
    artifact_manifest   JSONB,
    summary             TEXT,
    checkpoint_id       VARCHAR(200),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision            VARCHAR(20),
    decided_by          VARCHAR(200),
    decision_payload    TEXT,
    expires_at          TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '48 hours'),
    deferred_until      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at          TIMESTAMPTZ
);
CREATE INDEX idx_rev_workflow_id ON agent_review_requests(workflow_id);
CREATE INDEX idx_rev_status      ON agent_review_requests(status);
CREATE INDEX idx_rev_expires_at  ON agent_review_requests(expires_at) WHERE status = 'PENDING';

CREATE TABLE agent_checkpoints (
    id                  VARCHAR(200) PRIMARY KEY,
    session_id          UUID         NOT NULL,
    workflow_id         UUID         REFERENCES agent_workflows(id) ON DELETE SET NULL,
    strategy            VARCHAR(20)  NOT NULL,
    messages_blob_ref   JSONB,
    compressed_summary  TEXT,
    handoff_blob_ref    JSONB,
    cache_turn_indices  INT[],
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chk_session_id  ON agent_checkpoints(session_id);
CREATE INDEX idx_chk_workflow_id ON agent_checkpoints(workflow_id) WHERE workflow_id IS NOT NULL;
CREATE INDEX idx_chk_expires_at  ON agent_checkpoints(expires_at) WHERE expires_at IS NOT NULL;

-- Captures agent inputs/outputs after human review for RAG / project learning. Partitioned monthly.
CREATE TABLE agent_interaction_log (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    project_id          UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    session_id          UUID         NOT NULL,
    task_type           VARCHAR(50)  NOT NULL,
    input_hash          VARCHAR(64),
    input_summary       TEXT         NOT NULL,
    output_blob_key     VARCHAR(500),
    output_blob_hash    VARCHAR(64),
    output_preview      VARCHAR(500),
    human_decision      VARCHAR(20),
    approved_blob_key   VARCHAR(500),
    quality_rating      SMALLINT     CHECK (quality_rating BETWEEN 1 AND 5),
    indexed_in_os       BOOLEAN      NOT NULL DEFAULT false,
    input_tokens        INT          NOT NULL DEFAULT 0,
    output_tokens       INT          NOT NULL DEFAULT 0,
    cost_cents          DECIMAL(8,4) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE agent_interaction_log_2026_06
    PARTITION OF agent_interaction_log FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE agent_interaction_log_2026_07
    PARTITION OF agent_interaction_log FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE INDEX idx_ail_project_task ON agent_interaction_log(project_id, task_type);
CREATE INDEX idx_ail_not_indexed  ON agent_interaction_log(project_id, created_at DESC)
    WHERE indexed_in_os = false AND human_decision IN ('APPROVED', 'EDIT');

CREATE TABLE github_pr_tracking (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id            UUID         NOT NULL,
    repo_full_name        VARCHAR(200) NOT NULL,
    pr_number             INTEGER      NOT NULL,
    head_sha              VARCHAR(40)  NOT NULL,
    pr_url                VARCHAR(500),
    last_triggered_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    triggered_workflow_id UUID,
    CONSTRAINT uq_github_pr_tracking UNIQUE (project_id, repo_full_name, pr_number)
);
CREATE INDEX idx_gpt_project        ON github_pr_tracking(project_id);
CREATE INDEX idx_gpt_last_triggered ON github_pr_tracking(last_triggered_at);

-- =====================================================================================
-- Azure DevOps organizational structure + work-item history
-- =====================================================================================

CREATE TABLE ado_teams (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    ado_id            VARCHAR(100) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    default_area_path VARCHAR(1000),
    area_paths        JSONB NOT NULL DEFAULT '[]',
    member_count      INT NOT NULL DEFAULT 0,
    synced_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, ado_id)
);
CREATE INDEX idx_ado_teams_project ON ado_teams(project_id);

CREATE TABLE ado_areas (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    ado_id       VARCHAR(100),
    path         VARCHAR(1000) NOT NULL,
    name         VARCHAR(400) NOT NULL,
    parent_path  VARCHAR(1000),
    has_children BOOLEAN NOT NULL DEFAULT false,
    synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, path)
);
CREATE INDEX idx_ado_areas_project ON ado_areas(project_id);

CREATE TABLE ado_iterations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    ado_id       VARCHAR(100),
    path         VARCHAR(1000) NOT NULL,
    name         VARCHAR(400) NOT NULL,
    parent_path  VARCHAR(1000),
    start_date   DATE,
    finish_date  DATE,
    has_children BOOLEAN NOT NULL DEFAULT false,
    synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, path)
);
CREATE INDEX idx_ado_iterations_project ON ado_iterations(project_id);

CREATE TABLE ado_users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    unique_name        VARCHAR(400) NOT NULL,
    display_name       VARCHAR(400),
    email              VARCHAR(400),
    descriptor         VARCHAR(400),
    is_team_member     BOOLEAN NOT NULL DEFAULT false,
    seen_on_work_items BOOLEAN NOT NULL DEFAULT false,
    quality_role       VARCHAR(20),
    synced_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, unique_name)
);
CREATE INDEX idx_ado_users_project ON ado_users(project_id);
CREATE INDEX idx_ado_users_quality ON ado_users(project_id, quality_role) WHERE quality_role IS NOT NULL;

CREATE TABLE work_item_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    external_id   VARCHAR(200) NOT NULL,
    issue_type    VARCHAR(40),
    rev           INT NOT NULL,
    event_type    VARCHAR(30) NOT NULL,
    field         VARCHAR(120),
    from_value    TEXT,
    to_value      TEXT,
    from_category VARCHAR(30),
    to_category   VARCHAR(30),
    actor_name    VARCHAR(400),
    actor_unique  VARCHAR(400),
    revised_at    TIMESTAMPTZ,
    UNIQUE (project_id, external_id, rev, event_type, field)
);
CREATE INDEX idx_wie_project_actor ON work_item_events(project_id, actor_name);
CREATE INDEX idx_wie_project_ext   ON work_item_events(project_id, external_id);
CREATE INDEX idx_wie_revised       ON work_item_events(project_id, revised_at DESC);
CREATE INDEX idx_wie_to_category   ON work_item_events(project_id, to_category);

-- =====================================================================================
-- Views (knowledge graph / coverage)
-- =====================================================================================

CREATE VIEW sot_requirement_ancestry AS
WITH RECURSIVE ancestry AS (
    SELECT id, external_id, title, parent_id, depth,
           COALESCE(CAST(path AS TEXT), external_id) AS full_path
    FROM   platform_requirements
    WHERE  parent_id IS NULL
    UNION ALL
    SELECT r.id, r.external_id, r.title, r.parent_id, r.depth,
           a.full_path || '.' || COALESCE(r.external_id, r.id::TEXT)
    FROM   platform_requirements r
    JOIN   ancestry a ON a.id = r.parent_id
)
SELECT * FROM ancestry;

CREATE VIEW sot_test_plan_coverage_gaps AS
SELECT
    rr.release_id,
    r.project_id,
    r.id          AS requirement_id,
    r.external_id,
    r.title,
    r.issue_type,
    COUNT(e.id)   AS test_case_count
FROM sot_release_requirements rr
JOIN platform_requirements r ON r.id = rr.requirement_id
LEFT JOIN platform_traceability_edges e
       ON e.to_id     = r.id
      AND e.to_tier   = 'REQUIREMENT'
      AND e.edge_type = 'COVERED_BY'
GROUP BY rr.release_id, r.project_id, r.id, r.external_id, r.title, r.issue_type
HAVING COUNT(e.id) = 0;
