# SPEC — Epic D: Governance & auditability

> Status: **APPROVED — build in progress.** Fourth slice of the quality-cycle roadmap
> (`spec/quality-cycle-roadmap.md`). Epics A–C complete (archived under `spec/archive/`).

## 1. Objective

Make quality actions **accountable and reversible-to-inspect**: record who did what (with the **portal
user** as actor), surface a project **Audit Log**, keep **test-case version history** with a diff, and
add **bulk operations** over selected cases.

### Locked decisions (from the roadmap + grounding)
1. **Portal-user actor** — audit currently only captures API-key actors; the aspect sees a null actor
   for portal (JWT) calls. Add `actor_user_id` + `actor_username` to `AuditEvent` and capture the
   `AuthenticatedUser` principal.
2. **Project-scope audit** — `AuditEvent` has no `project_id` today; add one so the log is
   project-scoped (cleaner than joining `resource_id`).
3. **Version history is greenfield** — new `test_case_revision` (jsonb snapshot of title/steps/status/
   tags), snapshotted on save; no `@Version` optimistic locking added.
4. **Capabilities** — view audit log = VIEW_RESULTS; bulk approve/tag/assign/delete = OPERATE_QUALITY
   (parity with the existing single-item test-case mutations).

### ⚠️ Sign-off gate (mid-epic)
Tasks **D1–D7** are additive (audit, log, history, non-destructive bulk). Task **D8 (bulk delete)** is
**destructive/irreversible data** — the build **pauses after D7** and asks before D8.

### Grounding (verified against current code)
- `AuditEvent` (immutable builder) + `AuditEventRepository` (team/type/date finders only — no project/
  resource filter). `AuditAspect` writes eventType/resourceType only (no resourceId/details), actor
  from `ApiKey`; portal JWT sets `AuthenticatedUser` as principal, `details==null`.
- `PlatformTestCase` scalar fields + child tables `test_case_steps`, `test_case_tags`. Edit does
  delete-then-recreate steps; `delete` leaves tags orphaned (fix in bulk delete). Snapshot = scalars +
  steps + tags. jsonb via `@JdbcTypeCode(SqlTypes.JSON)` is the in-repo pattern.
- `TestCaseManagementService.update/approve/reject/delete` + controller (all mutations OPERATE_QUALITY,
  base `/api/v1/projects/{projectId}/test-cases`). No bulk/history/audit-read exists.
- Next Flyway = **V17** (audit columns), then **V18** (revision).

## 2. Scope

**In (D1–D7):**
1. `AuditEvent.projectId/actorUserId/actorUsername` (V17); `AuditAspect` captures the portal user.
2. Audited direct-writes on test-case approve/reject/delete/edit (actor via `CurrentUser`, projectId,
   resourceId, JSON details).
3. Audit Log query (project-scoped, filter by eventType/resourceType/date) + endpoint + portal proxy.
4. Audit Log UI page + route + nav.
5. `test_case_revision` (V18) + snapshot-on-save (update/approve/reject).
6. Revisions endpoint + History section on the case detail (diff-friendly list).
7. Bulk approve/tag/assign endpoint + service + UI multi-select bar + portal proxy.

**In (D8 — after sign-off):** bulk **delete** action (with tag cleanup) + UI delete.

**Out:** full field-level temporal tables; audit of every read; optimistic-locking `@Version`.

**Data:**
- V17: `audit_event` (a.k.a. the events table) gains `project_id UUID`, `actor_user_id UUID REFERENCES
  users(id)`, `actor_username VARCHAR(100)`.
- V18: `test_case_revision` (`id`, `test_case_id` FK, `project_id`, `version` int, `snapshot` jsonb,
  `changed_by` varchar, `change_kind` varchar, `changed_at`).

**Endpoints** (RBAC-guarded):
- `GET …/projects/{projectId}/audit-log?eventType&resourceType&since&limit` — VIEW_RESULTS.
- `GET …/projects/{projectId}/test-cases/{tcId}/revisions` — VIEW_RESULTS.
- `POST …/projects/{projectId}/test-cases/bulk` — `{ids, action, ...}` (OPERATE_QUALITY).
- Portal BFF proxies.

## 3. Acceptance criteria
- Approve/reject/delete/edit + gate-override record an audit event with the **portal user** as actor,
  scoped to the project; the Audit Log lists + filters them.
- Editing/approving a case creates a **revision**; the case's History shows prior snapshots.
- Bulk approve/tag/assign works over selected cases; (after sign-off) bulk delete works + cleans tags.
- Every endpoint RBAC-guarded; migrations additive/forward-only; portal design-token UI, `tsc -b` clean.

## 4. Commands / testing
- `mvn -q -pl <module> [-am] test`; `cd platform-portal/frontend && npx tsc -b`. Migrations validated
  via rolled-back psql. JUnit5 + Mockito + AssertJ; cover actor capture, audit write on each action,
  revision snapshot, bulk iteration + per-id ownership.

## 5. Boundaries
**Always:** RBAC-guard endpoints; audit sensitive actions with the real portal actor; additive/forward-
only migrations; per-id `loadAndVerify` project ownership in bulk ops; design-token UI. **Sign-off:**
bulk delete (D8). **Never:** put a username in `created_by`/`updated_by` as identity-of-record (actor
lives in audit/`changed_by`); log secrets; edit a shipped migration; bulk-delete without tag cleanup.
