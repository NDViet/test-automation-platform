# SPEC — Manual Test Execution (release/sprint/team/area scoped)

> Status: CONFIRMED — ready for planning/implementation.
> Scope: `platform-core`, `platform-ingestion`, `platform-portal` (+ frontend).
> Prior spec (LiteLLM gateway, implemented) archived at `spec/archive/litellm-spec.md`.

## 1. Objective

Let a QA engineer run a **manual test execution** against a defined scope, work through it
case‑by‑case over multiple sittings, and close it out with evidence and defect links.

Most of the foundation already exists (`test_runs`, `test_case_executions`, scope dimensions,
suite/case pickers, the release board, `BlobStore`, ADO read client). This spec fills the gaps:
**resume/reopen, mid‑run case addition, linking an existing ADO work item as a defect, and
per‑case evidence attachments.**

**Target users:** QA engineers / SDETs executing manual test passes for a release; QA leads
reviewing execution status on the release board.

### Confirmed product decisions (from clarification)
- **Defect = link an *existing* ADO work item** by id. Validate via the **read** API and store
  `id + URL`. **No ADO writes** — the ADO integration stays read‑only/inbound.
- **Evidence = per test‑case result**, stored in the platform **BlobStore** (MinIO/S3,
  content‑addressed), downloaded via presigned URL.
- **Mid‑run add = existing APPROVED cases** via the same scope‑filtered picker + suites; appended
  as `PENDING`; idempotent (no duplicate executions).
- **Lifecycle = persist + resume + reopen.** `IN_PROGRESS` survives sessions; the user explicitly
  completes (confirm if cases remain pending); a `COMPLETED` run can be reopened to `IN_PROGRESS`.

## 2. Scope

### In scope
1. Create a manual execution scoped to **release / sprint (iteration) / team / area** (exists;
   verify scope inheritance from a selected release).
2. Execute: per‑case status (`PENDING → PASSED | FAILED | BLOCKED | SKIPPED`) with actual result /
   notes (exists; extend with defect + evidence).
3. **Link an existing ADO work item** (defect) to a case execution; show id + clickable URL; unlink.
4. **Attach evidence** (screenshots, logs, files) to a case execution; list + download + delete.
5. **Resume** an `IN_PROGRESS` run across sessions; **reopen** a `COMPLETED` run.
6. **Add missing test cases** (existing, approved, scope‑filtered, incl. suites) to a live run.
7. Complete the run on explicit user action (confirm when pending > 0).

### Out of scope
- Creating/writing ADO work items (Bugs) from the platform.
- Authoring brand‑new test cases inline during a run.
- Uploading evidence directly to ADO.
- Automated‑run changes; analytics/trends for manual runs (separate effort).
- Real‑time multi‑user collaboration on one run.

## 3. Functional requirements & acceptance criteria

### F1 — Create manual execution scoped to release/sprint/team/area
- **Given** the New Manual Run modal, the user sets name, environment, optional release, and
  sprint/team/area, and picks cases and/or suites.
- Selecting a **release** prefills blank scope dimensions from the release mapping
  (`map_iteration_path`, `map_area_path`, `map_team_id`); the user may override.
- **Accept:** the run persists with `release_id / iteration_path / area_path / team_id` set; one
  `test_case_executions` row per selected (and matrix‑expanded) case at `PENDING`; lands on the
  run detail page.

### F2 — Execute a case (status + result)
- Per case: set status; `actual_result` required when `FAILED`; `notes` optional any status.
- **Accept:** `PUT …/executions/{execId}` persists status, `executed_by`, `executed_at`; run
  counters (passed/failed/blocked/skipped/pending) recompute; optimistic UI reflects immediately.

### F3 — Link an existing ADO defect to a case execution
- From a case (typically `FAILED`), the user enters an ADO work‑item **id**.
- Backend resolves the project's ADO credential and **validates the id via the read API**
  (`AzureBoardsPollClient`/read client); rejects unknown ids with a clear message.
- Store `defect_external_id` + `defect_url` (+ optional cached `defect_title`, `defect_state`) on
  the execution. Support **unlink**.
- **Accept:** a valid id shows as a chip with title/state and a link to `…/_workitems/edit/{id}`;
  an invalid id returns 400 with a readable message and stores nothing; unlink clears the fields.
- **Never** call any ADO write/PATCH endpoint.

### F4 — Attach evidence to a case execution
- Upload one or more files to a case execution (multipart). Stored via `BlobStore` (content‑
  addressed); an `execution_attachments` row links blob → execution with filename, content type,
  size, `uploaded_by`, `uploaded_at`.
- List attachments per case; download via **presigned URL**; delete an attachment (removes the row;
  blob is content‑addressed/shared so it is not hard‑deleted).
- **Accept:** uploaded files appear under the case; download fetches the original bytes. **Any file
  extension is accepted**; only the per‑file size cap (30 MB) is enforced, returning a clear error
  when exceeded. No per‑execution file‑count limit.

### F5 — Resume an in‑progress run
- An `IN_PROGRESS` run is reachable from the executions list and the release board and opens with
  prior per‑case statuses, defect links, and attachments intact.
- **Accept:** navigating away and back (or re‑login) preserves all execution state; no data loss.

### F6 — Add missing cases mid‑run
- On the run detail page, an **Add cases** action opens the same scope‑filtered, APPROVED‑only
  picker (and suites) used at creation.
- Selected cases are appended as new `PENDING` executions; cases already in the run are skipped
  (idempotent). Matrix expansion applies as on create.
- **Accept:** only `APPROVED` cases are selectable; re‑adding an existing case creates no duplicate;
  totals/pending update; allowed only while the run is `IN_PROGRESS`.

### F7 — Complete & reopen
- **Complete** is a user action; if pending > 0, the UI confirms ("N cases still pending — complete
  anyway?"). On complete: `status = COMPLETED`, `completed_at` set.
- **Reopen** a `COMPLETED` run → `status = IN_PROGRESS`, `completed_at` cleared; re‑enables editing,
  adding cases, defect linking, and evidence.
- **Accept:** completing with pending requires confirmation and succeeds; a completed run is
  read‑only until reopened; reopen restores full editability.

## 4. Data model changes (Postgres / Flyway)

Pre‑release ⇒ migrations are consolidated into a single `V1__initial_schema.sql`
(`platform-core/src/main/resources/db/migration/`). **Fold these changes into `V1`** (a fresh DB is
assumed); do **not** add a `V2` unless a deployed DB must be preserved.

- `test_case_executions` — add: `defect_external_id VARCHAR(64)`, `defect_url VARCHAR(500)`,
  `defect_title VARCHAR(500)`, `defect_state VARCHAR(60)` (all nullable).
- New table `execution_attachments`:
  - `id UUID PK`, `execution_id UUID NOT NULL REFERENCES test_case_executions(id) ON DELETE CASCADE`,
    `test_run_id UUID NOT NULL`, `file_name VARCHAR(300) NOT NULL`, `content_type VARCHAR(150)`,
    `size_bytes BIGINT NOT NULL`, `blob_ref VARCHAR(500) NOT NULL`, `uploaded_by VARCHAR(200)`,
    `uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
  - Index `idx_exec_attach_exec (execution_id)`.
- `test_runs.status` already supports `IN_PROGRESS | COMPLETED | ABANDONED`; reopen reuses
  `IN_PROGRESS` (no schema change). Add a `TestRun.reopen()` domain method.

## 5. API surface

All new backend endpoints live in **platform-ingestion** under
`/api/v1/projects/{projectId}/test-runs/{runId}/…`, proxied by **platform-portal** under
`/api/portal/…`, and surfaced in `platform-portal/frontend/src/lib/api.ts`.

| Method | Path | Purpose |
|---|---|---|
| POST | `…/test-runs/{runId}/cases` | Add cases mid‑run `{ testCaseIds[], suiteIds[], matrixType? }` → new executions (F6) |
| POST | `…/test-runs/{runId}/reopen` | Reopen a completed run (F7) |
| POST | `…/test-runs/{runId}/executions/{execId}/defect` | Validate + link ADO work item `{ workItemId }` (F3) |
| DELETE | `…/test-runs/{runId}/executions/{execId}/defect` | Unlink defect (F3) |
| GET | `…/test-runs/{runId}/executions/{execId}/attachments` | List attachments (F4) |
| POST | `…/test-runs/{runId}/executions/{execId}/attachments` | Multipart upload (F4) |
| GET | `…/attachments/{attachmentId}/download` | Presigned URL or streamed bytes (F4) |
| DELETE | `…/attachments/{attachmentId}` | Delete attachment row (F4) |

Reuse existing endpoints for create/list/get/complete/update‑execution and the selectable‑cases /
suites pickers. Portal proxies forward upstream status + message (existing pattern); the portal must
support **multipart passthrough** for the upload endpoint.

## 6. Commands

```bash
# Java toolchain — REQUIRED before any mvn (default shell JDK is 17 and fails)
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

# Build + test the touched backend modules (always use -am so platform-core is rebuilt in-reactor)
mvn -pl platform-ingestion,platform-portal -am test

# Run a single test class
mvn -pl platform-ingestion -am test -Dtest=TestRunServiceTest -Dsurefire.failIfNoSpecifiedTests=false

# Format (Java = Spotless/google-java-format, Frontend = Prettier)
mvn -q -Pformat process-sources -pl platform-core,platform-ingestion,platform-portal
cd platform-portal/frontend && npm run format

# Frontend typecheck + build (the portal jar bundles these static assets)
cd platform-portal/frontend && npx tsc -b && npx vite build

# Bring the local stack up reliably (creates volume_data dirs, warms Docker path cache)
./scripts/dev-up.sh
```

## 7. Project structure (where things go)

- **Domain + repo + migration:** `platform-core/src/main/java/com/platform/core/domain/`,
  `…/repository/`, `…/resources/db/migration/V1__initial_schema.sql`.
- **Service + controller + DTOs:** `platform-ingestion/src/main/java/com/platform/ingestion/management/tcm/`
  (`TestRunService`, `TestRunController`, request/response records).
- **ADO read validation:** reuse `com.platform.core.service.ado.AzureBoardsPollClient` (read only) +
  `CredentialResolver`; no new write client.
- **Blob storage:** `platform-common`/`platform-storage` `BlobStore` (`storeBytes`, `presignUrl`).
- **Portal BFF proxies:** `platform-portal/src/main/java/com/platform/portal/api/` (e.g.
  `PortalExecutionController` / the test‑run proxy controller).
- **Frontend:** page `platform-portal/frontend/src/pages/TestRunExecutionPage.tsx` (execute, defect,
  evidence, add‑cases, reopen); list `TestExecutionPage.tsx`; API in `lib/api.ts`; types in
  `lib/types.ts`.

## 8. Code style

- **Java:** Java 21, Spring Boot 4; constructor injection; `ResponseStatusException` for 4xx with a
  human‑readable reason; google‑java‑format (Spotless). Match the existing TCM service idioms
  (JdbcTemplate/JPA as already used in the module). Never log or echo secrets/PATs.
- **Frontend:** React + TS + Vite, TanStack Query for data, Tailwind; match existing page idioms
  (loading/empty/error via `LoadingSpinner`/`ErrorMessage`, optimistic mutations, `postMsg`‑style
  error surfacing). Prettier‑formatted. Files must pass `tsc -b` (strict).
- **Identity:** any user attribution (`executed_by`, `uploaded_by`) follows the platform's existing
  actor convention (e.g. `X-Actor`); reuse it, don't invent a new one.

## 9. Testing strategy

- **Per task: TDD (RED→GREEN), one commit per task**, message prefixed with the task id and ending
  with the required `Co-Authored-By` trailer.
- **Backend unit tests** (Mockito/JUnit5/AssertJ) for: add‑cases idempotency + APPROVED‑only +
  matrix expansion; reopen state transition; defect link validation (valid id stores id/URL, invalid
  id → 400, never calls a write method); attachment metadata persistence; complete‑with‑pending
  confirmation path.
- **Schema:** validate the combined `V1` applies cleanly on an empty DB (the established check).
- **Frontend:** `tsc -b` + `vite build` must pass; manual verification of the execute → link defect
  → attach evidence → add case → complete → reopen flow.
- **Full regression:** `mvn -pl platform-ingestion,platform-portal -am test` green before "done".

## 10. Boundaries

**Always**
- Keep the ADO integration **read‑only**: validate/link work items via read APIs only.
- Encrypt PATs at rest; resolve credentials via `CredentialResolver`; never log/return secrets.
- Make mutations idempotent (mid‑run add creates no duplicate executions; re‑linking the same defect
  is a no‑op).
- Recompute run counters from executions after every change; keep `IN_PROGRESS` durable across
  sessions.
- Format (Spotless + Prettier) and pass tests before marking a task done.

**Ask first**
- Any change that would make the platform **write** to Azure DevOps (creating/updating work items).
- Adding virus/content scanning, or introducing a file‑type allowlist/blocklist (currently all
  extensions are accepted).
- Changing the run state machine beyond `IN_PROGRESS ↔ COMPLETED` (e.g. `ABANDONED` semantics).
- Adding a new auth/identity mechanism for attribution.

**Never**
- Call ADO write/PATCH/POST work‑item endpoints.
- Hard‑delete shared content‑addressed blobs on attachment delete (remove the row only).
- Store secrets or full PATs in `test_runs`/executions/attachments or logs.
- Allow editing a `COMPLETED` run without an explicit reopen.

### Evidence limits (confirmed)
- Max **30 MB per file**. **No limit** on the number of files per execution. **All file
  extensions accepted** (no type allowlist/blocklist).
