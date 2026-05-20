-- Extends the knowledge graph schema.
-- platform_requirements already has parent_id, depth, path, traceability_edges handles all edge types.
-- This migration adds: version tracking on requirements, release/test-plan tables, token budgets.

-- 1. Version tracking on platform_requirements (enables RequirementChangeProcessor diff)
ALTER TABLE platform_requirements
    ADD COLUMN IF NOT EXISTS version_hash      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS prev_version_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS change_summary    TEXT;

-- 2. Releases (JIRA fixVersion, Linear cycle, manual milestone)
CREATE TABLE sot_releases (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name         TEXT        NOT NULL,
    release_type VARCHAR(20) NOT NULL DEFAULT 'VERSION', -- VERSION | SPRINT | MILESTONE
    external_id  TEXT,                                    -- JIRA fixVersion id, Linear cycle id
    target_date  DATE,
    state        VARCHAR(20) NOT NULL DEFAULT 'PLANNED', -- PLANNED | IN_PROGRESS | RELEASED | ARCHIVED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
CREATE INDEX idx_rel_project ON sot_releases(project_id, state);

-- 3. Which requirements are in scope for a release
CREATE TABLE sot_release_requirements (
    release_id     UUID NOT NULL REFERENCES sot_releases(id) ON DELETE CASCADE,
    requirement_id UUID NOT NULL REFERENCES platform_requirements(id) ON DELETE CASCADE,
    added_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (release_id, requirement_id)
);
CREATE INDEX idx_relreq_req ON sot_release_requirements(requirement_id);

-- 4. Test plans scoped to a release
CREATE TABLE sot_test_plans (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id     UUID          REFERENCES sot_releases(id) ON DELETE CASCADE,
    project_id     UUID          NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    state          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT', -- DRAFT | ACTIVE | CLOSED
    coverage_score NUMERIC(4,3),                            -- 0.000 – 1.000
    risk_level     VARCHAR(10),                             -- LOW | MEDIUM | HIGH | CRITICAL
    generated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_plan_release ON sot_test_plans(release_id);
CREATE INDEX idx_plan_project ON sot_test_plans(project_id);

-- 5. Items within a test plan
CREATE TABLE sot_test_plan_items (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID        NOT NULL REFERENCES sot_test_plans(id) ON DELETE CASCADE,
    test_case_id     UUID        NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    requirement_ids  UUID[]      NOT NULL DEFAULT '{}',
    execution_type   VARCHAR(20) NOT NULL DEFAULT 'MANUAL', -- AUTOMATED | MANUAL | EXPLORATORY
    priority         VARCHAR(20) NOT NULL DEFAULT 'SHOULD_RUN', -- MUST_RUN | SHOULD_RUN | NICE_TO_HAVE
    result           VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN', -- PASS | FAIL | BLOCKED | SKIPPED | NOT_RUN
    executed_at      TIMESTAMPTZ,
    UNIQUE (plan_id, test_case_id)
);
CREATE INDEX idx_item_plan ON sot_test_plan_items(plan_id, result);

-- 6. Monthly token budgets per project
CREATE TABLE agent_token_budgets (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    budget_month        VARCHAR(7)  NOT NULL,  -- "2026-05" (YYYY-MM)
    max_input_tokens    BIGINT      NOT NULL DEFAULT 10000000,  -- 10M default
    used_input_tokens   BIGINT      NOT NULL DEFAULT 0,
    max_output_tokens   BIGINT      NOT NULL DEFAULT 2000000,
    used_output_tokens  BIGINT      NOT NULL DEFAULT 0,
    max_cost_cents      NUMERIC(10,2) NOT NULL DEFAULT 5000.00, -- $50 default
    used_cost_cents     NUMERIC(10,2) NOT NULL DEFAULT 0,
    hard_limit          BOOLEAN     NOT NULL DEFAULT false, -- true = block when exceeded
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, budget_month)
);
CREATE INDEX idx_budget_project ON agent_token_budgets(project_id, budget_month);
