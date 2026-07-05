# PLAN — Epic F: Reporting & notifications

Spec: `SPEC.md`. Each task: RED → GREEN → regression → build → 1 commit.

## Autonomous (F1–F5)

### Task 1 — V21 notification_pref + entity/repo  ⟶ 1 commit
`notification_pref` (project, channel, destination, events TEXT-JSON, enabled, timestamps) + entity +
repo (findByProjectIdOrderByCreatedAtDesc, findByIdAndProjectId).
- RED: entity update() replaces fields; migration applies on rolled-back psql.

### Task 2 — Notification prefs CRUD + endpoints + portal  ⟶ 1 commit
`NotificationPrefService` CRUD (events via ObjectMapper list) + `NotificationPrefController`
(MANAGE_PROJECT) + portal proxy + RBAC test.
- RED: create persists channel+events; get-not-found → 404.

### Task 3 — Notification prefs UI  ⟶ 1 commit
`NotificationsPage` (list + create/edit modal: channel, destination, event checkboxes, enabled + delete)
+ route + nav; api + types. `tsc -b`.

### Task 4 — CSV export endpoints  ⟶ 1 commit
A `CsvWriter` helper (quote fields with comma/quote/newline). `ReportController`:
`GET …/reports/traceability.csv` (from CoverageService rows) + `GET …/reports/exec-summary.csv` (from
ReleaseReportService), both VIEW_RESULTS, `produces=text/csv` + Content-Disposition. Portal proxy.
- RED: CsvWriter quotes a field containing a comma; traceability CSV has a header + one row per requirement.

### Task 5 — Export/download buttons  ⟶ 1 commit
"Download CSV" buttons on Coverage (traceability) and Releases (exec-summary) pages; api helpers that
fetch the CSV and trigger a browser download. `tsc -b`.

## ⚠️ SIGN-OFF GATE — notification dispatch (Slack/email secrets + external send + SMTP dep). STOP.

### Task 6 — Notification dispatch  ⟶ 1 commit
A Kafka consumer (own groupId) on TEST_RESULTS_RAW (+ review/gate) that, per matching enabled pref,
sends via the existing Slack sender / a new email sender; records a dispatch log; add a gate-result
event. Degrades gracefully when a channel is unconfigured.
- RED: a run-completed event with a matching SLACK pref invokes the sender (mocked); disabled pref skipped.
