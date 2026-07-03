# Plan — Enhance Impact Analyses (editable jobs, richer context, PR filter/search)

Spec: `SPEC.md`. Base branch `feat/impact-analyses-edit-rerun` already includes the prior impact fixes
(GitHub-tab Codebase listing + credential-cascade token). Locked decisions: re-run **replaces** result;
**single** `customPrompt` field; diff depth = filenames + PR body + **Markdown-file diffs** (bounded).

Backend gate: `mvn -q -pl platform-agent -am test` (+ `platform-core` compiles the entity/migration).
Frontend gate: `cd platform-portal/frontend && npx tsc -b` (no SPA unit runner). One commit per task.

## Task 1 — Schema + entity: `custom_prompt`  ⟶ 1 commit
**Files:** `platform-core/.../domain/ImpactAnalysis.java`,
`platform-core/src/main/resources/db/migration/V10__impact_analysis_custom_prompt.sql`. **Deps:** none.
- Flyway `V10`: `ALTER TABLE impact_analyses ADD COLUMN custom_prompt text;` (additive, nullable).
- Entity: `customPrompt` field (`@Column(name="custom_prompt", columnDefinition="text")`) + getter/setter.
- RED: a small `ImpactAnalysis` entity test (set/get `customPrompt`, default null). GREEN: add field.
- Gate: `mvn -q -pl platform-core test` compiles + passes.

## Task 2 — Backend update + re-run endpoint  ⟶ 1 commit
**Files:** `platform-agent/.../api/ImpactAnalysisController.java` (+ test),
`platform-portal/.../api/PortalImpactController.java`. **Deps:** Task 1.
- `PUT /{projectId}/{id}` (`update`): load by id; if `projectId` mismatch → `404`; if status `RUNNING`
  → `409`; else replace `name?`, `linkedPrs`, `linkedRequirementIds`, `customPrompt`, set `RUNNING`,
  save, call `impactAnalysisService.runAnalysis(id, projectId)`; return updated job (`200`).
- Portal proxy: `PUT /{id}` → `agentClient.put().uri("/hub/impact/{projectId}/{id}")`.
- RED (new `ImpactAnalysisControllerUpdateTest`, Mockito): replace-fields + re-run invoked; `409` on
  RUNNING; `404` on mismatch. GREEN: implement endpoint.
- Gate: agent test green.

## Task 3 — Richer AI context in `runAnalysis`  ⟶ 1 commit
**File:** `platform-agent/.../api/ImpactAnalysisService.java` (+ test). **Deps:** Task 1.
- Extend PR loop: include PR **title + body** (from linked-PR metadata `prBody`); from the PR-files
  response include **`patch` for `*.md`/`*.markdown`** files only (bounded ≤ 8 KB/file, ≤ 32 KB total);
  non-md stay filename-only. Append `customPrompt` as explicit user instructions in the prompt.
- Bounds are constants; assembly never throws on missing body/patch (degrade).
- RED (extend `ImpactAnalysisServiceTest`): asserts PR body present, `.md` patch included &
  non-md patch excluded, `customPrompt` injected, truncation applied over cap (assert on the built
  user message via a seam — extract `buildUserMessage` to package-private for direct assertion, or
  verify inputs). GREEN: implement.
- Gate: `mvn -q -pl platform-agent test` green (regression across all agent tests).

## Task 4 — Frontend api + types  ⟶ 1 commit
**Files:** `platform-portal/frontend/src/lib/{api.ts,types.ts}`. **Deps:** Task 2.
- `api.updateImpactAnalysis(projectId, id, form)` → `PUT /projects/{projectId}/impact-analyses/{id}`.
- Extend `CreateImpactAnalysisForm` with `customPrompt?`; `LinkedPr` with `prBody?`; `CodabasePr`
  already has `body`. Gate: `npx tsc -b`.

## Task 5 — Frontend PR filter dropdown + search  ⟶ 1 commit
**File:** `platform-portal/frontend/src/pages/ImpactAnalysesPage.tsx`. **Deps:** Task 4.
- In the PR step of `CreateModal`: a repo `<Select>` (options from loaded PRs' `repoFullName`, + "All
  repos") and a search `<Input>` (match PR title or `#number`). Compose with selection. Tokens + `ui/`.
- Gate: `npx tsc -b`.

## Task 6 — Frontend edit job + instructions field  ⟶ 1 commit
**File:** `platform-portal/frontend/src/pages/ImpactAnalysesPage.tsx`. **Deps:** Task 4 (5 ideally).
- `CreateModal` doubles as edit: accept an optional `existing` job → seed name, selected PRs
  (`linkedPrs`), requirements (`linkedRequirementIds`), and `customPrompt`; capture PR `body` into the
  selection so it round-trips. Add an **"Instructions for the AI"** `<textarea>` (create + edit).
- An **Edit** action (list row / detail panel) opens the modal in edit mode; Save →
  `updateImpactAnalysis` → job goes `RUNNING`, list/detail refetch shows the new result.
- Gate: `npx tsc -b`.

## Out of scope (SPEC §2 / §9)
Versioning/history, augment-merge, non-md full patches, threaded prompts, suggestion-schema changes,
auto-re-run triggers. Deploy (rebuild agent+portal, apply V10) is a separate step after the build.
