# Test Automation Platform

## Per-Module API Reference

This reference is extracted from the controller classes currently present in the repository. It is grouped by module and reflects the HTTP API surface that is actually implemented in code today.

## Conventions

- Paths are shown exactly as exposed by the controllers.
- Query parameters and request bodies are listed only when they are visible from controller signatures.
- Response schemas are summarized at a high level unless the controller uses a named DTO directly.
- `platform-integration` is included as a module note even though it currently exposes no HTTP controllers.

## `platform-ingestion`

### `ResultIngestionController`

Base path: `/api/v1/results`

#### `POST /api/v1/results/ingest`

Purpose:

- ingest raw test report files and normalize them into the platform

Consumes:

- `multipart/form-data`

Form fields:

- `teamId` - required
- `projectId` - required
- `format` - required, mapped to `SourceFormat`
- `branch` - optional
- `environment` - optional, defaults to `unknown`
- `commitSha` - optional
- `ciRunUrl` - optional
- `files` - required multipart file list

Response:

- `202 Accepted`
- body type: `IngestResponse`
- includes generated `runId`, ingestion status, accepted test count, and execution detail link

Notes:

- controller-level description explicitly covers JUnit XML, Cucumber JSON, TestNG, Allure, Playwright, and Newman style ingestion
- actual parser support is broader and includes `PLATFORM_NATIVE`, `K6`, `GATLING`, and `JMETER`

### `CoverageIngestionController`

Base path: `/api/v1/coverage`

#### `POST /api/v1/coverage`

Purpose:

- submit standalone test-to-class coverage mappings for Test Impact Analysis

Consumes:

- JSON body: `CoverageManifest`

Request body fields:

- `projectId`
- `mappings[]`
- each mapping contains:
  - `testId`
  - `coveredClasses[]`

Response:

- `202 Accepted`
- JSON map containing:
  - `projectId`
  - `mappingsUpserted`
  - `status`

### `TeamQueryController`

Base path: `/api/v1/teams`

#### `GET /api/v1/teams`

Purpose:

- list all teams

Response:

- `200 OK`
- body type: `List<TeamDto>`

#### `GET /api/v1/teams/{slug}`

Purpose:

- fetch a team by slug

Path params:

- `slug`

Response:

- `200 OK` with `TeamDto`
- `404 Not Found` when absent

### `ProjectQueryController`

Base path: `/api/v1/projects`

#### `GET /api/v1/projects`

Purpose:

- list projects

Query params:

- `teamSlug` - optional filter

Response:

- `200 OK`
- body type: `List<ProjectDto>`

#### `GET /api/v1/projects/{id}`

Purpose:

- fetch a project by UUID

Path params:

- `id`

Response:

- `200 OK` with `ProjectDto`
- `404 Not Found`

#### `GET /api/v1/projects/by-slug/{slug}`

Purpose:

- fetch a project by slug

Path params:

- `slug`

Response:

- `200 OK` with `ProjectDto`
- `404 Not Found`

### `ExecutionQueryController`

No class-level base path. Paths are declared directly on methods.

#### `GET /api/v1/projects/{projectId}/executions`

Purpose:

- list recent executions for a project

Path params:

- `projectId` - UUID

Query params:

- `limit` - optional, default `20`, capped in service

Response:

- `200 OK`
- body type: `List<ExecutionSummaryDto>`

#### `GET /api/v1/executions/{runId}`

Purpose:

- fetch full execution detail by run ID

Path params:

- `runId`

Response:

- `200 OK` with `ExecutionDetailDto`
- `404 Not Found`

### `ApiKeyController`

Base path: `/api/v1/api-keys`

#### `POST /api/v1/api-keys`

Purpose:

- create a new API key for a team

Consumes:

- JSON body: `CreateApiKeyRequest`

Request body fields:

- `name`
- `teamId`
- `ttlDays`

Response:

- `201 Created`
- body type: `ApiKeyCreationResult`

Notes:

- the raw key is returned only at creation time

#### `GET /api/v1/api-keys`

Purpose:

- list active API keys for a team

Query params:

- `teamId` - required UUID

Response:

- `200 OK`
- body type: `List<ApiKeySummary>`

#### `DELETE /api/v1/api-keys/{id}`

Purpose:

- revoke an API key

Path params:

- `id` - key UUID

Response:

- `204 No Content`

## `platform-analytics`

### `AnalyticsController`

Base path: `/api/v1/analytics`

#### `GET /api/v1/analytics/{projectId}/flakiness`

Purpose:

- list top flaky tests for a project

Path params:

- `projectId` - UUID

Query params:

- `limit` - optional, default `20`
- `classification` - optional flakiness classification filter

Response:

- `200 OK`
- body type: `List<FlakinessDto>`

#### `GET /api/v1/analytics/{projectId}/trends/pass-rate`

Purpose:

- daily pass-rate trend for a project

Path params:

- `projectId`

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`
- body type: `List<PassRatePoint>`

#### `GET /api/v1/analytics/{projectId}/trends/mttr`

Purpose:

- mean time to recovery for a project

Path params:

- `projectId`

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`
- map containing:
  - `projectId`
  - `lookbackDays`
  - `mttrMinutes`

#### `GET /api/v1/analytics/{projectId}/trends/duration`

Purpose:

- execution duration stats

Path params:

- `projectId`

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`
- body type: `TrendAnalysisService.DurationStats`

#### `GET /api/v1/analytics/{projectId}/quality-gate`

Purpose:

- evaluate the quality gate against the latest run for a project

Path params:

- `projectId`

Response:

- `200 OK` with `QualityGateResult`
- `404 Not Found` when the project has no runs

#### `GET /api/v1/analytics/org/summary`

Purpose:

- organization-wide summary across projects

Query params:

- `teamSlug` - optional
- `days` - optional, default `7`

Response:

- `200 OK`
- body type: `OrgSummaryDto`

### `AlertController`

No class-level base path. Paths are declared directly on methods.

#### `GET /api/v1/alerts/projects/{projectId}`

Purpose:

- list recent alerts for a project

Path params:

- `projectId`

Query params:

- `days` - optional, default `7`

Response:

- `200 OK`
- body type: `List<AlertHistory>`

#### `GET /api/v1/alerts/org`

Purpose:

- list recent organization-wide alerts

Query params:

- `days` - optional, default `7`

Response:

- `200 OK`
- body type: `List<AlertHistory>`

#### `GET /api/v1/projects/{projectId}/quality-gate/ci`

Purpose:

- CI-oriented quality gate endpoint

Path params:

- `projectId`

Response:

- `200 OK` when gate passes
- `422 Unprocessable Entity` when gate fails
- `404 Not Found` when no runs exist

Response body:

- map containing:
  - `projectId`
  - `runId`
  - `passed`
  - `passRate`
  - `newFailures`
  - `violations`

### `ReleaseReportController`

Base path: `/api/v1/projects/{projectId}/release-report`

#### `GET /api/v1/projects/{projectId}/release-report`

Purpose:

- generate a release-window quality report

Path params:

- `projectId`

Query params:

- `tag` - optional release label
- `days` - optional, default `14`
- `from` - optional ISO date-time
- `to` - optional ISO date-time

Response:

- `200 OK` with `ReleaseReportDto`
- `404 Not Found` when the project does not exist

### `TestImpactController`

Base path: `/api/v1/analytics`

#### `GET /api/v1/analytics/{projectId}/impact`

Purpose:

- analyze impacted tests from changed files or changed classes

Path params:

- `projectId`

Query params:

- `changedFiles` - optional, repeated or comma-friendly caller-side expansion
- `changedClasses` - optional
- `branch` - optional
- `triggeredBy` - optional, default `api`

Response:

- `200 OK`
- body type: `TestImpactResult`

#### `GET /api/v1/analytics/{projectId}/impact/summary`

Purpose:

- summarize coverage breadth and whether TIA is effectively enabled

Path params:

- `projectId`

Response:

- `200 OK`
- map containing:
  - `projectId`
  - `mappedTests`
  - `mappedClasses`
  - `tiaEnabled`

### `TiaTrendsController`

Base path: `/api/v1/analytics`

#### `GET /api/v1/analytics/tia/summary`

Purpose:

- org-level TIA summary

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`
- body type: `TiaTrendsService.TiaSummary`

#### `GET /api/v1/analytics/tia/coverage-breadth`

Purpose:

- coverage breadth by project

Response:

- `200 OK`
- body type: `List<ProjectCoverageBreadth>`

#### `GET /api/v1/analytics/{projectId}/tia/risk-distribution`

Purpose:

- risk distribution for TIA events in a project

Path params:

- `projectId`

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`
- body type: `Map<String, Long>`

#### `GET /api/v1/analytics/{projectId}/tia/reduction-trend`

Purpose:

- daily reduction trend for TIA

Path params:

- `projectId`

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`
- body type: `List<DailyReductionPoint>`

#### `GET /api/v1/analytics/{projectId}/tia/coverage-breadth-trend`

Purpose:

- daily coverage breadth trend

Path params:

- `projectId`

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`
- body type: `List<DailyCoverageBreadthPoint>`

#### `GET /api/v1/analytics/{projectId}/tia/events`

Purpose:

- list recent TIA events

Path params:

- `projectId`

Query params:

- `limit` - optional, default `50`

Response:

- `200 OK`
- body type: `List<TiaEventDto>`

### `LogSearchController`

Base path: `/api/v1/logs`

#### `GET /api/v1/logs/runs/{runId}`

Purpose:

- fetch logs for an entire run

Path params:

- `runId`

Query params:

- `size` - optional
- `level` - optional log-level filter

Response:

- `200 OK`
- body type: `LogSearchResponse`

#### `GET /api/v1/logs/tests/{testId}`

Purpose:

- fetch logs for a specific test case

Path params:

- `testId`

Query params:

- `size` - optional
- `level` - optional log-level filter

Response:

- `200 OK`
- body type: `LogSearchResponse`

#### `GET /api/v1/logs/runs`

Purpose:

- list recent run IDs for a team/project pair

Query params:

- `teamId` - required
- `projectId` - required
- `days` - optional, default `7`

Response:

- `200 OK`
- body type: `List<String>`

## `platform-ai`

### `AiAnalysisController`

Base path: `/api/v1`

#### `GET /api/v1/projects/{projectId}/analyses`

Purpose:

- list recent failure analyses for a project

Path params:

- `projectId`

Query params:

- `category` - optional
- `days` - optional, default `7`

Response:

- `200 OK`
- body type: `List<FailureAnalysisDto>`

#### `GET /api/v1/projects/{projectId}/tests/{testId}/analysis`

Purpose:

- fetch the latest analysis for a test

Path params:

- `projectId`
- `testId`

Response:

- `200 OK` with `FailureAnalysisDto`
- `404 Not Found`

#### `GET /api/v1/projects/{projectId}/tests/{testId}/analyses`

Purpose:

- fetch analysis history for a test

Path params:

- `projectId`
- `testId`

Query params:

- `limit` - optional, default `10`

Response:

- `200 OK`
- body type: `List<FailureAnalysisDto>`

#### `POST /api/v1/projects/{projectId}/results/{resultId}/analyse`

Purpose:

- trigger on-demand analysis for one failed or broken result

Path params:

- `projectId`
- `resultId`

Response:

- `200 OK` with `FailureAnalysisDto`
- `400 Bad Request` if result exists but is not failed/broken
- `404 Not Found` if result does not exist

#### `POST /api/v1/analyse/run-now`

Purpose:

- queue on-demand analysis of unanalysed failures

Query params:

- `hours` - optional, default `24`

Response:

- `202 Accepted`
- map containing:
  - `queued`
  - `hours`

### `AiSettingsController`

Base path: `/api/v1/ai/settings`

#### `GET /api/v1/ai/settings`

Purpose:

- fetch current AI settings

Response:

- `200 OK`
- map containing:
  - `enabled`
  - `realtimeEnabled`
  - `provider`
  - `model`
  - `apiKeySet`

#### `PUT /api/v1/ai/settings`

Purpose:

- update AI settings

Consumes:

- JSON body: `AiSettingsUpdate`

Request body fields:

- `enabled`
- `realtimeEnabled`
- `provider`
- `model`
- `apiKey`

Response:

- `200 OK`
- returns the current effective settings map

#### `POST /api/v1/ai/settings/test`

Purpose:

- test AI provider connectivity

Consumes:

- JSON body: `TestConnectionRequest`

Request body fields:

- `provider`
- `model`
- `apiKey`

Response:

- `200 OK`
- body type: `TestConnectionResult`

## `platform-integration`

### HTTP API surface

Current status:

- no controller classes are implemented in `platform-integration`
- the module is event-driven only and reacts to Kafka `test.results.raw`
- operational behavior is currently exposed indirectly through persistence and downstream tracker side effects rather than an HTTP API

## `platform-portal`

This module exposes a BFF API for the React SPA. The endpoints mainly proxy and aggregate data from ingestion, analytics, and AI services.

### `PortalOverviewController`

Base path: `/api/portal`

#### `GET /api/portal/overview`

Purpose:

- fetch org summary plus recent alerts for portal overview

Query params:

- `days` - optional, default `7`

Response:

- `200 OK`
- map containing:
  - `summary`
  - `recentAlerts`

#### `GET /api/portal/teams`

Purpose:

- proxy team listing from ingestion

Response:

- `200 OK`
- proxied body from ingestion teams endpoint

#### `GET /api/portal/projects`

Purpose:

- proxy project listing from ingestion

Query params:

- `teamSlug` - optional

Response:

- `200 OK`
- proxied body from ingestion projects endpoint

### `PortalProjectController`

Base path: `/api/portal/projects`

#### `GET /api/portal/projects/{projectId}`

Purpose:

- aggregate project detail page data

Path params:

- `projectId`

Query params:

- `days` - optional, default `7`

Response:

- `200 OK`
- map containing:
  - `project`
  - `flakiness`
  - `qualityGate`
  - `passRateTrend`
  - `recentExecutions`

#### `GET /api/portal/projects/{projectId}/executions`

Purpose:

- proxy recent executions for a project

Path params:

- `projectId`

Query params:

- `limit` - optional, default `20`

Response:

- `200 OK`

#### `GET /api/portal/projects/{projectId}/trends/pass-rate`

Purpose:

- proxy pass-rate trend

Path params:

- `projectId`

Query params:

- `days` - optional, default `30`

Response:

- `200 OK`

#### `GET /api/portal/projects/{projectId}/flakiness`

Purpose:

- proxy flakiness list

Path params:

- `projectId`

Query params:

- `limit` - optional, default `20`
- `classification` - optional

Response:

- `200 OK`

#### `GET /api/portal/projects/{projectId}/quality-gate`

Purpose:

- proxy quality-gate result

Path params:

- `projectId`

Response:

- `200 OK`

#### `GET /api/portal/projects/{projectId}/analyses`

Purpose:

- proxy AI analyses for a project

Path params:

- `projectId`

Query params:

- `days` - optional, default `7`

Response:

- `200 OK`

#### `GET /api/portal/projects/{projectId}/impact`

Purpose:

- proxy Test Impact Analysis

Path params:

- `projectId`

Query params:

- `changedFiles` - optional repeated values
- `changedClasses` - optional repeated values

Response:

- `200 OK`

#### `GET /api/portal/projects/{projectId}/impact/summary`

Purpose:

- proxy TIA summary

Path params:

- `projectId`

Response:

- `200 OK`

#### `GET /api/portal/projects/{projectId}/release-report`

Purpose:

- proxy release report

Path params:

- `projectId`

Query params:

- `days` - optional, default `14`
- `tag` - optional

Response:

- `200 OK`

### `PortalExecutionController`

Base path: `/api/portal/executions`

#### `GET /api/portal/executions/{runId}`

Purpose:

- proxy full run detail

Path params:

- `runId`

Response:

- `200 OK`

### `PortalAlertController`

Base path: `/api/portal/alerts`

#### `GET /api/portal/alerts`

Purpose:

- proxy org-wide alerts

Query params:

- `days` - optional, default `7`

Response:

- `200 OK`

#### `GET /api/portal/alerts/projects/{projectId}`

Purpose:

- proxy project-specific alerts

Path params:

- `projectId`

Query params:

- `days` - optional, default `7`

Response:

- `200 OK`

### `PortalAiController`

Base path: `/api/portal/ai`

#### `GET /api/portal/ai/settings`

Purpose:

- proxy AI settings

Response:

- `200 OK`

#### `PUT /api/portal/ai/settings`

Purpose:

- proxy AI settings update

Consumes:

- generic JSON body forwarded to AI service

Response:

- `200 OK`

#### `POST /api/portal/ai/settings/test`

Purpose:

- proxy AI connectivity test

Consumes:

- generic JSON body forwarded to AI service

Response:

- `200 OK`

#### `POST /api/portal/ai/projects/{projectId}/results/{resultId}/analyse`

Purpose:

- proxy on-demand AI analysis for a result

Path params:

- `projectId`
- `resultId`

Response:

- `200 OK`

#### `POST /api/portal/ai/analyse/run-now`

Purpose:

- proxy on-demand batch AI analysis trigger

Query params:

- `hours` - optional, default `24`

Response:

- `200 OK`

### `PortalApiKeyController`

Base path: `/api/portal/api-keys`

#### `GET /api/portal/api-keys`

Purpose:

- proxy API key listing for a team

Query params:

- `teamId` - required

Response:

- `200 OK`

#### `POST /api/portal/api-keys`

Purpose:

- proxy API key creation

Consumes:

- generic JSON body forwarded to ingestion

Response:

- `200 OK`

Notes:

- underlying ingestion endpoint returns `201 Created`, but the BFF controller returns the proxied body without re-declaring status handling

#### `DELETE /api/portal/api-keys/{id}`

Purpose:

- proxy API key revocation

Path params:

- `id`

Response:

- `204 No Content`

## Controller Inventory Summary

| Module | Controllers |
|---|---|
| `platform-ingestion` | `ResultIngestionController`, `CoverageIngestionController`, `TeamQueryController`, `ProjectQueryController`, `ExecutionQueryController`, `ApiKeyController` |
| `platform-analytics` | `AnalyticsController`, `AlertController`, `ReleaseReportController`, `TestImpactController`, `TiaTrendsController`, `LogSearchController` |
| `platform-ai` | `AiAnalysisController`, `AiSettingsController` |
| `platform-integration` | none |
| `platform-portal` | `PortalOverviewController`, `PortalProjectController`, `PortalExecutionController`, `PortalAlertController`, `PortalAiController`, `PortalApiKeyController` |
