# SPEC — Epic A: Failure → Defect → Retest loop

> Status: **DRAFT — awaiting confirmation.** First buildable slice of the quality-cycle roadmap
> (`spec/quality-cycle-roadmap.md`). Prior specs archived under `spec/archive/`.

## 1. Objective

Turn scattered failing executions into **deduplicated failure clusters** that a QA lead can act on:
each cluster groups recurring failures of the same test+signature, can be **linked to an existing
tracker defect or create a new one** (ADO/Jira/… via the existing `IssueTracker`), **suppressed as a
known issue** (stops re-alerting), and **retested** — with the retest results auto-updating the
cluster's state. **Layer on the tracker** (per roadmap decision A): the platform owns the *cluster*,
not the defect lifecycle.

### Target users
QA engineers/leads triaging CI + manual failures instead of re-reading the same stack trace every run.

### Locked decisions
1. **Defect link = both** — link an existing tracker item **or** one-click create a new bug (reusing
   `AzureBoardsIssueTracker.createWorkItem` / the `IssueTracker` abstraction), pre-filled from the
   cluster signature + occurrences. The tracker stays the defect source of truth.
2. **Scope = both, automated-primary** — cluster automated failures by a normalized signature from
   failure message/stack (strong); cluster manual `FAILED` executions by test-case + `actual_result`
   (weaker). One board covers both.
3. **Retest = targeted run** — create a run of just the cluster's cases (reuse `createTestRun` /
   `addTestRunCases`); roll results up to the cluster. Auto-dispatching CI is **Epic C** (deferred).

## 2. Scope
**In:** clustering on ingest (automated) + on manual `FAILED`; dedup by execution; reopen a `RESOLVED`
cluster when a new occurrence arrives; link/create tracker defect; mark known-issue; targeted retest +
roll-up (all-pass → `RESOLVED`, any-fail → `OPEN`); a **Failures** board UI. All endpoints RBAC-guarded
(`OPERATE_QUALITY`) and the mutating actions **audited** (reuse `AuditAspect`).
**Out:** owning defect lifecycle / auto-closing the tracker item; bidirectional sync; CI auto-dispatch
(Epic C); notifications/alerts on clusters (Epic F) — clusters just expose a `known_issue` flag those
consume later.

## 3. Data model (Flyway **V12**, additive)
- **`failure_cluster`**: `id, project_id(FK cascade), source(AUTOMATED|MANUAL), signature_hash,
  test_ref, title, sample_message(text), status(OPEN|KNOWN_ISSUE|RESOLVED default OPEN),
  defect_external_id?, defect_url?, defect_title?, defect_state?, occurrences int, first_seen_at,
  last_seen_at`; `UNIQUE(project_id, signature_hash)`.
- **`failure_occurrence`**: `id, cluster_id(FK cascade), execution_id, run_id?, occurred_at`;
  `UNIQUE(cluster_id, execution_id)` (dedup).
- Entities + repositories in `platform-core`.

## 4. Approach (implementation sketch — not binding)
- **`FailureSignature`** (package-private, pure, testable): normalize `failureMessage` + top app-frame
  lines — strip line/col numbers, `0x…` addresses, UUIDs, timestamps, volatile digits; keep the
  exception type + normalized message + top N frames in the owning package → SHA-256 `signature_hash`.
  Manual: normalize `actual_result` (or `test_case_id` + first line) the same way.
- **`FailureClusterService`** (`platform-ingestion`): `record(projectId, source, signature, testRef,
  message, executionId, runId)` → upsert cluster by `(project_id, signature_hash)`, insert occurrence
  (ignore duplicate execution), bump `occurrences`/`last_seen`; if cluster was `RESOLVED`, reopen to
  `OPEN`; never demote `KNOWN_ISSUE`.
- **Hooks:** call `record(...)` from the **manual** execution-update path when status→`FAILED`, and
  from the **automated** result ingestion on `FAILED`/`BROKEN` (exact consumer confirmed at build —
  `ResultAnalysisConsumer`/result ingest).
- **Retest:** `POST …/retest` creates a targeted run of the cluster's cases (reuse existing
  create-run/add-cases); a roll-up (on run completion or on demand) sets `RESOLVED` if all its cases
  pass, else `OPEN`.
- **Defect:** `link-defect` stores the external id/url/title/state (resolve via tracker);
  `create-defect` calls `IssueTracker.createWorkItem(type=Bug, patch)` with title/description from the
  cluster, then stores the returned id/url.

### Endpoints (agent/ingestion + portal BFF proxy)
`GET …/failure-clusters?status=&source=&q=` · `GET …/failure-clusters/{id}` (+ recent occurrences) ·
`PATCH …/failure-clusters/{id}` `{status}` · `POST …/{id}/link-defect` `{externalId}` ·
`POST …/{id}/create-defect` · `POST …/{id}/retest` → `{runId}`.

## 5. Acceptance criteria
- [ ] Two failed executions of the same test with the **same normalized signature** (differing only by
      line numbers/addresses/timestamps) land in **one** cluster with `occurrences=2`; a genuinely
      different exception makes a **separate** cluster.
- [ ] The **same execution** is never counted twice (occurrence dedup).
- [ ] A `RESOLVED` cluster **reopens** to `OPEN` on a new occurrence; a `KNOWN_ISSUE` cluster keeps
      recording occurrences but stays `KNOWN_ISSUE`.
- [ ] `link-defect` associates an existing item (title/state resolved); `create-defect` creates a
      tracker Bug via `IssueTracker` and stores its id/url on the cluster.
- [ ] `retest` creates a run containing exactly the cluster's cases; when they all pass the cluster
      becomes `RESOLVED`, otherwise it stays/returns `OPEN`.
- [ ] All endpoints enforce `OPERATE_QUALITY`; `create-defect`/`known-issue`/`retest` write an
      `AuditEvent`.
- [ ] Manual + automated failures both cluster; `npx tsc -b` clean; migration `V12` applies (validated
      via rolled-back psql).

## 6. Project structure (touched)
```
platform-core/.../domain/{FailureCluster,FailureOccurrence}.java + repositories
platform-core/.../db/migration/V12__failure_clusters.sql
platform-ingestion/.../tcm/FailureSignature.java (+ test)  FailureClusterService.java (+ test)
platform-ingestion/.../tcm/FailureClusterController.java (+ test)   # + hook manual FAILED path
platform-<result-ingest>/…                                          # hook automated FAILED/BROKEN → record(...)
platform-integration/.../azureboards/AzureBoardsIssueTracker.java   # reuse createWorkItem (no change expected)
platform-portal/.../api/PortalFailureController.java                # BFF proxy
platform-portal/frontend/src/pages/FailuresPage.tsx + route "failures" + api.ts
```

## 7. Commands
- **Backend:** `mvn -q -pl platform-ingestion -am test` (+ `platform-core`); migration via `db-migrate`.
- **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Deploy:** `deploy-local` skill (build ingestion+portal → images → V12 → `up -d --no-deps` → verify).

## 8. Testing strategy
- **Backend (JUnit5 + Mockito + AssertJ):** `FailureSignature` normalization + stability; service
  upsert/dedup/reopen/known-issue; `create-defect` invokes `IssueTracker.createWorkItem` with the right
  patch and stores the id; `retest` builds the run from the cluster's cases; roll-up transitions;
  controller RBAC. Regression = full ingestion suite.
- **Migration:** rolled-back `BEGIN…ROLLBACK` invariant check (as V10/V11).
- **Frontend:** `tsc -b` + manual (no SPA unit runner).

## 9. Boundaries
**Always** — RBAC-guard endpoints; **audit** create-defect / known-issue / retest; keep the tracker as
defect source of truth (store link only); additive/forward-only migration; design tokens + `ui/` kit;
preserve `created_by = AGENT|HUMAN`.
**Ask first** — auto-closing/transitioning the tracker item on retest-pass; clustering signature
tuning that could over-merge; adding a nav item vs. a tab under Flaky.
**Never** — own a second defect source of truth; log secrets/PATs; block a user action without a path;
edit a shipped migration.

## 10. Open questions (defaults chosen)
- New **Failures** nav page (default) vs. a tab under Flaky Tests — adjustable.
- Roll-up trigger: on run completion **and** an explicit "re-check" button (default both).
- Manual-failure signature is weaker (free-text `actual_result`); default clusters by
  `test_case_id` + first non-empty line — refine if noisy.
