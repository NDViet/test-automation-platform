# SPEC — Epic C: Execution planning & ownership

> Status: **APPROVED — build in progress.** Third slice of the quality-cycle roadmap
> (`spec/quality-cycle-roadmap.md`). Epics A + B complete (archived under `spec/archive/`).

## 1. Objective

Give test execution an **owner** and a **schedule**. Two halves:
- **Assignment** — an execution can be assigned to a user; a tester sees a **"My Queue"** of only
  their assigned executions.
- **Scheduling** — a project can define a **run schedule** that dispatches a GitHub Actions
  `workflow_dispatch` (via the existing `github-workflows` integration) on a cadence; results ingest
  via the CI reporter exactly as today.

### Locked decisions (from the roadmap)
1. **Dispatch CI (GitHub Actions)** — reuse `ProjectGitHubWorkflowService.triggerDispatch`
   (resolves the project's PAT via the `ProjectRepoAssignment` credential). No platform-native runner.
2. **Assignment is a new concept** — `assignee_user_id` on `TestCaseExecution`, distinct from the
   free-text `executedBy` audit string.

### ⚠️ Sign-off gate (mid-epic)
Tasks **C1–C4 (assignment)** are pure data/endpoints/UI and build autonomously. Tasks **C5–C8
(scheduling)** dispatch **external GitHub Actions using the project PAT (a secret)** — the build skill
requires explicit sign-off before shipping that. The build **pauses after C4** and asks before C5.

### Grounding (verified against current code)
- `TestCaseExecution` (`test_case_executions`) has `executed_by` (free-text, not a FK); status =
  PENDING/PASSED/FAILED/BLOCKED/SKIPPED. Updated via `TestRunService.updateExecution`.
- `AuthenticatedUser(userId, username, superAdmin)`; `CurrentUser.get().userId()` gives the id.
- `User`/`users` table + `UserRepository.findById/findAllByOrderByUsernameAsc`.
- `ProjectGitHubWorkflowService.triggerDispatch(projectId, DispatchRequest(repoFullName, workflowId,
  ref, inputs))` → resolves PAT via `ProjectRepoAssignment.credentialId`; manual endpoint is
  `POST …/github/workflow-dispatch` (OPERATE_QUALITY).
- `@EnableScheduling` is on `IngestionApplication`; `GitHubSyncScheduler` (`@Scheduled(fixedDelay=60s)`
  DB-poller) is the pattern to mirror. **No ShedLock/cron engine** — flag: naive multi-instance poller
  double-dispatches; mitigate with a DB `next_run_at` claim + single-instance assumption for now.
- Next Flyway = **V15** (assignment), then **V16** (schedules).

## 2. Scope

**In (C1–C4, assignment):** `assignee_user_id` on executions; assign endpoint; "My Queue" (executions
assigned to the current user across the project); assignee shown on the run's executions; user picker.

**In (C5–C8, scheduling — after sign-off):** `run_schedules` (project, repo, workflowId, ref, inputs,
cadence via `next_run_at`, enabled, last_dispatched_at); CRUD (MANAGE_PROJECT); a `@Scheduled` poller
that dispatches due schedules through `triggerDispatch` and advances `next_run_at`; a manual
"dispatch now"; a Schedules tab.

**Out:** platform-native runner; a full cron-expression engine (use interval + `next_run_at`);
multi-instance distributed locking (single-dispatcher assumption, flagged).

**Data:**
- V15: `test_case_executions` gains `assignee_user_id UUID REFERENCES users(id)`.
- V16: `run_schedules` (`id`, `project_id` FK, `name`, `repo_full_name`, `workflow_id` BIGINT, `ref`,
  `inputs` jsonb, `interval_minutes` int, `next_run_at` timestamptz, `enabled` bool,
  `last_dispatched_at`, `created_at`, `updated_at`).

**Endpoints** (RBAC-guarded):
- `PUT …/test-runs/{runId}/executions/{execId}/assignee` — assign (OPERATE_QUALITY).
- `GET …/projects/{projectId}/my-queue` — my assigned executions (VIEW_RESULTS).
- `GET …/projects/{projectId}/assignable-users` — picker list (VIEW_RESULTS).
- `GET/POST/PUT/DELETE …/projects/{projectId}/run-schedules` — CRUD (MANAGE_PROJECT).
- `POST …/run-schedules/{id}/dispatch` — manual fire (MANAGE_PROJECT).
- Portal BFF proxies for each.

## 3. Acceptance criteria
- An execution can be assigned to a valid user; an invalid user id is rejected; the assignee is shown.
- "My Queue" returns only executions assigned to the calling user, across the project.
- (After sign-off) a schedule persists; the poller dispatches a due schedule via GitHub and advances
  `next_run_at`; `last_dispatched_at` records the fire; disabled schedules never dispatch.
- Every endpoint RBAC-guarded; migrations additive/forward-only; portal design-token UI, `tsc -b` clean.

## 4. Commands / testing
- `mvn -q -pl <module> [-am] test`; `cd platform-portal/frontend && npx tsc -b`.
- Migrations validated via rolled-back psql. JUnit5 + Mockito + AssertJ; cover assign validation, my-queue
  scoping, schedule due-selection + dispatch call (mock `triggerDispatch`), disabled-skip.

## 5. Boundaries
**Always:** RBAC-guard endpoints; validate the assignee user; additive/forward-only migrations; reuse
`triggerDispatch` (don't reimplement GitHub calls); design-token UI. **Ask first / sign-off:** the CI
dispatch half (C5–C8) — secrets + external Actions. **Never:** log/expose the PAT; put a username in
`created_by`/`executed_by` as identity-of-record (assignee lives in `assignee_user_id`); edit a shipped
migration; double-dispatch (guard with `next_run_at`).
