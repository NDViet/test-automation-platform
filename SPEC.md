# SPEC — Interactive AI Test Case Generation (Skills, Prompts, Input & Clarifying Questions)

> Status: **DRAFT — awaiting confirmation**
> Scope: `platform-core`, `platform-common`, `platform-agent`, `platform-llm`,
> `platform-portal` (+ frontend).
> Prior specs archived under `spec/archive/` (LiteLLM gateway, Manual Test
> Execution — both implemented).

## 1. Objective

### Problem
Today, AI test-case generation is a one-shot, fully-hardcoded flow. The user can
only pick `requirementIds`; the system prompt is hardcoded inside
`TestCaseGenerationNode`, there is no user prompt, no reusable instructions, and
no way for the agent to ask for missing context. It generates DRAFT
`PlatformTestCase` rows in a single Claude call and finishes. Quality is capped
by whatever the requirements happen to say.

### Goal
Make generation **steerable and interactive**:

1. **Skills** — a reusable, project-scoped library of named instruction sets
   (CRUD) the user can attach to a run to shape how the agent generates.
2. **Prompt templates** — project-level saved **system prompt** and **user
   prompt** templates with sensible defaults, **overridable per run**.
3. **Rich input** — generation input may combine **selected requirements**,
   **free-text**, and **file attachments** (stored in BlobStore), in any
   combination.
4. **Clarifying questions (pause → answer → resume)** — during generation the
   agent may pause and surface questions; the user answers in the portal; the run
   **resumes from checkpoint** and continues. Multiple rounds are supported until
   the agent has enough context, then it produces test cases.

### Target users
- **QA engineers / SDETs** authoring test cases who want to inject domain
  knowledge (skills), tailor prompts, and feed extra context (free text, files).
- **QA leads** who curate the project's reusable skill library and prompt
  templates so the whole team generates consistently.

### Acceptance criteria (feature-level)
- A user can create/read/update/delete **skills** scoped to a project and select
  zero or more for a generation run.
- A user can save and edit project **prompt templates** (system + user) and
  override either per run without mutating the saved template.
- A generation request accepts any combination of `requirementIds`, free-text
  `input`, attached `fileIds`, selected `skillIds`, and per-run prompt
  overrides; at least one input source must be present.
- When the agent needs clarification, the run enters **AWAITING_INPUT**, the
  questions are visible in the portal, and the run does not produce test cases
  until answered (or explicitly cancelled).
- After the user submits answers, the same run **resumes** (does not restart from
  scratch — prior conversation/context is preserved via checkpoint) and either
  asks again or completes with DRAFT test cases.
- Generated test cases remain DRAFT `PlatformTestCase` rows tied to the workflow,
  exactly as today (no change to downstream review/approval lifecycle).
- Skills, prompt overrides, attached files, and the full Q&A transcript are
  recorded against the workflow for auditability.

### Out of scope (this spec)
- Changing the DRAFT → UNDER_REVIEW → APPROVED lifecycle or the Review Queue.
- Streaming token output to the UI.
- Cross-project/global skill sharing or skill versioning history (project-scoped,
  last-write-wins for now).
- Generating automation code (separate flow).
- Editing ADO/work items (platform stays read-only on ADO).

## 2. Commands

Build/run uses the existing monorepo toolchain. **Backend jars are prebuilt and
COPYed into images**, so always `mvn package` before `docker compose build`.

```bash
# JDK 21 required
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

# Backend: build a module (+ deps) and run its tests
mvn -q -pl platform-agent -am package                 # build agent + deps
mvn -q -pl platform-agent -am test                    # run agent tests
mvn -q -pl platform-core,platform-agent,platform-portal -am package -DskipTests

# Frontend (platform-portal/frontend)
cd platform-portal/frontend
npm run format
npx tsc -b            # typecheck
npx vite build        # production build (bundled into platform-portal static)

# Containers: rebuild only what changed, then recreate
docker compose build platform-agent platform-portal
docker compose up -d --force-recreate platform-agent platform-portal

# Full consistency pass (all JVM services share Flyway version)
mvn -q package -DskipTests && docker compose build && docker compose up -d
```

Local verification: portal at `http://localhost:8085`, agent hub behind the
portal BFF (`/api/portal/...` → `/hub/...`). Browser checks via chrome-devtools
MCP.

## 3. Project structure

New and changed code lives in the existing modules. **Vertical slices** — each
capability spans domain → persistence → service → controller → portal BFF →
frontend.

### Backend — `platform-core` (domain + persistence + Flyway)
- `domain/AiSkill.java` — project-scoped skill: `id, projectId, name,
  description, instructions (TEXT), enabled, createdBy, createdAt, updatedAt`.
- `domain/AiPromptTemplate.java` — project-scoped template: `id, projectId,
  kind (SYSTEM|USER), name, body (TEXT), isDefault, updatedAt`.
- `domain/GenerationClarification.java` — one Q&A turn for a workflow:
  `id, workflowId, round, questionsJson (TEXT), answersJson (TEXT, nullable),
  status (PENDING|ANSWERED|SKIPPED), createdAt, answeredAt`.
- Extend `domain/AgentWorkflow.java` (or a new `GenerationRequest` side table)
  to persist the run's resolved inputs: `skillIdsJson, systemPromptUsed,
  userPromptUsed, freeTextInput, attachmentManifestJson`.
- `repository/` — `AiSkillRepository`, `AiPromptTemplateRepository`,
  `GenerationClarificationRepository` (Spring Data JPA).
- Reuse existing `PlatformTestCase`, `TestCaseStep`, `AgentWorkflow`,
  `AgentWorkflowStep`, `agent_checkpoints`, BlobStore (`ARTIFACTS` for uploaded
  input files; `CHECKPOINTS` for conversation state).
- `db/migration/` — **a single new forward migration** (e.g.
  `V2__ai_skills_prompts_clarifications.sql`) adding `ai_skills`,
  `ai_prompt_templates`, `generation_clarifications`, and the new columns on
  `agent_workflows`. (Product is brand-new; if V1 has not shipped to any
  non-resettable environment, fold into V1 instead — confirm at build time.
  Flyway version must stay aligned across the db-migrate image and all services.)

### Backend — `platform-agent` (orchestration + node)
- `node/impl/TestCaseGenerationNode.java` — **modify**: build the prompt from
  (resolved system template/override) + (skills) + (user template/override +
  free text + requirement context + file excerpts); expose an `ask_user` tool so
  the model can request clarification; on tool-call, return
  `NodeResult.awaitingInput(questions, checkpointId)` instead of completing.
- `node/AskUserTool` (or a tool spec within the node) — structured tool the model
  calls to emit questions: `[{ id, question, kind (TEXT|CHOICE), options? }]`.
- `workflow/AgentWorkflowService.java` — **modify**: handle the new
  `AWAITING_INPUT` workflow state; persist `GenerationClarification`; emit the
  Kafka `AWAITING_INPUT` event.
- `workflow/GenerationResumeService.java` — **new**: given workflowId + answers,
  rehydrate from checkpoint via `CheckpointService` + `orchestrator.resume(...)`,
  inject the answers as the next user turn, and continue the node loop.
- `api/TestCaseGenerationController.java` — **modify/extend**:
  - `POST /hub/test-cases/{projectId}/generate` — accept the richer request DTO.
  - `POST /hub/test-cases/{projectId}/generations/{workflowId}/answers` — submit
    clarification answers and resume.
  - `GET  /hub/test-cases/{projectId}/generations/{workflowId}` — run status +
    pending questions + transcript.
- `api/AiSkillController.java`, `api/AiPromptTemplateController.java` — **new**:
  CRUD for skills and prompt templates under `/hub/...`.

### Backend — `platform-common`
- Extend `agent/NodeResult.java` with an `awaitingInput` variant (or reuse the
  awaitingReview machinery with a distinct decision channel).
- DTOs/records for the generation request, clarification questions/answers.

### Backend — `platform-llm`
- No new public API expected; multi-turn is already supported (message-list
  accumulation + checkpoints). Confirm the `resume(checkpointId, node)` path
  covers injecting a fresh user message (the answers turn).

### Backend — `platform-portal` (BFF proxy)
- `api/PortalTestCaseController.java` — **modify**: proxy the enriched generate
  call, the answers endpoint, and the generation-status GET; multipart
  passthrough for input-file uploads (reuse the existing
  ByteArrayResource/HttpEntity pattern used for execution evidence).
- `api/PortalAiController.java` (or extend the existing AI settings controller) —
  proxy skill + prompt-template CRUD.

### Frontend — `platform-portal/frontend/src`
- `pages/TestCasesPage.tsx` — **modify** `GenerateTestCasesModal`: add skill
  multi-select, system/user prompt fields (prefilled from default template,
  editable), free-text input, file attach, plus the existing requirement select.
- `pages/AiGenerationRunPage.tsx` (or a panel/modal) — **new**: shows run status;
  when `AWAITING_INPUT`, renders questions with answer inputs and a "Submit
  answers" action; shows the Q&A transcript and final result.
- `pages/AiSettingsPage.tsx` — **extend**: Skills library CRUD + Prompt
  Templates CRUD sections (project-scoped).
- `lib/api.ts` — methods: skills CRUD, prompt-template CRUD, enriched
  `generateTestCasesFromAI`, `getGeneration`, `submitGenerationAnswers`,
  input-file upload.
- `lib/types.ts` — `AiSkill`, `AiPromptTemplate`, `GenerationRequest`,
  `GenerationStatus`, `ClarificationQuestion`, `ClarificationAnswer`.

## 4. Code style

Follow the existing repo conventions (match surrounding code):

- **Java 21 / Spring Boot 4**: constructor injection; records for DTOs;
  package-by-feature under `com.platform.<module>`. Throw
  `ResponseStatusException` with precise status codes for guard failures (e.g.
  `409` for answering a run that isn't awaiting input, `404` unknown skill,
  `400` empty input). Keep ADO read-only.
- **Persistence**: JPA entities mirror existing ones; UUID PKs; JSON columns as
  `TEXT` serialized with Jackson `ObjectMapper` (matching the defect/attachment
  pattern). New migration is additive.
- **Agent node**: keep the JSON-array output contract and
  `parseGeneratedTestCases` approach. The `ask_user` tool must be a clean,
  well-described tool spec so the model only calls it when context is genuinely
  insufficient; cap clarification rounds (config, default 3) to prevent loops.
- **Prompts**: a resolved prompt = template/override is data, never hardcoded
  business specifics. Persist the exact `systemPromptUsed`/`userPromptUsed` for
  audit/repro.
- **Frontend**: React + TanStack Query + Tailwind + lucide-react; follow
  existing modal/page idioms; run `npm run format` and keep `tsc -b` clean.
- **Naming**: `AiSkill`, `AiPromptTemplate`, `GenerationClarification`,
  `AWAITING_INPUT`. Reuse `AGENT` as `createdBy` for generated cases.

## 5. Testing strategy

TDD (JUnit5 + Mockito + AssertJ) per task; one commit per task; full suite +
build before commit.

### Backend unit tests
- **Skills CRUD**: create/list/update/delete scoped by project; reject
  cross-project access; unique name per project.
- **Prompt templates**: default resolution; per-run override does not mutate the
  saved template; SYSTEM vs USER kinds.
- **Request validation**: at least one input source required (requirements OR
  free-text OR files); unknown `skillId`/`fileId` → 400/404.
- **Prompt assembly**: given skills + template + free text + requirements + file
  excerpts, the composed system/user messages contain each part and are
  persisted as `*Used`.
- **Clarification loop** (the core):
  - When the model calls `ask_user`, the node returns `awaitingInput`, a
    `GenerationClarification` (round N, PENDING) is saved, workflow →
    `AWAITING_INPUT`, Kafka event emitted, and **no test cases are persisted**.
  - `GenerationResumeService` with answers → marks the clarification ANSWERED,
    resumes from checkpoint, injects answers as the next user turn, continues.
  - Multi-round: a second `ask_user` produces round N+1; answering completes.
  - Answering a run not in `AWAITING_INPUT` → 409.
  - Cap reached → node proceeds with best-effort generation, noting assumptions.
- **Completion**: on final JSON, DRAFT `PlatformTestCase` + `TestCaseStep` rows
  persist as today, tagged with workflowId; transcript saved.

### Frontend
- Type-level: `tsc -b` clean with new types.
- Manual browser verification (chrome-devtools MCP): generate modal with skills
  + prompts + free text + file; run reaching `AWAITING_INPUT` renders questions;
  submitting answers resumes and yields DRAFT cases.

### Regression
- Existing one-shot path still works when no skills/prompts/questions are used
  (backward compatible: empty skill list, default prompts, model asks nothing).
- Existing `AgentWorkflow`/Review Queue behavior unchanged.

## 6. Boundaries

### Always
- Keep ADO integration **read-only** (no work-item writes).
- `mvn package` before `docker compose build`; keep Flyway version aligned across
  the db-migrate image and every JVM service.
- Persist exactly what was sent to the model (resolved prompts, skills, inputs,
  file manifest) and the full Q&A transcript for audit/repro.
- Make generation backward compatible: a request with only `requirementIds` and
  no skills/overrides behaves like today.
- Bound the clarification loop with a configurable max-round cap.
- TDD with a failing test first; one focused commit per task using the required
  trailer.
- Keep `PLATFORM_CRED_KEY` and secrets in gitignored `.env`; never commit/log.

### Ask first
- Any change to the DRAFT→APPROVED lifecycle or the Review Queue.
- Folding the new schema into V1 vs adding V2 (depends on whether V1 has shipped
  to a non-resettable environment).
- Adding a new Kafka topic vs extending `AGENT_WORKFLOW_EVENTS`.
- Allowing skills/templates to reference KNOWLEDGE/RAG blobs (deferred; confirm
  before pulling into context).
- Per-run model/temperature selection exposure beyond current AI settings.

### Never
- Never write to ADO or any external system during generation.
- Never auto-approve generated test cases (they stay DRAFT).
- Never restart a clarified run from scratch — always resume from checkpoint so
  prior context/cost isn't lost.
- Never hardcode project/domain specifics into prompts — they come from
  templates/skills/input data.
- Never block the request thread on the LLM — generation stays async with status
  polling/events.
- Never let an unbounded clarification loop run (respect the cap).
- Never `git add -A` blindly; stage only the files a task touched.

---

## Confirmed decisions (from clarification)
1. **Skills** = reusable, project-scoped **library (CRUD)**, selected per run.
2. **Clarifying questions** = **pause → answer → resume**, multiple rounds.
3. **Input** = **free-text + selected requirements + file attachments** (any
   combination; ≥1 required).
4. **Prompts** = **saved project templates (defaults) + per-run override** for
   both system and user prompts.
