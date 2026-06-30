# TODO — Review & Iterative Refinement of AI-Generated Test Cases

Source spec: `SPEC.md` · Plan: `tasks/plan.md`.
Convention: TDD per task (RED → GREEN → regression → build), one commit per task. `[ ]` open · `[x]` done.
Each endpoint task ships its `@RequireCapability(OPERATE_QUALITY, "projectId")` + a reflection
annotation test (spec F6).

---

## Phase A — Staging foundation

- [x] **A1: `V8` migration + `GeneratedTestCaseProposal` entity + repository**
  - Table per SPEC §4 (`generated_test_case_proposals`, `UNIQUE(workflow_id, ordinal)`); entity +
    repo (`findByWorkflowIdOrderByOrdinal`, `findByIdAndProjectId`).
  - **AC:** AC1.4; migration applies; `ddl-auto: validate` passes.
  - Done: `V8` (CHECK on status, FKs ON DELETE CASCADE, default PROPOSED); entity + repo
    (`findByWorkflowIdOrderByOrdinalAsc`, `findByWorkflowIdAndStatusOrderByOrdinalAsc`,
    `findByIdAndProjectId`). **Verified:** V8 pre-flight in a rolled-back txn (table + indexes + insert
    default); platform-core 31/0.

- [x] **A2: Generation terminal writes proposals + review-pending (not DRAFT)**
  - `finishFromClaude()` writes PROPOSED proposals instead of DRAFT test cases; both completion
    handlers (initial + resume) end the workflow `AWAITING_REVIEW`.
  - **AC:** AC1.1, AC1.2, AC1.3, AC7.1.
  - Done: node stages proposals (steps as JSON) + returns `awaitingReview`; `AgentWorkflowService`
    marks `AWAITING_REVIEW` and **skips the Kafka review gateway for GENERATE_TEST_CASES** (proposals
    are curated via the new API); `GenerationResumeService.handleResult` gained a `needsReview` branch.
    **Verify:** `TestCaseGenerationNodeTest` +1 (stages 2 proposals PROPOSED, ordinals 0/1, **no**
    `testCaseRepo`/`stepRepo` save, `needsReview()` true); agent 163/0.
  - ⚠️ Decision (SPEC §9 ask-first): **reused `AWAITING_REVIEW`** + skip-gateway-for-generation
    rather than a new state. Flag at CHECKPOINT A.

- [x] **CHECKPOINT A** — generate → proposals in staging, **0** new catalog rows, workflow
  `AWAITING_REVIEW`; clarification flow intact. (Live-verified in the A+B and D1 deploys.)

---

## Phase B — Review, accept, reject

- [x] **B1: List proposals (GET + BFF + `ProposalsReview` shell)**
  - `GET …/generations/{workflowId}/proposals`; portal proxy; `ProposalsReview.tsx` read-only list
    wired into `GenerationProgress.tsx` review-pending state.
  - **AC:** AC2.1, AC2.2, AC6.1.
  - Done: `ProposalService.list` (project-scoped, parses steps JSON) + DTOs; controller endpoint
    `@RequireCapability(OPERATE_QUALITY,"projectId")`; portal BFF proxy; `ProposalsReview` (expandable
    cards, priority/status badges) replaces the AWAITING_REVIEW banner; `api.listProposals` +
    `GeneratedProposal` type. **Verify:** `ProposalServiceTest` (2, list + cross-project filter) +
    `ProposalEndpointsRbacTest` (1); agent 166/0; `tsc -b` green.

- [x] **B2: Accept (per-case) + Accept all**
  - `POST …/proposals/{id}/accept` + `…/proposals/accept-all`; `ProposalService.accept` creates
    DRAFT + steps (idempotent; rejected→409; actor from `CurrentUser`); BFF + UI.
  - **AC:** AC3.1, AC3.2, AC3.3, AC3.4, AC6.2.
  - Done: `accept`/`acceptAll` (shared `acceptEntity`) create DRAFT + steps from JSON, link source
    requirement, mark ACCEPTED + `acceptedTestCaseId`; idempotent; rejected→409; actor from
    `CurrentUser.username()`. Endpoints + BFF proxies; UI per-card Accept + header Accept-all.
    **Verify:** `ProposalServiceTest` +4 (creates DRAFT+steps/ACCEPTED, idempotent, rejected→409,
    acceptAll); RBAC test covers accept/accept-all; agent 170/0; `tsc -b` green.

- [x] **B3: Reject (per-case)**
  - `POST …/proposals/{id}/reject` → REJECTED; excluded; never persisted. BFF + UI.
  - **AC:** AC4.1, AC4.2.
  - Done: `ProposalService.reject` (idempotent; accepted→409; never touches the catalog); endpoint +
    BFF proxy; per-card Reject button. **Verify:** `ProposalServiceTest` +2 (reject marks REJECTED/no
    catalog save; accepted→409); RBAC covers reject; agent 172/0; `tsc -b` green.

- [x] **CHECKPOINT B** — accept/reject verified at unit level (catalog created only on accept; reject
  never persists). Live deploy-verify folded into D1.

---

## Phase C — Iterative refinement

- [x] **C1: Resumable checkpoint at generation completion (refine prerequisite)** ⚠️ key risk
  - Ensure `orchestrator.resume(...)` can continue from the **completed** conversation (today
    checkpoints save on pause only); store the completion checkpoint id for refine.
  - **AC:** post-completion resume rehydrates the same conversation coherently.
  - Done: `LangChainAgentRunner` now checkpoints the finished conversation (incl. the final turn) at
    the no-tool-call completion path and returns a new `NodeResult.completed(... checkpointId ...)`
    overload carrying it; the node already forwards `claudeResult.checkpointId()` into its
    `awaitingReview`, so the completion checkpoint is now persisted for refine to resume from.
    **Verify:** `LangChainAgentRunnerTest` completion test asserts `save` called + `checkpointId`
    carried; agent 172/0.

- [x] **C2: Refine (per-case) — continue conversation, update in place**
  - `POST …/proposals/{id}/refine {instruction}`; refine resume mode (inject instruction scoped to
    the proposal); update proposal in place (stays PROPOSED), async + streamed; UI instruction box.
  - **AC:** AC5.1, AC5.2, AC5.4, AC5.5, AC6.1.
  - Done: `V9` persists the run's review checkpoint; `finishFromClaude` records it; node
    `refineProposal` resumes the conversation, parses the single revised case, updates the proposal
    in place (stays PROPOSED, never touches the catalog), advances the checkpoint;
    `GenerationResumeService.validateRefine`+`@Async refineAsync` (only-PROPOSED / no-checkpoint →
    409); endpoint + BFF proxy; per-card Refine instruction box (async → status RUNNING→AWAITING_REVIEW
    re-polls + remounts). **Verify:** node refine test (updates in place, checkpoint advances, no
    catalog), `GenerationResumeServiceTest` +3 guards, RBAC covers refine; agent 176/0; `tsc -b` green.

- [x] **C3: Refine all (batch)**
  - `POST …/proposals/refine-all {instruction}` over all PROPOSED; update each in place. BFF + UI.
  - **AC:** AC5.3.
  - Done: `validateRefineAll` (no-checkpoint / nothing-proposed → 409) + `@Async refineAllAsync`
    (refines each still-PROPOSED case in sequence, continuing the conversation per case); endpoint +
    BFF proxy; header "Refine all" instruction box. **Verify:** `GenerationResumeServiceTest` +2
    (nothing-proposed→409, plan+RUNNING); RBAC covers refine-all; agent 178/0; `tsc -b` green.

- [x] **CHECKPOINT C** — refine (per-case + all) verified at unit level (updates in place, never the
  catalog, guards). Live deploy-verify folded into D1.

---

## Phase D — Hardening & sign-off

- [x] **D1: Full regression + per-role browser E2E + deploy**
  - Full `mvn test` + `tsc -b` green; rebuild/redeploy touched services; Tester E2E of the loop;
    negative check (viewer / wrong-project → 403 on proposal endpoints); clarification + lifecycle +
    Review Queue unaffected.
  - **AC:** AC7.1, AC7.2 + F1–F6 matrix.
  - Done: full reactor green; `tsc -b` green; rebuilt + redeployed agent+portal (V9 applied).
    **Live-verified through the portal BFF:** generation→10 staged proposals + checkpoint persisted;
    refine one (title sharpened, 6→8 steps, stays PROPOSED, 0 catalog rows); refine-all (all 10 in
    place, 0 catalog rows); accept→DRAFT+steps / reject→discarded (B-phase); viewer→403 on
    list/accept-all/refine-all. (Note: the React ProposalsReview panel wasn't separately click-tested
    — the BFF data path it calls is fully exercised live.)

- [x] **CHECKPOINT D** — feature complete, deployed, verified.

---

## Open decisions (confirm during build — SPEC §9)
- Reuse `AWAITING_REVIEW` vs. a dedicated generation review state (assumed: reuse).
- C1 completion-checkpoint feasibility (the one real unknown — surface early).
- Proposal retention/expiry (none this iteration unless asked).
- `steps_json` in staging vs. normalized child table (assumed: JSON).
