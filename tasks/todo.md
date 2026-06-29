# TODO — Agent Management & Task Assignment

Source spec: `spec/agent-management-spec.md` · Plan: `tasks/plan.md`.
Convention: TDD per task (RED → GREEN → regression → build), one commit per task. `[ ]` open · `[x]` done.

---

## Phase A — Foundation (schema + pure resolver)

- [x] **T1: Flyway `V2__agent_management.sql` + JPA domain**
  - Create tables `agents`, `task_agent_assignments`, `task_sub_types`; add `agent_id UUID NULL`,
    `task_sub_type TEXT NULL` to `ai_generation_runs`; add `scope`/`scope_id` to `ai_skills` &
    `ai_prompt_templates` (default existing rows → `PROJECT` + their `project_id`).
  - JPA entities `Agent`, `TaskAgentAssignment`, `TaskSubType` + repositories.
  - **AC:** migration applies on a clean DB and on a DB seeded with existing project skills/templates
    (existing rows get `PROJECT` scope); entities load; unique constraints enforced
    (`agents`(scope,scope_id,name), `task_agent_assignments`(scope,scope_id,task_type,sub_type)).
  - **Verify:** `mvn -pl platform-core -am test`; boot platform-agent against the dev DB (Flyway log
    shows V2 applied, no validation error); `\d agents` shows columns.

- [x] **T2: Seed data — built-in default agents + sub-type catalog**
  - Seed one built-in default `Agent` per supported `AgentTaskType` (system seed prompt, model role,
    conservative context). Seed `task_sub_types`: `GENERATE_TEST_CASES` → FUNCTIONAL(default),
    NON_FUNCTIONAL; all other tasks → single `DEFAULT`.
  - Seeds are non-deletable resolution fallbacks (flag column or well-known ids).
  - **AC:** after migrate+seed, every supported task resolves to a seed agent with no user data;
    seed rows cannot be deleted via the CRUD API (returns 409/forbidden).
  - **Verify:** unit test asserts a seed agent exists for each task; delete-seed test rejects.

- [x] **T3: `AgentResolutionService` (the heart) — cascade + assembly**
  - Implement: resolve agent for (projectId, taskType, subType, explicitAgentId?) →
    explicit → project assignment → org assignment → seed; subType defaulting; produce
    `EffectiveAgentConfig` (composed system prompt = persona + template/seed + skills; user prompt;
    model id via model_id→role→tier; context toggles; maxRounds). Pure, no LLM, no I/O beyond repos.
  - **AC:** unit tests cover all four cascade branches, subtype default fallback, name-shadowing
    (project shadows org), prompt composition **order**, model override precedence
    (`model_id` > `model_role` > task tier), and out-of-scope reference rejection.
  - **Verify:** `mvn -pl platform-agent -am -Dtest=AgentResolutionServiceTest test` green.

- [x] **CHECKPOINT A** — migration applies (transactional dry-run); JPA compiles; resolver tests green (6/6); no UI yet.

---

## Phase B — Agent CRUD slice (vertical: service → REST → BFF → UI)

- [x] **T4: `AgentService` + `AgentController` (org & project scope)**
  - CRUD with scope-ownership guards: an agent may only reference templates/skills visible in its
    scope (project sees project ∪ inherited org; org sees org-only). Name unique per scope.
    Routes `/hub/{scope}/{scopeId}/ai/agents` (scope = `orgs`|`projects`) + effective:
    `GET /hub/projects/{projectId}/ai/agents/effective`.
  - **AC:** create/read/update/delete at both scopes; effective list = project ∪ org with project
    shadowing; referencing an out-of-scope template/skill → 400; deleting a seed → 409.
  - **Verify:** `AgentServiceTest` (CRUD + guards + effective merge) green; `curl` each route.

- [x] **T5: BFF proxy (`PortalAiController`) + frontend `api`/`types`**
  - Proxy the agent routes under `/api/portal/ai/...`; add `Agent`/`AgentForm`/`EffectiveAgent`
    types and `api.agents*` calls following `aiSkills*` patterns.
  - **AC:** portal forwards GET/POST/PUT/DELETE incl. `X-Actor`; effective endpoint reachable.
  - **Verify:** `curl http://localhost:8085/api/portal/ai/projects/{id}/agents/effective` → JSON.

- [x] **T6: Agents page (`/settings/agents`)**
  - Org/project scope toggle; list, create, edit, delete. Editor: persona, system/user template
    pickers (from existing templates), multi-select skills, model role/id, context toggles,
    max rounds, enable. Inherited org agents read-only + "clone to project".
  - **AC:** user can CRUD a project agent and clone an org agent; form validates required name;
    inherited agents are not editable in project scope.
  - **Verify:** `npx tsc -b`; manual browser CRUD against running stack.

- [x] **CHECKPOINT B** — Agent CRUD works org+project; scope-ownership enforced.

---

## Phase C — Task assignment slice (vertical: service → REST → BFF → matrix UI)

- [x] **T7: `TaskAgentService` + `TaskAgentController` (+ subtype catalog endpoint)**
  - Assignment upsert/delete per (scope, task, subType→agent); `GET /hub/ai/task-subtypes`;
    `GET /hub/projects/{projectId}/ai/task-agents/effective?taskType=&subType=` returns resolved
    agent + source (`PROJECT`/`ORG`/`SEED`).
  - **AC:** set a project default for (GENERATE_TEST_CASES, NON_FUNCTIONAL); effective resolves to it;
    delete reverts to org/seed with correct source flag; cannot bind an agent the scope can't see.
  - **Verify:** `TaskAgentServiceTest` (upsert/revert/cascade/source) green.

- [x] **T8: BFF proxy + assignment matrix UI (`/settings/task-agents`)**
  - Proxy routes; matrix of task types (grouped by flow) × sub-types → agent select, with
    inherited/seed shown as placeholder; org/project scope toggle.
  - **AC:** user assigns/reverts a default per (task, subtype); inherited values visible; saves persist.
  - **Verify:** `npx tsc -b`; browser: set + reload shows persisted default.

- [x] **CHECKPOINT C** — matrix sets/reverts defaults; `…/effective` returns correct source.

---

## Phase D — Execution wiring for GENERATE_TEST_CASES (first end-to-end value)

- [x] **T9: Request + resolution plumb-through**
  - `GenerateTestCasesRequest` (+ controller parse) gain `agentId?`, `subType?`. Controller calls
    `AgentResolutionService`, persists `agent_id`/`task_sub_type` on `AiGenerationRun`, and passes
    `EffectiveAgentConfig` into the run path (side table keyed by workflowId, like today's run inputs).
  - **AC:** request with/without `agentId`/`subType` both work; run row records the resolved agent
    + subtype; absent selection resolves the default (project→org→seed).
  - **Verify:** `TestCaseGenerationControllerTest` asserts plumbing + run fields; contract test
    accepts missing fields (back-compat).

- [x] **T10: Node consumes `EffectiveAgentConfig` + model override**
  - `TestCaseGenerationNode` builds its prompt from the resolved config (persona+template+skills)
    instead of re-deriving; `DefaultContextAssembler` honours `context_config`; `LangChainAgentRunner`
    uses the agent's `model_id` when present (minimal plumb alongside tier).
  - **AC:** functional vs non-functional configs produce **different** system prompts/model; the
    node still persists DRAFT test cases; **no-selection output matches today's seed-prompt path**
    (regression test).
  - **Verify:** unit test diffs assembled prompt per subtype; regression test vs current seed output.

- [x] **T11: Execution selector UI (Generate modal)**
  - Add Agent + Sub-type selectors pre-filled with the resolved default; functional/non-functional
    appear as sub-types. Reuse the live-stream progress UI.
  - **AC:** selectors default correctly; changing subtype updates the default agent; submit passes
    `agentId`/`subType`; live progress unaffected.
  - **Verify:** `npx tsc -b`; browser: run functional vs non-functional, confirm distinct results.

- [x] **CHECKPOINT D** — functional/non-functional run their agents; run records agent; no-config
  path unchanged; verified in browser end-to-end.

---

## Phase E — Roll-out & verification

- [~] **T12: wire applicable executors (PARTIAL — automation done; ai-side deferred)**
  - DONE: `GENERATE_AUTOMATION_CODE` resolves an agent (explicit→assignment→seed), records it on
    a run, and the node applies the model override (keeping its specialized GitHub-tool prompt).
    Controller accepts `agentId`/`subType`. Unit test added.
  - DEFERRED (needs a cross-module design decision, not in spec): **failure analysis / fixing run in
    `platform-ai` (`LiteLlmAnalysisClient`)**, not the agent workflow, so they cannot reuse
    `AgentResolutionService` (in `platform-agent`) without an HTTP call or duplicated resolver.
    Flagged for the user; out of scope for the "automation-only" wiring.
  - **Verify:** `TestCaseGenerationControllerTest.automationResolvesAgentAndRecordsRun` green;
    browser smoke deferred to deploy.

- [~] **T13: Full regression + build + RBAC pass (RBAC done; deploy/build pending)**
  - DONE (ON HOLD) — **RBAC**: `AgentRbacGuard` gates agent + task-agent writes; logic + 8 tests
    ready, but **gated by `agent.rbac.enabled` (default false)** so it does NOT enforce yet. Enable
    once platform-wide RBAC exists (`AGENT_RBAC_ENABLED=true`): ORG → ORG_ADMIN, PROJECT →
    ORG_ADMIN or TEAM_ADMIN of a team in the project. X-Actor wired end-to-end already.
  - PENDING — deploy: apply V2–V4 via Flyway + build all jars/images; browser auth checks.
  - **Verify:** `mvn -pl platform-agent,platform-core,platform-portal -am test`; image build;
    browser auth checks.

- [ ] **CHECKPOINT E** — rollout complete; regression green; images build; browser smoke passes.

---

## Resolved decisions (defaults accepted 2026-06-29)
1. **RBAC** — org-admin CRUDs org agents/assignments; project-manager CRUDs project ones (T13).
2. **Agent versioning** — snapshot-on-run only; no version history table (T1/T9).
3. **Scope migration** — add `scope`/`scope_id` to `ai_skills` & `ai_prompt_templates` now (T1).
4. **Cross-project clone** — org→project clone only in v1 (T6); project→project deferred.
