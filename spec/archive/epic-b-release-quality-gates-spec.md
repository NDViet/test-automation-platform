# SPEC — Epic B: Enforcing release quality gates

> Status: **APPROVED — build in progress.** Second slice of the quality-cycle roadmap
> (`spec/quality-cycle-roadmap.md`). Epic A (failure → defect → retest) is complete and archived at
> `spec/archive/epic-a-failure-defect-retest-spec.md`.

## 1. Objective

Turn quality data the platform already computes (pass-rate, coverage, flakiness, open failure
clusters) into an **enforcing go/no-go decision on a release**. A project defines gate thresholds; a
release can be **evaluated** against them (per-criterion PASS/FAIL); and the transition to
`RELEASED` is **blocked** unless the gate passes — with a **capability-gated, audited override** so a
QA lead can ship anyway with a recorded reason. No hard lock without an escape.

### Target users
QA leads / eng managers (configure thresholds, read go/no-go, override with justification), QA
engineers (see why a release is blocked).

### Locked decisions (from the program roadmap)
1. **Enforcing gate**: the gate **blocks** the `SotRelease` `state → RELEASED` transition until
   criteria pass. (No in-platform deploy trigger exists; the release transition is the enforcement
   point — grounded against `ReleaseService.applyEditable`.)
2. **Override path**: an explicit `MANAGE_PROJECT`-gated override with a **required reason**, recorded
   on the release and **audited** (`@AuditLog`). Never a lock with no escape.
3. **Layer on existing metrics**: reuse `TestExecution`/`test_runs` (pass-rate), `CoverageService`
   (coverage %), `FlakinessScore` (critical-flaky), Epic A `FailureCluster` (open blockers). No new
   metric pipelines.
4. **No custom rule DSL**: thresholds are a fixed set of numeric/boolean criteria.

### Grounding (verified against current code)
- `SotRelease.state` = `PLANNED|IN_PROGRESS|RELEASED|ARCHIVED`; the `state→RELEASED` write happens in
  `ReleaseService.applyEditable` (unguarded today) — the block attaches here.
- A per-run `QualityGateEvaluator` exists in analytics but is **not** release-scoped or persisted; Epic
  B adds a **release-scoped, persisted** gate instead of bending it.
- Metric sources: `test_runs.release_id` (pass-rate), `CoverageService.coverage(projectId, area, team,
  iteration)` (release mapping aligns to these filters), `FlakinessScore.Classification.CRITICAL_FLAKY`,
  `FailureClusterRepository.findByProjectIdAndStatus…(OPEN)`.
- Audit records **no portal-user actor** yet (fixed in Epic D); the override is still `@AuditLog`'d now
  (event + reason + clientIp), and the reason is persisted on the release regardless.
- Next Flyway version = **V14**.

## 2. Scope

**In:**
1. **Gate config** — per-project thresholds: `min_pass_rate` (0–1), `min_coverage_pct` (0–100),
   `max_open_blockers` (int), `max_critical_flaky` (int), `require_suites_executed` (bool), plus
   `enabled`. Get-or-default + upsert.
2. **Release-scoped metrics** — compute the four inputs for one release from existing sources.
3. **Gate evaluation** — per-criterion PASS/FAIL + overall; persist `gate_status` + `gate_evaluated_at`
   on the release.
4. **Enforcement** — block `state → RELEASED` unless the persisted gate is `PASS` or `OVERRIDDEN`
   (else `409` with the failing criteria).
5. **Override** — `POST …/releases/{id}/override-gate` with a required reason → `gate_status =
   OVERRIDDEN`, reason + actor recorded, audited.
6. **UI** — a gate panel on the Release page: per-criterion pass/fail, Evaluate button, Override modal;
   plus a thresholds config form.

**Out:** custom rule DSL; blocking a deploy trigger (none exists); gating non-release transitions;
release-scoped failure clusters (clusters stay project-scoped — "open blockers" is a project count).

**Data (V14, additive/forward-only):**
- `quality_gate` (`id`, `project_id` UNIQUE FK→projects, `enabled` bool default true, `min_pass_rate`
  numeric, `min_coverage_pct` numeric, `max_open_blockers` int, `max_critical_flaky` int,
  `require_suites_executed` bool, `created_at`, `updated_at`).
- `sot_releases` gains: `gate_status` VARCHAR(20) null (`PASS|FAIL|OVERRIDDEN`), `gate_evaluated_at`
  timestamptz null, `gate_override_reason` TEXT null, `gate_overridden_by` VARCHAR(255) null.

**Endpoints** (all RBAC-guarded):
- `GET  …/projects/{projectId}/quality-gate` — config (VIEW_RESULTS).
- `PUT  …/projects/{projectId}/quality-gate` — upsert config (MANAGE_PROJECT).
- `POST …/projects/{projectId}/releases/{id}/evaluate-gate` — evaluate + persist (OPERATE_QUALITY).
- `POST …/projects/{projectId}/releases/{id}/override-gate` — reason required (MANAGE_PROJECT, audited).
- Enforcement lives inside the existing `PUT …/releases/{id}` (no new endpoint).
- Portal BFF proxies for each.

## 3. Acceptance criteria
- A project can save gate thresholds; unset → sensible defaults; `enabled=false` disables enforcement.
- Evaluating a release returns **per-criterion** PASS/FAIL (actual vs threshold) and an overall verdict,
  and persists `gate_status`/`gate_evaluated_at`.
- With `enabled=true` and gate `FAIL`, PUT-ing `state:"RELEASED"` is **rejected (409)** listing the
  failing criteria; with gate `PASS` or `OVERRIDDEN` it succeeds.
- Override sets `OVERRIDDEN`, stores the reason + actor, emits an audit event, and unblocks the release.
- All new endpoints are capability-guarded; V14 is additive/forward-only; portal uses design tokens +
  `ui/` kit and is `tsc -b` clean.

## 4. Commands
- **Backend:** `mvn -q -pl <module> [-am] -Dtest='X#m' -Dsurefire.failIfNoSpecifiedTests=false test`;
  full module `mvn -pl <module> test`. **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Migrations:** Flyway V14, additive, validated via rolled-back psql invariants.
- **Deploy:** deferred (build only), via the `deploy-local` skill when the epic completes.

## 5. Project structure (modules touched)
```
platform-core        # V14 + QualityGate entity/repo + SotRelease gate columns + repos
platform-ingestion   # GateConfigService, ReleaseGateService (eval + persist + override),
                     #   release-metrics aggregator, enforcement in ReleaseService, controllers, @AuditLog
platform-analytics   # (reuse) coverage/flaky/pass-rate reads if a metric needs analytics-side data
platform-portal(+FE) # BFF proxies + gate panel + thresholds config UI
```

## 6. Code style / testing / boundaries
- **Java:** constructor injection; `@RequireCapability`; reuse `CoverageService`, `FailureClusterRepository`,
  `FlakinessScoreRepository`, `AuditLog`. JUnit5 + Mockito + AssertJ; strict stubs (`lenient()` only for
  shared setup). Regression = full module suites green.
- **SQL:** additive, nullable, forward-only; never edit shipped migrations.
- **Frontend:** Tailwind v4 design tokens + `ui/` kit; no raw palette/purple; `tsc -b` gate; one page/commit.
- **Boundaries — Always:** RBAC-guard every endpoint; audit the override; provide the override escape;
  keep migrations additive. **Never:** block with no override; bypass RBAC; put a username in `created_by`
  (override actor lives in `gate_overridden_by`, not origin); log secrets; edit a shipped migration.
