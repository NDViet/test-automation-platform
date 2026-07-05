# SPEC — Epic F: Reporting & notifications

> Status: **APPROVED — build in progress.** Final slice of the quality-cycle roadmap
> (`spec/quality-cycle-roadmap.md`). Epics A–E complete (archived under `spec/archive/`).

## 1. Objective

Get quality signal out of the platform: **CSV exports** (traceability matrix, exec-summary) and
**notifications** (per-project prefs; dispatch run/failure/review/gate events to Slack/email).

### ⚠️ Sign-off gate (mid-epic)
Tasks **F1–F5** are pure data (prefs storage + UI, CSV generation) — autonomous. Task **F6
(notification dispatch)** performs **external sends using Slack/email credentials** and adds a **new
SMTP dependency + secret** — the build **pauses after F5** and asks before F6.

### Locked decisions (roadmap + grounding)
1. **Reuse Slack infra** for dispatch (agent bot-token sender + analytics webhook sender both exist).
2. **Email is new** — spring-boot-starter-mail + SMTP secret; lands entirely in F6 (sign-off).
3. **CSV is greenfield** — hand-rolled CSV (no lib), `produces=text/csv` + Content-Disposition. Pure data.
4. **PDF export deferred** — CSV only (PDF needs a lib; note as follow-up).
5. **Gate-result event gap** — no gate PASS/FAIL Kafka event exists; F6 adds/re-evaluates (sign-off side).

### Grounding (verified against current code)
- Slack senders: `platform-agent/.../SlackNotificationService` (bot token) + `platform-analytics/.../
  AlertNotificationService` (webhook url); delivery recorded via `AlertHistory` (mirror for a notify log).
- Kafka: `Topics.TEST_RESULTS_RAW` (UnifiedTestResult) + `AGENT_APPROVAL_REQUESTS`; consumer pattern =
  `ResultAnalysisConsumer` (@KafkaListener). No gate-result topic.
- Exports: `CoverageService.coverage(...).rows` (requirement→case→lastStatus) + `ReleaseReportService.
  generate(...)` (`ReleaseReportDto`). No existing CSV/email infra anywhere. Next Flyway = **V21**.

## 2. Scope

**In (F1–F5):**
1. `notification_pref` (V21): per-project channel + destination + subscribed events + enabled.
2. Prefs CRUD + endpoints + portal (MANAGE_PROJECT — touches delivery config).
3. Prefs UI (a Notifications manager).
4. Traceability CSV + exec-summary CSV export endpoints (VIEW_RESULTS, `text/csv`).
5. Export/download buttons (Coverage + Releases pages).

**In (F6 — after sign-off):** a Kafka consumer that, on run-completed / run-has-failures / review-
requested / gate-result, sends to each matching pref's channel (reuse Slack; add email/SMTP); record a
dispatch log; add the gate-result event.

**Out:** Teams/PagerDuty; BI warehouse export; PDF export (follow-up).

**Data:**
- V21: `notification_pref` (`id`, `project_id` FK, `channel` VARCHAR(20) SLACK|EMAIL|WEBHOOK,
  `destination` VARCHAR(500), `events` TEXT(JSON array), `enabled` bool, timestamps).

**Endpoints:**
- `GET/POST/PUT/DELETE …/projects/{projectId}/notification-prefs` (MANAGE_PROJECT).
- `GET …/projects/{projectId}/reports/traceability.csv` (VIEW_RESULTS, text/csv).
- `GET …/projects/{projectId}/reports/exec-summary.csv?days=` (VIEW_RESULTS, text/csv).
- Portal BFF proxies.

## 3. Acceptance criteria
- A notification pref can be created/edited/deleted (channel + destination + events), project-scoped.
- Traceability CSV downloads with a row per requirement→case→last-result; exec-summary CSV downloads
  the aggregate numbers. Both `text/csv` with a filename.
- (After sign-off) configured events dispatch to Slack/email per prefs; a dispatch is logged.
- Every endpoint RBAC-guarded; migrations additive/forward-only; portal design-token UI, `tsc -b` clean.

## 4. Commands / testing
- `mvn -q -pl <module> [-am] test`; `cd platform-portal/frontend && npx tsc -b`. Migrations validated via
  rolled-back psql. JUnit5 + Mockito + AssertJ; cover prefs CRUD, CSV generation (header + row + quoting),
  and (F6) dispatch selection + send-per-pref (mock the sender).

## 5. Boundaries
**Always:** RBAC-guard endpoints; additive/forward-only migrations; reuse existing Slack senders; design-
token UI; CSV-quote fields containing commas/quotes/newlines. **Sign-off:** F6 dispatch (secrets + external
send + SMTP dep). **Never:** log/expose Slack tokens, webhook URLs, or SMTP passwords; edit a shipped
migration; send anything before sign-off.
