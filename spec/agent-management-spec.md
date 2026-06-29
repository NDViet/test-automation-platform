# SPEC — Agent Management & Task Assignment

> Status: **DRAFT — awaiting confirmation**
> Scope: platform-agent (core), platform-core (domain/migrations), platform-portal (BFF + React)
> Author: (you) · Date: 2026-06-29

---

## 1. Objective

Let users **define reusable AI "agents"** — a named bundle of *persona + prompt(s) + skills + model + injected context* — and **assign them to platform tasks**, with sensible built-in defaults they can override. Before running an applicable task, a user can **pick which agent runs it**, including by task **sub-type** (e.g. *functional* vs *non-functional* test generation, each served by its own agent).

This consolidates today's scattered, per-run selection (skills + prompt overrides + model role chosen ad hoc in the generation modal) into a first-class, governable entity.

### Target users
- **QA leads / platform admins** — author org-wide agents and set task defaults once for all projects.
- **Project QA engineers** — override inherited agents per project, and pick an agent at execution time.

### Decisions locked (from clarification)
1. **Coverage:** all applicable `AgentTaskType`s (not just the 4 named flows).
2. **Scope:** **Org → Project** cascade (org library, project override).
3. **Composition:** agents **compose by reference** — they point at existing `AiPromptTemplate` + `AiSkill` records.
4. **Assignment:** **per (task, sub-type)** default agent, with **execution-time override**.

---

## 2. Background — what exists today (reuse, don't reinvent)

| Existing piece | Role today | How agents use it |
|---|---|---|
| `AiSkill` (project-scoped) | Reusable instruction snippets appended to the system prompt | Agent references a list of skill ids |
| `AiPromptTemplate` (project-scoped, `SYSTEM`/`USER`, one default per kind, built-in seeds) | The system/user prompt body | Agent references a system template id + user template id |
| AI Settings per-role model map (`analysis`/`standard`/`complex`/`summarizer`) | Model id per tier, via LiteLLM | Agent picks a model (by role or explicit id) |
| `AgentTaskType` enum | Granular task identifiers | Agent declares which task(s) it can serve; assignments key off it |
| `AiGenerationRun` (1:1 workflow) | Captures resolved skills/prompts/model for audit | Gains `agent_id` + `task_sub_type` columns |
| `DefaultContextAssembler` + nodes | Build `ContextBundle`, run the node | Resolve the chosen agent → feed its config into the bundle |

**Implication:** `AiSkill` and `AiPromptTemplate` are currently **project-scoped only**. To support org-level agents that reference org-level templates/skills, both gain an **Org→Project scope** (Section 9, migration). Project agents may reference project **or** inherited org templates/skills; org agents may reference only org-level ones.

---

## 3. Domain model

### 3.1 `Agent`
A reusable configuration. Scoped ORG or PROJECT.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `scope` | enum `ORG`\|`PROJECT` | |
| `scope_id` | UUID | org id or project id |
| `name` | text | unique per (scope, scope_id) |
| `description` | text | |
| `persona` | text | role/tone preamble, prepended to the system prompt |
| `system_template_id` | UUID? | → `AiPromptTemplate` (SYSTEM). Null ⇒ use task's seed |
| `user_template_id` | UUID? | → `AiPromptTemplate` (USER). Null ⇒ seed |
| `skill_ids` | JSON[UUID] | → `AiSkill[]`, appended in order |
| `model_role` | enum? | `STANDARD`\|`COMPLEX`\|`SUMMARIZER` — resolves to the configured model |
| `model_id` | text? | explicit LiteLLM model id; overrides `model_role` when set |
| `context_config` | JSONB | which platform data to inject (see 3.4) |
| `max_rounds` | int | clarifying-question rounds (0 disables) |
| `enabled` | bool | |
| `created_by`, `created_at`, `updated_at` | | audit |

Compatibility is via **assignments** (3.3), not a field on the agent — an agent is reusable across tasks. (Optional convenience: `applicable_task_types JSON[]` used only to filter the picker; not authoritative.)

### 3.2 `TaskSubType` (reference catalog, config-seeded)
Allowed sub-types **per task type**. Seeded built-ins, extensible later.

| Field | Type | Notes |
|---|---|---|
| `task_type` | `AgentTaskType` | |
| `key` | text | e.g. `FUNCTIONAL`, `NON_FUNCTIONAL`, `SECURITY`, `PERFORMANCE`, `ACCESSIBILITY` |
| `label` | text | display |
| `is_default` | bool | the sub-type chosen when the user doesn't pick |

Seed example — `GENERATE_TEST_CASES`: `FUNCTIONAL` (default), `NON_FUNCTIONAL`. Tasks with no meaningful sub-typing have a single implicit `DEFAULT` sub-type.

### 3.3 `TaskAgentAssignment`
Binds an agent as the default for a (task, sub-type) at a scope.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `scope` | `ORG`\|`PROJECT` | |
| `scope_id` | UUID | |
| `task_type` | `AgentTaskType` | |
| `sub_type` | text | `DEFAULT` when the task isn't sub-typed |
| `agent_id` | UUID | → `Agent` (must be visible in this scope) |
| `enabled` | bool | |

Unique on (`scope`, `scope_id`, `task_type`, `sub_type`).

### 3.4 `context_config` shape
A set of booleans/toggles controlling what `DefaultContextAssembler` injects — initial keys:
`requirements` (hierarchy + ACs), `executionHistory` (pass rate), `attachedFiles`, `prDiff`, `existingCoverage`. Unknown keys ignored; defaults conservative (only what the task needs).

---

## 4. Resolution semantics

### 4.1 Which agent runs a task
At execution, resolve in priority order:
1. **Explicit selection** — `agentId` passed in the execute request (must be visible to the project).
2. **Project assignment** for (taskType, subType).
3. **Org assignment** for (taskType, subType).
4. **Built-in seed agent** for the task (Section 8).

`subType` resolves to the request's value, else the task's `is_default` sub-type, else `DEFAULT`.

### 4.2 Agent → effective run config
Given the resolved agent, produce:
- **system prompt** = `persona` + (resolved `system_template_id` body **or** task seed) + each enabled `AiSkill.instructions`.
- **user prompt** = resolved `user_template_id` body **or** task seed (then the node layers task input: requirements, free text, files).
- **model** = `model_id` if set, else the configured model for `model_role`, else the task's existing default tier.
- **context** = `context_config` toggles → `ContextBundle` assembly.
- **max rounds** = `max_rounds`.

This replaces the ad-hoc assembly currently in `AiGenerationRun`/`TestCaseGenerationNode.assemblePrompt`; that path becomes "resolve agent → build run". `AiGenerationRun` records the chosen `agent_id` + `sub_type` for audit (prompts still snapshotted in `*_used`).

### 4.3 Effective views (for the UI)
- **Effective agents for a project** = project agents ∪ org agents (project name shadows org name on clash).
- **Effective assignment for (project, task, subType)** = the resolved default per 4.1 (2→3→4), with a flag indicating where it came from (`PROJECT`/`ORG`/`SEED`).

---

## 5. APIs (portal BFF → platform-agent)

Mirror the existing `AiSkill`/`AiPromptTemplate` controller style (`X-Actor` for writes; portal proxies via `RestClient`). All paths under `/api/portal/ai`.

### Agents
- `GET    /ai/{scope}/{scopeId}/agents` — list (scope = `orgs`|`projects`)
- `POST   /ai/{scope}/{scopeId}/agents`
- `PUT    /ai/{scope}/{scopeId}/agents/{id}`
- `DELETE /ai/{scope}/{scopeId}/agents/{id}`
- `GET    /ai/projects/{projectId}/agents/effective` — merged org+project list

### Task assignments
- `GET /ai/{scope}/{scopeId}/task-agents` — all bindings at this scope
- `PUT /ai/{scope}/{scopeId}/task-agents` — upsert one (task, subType → agent)
- `DELETE /ai/{scope}/{scopeId}/task-agents/{id}` — revert to inherited/seed
- `GET /ai/projects/{projectId}/task-agents/effective?taskType=&subType=` — resolved default + source

### Sub-type catalog
- `GET /ai/task-subtypes` (optionally `?taskType=`) — allowed sub-types per task

### Execution (extend existing triggers)
Applicable execute endpoints accept **optional** `agentId` and `subType`. First: test-case generation —
`POST /projects/{projectId}/test-cases/generate` body gains `agentId?`, `subType?`. Absent ⇒ resolve default. Same pattern later for automation generation, failure analysis, failure fixing.

---

## 6. Frontend (React, `platform-portal/frontend`)

1. **Agents page** (`/settings/agents`, org & project scope toggle): list, create, edit, delete. Editor composes — persona textarea, system/user template pickers (from existing templates), multi-select skills, model role/id, context toggles, max rounds, enable. Inherited org agents shown read-only with a "clone to project" action.
2. **Task assignment matrix** (`/settings/task-agents`): rows = task types (grouped by flow), columns/expanders = sub-types; each cell selects the default agent (with inherited/seed shown as placeholder). Org vs project scope toggle.
3. **Execution selector**: in the **Generate Test Cases** modal (and later other task launchers) add an **Agent** + **Sub-type** selector, pre-filled with the resolved default; "functional / non-functional" appear as sub-types for generation. Reuse the live-stream progress UI already in place.

Types in `src/lib/types.ts`, calls in `src/lib/api.ts`, following existing `aiSkills`/`aiPromptTemplates` patterns.

---

## 7. Integration with the workflow

- `GenerateTestCasesRequest` (+ controller) carry `agentId?`, `subType?`.
- A new `AgentResolutionService` (platform-agent) implements Section 4.1/4.2 → returns an `EffectiveAgentConfig`.
- `TestCaseGenerationController` resolves the agent, persists `agent_id`/`sub_type` on `AiGenerationRun`, and the node consumes `EffectiveAgentConfig` instead of re-deriving prompts/skills.
- `DefaultContextAssembler` honours `context_config`.
- Model selection: `LangChainAgentRunner`/`LlmChatModelProvider` accept the agent's `model_id` when present (today it resolves by tier only).

---

## 8. Defaults & seeding

- For **every** supported `AgentTaskType`, seed a **built-in default agent** (system seed prompt + sensible model role + default context) so the platform works before any user CRUD — matching "create the default, let user CRUD further."
- Seed the `TaskSubType` catalog (generation gets FUNCTIONAL/NON_FUNCTIONAL; everything else `DEFAULT`).
- Seeds are resolution fallbacks (4.1 step 4); they are **not** rows users can delete. Users create their own agents/assignments to override.

---

## 9. Migrations & impact

- **New tables:** `agents`, `task_agent_assignments`, `task_sub_types` (Flyway, `platform-core`).
- **Alter `ai_generation_runs`:** add `agent_id UUID NULL`, `task_sub_type TEXT NULL`.
- **Scope expansion for `ai_skills` & `ai_prompt_templates`:** add `scope`/`scope_id` (default existing rows → `PROJECT`/their `project_id`); resolution becomes project ∪ inherited org. Backwards compatible (existing project rows unchanged).
- No breaking change to current generation: when no agent is selected and no assignment exists, behaviour falls back to today's seed prompts.

---

## 10. Project structure (where code goes)

```
platform-core/
  src/main/java/com/platform/core/domain/Agent.java
  .../domain/TaskAgentAssignment.java
  .../domain/TaskSubType.java
  .../repository/{Agent,TaskAgentAssignment,TaskSubType}Repository.java
  src/main/resources/db/migration/V<n>__agents.sql
platform-agent/
  .../agent/agents/AgentService.java              # CRUD (org+project, cascade)
  .../agent/agents/TaskAgentService.java          # assignment CRUD + effective resolution
  .../agent/agents/AgentResolutionService.java    # §4 resolve + EffectiveAgentConfig
  .../agent/api/AgentController.java               # REST (agents)
  .../agent/api/TaskAgentController.java           # REST (assignments, subtypes)
platform-portal/
  .../portal/api/PortalAiController.java           # add agent/task-agent proxy routes
  frontend/src/pages/AgentsPage.tsx
  frontend/src/pages/TaskAgentsPage.tsx
  frontend/src/components/AgentSelect.tsx          # execution-time picker
```

---

## 11. Code style
- Match existing modules: constructor injection, `@Transactional` services, `ResponseStatusException` for 4xx, DTO records, `X-Actor` on writes.
- Frontend: TanStack Query + the existing `api`/`types` patterns; no new state libs.
- Keep agent **resolution** logic in one service (`AgentResolutionService`) — single source of truth, unit-testable without a live LLM.

---

## 12. Testing strategy
- **Unit:** `AgentResolutionService` cascade (explicit → project → org → seed), sub-type defaulting, agent→effective-config assembly (prompt composition order, model override). `AgentService`/`TaskAgentService` CRUD + scope-ownership guards (can't reference an out-of-scope template/skill/agent).
- **Contract:** execute endpoints accept/ignore `agentId`/`subType`; `AiGenerationRun` records them.
- **Regression:** with no agent selected and no assignments, generation output matches today's seed-prompt path.
- **Frontend:** type-check; smoke the Agents CRUD + assignment matrix + execution selector against a running backend.
- No live-LLM dependency in tests — resolution and assembly are pure.

---

## 13. Boundaries

**Always**
- Keep a working fallback: unresolved → seed agent; never block a task because no agent is configured.
- Enforce scope visibility: a binding/agent may only reference templates/skills/agents visible in its scope.
- Snapshot the resolved prompts on the run (audit/repro) even though agents are referenced by id.

**Ask first**
- Renaming/removing existing `AiSkill`/`AiPromptTemplate` APIs (the scope migration should be additive).
- Any change to default model roles or seed prompt wording (affects all projects).
- Expanding sub-types beyond functional/non-functional for generation.

**Never**
- Never overwrite the root `SPEC.md` or other features' specs.
- Never hard-delete seed agents/sub-types, or let a delete orphan an in-flight workflow.
- Never embed secrets/model keys in agents — models route through the existing LiteLLM settings.

---

## 14. Acceptance criteria (v1)
- **AC1** A user can CRUD agents at org and project scope; project agents shadow org agents by name.
- **AC2** A user can set a default agent per (task, sub-type) at org and project scope, and revert to inherited/seed.
- **AC3** `GET …/agents/effective` and `…/task-agents/effective` return the correctly-resolved cascade with a source flag.
- **AC4** Generate Test Cases shows an Agent + Sub-type selector defaulting to the resolved agent; functional vs non-functional run their assigned agents and produce visibly different output.
- **AC5** With nothing configured, generation falls back to the seed agent and behaves exactly as today.
- **AC6** `AiGenerationRun` records `agent_id` + `task_sub_type`; resolved prompts are snapshotted.
- **AC7** Scope-ownership is enforced (cannot bind an agent to a project that can't see it; cannot reference an out-of-scope template/skill).

---

## 15. Phased delivery
1. **Domain + migrations** (tables, scope columns, seeds) + `AgentResolutionService` with unit tests.
2. **Agent CRUD** (service + REST + portal proxy + Agents page).
3. **Task assignments** (service + REST + assignment matrix UI).
4. **Execution wiring** for `GENERATE_TEST_CASES` (request fields, resolution, node consumes config, run records agent) + execution selector UI + functional/non-functional sub-types.
5. **Roll out** to the remaining task types (automation, failure analysis, failure fixing, then the rest) by adding their seed agents + sub-types — no new code shape.

---

## 16. Resolved decisions (defaults accepted 2026-06-29)
- **RBAC:** org-admin CRUDs org-scoped agents/assignments; project-manager CRUDs project-scoped ones.
- **Versioning:** prompt-snapshot-on-run only; no agent version-history table in v1.
- **Template/skill scope migration:** add `scope`/`scope_id` to `ai_skills` & `ai_prompt_templates` now (additive; existing rows → `PROJECT`).
- **Cross-project clone:** org→project clone only in v1; project→project deferred.
