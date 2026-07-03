# TODO — Fix test-case delete + AI origin on approval

- [x] **Task 1** — Backend: `acceptEntity` stamps `createdBy='AGENT'` (not the approver) + test.
- [x] **Task 2** — Migration `V11`: backfill `created_by NOT IN ('AGENT','HUMAN')` → `'AGENT'`.
- [x] **Task 3** — Frontend: wire a Delete action (confirm) for test cases in TestCasesPage.
