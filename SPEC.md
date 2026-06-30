# SPEC — Review & Iterative Refinement of AI-Generated Test Cases

> Status: **DRAFT — awaiting confirmation**
> Scope: `platform-core`, `platform-agent`, `platform-portal` (+ frontend).
> Prior specs archived under `spec/archive/` (interactive AI generation, LiteLLM
> gateway, manual test execution — all implemented).

## 1. Objective

Today, when AI manual test-case generation finishes, `TestCaseGenerationNode.finishFromClaude()`
**immediately writes every generated case into `platform_test_cases` as `DRAFT`**. The user never
gets to review the output, refine it, or accept cases selectively — the catalog is populated whether
the results are good or not.

This feature inserts a **post-generation review & refinement stage**. After the AI finishes, the
generated cases land in a **separate staging area as "proposals"** (never in the catalog yet). The
user reviews the proposed list and, **per case**, can **accept** (→ becomes a `DRAFT` in the
catalog), **reject** (discarded), or **refine** (give a free-text instruction; the AI revises that
case by **continuing the same generation conversation**). Batch **Accept all** / **Refine all** are
also available. Only accepted proposals ever become real test cases.

This is distinct from, and complementary to, the existing **clarification rounds** (`ask_user` /
`AWAITING_INPUT`) which happen *before* generation. This new stage happens *after* generation.

### Target users
- **Testers** (capability `OPERATE_QUALITY` on the project) — the people who trigger generation and
  curate the results. All new endpoints are gated to `OPERATE_QUALITY` scoped to the project,
  consistent with the existing generation endpoints.

## 2. Resolved design decisions (from clarification)
1. **Staging:** a **separate staging table** (`generated_test_case_proposals`), tied to the
   generation run / workflow. Proposals are **not** in `platform_test_cases`. A proposal is written
   to `platform_test_cases` (as `DRAFT`) only on accept.
2. **Review actions:** **per-case accept / reject / refine**, plus batch **accept-all** and
   **refine-all**.
3. **Refine engine:** **continue the conversation** — reuse the existing resume/checkpoint
   mechanism (`GenerationResumeService` + LangChain checkpointing) so the LLM keeps full context
   (requirements, prior clarifications); the refine instruction is appended and the AI returns the
   revised case(s), which **update the proposal in place** (status stays `PROPOSED`).
4. **On accept:** the case enters the catalog as **`DRAFT`**; the existing
   `DRAFT → UNDER_REVIEW → APPROVED → DEPRECATED` lifecycle continues unchanged afterwards.

## 3. Core features & acceptance criteria

Each AC is independently testable.

### F1 — Generation lands in staging, not the catalog
- **AC1.1** When generation finishes, the generated cases are written to
  `generated_test_case_proposals` (status `PROPOSED`), **not** to `platform_test_cases`.
- **AC1.2** No `platform_test_cases` row exists for a generation run until the user accepts at least
  one proposal. (Regression guard: a completed generation produces 0 catalog rows by itself.)
- **AC1.3** The workflow ends in a **review-pending** state (reuse `AWAITING_REVIEW`, or a dedicated
  generation state) rather than `COMPLETED`, so the UI knows to show the review panel.
- **AC1.4** Each proposal records: project id, workflow/generation-run id, ordinal, title,
  description, preconditions, expected result, priority, `sourceRequirementId`, the ordered steps
  (action / expected / notes), and `status` (`PROPOSED` | `ACCEPTED` | `REJECTED`).

### F2 — Review list
- **AC2.1** `GET …/generations/{workflowId}/proposals` returns the proposals for that run with their
  full content + status, ordered by ordinal.
- **AC2.2** The frontend generation panel, on review-pending, renders the proposed cases (title,
  priority, source requirement, expandable steps) with per-case **Accept / Reject / Refine** controls
  and batch **Accept all** / **Refine all**.

### F3 — Accept (per-case and batch)
- **AC3.1** Accepting a `PROPOSED` proposal creates one `platform_test_cases` row (status `DRAFT`,
  `createdBy` = the **JWT principal**, `agentSessionId` = workflow id, `sourceRequirementId` +
  linked requirement preserved) plus its `test_case_steps`, then marks the proposal `ACCEPTED` and
  links `accepted_test_case_id`.
- **AC3.2** Accept is **idempotent**: accepting an already-`ACCEPTED` proposal does not create a
  duplicate catalog row.
- **AC3.3** **Accept all** accepts every remaining `PROPOSED` proposal in one call.
- **AC3.4** A `REJECTED` proposal cannot be accepted (409/400).

### F4 — Reject (per-case and batch)
- **AC4.1** Rejecting a `PROPOSED` proposal marks it `REJECTED`; it is excluded from the active
  review list and never enters the catalog.
- **AC4.2** A rejected proposal stays rejected (no resurrection) — keep it simple.

### F5 — Refine (continue the conversation)
- **AC5.1** `POST …/generations/{workflowId}/proposals/{proposalId}/refine` with a free-text
  `instruction` resumes the generation conversation (via the checkpoint), scoped to that proposal,
  and the AI returns a revised version that **updates the proposal in place** (still `PROPOSED`).
- **AC5.2** Refine preserves prior context — the LLM sees the original requirement(s), earlier
  clarification answers, and the current proposal content; the instruction is additive.
- **AC5.3** **Refine all** applies one instruction to all remaining `PROPOSED` proposals (batch
  resume), updating each in place.
- **AC5.4** Refinement is **async** and streams progress on the existing
  `gen:progress:{workflowId}` channel / `/ws/generations/{workflowId}` socket; the panel reflects
  "refining…" then shows the updated case.
- **AC5.5** An `ACCEPTED`/`REJECTED` proposal cannot be refined (only `PROPOSED`).

### F6 — Authorization & audit
- **AC6.1** Every new endpoint requires `OPERATE_QUALITY` on the `projectId` (platform RBAC model);
  unauthenticated → 401/403, wrong-project tester → 403.
- **AC6.2** Accept records the accepting user (`createdBy` from `CurrentUser`, never a header).

### F7 — Backward compatibility
- **AC7.1** The existing clarification-before-generation flow (`ask_user`, `AWAITING_INPUT`,
  `…/answers`) is unchanged.
- **AC7.2** The existing test-case lifecycle endpoints (`submit-review`, `approve`, `reject`) and the
  Review Queue are unchanged; accepted drafts flow into them as before.

## 4. Architecture & where it hooks in

### Data model — Flyway `V8__generated_test_case_proposals.sql` (next version after V7)
```
generated_test_case_proposals
  id              UUID PK
  workflow_id     UUID NOT NULL          -- FK agent_workflows / ai_generation_runs.workflow_id
  project_id      UUID NOT NULL          -- FK projects
  ordinal         INT  NOT NULL
  title           TEXT NOT NULL
  description     TEXT
  preconditions   TEXT
  expected_result TEXT
  priority        VARCHAR(20)
  source_requirement_id  VARCHAR
  steps_json      TEXT NOT NULL          -- ordered [{action,expectedResult,notes}] as JSON;
                                         --  expanded into test_case_steps rows on accept
  status          VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'  -- PROPOSED|ACCEPTED|REJECTED
  accepted_test_case_id UUID             -- set on accept (link to platform_test_cases row)
  created_at / updated_at TIMESTAMPTZ
  UNIQUE (workflow_id, ordinal)
```
Steps are stored as JSON in staging for simplicity; they are expanded into real `test_case_steps`
rows only on accept.

### Backend (platform-agent — generation lives here)
- **`TestCaseGenerationNode.finishFromClaude()`**: change the terminal behavior — instead of
  `testCaseRepo.save(DRAFT)` + steps, write `generated_test_case_proposals` rows and mark the
  workflow review-pending. This is the **single most important change**.
- **New `ProposalService`** (platform-agent): list / accept / accept-all / reject / refine /
  refine-all. Accept creates the `PlatformTestCase` (DRAFT) + `TestCaseStep`s (reuse the existing
  repos/mapping). Refine calls into the resume path.
- **Refine via `GenerationResumeService`**: add a "refine proposal(s)" resume mode that injects the
  instruction + the targeted proposal id(s) into the resumed conversation and routes the revised
  output back to update proposals (not to create drafts).
- **New endpoints** on `TestCaseGenerationController` (or a sibling controller), all
  `@RequireCapability(OPERATE_QUALITY, scope="projectId")`:
  - `GET    /hub/test-cases/{projectId}/generations/{workflowId}/proposals`
  - `POST   …/proposals/{proposalId}/accept`
  - `POST   …/proposals/{proposalId}/reject`
  - `POST   …/proposals/{proposalId}/refine`        body `{ instruction }`
  - `POST   …/proposals/accept-all`
  - `POST   …/proposals/refine-all`                 body `{ instruction }`

### Frontend (platform-portal/frontend)
- **`GenerationProgress.tsx`**: when the run is review-pending, render a new **`ProposalsReview`**
  component instead of the "New DRAFT test cases were created" banner.
- **`ProposalsReview.tsx`** (new): the proposed list with per-case Accept / Reject / Refine (refine
  opens a small instruction box) and batch Accept all / Refine all; reflects streaming refine
  progress; on accept shows the case moving to the catalog.
- **`lib/api.ts`** + **`lib/types.ts`**: `listProposals`, `acceptProposal`, `rejectProposal`,
  `refineProposal`, `acceptAllProposals`, `refineAllProposals`, and a `Proposal` type.
- **Portal BFF**: proxy the new `/hub/...` endpoints (the portal forwards the JWT already).

## 5. Commands (this repo)
- **Backend build/test (per module, deps not in .m2):**
  `mvn -pl <module> -am "-Dtest=X" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  (PowerShell: quote the `-D` args). Full regression: `mvn test`.
- **Frontend typecheck:** `cd platform-portal/frontend && npx tsc -b` (no JS test runner in repo —
  tsc is the gate, plus browser smoke).
- **DB migration** runs via the `db-migrate` Flyway job on `docker compose --profile services up`.
- **Deploy/iterate locally:** host `mvn -pl <svc> -am -DskipTests package` →
  `docker compose --profile services build <svc>` → `docker compose --profile services up -d <svc>`.

## 6. Project structure (where changes go)
- `platform-core` — `GeneratedTestCaseProposal` entity + repository; `V8__…sql` migration.
- `platform-agent` — node terminal change, `ProposalService`, resume "refine" mode, new endpoints,
  DTOs.
- `platform-portal` — BFF proxy controller methods; `frontend/src/components/ProposalsReview.tsx`,
  edits to `GenerationProgress.tsx`, `lib/api.ts`, `lib/types.ts`.

## 7. Code style
- Match the surrounding code: Spring constructor injection; records for DTOs; `ResponseStatusException`
  for 4xx; google-java-format (spotless) for Java; existing Tailwind/React patterns and the
  `api`/react-query conventions on the frontend. Reuse existing helpers
  (`CurrentUser.username()` for the actor, `@RequireCapability` for authz, the
  `GenerationProgressPublisher` for streaming). No new frameworks.

## 8. Testing strategy (TDD per slice)
- **Backend unit (Mockito):** `ProposalService` — accept creates a DRAFT + steps and marks
  `ACCEPTED` (AC3.1), accept idempotency (AC3.2), reject (AC4.1), accept/refine guards on
  rejected/accepted (AC3.4/AC5.5), accept-all (AC3.3). Node terminal change — finishing writes
  proposals, not drafts (AC1.1/AC1.2) — assert `proposalRepo.save` and **no** `testCaseRepo.save`.
- **Endpoint annotation tests:** the new endpoints carry `@RequireCapability(OPERATE_QUALITY,
  "projectId")` (reflection test, matching the existing `*RbacAnnotationTest` pattern).
- **Migration pre-flight:** apply `V8` against a seeded run in a rolled-back transaction.
- **Frontend:** `npx tsc -b` green; browser smoke — generate → review panel → refine one case
  (updates in place) → accept one (appears in catalog as DRAFT) → reject one (gone) → accept-all.
- **Regression:** full `mvn test` green; existing clarification + lifecycle suites unaffected.

## 9. Boundaries

**Always**
- Keep generated cases out of `platform_test_cases` until explicitly accepted.
- Gate every new endpoint with `OPERATE_QUALITY` on the project; derive the actor from the JWT
  (`CurrentUser`), never a header.
- Reuse the existing generation conversation/checkpoint for refinement (no parallel LLM plumbing).
- One Flyway migration, additive (`V8`); TDD with a passing test + its own commit per slice.

**Ask first**
- Any change to the **status enum** of `platform_test_cases` or the existing lifecycle transitions.
- Adding a brand-new workflow status vs. reusing `AWAITING_REVIEW`.
- Auto-expiry / cleanup policy for old unaccepted proposals (if wanted, decide retention first).
- Storing steps as a child table instead of `steps_json` in staging (if normalization is preferred).

**Never**
- Never persist a proposal directly as `APPROVED` (accept = `DRAFT` only).
- Never auto-accept or auto-reject on the user's behalf (no silent catalog writes).
- Never break the existing clarification-before-generation flow, the lifecycle endpoints, or the
  Review Queue.
- Never trust a client-supplied actor/role; the backend remains the enforcement point.

## 10. Out of scope (this iteration)
- Editing proposal fields by hand in the UI before accept (refinement is via AI instruction only).
- Resurrecting rejected proposals.
- Bulk diff/compare views; proposal versioning history beyond "current content".
- Changing how generation is triggered or how clarification rounds work.
