# SPEC — Fix: test-case delete + preserve AI origin on approval

> Status: **DRAFT — awaiting confirmation.** Two bug fixes. Prior spec archived at
> `spec/archive/impact-analyses-edit-rerun-spec.md`.

## 1. Objective

Fix two defects in test-case management:

1. **A test case cannot be deleted.** The delete API works end-to-end (`api.deleteTestCase` →
   portal `DELETE /test-cases/{tcId}` → ingestion `@DeleteMapping` guarded by `OPERATE_QUALITY` →
   `TestCaseManagementService.delete`; a raw DB delete succeeds and children cascade). **But no
   frontend UI ever calls `api.deleteTestCase`** — users have no way to trigger a delete.
2. **Approving an AI-generated test case loses its "created by AI" origin.** `ProposalService.
   acceptEntity` builds `new PlatformTestCase(…, actor != null ? actor : "AGENT", …)` — passing the
   **approver** (`actor`) as `createdBy`. `createdBy` is an origin marker (`AGENT | HUMAN`), so the
   approver's username leaks in. **Confirmed in data: 17 rows have `created_by='admin'`** (vs 71
   `AGENT`); the only non-role value present is `admin`.

### Target users
- QA leads / engineers managing the catalog — need to delete obsolete cases and trust that
  AI-authored cases stay attributed to the AI after approval.

### Root causes (verified)
- Delete: no wired UI action (backend fully functional; entity is flat; FK children use
  `ON DELETE CASCADE`; raw `DELETE` returned `DELETE 1`).
- Origin: `platform-agent/.../proposals/ProposalService.acceptEntity` uses the approver as
  `createdBy`. The direct approve path (`TestCaseManagementService.approve` → `tc.approve()`)
  correctly preserves `createdBy`, so **only the proposal-acceptance path is wrong**.

### Locked decisions (defaults — confirm or override)
1. **Hard delete** (matches the existing endpoint), gated behind an explicit confirm that warns it is
   permanent and also removes the case's steps / executions / run history (cascade).
2. **Fix forward + one-time backfill**: `acceptEntity` sets `createdBy="AGENT"`; a migration restores
   existing bug rows (`created_by NOT IN ('AGENT','HUMAN')`) to `'AGENT'`.
3. **Preserve AI origin only** — no new approver column; who-approved is already recorded by the
   review-queue / work-item flow.

## 2. Scope

### In scope
- **Backend (agent):** `ProposalService.acceptEntity` → `createdBy` is always `"AGENT"` for an
  accepted AI proposal (never the actor). Keep `markAccepted`, steps, requirement linking unchanged.
- **Migration (core) `V11`:** backfill `UPDATE platform_test_cases SET created_by='AGENT' WHERE
  created_by NOT IN ('AGENT','HUMAN')`.
- **Frontend (portal):** a **Delete** action for a test case (list-row action and/or the detail/edit
  view) wired to `api.deleteTestCase`, behind a clear confirm; invalidate the test-case queries on
  success; handle a `403` (insufficient capability) gracefully. Design tokens + `ui/` kit.

### Out of scope (deferred)
- Soft-delete / deprecate workflow (unless chosen instead of hard delete).
- A dedicated approver / decided-by column on the test case.
- Bulk delete, undo/restore, or trash view.
- Any change to the direct approve endpoint or the generation flow beyond the origin fix.

## 3. Acceptance criteria

**Delete**
- [ ] Each test case exposes a **Delete** action (row and/or detail) that calls
      `DELETE /projects/{projectId}/test-cases/{tcId}`; on success the case is removed from the list
      (React Query invalidated) and any open detail closes.
- [ ] A confirm dialog states the delete is **permanent** and removes the case's steps/executions/history.
- [ ] Deleting a case that has steps / tags / executions succeeds (cascade) — no error surfaced.
- [ ] The endpoint's `OPERATE_QUALITY` capability guard is unchanged; a `403` is shown as a friendly
      error, not a crash.

**AI origin**
- [ ] Accepting/approving an AI-generated proposal yields a test case with `createdBy='AGENT'`,
      regardless of who approves.
- [ ] No newly created test case stores a username in `created_by` (only `AGENT` / `HUMAN`).
- [ ] After `V11`, `SELECT count(*) FROM platform_test_cases WHERE created_by NOT IN ('AGENT','HUMAN')`
      returns **0**.
- [ ] The direct approve endpoint (`/test-cases/{tcId}/approve`) still preserves the case's existing
      `createdBy` (regression guard).

## 4. Approach (sketch — not binding)
- `ProposalService.acceptEntity(p, actor)`: replace `actor != null ? actor : "AGENT"` with `"AGENT"`.
  (The `actor` parameter stays for logging/audit only; it must never populate `createdBy`.)
- `platform-core/.../db/migration/V11__fix_ai_testcase_origin.sql`: the backfill `UPDATE`.
- Frontend: add a `Trash2` action in `TestCasesPage` (and/or the case detail modal) →
  `confirm(...)` → `useMutation(api.deleteTestCase)` → invalidate `['test-cases', projectId]`
  (match the page's existing query keys). Mirror the confirm+delete pattern already used for suites /
  runs / projects.

## 5. Commands
- **Backend build/test:** `mvn -q -pl platform-agent -am test` (ProposalService + regression) and
  `-pl platform-core` (migration compiles). Deploy build: `mvn -q -pl platform-agent,platform-portal
  -am package -DskipTests`.
- **Migration** applies via the `db-migrate` (Flyway) service (`db_migration` volume binds
  `platform-core/.../db/migration`).
- **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Deploy (local docker-compose):** apply `V11` (run `db-migrate`), rebuild + recreate
  `platform-agent` and `platform-portal` (use `--no-deps` to leave data volumes untouched).

## 6. Project structure (touched)
```
platform-agent/.../proposals/ProposalService.java            # createdBy='AGENT' on accept
platform-agent/.../proposals/ProposalServiceTest.java        # new/updated test
platform-core/src/main/resources/db/migration/V11__fix_ai_testcase_origin.sql   # backfill
platform-portal/frontend/src/pages/TestCasesPage.tsx         # Delete action (+ detail if applicable)
# unchanged (already correct/working):
platform-portal/frontend/src/lib/api.ts                      # deleteTestCase exists
platform-portal/.../api/PortalTestCaseController.java         # DELETE proxy exists (line 215)
platform-ingestion/.../tcm/TestCaseManagementController.java  # @DeleteMapping guarded, works
```

## 7. Code style / conventions
- **Java:** minimal change; `createdBy` must remain within the `AGENT | HUMAN` vocabulary; don't widen
  the column; keep the accept flow transactional.
- **SQL:** additive, forward-only Flyway (`V11`); the backfill is idempotent (re-running is a no-op).
- **Frontend:** Tailwind v4 design tokens + `ui/` primitives (`Button`); real `<button>` + confirm;
  invalidate the exact query keys the page uses; no raw palette / no purple.

## 8. Testing strategy
- **Agent unit test:** `acceptEntity` produces `createdBy='AGENT'` even when `actor="admin"`; proposal
  is marked accepted and steps are created (mirrors existing `ProposalService`/generation tests).
- **Migration:** assert the post-migrate invariant via psql (`… NOT IN ('AGENT','HUMAN')` = 0) during
  verification; optionally a data-JPA test.
- **Regression:** full `mvn -q -pl platform-agent test`; ingestion delete behavior unchanged.
- **Frontend:** `npx tsc -b`; manual — delete a case (with steps/executions) → gone; cancel confirm →
  no-op; approve an AI proposal → origin stays AI.

## 9. Boundaries
**Always**
- Keep the `OPERATE_QUALITY` capability guard on delete; confirm before the (irreversible) hard delete.
- Keep `created_by` restricted to `AGENT` / `HUMAN`.
- Preserve the direct approve/generation behavior; keep accept transactional.

**Ask first**
- Switching delete to soft-delete/deprecate instead of hard delete.
- Adding an approver/decided-by column, or any schema change beyond the additive `V11` backfill.
- Bulk delete / undo.

**Never**
- Delete a test case without an explicit confirm.
- Store a username (or anything other than `AGENT`/`HUMAN`) in `created_by`.
- Edit an already-shipped Flyway migration; break the generation or approve flow.

## 10. Open questions (defaults chosen; override on review)
- Delete type: **hard delete + confirm** (vs deprecate). Hard delete removes execution history.
- Backfill: **yes** — restore 17 `admin` rows to `AGENT`.
- Approver tracking: **not added** (review/work-item flow already records the approver).
- Delete placement: list-row action **and** case detail (confirm on both) — adjust if you prefer one.
