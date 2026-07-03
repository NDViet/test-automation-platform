# Plan — Fix test-case delete + preserve AI origin on approval

Spec: `SPEC.md`. Branch `fix/test-case-delete-and-ai-origin`. Decisions: hard delete + confirm;
fix-forward + backfill; no approver column.

Backend gate: `mvn -q -pl platform-agent -am test` (+ `platform-core` compiles migration).
Frontend gate: `cd platform-portal/frontend && npx tsc -b`. One commit per task.

## Task 1 — Backend: preserve AI origin on proposal accept  ⟶ 1 commit
**File:** `platform-agent/.../proposals/ProposalService.java` (+ test). **Deps:** none.
- `acceptEntity(p, actor)`: build the `PlatformTestCase` with `createdBy="AGENT"` (never `actor`).
  Keep `actor` only for audit/logging; it must not populate `createdBy`.
- RED: `ProposalServiceTest.acceptStampsAgentOriginNotApprover` — accepting a PROPOSED proposal with
  `actor="admin"` saves a `PlatformTestCase` whose `createdBy == "AGENT"` (captured via the
  test-case repo save), and the proposal is marked accepted.
- GREEN: change the one expression. Gate: `mvn -q -pl platform-agent -am test` green.

## Task 2 — Migration V11: backfill mis-stamped origin  ⟶ 1 commit
**File:** `platform-core/src/main/resources/db/migration/V11__fix_ai_testcase_origin.sql`. **Deps:** none.
- `UPDATE platform_test_cases SET created_by='AGENT' WHERE created_by NOT IN ('AGENT','HUMAN');`
  (idempotent; additive; forward-only).
- Verification (per SPEC §3): after apply, `SELECT count(*) … WHERE created_by NOT IN
  ('AGENT','HUMAN')` = 0 — checked at deploy via psql (data migration; no unit runner for SQL here).
- Gate: `mvn -q -pl platform-core -DskipTests package` compiles resources.

## Task 3 — Frontend: wire a Delete action for test cases  ⟶ 1 commit
**File:** `platform-portal/frontend/src/pages/TestCasesPage.tsx`. **Deps:** none (api already exists).
- Add a `Trash2` delete action (list-row and/or the case detail view) → `window.confirm(...)` warning
  it's permanent and removes steps/executions/history → `useMutation(() =>
  api.deleteTestCase(projectId, tcId))` → invalidate the page's test-case query keys; close the detail
  if the deleted case was open. Mirror the confirm+delete pattern used for suites/runs/projects.
- Handle error (e.g. 403) with the existing error surface; don't crash.
- Gate: `npx tsc -b`. (No SPA unit runner — type-check + manual.)

## Out of scope (SPEC §2 / §9)
Soft-delete/deprecate, approver column, bulk delete/undo. Deploy (apply V11, rebuild agent+portal)
is a separate step after the build.
