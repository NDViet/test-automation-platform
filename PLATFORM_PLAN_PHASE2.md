# Test Automation Platform — Phase 2 Enhancement Plan

## Table of Contents

1. [Overview](#1-overview)
2. [Current State Baseline](#2-current-state-baseline)
3. [Enhancement Themes](#3-enhancement-themes)
   - [Theme 1 — Developer Experience & Onboarding](#theme-1--developer-experience--onboarding)
   - [Theme 2 — CI/CD Native Integration](#theme-2--cicd-native-integration)
   - [Theme 3 — Advanced Analytics & Intelligence](#theme-3--advanced-analytics--intelligence)
   - [Theme 4 — Test Quarantine System](#theme-4--test-quarantine-system)
   - [Theme 5 — Enhanced AI Capabilities](#theme-5--enhanced-ai-capabilities)
   - [Theme 6 — Notification & Alerting Channels](#theme-6--notification--alerting-channels)
   - [Theme 7 — Performance & Contract Test Support](#theme-7--performance--contract-test-support)
   - [Theme 8 — Enhanced SDK — Test Authoring Utilities](#theme-8--enhanced-sdk--test-authoring-utilities)
   - [Theme 9 — Portal (Missing Module)](#theme-9--portal-missing-module)
   - [Theme 10 — Quality Metrics Expansion](#theme-10--quality-metrics-expansion)
   - [Theme 11 — Enterprise Readiness](#theme-11--enterprise-readiness)
4. [Prioritization Matrix](#4-prioritization-matrix)
5. [Implementation Phases](#5-implementation-phases)
6. [New Module & Data Model Changes](#6-new-module--data-model-changes)
7. [Success Metrics](#7-success-metrics)

---

## 1. Overview

Phase 1 delivered a fully functional quality intelligence backend: multi-framework ingestion,
flakiness scoring, AI failure classification, JIRA lifecycle management, quality gates, alerts,
and observability. **125 tests pass. All 9 modules are complete.**

Phase 2 shifts focus from backend completeness to **adoption, usability, and depth of insight** —
making the platform the daily tool of every engineer, QA lead, and engineering manager, not just
the Test Automation Architect.

### Phase 2 Goals

| Goal | Measurable Target |
|---|---|
| Zero-friction team onboarding | New team integrated in < 30 minutes with archetype + action |
| Real-time CI feedback loop | PR quality comment posted within 60s of run completion |
| Eliminate flakiness noise | 100% of CRITICAL_FLAKY tests automatically quarantined |
| AI cost reduction | ≥ 70% of failure analyses served from root-cause cluster cache |
| Manager self-service | NL query answers any quality question without API knowledge |
| Unified quality number | Suite Health Score (0–100) adopted as release gate KPI |
| CI time reduction | Test Impact Analysis reduces PR CI time by ≥ 50% |

---

## 2. Current State Baseline

### Modules Complete (Phase 1)

| Module | Port | Status | Tests |
|---|---|---|---|
| platform-common | — | ✅ Complete | 2 |
| platform-core | — | ✅ Complete | 2 |
| platform-ingestion | 8081 | ✅ Complete | 7 |
| platform-sdk | — | ✅ Complete | 4 |
| platform-testframework | — | ✅ Complete | — |
| platform-analytics | 8082 | ✅ Complete | 8 |
| platform-integration | 8083 | ✅ Complete | 2 |
| platform-ai | 8084 | ✅ Complete | 2 |
| platform-portal | — | ❌ Not started | — |
| platform-api-gateway | 8080 | ❌ Not started | — |

### Source Formats Supported

`JUNIT_XML` · `CUCUMBER_JSON` · `TESTNG` · `ALLURE` · `PLAYWRIGHT` · `NEWMAN` · `PLATFORM_NATIVE`

### Gaps Driving Phase 2

- No UI — Grafana dashboards are powerful but not accessible to non-engineers
- No CI-native feedback (PR comments, GitHub Checks)
- No test quarantine — flaky tests pollute CI signal
- No test impact analysis — every run executes all tests
- Notification channels are generic webhooks only
- Performance and contract test results are disconnected
- No RBAC or SSO — not enterprise-deployable
- SDK has no HTTP/DB assertion helpers — authoring still requires boilerplate

---

## 3. Enhancement Themes

---

### Theme 1 — Developer Experience & Onboarding

#### 1.1 Maven Archetype / Project Scaffolding

**Problem:** Teams manually set up `pom.xml`, `platform.properties`, `testng.xml`, and base
classes. Error-prone and time-consuming.

**Solution:** A `platform-archetype` Maven archetype that generates a fully wired project:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.platform \
  -DarchetypeArtifactId=platform-archetype \
  -DgroupId=com.example \
  -DartifactId=my-tests \
  -DteamId=team-payments \
  -DprojectId=proj-checkout \
  -Dframework=cucumber|testng|junit5
```

**Generated files:**
- `pom.xml` — correct deps, Surefire config, `parallel.count` property
- `src/test/resources/platform.properties` — pre-filled with team/project
- `src/test/resources/testng.xml` or JUnit5 service loader config
- `src/test/java/.../BaseTest.java` — extending the correct base class
- `src/test/java/.../SampleTest.java` — one passing test demonstrating `log.step()`
- `.github/workflows/test.yml` — complete CI workflow using platform action
- `Dockerfile` — for containerized execution

**New module:** `platform-archetype`

**Value:** New team onboarded in < 30 minutes, zero configuration errors.

---

#### 1.2 IntelliJ IDEA Plugin

**Problem:** Developers have no visibility into test health while writing tests in their IDE.

**Solution:** IntelliJ plugin (`platform-intellij-plugin`) with:

- **Gutter badges** on test methods showing live flakiness classification:
  - 🟢 `STABLE` (score < 0.10)
  - 🟡 `WATCH` (0.10–0.30)
  - 🔴 `FLAKY` (0.30–0.60)
  - ⛔ `CRITICAL_FLAKY` (> 0.60)
- **Hover tooltip:** last failure message, last 5 run statuses, AI root cause summary
- **Right-click context menu:**
  - "View full test history in portal"
  - "Trigger on-demand AI analysis"
  - "Quarantine this test" (creates PR adding `@Quarantined`)
- **Settings panel:** platform URL + API key, refresh interval (default: 5 min)

**New module:** `platform-intellij-plugin` (Kotlin, IntelliJ Platform SDK)

**Value:** Flakiness signal reaches the developer at the point of writing — before CI.

---

#### 1.3 `@TestOwner` Annotation

**Problem:** Failure routing relies on manual JIRA assignment; wrong team gets notified.

**Solution:** New annotation in `platform-testframework`:

```java
@TestOwner(
    team          = "payments",
    slackChannel  = "#payments-alerts",
    oncall        = "PD-PAYMENTS",      // PagerDuty service key
    jiraProject   = "PAY"               // override global JIRA project
)
@Test
public void checkoutFlow() { ... }
```

Platform uses `@TestOwner` metadata to:
- Route Slack/Teams notifications to the owning team's channel
- Create JIRA tickets in the team's project with the correct label
- Generate per-owner quality scorecards in the portal
- Attribute flakiness burden to the correct team

**Changes:** `TestMetadata` annotation + `TestCaseResultDto.owner` field +
`platform-core` migration V14 (`test_case_results.owner_team` column).

---

### Theme 2 — CI/CD Native Integration

#### 2.1 GitHub Actions Composite Action (Production-Ready)

**Problem:** The current `/.github/actions/test-platform-action/action.yml` is a skeleton
with no quality gate enforcement or PR decoration.

**Solution:** Full composite action:

```yaml
- uses: platform/test-platform-action@v2
  with:
    api-key:       ${{ secrets.PLATFORM_API_KEY }}
    team-id:       team-payments
    project-id:    proj-checkout
    quality-gate:  true      # fail workflow if gate doesn't pass (default: true)
    pr-comment:    true      # post result summary as PR comment (default: true)
    check-run:     true      # create GitHub Check with pass/fail (default: true)
    framework:     testng    # junit5 | testng | cucumber | playwright | newman
    report-dir:    target/surefire-reports
```

**Action steps:**
1. Upload report files to platform ingestion (`POST /api/v1/results/ingest`)
2. Poll quality gate (`GET /api/v1/projects/{id}/quality-gate/ci`) with 30s timeout
3. If `pr-comment: true` — call `POST /api/v1/projects/{id}/pr-comments` with PR number
4. If `check-run: true` — create GitHub Check via `gh` CLI with gate result
5. Exit with code 1 if gate failed and `quality-gate: true`

**PR Comment format:**
```
## 🧪 Test Quality Gate: ✅ PASSED

| Metric | Value | Threshold |
|--------|-------|-----------|
| Pass rate | 94.2% | ≥ 80% |
| New failures | 2 | ≤ 10 |
| Execution mode | SEQUENTIAL | — |

### ⚠️ Flaky Tests Detected
- `CheckoutTest#verifyTotal` — flakiness score 0.41 (FLAKY)

### ❌ New Failures (2)
- `PaymentTest#processRefund` — APPLICATION_BUG (AI confidence: 94%)
- `CartTest#addItem` — INFRASTRUCTURE (AI confidence: 88%)

[🔗 View full run](https://platform.example.com/runs/run-abc123)
```

---

#### 2.2 GitLab CI / Jenkins / Azure DevOps Templates

**Solution:** Pre-built includable templates for each CI system:

**GitLab CI** (`.gitlab-ci.yml` include):
```yaml
include:
  - project: 'platform/ci-templates'
    file: '/platform-quality-gate.yml'

test:
  extends: .platform-quality-gate
  variables:
    PLATFORM_TEAM_ID: team-payments
    PLATFORM_PROJECT_ID: proj-checkout
```

**Jenkins** (shared library `vars/platformQualityGate.groovy`):
```groovy
platformQualityGate teamId: 'team-payments', projectId: 'proj-checkout'
```

**Azure DevOps** (task extension):
```yaml
- task: PlatformQualityGate@2
  inputs:
    teamId: team-payments
    projectId: proj-checkout
    failOnGateBreak: true
```

**New repo:** `platform-ci-templates` (separate repository, distributed as versioned artifacts)

---

#### 2.3 Branch Quality Gate (Merge Protection)

**Problem:** Quality gate result is reported but cannot block a merge.

**Solution:** New `BranchProtectionService` in `platform-analytics`:
- Registers a GitHub Commit Status on every push to a protected branch (via GitHub API)
- Status is `pending` while analysis runs, `success`/`failure` when gate evaluates
- Branch protection rule: require `platform/quality-gate` status check before merge
- Configurable per project in `IntegrationConfig`: `blockMergeOnGateFailure: true`

**New entity:** `BranchProtectionConfig` (projectId, provider, repoSlug, protectedBranches[])

---

### Theme 3 — Advanced Analytics & Intelligence

#### 3.1 Test Impact Analysis (TIA)

**Problem:** Every CI run executes all tests. PRs with small changes pay full suite cost.

**Solution:** New `TestImpactService` in `platform-analytics`:

**How it works:**
1. **Coverage ingestion** — new `SourceFormat.JACOCO_XML` parser ingests JaCoCo XML reports,
   mapping `className → [testCaseId]` (which tests cover which classes)
2. **Impact query** — given a git diff, return the set of test IDs covering changed classes
3. **Fallback** — manual `@AffectedBy("com.example.PaymentService")` annotation on test class

**API:**
```
GET /api/v1/projects/{id}/impact?commitSha=abc123&baseSha=main
→ {
    "recommendedTests": ["CheckoutTest#verifyTotal", ...],
    "totalTests": 142,
    "selectedTests": 31,
    "estimatedReduction": "78%",
    "riskLevel": "LOW",
    "uncoveredChangedClasses": []
  }
```

**SDK integration:**
```java
@AffectedBy({"com.example.PaymentService", "com.example.CartService"})
class CheckoutTest extends PlatformBaseTest { ... }
```

**New DB table:** `test_coverage_mapping` (test_case_id, class_name, method_name, project_id)

**Value:** 50–80% CI time reduction on incremental PRs.

---

#### 3.2 Duration Regression Detection

**Problem:** Tests silently get slower by small percentages each week. No alert until timeout.

**Solution:** Enhancements to `TrendAnalysisService`:
- Track per-test P95 duration over a 30-day rolling window
- Alert rule `TestDurationRegressed`: when P95 exceeds 30-day baseline by > 25%
- New Grafana panel "Slowest Growing Tests" — sorted by duration increase rate (ms/day)
- New API: `GET /api/v1/projects/{id}/trends/duration-regressions?threshold=25`

**New Prometheus alert:**
```yaml
- alert: TestDurationRegressed
  expr: |
    (platform_test_duration_p95_ms - platform_test_duration_p95_baseline_ms)
    / platform_test_duration_p95_baseline_ms > 0.25
  for: 3d
  labels:
    severity: LOW
```

---

#### 3.3 Change-Failure Correlation

**Problem:** No link between git commits and which tests broke on that commit.

**Solution:**
- Add `commitSha` and `changedFiles` (JSON array) to `TestExecution`; populated from CI env vars
  (`GITHUB_SHA`, `CI_COMMIT_SHA`, `GIT_COMMIT`)
- New `CommitQualitySnapshot` entity: commit SHA → test execution → failure count, new failures,
  fixed tests, commit message, author
- New API: `GET /api/v1/projects/{id}/commits?days=7` — list commits with pass/fail delta
- New Grafana panel: "Failure Rate by Commit" — identify the commit that introduced regressions

**New DB migration V14:** `test_executions.commit_sha VARCHAR(40)`, `test_executions.changed_files JSONB`

---

#### 3.4 Predictive Flakiness Risk

**Problem:** Flakiness score is reactive. Tests degrade before detection.

**Solution:** Extend `FlakinessScoringService` with leading indicators:

| Indicator | Signal | Weight |
|---|---|---|
| Duration variance | High stddev → timing sensitivity | 0.20 |
| Environment sensitivity | Fails more in staging than local | 0.25 |
| Assertion volatility | Detected by Claude code analysis | 0.30 |
| Recent pass/fail alternation | PFPFPF pattern in last 10 runs | 0.25 |

New field: `FlakinessScore.predictedRisk` (RISING / STABLE / DECLINING) with 30-day forecast.
Surfaced in the portal as "At Risk" badge before the test becomes formally FLAKY.

---

#### 3.5 Suite Health Score

**Problem:** No single number captures overall test quality. Release decisions use subjective judgment.

**Solution:** Composite `SuiteHealthScore` (0–100) per project, computed nightly:

| Component | Weight | Formula |
|---|---|---|
| 7-day avg pass rate | 35% | `passRate7d × 100` |
| Flakiness burden | 25% | `(1 − criticalFlaky / totalTests) × 100` |
| Duration vs baseline | 15% | `max(0, 100 − durationDeltaPct)` |
| Coverage delta | 15% | `50 + min(50, coverageDeltaPct × 5)` |
| Maintenance velocity | 10% | `max(0, 100 − avgDaysOpenPerFailure × 2)` |

New entity: `SuiteHealthSnapshot` (projectId, score, components JSON, computedAt)

New Grafana panel: "Suite Health Score" — stat panel 0–100 with thresholds
(≥80 green, 60–79 yellow, <60 red) + 30-day sparkline.

Used as the primary KPI in release gate decisions.

---

### Theme 4 — Test Quarantine System

**Problem:** CRITICAL_FLAKY tests fail CI builds even when the failure is known and tracked.
Engineers either ignore failures (signal degradation) or manually skip tests (technical debt).

**Solution:** First-class quarantine lifecycle in `platform-testframework`:

```java
@Test
@Quarantined(
    since    = "2026-03-01",
    reason   = "PLAT-1234",          // JIRA/Linear ticket
    reviewBy = "2026-04-01"          // mandatory review date
)
void flakyPaymentTest() { ... }
```

**Behavior:**
- Platform still runs the test and records the result
- Test is excluded from quality gate evaluation (no false CI failures)
- If `reviewBy` date is past → quality gate warns "Quarantine overdue"
- If test passes 5 consecutive runs → platform auto-suggests removing `@Quarantined`

**New API endpoints:**
```
GET  /api/v1/projects/{id}/quarantine          — list all quarantined tests
POST /api/v1/projects/{id}/quarantine          — quarantine a test by testId
DELETE /api/v1/projects/{id}/quarantine/{testId} — remove quarantine
```

**Automatic quarantine suggestion:** when `classification == CRITICAL_FLAKY` for 3+ consecutive
days, platform creates a GitHub PR adding `@Quarantined` to the test class.

**New Prometheus metric:** `platform_tests_quarantined` Gauge (by project)

**New alert:** `QuarantineBurdenHigh` — fires when > 10% of suite is quarantined
(signals systemic quality problem, not individual flakiness).

**New Grafana panel:** "Quarantine Queue" table — test name, quarantined since, review date,
JIRA link, consecutive passes since quarantine.

---

### Theme 5 — Enhanced AI Capabilities

#### 5.1 Root Cause Clustering (Cost Reduction)

**Problem:** Same infrastructure failure (e.g., DB connection timeout) triggers individual Claude
calls for each affected test. Wastes tokens; provides no cross-test insight.

**Solution:** Before calling Claude, check OpenSearch k-NN similarity:

1. Vectorize the new failure's stack trace (existing embedding infrastructure)
2. Query OpenSearch for nearest `FailureAnalysis` with cosine similarity > 0.85
3. If match found: reuse `rootCause`, `failureCategory`, `confidence`; set `reuseOf` FK
4. If no match: call Claude as normal; store new embedding

**New fields:** `FailureAnalysis.reuseOf` (FK to canonical analysis),
`FailureAnalysis.clusterSize` (how many analyses share this root cause)

**New metric:** `platform_ai_reuse_rate` Gauge — % analyses served from cache vs new Claude calls

**Target:** ≥ 70% reuse rate on mature projects, reducing AI cost by the same factor.

**New Grafana panel:** "Root Cause Clusters" — treemap of cluster sizes by category,
showing the top 10 distinct root causes currently active.

---

#### 5.2 Claude Vision — Screenshot Failure Analysis

**Problem:** Playwright/Selenium screenshots are attached to failures but never analyzed;
visual regressions require manual investigation.

**Solution:** When a failure has a screenshot attachment AND the failure category is
`APPLICATION_BUG` or `UNKNOWN`, send the image to Claude Opus 4.6 with vision:

```
System: You are a test failure analyst. A UI test failed. Analyze the screenshot.
User:   Assertion: expected "Welcome, John" but got assertion error.
        [screenshot bytes as base64 image]
        What does the UI show? Is the expected state present? What is the likely cause?
```

**New field:** `FailureAnalysis.screenshotAnalysis` (TEXT) — Claude's visual interpretation

**Changes in `PromptBuilder`:** include screenshot when available
**Changes in `ClaudeApiClient`:** use multipart message content with image block

**Particularly valuable for:** Playwright tests, Selenium visual checks, responsive design failures.

---

#### 5.3 Natural Language Query — "Ask Your Test Data"

**Problem:** Analytics require knowing API endpoints and query parameters. Engineering managers
and QA leads cannot self-serve.

**Solution:** New `NlQueryService` in `platform-ai`:

```
POST /api/v1/query
{ "question": "Which tests started failing after the last deploy to staging?" }
→ {
    "answer": "3 tests started failing on 2026-03-08 after deploy abc123 to staging: ...",
    "supporting": { "runIds": [...], "testIds": [...] },
    "queryUsed": "GET /api/v1/projects/proj-checkout/trends/pass-rate?days=3&env=staging"
  }
```

**How it works:**
1. Claude converts the natural language question to one or more platform API calls
2. Platform executes the calls and returns results to Claude
3. Claude synthesizes a human-readable answer with evidence

**Portal surface:** Chat panel available on every project page.
**Use cases:** Release go/no-go questions, root cause investigation, trend analysis.

---

#### 5.4 Auto-Generated Test Cases from Bug Reports

**Problem:** When a bug is found manually, writing a regression test is deferred or forgotten.

**Solution:**
```
POST /api/v1/projects/{id}/generate-test
{
  "jiraKey":   "PAY-456",
  "framework": "CUCUMBER",
  "baseClass": "com.example.BaseTest"
}
→ {
    "featureFile":    "...",   // Gherkin text
    "stepDefinitions": "..."   // Java step class skeleton
  }
```

Claude fetches the JIRA issue (description, acceptance criteria, steps to reproduce) and
generates: Gherkin feature file + step definition skeleton with `// TODO` markers.
Not production-ready, but eliminates the blank-page problem; cuts authoring time by ~60%.

---

### Theme 6 — Notification & Alerting Channels

#### 6.1 Slack Rich Notifications

**Problem:** Current webhook sends raw JSON. No threading, no formatting, wrong channel routing.

**Solution:** First-class Slack channel in `platform-integration`:

**Config:**
```java
@IntegrationConfig(type = SLACK)
// platform.properties
platform.notifications.slack.webhook-url=https://hooks.slack.com/...
platform.notifications.slack.default-channel=#test-alerts
platform.notifications.slack.mention-on-critical=@oncall
```

**Message format (quality gate failure):**
```
🔴 Quality Gate FAILED — proj-checkout (main)

Pass rate: 72.3%  (threshold: 80%)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ 3 new failures  |  ⚠️ 2 flaky tests
🏃 142 total tests  |  ⏱ 4m 32s

Top failures:
• CheckoutTest#verifyOrderTotal
  → APPLICATION_BUG (AI: 94%)  PLAT-1234
• PaymentTest#processRefund
  → INFRASTRUCTURE (AI: 88%)

👉 View report  |  🔕 Snooze 1hr
```

**Features:**
- Thread updates: re-runs update the same Slack thread, not new messages
- `@TestOwner` routing: failures routed to `slackChannel` from the annotation
- Interactive buttons: "View in portal" deep-link, "Snooze alert for 1hr"

---

#### 6.2 Microsoft Teams Integration

Same as Slack but using Teams Adaptive Cards format. Configured independently:
```properties
platform.notifications.teams.webhook-url=https://outlook.office.com/webhook/...
```

---

#### 6.3 PagerDuty / OpsGenie Integration

**Problem:** Critical test failures in staging/production environments have no on-call escalation.

**Solution:**
```properties
platform.notifications.pagerduty.api-key=...
platform.notifications.pagerduty.service-key=...
platform.notifications.pagerduty.trigger-on=CRITICAL    # alert severity
platform.notifications.pagerduty.environments=staging,production
```

Triggers PagerDuty incident when:
- `severity == CRITICAL` AND `environment IN (staging, production)`
- Auto-resolves when the next run passes the gate

**Avoids alert fatigue:** does NOT trigger for `dev` or `local` environments.

---

#### 6.4 Weekly Digest Email

**Problem:** No proactive quality reporting for engineering managers; they must pull data.

**Solution:** Scheduled job (Monday 09:00, configurable) sends per-team digest email:

```
Subject: [Platform] Weekly Quality Digest — team-payments (Mar 3–9, 2026)

Suite Health Score: 82/100  ▲ +4 vs last week

✅ Pass rate:    94.2%  (▲ +1.3%)
⚠️ Flaky tests:     3  (▲ +1 new this week)
⏱ Avg duration:  4m12s  (▲ +23s — 2 tests regressed)
🎫 Open tickets:    5   (1 resolved this week)

Top new failures this week:
1. CheckoutTest#verifyTotal  [PLAT-1234]
2. PaymentTest#processRefund [PLAT-1235]

Quarantine queue: 1 test (review due 2026-04-01)
```

---

### Theme 7 — Performance & Contract Test Support

#### 7.1 k6 / Gatling / JMeter Result Ingestion

**Problem:** Performance test results are entirely disconnected from the quality platform.
Release decisions ignore performance regressions.

**Solution:** New `SourceFormat` values and parsers:

| Format | Parser | Maps to |
|---|---|---|
| `K6_JSON` | `K6ResultParser` | Each k6 `check` → TestCaseResult |
| `GATLING_LOG` | `GatlingParser` | Each request group → TestCaseResult |
| `JMETER_XML` | `JMeterParser` | Each sampler → TestCaseResult |

**Performance baseline management:**
- First run for a test establishes the P95 baseline
- Subsequent runs compare against baseline; failure = regression > configurable threshold
- New quality gate rule: `p95LatencyMs < 500` in gate config

**New Grafana dashboard section:** "Performance Trends"
- P50 / P95 / P99 latency over time (per test group)
- Throughput (req/s) trend
- Error rate trend
- Baseline deviation heatmap

---

#### 7.2 Pact Contract Test Integration

**Problem:** API contract test failures are not tracked; provider breaks are invisible until
integration tests fail downstream.

**Solution:**
- New `SourceFormat.PACT_JSON` parser
- Contract test failures appear in the quality gate
- Cross-team bug routing: when a consumer contract fails, JIRA ticket is created in
  the *provider team's* JIRA project (resolved from `IntegrationConfig`)
- New Grafana panel: "Contract Coverage" — which provider/consumer pairs have green contracts

---

### Theme 8 — Enhanced SDK — Test Authoring Utilities

#### 8.1 `TestRestClient` — HTTP Client with Auto-Logging

**Problem:** API test authors manually log request/response details; it's boilerplate often skipped.
Missing request context makes failure investigation slow.

**Solution:** Fluent HTTP wrapper in `platform-testframework`:

```java
TestRestClient client = TestRestClient.create(log, baseUrl);

Response response = client
    .post("/api/orders")
    .header("Authorization", "Bearer " + token)
    .contentType("application/json")
    .body(orderPayload)
    .execute();
// Auto-logged as step: "POST /api/orders → 201 Created (143ms)"

// Assert and attach response
assertThat(response.statusCode()).isEqualTo(201);
log.attach("response.json", response.bodyBytes(), "application/json");
```

**Auto-logged per call:**
- Step name: `{METHOD} {path} → {status} ({durationMs}ms)`
- Request/response headers (sensitive headers redacted by default: Authorization, Cookie)
- Response body attached as named attachment (configurable size limit, default 10KB)
- Failed requests auto-logged at ERROR level with full response body

**Backends:** RestAssured adapter + OkHttp adapter (selectable via builder).

---

#### 8.2 `DbAssertion` — Database Verification Steps

**Problem:** Verifying database state after a test action requires boilerplate JDBC code
with no step tracking, making failures hard to diagnose.

**Solution:**

```java
DbAssertion.using(dataSource, log)
    .assertThat("orders WHERE id = ?", orderId)
    .hasExactlyOneRow()
    .column("status").isEqualTo("CONFIRMED")
    .column("amount").isGreaterThan(BigDecimal.ZERO);
// Logged: "DB Assert: SELECT * FROM orders WHERE id=123 → 1 row, status=CONFIRMED ✓"

DbAssertion.using(dataSource, log)
    .assertThat("audit_events WHERE entity_id = ?", orderId)
    .hasAtLeastRows(1)
    .column("event_type").containsAnyOf("ORDER_CREATED", "PAYMENT_PROCESSED");
```

**Failures** produce a clear step log:
```
FAILED — DB Assert: SELECT * FROM orders WHERE id=123
  Expected: status = CONFIRMED
  Actual:   status = PENDING
```

---

#### 8.3 `TestData` Builder — Lifecycle Tracking

**Problem:** Test data creation/cleanup is untracked. Orphaned data causes cross-test
contamination and non-deterministic failures.

**Solution:**

```java
// In @BeforeEach or test body
Order order = testData
    .create(Order.class)
    .with("customerId", customerId)
    .with("status", "PENDING")
    .named("checkout test order")   // appears in step log
    .build();
// Logged: "TestData: created Order#123 (checkout test order)"

// Cleanup happens automatically on test end (pass or fail)
// Cleanup failures are logged as WARN, never fail the test
```

**Features:**
- Automatic cleanup registry: `testData.cleanup()` called by `PlatformExtension.afterEach()`
- Cleanup order respects FK dependencies (reverse creation order)
- Named test data appears as a log entry, making failure investigation clearer
- Works with JPA (`EntityManager`), JDBC (`DataSource`), or REST (cleanup via DELETE call)

---

#### 8.4 Appium / Mobile Support

**Problem:** Mobile test results are not tracked; no mobile-specific failure analysis.

**Solution:** `PlatformAppiumExtension` mirroring `PlatformPlaywrightExtension`:

```java
class LoginMobileTest extends PlatformBaseTest {

    @RegisterExtension
    PlatformAppiumExtension appium = PlatformAppiumExtension.builder()
            .appiumUrl("http://localhost:4723")
            .capability("platformName", "Android")
            .capability("deviceName", "Pixel_8_API_35")
            .capability("app", "/path/to/app.apk")
            .build();

    @Test
    void userCanLogin(AppiumDriver driver) {
        log.step("Launch app and verify login screen");
          assertThat(driver.findElement(By.id("login_button"))).isDisplayed();
        log.endStep();
    }
}
```

**Auto-captured on failure:**
- Screenshot
- Device info (OS version, device name, app version) → `env()` metadata
- Appium server logs (last 50 lines)

---

### Theme 9 — Portal (Missing Module)

The portal is the highest-leverage missing piece. Grafana dashboards are powerful for
engineers but inaccessible to QA leads, product managers, and engineering managers.

#### 9.1 Technical Stack

- **Frontend:** React 19.2.4 + Vite 7.3.1 + TypeScript
- **UI components:** shadcn/ui + Tailwind CSS
- **Data fetching:** TanStack Query v5 (caching + auto-refresh)
- **Charts:** Recharts
- **Real-time:** native WebSocket (subscribe to live run updates)
- **Auth:** OIDC via Spring Security OAuth2 (Theme 11.2)
- **BFF:** Spring Boot 4 REST layer in `platform-portal` module (proxies to backend services)

#### 9.2 Routes & Pages

| Route | Page | Primary Users |
|---|---|---|
| `/` | Org overview: suite health scores, critical alerts, top failing teams | Architect, VP Eng |
| `/teams/:teamSlug` | Team dashboard: projects, pass rate trend, flakiness, open tickets | QA Lead |
| `/projects/:id` | Project dashboard: pass rate, health score, recent runs, flaky tests | Dev, QA |
| `/runs/:runId` | Run detail: test list, filter by status, expand to steps + logs | Dev |
| `/tests/:testId` | Test history: every run, AI analyses, flakiness trend, JIRA links | Dev, QA |
| `/release-gate` | Release readiness: select tag/branch, per-rule pass/fail verdict | Release Manager |
| `/quarantine` | Quarantine queue: all quarantined tests, age, owner, review dates | QA Lead |
| `/alerts` | Alert history with severity filter and snooze/resolve actions | Dev, QA Lead |
| `/ask` | Natural language query chat interface | Any |
| `/settings/api-keys` | API key management | Team Admin |
| `/settings/integrations` | JIRA / Slack / PagerDuty config per team | Team Admin |
| `/settings/gates` | Quality gate rule configuration per project | Team Admin |

#### 9.3 Live Run View

WebSocket subscription to `test.results.raw` Kafka topic (via Spring WebSocket bridge)
shows real-time test-by-test progress during an active CI run:

- Live progress bar: X/N tests complete
- Pass rate updating in real time
- Failures streaming as they happen with AI category badge
- "Stop run" button (sends kill signal via API — for self-hosted executors)

#### 9.4 Release Gate Page

Structured view for release go/no-go decision:

```
Release Readiness — proj-checkout  v2.4.0 (tag: release-2.4.0)

Overall: 🟢 READY TO RELEASE

Gate Rules:
✅ Pass rate ≥ 80%          94.2%      (last 3 runs on main)
✅ No CRITICAL flaky tests  0          (quarantined: 1)
✅ P95 latency < 500ms      312ms      (performance suite)
✅ Contract tests passing   8/8        (provider: api-gateway)
⚠️  Coverage delta ≥ 0%     -1.2%      (WARNING — not blocking)
✅ No open P1 JIRA bugs     0 open

Suite Health Score: 82/100  ▲ +4 vs previous release
```

---

### Theme 10 — Quality Metrics Expansion

#### New Prometheus Metrics

| Metric | Type | Tags | Description |
|---|---|---|---|
| `platform.suite.health.score` | Gauge | project | Composite 0–100 score |
| `platform.tests.quarantined` | Gauge | project | Active quarantined test count |
| `platform.tests.duration.p95.delta` | Gauge | project | % vs 30d baseline |
| `platform.coverage.line.delta` | Gauge | project | % coverage change vs previous run |
| `platform.pr.gate.result` | Counter | project, branch, result | PR gate pass/fail |
| `platform.ticket.time_to_create_ms` | Histogram | project, category | Failure → JIRA ticket latency |
| `platform.ticket.time_to_resolve_ms` | Histogram | project, category | JIRA open → close duration |
| `platform.ai.reuse.rate` | Gauge | project | % analyses from cluster cache |
| `platform.notification.delivery` | Counter | channel, result | Slack/PagerDuty delivery |
| `platform.test.impact.reduction` | Gauge | project | % tests skipped by TIA |
| `platform.quarantine.overdue` | Gauge | project | Tests past `reviewBy` date |

#### New Grafana Panels

**quality-report dashboard:**
- Suite Health Score (0–100) stat panel with threshold colors + 30-day sparkline
- Quarantine Burden % — gauge showing quarantined / total tests
- Release Readiness History — pass/fail per release tag

**failure-analysis dashboard:**
- Root Cause Cluster treemap — cluster size by root cause category
- AI Cost Saved — cumulative tokens saved by cluster reuse
- Screenshot Analysis Coverage — % of UI failures with visual analysis

**execution-overview dashboard:**
- Test Impact Analysis Efficiency — % test reduction over time
- Coverage Delta Trend — line chart of coverage change per run

**platform-health dashboard:**
- Ticket MTTR — histogram of mean time to resolve failures
- Notification Delivery Rate — by channel (Slack, Teams, PagerDuty)

---

### Theme 11 — Enterprise Readiness

#### 11.1 RBAC

**Problem:** All authenticated API keys have `ROLE_INGESTION`; no access control within the portal.

**Solution:** Role model:

| Role | Scope | Permissions |
|---|---|---|
| `ORG_ADMIN` | Organization | Create teams, manage all projects, view all data, manage API keys |
| `TEAM_ADMIN` | Team | Manage team's projects, API keys, integration config, gate rules |
| `TEAM_MEMBER` | Team | View team's data, trigger on-demand AI analysis, quarantine tests |
| `VIEWER` | Organization | Read-only across all teams (for manager dashboards) |

New entity: `TeamMember` (userId, teamId, role, grantedAt, grantedBy)

RBAC enforced at:
- REST API level via Spring Security `@PreAuthorize`
- Portal route level (redirect to 403 page)
- Kafka consumer level (results only processed if team has active subscription)

---

#### 11.2 SSO / OIDC

```properties
# application.yml (portal/gateway)
spring:
  security:
    oauth2:
      client:
        provider:
          okta:
            issuer-uri: https://dev-12345.okta.com/oauth2/default
        registration:
          okta:
            client-id:     ${OIDC_CLIENT_ID}
            client-secret: ${OIDC_CLIENT_SECRET}
            scope:         openid, profile, email, groups
```

OIDC groups mapped to platform roles via `OidcGroupRoleMapper` configuration.
Supported providers: Okta, Azure AD, Google Workspace, Keycloak.

---

#### 11.3 Data Retention Policies

```properties
platform.retention.default-days=90    # keep raw results 90 days
platform.retention.aggregates=forever # keep trend aggregates indefinitely
platform.retention.logs-days=30       # OpenSearch log retention
```

- Configurable per team in `IntegrationConfig`
- Nightly cleanup job purges `test_case_results` and `test_executions` older than TTL
- Summarized aggregates (flakiness scores, daily pass rates) kept forever
- Alert when DB storage > 80% of configured limit

New entity: `RetentionPolicy` (teamId, rawResultDays, logDays)

---

#### 11.4 CI Minute Cost Tracking

**Problem:** No visibility into which teams consume the most CI resources.

**Solution:**
- Add `ciMinutesConsumed = ceil(parallelism × durationMs / 60000)` to `TestExecution`
- Aggregate by team and project per billing period
- New Grafana dashboard: "CI Cost by Team" — bar chart of estimated CI minutes consumed
- Alert: `TeamCiCostSpike` — when a team's weekly CI minutes increase > 50%
- API: `GET /api/v1/org/ci-cost?period=2026-03` for FinOps reporting

---

## 4. Prioritization Matrix

| Priority | ID | Theme | Value | Effort | Phase |
|---|---|---|---|---|---|
| 🔴 **P0** | T9 | Platform Portal | Highest — no UI exists | High | 8A |
| 🔴 **P0** | T2.1 | GitHub Actions (production) | Drives team adoption | Medium | 8A |
| 🟠 **P1** | T4 | Test Quarantine System | Eliminates CI noise immediately | Low | 8A |
| 🟠 **P1** | T5.3 | Root Cause Clustering | Cuts AI cost 70% | Medium | 8A |
| 🟠 **P1** | T3.5 | Suite Health Score | Single release KPI | Medium | 8B |
| 🟠 **P1** | T6.1 | Slack Rich Notifications | Closes daily feedback loop | Low | 8B |
| 🟡 **P2** | T3.1 | Test Impact Analysis | 50–80% CI time reduction | High | 8C |
| 🟡 **P2** | T8.1 | TestRestClient | Reduces API test boilerplate | Low | 8B |
| 🟡 **P2** | T8.2 | DbAssertion | Reduces DB test boilerplate | Low | 8B |
| 🟡 **P2** | T3.2 | Duration Regression Alerts | Prevents silent degradation | Low | 8B |
| 🟡 **P2** | T2.2 | GitLab / Jenkins / AzDO templates | Broader CI adoption | Medium | 8C |
| 🟡 **P2** | T3.3 | Change-Failure Correlation | Engineering accountability | Medium | 8C |
| 🟢 **P3** | T5.2 | NL Query / AI Chat | Manager self-service | Medium | 8D |
| 🟢 **P3** | T7.1 | k6 / Gatling Ingestion | Unified quality view | Medium | 8D |
| 🟢 **P3** | T11.1 | RBAC | Enterprise sales requirement | High | 8D |
| 🟢 **P3** | T11.2 | SSO / OIDC | Enterprise sales requirement | High | 8D |
| 🟢 **P3** | T6.3 | PagerDuty / OpsGenie | On-call integration | Low | 8C |
| 🟢 **P3** | T3.4 | Predictive Flakiness | Proactive quality signal | Medium | 8D |
| 🔵 **P4** | T5.1 | Claude Vision Screenshots | Richer AI analysis | Low | 8E |
| 🔵 **P4** | T1.2 | IntelliJ Plugin | Developer delight | High | 8E |
| 🔵 **P4** | T1.1 | Maven Archetype | Faster onboarding | Low | 8E |
| 🔵 **P4** | T8.3 | TestData Builder | Test data lifecycle | Medium | 8E |
| 🔵 **P4** | T8.4 | Appium Support | Mobile testing | Medium | 8E |
| 🔵 **P4** | T7.2 | Pact Contract Tests | Contract coverage | Medium | 8E |
| 🔵 **P4** | T5.4 | Test Generation from Bugs | AI authoring assist | Medium | 8E |
| 🔵 **P4** | T11.3 | Data Retention Policies | Operational hygiene | Low | 8D |
| 🔵 **P4** | T11.4 | CI Minute Cost Tracking | FinOps | Low | 8D |
| 🔵 **P4** | T6.4 | Weekly Digest Email | Stakeholder visibility | Low | 8D |
| 🔵 **P4** | T1.3 | `@TestOwner` Annotation | Ownership routing | Low | 8B |
| 🔵 **P4** | T2.3 | Branch Merge Protection | Gate enforcement | Low | 8C |

---

## 5. Implementation Phases

### Phase 8A — High-Impact Quick Wins (Weeks 1–4)

**Goal:** Portal MVP live, quarantine working, AI costs cut, GitHub CI adopted by at least 2 teams.

| Deliverable | Module | Effort |
|---|---|---|
| Portal skeleton + org overview page | platform-portal | 1w |
| Portal project + run detail pages | platform-portal | 1w |
| Test Quarantine — `@Quarantined`, API, gate exclusion | platform-testframework + analytics | 3d |
| Root cause clustering (OpenSearch k-NN reuse) | platform-ai | 3d |
| GitHub Actions production action | platform-ci-templates | 3d |
| Slack rich notifications | platform-integration | 2d |

---

### Phase 8B — Analytics Depth (Weeks 5–8)

**Goal:** Suite Health Score adopted as primary KPI; SDK helpers adopted by ≥ 1 new team.

| Deliverable | Module | Effort |
|---|---|---|
| Suite Health Score entity + nightly job + API | platform-analytics | 3d |
| Duration regression detection + alert | platform-analytics | 2d |
| `TestRestClient` HTTP helper | platform-testframework | 3d |
| `DbAssertion` database helper | platform-testframework | 2d |
| `@TestOwner` annotation + routing | platform-testframework + integration | 2d |
| Portal release gate page | platform-portal | 3d |
| Portal quarantine queue page | platform-portal | 2d |
| PagerDuty / OpsGenie integration | platform-integration | 2d |

---

### Phase 8C — CI/CD Breadth (Weeks 9–12)

**Goal:** Test Impact Analysis live; all major CI systems supported.

| Deliverable | Module | Effort |
|---|---|---|
| JaCoCo ingestion + coverage mapping | platform-ingestion | 4d |
| Test Impact Analysis service + API | platform-analytics | 1w |
| SDK `@AffectedBy` annotation | platform-testframework | 1d |
| GitLab CI / Jenkins / Azure DevOps templates | platform-ci-templates | 3d |
| Change-failure correlation (commitSha, changedFiles) | platform-core + analytics | 3d |
| Branch merge protection (GitHub Checks) | platform-integration | 2d |

---

### Phase 8D — Enterprise & Intelligence (Weeks 13–16)

**Goal:** Enterprise-deployable; NL query live; performance tests tracked.

| Deliverable | Module | Effort |
|---|---|---|
| RBAC (TeamMember entity, `@PreAuthorize`) | platform-core + ingestion + analytics | 1w |
| SSO / OIDC (Spring OAuth2 Resource Server) | platform-api-gateway + portal | 1w |
| NL query service (Claude tool use) | platform-ai | 1w |
| k6 / Gatling / JMeter parsers | platform-ingestion | 1w |
| Data retention policies + cleanup job | platform-core + analytics | 3d |
| CI minute cost tracking + dashboard | platform-analytics | 2d |
| Weekly digest email | platform-analytics | 2d |
| Predictive flakiness risk score | platform-analytics | 3d |

---

### Phase 8E — Polish & Ecosystem (Weeks 17–20)

**Goal:** Full ecosystem coverage; developer-facing tools adopted.

| Deliverable | Module | Effort |
|---|---|---|
| Claude Vision screenshot analysis | platform-ai | 3d |
| Maven archetype | platform-archetype | 3d |
| `TestData` builder + lifecycle tracker | platform-testframework | 1w |
| Appium / mobile support | platform-testframework | 1w |
| Pact contract test parser | platform-ingestion | 3d |
| Test generation from bug reports | platform-ai | 1w |
| IntelliJ plugin MVP | platform-intellij-plugin | 2w |
| Helm chart finalization + production hardening | helm/ | 1w |

---

## 6. New Module & Data Model Changes

### New Modules

| Module | Type | Phase |
|---|---|---|
| `platform-portal` | Spring Boot BFF + React SPA | 8A |
| `platform-api-gateway` | Spring Cloud Gateway | 8A |
| `platform-ci-templates` | Composite GitHub Action + CI templates | 8A |
| `platform-archetype` | Maven archetype | 8E |
| `platform-intellij-plugin` | IntelliJ Platform SDK (Kotlin) | 8E |

### New DB Migrations

| Version | Content |
|---|---|
| V14 | `test_executions.commit_sha`, `test_executions.changed_files JSONB` |
| V15 | `suite_health_snapshots` table (projectId, score, components JSONB, computedAt) |
| V16 | `quarantine_entries` table (testId, projectId, since, reason, jiraKey, reviewBy) |
| V17 | `test_coverage_mapping` table (testCaseId, className, methodName, projectId) |
| V18 | `team_members` table (userId, teamId, role, grantedAt, grantedBy) |
| V19 | `retention_policies` table (teamId, rawResultDays, logDays) |
| V20 | `test_case_results.owner_team VARCHAR(100)` |
| V21 | `failure_analyses.reuse_of UUID FK`, `failure_analyses.cluster_size INT` |
| V22 | `failure_analyses.screenshot_analysis TEXT` |

### New Kafka Topics

| Topic | Producer | Consumers |
|---|---|---|
| `test.pr.events` | platform-analytics (gate result) | platform-integration (PR comment, GitHub Check) |
| `test.quarantine.events` | platform-analytics (auto-suggest) | platform-integration (create PR) |

---

## 7. Success Metrics

| Metric | Baseline (Phase 1 end) | Phase 2 Target |
|---|---|---|
| Teams using the platform | — (no portal) | ≥ 5 teams using portal weekly |
| Onboarding time per team | ~2 hours | < 30 minutes (archetype + action) |
| PR quality feedback time | Manual pull | < 60 seconds (automatic PR comment) |
| AI analysis cost per failure | $0.012 avg | < $0.004 avg (70% cluster reuse) |
| CRITICAL_FLAKY tests in CI | Blocking builds | 0 (all quarantined) |
| CI time per PR (TIA-enabled) | Full suite | ≤ 50% of full suite |
| Manager self-service queries | 0 (needs eng) | NL query covers top 20 questions |
| Suite Health Score adoption | Not exists | Primary release gate KPI for ≥ 3 teams |
| Performance tests tracked | 0 | k6/Gatling results in platform for ≥ 1 team |

---

*Plan authored: 2026-03-09*
*Baseline: Phase 1 complete — 125 tests passing, all green*
*Next review: Phase 8A kickoff*
