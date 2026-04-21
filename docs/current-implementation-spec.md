# Test Automation Platform

## Current Implementation Specification

This document describes the system that is implemented in the repository today. It is intentionally code-aligned: it reflects the current modules, services, APIs, and integration points present in the codebase rather than the broader roadmap.

## 1. Platform Summary

### 1.1 Purpose

The platform is a multi-module quality intelligence system for automated testing. Its core job is to:

- ingest test outputs from multiple frameworks and runtimes
- normalize them into a single canonical model
- persist execution history and quality metadata
- compute quality analytics such as flakiness, trends, and quality gates
- classify failures using AI
- manage issue-tracker lifecycle for repeated failures and flaky tests
- expose the data through a BFF-backed portal and Grafana/OpenSearch dashboards

### 1.2 Architectural Shape

The repository is a Maven multi-module monorepo with:

- 5 deployable Spring Boot services
- 2 shared Java libraries for platform contracts and persistence
- 1 native Java testkit for richer instrumentation
- 1 adapter family for zero-code publishing
- 1 frontend SPA embedded in the portal service
- infra assets for local Docker Compose and Helm-based deployment

### 1.3 Runtime Services

| Service | Port | Responsibility |
|---|---:|---|
| `platform-ingestion` | 8081 | Accepts report uploads, parses them, persists normalized runs, publishes Kafka events, exposes team/project/execution queries |
| `platform-analytics` | 8082 | Computes flakiness, trends, quality gates, alerts, release reports, TIA, and OpenSearch log queries |
| `platform-integration` | 8083 | Consumes result events and manages issue-tracker lifecycle |
| `platform-ai` | 8084 | Performs AI-based failure classification and stores analysis history |
| `platform-portal` | 8085 | Spring Boot BFF plus embedded React SPA |

### 1.4 Shared Modules

| Module | Role |
|---|---|
| `platform-common` | Shared DTOs, enums, and Kafka topic names |
| `platform-core` | Domain entities, Flyway schema, repositories, shared persistence services |

### 1.5 Client/SDK Modules

| Module | Role |
|---|---|
| `platform-testkit` | Native Java instrumentation layer for JUnit 5, TestNG, Cucumber, Playwright-assisted tests, and custom runners |
| `platform-adapters` | Zero-code publishing adapters for Java test reports plus JS adapters for Playwright and k6 |

## 2. End-to-End Flow

### 2.1 Primary Result Flow

1. A test framework or adapter submits report files to `platform-ingestion`.
2. `platform-ingestion` selects a parser based on `SourceFormat`.
3. Parsed output is normalized into `UnifiedTestResult`.
4. `platform-core` persistence writes:
   - one `test_executions` row per run
   - one `test_case_results` row per test case
   - optional `performance_metrics` row for performance runs
   - optional `test_coverage_mappings` rows for TIA
5. `platform-ingestion` publishes the normalized run to Kafka topic `test.results.raw`.
6. Downstream services consume the event:
   - `platform-analytics` computes flakiness, gates, and alerts
   - `platform-ai` optionally performs real-time classification
   - `platform-integration` evaluates ticket lifecycle actions

### 2.2 Portal Flow

The portal does not query the database directly. The React app calls the portal BFF at `/api/portal/**`, and the BFF fans out to ingestion, analytics, and AI services using `RestClient`.

## 3. Core Contracts

### 3.1 Canonical Result Model

`platform-common` defines the platform contract.

#### `UnifiedTestResult`

Represents one normalized execution run. It carries:

- run identity: `runId`, `teamId`, `projectId`
- source metadata: branch, environment, commit SHA, CI provider, CI run URL
- execution summary: totals for passed, failed, skipped, broken, and duration
- execution metadata: execution mode, parallelism, suite name
- classification metadata: `SourceFormat` and derived `TestType`
- detailed payload: `List<TestCaseResultDto>`
- performance payload: `PerformanceMetricsDto` for performance test formats

#### `TestCaseResultDto`

Represents one logical test case and supports both parser-ingested and native SDK-published runs. It includes:

- logical identifiers: `testId`, class name, method name, display name
- status and timing
- failure message and stack trace
- retry count and attachments
- optional rich data from the testkit:
  - structured steps
  - trace ID
  - environment map
  - covered classes for TIA

#### `PerformanceMetricsDto`

Carries aggregate performance metrics such as:

- average/min/median/max latency
- p90/p95/p99 latency
- total requests and throughput
- error rate
- max VUs
- duration

### 3.2 Enumerations

Implemented enums include:

- `SourceFormat`: `JUNIT_XML`, `CUCUMBER_JSON`, `TESTNG`, `ALLURE`, `PLAYWRIGHT`, `NEWMAN`, `PLATFORM_NATIVE`, `K6`, `GATLING`, `JMETER`
- `TestStatus`: `PASSED`, `FAILED`, `SKIPPED`, `BROKEN`
- `TriggerType`: `CI_PUSH`, `CI_PR`, `SCHEDULED`, `MANUAL`
- `TestType`: currently inferred mainly as `FUNCTIONAL` or `PERFORMANCE`

### 3.3 Kafka Topics

Defined centrally in `Topics`:

- `test.results.raw`
- `test.results.analyzed`
- `test.flakiness.events`
- `test.integration.commands`
- `test.alert.events`

The current implementation actively uses `test.results.raw`. The other topic constants are reserved for broader event choreography.

## 4. Persistence Specification

`platform-core` owns the persistent domain model and Flyway migrations `V1` through `V21`.

### 4.1 Entity Set

| Entity | Purpose |
|---|---|
| `Team` | Team registry keyed by slug |
| `Project` | Project registry under a team |
| `TestExecution` | One normalized run |
| `TestCaseResult` | One normalized test result within a run |
| `FlakinessScore` | Rolling flakiness score per `(testId, projectId)` |
| `FailureAnalysis` | AI analysis record for a failed test case result |
| `IntegrationConfig` | Per-team tracker configuration |
| `IssueTrackerLink` | Mapping from test to issue-tracker ticket |
| `AlertHistory` | Persisted alert firing history |
| `ApiKey` | Hashed ingestion/API keys |
| `AuditEvent` | Immutable audit trail for sensitive operations |
| `TestCoverageMapping` | Test-to-class mapping for TIA |
| `PlatformSetting` | Runtime-configurable settings, mainly for AI |
| `PerformanceMetric` | Aggregated performance metrics for one execution |
| `TiaEvent` | Historical record of one TIA query and its outcome |

### 4.2 Persistence Behavior

`ExecutionPersistenceService` is the central write orchestrator for run ingestion:

- deduplicates on `runId`
- auto-registers unknown teams and projects by slug
- persists `TestExecution`
- persists all `TestCaseResult` rows
- persists one `PerformanceMetric` when `TestType == PERFORMANCE`

### 4.3 Auto-Registration

The platform supports first-write creation of:

- `Team` from incoming `teamId` slug
- `Project` from incoming `projectId` slug under that team

This allows ingestion to work without a separate provisioning step.

## 5. `platform-ingestion`

### 5.1 Responsibility

`platform-ingestion` is the entrypoint for results and coverage data. It owns:

- multipart result upload
- parser selection and normalization
- persistence of normalized runs
- publication to Kafka
- coverage mapping ingestion
- API key management and request authentication
- query endpoints for teams, projects, and executions

### 5.2 Public APIs

#### Result ingestion

- `POST /api/v1/results/ingest`

Accepted inputs:

- `teamId`
- `projectId`
- `format`
- optional `branch`, `environment`, `commitSha`, `ciRunUrl`
- multipart `files`

Response:

- `202 Accepted`
- returns a generated platform `runId`

#### Coverage ingestion

- `POST /api/v1/coverage`

Accepts `CoverageManifest` for standalone TIA mappings.

#### Query APIs

- `GET /api/v1/teams`
- `GET /api/v1/teams/{slug}`
- `GET /api/v1/projects`
- `GET /api/v1/projects/{id}`
- `GET /api/v1/projects/by-slug/{slug}`
- `GET /api/v1/projects/{projectId}/executions`
- `GET /api/v1/executions/{runId}`

#### API key APIs

- `POST /api/v1/api-keys`
- `GET /api/v1/api-keys?teamId=...`
- `DELETE /api/v1/api-keys/{id}`

### 5.3 Parser Component Set

The ingestion service currently implements the following parsers:

| Parser | Source format | Notes |
|---|---|---|
| `JUnitXmlParser` | `JUNIT_XML` | Parses Surefire-style XML |
| `CucumberJsonParser` | `CUCUMBER_JSON` | Parses features, scenarios, and step outcomes |
| `TestNGParser` | `TESTNG` | Parses `testng-results.xml` |
| `AllureParser` | `ALLURE` | Parses per-test `*-result.json` files |
| `PlaywrightParser` | `PLAYWRIGHT` | Parses Playwright JSON report and honors `coveredModules` |
| `NewmanParser` | `NEWMAN` | Maps Postman assertions to test cases |
| `K6JsonParser` | `K6` | Produces performance metrics from k6 summary JSON |
| `GatlingStatsJsonParser` | `GATLING` | Parses `stats.json` request summaries |
| `JMeterJtlParser` | `JMETER` | Aggregates JTL sample data by label |
| `NativeResultParser` | `PLATFORM_NATIVE` | Accepts already-normalized JSON from the testkit |

### 5.4 Coverage Component

`CoverageIngestionService` supports two paths:

- automatic ingestion from `TestCaseResultDto.coveredClasses`
- standalone manifest ingestion through `POST /api/v1/coverage`

Mappings are upserted into `test_coverage_mappings`.

### 5.5 Security and Audit

Security is API-key based and stateless:

- `ApiKeyAuthFilter` validates `X-API-Key`
- only hashed keys are stored
- usage timestamps are updated on successful requests
- API key auth can be disabled via config for local development

Audit behavior:

- `@AuditLog` plus `AuditAspect` creates `AuditEvent` rows
- ingestion and API key lifecycle events are auditable

### 5.6 Error Handling

`GlobalExceptionHandler` turns:

- parse failures into `400`
- validation issues into `400`
- unexpected failures into `500`

## 6. `platform-analytics`

### 6.1 Responsibility

`platform-analytics` is the quality computation service. It owns:

- flakiness scoring
- trend and duration analysis
- quality gates
- release reports
- alert rule evaluation and alert history
- Test Impact Analysis
- TIA historical trends
- OpenSearch-backed log search

### 6.2 Event Consumer

`ResultAnalysisConsumer` listens to `test.results.raw` and, per run:

1. resolves project UUID from team/project slugs
2. computes flakiness scores for all unique test IDs in the run
3. evaluates a quality gate
4. evaluates alert rules

### 6.3 Flakiness Scoring

`FlakinessScoringService` computes a score over a 30-day lookback using:

`score = failure_rate * 0.5 + recency_weight * 0.3 + env_variance * 0.2`

Current classifications:

- `< 0.10`: `STABLE`
- `0.10–0.30`: `WATCH`
- `0.30–0.60`: `FLAKY`
- `>= 0.60`: `CRITICAL_FLAKY`

The result is upserted into `flakiness_scores`.

### 6.4 Trend Analysis

`TrendAnalysisService` provides:

- daily pass-rate points
- mean time to recovery
- duration p50/p95/max

### 6.5 Quality Gates

`QualityGateEvaluator` currently enforces:

- minimum pass rate
- maximum new failure count
- pass-rate drop versus 7-day rolling average

APIs:

- `GET /api/v1/analytics/{projectId}/quality-gate`
- `GET /api/v1/projects/{projectId}/quality-gate/ci`

The CI endpoint returns:

- `200` when the gate passes
- `422` when the gate fails
- `404` when no runs exist

### 6.6 Alerts

Alerting components:

- `AlertRuleEngine`
- `AlertNotificationService`
- `AlertHistoryService`

Current supported metrics:

- pass rate below threshold
- new failures above threshold
- broken tests above threshold
- critical flaky count above threshold

Current supported notification channels:

- Slack incoming webhook
- generic webhook

APIs:

- `GET /api/v1/alerts/projects/{projectId}`
- `GET /api/v1/alerts/org`

### 6.7 Release Reports

`ReleaseReportService` builds a release-window quality snapshot including:

- total runs/tests/passed/failed/broken/skipped
- overall pass rate
- flaky and critical-flaky counts
- top failing tests
- quality gate result for the latest run in the window

API:

- `GET /api/v1/projects/{projectId}/release-report`

### 6.8 Test Impact Analysis

TIA components:

- `TestImpactService`
- `TestImpactController`
- `TiaTrendsService`
- `TiaTrendsController`

Input:

- changed file paths or changed class names

Algorithm:

1. normalize file paths to class/module names
2. resolve exact coverage mappings
3. resolve wildcard mappings
4. return the union of recommended test IDs
5. compute risk from coverage completeness
6. persist a `TiaEvent`

Outputs include ready-to-use:

- JUnit/Maven `-Dtest=` filters
- Gradle `--tests` filter
- estimated reduction
- uncovered changed classes
- risk level

### 6.9 Log Search

`LogSearchService` queries OpenSearch indices named `test_execution_logs-*`.

It supports:

- logs by `run_id`
- logs by `test_id`
- recent run IDs by team and project

APIs:

- `GET /api/v1/logs/runs/{runId}`
- `GET /api/v1/logs/tests/{testId}`
- `GET /api/v1/logs/runs?teamId=...&projectId=...`

At startup the service ensures the OpenSearch index template exists.

## 7. `platform-ai`

### 7.1 Responsibility

`platform-ai` provides AI-based failure classification and runtime-configurable provider settings.

It owns:

- querying analysis history
- on-demand per-result analysis
- on-demand batch analysis
- scheduled nightly batch analysis
- provider routing between Claude and OpenAI
- persistence of `FailureAnalysis`
- runtime settings via `PlatformSetting`

### 7.2 Public APIs

- `GET /api/v1/projects/{projectId}/analyses`
- `GET /api/v1/projects/{projectId}/tests/{testId}/analysis`
- `GET /api/v1/projects/{projectId}/tests/{testId}/analyses`
- `POST /api/v1/projects/{projectId}/results/{resultId}/analyse`
- `POST /api/v1/analyse/run-now`
- `GET /api/v1/ai/settings`
- `PUT /api/v1/ai/settings`
- `POST /api/v1/ai/settings/test`

### 7.3 Classification Pipeline

`FailureClassificationService`:

- guards against duplicate successful analysis for the same `test_case_result_id`
- fetches recent failure history
- builds a token-conscious prompt using `PromptBuilder`
- calls the active AI provider through `AiClient`
- persists a `FailureAnalysis`
- stores token usage and provider metadata
- records `ERROR` analyses when provider calls fail

### 7.4 Provider Abstraction

Implemented provider components:

- `ClaudeApiClient`
- `OpenAiClient`
- `AiClientRouter`

Current runtime behavior:

- default path routes to Claude unless the active provider setting is `openai`
- API keys can be sourced from environment variables or `platform_settings`
- model selection is runtime-configurable

### 7.5 Background Execution Modes

Implemented modes:

- real-time Kafka-triggered analysis through `AnalysisEventConsumer`
- nightly scheduled analysis through `NightlyAnalysisBatchJob`
- manual on-demand batch trigger through REST

### 7.6 Classification Output

Each `FailureAnalysis` stores:

- category
- confidence
- root cause
- detailed analysis
- suggested fix
- flaky candidate flag
- affected component
- model version
- token counts
- analysis status

## 8. `platform-integration`

### 8.1 Responsibility

`platform-integration` automates issue lifecycle for failing and flaky tests based on persisted history and flakiness scores.

### 8.2 Actual Tracker Support

The design mentions multiple trackers, but the current implementation ships only:

- JIRA integration

Linear and GitHub Issues are not implemented yet in this codebase.

### 8.3 Event Consumer

`IntegrationEventConsumer` listens to `test.results.raw`, resolves the project/team, loads enabled integration configs, creates tracker clients, and delegates to `TicketLifecycleManager`.

### 8.4 Decision Engine

`IssueDecisionEngine` is a pure decision component. It currently supports these actions:

- `CREATE`
- `UPDATE`
- `CLOSE`
- `REOPEN`
- `SKIP`

Decision inputs include:

- recent run history
- flakiness score
- existing ticket link state
- configurable thresholds for consecutive failures, consecutive passes, and flakiness

### 8.5 Lifecycle Manager

`TicketLifecycleManager`:

- selects test IDs to process from current failures plus previously linked tests
- loads history and flakiness state
- calls the decision engine
- formats issue titles, descriptions, and comments
- calls tracker methods
- persists or updates `IssueTrackerLink`

### 8.6 Duplicate Prevention

`DuplicateDetector` prevents ticket spam by using `issue_tracker_links` as the authoritative mapping layer.

### 8.7 JIRA Adapter Components

Implemented JIRA stack:

- `JiraTrackerFactory`
- `JiraIssueTracker`
- `JiraClient`
- `JiraIssueMapper`

Capabilities:

- create issue
- add comments
- transition to done
- reopen
- search for open issue by test-derived label

## 9. `platform-portal`

### 9.1 Responsibility

`platform-portal` is the user-facing application. It is split into:

- Spring Boot BFF
- embedded React SPA built by Vite during Maven packaging

### 9.2 BFF Controllers

Implemented BFF surface:

- `PortalOverviewController`
- `PortalProjectController`
- `PortalExecutionController`
- `PortalAiController`
- `PortalAlertController`
- `PortalApiKeyController`

The BFF aggregates and proxies data from:

- ingestion service
- analytics service
- AI service

### 9.3 Frontend Routes

React routes currently implemented:

- `/` - org overview
- `/projects/:projectId` - project detail
- `/runs/:runId` - run detail
- `/alerts` - alert history
- `/settings/api-keys` - API key management
- `/settings/ai` - AI settings

### 9.4 Current UI Feature Set

The SPA currently includes pages for:

- organization summary and recent alerts
- project quality detail
- run/test-case detail
- alert list
- API key creation and revocation
- AI provider configuration and test connectivity

### 9.5 Delivery Model

The frontend is built during Maven package and emitted into Spring Boot static resources. `WebConfig` also configures SPA route fallback so deep links resolve to `index.html`.

## 10. `platform-testkit`

### 10.1 Responsibility

`platform-testkit` is the rich native Java instrumentation layer for test suites that opt in. It goes beyond file parsing and publishes structured execution context directly.

### 10.2 Framework Integrations

Implemented integration points:

- JUnit 5 via `PlatformExtension` and `PlatformBaseTest`
- TestNG via `PlatformTestNGListener`
- Cucumber via `PlatformCucumberPlugin`
- custom/manual runners via `PlatformTestSession`
- Playwright-assisted JUnit integration via `PlatformPlaywrightExtension`

### 10.3 Rich Execution Features

Implemented features include:

- per-test `TestContext`
- run-level context via `RunContext`
- structured step tracking
- dual logging to SLF4J and result payload
- OpenTelemetry trace IDs
- automatic retry support through `RetryExtension` and `@Retryable`
- soft assertions
- environment capture
- `@AffectedBy` coverage capture
- rule-based local failure hints
- browser diagnostics capture for selector-related failures
- native JSON publishing through `NativeReportPublisher`

### 10.4 Main Annotations

Implemented annotations:

- `@Step`
- `@TestMetadata`
- `@AffectedBy`
- `@Retryable`
- `@TestStep`

### 10.5 Diagnostics Path

For bad locator failures, the testkit can capture:

- failed selector
- DOM snapshot
- optional screenshot bytes through a registered diagnostics provider

The actual AI analysis remains server-side in `platform-ai`.

## 11. `platform-adapters`

### 11.1 Responsibility

`platform-adapters` is the low-friction publishing layer for teams that do not want to adopt the full testkit.

### 11.2 Java Adapter

Implemented Java components:

- `PlatformConfig`
- `PlatformReporter`
- `PlatformReportingExtension` for JUnit 5
- `PlatformTestNGListener` for TestNG
- `PlatformCucumberPlugin` for Cucumber
- `@PlatformProject` for per-class project overrides

Behavior:

- reads existing report files
- builds multipart requests to `platform-ingestion`
- never fails the test run on publication errors

### 11.3 Playwright JS Adapter

Package path:

- `platform-adapters/js/playwright`

Implemented behavior:

- custom Playwright reporter
- converts suite tree into server parser-compatible JSON
- auto-detects branch, commit SHA, and CI run URL
- optionally attaches `coveredModules`
- posts report to `/api/v1/results/ingest` with format `PLAYWRIGHT`

### 11.4 k6 JS Adapter

Package path:

- `platform-adapters/js/k6`

Implemented behavior:

- exports a drop-in `handleSummary`
- or provides `publishToPlatform(data)`
- posts K6 summary JSON to ingestion with format `K6`
- auto-detects branch, commit SHA, and CI URL from common CI providers

## 12. CI Templates

The repository includes CI helpers for Test Impact Analysis:

- GitHub Actions template
- Azure DevOps template
- Jenkins shared-library step

These templates:

- calculate changed files using git diff
- call analytics TIA endpoints
- expose Maven/Gradle filter strings
- allow fallback to full-suite execution based on risk

## 13. Infrastructure and Operations

### 13.1 Local Runtime

`docker-compose.yml` provisions:

- PostgreSQL
- Kafka in KRaft mode
- Redis
- OpenSearch
- Logstash
- Loki
- Promtail
- Grafana
- Prometheus
- InfluxDB
- a Flyway migration job
- all 5 platform services

### 13.2 Observability Assets

Implemented assets include:

- Prometheus scrape config
- Grafana datasources and dashboards
- Loki and Promtail config
- Logstash pipeline for test logs into OpenSearch
- OpenSearch index template for test execution logs

### 13.3 Kubernetes Packaging

The repo includes a Helm chart under `helm/platform` for service deployment configuration.

## 14. Implemented vs Planned Boundary

This repository contains several roadmap references that go beyond the currently implemented code. The current implemented boundary is:

- portal exists and is functional
- analytics, AI, TIA, alerts, and release reporting are implemented
- adapters exist for Java, Playwright, and k6
- issue-tracker integration is currently JIRA-only
- API key auth exists for ingestion service
- AI provider switching exists between Claude and OpenAI

Notable items referenced in plans/docs but not implemented as first-class components here include:

- Linear integration
- GitHub Issues integration
- dedicated API gateway service
- enterprise auth/RBAC/SSO
- quarantining workflow
- deeper portal management features beyond the implemented screens

## 15. Summary

The implemented platform is a working, event-driven test quality system built around a canonical normalized result model. Its strongest completed areas are:

- broad multi-format ingestion
- shared persistence model
- flakiness and TIA analytics
- runtime-configurable AI classification
- automated JIRA lifecycle management
- a BFF-backed portal
- both low-friction adapters and high-fidelity native test instrumentation

The system is already organized as a platform, not a single application: ingestion is the write edge, Kafka is the event backbone, analytics and AI enrich the signal, integration externalizes action, and the portal exposes the result.
