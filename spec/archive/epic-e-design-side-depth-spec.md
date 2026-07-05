# SPEC — Epic E: Design-side depth

> Status: **APPROVED — build in progress.** Fifth slice of the quality-cycle roadmap
> (`spec/quality-cycle-roadmap.md`). Epics A–D complete (archived under `spec/archive/`).

## 1. Objective

Reduce test-design duplication: **named test-data datasets** (reusable key/value sets, project/env
scoped) and a **shared-step library** (a case step can reference a shared step, so editing the shared
step updates every case that uses it).

### Locked decisions (roadmap)
1. **BDD/Gherkin is deferred** (behind a flag / out of scope) unless teams need Cucumber — not in Epic E.
2. **Shared-step propagation by reference** — a `test_case_step.shared_step_id` points at a
   `shared_step`; resolved content is read from the shared step, so edits propagate without touching
   each case.
3. **Dataset→matrix wiring is a deferrable seam** — datasets are fully managed/stored now; feeding a
   dataset into the run matrix is a one-method change in `TcmRunService.loadAxes` (documented, not built
   in Epic E to avoid reworking run creation). No high-risk gates in this epic.

### Grounding (verified against current code)
- `TestCaseStep` (id/testCaseId/stepNumber/action/expectedResult/notes) + `TestCaseStepRepository`
  (findByTestCaseIdOrderByStepNumberAsc / deleteAllByTestCaseId); steps built in
  `TestCaseManagementService.saveSteps/loadSteps`, DTO `TestCaseStepDto`.
- `MatrixType`/`PropertyMatrixService.expand(axes, type)`; axes come solely from
  `TcmRunService.loadAxes` (the dataset seam).
- CRUD pattern to mirror: `TestCaseTagController` (+ nested service), class VIEW_RESULTS / mutations
  OPERATE_QUALITY. Next Flyway = **V19** (dataset), then **V20** (shared_step + step column).

## 2. Scope

**In:**
1. **Datasets** — `test_dataset` (project, name, env?, rows JSON) CRUD + management UI.
2. **Shared steps** — `shared_step` (project, action, expected, notes) CRUD + a library UI; a case
   step may reference one (`shared_step_id`); read-time resolution overlays the shared content so edits
   propagate; the step editor can insert a shared-step reference.

**Out:** BDD/Gherkin; deep dataset→matrix expansion (documented seam only); dataset row typing beyond
key/value JSON.

**Data:**
- V19: `test_dataset` (`id`, `project_id` FK, `name`, `env`, `rows` TEXT(JSON), `created_at`,
  `updated_at`, UNIQUE(project_id,name,env)).
- V20: `shared_step` (`id`, `project_id` FK, `action`, `expected_result`, `notes`, timestamps);
  `test_case_steps.shared_step_id UUID`.

**Endpoints** (class VIEW_RESULTS; mutations OPERATE_QUALITY):
- `GET/POST/PUT/DELETE …/projects/{projectId}/datasets`
- `GET/POST/PUT/DELETE …/projects/{projectId}/shared-steps`
- Portal BFF proxies.

## 3. Acceptance criteria
- A dataset can be created/edited/deleted, scoped to a project (+ optional env); listed in a Datasets UI.
- A shared step can be created/edited/deleted; a case step can reference one; **editing the shared step
  changes the resolved content of every referencing case** (verified at read).
- Every endpoint RBAC-guarded; migrations additive/forward-only; portal design-token UI, `tsc -b` clean.
- (Documented follow-up) feeding a dataset into the run matrix via `TcmRunService.loadAxes`.

## 4. Commands / testing
- `mvn -q -pl <module> [-am] test`; `cd platform-portal/frontend && npx tsc -b`. Migrations validated via
  rolled-back psql. JUnit5 + Mockito + AssertJ; cover dataset CRUD scoping, shared-step CRUD, and step
  resolution overlay (referencing step shows current shared content).

## 5. Boundaries
**Always:** RBAC-guard endpoints; additive/forward-only migrations; design-token UI; resolve shared
steps by reference (don't copy content into the case). **Never:** build BDD; edit a shipped migration;
break the `created_by` origin invariant.
