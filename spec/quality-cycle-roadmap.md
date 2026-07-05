# SPEC — Quality Cycle: program roadmap (Epics A–F)

> Status: **DRAFT — awaiting confirmation.** Program/roadmap spec: scopes six epics that complete the
> quality lifecycle, each with data model + endpoints + acceptance, and a phased sequence. Every epic
> then gets its own `/spec` + `/build`. Prior spec archived at
> `spec/archive/test-case-delete-and-ai-origin-spec.md`.

## 1. Objective

The platform already covers Requirements → Test Cases (CRUD, AI generation, review queue) → Suites →
Manual + Automated Execution (evidence, ADO defect links, pw-trace) → Flakiness, Impact Analysis,
Coverage, Dashboards, Releases, Alerts, RBAC. This roadmap closes the remaining lifecycle gaps so the
platform doesn't just *report* quality but *drives the quality decision and the team workflow*:

- **A** — close the **failure → defect → retest** loop.
- **B** — **enforcing release quality gates** (go/no-go that can block).
- **C** — **execution planning & ownership** (assignment + CI-dispatched scheduling).
- **D** — **governance & auditability** (surface + extend audit, version history, bulk ops).
- **E** — **design-side depth** (test-data management + shared/reusable steps; optional BDD).
- **F** — **reporting & notifications** (traceability/exec exports + run/failure/gate notifications).

### Target users
QA leads (gates, planning, sign-off), QA engineers (assignment, retest, authoring), engineering
managers (readiness, audit/compliance).

### Locked decisions (from clarification)
1. **A — layer on ADO**: defects stay in ADO (existing `TestCaseExecution.defect_external_id/url/…`);
   add clustering/dedup, known-issue suppression, and retest-on-fix **on top of** those links. No
   in-platform defect entity, no dual source of truth.
2. **B — enforcing gate**: the gate can **block** a release transition (`released`) / deploy trigger
   until criteria pass, with an explicit **override path** that is **audited**.
3. **C — dispatch CI (GitHub Actions)**: scheduling/on-demand automated runs reuse the existing
   `github-workflows` integration (`workflow_dispatch`); results return via the CI reporter as today.
4. **Deliverable = program roadmap**: this SPEC scopes all six; implementation detail lands in per-epic
   specs.

### Grounding (verified against current code)
- Audit infra exists: `AuditEvent` (+ `AuditAspect`/`AuditLog`) but is **API-key-actor scoped**
  (`actor_key_id/prefix`) and **not surfaced** in the portal — D extends it to portal-user actions.
- Defects are external only: `TestCaseExecution` has `defect_external_id/url/title/state`.
- Eventing/Slack exists: `KafkaReviewGateway`, `SlackInteractionController` — F builds on this.
- `TestRun.status` = IN_PROGRESS/COMPLETED/ABANDONED; `TestCaseExecution.status` = PENDING/PASSED/…,
  `executedBy` present but **no assignee** — C adds assignment.

## 2. Scope (per epic)

### Epic A — Failure → Defect → Retest loop
**In:** (1) **Failure clustering** — group failed/broken executions by `(testCaseId | testId, failure
signature)` across runs; a normalized signature from `failureMessage`/`stackTrace`. (2) **Dedup +
known-issue suppression** — link a cluster to one ADO defect; mark a cluster "known issue" so it stops
re-alerting. (3) **Retest-on-fix** — from a cluster/defect, spawn a targeted retest of just its linked
cases (reuse `addTestRunCases` + execution) and roll up pass/fail to auto-close or reopen the cluster.
**Out:** owning defect lifecycle in-platform; bidirectional ADO write beyond the existing link.
**Data:** `failure_cluster` (project_id, signature_hash, test_ref, first_seen, last_seen, occurrences,
status[OPEN|KNOWN_ISSUE|RESOLVED], defect_external_id?), `failure_occurrence` (cluster_id,
execution_id). **Endpoints:** `GET …/failure-clusters`, `PATCH …/{id}` (status/known-issue/defect),
`POST …/{id}/retest` (→ new/targeted run). **UI:** a "Failures" board + retest action.

### Epic B — Enforcing release quality gates
**In:** per-project **gate config** (thresholds: min pass-rate, min coverage %, max open blockers, max
critical-flaky, required suites executed); **gate evaluation** for a release (compute from execution +
coverage + flaky data); **enforcement** — block `Release` transition to `released` / the deploy-trigger
unless PASS, with an **override** (capability-gated + audited reason). **Out:** custom rule DSL.
**Data:** `quality_gate` (project_id, thresholds jsonb, enabled), `release` gains
`gate_status`(PASS|FAIL|OVERRIDDEN)+`gate_evaluated_at`. **Endpoints:** `GET/PUT …/quality-gate`,
`POST …/releases/{id}/evaluate-gate`, `POST …/releases/{id}/override-gate` (reason, audited).
**UI:** gate panel on the Release/Quality dashboard with per-criterion pass/fail + override.

### Epic C — Execution planning & ownership
**In:** (1) **Assignment** — add `assignee` (user) to `TestCaseExecution` (or a run-assignment table);
"My test queue" view; run progress by assignee. (2) **Scheduling** — a schedule config that dispatches
a GitHub Actions `workflow_dispatch` (via existing `github-workflows`) on cron/on-demand against an
environment; ingested results as today. **Out:** platform-native runner.
**Data:** `TestCaseExecution.assignee_user_id`; `run_schedule` (project_id, workflow_ref, cron, env,
enabled, last_dispatched_at). **Endpoints:** `PATCH …/executions/{id}/assign`, `GET …/my-queue`,
`GET/POST/DELETE …/run-schedules`, `POST …/run-schedules/{id}/dispatch`. **UI:** assignment control +
"My Queue" + a Schedules tab.

### Epic D — Governance & auditability
**In:** (1) **Extend audit to portal users** — `AuditEvent` gains `actor_user_id`; write events for
approve/reject/**delete**/edit of test cases, gate overrides (B), retest triggers (A). (2) **Surface**
a project-scoped **Audit Log** UI (filter by resource/actor/type/outcome/date). (3) **Test-case version
history** — snapshot on each save (title/steps/status/etc.) with a diff view. (4) **Bulk ops** —
bulk approve/tag/delete/assign over selected cases. **Out:** full field-level temporal tables.
**Data:** `AuditEvent.actor_user_id`; `test_case_revision` (test_case_id, version, snapshot jsonb,
changed_by, changed_at). **Endpoints:** `GET …/audit-events`, `GET …/test-cases/{id}/revisions`,
`POST …/test-cases/bulk` (action, ids). **UI:** Audit Log page, "History" tab on a case, list
multi-select + bulk bar.

### Epic E — Design-side depth
**In:** (1) **Test-data management** — named **datasets** (key/value or table rows) scoped to
project/env, referenceable by parameterized cases / the run matrix. (2) **Shared/reusable steps** — a
step library; cases can reference a shared step (updates propagate). **Out (flagged):** BDD/Gherkin
authoring + `.feature` import — **behind a flag, deferred** unless teams need Cucumber.
**Data:** `test_dataset` (project_id, name, env?, rows jsonb), `shared_step` (project_id, action,
expected, notes), `test_case_step.shared_step_id?`. **Endpoints:** dataset + shared-step CRUD; matrix
picks a dataset. **UI:** Datasets + Shared Steps managers; step editor can insert a shared step.

### Epic F — Reporting & notifications
**In:** (1) **Notifications** — reuse Slack infra + add email; project/user prefs; events: run
completed, run has failures, review requested, **gate PASS/FAIL/override**. (2) **Exports** —
traceability matrix (CSV), execution/exec-summary report (CSV/PDF), optional scheduled email of the
exec summary. **Out:** Teams/PagerDuty (later); BI warehouse export.
**Data:** `notification_pref` (scope, channel, events), reuse Kafka events. **Endpoints:**
`GET/PUT …/notification-prefs`, `GET …/reports/traceability.csv`, `GET …/reports/exec-summary.(csv|pdf)`.
**UI:** notification settings; export buttons on Coverage/Quality/Release.

## 3. Acceptance criteria (epic-level; detailed criteria in per-epic specs)
- **A** — repeated failures of the same test group into **one cluster**; a cluster links **one** ADO
  defect and can be marked **known-issue** (stops alerting); **retest** runs only the cluster's cases
  and auto-updates cluster status from results.
- **B** — a release shows per-criterion PASS/FAIL; marking it `released` is **blocked** on FAIL unless
  **overridden** with a reason; the override is **audited**; thresholds are project-configurable.
- **C** — an execution can be **assigned**; a tester sees only their **queue**; a **schedule**
  dispatches the GitHub workflow and the run appears via the CI reporter.
- **D** — approve/reject/**delete**/override actions appear in the **Audit Log** with the **portal
  user** as actor; a case shows its **revision history**; **bulk** approve/delete/tag works.
- **E** — a parameterized case/matrix runs against a **dataset**; editing a **shared step** updates
  every case that references it.
- **F** — configured events fire to Slack/email per prefs; **traceability CSV** and **exec-summary**
  export correctly.
- **Cross-cutting:** every new endpoint is **RBAC-guarded** (`OPERATE_QUALITY`/appropriate capability);
  every migration is additive + forward-only; portal uses design tokens + `ui/` kit, `npx tsc -b` clean.

## 4. Phased sequencing (each phase = its own spec+build+deploy)
- **Phase 1 — decide & close the loop:** **A** (clustering + retest) then **B** (enforcing gate).
- **Phase 2 — team ops & governance:** **C** (assignment + CI schedule) then **D** (audit surface +
  history + bulk).
- **Phase 3 — depth & outputs:** **E** (datasets + shared steps) then **F** (notifications + exports).
Dependencies: B reads A's cluster/known-issue + coverage; D audits A/B/C actions; F notifies B's gate.

## 5. Commands
- **Backend:** `mvn -q -pl <module> -am test`; migrations via the `db-migrate` Flyway service
  (next version **V12**, forward-only, additive). **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Deploy:** the `deploy-local` skill (build changed jars → `docker compose build` → apply Flyway →
  `up -d --no-deps` → verify). Each phase ships independently.

## 6. Project structure (modules touched, by epic)
```
platform-core            # entities + Flyway V12+ (failure_cluster, quality_gate, run_schedule, test_case_revision,
                         #   test_dataset, shared_step, notification_pref) + AuditEvent/TestCaseExecution/Release columns
platform-ingestion       # test-case/exec/release management + AuditAspect (D) + gate eval (B) + clustering (A)
platform-agent           # retest orchestration (A), CI workflow dispatch (C), Slack/notify (F) — reuse existing gateways
platform-analytics       # gate metrics, exec-summary/traceability reports (F)
platform-portal (+ FE)   # BFF proxies + pages: Failures board, Gate panel, My Queue/Schedules, Audit Log/History,
                         #   Datasets/Shared Steps, Notification prefs, export buttons
```

## 7. Code style / conventions
- **Java:** constructor injection; RBAC via `@RequireCapability`; reuse `CredentialResolver`,
  `GitHubApiClient`, `AuditAspect`, Kafka gateways — don't reinvent. Never log secrets/PATs.
- **SQL:** additive, nullable, forward-only Flyway; idempotent backfills; never edit shipped migrations.
- **Frontend:** Tailwind v4 **design tokens** + `ui/` primitives; no raw palette / no purple; a11y +
  `tsc -b` gate; one page per commit.
- **Attribution invariant (from the last fix):** `created_by` stays `AGENT|HUMAN`; user identity lives
  in audit / assignee / approver fields, never in origin.

## 8. Testing strategy
- **Backend:** JUnit5 + Mockito + AssertJ per module; cover clustering signature/dedup, gate
  evaluation + block/override, schedule dispatch, audit write on each guarded action, dataset/shared-step
  resolution, report generation. Regression = full module suites green.
- **Data migrations:** validate via rolled-back `BEGIN…ROLLBACK` psql invariants (as done for V10/V11).
- **Frontend:** `tsc -b` + manual (no SPA unit runner).
- **Per-epic** specs define concrete assertions.

## 9. Boundaries
**Always**
- RBAC-guard every new endpoint; **audit** sensitive actions (delete, approve, gate override, retest).
- Keep **ADO as the defect source of truth** (A); additive/forward-only migrations.
- Provide an **override path** for the enforcing gate (B) — never a hard lock with no escape — and audit it.
- Design-token UI; preserve the `created_by` origin invariant.

**Ask first**
- Each epic's **new tables/columns** (expected, but confirm in the per-epic spec).
- Any **bidirectional** ADO/Jira defect sync; blocking behavior specifics for B; notification channel
  credentials/secrets for F; enabling **BDD/Gherkin** (E, deferred).
- Reusing/altering shared eventing (`KafkaReviewGateway`) contracts.

**Never**
- Bypass RBAC or write a gate that blocks with no override.
- Introduce a second defect source of truth (dual-write) — chosen approach is layer-on-ADO.
- Log or expose secrets; put a username into `created_by`; edit an already-shipped migration.

## 10. Out of scope (program-level)
Non-portal frontends; a platform-native test runner; a BI/warehouse pipeline; Teams/PagerDuty channels
(later); custom gate-rule DSL; full temporal/field-level audit tables; owning defect lifecycle in-platform.
