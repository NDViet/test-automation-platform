# Plan — Manual Test Execution (release/sprint/team/area scoped)

Spec: `SPEC.md` (CONFIRMED). This plan slices the four gaps (resume/reopen, mid‑run add, ADO defect
link, evidence) into vertical tasks, each shipping backend → portal proxy → frontend → tests in one
commit. The create / per‑case status / scope / pickers / release board already exist and are reused.

## 1. What already exists (reused, not rebuilt)
- `TestRun` (status `IN_PROGRESS|COMPLETED|ABANDONED`, scope dims, `complete()`/`abandon()`),
  `TestCaseExecution` (`record(status, actualResult, notes, executedBy)`), `TestRunService` +
  `TestRunController` (`/api/v1/projects/{projectId}/test-runs`).
- `TcmRunService.createRun(...)` — APPROVED‑only gate + matrix expansion → executions.
- `SuiteResolverService.resolveMany`, selectable‑cases picker, suites.
- `AzureBoardsPollClient.connect(projectId)` + `getWorkItems(ado, ids)` (READ), `CredentialResolver`.
- `BlobStore.storeBytes/fetchBytes/presignUrl` (S3/MinIO; `presignUrl` throws on Filesystem dev).
- Portal proxy `PortalTestCaseController` (`/api/portal/projects/{projectId}/test-runs/**`).
- Frontend `TestRunExecutionPage.tsx`, `TestExecutionPage.tsx`, `lib/api.ts` (BASE `/api/portal`),
  `lib/types.ts`.

## 2. Dependency graph

```
M1 (schema + data foundation)
   ├──> T3 (ADO defect link)      [needs defect columns]
   └──> T4 (evidence attachments) [needs execution_attachments table]

T1 (reopen + complete-confirm + COMPLETED read-only guard)
   └──> establishes the "editable only when IN_PROGRESS" guard reused by T2/T3/T4

T2 (add cases mid-run)            [needs T1 guard; reuses TcmRunService gate]
T5 (verify F1/F2/F5 + cohesive wiring + regression)  [last; depends on all]
```

Execution order: **M1 → T1 → T2 → T3 → T4 → T5.** (T1 before T2/T3/T4 so the read‑only guard exists
before features that must respect it. M1 before T3/T4 for schema.)

## 3. Conventions
- TDD per task (RED→GREEN); one commit per task, message prefixed with the task id + the
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer.
- `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` before every `mvn`; build touched modules
  with `-am`. Backend tests are Mockito/JUnit5 (no live DB). Format (Spotless + Prettier) and pass
  `tsc -b`/`vite build` before "done".
- Schema changes fold into the single `V1__initial_schema.sql` (pre‑release). The combined `V1` is
  verified by applying it to a throwaway DB (`psql` on a scratch database), **not** against the
  running one (whose old `V1` checksum would conflict).

## 4. Tasks

### M1 — Schema + data foundation
- **platform-core:** in `V1__initial_schema.sql` add to `test_case_executions`:
  `defect_external_id VARCHAR(64)`, `defect_url VARCHAR(500)`, `defect_title VARCHAR(500)`,
  `defect_state VARCHAR(60)` (nullable); add table `execution_attachments` (cols per SPEC §4) +
  `idx_exec_attach_exec`. New `ExecutionAttachment` entity + `ExecutionAttachmentRepository`
  (`findByExecutionId`, `findByTestRunId`). Extend `TestCaseExecution` with the 4 defect fields +
  `linkDefect(extId, url, title, state)` / `clearDefect()`; add `TestRun.reopen()`.
- **Acceptance:** combined `V1` applies cleanly on an empty DB; entities map to the new columns;
  `linkDefect/clearDefect/reopen` set/clear the right fields. No behavior change to existing flows.
- **Verify:** `mvn -pl platform-core -am test`; apply `V1` to a scratch DB (`createdb v1test` →
  `psql -f V1` → assert `execution_attachments` + the 4 columns exist) → drop. Unit test for the
  new domain methods.

> ### ✅ CHECKPOINT CP-1 — Foundation
> Combined `V1` applies on a fresh DB; entities/repos compile and map. **Note:** local stack needs a
> DB reset (drop `platform` DB or wipe `volume_data/postgres`) before it will boot on the new `V1`.
> **Sign off** before building features on it.

### T1 — F7: reopen + complete‑with‑confirm + read‑only guard
- **Backend:** `TestRunService.reopen(projectId, runId)` (only from `COMPLETED` → `IN_PROGRESS`,
  clears `completedAt`); keep `complete()` allowed with pending (counts already computed). Add a
  private `requireEditable(run)` guard (throws 409 when not `IN_PROGRESS`) and apply it in
  `updateExecution`. New `POST …/test-runs/{runId}/reopen`.
- **Portal proxy:** add `POST /test-runs/{runId}/reopen` to `PortalTestCaseController`.
- **Frontend:** `TestRunExecutionPage` — when `COMPLETED`, show a **Reopen** button and render the
  run read‑only (status buttons disabled); **Complete** shows a confirm dialog when pending > 0
  ("N cases still pending — complete anyway?"). `api.reopenTestRun`.
- **Acceptance:** reopen flips status and re‑enables editing; editing a COMPLETED run’s execution →
  409; complete with pending succeeds after confirm; completed run renders read‑only.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test` (unit tests: reopen transition,
  guard rejects when COMPLETED); `tsc -b` + `vite build`.

### T2 — F6: add existing approved cases mid‑run
- **Backend:** `TestRunService.addCases(projectId, runId, AddCasesRequest{testCaseIds, suiteIds,
  matrixType})` — `requireEditable`; resolve via `effectiveCaseIds` (explicit ∪ suites); enforce
  APPROVED‑only + matrix expansion (reuse `TcmRunService` logic); **skip cases already in the run**
  (idempotent, no duplicate executions); recompute counts. New `POST …/test-runs/{runId}/cases`.
- **Portal proxy:** add `POST /test-runs/{runId}/cases`.
- **Frontend:** “Add cases” action on `TestRunExecutionPage` opening the existing scope‑filtered,
  APPROVED‑only picker + suites; on submit appends PENDING executions and refreshes. `api.addTestRunCases`.
- **Acceptance:** only APPROVED selectable; re‑adding an existing case adds nothing; totals/pending
  grow by the net‑new count; blocked (409) when run is COMPLETED.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test` (idempotency + APPROVED + COMPLETED‑guard tests); `tsc -b` + `vite build`.

### T3 — F3: link / unlink an existing ADO defect (READ‑ONLY)
- **Backend:** `TestRunService.linkDefect(projectId, runId, execId, LinkDefectRequest{workItemId})`
  — `requireEditable`; `AzureBoardsPollClient.connect(projectId)` + `getWorkItems([id])`; if absent
  → 400 "Work item {id} not found"; else read `System.Title`/`System.State`, build URL
  `{ado.root}/_workitems/edit/{id}`, call `exec.linkDefect(...)`. `unlinkDefect(...)` → `clearDefect()`.
  Extend `TestCaseExecutionDto` with `defectId/defectUrl/defectTitle/defectState`. New
  `POST` + `DELETE …/executions/{execId}/defect`.
- **Portal proxy:** add the two endpoints.
- **Frontend:** on a case row, a **Link defect** input (work‑item id) → chip with title/state +
  external link; **unlink** clears it. `api.linkExecutionDefect` / `api.unlinkExecutionDefect`;
  extend `TestCaseExecution` type.
- **Acceptance:** valid id → stored + chip + link to `…/_workitems/edit/{id}`; invalid id → 400,
  nothing stored; unlink clears; **no ADO write/PATCH/POST work‑item call** anywhere.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test` (valid stores; invalid → 400;
  Mockito `verify` that only read methods are invoked, no write); `tsc -b` + `vite build`.

> ### ✅ CHECKPOINT CP-ADO — read‑only guarantee (CRITICAL)
> Confirm defect linking validates via read APIs only and the platform makes **zero** ADO writes.
> **Explicit sign‑off** before this runs against a real org/PAT.

### T4 — F4: per‑case evidence attachments (BlobStore)
- **Backend:** `ExecutionAttachmentService` — `upload(projectId, runId, execId, MultipartFile,
  uploadedBy)` (`requireEditable`; enforce **30 MB/file**, no count/type limit; `BlobStore.storeBytes`
  → persist row with serialized `BlobRef`); `list(execId)`; `download(attachmentId)` →
  **stream bytes** via `fetchBytes` (works for both S3 and Filesystem dev; presign optional when
  supported); `delete(attachmentId)` (remove row only — never delete the shared content‑addressed
  blob). Endpoints: `GET`/`POST(multipart)` `…/executions/{execId}/attachments`,
  `GET …/attachments/{id}/download`, `DELETE …/attachments/{id}`.
- **Portal proxy:** add the four endpoints incl. **multipart passthrough** for upload and a
  streaming passthrough for download.
- **Frontend:** per‑case attachments list with upload (drag/drop or file input), download link, and
  delete; show >30 MB rejection message. `api.listExecutionAttachments` / `uploadExecutionAttachment`
  / `deleteExecutionAttachment` + a download URL helper.
- **Acceptance:** any extension uploads; >30 MB → clear error; download returns original bytes;
  delete removes the row (blob untouched); blocked (409) when run COMPLETED.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test` (size‑limit reject; metadata
  persisted; delete removes row, not blob — assert `blobStore.delete` not called); `tsc -b` + `vite build`.

### T5 — F1/F2/F5: verify scope/execute/resume + cohesive run detail + regression
- **Backend:** confirm create‑scope inheritance (release → blank dims) still holds; ensure every
  mutating execution endpoint honors `requireEditable`. Add a small test asserting the release →
  run scope inheritance.
- **Frontend:** cohesively present on `TestRunExecutionPage`: per‑case status + actual result,
  defect chip, attachments, the Add‑cases action, and the Reopen/Complete controls; ensure an
  `IN_PROGRESS` run reopened from the list/board restores all state (F5). Reuse the height‑fill
  scroll idiom so long runs don’t clip.
- **Acceptance:** the full path works — create (scoped) → execute → link defect → attach evidence →
  add a missed case → complete (confirm) → reopen → edit → complete. Resume mid‑run loses nothing.
- **Verify:** full `mvn -pl platform-ingestion,platform-portal -am test` green; `tsc -b` +
  `vite build`; manual end‑to‑end against a freshly reset DB + the ADO credential.

> ### ✅ CHECKPOINT CP-FINAL
> End‑to‑end manual execution lifecycle verified in the browser against a fresh DB. **Sign off to finish.**

## 5. Cross‑cutting acceptance (all tasks)
- ADO stays **read‑only**; PATs encrypted, never logged/returned.
- Mutations idempotent where stated; run counters recomputed after every change.
- A `COMPLETED` run is read‑only until reopened (409 on mutate).
- Attachment delete never hard‑deletes shared blobs.
- Spotless + Prettier + `tsc -b` + tests green before any task is marked done.

## 6. Risks / notes
- **DB reset required** after M1 (the running DB has the pre‑consolidation `V1` checksum). Reset via
  `docker compose down && rm -rf volume_data/postgres/* && ./scripts/dev-up.sh`, or
  `DROP DATABASE platform; CREATE DATABASE platform;` then restart ingestion.
- `presignUrl` is unsupported on the Filesystem dev BlobStore → download streams bytes through the
  service (universal); presign is an optional fast‑path for S3/MinIO only.
- ADO `getWorkItems` is org‑scoped (no project arg) and returns batch JSON; treat an empty result
  or non‑2xx as "not found / not accessible" → 400 with a readable message.
