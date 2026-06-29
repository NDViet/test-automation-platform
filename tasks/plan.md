# Plan — Interactive AI Test Case Generation

Source: `SPEC.md`. Slices are **vertical** (domain → persistence → service → controller →
BFF → frontend per capability) and ordered by dependency. Each task is TDD (RED → GREEN →
regression → build → commit), one commit per task.

## Key facts from code exploration (ground truth)

- `TestCaseGenerationNode` is a one-shot node: hardcoded `systemPrompt()`, builds a
  requirements message, wraps itself in a `TextOnlyNode` shim with **no tools**, calls
  `orchestrator.run(bundle, shim)`, parses a JSON array, persists DRAFT `PlatformTestCase`
  + `TestCaseStep`, returns `NodeResult.completed(...)`.
- The orchestrator (`LangChainAgentRunner.run`) already has a **tool-call loop** with a
  `REVIEW_SENTINEL` (`__AWAITING_REVIEW__`): when `node.dispatchToolCall(...)` returns a
  string starting with the sentinel, it saves a checkpoint (messages → CHECKPOINTS blob via
  `CheckpointService.save`) and returns `NodeResult.awaitingReview(..., checkpointId, ...)`.
  **We reuse this exact mechanism** for clarifying questions with a new sentinel.
- ⚠️ `LangChainAgentRunner.resume(checkpointId, node)` is a **stub** returning
  `NodeResult.failed("RESUME_NOT_IMPLEMENTED")`. Making it real is the heart of the feature.
- ⚠️ Checkpoint save uses raw `mapper.writeValueAsString(messages)` on LangChain4j
  `ChatMessage` objects. For round-trip resume we must use LangChain4j's
  `ChatMessageSerializer.messagesToJson()` / `ChatMessageDeserializer.messagesFromJson()`
  (Jackson won't round-trip the polymorphic message hierarchy reliably). Coordinated change
  in save + resume.
- `AgentWorkflowService.executeWorkflow(UUID, ContextBundle)` is `@Async`; on
  `result.needsReview()` it sets `workflow.markAwaitingReview()`, calls
  `reviewGateway.requestReview(...)`, publishes Kafka `AWAITING_REVIEW`, and returns.
- `AgentWorkflow` entity: status varchar(20) values PENDING|RUNNING|COMPLETED|FAILED|
  AWAITING_REVIEW; mutators `markRunning/markCompleted/markFailed/markAwaitingReview`,
  `addTokens`. Trigger carried as `triggerRef` jsonb.
- `ContextBundle` is a record carrying sessionId/workflowId/projectId/projectSlug/trigger +
  context tiers + `checkpointId` + `resumeStrategy` + `llmTier`. It does **not** today carry
  skills/prompt-overrides/free-text/files — we pass those via a new side table keyed by
  workflowId, loaded by the node (avoids widening the record signature everywhere).
- File-upload reference: `ExecutionAttachmentService.upload(MultipartFile → BlobStore
  .storeBytes(ARTIFACTS, bytes, contentType) → serialize(BlobRef) → row)`. Portal multipart
  passthrough already wired (ByteArrayResource + HttpEntity) for evidence.
- Generate endpoint today: `POST /hub/test-cases/{projectId}/generate` →
  `workflowService.createWorkflow(projectId, trigger)` → `contextAssembler.assemble(...)` →
  `executeWorkflow(...)`. BFF proxy in `PortalTestCaseController`.
- Kafka topics: `Topics.AGENT_WORKFLOW_EVENTS`, `AGENT_APPROVAL_REQUESTS`,
  `AGENT_APPROVAL_DECISIONS`. We will **not** add a new topic (answers resume is invoked
  directly/async — see C/D); reusing/extending event strings only.

## Decisions needed before build (CHECKPOINT 0)

1. **Migration strategy.** Product is brand-new. Two options:
   (a) one new `V2__ai_generation.sql` containing all new tables/columns; or
   (b) one migration *per slice* (`V2__ai_skills`, `V3__ai_prompt_templates`,
   `V4__ai_generation_runs`, `V5__generation_clarifications`). Recommend **(b)** —
   per-slice migrations never edit an already-applied file, avoiding the checksum-mismatch
   pain seen earlier. Confirm.
2. **Resume transport.** Answers endpoint invokes `GenerationResumeService` **directly and
   async** (mirrors `executeWorkflow`), no new Kafka topic. Confirm (vs Kafka decision
   round-trip like the review gate).
3. **Where resolved request lives.** New side table `ai_generation_runs` (1:1 with
   workflow) rather than widening `AgentWorkflow`. Confirm.

---

## Phase A — Skills library (independent slice)

### T1 — AiSkill: domain + repo + migration + CRUD service
- **Files:** `platform-core` `domain/AiSkill.java`, `repository/AiSkillRepository.java`,
  `db/migration/V2__ai_skills.sql`; service `AiSkillService` (place alongside existing TCM
  services / agent api package — match repo convention).
- **Behavior:** project-scoped CRUD; fields id, projectId, name, description, instructions
  (TEXT), enabled, createdBy, createdAt, updatedAt; unique (projectId, name).
- **AC:** create/list/get/update/delete scoped by projectId; reject cross-project access
  (404 when skill's projectId ≠ path projectId); duplicate name in same project → 409.
- **Tests:** `AiSkillServiceTest` — CRUD happy paths, cross-project rejection, duplicate
  name. **Verify:** `mvn -pl platform-core,platform-agent -am test`.

### T2 — Skills HTTP API + portal BFF proxy
- **Files:** `platform-agent` `api/AiSkillController.java`
  (`/hub/projects/{projectId}/ai/skills` CRUD); `platform-portal` `PortalAiController` (new)
  proxying `/api/portal/projects/{projectId}/ai/skills`.
- **AC:** all CRUD verbs proxied; status codes preserved.
- **Tests:** controller slice test (MockMvc) for create+list+delete.
  **Verify:** `mvn -pl platform-agent,platform-portal -am test`.

### T3 — Skills frontend (Settings CRUD)
- **Files:** `frontend/src/lib/types.ts` (`AiSkill`), `lib/api.ts` (skills CRUD),
  `pages/AiSettingsPage.tsx` (Skills library section: list, create/edit modal, delete).
- **AC:** user can manage skills under AI settings; list refetches on mutation.
- **Verify:** `tsc -b` clean; `vite build`; browser smoke (create/edit/delete a skill).

> **CHECKPOINT A** — skills CRUD usable end-to-end.

---

## Phase B — Prompt templates (independent slice, parallel to A)

### T4 — AiPromptTemplate: domain + repo + migration + service (default resolution)
- **Files:** `platform-core` `domain/AiPromptTemplate.java`,
  `repository/AiPromptTemplateRepository.java`, `db/migration/V3__ai_prompt_templates.sql`;
  `AiPromptTemplateService`.
- **Behavior:** project-scoped; kind SYSTEM|USER; name, body (TEXT), isDefault, updatedAt.
  Service resolves the default per (projectId, kind); seeds a built-in default body (the
  current hardcoded system prompt) when none exists.
- **AC:** CRUD; exactly one default per (projectId, kind) (setting a new default clears the
  prior); `resolveDefault(projectId, kind)` returns seeded fallback when empty.
- **Tests:** `AiPromptTemplateServiceTest` — default uniqueness, fallback resolution,
  SYSTEM vs USER isolation. **Verify:** module tests.

### T5 — Prompt-template HTTP API + portal BFF proxy
- **Files:** `api/AiPromptTemplateController.java`
  (`/hub/projects/{projectId}/ai/prompt-templates`); extend `PortalAiController`.
- **AC:** CRUD + `GET .../defaults` returning resolved system+user defaults (for modal
  prefill). **Tests:** controller slice test. **Verify:** module tests.

### T6 — Prompt-template frontend (Settings CRUD)
- **Files:** `types.ts` (`AiPromptTemplate`), `api.ts`, `AiSettingsPage.tsx` (Prompt
  Templates section).
- **AC:** manage system/user templates; mark default. **Verify:** `tsc -b`, `vite build`,
  browser smoke.

> **CHECKPOINT B** — skills + prompt templates both manageable; backend ready to be consumed
> by generation.

---

## Phase C — Rich generation request (input + assembly), still one-shot

### T7 — Generation request model + input-file upload + resolved-run persistence
- **Files:** `platform-core` `domain/AiGenerationRun.java` (1:1 workflow: workflowId,
  projectId, skillIdsJson, systemPromptUsed TEXT, userPromptUsed TEXT, freeText TEXT,
  attachmentManifestJson, maxRounds, createdAt), `repository/AiGenerationRunRepository.java`,
  `db/migration/V4__ai_generation_runs.sql`; `platform-common` request DTO
  `GenerateTestCasesRequest(requirementIds, freeText, fileIds, skillIds,
  systemPromptOverride, userPromptOverride, maxRounds)`; input-file upload endpoint
  `POST /hub/projects/{projectId}/ai/generation-files` (MultipartFile → BlobStore ARTIFACTS,
  return fileId+manifest) following `ExecutionAttachmentService.upload`; portal BFF multipart
  proxy.
- **AC:** request validation — **≥1 input source** required (requirementIds OR freeText OR
  fileIds) else 400; unknown skillId → 404; unknown fileId → 400. Upload stores bytes in
  ARTIFACTS and returns a referencable id.
- **Tests:** request-validation unit test (each branch); upload service test (BlobStore
  mocked). **Verify:** module tests.

### T8 — Enriched generate endpoint + run persistence (no clarifying questions yet)
- **Files:** modify `TestCaseGenerationController` generate endpoint to accept
  `GenerateTestCasesRequest` (keep back-compat: legacy `{requirementIds}` body still parses);
  persist an `AiGenerationRun` (skills, free text, file manifest, prompt overrides) before
  dispatch; pass workflowId so the node can load it. Update `PortalTestCaseController` proxy.
- **AC:** richer body creates a workflow + `AiGenerationRun`; legacy body still works.
- **Tests:** controller test for both body shapes. **Verify:** module tests.

### T9 — Prompt assembly in TestCaseGenerationNode
- **Files:** modify `TestCaseGenerationNode` — load `AiGenerationRun` by workflowId; resolve
  **system** = override ?? default template ?? current hardcoded text, **+ appended skills**
  (selected `AiSkill.instructions`); resolve **user** = override ?? default template, **+**
  free text **+** requirements message (existing) **+** file excerpts (fetch text blobs,
  truncate). Persist exact `systemPromptUsed`/`userPromptUsed` back to `AiGenerationRun`.
  Keep JSON-array output contract + persistence unchanged.
- **AC:** composed messages contain each provided part; `*Used` persisted; with no
  skills/overrides/free-text/files the prompt equals today's (backward compatible).
- **Tests:** assembled-prompt test (contains skill + free text + requirement + file excerpt,
  `*Used` saved) + back-compat test (empty extras ⇒ legacy prompt). **Verify:** `mvn -pl
  platform-agent -am test`.

### T10 — Generate modal frontend (inputs)
- **Files:** `types.ts` (`GenerateTestCasesRequest`), `api.ts` (enriched
  `generateTestCasesFromAI`, file upload), `pages/TestCasesPage.tsx` `GenerateTestCasesModal`
  — add skill multi-select, system/user prompt fields prefilled from `/defaults` (editable),
  free-text box, file attach; existing requirement select retained; client-side ≥1-input
  guard.
- **AC:** can launch a run with any combination; defaults prefill; legacy requirements-only
  path still works. **Verify:** `tsc -b`, `vite build`, browser smoke (run with skill + free
  text + file produces DRAFT cases).

> **CHECKPOINT C** — steerable one-shot generation (skills + prompts + rich input) works end
> to end; no clarifying questions yet. Validate quality before adding the interactive loop.

---

## Phase D — Clarifying questions (pause → answer → resume)

### T11 — `awaitingInput` result type + status plumbing
- **Files:** `platform-common` `NodeResult.awaitingInput(...)` + `NodeResultStatus
  .AWAITING_INPUT` + `needsInput()` (mirror `awaitingReview`/`needsReview`); `platform-core`
  `AgentWorkflow.markAwaitingInput()` ("AWAITING_INPUT" fits varchar(20)).
- **AC:** new factory carries checkpointId + a questions payload; workflow can enter
  AWAITING_INPUT.
- **Tests:** `NodeResultTest`. **Verify:** `mvn -pl platform-common,platform-core -am test`.

### T12 — `ask_user` tool on the node + orchestrator sentinel
- **Files:** modify `TestCaseGenerationNode` (and shim) to advertise an `ask_user`
  `ToolSpecification` (`questions: [{id, question, kind(TEXT|CHOICE), options?}]`) and
  implement `dispatchToolCall("ask_user", ...)` returning `INPUT_SENTINEL + questionsJson`;
  modify `LangChainAgentRunner` to recognize `INPUT_SENTINEL` (`__AWAITING_INPUT__`)
  alongside `REVIEW_SENTINEL`, save checkpoint, return `awaitingInput(..., checkpointId,
  questionsJson)`.
- **AC:** when the model calls `ask_user`, the run saves a checkpoint and returns
  awaitingInput with the questions; **no test cases persisted** that turn.
- **Tests:** orchestrator test with a stub `ChatModel` emitting an `ask_user` tool call ⇒
  checkpoint saved + awaitingInput returned. **Verify:** `mvn -pl platform-agent -am test`.

### T13 — Workflow service handles AWAITING_INPUT + persists clarification
- **Files:** `platform-core` `domain/GenerationClarification.java`
  (workflowId, round, questionsJson, answersJson nullable, status
  PENDING|ANSWERED|SKIPPED, createdAt, answeredAt) + repo +
  `db/migration/V5__generation_clarifications.sql`; modify `AgentWorkflowService
  .executeWorkflow` — on `result.needsInput()`: persist a `GenerationClarification`
  (round N, PENDING), `workflow.markAwaitingInput()`, publish `AWAITING_INPUT`, return.
- **AC:** reaching awaitingInput creates a PENDING clarification (round increments) and parks
  the workflow; event emitted.
- **Tests:** `AgentWorkflowServiceTest` (mock orchestrator returns awaitingInput) ⇒
  clarification saved + workflow AWAITING_INPUT + no completion. **Verify:** module test.

### T14 — Implement real checkpoint resume in the orchestrator (HIGH RISK)
- **Files:** modify `LangChainAgentRunner` — `saveCheckpoint` serializes via LangChain4j
  `ChatMessageSerializer.messagesToJson(...)`; implement `resume(checkpointId, node, String
  nextUserMessage)` (extend `AgentOrchestrator`): load `ConversationState`, fetch +
  `ChatMessageDeserializer.messagesFromJson(...)`, append answers as `UserMessage`, run the
  tool-loop, return completed / awaitingInput.
- **AC:** a saved checkpoint round-trips to an equivalent message list; resume continues the
  conversation and can complete or ask again.
- **Tests:** round-trip serialization test; resume test with stub `ChatModel` that completes
  after the injected answers turn. **Spike LangChain4j (de)serialization first.**
  **Verify:** `mvn -pl platform-agent -am test`.

### T15 — GenerationResumeService + answers endpoint (multi-round, cap, 409)
- **Files:** `platform-agent` `workflow/GenerationResumeService.java`
  (`@Async resumeWithAnswers(projectId, workflowId, answers)`): guard status ==
  AWAITING_INPUT else 409; mark latest clarification ANSWERED (answersJson); rebuild resume
  `ContextBundle` and call `orchestrator.resume(checkpoint, node, answersAsText)`; handle the
  result like `executeWorkflow` (awaitingInput → new round; completed → persist + COMPLETED;
  failed → FAILED). Enforce `maxRounds` cap (default 3 from `AiGenerationRun`) → on cap,
  inject "proceed with best effort, note assumptions" instead of re-asking. Endpoint
  `POST /hub/test-cases/{projectId}/generations/{workflowId}/answers`; portal BFF proxy.
- **AC:** answering resumes the same run from checkpoint (no restart); a second `ask_user`
  yields round N+1; answering when not AWAITING_INPUT → 409; cap reached ⇒ completes with
  assumptions noted.
- **Tests:** `GenerationResumeServiceTest` — resume→complete, multi-round, 409 guard, cap.
  **Verify:** `mvn -pl platform-agent -am test`.

### T16 — Generation status endpoint (status + pending questions + transcript)
- **Files:** `GET /hub/test-cases/{projectId}/generations/{workflowId}` returning workflow
  status, pending questions (if any), clarification transcript + resolved run info; portal
  BFF proxy.
- **AC:** returns AWAITING_INPUT + questions while parked; COMPLETED + summary after finish;
  lists all rounds.
- **Tests:** controller test for parked vs completed. **Verify:** module test.

### T17 — Interactive run frontend (questions UI + transcript)
- **Files:** `types.ts` (`GenerationStatus`, `ClarificationQuestion`, `ClarificationAnswer`),
  `api.ts` (`getGeneration`, `submitGenerationAnswers`), new `pages/AiGenerationRunPage.tsx`
  (or panel reachable from the modal/list): poll status; when AWAITING_INPUT render questions
  (text + choice) with answer inputs and "Submit answers"; show Q&A transcript and final
  DRAFT-case result; surface the run after launching from `GenerateTestCasesModal`.
- **AC:** a run that asks questions shows them; submitting resumes; multi-round visible;
  completion shows generated cases. **Verify:** `tsc -b`, `vite build`, browser E2E.

> **CHECKPOINT D** — full interactive loop works end to end.

---

## Phase E — Verification & rollout

### T18 — Regression + full backend suite
- **AC:** entire backend suite green (`mvn -q test`); legacy one-shot generation
  (requirements-only, no questions) unchanged; Review Queue/AgentWorkflow intact.

### T19 — Build all jars + images, browser E2E, Flyway alignment
- **AC:** `mvn -q package -DskipTests && docker compose build && docker compose up -d` with
  every JVM service on the aligned Flyway version and new migrations applied; chrome-devtools
  E2E: manage a skill + template, launch a run with skill+freetext+file, answer a clarifying
  question across two rounds, see DRAFT cases. Never log/commit secrets.

---

## Dependency graph

```
Phase A (T1→T2→T3)  ─┐
Phase B (T4→T5→T6)  ─┤  (A, B independent — parallelizable)
                     ▼
Phase C  T7 → T8 → T9 → T10        (uses A,B services for assembly/prefill)
                     ▼
Phase D  T11 → T12 → T13           (status/tool plumbing)
              T14 → T15 → T16      (T14 resume gates T15; T15 gates T16)
                     ▼
              T17                  (frontend needs T15/T16)
                     ▼
Phase E  T18 → T19
```

Critical path: T7→T8→T9→T11→T12→T13→T14→T15→T16→T17→T18→T19. **T14 (real checkpoint resume)
is the highest-risk task** — spike LangChain4j message (de)serialization before committing.
