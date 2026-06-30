# Plan — Review & Iterative Refinement of AI-Generated Test Cases

Source spec: `SPEC.md`. Convention: TDD per task (RED → GREEN → regression → build → commit), one
commit per task; stage only the task's files + its todo update. Next Flyway version is **V8**.

## Where this hooks into the existing flow
- Generation is async in **platform-agent**: `POST /hub/test-cases/{projectId}/generate` →
  `TestCaseGenerationNode`. The shared terminal `finishFromClaude()` **currently saves each case to
  `platform_test_cases` as DRAFT** (the line we redirect).
- Clarification-before-generation already exists (`ask_user` / `AWAITING_INPUT` /
  `GenerationResumeService.resumeAsync` → `orchestrator.resume(bundle, checkpointId, …)`). **Refine
  reuses exactly this resume/checkpoint machinery.**
- Progress streams on `gen:progress:{workflowId}` (Redis) → `/ws/generations/{workflowId}` →
  `GenerationProgress.tsx`. The portal BFF proxies `/hub/...` and forwards the JWT.

## Dependency graph
```
A1 data model (V8 + entity + repo)
   └─> A2 node terminal → proposals + review-pending  ── CHECKPOINT A
          ├─> B1 list proposals (GET + BFF + UI shell)
          │      ├─> B2 accept + accept-all
          │      └─> B3 reject                          ── CHECKPOINT B
          └─> C1 capture a resumable checkpoint at completion (refine prerequisite)
                 └─> C2 refine (per-case, continue conversation)
                        └─> C3 refine-all                ── CHECKPOINT C
D1 final regression + per-role browser E2E + deploy      ── CHECKPOINT D
```
Vertical slices: each B/C task carries its backend endpoint + test + portal BFF proxy + the matching
`ProposalsReview` UI control, so every task is a usable end-to-end path. RBAC (`OPERATE_QUALITY` on
`projectId`) + a reflection annotation test ships **with each endpoint task** (spec F6), not as a
separate phase.

---

## Phase A — Staging foundation (generation stops writing the catalog)

### A1 — `V8` migration + `GeneratedTestCaseProposal` entity + repository
- New table `generated_test_case_proposals` (see SPEC §4): id, workflow_id, project_id, ordinal,
  title, description, preconditions, expected_result, priority, source_requirement_id, steps_json,
  status (`PROPOSED|ACCEPTED|REJECTED`, default PROPOSED), accepted_test_case_id, timestamps,
  `UNIQUE(workflow_id, ordinal)`. Entity in `platform-core.domain`, repo with
  `findByWorkflowIdOrderByOrdinal`, `findByIdAndProjectId`, status helpers.
- **AC:** AC1.4. Migration applies; `ddl-auto: validate` passes for the entity.
- **Verify:** `V8` pre-flight in a rolled-back txn; `mvn -pl platform-core -am test`.

### A2 — Generation terminal writes proposals + review-pending (not DRAFT)
- Change `TestCaseGenerationNode.finishFromClaude()`: instead of `testCaseRepo.save(DRAFT)` + steps,
  write `GeneratedTestCaseProposal` rows (status PROPOSED, ordered, steps as JSON) and return a
  result that maps the workflow to **review-pending** (reuse `AWAITING_REVIEW`). Update **both**
  completion handlers (initial-run path and `GenerationResumeService.handleResult`) so a completed
  generation → `AWAITING_REVIEW`, not `COMPLETED`. Clarification-before-generation is unchanged.
- **AC:** AC1.1, AC1.2, AC1.3, AC7.1.
- **Verify (RED→GREEN):** node test — finishing writes `proposalRepo.save` N times and **never**
  `testCaseRepo.save`; workflow ends review-pending. Regression: agent suite green.

### CHECKPOINT A — deploy/verify
Generate from a requirement → rows in `generated_test_case_proposals` (PROPOSED), **zero** new
`platform_test_cases`, workflow `AWAITING_REVIEW`. Clarification flow still works.

---

## Phase B — Review, accept, reject (curation MVP)

### B1 — List proposals (read path end-to-end)
- `GET /hub/test-cases/{projectId}/generations/{workflowId}/proposals` →
  `@RequireCapability(OPERATE_QUALITY, "projectId")`; returns proposals (full content + status,
  ordered). Portal BFF proxy + `api.listProposals` + `Proposal` type. New `ProposalsReview.tsx`
  (read-only list: title, priority, source requirement, expandable steps, status); wired into
  `GenerationProgress.tsx` for the review-pending state (replaces the "DRAFT created" banner).
- **AC:** AC2.1, AC2.2, AC6.1.
- **Verify:** endpoint annotation reflection test; `npx tsc -b`; browser — review-pending shows the
  proposed list.

### B2 — Accept (per-case) + Accept all
- `POST …/proposals/{proposalId}/accept` and `POST …/proposals/accept-all` (both OPERATE_QUALITY).
  New `ProposalService.accept`: create `PlatformTestCase` (DRAFT, `createdBy` = `CurrentUser`,
  `agentSessionId` = workflowId, source requirement preserved) + `TestCaseStep`s from steps_json,
  mark proposal ACCEPTED + set `accepted_test_case_id`; **idempotent**; reject `REJECTED` (409).
  `acceptAll` accepts all remaining PROPOSED. BFF + UI buttons (accept / accept-all).
- **AC:** AC3.1, AC3.2, AC3.3, AC3.4, AC6.2.
- **Verify:** `ProposalServiceTest` (accept creates DRAFT+steps+ACCEPTED; idempotent; rejected→409;
  acceptAll) + annotation test; browser — accept one → appears in catalog as DRAFT.

### B3 — Reject (per-case)
- `POST …/proposals/{proposalId}/reject` (OPERATE_QUALITY) → mark REJECTED; excluded from the active
  list; never enters the catalog. BFF + UI button.
- **AC:** AC4.1, AC4.2.
- **Verify:** `ProposalServiceTest` reject case + annotation test; browser — reject → gone, no
  catalog row.

### CHECKPOINT B — deploy/verify
Generate → review list → accept some (catalog DRAFTs appear, lifecycle intact) → reject some (gone).

---

## Phase C — Iterative refinement (continue the conversation)

### C1 — Capture a resumable checkpoint at generation completion (refine prerequisite)
- Today checkpoints are persisted on **pause** (`needsInput`). Refine needs to resume from the
  **completed** conversation. Ensure the orchestrator persists/returns a checkpoint id at completion
  and store it on the run (`AiGenerationRun`) or per-proposal so `orchestrator.resume(...)` can
  continue. **This is the key technical risk** — investigate `LangChainAgentRunner` checkpointing;
  if completion checkpoints aren't already available, add them.
- **AC:** after a generation completes, `orchestrator.resume(bundle, completionCheckpointId, …)`
  rehydrates the same conversation (verified by a follow-up turn producing coherent output).
- **Verify:** unit/integration on the resume path; manual resume produces a sensible revision.

### C2 — Refine (per-case) — update proposal in place
- `POST …/proposals/{proposalId}/refine` body `{ instruction }` (OPERATE_QUALITY). Add a **refine
  resume mode** to `GenerationResumeService`/node: resume the conversation from the completion
  checkpoint, inject the instruction scoped to that proposal (reference it by ordinal/content), parse
  the revised single case, and **update the proposal in place** (stays PROPOSED). Async; stream on
  the existing `gen:progress` channel. UI: per-case "Refine" opens an instruction box; shows
  "refining…" then the updated case.
- **AC:** AC5.1, AC5.2, AC5.4, AC5.5, AC6.1.
- **Verify:** service test (refine updates the proposal, no new catalog row, guard on
  accepted/rejected) + annotation test; browser — refine one → content changes in place.

### C3 — Refine all (batch)
- `POST …/proposals/refine-all` body `{ instruction }` (OPERATE_QUALITY): apply one instruction to
  all remaining PROPOSED proposals (batch resume), update each in place. BFF + UI "Refine all".
- **AC:** AC5.3.
- **Verify:** service test (all PROPOSED updated, ACCEPTED/REJECTED untouched) + annotation test;
  browser — refine-all updates the set.

### CHECKPOINT C — deploy/verify
Full loop: generate → refine one (and refine-all) → accept the good ones (DRAFTs) → reject the rest.

---

## Phase D — Hardening & sign-off

### D1 — Full regression + per-role browser E2E + deploy
- Full `mvn test` green; `npx tsc -b` green; rebuild + redeploy all touched services. Browser E2E as
  a **Tester** (OPERATE_QUALITY): the whole loop; and a negative check (a viewer / wrong-project user
  is denied the proposal endpoints, 403). Confirm the existing clarification flow, lifecycle
  endpoints, and Review Queue are unaffected.
- **AC:** AC7.1, AC7.2, plus the full matrix from F1–F6.
- **Verify:** regression + image build + deploy + browser E2E.

### CHECKPOINT D — feature complete, deployed, verified.

---

## Risks / decisions to confirm during build (from SPEC §9 "ask first")
- **Workflow state**: reuse `AWAITING_REVIEW` vs. a dedicated generation state (plan assumes reuse).
- **Completion checkpoint (C1)**: the one real unknown — whether the orchestrator already checkpoints
  at completion. If not, C1 grows; surface early.
- **Proposal retention**: no auto-expiry of un-accepted proposals in this iteration (flag if wanted).
- **Steps storage**: `steps_json` in staging (plan assumes JSON; normalize only if asked).
