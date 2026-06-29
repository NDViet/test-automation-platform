-- V2 — Agent Management & Task Assignment
--
-- Adds first-class AI "agents" (persona + referenced prompt templates + skills + model + injected
-- context), an org→project scope on the existing reusable artifacts, per-(task, sub-type) agent
-- assignments, a sub-type catalog, and agent attribution on generation runs.
--
-- Built-in default ("seed") agents are NOT rows here — they are code fallbacks in
-- AgentResolutionService (like AiPromptTemplateService's seed prompts), so this table holds only
-- org/project agents and there is no platform-scoped row to orphan or delete.

-- ── Org→project scope on existing reusable artifacts (additive, backfilled) ────────────────────
-- The owning column becomes (scope, scope_id). project_id is retained (now nullable) for the
-- existing project-scoped CRUD and FK cascade; org-scoped rows carry scope_id = org id, project_id
-- NULL. The original UNIQUE(project_id, …) constraints stay valid (NULLs are distinct in Postgres),
-- so they are left in place; new scope-based UNIQUE constraints enforce the real invariant.

ALTER TABLE ai_skills ADD COLUMN scope    VARCHAR(10) NOT NULL DEFAULT 'PROJECT';
ALTER TABLE ai_skills ADD COLUMN scope_id UUID;
UPDATE ai_skills SET scope_id = project_id WHERE scope_id IS NULL;
ALTER TABLE ai_skills ALTER COLUMN scope_id SET NOT NULL;
ALTER TABLE ai_skills ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE ai_skills ADD CONSTRAINT uq_ai_skills_scope_name UNIQUE (scope, scope_id, name);
CREATE INDEX idx_ai_skills_scope ON ai_skills(scope, scope_id);

ALTER TABLE ai_prompt_templates ADD COLUMN scope    VARCHAR(10) NOT NULL DEFAULT 'PROJECT';
ALTER TABLE ai_prompt_templates ADD COLUMN scope_id UUID;
UPDATE ai_prompt_templates SET scope_id = project_id WHERE scope_id IS NULL;
ALTER TABLE ai_prompt_templates ALTER COLUMN scope_id SET NOT NULL;
ALTER TABLE ai_prompt_templates ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE ai_prompt_templates
    ADD CONSTRAINT uq_ai_prompt_tpl_scope_kind_name UNIQUE (scope, scope_id, kind, name);
CREATE INDEX idx_ai_prompt_tpl_scope_kind ON ai_prompt_templates(scope, scope_id, kind);

-- ── Agents ─────────────────────────────────────────────────────────────────────────────────────
-- scope_id is polymorphic (organizations.id or projects.id) and validated in the application, so
-- no DB FK on it. Template references SET NULL on delete (agent falls back to the task seed prompt).
CREATE TABLE agents (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    scope              VARCHAR(10)  NOT NULL,            -- ORG | PROJECT
    scope_id           UUID         NOT NULL,
    name               TEXT         NOT NULL,
    description        TEXT,
    persona            TEXT,
    system_template_id UUID         REFERENCES ai_prompt_templates(id) ON DELETE SET NULL,
    user_template_id   UUID         REFERENCES ai_prompt_templates(id) ON DELETE SET NULL,
    skill_ids          TEXT,                             -- JSON array of ai_skills UUIDs
    model_role         VARCHAR(20),                      -- STANDARD | COMPLEX | SUMMARIZER
    model_id           TEXT,                             -- explicit LiteLLM model id (overrides role)
    context_config     JSONB,
    max_rounds         INT          NOT NULL DEFAULT 3,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by         VARCHAR(200),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_agents_scope_name UNIQUE (scope, scope_id, name)
);
CREATE INDEX idx_agents_scope ON agents(scope, scope_id);

-- ── Task sub-type catalog (e.g. GENERATE_TEST_CASES → FUNCTIONAL/NON_FUNCTIONAL) ────────────────
CREATE TABLE task_sub_types (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type  VARCHAR(60)  NOT NULL,                    -- AgentTaskType name
    key        VARCHAR(40)  NOT NULL,
    label      VARCHAR(100) NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_task_sub_types UNIQUE (task_type, key)
);
CREATE INDEX idx_task_sub_types_task ON task_sub_types(task_type);

-- ── Task → agent assignment (default agent per scope/task/sub-type) ─────────────────────────────
CREATE TABLE task_agent_assignments (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    scope      VARCHAR(10)  NOT NULL,                    -- ORG | PROJECT
    scope_id   UUID         NOT NULL,
    task_type  VARCHAR(60)  NOT NULL,
    sub_type   VARCHAR(40)  NOT NULL DEFAULT 'DEFAULT',
    agent_id   UUID         NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_task_agent_assign UNIQUE (scope, scope_id, task_type, sub_type)
);
CREATE INDEX idx_task_agent_assign_scope ON task_agent_assignments(scope, scope_id);

-- ── Agent attribution on generation runs (audit) ────────────────────────────────────────────────
ALTER TABLE ai_generation_runs ADD COLUMN agent_id      UUID;
ALTER TABLE ai_generation_runs ADD COLUMN task_sub_type VARCHAR(40);
