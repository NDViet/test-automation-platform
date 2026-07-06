# SPEC — Resume test‑case generation workflow from the Review Queue

> Status: **DRAFT — awaiting confirmation.** A standalone feature (not part of the A–F quality‑cycle
> program, which is complete + deployed). Prior program specs archived under `spec/archive/`.

## 1. Objective

Let a QA engineer **resume a paused “generate test cases from a requirement” workflow directly from
the Review Queue** — reopen its proposals, curate them (accept / refine), and **regenerate a fresh set
with more context** (add requirements, pull PR context, add custom input), then **finish** the review
so the workflow completes and leaves the queue.

Today the generation workflow parks at `AWAITING_REVIEW` with staged proposals, and a full
proposal‑review UI exists — but it is only reachable **in the same browser session** from the generate
modal. The Review Queue’s `WORKFLOW` / AWAITING_REVIEW card has **no action**. This feature makes that
workflow resumable from the queue and adds a context‑augmented regeneration + explicit finalize.

### Target users
QA engineers / leads who triage the Review Queue (`…/review-queue`) and iterate on AI‑generated cases.

### Locked decisions (from clarification)
1. **Scope = full**: reachable‑from‑queue review **plus** greenfield “generate more with added context”
   (more requirements, PR context, custom input) **plus** an explicit finalize.
2. **Regenerate = fresh round replaces**: a regeneration discards the current **un‑accepted** proposals
   (`PROPOSED`) and produces a new set; already‑`ACCEPTED` proposals stay (they are real DRAFT test
   cases already) and are never touched.
3. **PR context = reuse Impact Analysis**: resolve PRs for the workflow’s requirements by reverse‑
   querying `ImpactAnalysis.linkedRequirementIds ↔ linkedPrs`; inject the selected analyses’ summary /
   suggestions as prompt context. No new PR fetching.
4. **Finalize = explicit “Finish review”**: marks the workflow `COMPLETED`, auto‑rejects any remaining
   `PROPOSED` proposals, and drops the item from the queue.

### Grounding (verified against current code)
- **Workflow**: `AgentWorkflow` (`agent_workflows`), statuses `PENDING/RUNNING/AWAITING_REVIEW/
  AWAITING_INPUT/COMPLETED/FAILED`; `AgentWorkflowService.executeWorkflow` special‑cases generation to
  park (no `AgentReviewRequest` created) at `AgentWorkflowService.java:142‑155`.
- **Generation node**: `TestCaseGenerationNode` assembles the prompt from the requirement set (trigger
  `entityExternalId` = comma‑sep requirement UUIDs), `freeText`, files, skills, prompt overrides
  (`AiGenerationRun`), writes `GeneratedTestCaseProposal` rows (V8, status `PROPOSED`), records a
  `reviewCheckpointId` (V9 column on `ai_generation_runs`), returns `awaitingReview`.
- **Proposals**: `GeneratedTestCaseProposal` (`PROPOSED/ACCEPTED/REJECTED`); `ProposalService.acceptEntity`
  → creates a `PlatformTestCase` (origin **AGENT**, status DRAFT) + steps, links `sourceRequirementId`.
- **Existing resume**: `GenerationResumeService` — answer clarifications (`AWAITING_INPUT`) and
  refine proposals (`refine`/`refineAll`, checkpoint replay). **No “generate more with new context”, no
  resume‑from‑queue, no finalize‑to‑COMPLETED** — all greenfield.
- **REST**: `TestCaseGenerationController` (agent) — `getGeneration` (VIEW_RESULTS), accept/accept‑all/
  reject/refine/refine‑all/answers (OPERATE_QUALITY). Portal proxies under `/api/portal/…`.
- **Queue**: `WorkInProgressController` builds `WipItem`s; `WORKFLOW` items carry `metadata.workflowId`
  but render no action (`ReviewQueuePage.tsx:315‑323`). Proposal UI = `ProposalsReview.tsx`, mounted
  only by `GenerationProgress.tsx:104` in‑session.
- **PR ↔ requirement**: only via `ImpactAnalysis` (`linkedRequirementIds` jsonb + `linkedPrs` jsonb).
- Next Flyway = **V24**. ⚠️ `PlatformRequirement` cites migrations (V33/V59+) whose files don’t exist —
  verify the live DB before touching requirement columns; V24 here only touches `ai_generation_runs`.

## 2. Scope

**In:**
1. **Resume from queue** — the `WORKFLOW` / AWAITING_REVIEW card gets a **Review** action opening a
   workflow‑review view that loads the workflow’s proposals + run inputs (`getGeneration`), reusing
   `ProposalsReview` (accept / accept‑all / reject / refine / refine‑all — all existing).
2. **Regenerate with more context** — in that view, a “Generate more” panel to: add/replace the
   **requirement set** (picker), **select PR context** (Impact Analyses linked to those requirements),
   and add **custom input** (freeText). Submitting runs a **fresh generation round** that replaces the
   un‑accepted proposals; the workflow flips `RUNNING → AWAITING_REVIEW` with the new set.
3. **PR context resolution** — a read endpoint that, for the workflow’s requirements, lists the linked
   Impact Analyses (PR refs + summary/suggestions) to choose from; selected ones are injected into the
   regeneration prompt as a `## PR context` block.
4. **Finish review** — an action that auto‑rejects remaining `PROPOSED` proposals and marks the workflow
   `COMPLETED`, removing it from the queue.

**Out:** live GitHub PR fetching (PR context comes from stored Impact Analyses only); resuming the
non‑generation `AgentReviewRequest` path; changing the accept flow / `created_by=AGENT` invariant;
multi‑requirement workflow re‑planning beyond the generation node.

**Data (V24, additive/forward‑only):** `ai_generation_runs` gains
- `pr_context_json TEXT` — the selected Impact‑Analysis / PR context used for the latest round (audit).
- `requirement_ids_json TEXT` — the effective requirement set for regeneration (so “add requirements”
  persists on the run rather than mutating the shared workflow trigger).
- `regen_round INT NOT NULL DEFAULT 0` — increments each regeneration (audit / ordinal safety).

No new table; proposals/checkpoints reuse V8/V9.

**Endpoints** (agent module, under the existing generation base; all portal‑proxied):
- `GET  …/generations/{workflowId}` — proposals + run inputs (**exists**, VIEW_RESULTS).
- `GET  …/generations/{workflowId}/pr-context` — Impact Analyses linked to the workflow’s requirements
  (`{analysisId, prs[], summary}`), for selection (VIEW_RESULTS).
- `POST …/generations/{workflowId}/regenerate` — body `{ requirementIds?, impactAnalysisIds[], freeText? }`;
  replaces un‑accepted proposals with a fresh round (OPERATE_QUALITY).
- `POST …/generations/{workflowId}/finish` — auto‑reject remaining + mark COMPLETED (OPERATE_QUALITY).
- (existing) accept / accept‑all / reject / refine / refine‑all / answers.

## 3. Acceptance criteria
- An AWAITING_REVIEW generation workflow in the Review Queue shows a **Review** action; opening it lists
  that workflow’s proposals with accept / accept‑all / reject / refine (reachable outside the original
  session).
- “Generate more” with an augmented requirement set + selected PR context + custom input runs a fresh
  round: the previous **un‑accepted** proposals are gone, a **new** set appears, already‑accepted DRAFT
  cases are untouched, and the run records `pr_context_json` / `requirement_ids_json` / an incremented
  `regen_round`.
- PR‑context list returns only Impact Analyses whose `linkedRequirementIds` intersect the workflow’s
  requirements, each with its PR refs + summary; selecting one demonstrably changes the generated cases.
- **Finish review** marks the workflow COMPLETED, auto‑rejects remaining `PROPOSED`, and the item leaves
  the queue.
- Accepted proposals still become `PlatformTestCase` with `created_by='AGENT'`, status DRAFT (invariant
  preserved). Every new endpoint is RBAC‑guarded; V24 is additive/forward‑only; portal uses design
  tokens + `ui/` kit, `npx tsc -b` clean.

## 4. Commands
- **Backend:** `mvn -q -pl platform-agent -am test` (generation lives in platform‑agent); full module
  `mvn -pl platform-agent test`. **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Migrations:** Flyway **V24**, additive, validated via a rolled‑back psql BEGIN…ROLLBACK.
- **Deploy:** the `deploy-local` skill — rebuild `platform-agent` + `platform-portal` (core via `-am`),
  apply V24 via `db-migrate`, `up -d --no-deps`, verify.

## 5. Project structure (modules touched)
```
platform-core        # AiGenerationRun columns (V24) + entity accessors
platform-agent       # GenerationResumeService.regenerate/finish, TestCaseGenerationNode fresh-round
                     #   prompt assembly (requirements + PR-context block + custom input),
                     #   PrContextService (reverse-query ImpactAnalysis), TestCaseGenerationController endpoints
platform-portal(+FE) # BFF proxies + Review Queue 'Review' action + a WorkflowReviewModal reusing
                     #   ProposalsReview + a 'Generate more' panel (requirement picker, PR-context
                     #   selector, custom input) + 'Finish review'
```

## 6. Code style / testing / boundaries
- **Java:** constructor injection; `@RequireCapability(OPERATE_QUALITY|VIEW_RESULTS, scope="projectId")`;
  reuse `ProposalService`, `ContextAssembler`, `TestCaseGenerationNode`, `ImpactAnalysisRepository`,
  langchain4j orchestrator — don’t reinvent the AI call. JUnit5 + Mockito + AssertJ; strict stubs.
- **SQL:** additive, nullable, forward‑only; never edit a shipped migration.
- **Frontend:** Tailwind v4 design tokens + `ui/` kit; no raw palette/purple; hooks before early
  returns (Rules of Hooks); `tsc -b` gate; reuse `ProposalsReview`.
- **Testing:** cover PR‑context reverse‑resolution (only intersecting analyses), regenerate replacing
  only `PROPOSED` (accepted untouched) + persisting run inputs + incrementing `regen_round`, finish
  rejecting remaining + COMPLETED transition, and RBAC annotations. Migration validated via psql.

**Boundaries — Always:** RBAC‑guard every new endpoint; additive/forward‑only migrations; keep
`created_by='AGENT'` for accepted proposals; design‑token UI. **Ask first:** any change to the accept
flow or the workflow state machine beyond adding a COMPLETED transition; pulling **live** PR diffs
(out of scope now). **Never:** mutate/overwrite already‑`ACCEPTED` proposals or their test cases; log
secrets/PATs; bypass RBAC; edit a shipped migration.
