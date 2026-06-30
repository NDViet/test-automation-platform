# Plan — Agent Management & Task Assignment

Source: `spec/agent-management-spec.md`. Slices are **vertical** (domain → persistence → service →
controller → BFF → frontend per capability) and dependency-ordered. Each task is TDD-first
(RED → GREEN → regression → build), one commit per task. Checkpoints gate each phase.

## Ground truth from code exploration

- **Flyway** has a single baseline `V1__initial_schema.sql`; the next migration is **`V2__agent_management.sql`**.
- **Existing reusable pieces** (compose, don't duplicate): `AiSkill` + `AiSkillService`/`AiSkillController`
  (`/hub/projects/{projectId}/ai/skills`), `AiPromptTemplate` + `AiPromptTemplateService`
  (`SYSTEM`/`USER`, one default per kind, seed fallbacks), AI-Settings per-role model map
  (`ai.litellm.model.{standard,complex,summarizer}`), `AgentTaskType` enum.
- **Controllers**: platform-agent exposes `/hub/...`; `PortalAiController` proxies them under
  `/api/portal/ai/...` via `RestClient` (`aiClient`/`agentClient`), passing `X-Actor` on writes.
- **Generation entry**: `POST /hub/test-cases/{projectId}/generate` →
  `TestCaseGenerationController.generateTestCases` parses `GenerateTestCasesRequest`, creates
  `AgentWorkflow` + (new-flow) `AiGenerationRun`, assembles `ContextBundle`, calls
  `AgentWorkflowService.executeWorkflow`. The node (`TestCaseGenerationNode.execute`) loads
  `AiGenerationRun` by workflowId and assembles prompts from skills/overrides.
- **Scope reality**: `AiSkill` and `AiPromptTemplate` are **project-scoped only** today. Org-level
  agents that reference org-level templates/skills require an **additive `scope`/`scope_id`**
  column on both (Open Question #3 — proceed unless told to defer).
- **Model override gap**: `LangChainAgentRunner` resolves the model **by tier** (`resolveModelId`);
  `ContextBundle` carries `llmTier` but **no explicit model id**. An agent's `model_id` override
  needs a thin plumb-through (resolution writes the chosen model into the run/bundle path).
- **Context injection**: `DefaultContextAssembler` builds the bundle and (just refactored)
  `inferManualTasks` routes `generate_test_cases → [GENERATE_TEST_CASES]`. `context_config`
  toggles hook in here.
- **Resolution is pure** (no LLM) → fully unit-testable; it is the **heart** of the feature and
  ships first behind tests.

## Component dependency graph

```
                 ┌────────────────────────────┐
                 │  V2 migration + JPA domain  │  (agents, task_agent_assignments,
                 │  + scope cols on skills/tpl │   task_sub_types, ai_generation_runs.agent_id)
                 └──────────────┬─────────────┘
                                │
                 ┌──────────────▼─────────────┐
                 │   AgentResolutionService    │  explicit→project→org→seed; subtype default;
                 │   (+ EffectiveAgentConfig)  │  agent→effective prompt/model/context  ← PURE
                 └───────┬───────────────┬─────┘
          ┌──────────────┘               └───────────────┐
┌─────────▼──────────┐          ┌──────────────────▼─────────────┐
│ Agent CRUD slice   │          │ Task-assignment + subtype slice │
│ service→REST→BFF→UI│          │ service→REST→BFF→matrix UI      │
└─────────┬──────────┘          └──────────────────┬─────────────┘
          └───────────────┬───────────────────────┘
                ┌──────────▼───────────┐
                │ Execution wiring slice│  generate request (agentId/subType) → resolve →
                │ (GENERATE_TEST_CASES) │  node consumes config → run records agent → selector UI
                └──────────┬───────────┘
                ┌──────────▼───────────┐
                │ Roll-out slice        │  seed agents + subtypes for remaining task types
                └──────────────────────┘
```

## Vertical slicing rationale

- **Phase A** is the only "foundation" block (schema + pure resolver). It is a tracer bullet:
  domain + the decision logic + seeds, proven by unit tests **before** any UI exists.
- **Phase B** and **Phase C** are independent vertical slices (Agent CRUD vs Assignment) that both
  depend only on A — they can be built in either order / in parallel.
- **Phase D** is the first user-visible end-to-end value: pick a (functional/non-functional) agent
  in the Generate modal and see different output. It depends on A+B+C.
- **Phase E** is pure rollout (data/seeds), no new code shape.

## Checkpoints (human-verify gates)

- **CHECKPOINT A** — migration applies cleanly (Flyway), JPA boots, `AgentResolutionService`
  unit tests green (cascade + subtype + assembly). No UI yet.
- **CHECKPOINT B** — Agent CRUD works org+project via the Agents page; scope-ownership enforced.
- **CHECKPOINT C** — Assignment matrix sets/reverts defaults; `…/effective` returns correct source.
- **CHECKPOINT D** — Generate Test Cases with functional vs non-functional runs the assigned
  agents; `AiGenerationRun` records `agent_id`/`sub_type`; no-config path matches today.
- **CHECKPOINT E** — remaining task types have seed agents + subtypes; regression suite green;
  images build; browser smoke.

## Risks / watch-items

- **Scope migration** of `ai_skills`/`ai_prompt_templates` is the riskiest change (touches existing
  CRUD + resolution). Keep it strictly additive; default existing rows to `PROJECT`/their project.
- **Model-id plumb-through** crosses `platform-llm` boundary — keep the change minimal (pass the
  resolved model id alongside tier; do not refactor `LlmChatModelProvider`'s caching contract).
- **No live-LLM in CI** — all resolution/assembly tests are pure; execution-wiring tests assert
  request plumbing + run records, not model output.
- **Backwards compatibility** — unresolved agent ⇒ seed; existing generation with no selection
  must be byte-compatible with today's seed-prompt path (regression test).
