# TODO — Interactive AI Test Case Generation

Track per-task. Mark `[x]` only after RED→GREEN→regression→build→commit. See `tasks/plan.md`.

## Checkpoint 0 — confirm before build
- [ ] C0: confirm migration strategy (per-slice V2..V5 vs single V2), resume transport
  (direct async, no new Kafka topic), and `ai_generation_runs` side table

## Phase A — Skills library
- [x] T1: AiSkill domain + repo + V2 migration + CRUD service (+ tests)
- [x] T2: Skills HTTP API + portal BFF proxy (+ tests)
- [x] T3: Skills frontend (AiSettingsPage CRUD)
- [x] CHECKPOINT A: skills CRUD end-to-end

## Phase B — Prompt templates
- [x] T4: AiPromptTemplate domain + repo + V3 migration + service w/ default resolution (+ tests)
- [x] T5: Prompt-template HTTP API + `/defaults` + portal BFF proxy (+ tests)
- [x] T6: Prompt-template frontend (AiSettingsPage CRUD)
- [x] CHECKPOINT B: skills + templates manageable

## Phase C — Rich generation request (one-shot)
- [x] T7: AiGenerationRun model + input-file upload + V4 migration + request validation (+ tests)
- [x] T8: Enriched generate endpoint + run persistence + portal proxy (back-compat) (+ tests)
- [x] T9: Prompt assembly in TestCaseGenerationNode (skills+prompts+freetext+files) (+ tests)
- [x] T10: Generate modal frontend (skills, prompts, free text, file attach)
- [x] CHECKPOINT C (code paths verified; full LLM run needs a LiteLLM API key): steerable one-shot generation works end-to-end

## Phase D — Clarifying questions (pause → answer → resume)
- [x] T11: NodeResult.awaitingInput + AWAITING_INPUT status + markAwaitingInput (+ tests)
- [x] T12: ask_user tool on node + orchestrator INPUT_SENTINEL (+ tests)
- [x] T13: GenerationClarification + V5 migration + workflow AWAITING_INPUT handling (+ tests)
- [x] T14: Real checkpoint resume in LangChainAgentRunner (HIGH RISK — spike first) (+ tests)
- [x] T15: GenerationResumeService + answers endpoint (multi-round, cap, 409) (+ tests)
- [x] T16: Generation status endpoint (status + questions + transcript) + proxy (+ tests)
- [x] T17: Interactive run frontend (questions UI + transcript)
- [x] CHECKPOINT D (endpoints + logic verified; live loop needs LiteLLM key): full interactive loop end-to-end

## Phase E — Verification & rollout
- [x] T18: Regression + full backend test suite green
- [x] T19: Build all jars + images, Flyway alignment, browser E2E
