# TODO — Manual Test Execution

Vertical slices. Check off only when **Acceptance** and **Verify** both pass. Stop at each
**CHECKPOINT** for human review. Spec: `SPEC.md` · Plan: `tasks/plan.md`.

Order: **M1 → T1 → T2 → T3 → T4 → T5.**

---

## Phase 1 — Foundation

### [x] M1 — Schema + data foundation (defect columns, execution_attachments, domain methods)
- **platform-core:** extend `V1__initial_schema.sql` (4 defect columns on `test_case_executions` +
  `execution_attachments` table + index); `ExecutionAttachment` entity + repository; defect fields +
  `linkDefect()`/`clearDefect()` on `TestCaseExecution`; `TestRun.reopen()`.
- **Acceptance:** combined `V1` applies on an empty DB; entities map; domain methods set/clear fields;
  existing flows unchanged.
- **Verify:** `mvn -pl platform-core -am test`; apply `V1` to a scratch DB and assert new objects; unit test domain methods.

> ### ✅ CHECKPOINT CP-1 — Foundation
> `V1` applies on a fresh DB; foundation compiles. Local DB must be reset before the stack boots on
> the new `V1`. **Sign off.**

---

## Phase 2 — Lifecycle & mid-run editing

### [x] T1 — F7: reopen + complete-with-confirm + read-only guard
- `TestRunService.reopen` + `requireEditable` guard (409 when not IN_PROGRESS) applied to
  `updateExecution`; `POST …/test-runs/{runId}/reopen`; portal proxy; frontend Reopen button +
  read-only completed view + complete-with-pending confirm.
- **Acceptance:** reopen flips COMPLETED→IN_PROGRESS; editing a completed run → 409; complete with
  pending succeeds after confirm; completed run renders read-only.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test`; `tsc -b` + `vite build`.

### [x] T2 — F6: add existing approved cases mid-run
- `TestRunService.addCases` (APPROVED-only, matrix-expanded, idempotent skip-existing, requireEditable);
  `POST …/test-runs/{runId}/cases`; portal proxy; frontend "Add cases" picker + suites.
- **Acceptance:** only APPROVED selectable; re-adding existing adds nothing; totals/pending grow by
  net-new; 409 when COMPLETED.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test`; `tsc -b` + `vite build`.

> ### ✅ CHECKPOINT CP-2 — Lifecycle + add-cases
> Resume/reopen/complete and mid-run add work end-to-end. **Sign off.**

---

## Phase 3 — ADO defect linking (read-only)

### [x] T3 — F3: link / unlink an existing ADO defect
- `linkDefect`/`unlinkDefect` validating via `AzureBoardsPollClient.getWorkItems([id])` (READ only),
  storing id/url/title/state; DTO extended; `POST`/`DELETE …/executions/{execId}/defect`; portal
  proxy; frontend link input + chip + unlink.
- **Acceptance:** valid id stored + chip + edit URL; invalid id → 400, nothing stored; unlink clears;
  zero ADO write calls.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test` (incl. Mockito verify: no write
  method invoked); `tsc -b` + `vite build`.

> ### ✅ CHECKPOINT CP-ADO — read-only guarantee (CRITICAL)
> Confirm only read APIs are used; the platform performs **no** ADO writes. **Explicit sign-off**
> before running against a real org/PAT.

---

## Phase 4 — Evidence

### [x] T4 — F4: per-case evidence attachments (BlobStore)
- `ExecutionAttachmentService` (upload 30 MB cap / no count or type limit, list, download-streams-bytes,
  delete row only); 4 endpoints; portal multipart + streaming passthrough; frontend upload/list/
  download/delete UI.
- **Acceptance:** any extension uploads; >30 MB → clear error; download returns original bytes;
  delete removes row (blob untouched); 409 when COMPLETED.
- **Verify:** `mvn -pl platform-ingestion,platform-portal -am test` (size reject; metadata persisted;
  blob not deleted on row delete); `tsc -b` + `vite build`.

> ### ✅ CHECKPOINT CP-3 — Evidence
> Upload/download/delete works against MinIO (and filesystem dev). **Sign off.**

---

## Phase 5 — Verify & cohesion

### [x] T5 — F1/F2/F5: verify scope/execute/resume + cohesive run detail + regression
- Confirm release→run scope inheritance + execute + resume; ensure every mutating endpoint honors
  `requireEditable`; present status/defect/evidence/add-cases/reopen cohesively on the run detail
  page; height-fill scroll for long runs.
- **Acceptance:** full path works (create scoped → execute → link defect → attach evidence → add
  missed case → complete w/ confirm → reopen → edit → complete); resume loses nothing.
- **Verify:** full `mvn -pl platform-ingestion,platform-portal -am test` green; `tsc -b` + `vite
  build`; manual end-to-end on a fresh DB + ADO credential.

> ### ✅ CHECKPOINT CP-FINAL
> Lifecycle verified in the browser against a fresh DB. **Sign off to finish.**

---

## Cross-cutting (every task)
- ADO read-only; PATs encrypted, never logged/returned.
- Idempotent where stated; run counters recomputed after each change.
- COMPLETED run read-only until reopened (409 on mutate).
- Attachment delete never hard-deletes shared blobs.
- Spotless + Prettier + `tsc -b` + tests green before "done".
