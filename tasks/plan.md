# PLAN — Resume test-case generation workflow from the Review Queue

Spec: `SPEC.md`. Build order = dependency order. Each task: RED → GREEN → regression → build → 1 commit.
Generation lives in **platform-agent** (first work there this program) + platform-core + portal.

## Task 1 — V24: ai_generation_runs columns + entity accessors  ⟶ 1 commit
`ai_generation_runs` gains `pr_context_json TEXT`, `requirement_ids_json TEXT`, `regen_round INT NOT
NULL DEFAULT 0` (V24). `AiGenerationRun` gains fields + getters + a `recordRegenInputs(requirementIds
Json, freeText, prContextJson)` that bumps `regenRound`.
- RED: recordRegenInputs sets fields + increments regen_round; migration applies on rolled-back psql.

## Task 2 — PR-context resolution + endpoint  ⟶ 1 commit
`PrContextService.forWorkflow(projectId, workflowId)` → the workflow's requirement ids, then the
`ImpactAnalysis` rows whose `linkedRequirementIds` intersect them → DTO `{analysisId, prs[], summary}`.
`GET …/generations/{workflowId}/pr-context` (VIEW_RESULTS) + portal proxy.
- RED: returns only analyses intersecting the workflow's requirements; empty when none.

## Task 3 — Regenerate (fresh round replaces)  ⟶ 1 commit
`GenerationResumeService.regenerate(projectId, workflowId, RegenerateRequest{requirementIds?,
impactAnalysisIds[], freeText?})`: validate workflow AWAITING_REVIEW + owned by project; reject the
current PROPOSED proposals; assemble the PR-context block from the selected analyses; persist
`recordRegenInputs(...)` (requirement set + freeText+PR-context + pr_context_json); mark RUNNING; kick a
fresh generation round (async) that writes a NEW proposal set and re-parks AWAITING_REVIEW.
`POST …/generations/{workflowId}/regenerate` (OPERATE_QUALITY) + portal proxy. The node reads the
requirement set / augmented freeText from the run for the fresh round (accepted proposals untouched).
- RED: regenerate rejects existing PROPOSED (keeps ACCEPTED), persists inputs + increments regen_round,
  and invokes the (mocked) generation runner; unknown/foreign workflow → 404; non-AWAITING_REVIEW → 409.

## Task 4 — Finish review → COMPLETED  ⟶ 1 commit
`GenerationResumeService.finish(projectId, workflowId)`: reject remaining PROPOSED proposals, mark the
workflow COMPLETED. `POST …/generations/{workflowId}/finish` (OPERATE_QUALITY) + portal proxy.
- RED: finish rejects remaining PROPOSED, marks workflow COMPLETED; ACCEPTED untouched.

## Task 5 — Portal BFF proxies + api + types  ⟶ 1 commit
Portal proxies for `pr-context` (GET), `regenerate` (POST), `finish` (POST). `api.ts` methods +
`types.ts` (PrContextItem, RegenerateForm). RBAC enforced downstream (agent).
- Verify: portal compiles; `tsc -b` clean.

## Task 6 — Review Queue action + WorkflowReviewModal  ⟶ 1 commit
The `WORKFLOW` / AWAITING_REVIEW card in `ReviewQueuePage` gets a **Review** action opening a
`WorkflowReviewModal` that loads `getGeneration(workflowId)` and mounts the existing `ProposalsReview`
(accept / accept-all / reject / refine) — reachable outside the original session.
- Verify: `tsc -b` clean; modal lists a workflow's proposals with the existing curate actions.

## Task 7 — Generate-more panel + Finish review  ⟶ 1 commit
In `WorkflowReviewModal`: a "Generate more" panel (requirement picker, PR-context selector via
`pr-context`, custom input) → `regenerate`; and a **Finish review** button → `finish`. Design tokens +
`ui/` kit.
- Verify: `tsc -b` clean.
