# Test Automation Platform — Comprehensive Implementation Plan

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Goals & Success Metrics](#3-goals--success-metrics)
4. [System Architecture](#4-system-architecture)
5. [Technology Stack](#5-technology-stack)
6. [Module Design](#6-module-design)
7. [Data Model](#7-data-model)
8. [API Design](#8-api-design)
9. [Integration Design](#9-integration-design)
10. [Observability Design](#10-observability-design)
11. [AI Failure Intelligence](#11-ai-failure-intelligence)
12. [Security & Access Control](#12-security--access-control)
13. [Infrastructure & Deployment](#13-infrastructure--deployment)
14. [Implementation Phases](#14-implementation-phases)
15. [Repository Structure](#15-repository-structure)
16. [Non-Functional Requirements](#16-non-functional-requirements)
17. [Risk & Trade-offs](#17-risk--trade-offs)
18. [Team Onboarding Guide](#18-team-onboarding-guide)

---

## 1. Executive Summary

The Test Automation Platform is shared organizational infrastructure that unifies test execution results from multiple teams using heterogeneous frameworks (JUnit 5, TestNG, Cucumber, Playwright, REST-assured, Appium) into a single quality intelligence system. It provides cross-team dashboards, automated flakiness detection, AI-powered failure analysis, and bidirectional JIRA integration — giving the Test Automation Architect and team leads a complete, real-time view of quality across the delivery pipeline.

---

## 2. Problem Statement

### Current State

| Pain Point | Impact |
|---|---|
| Each team reports in their own format (Allure, ExtentReport, Cucumber HTML, TestNG XML) | No organization-wide quality view |
| No shared test result schema | Cannot compare pass rates, trends, or flakiness across teams |
| Flakiness is invisible until it blocks CI | Technical test debt accumulates unnoticed |
| Failures are analyzed manually per team | Slow triage, repeated root causes never surfaced org-wide |
| No link between test failures and JIRA work items | Failures fall through the cracks, no accountability |
| Quality trend across releases is unknown | Regressions discovered late, after customer impact |

### Target State

A platform acts as the central nervous system for test quality:
- Any team, any framework publishes results to one place
- Architect sees org-wide quality health in real time
- Flaky tests are auto-detected, scored, and ticketed
- AI classifies failures and suggests fixes
- JIRA tickets are created, updated, and closed automatically

---

## 3. Goals & Success Metrics

### Primary Goals

1. **Unified visibility** — single dashboard showing quality across all teams and frameworks
2. **Framework agnostic** — ingest results from JUnit 5, TestNG, Cucumber, Playwright, Postman, REST-assured, Appium without requiring teams to change their existing setup
3. **Flakiness intelligence** — automatically detect, score, and track flaky tests
4. **Actionable failures** — auto-create/update/close JIRA tickets linked to test results
5. **AI-assisted triage** — classify root causes and group similar failures across teams

### Success Metrics

| Metric | Baseline | Target (6 months) |
|---|---|---|
| Time for architect to get org quality view | Hours (manual) | < 1 minute (portal) |
| Mean time to ticket creation after failure | 1–3 days (manual) | < 5 minutes (automated) |
| Flaky test identification time | Weeks (ad hoc) | Same day (automated scoring) |
| Duplicate JIRA tickets per failure | 3–5 | 1 (dedup enforced) |
| Team onboarding time to platform | N/A | < 2 hours (SDK + docs) |
| Cross-team failure pattern detection | 0% | > 80% of repeated root causes grouped |

---

## 4. System Architecture

### 4.1 High-Level Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│                          TEAM LAYER (existing, unchanged)                  │
│                                                                             │
│  Team A (JUnit5+        Team B (Cucumber+      Team C (TestNG+             │
│  REST-assured)          Selenium)              Appium)                     │
│  Maven / Gradle         Maven                  Maven                       │
│                                                                             │
│  Team D (Playwright+    Team E (Postman /       Team N (any)               │
│  TypeScript)            Newman)                                             │
└──────┬───────────────────────┬────────────────────────┬────────────────────┘
       │                       │                        │
       │   HTTP multipart upload OR platform-sdk extension (JUnit/TestNG)
       │                       │                        │
       └───────────────────────┴────────────────────────┘
                               │
                               ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         INGESTION LAYER                                    │
│                                                                             │
│  platform-ingestion  (Spring Boot)                                         │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │  ResultIngestionController                                          │   │
│  │  POST /api/v1/results/ingest  (multipart: format + files)          │   │
│  │                                                                      │   │
│  │  Parsers                          Normalizer                        │   │
│  │  ├── JUnitXmlParser           →   UnifiedTestResult                 │   │
│  │  ├── CucumberJsonParser       →   UnifiedTestResult                 │   │
│  │  ├── TestNGParser             →   UnifiedTestResult                 │   │
│  │  ├── AllureParser             →   UnifiedTestResult                 │   │
│  │  ├── PlaywrightJsonParser     →   UnifiedTestResult                 │   │
│  │  └── NewmanJsonParser         →   UnifiedTestResult                 │   │
│  └─────────────────────────────────────┬──────────────────────────────┘   │
└────────────────────────────────────────┼──────────────────────────────────┘
                                         │ Kafka: test.results.raw
                                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                          CORE DATA LAYER                                   │
│                                                                             │
│  platform-core  (Spring Boot)                                              │
│  ┌────────────────────┐   ┌────────────────────┐   ┌──────────────────┐  │
│  │   PostgreSQL        │   │    OpenSearch       │   │     Redis        │  │
│  │   (structured       │   │    (full-text logs, │   │   (live state,   │  │
│  │    metadata,        │   │     failure search, │   │    session cache, │  │
│  │    test history,    │   │     aggregations)   │   │    rate limits)  │  │
│  │    flakiness)       │   │                     │   │                  │  │
│  └────────────────────┘   └────────────────────┘   └──────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
                                         │
                    ┌────────────────────┼─────────────────────┐
                    ▼                    ▼                      ▼
┌────────────────────────┐  ┌─────────────────────┐  ┌────────────────────┐
│  platform-analytics    │  │  platform-ai         │  │  platform-         │
│  (Spring Boot)         │  │  (Spring Boot)       │  │  integration       │
│                        │  │                      │  │  (Spring Boot)     │
│  - Flakiness scoring   │  │  - Failure classif.  │  │                    │
│  - Trend analysis      │  │  - Root cause AI     │  │  - JIRA client     │
│  - Quality metrics     │  │  - Fix suggestions   │  │  - Linear client   │
│  - Coverage mapping    │  │  - Similarity search │  │  - GitHub Issues   │
│  - Alert engine        │  │  - Batch nightly job │  │  - Slack notifs    │
└────────────────────────┘  └─────────────────────┘  └────────────────────┘
                    │                    │                      │
                    └────────────────────┴──────────────────────┘
                                         │
                                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         PORTAL LAYER                                       │
│                                                                             │
│  platform-portal  (React + Spring Boot BFF)                                │
│                                                                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │  Org Dashboard   │  │  Team Dashboard  │  │  Test Detail View        │ │
│  │  (Architect)     │  │  (Team Lead)     │  │  (Developer)             │ │
│  │                  │  │                  │  │                          │ │
│  │  - Quality matrix│  │  - Pass/fail     │  │  - Run history           │ │
│  │  - Cross-team    │  │    timeline      │  │  - AI root cause         │ │
│  │    flakiness     │  │  - Top 10 flaky  │  │  - JIRA ticket link      │ │
│  │  - Release gates │  │  - AI failure    │  │  - Similar failures      │ │
│  │  - SLA tracker   │  │    breakdown     │  │  - Fix suggestion        │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Event Flow

```
Test Run Completes
       │
       ▼
CI/CD calls POST /api/v1/results/ingest
       │
       ▼
Parser (format-specific) → ResultNormalizer → UnifiedTestResult
       │
       ▼
Persisted to PostgreSQL + OpenSearch
       │
       ├──► Kafka: test.results.raw
       │         │
       │         ├──► Analytics Consumer: update flakiness scores, trends
       │         ├──► AI Consumer: classify new failures
       │         └──► Integration Consumer: evaluate JIRA ticket actions
       │
       └──► WebSocket broadcast → Portal live update
```

### 4.3 JIRA Integration Flow

```
Analytics detects failure/flakiness
       │
       ▼
IssueDecisionEngine evaluates:
  - Category (bug / flaky / test-defect / infra)
  - Consecutive failure count
  - Flakiness threshold crossed
  - Existing open ticket?
       │
       ├── No existing ticket → Create new JIRA ticket
       ├── Existing open ticket → Add run comment + increment counter
       ├── Test back to green → Transition ticket to DONE
       └── Ticket DONE but failing again → Reopen + add regression comment
```

---

## 5. Technology Stack

### 5.1 Core Backend

| Component | Technology | Version | Rationale |
|---|---|---|---|
| Java | JDK | 21 (LTS) | Minimum: Java 17 (Spring Boot 4 requirement); Java 21 LTS recommended for virtual threads + modern GC |
| Application framework | Spring Boot | 4.0.3 | Based on Spring Framework 7 + Jakarta EE 11; requires Java 17+ |
| Reactive layer | Spring WebFlux | 7.x | Non-blocking ingestion under high load |
| ORM | Spring Data JPA + Hibernate | 7.x | Structured relational data |
| Migration | Flyway | 11.x | Version-controlled DB schema |
| Messaging | Apache Kafka | 4.2.0 | KRaft-only (ZooKeeper fully removed); durable event streaming |
| Kafka client | Spring Kafka | 4.0.x | KRaft-native; ZooKeeper support dropped |
| Caching | Redis + Spring Cache | 7.x | Session state, dedup locks, rate limiting |
| HTTP client | Spring WebClient | 6.x | Non-blocking calls to JIRA, Claude API |
| Scheduling | Spring Scheduler + Quartz | 2.x | Nightly batch jobs |
| Security | Spring Security + OAuth2 | 6.x | SSO, RBAC |
| API documentation | SpringDoc OpenAPI | 2.x | Auto-generated Swagger UI |

### 5.2 Data Storage

| Store | Technology | Version | Purpose |
|---|---|---|---|
| Primary DB | PostgreSQL | 17.x | Stable LTS; test metadata, execution history, flakiness scores, team config |
| Search & Analytics | OpenSearch | 3.5.0 | Full-text failure search, log aggregation, aggregation queries |
| Cache / State | Redis | 8.6.1 | Live execution state, dedup locks, session cache |
| Object Storage | MinIO (self-hosted) / S3 | - | Screenshots, video recordings, large log files |
| Time-series | Prometheus (pull model) | 2.x | Platform infrastructure metrics |

### 5.3 Test Execution & Parsing

| Framework | Parse Format | Java Library |
|---|---|---|
| JUnit 5 / Maven Surefire | JUnit XML (surefire-reports/*.xml) | JAXB / StAX |
| TestNG | testng-results.xml | JAXB |
| Cucumber | cucumber.json | Jackson |
| Allure | allure-results/*.json | Jackson |
| Playwright | playwright-report/results.json | Jackson |
| Postman / Newman | newman-report.json | Jackson |
| REST-assured | JUnit XML (via Surefire) | JAXB |

### 5.4 Observability

| Concern | Technology | Purpose |
|---|---|---|
| Metrics | Micrometer + Prometheus 3.10.0 | Platform health + test quality metrics |
| Dashboards | Grafana 12.4.0 | Visual dashboards for ops + quality |
| Log aggregation | Logstash + OpenSearch | Centralized log pipeline |
| Tracing | OpenTelemetry + Jaeger | Distributed tracing across services |
| Alerting | Prometheus Alertmanager | Flakiness alerts, SLA breaches, infra alerts |
| Uptime | Spring Boot Actuator | Health checks, readiness probes |

### 5.5 AI Layer

| Component | Technology | Purpose |
|---|---|---|
| Failure classification | Anthropic Claude API (claude-sonnet-4-6) | Root cause analysis, fix suggestions |
| Similarity detection | OpenSearch k-NN (vector search) | Group similar failures across teams |
| Embeddings | Claude Embeddings API | Vectorize failure messages for similarity |
| Batch processing | Spring Batch | Nightly failure analysis jobs |

### 5.6 Integration

| System | Technology | Notes |
|---|---|---|
| JIRA Cloud | Jira REST API v3 | OAuth 2.0, supports Cloud & Server |
| JIRA Server / DC | Jira REST API v2 | PAT-based auth |
| Linear | Linear GraphQL API | Alt to JIRA for some teams |
| GitHub Issues | GitHub REST API v4 | For open-source projects |
| Azure DevOps | ADO REST API | Enterprise teams |
| Slack | Slack Web API | Failure digests, alerts |
| GitHub Actions | REST API + Actions SDK | CI/CD trigger metadata |
| Jenkins | Jenkins REST API | CI/CD metadata, build links |

### 5.7 Portal (Frontend)

| Component | Technology | Version |
|---|---|---|
| Framework | React | 19.2.4 |
| Build tool | Vite | 7.3.1 |
| UI components | shadcn/ui + Tailwind CSS | Latest |
| Charts | Recharts | 2.x |
| State management | Zustand | 4.x |
| Data fetching | TanStack Query | 5.x |
| WebSocket | SockJS + STOMP | - |
| Auth | React OAuth2 | - |

### 5.8 Infrastructure

| Component | Technology | Notes |
|---|---|---|
| Containerization | Docker + Docker Compose | Local dev + CI |
| Orchestration | Kubernetes 1.35.x | Production deployment |
| Service mesh | None (start simple) | Consider Istio at scale |
| API Gateway | Spring Cloud Gateway 4.2.8 | Rate limiting, routing, auth |
| Config management | Spring Cloud Config | Externalized config |
| Secret management | HashiCorp Vault / K8s Secrets | API keys, DB passwords |
| CI/CD | GitHub Actions | Platform's own pipeline |

---

## 6. Module Design

### Module 1: `platform-api-gateway`

Spring Cloud Gateway — single entry point for all clients.

```
Responsibilities:
- Route requests to downstream services
- Rate limiting per team API key
- JWT validation (forward claims downstream)
- Request/response logging

Routes:
  /api/v1/results/**     → platform-ingestion
  /api/v1/analytics/**   → platform-analytics
  /api/v1/teams/**       → platform-core
  /api/v1/integrations/** → platform-integration
  /api/v1/ai/**          → platform-ai
  /ws/**                 → platform-core (WebSocket)
```

### Module 2: `platform-ingestion`

Accepts raw test reports, parses, normalizes, persists, and publishes.

```
src/main/java/com/platform/ingestion/
├── api/
│   └── ResultIngestionController.java
│       POST /api/v1/results/ingest
│         @RequestParam String teamId
│         @RequestParam String projectId
│         @RequestParam SourceFormat format
│         @RequestParam String branch
│         @RequestParam String environment
│         @RequestPart MultipartFile[] files
│       Returns: IngestResponse { runId, status, testCount }
│
├── parser/
│   ├── ResultParserFactory.java        # Selects parser by SourceFormat
│   ├── ResultParser.java               # Interface
│   ├── JUnitXmlParser.java
│   ├── CucumberJsonParser.java
│   ├── TestNGParser.java
│   ├── AllureResultsParser.java
│   ├── PlaywrightJsonParser.java
│   └── NewmanJsonParser.java
│
├── normalizer/
│   └── ResultNormalizer.java           # Parser output → UnifiedTestResult
│
├── enricher/
│   ├── GitMetadataEnricher.java        # Attach commit SHA, author
│   └── CiMetadataEnricher.java         # Attach CI build URL, trigger type
│
└── publisher/
    └── ResultEventPublisher.java       # Kafka producer → test.results.raw
```

### Module 3: `platform-core`

Domain model, persistence, and shared services.

```
src/main/java/com/platform/core/
├── domain/
│   ├── Team.java
│   ├── Project.java
│   ├── TestSuite.java
│   ├── TestExecution.java             # One record per CI run
│   ├── TestCaseResult.java            # One record per test case per run
│   ├── TestCaseHistory.java           # Aggregated view across runs
│   ├── FlakinessScore.java            # Computed and stored per test
│   └── JiraTicketLink.java            # test_id ↔ jira_key mapping
│
├── repository/
│   ├── TeamRepository.java
│   ├── ProjectRepository.java
│   ├── TestExecutionRepository.java
│   ├── TestCaseResultRepository.java
│   ├── FlakinessRepository.java
│   └── JiraTicketLinkRepository.java
│
├── service/
│   ├── TeamService.java
│   ├── ProjectService.java
│   ├── ExecutionPersistenceService.java  # Consumes Kafka, writes to DB
│   └── ResultQueryService.java           # Powers portal API queries
│
└── dto/
    ├── UnifiedTestResult.java
    ├── TestCaseResult.java
    └── SourceFormat.java               # Enum: JUNIT_XML, CUCUMBER_JSON, etc.
```

### Module 4: `platform-analytics`

Flakiness detection, trend analysis, quality metrics, alerting.

```
src/main/java/com/platform/analytics/
├── flakiness/
│   ├── FlakinessScoringService.java
│   │   # score = (failure_rate * recency_weight * env_variance_factor)
│   │   # Scores 0.0-1.0: < 0.1 stable, 0.1-0.3 watch, > 0.3 flaky
│   ├── FlakinessConsumer.java          # Kafka consumer, updates scores
│   └── FlakinessReportService.java     # Top-N flaky tests queries
│
├── trends/
│   ├── TrendAnalysisService.java
│   │   # Pass rate over time (daily/weekly/monthly)
│   │   # MTTR (mean time to green after failure)
│   │   # Duration trends (p50, p95, p99 per suite)
│   │   # New failures per release
│   └── QualityGateEvaluator.java       # PASS/FAIL gate for release pipelines
│
├── coverage/
│   └── FeatureCoverageMapper.java      # Tags → Jira epics/stories
│
└── alerts/
    ├── AlertRuleEngine.java            # Evaluate rules per run
    ├── AlertRule.java                  # Configurable: threshold, window, severity
    └── AlertNotificationService.java   # Slack, email, webhook dispatcher
```

**Flakiness Score Algorithm:**

```
score(test, project, lookbackDays):
  runs = last N runs in lookback window
  failure_rate = failed_runs / total_runs

  # Recency bias: recent failures matter more
  recency_weight = weighted average with decay factor 0.85 per day

  # Environment variance: fails in one env but not another = more flaky
  env_variance = stddev(failure_rate across environments)

  # Final score
  score = (failure_rate * 0.5) + (recency_weight * 0.3) + (env_variance * 0.2)

Classification:
  score < 0.10  → STABLE
  score 0.10–0.30 → WATCH
  score 0.30–0.60 → FLAKY
  score > 0.60  → CRITICAL_FLAKY
```

### Module 5: `platform-ai`

Claude API integration for failure intelligence.

```
src/main/java/com/platform/ai/
├── client/
│   └── ClaudeApiClient.java            # Anthropic Java SDK wrapper
│
├── classification/
│   ├── FailureClassificationService.java
│   │   # Input:  stack trace, failure message, last 5 run history
│   │   # Output: FailureAnalysis { category, rootCause, confidence,
│   │   #                           suggestedFix, isFlakyCandidate }
│   └── FailureCategory.java
│       # Enum: APPLICATION_BUG, TEST_DEFECT, ENVIRONMENT,
│       #       FLAKY_TIMING, DEPENDENCY, UNKNOWN
│
├── similarity/
│   ├── FailureEmbeddingService.java    # Vectorize failure messages
│   └── SimilarFailureFinder.java       # k-NN search in OpenSearch
│
├── batch/
│   └── NightlyAnalysisBatchJob.java    # Spring Batch: analyze last 24h failures
│
└── prompt/
    └── PromptBuilder.java              # Builds structured prompts for Claude
```

**Prompt Structure:**

```
You are a test automation expert. Analyze this test failure and provide structured analysis.

TEST CONTEXT:
- Test: {fully.qualified.TestClass#methodName}
- Project: {project} | Team: {team}
- Framework: {JUnit5 / Cucumber / TestNG}
- Environment: {staging}
- Branch: {main}

FAILURE:
{failure message}

STACK TRACE:
{stack trace — truncated to 50 lines}

RUN HISTORY (last 10 runs):
{table: run_id | date | status | duration}

RESPOND IN JSON:
{
  "category": "APPLICATION_BUG | TEST_DEFECT | ENVIRONMENT | FLAKY_TIMING | DEPENDENCY",
  "confidence": 0.0-1.0,
  "rootCause": "one sentence",
  "detailedAnalysis": "paragraph",
  "suggestedFix": "actionable steps",
  "isFlakyCandidate": true/false,
  "affectedComponent": "class or service name"
}
```

### Module 6: `platform-integration`

Issue tracker integration with JIRA, Linear, GitHub Issues.

```
src/main/java/com/platform/integration/
├── port/
│   └── IssueTrackerPort.java           # Interface — all trackers implement this
│       # createIssue(IssueRequest) → IssueReference
│       # updateIssue(key, IssueUpdate) → void
│       # closeIssue(key, comment) → void
│       # reopenIssue(key, comment) → void
│       # findExistingIssue(testId, projectKey) → Optional<IssueReference>
│
├── jira/
│   ├── JiraIssueTracker.java           # Implements IssueTrackerPort
│   ├── JiraClient.java                 # HTTP calls to Jira REST API v3
│   ├── JiraAuthConfig.java             # OAuth2 / PAT configuration
│   └── JiraIssueMapper.java            # UnifiedTestResult → Jira issue fields
│
├── linear/
│   └── LinearIssueTracker.java         # Implements IssueTrackerPort (GraphQL)
│
├── github/
│   └── GitHubIssueTracker.java         # Implements IssueTrackerPort
│
├── lifecycle/
│   ├── IssueDecisionEngine.java        # Decides: create / update / close / skip
│   ├── TicketLifecycleManager.java     # Orchestrates lifecycle transitions
│   └── DuplicateDetector.java          # Prevents ticket spam
│
└── consumer/
    └── IntegrationEventConsumer.java   # Kafka consumer: test.results.analyzed
```

**IssueDecisionEngine Rules:**

```
Rule 1 — Create BUG ticket:
  IF category = APPLICATION_BUG
  AND consecutive_failures >= team.config.min_consecutive_failures (default: 3)
  AND no existing open ticket for this test
  → CREATE Bug ticket, HIGH priority if on main branch

Rule 2 — Create TEST_MAINTENANCE ticket:
  IF flakiness_score > team.config.flakiness_threshold (default: 0.30)
  AND no existing open ticket for this test
  → CREATE Test Maintenance ticket

Rule 3 — Create TEST_FIX ticket:
  IF category = TEST_DEFECT
  AND confidence > 0.7
  → CREATE Test Fix ticket, assign to test owner

Rule 4 — Update existing ticket:
  IF existing open ticket found for this test
  → ADD comment with latest run details
  → INCREMENT occurrence count in custom field

Rule 5 — Close ticket:
  IF test passed consecutively >= 3 runs
  AND existing open ticket found
  → TRANSITION to Done
  → ADD comment: "Resolved. Test passed in runs: #x, #y, #z"

Rule 6 — Reopen ticket:
  IF existing Done ticket found
  AND test is failing again
  → REOPEN ticket
  → ADD comment: "Regression detected. Failing since run #x"

Rule 7 — Deduplicate:
  IF multiple tests fail with same root cause (similarity > 0.85)
  → Create ONE incident ticket
  → Link all affected tests to it
```

### Module 7: `platform-portal`

React portal with BFF (Backend for Frontend).

```
platform-portal/
├── backend/  (Spring Boot BFF)
│   ├── OrgDashboardController.java     GET /bff/org/dashboard
│   ├── TeamDashboardController.java    GET /bff/teams/{teamId}/dashboard
│   ├── TestDetailController.java       GET /bff/tests/{testId}
│   ├── ExecutionController.java        GET /bff/executions/{runId}
│   └── WebSocketController.java        STOMP /ws/executions/live
│
└── frontend/ (React + Vite)
    ├── pages/
    │   ├── OrgDashboard.tsx            # Architect view
    │   ├── TeamDashboard.tsx           # Team lead view
    │   ├── TestDetail.tsx              # Developer view
    │   └── ExecutionDetail.tsx         # Single run view
    ├── components/
    │   ├── QualityHealthMatrix.tsx     # Cross-team health table
    │   ├── FlakinessLeaderboard.tsx    # Top flaky tests org-wide
    │   ├── TrendChart.tsx              # Pass rate over time
    │   ├── FailureCategoryPie.tsx      # AI classification breakdown
    │   ├── JiraTicketBadge.tsx         # Inline ticket status
    │   └── AiAnalysisCard.tsx          # Root cause + fix suggestion
    └── hooks/
        ├── useLiveExecutions.ts        # WebSocket subscription
        └── useQualityTrend.ts
```

**Org Dashboard View (Architect):**

```
Organization Quality Health — Week of Mar 7, 2026
─────────────────────────────────────────────────────────────────────
Team          Pass Rate   Flaky Tests   Broken Tests   Trend     Gate
─────────────────────────────────────────────────────────────────────
Checkout      94.2%       12            3              +2.1%     PASS
Payments      87.1%       31            8              -5.4%     WARN
Auth          98.7%        2            0              stable    PASS
Search        71.0%       44           15             -11.0%     FAIL
─────────────────────────────────────────────────────────────────────
Org Average   87.8%       89           26              -3.5%
```

### Module 8: `platform-sdk`

Zero-friction onboarding library for Java teams.

```
platform-sdk/
├── junit5/
│   └── PlatformReportingExtension.java
│       # JUnit 5 Extension — auto-publishes on suite completion
│       # @ExtendWith(PlatformReportingExtension.class)
│       # @PlatformProject(team="payments", project="payment-service")
│
├── testng/
│   └── PlatformTestNGListener.java
│       # TestNG IReporter — plugs into testng.xml
│       # <listener class-name="com.platform.sdk.PlatformTestNGListener"/>
│
├── cucumber/
│   └── PlatformCucumberPlugin.java
│       # Cucumber Plugin — add to @CucumberOptions plugins
│       # plugins = {"com.platform.sdk.PlatformCucumberPlugin"}
│
└── config/
    └── PlatformConfig.java
        # Reads from platform.properties or env vars:
        # PLATFORM_URL, PLATFORM_API_KEY, PLATFORM_TEAM, PLATFORM_PROJECT
```

---

## 7. Data Model

### 7.1 PostgreSQL Schema

```sql
-- Teams and Projects
CREATE TABLE teams (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(50)  NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID NOT NULL REFERENCES teams(id),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(50)  NOT NULL,
    repo_url    VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (team_id, slug)
);

-- Integration configuration per team
CREATE TABLE integration_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id         UUID NOT NULL REFERENCES teams(id),
    tracker_type    VARCHAR(20) NOT NULL,    -- JIRA, LINEAR, GITHUB
    base_url        VARCHAR(500),
    project_key     VARCHAR(50),
    config_json     JSONB NOT NULL,          -- tracker-specific config
    enabled         BOOLEAN DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- Test Executions (one per CI run)
CREATE TABLE test_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          VARCHAR(100) NOT NULL UNIQUE,
    project_id      UUID NOT NULL REFERENCES projects(id),
    branch          VARCHAR(200),
    commit_sha      VARCHAR(40),
    environment     VARCHAR(50),
    trigger_type    VARCHAR(30),            -- CI_PUSH, SCHEDULED, MANUAL
    source_format   VARCHAR(30) NOT NULL,   -- JUNIT_XML, CUCUMBER_JSON, etc.
    ci_provider     VARCHAR(30),
    ci_run_url      VARCHAR(1000),
    total_tests     INT NOT NULL,
    passed          INT NOT NULL DEFAULT 0,
    failed          INT NOT NULL DEFAULT 0,
    skipped         INT NOT NULL DEFAULT 0,
    broken          INT NOT NULL DEFAULT 0,
    duration_ms     BIGINT,
    executed_at     TIMESTAMP NOT NULL,
    ingested_at     TIMESTAMP NOT NULL DEFAULT now()
);

-- Individual test case results (one per test per run)
CREATE TABLE test_case_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID NOT NULL REFERENCES test_executions(id),
    test_id         VARCHAR(500) NOT NULL,  -- fully qualified name
    display_name    VARCHAR(500),
    class_name      VARCHAR(300),
    method_name     VARCHAR(200),
    tags            TEXT[],
    status          VARCHAR(20) NOT NULL,   -- PASSED, FAILED, SKIPPED, BROKEN
    duration_ms     BIGINT,
    failure_message TEXT,
    stack_trace     TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tcr_test_id ON test_case_results(test_id);
CREATE INDEX idx_tcr_execution_id ON test_case_results(execution_id);
CREATE INDEX idx_tcr_status ON test_case_results(status);

-- Aggregated flakiness scores (updated on each run)
CREATE TABLE flakiness_scores (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id             VARCHAR(500) NOT NULL,
    project_id          UUID NOT NULL REFERENCES projects(id),
    score               DECIMAL(5,4) NOT NULL,
    classification      VARCHAR(20) NOT NULL,  -- STABLE, WATCH, FLAKY, CRITICAL_FLAKY
    total_runs          INT NOT NULL,
    failure_count       INT NOT NULL,
    failure_rate        DECIMAL(5,4) NOT NULL,
    last_failed_at      TIMESTAMP,
    last_passed_at      TIMESTAMP,
    computed_at         TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (test_id, project_id)
);

-- AI failure analysis results
CREATE TABLE failure_analyses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_result_id UUID NOT NULL REFERENCES test_case_results(id),
    category            VARCHAR(30),
    confidence          DECIMAL(3,2),
    root_cause          TEXT,
    detailed_analysis   TEXT,
    suggested_fix       TEXT,
    is_flaky_candidate  BOOLEAN,
    affected_component  VARCHAR(200),
    analyzed_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- JIRA / issue tracker links
CREATE TABLE issue_tracker_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id         VARCHAR(500) NOT NULL,
    project_id      UUID NOT NULL REFERENCES projects(id),
    tracker_type    VARCHAR(20) NOT NULL,
    issue_key       VARCHAR(50) NOT NULL,
    issue_url       VARCHAR(1000),
    issue_status    VARCHAR(50),
    issue_type      VARCHAR(50),
    linked_at       TIMESTAMP NOT NULL DEFAULT now(),
    last_synced_at  TIMESTAMP,
    UNIQUE (test_id, project_id, tracker_type)
);
```

### 7.2 OpenSearch Index Mappings

```json
// Index: test_case_results
{
  "mappings": {
    "properties": {
      "test_id":         { "type": "keyword" },
      "execution_id":    { "type": "keyword" },
      "project_id":      { "type": "keyword" },
      "team_id":         { "type": "keyword" },
      "status":          { "type": "keyword" },
      "failure_message": { "type": "text", "analyzer": "english" },
      "stack_trace":     { "type": "text" },
      "tags":            { "type": "keyword" },
      "executed_at":     { "type": "date" },
      "duration_ms":     { "type": "long" },
      "failure_embedding": {
        "type": "knn_vector",
        "dimension": 1536,
        "method": { "name": "hnsw", "space_type": "l2" }
      }
    }
  }
}
```

---

## 8. API Design

### 8.1 Ingestion API

```
POST /api/v1/results/ingest
  Content-Type: multipart/form-data
  X-API-Key: {team-api-key}

  teamId:      string (required)
  projectId:   string (required)
  format:      JUNIT_XML | CUCUMBER_JSON | TESTNG | ALLURE | PLAYWRIGHT | NEWMAN
  branch:      string
  environment: string (default: "unknown")
  commitSha:   string
  ciRunUrl:    string
  files:       MultipartFile[] (required)

Response 202 Accepted:
{
  "runId": "run-abc123",
  "status": "ACCEPTED",
  "testCount": 142,
  "processingUrl": "/api/v1/executions/run-abc123"
}
```

### 8.2 Query API

```
GET /api/v1/executions?projectId=&branch=&from=&to=&page=&size=
GET /api/v1/executions/{runId}
GET /api/v1/executions/{runId}/tests

GET /api/v1/tests/{testId}/history?projectId=&lookbackDays=30
GET /api/v1/tests/{testId}/analysis

GET /api/v1/analytics/flakiness?projectId=&teamId=&limit=20
GET /api/v1/analytics/trends?projectId=&groupBy=DAY|WEEK|MONTH&from=&to=
GET /api/v1/analytics/org-health
GET /api/v1/analytics/quality-gate/{projectId}?branch=main
```

### 8.3 Integration API

```
GET  /api/v1/integrations/config/{teamId}
PUT  /api/v1/integrations/config/{teamId}

GET  /api/v1/integrations/links?testId=&projectId=
POST /api/v1/integrations/links          # Manual link: test ↔ ticket
DELETE /api/v1/integrations/links/{id}

POST /api/v1/integrations/trigger/{testId}  # Manually trigger ticket action
```

---

## 9. Integration Design

### 9.1 JIRA Ticket Template

```
Summary:  [AUTO] {TestClass}#{method} — {STATUS} ({N} runs)

Type:     Bug | Test Maintenance | Test Fix | Incident
Priority: Critical (main) | High (release) | Medium (feature branch)
Labels:   automated-failure, {team-slug}, {category}
Component: {project}

Description:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Automated Test Failure Report

Test:         {fully.qualified.ClassName#methodName}
Project:      {project} | Team: {team}
Framework:    {JUnit5 | Cucumber | TestNG}
Environment:  {environment}
Branch:       {branch}
First seen:   {date} ({N} consecutive failures)

Failure History:
  Run #{id} — FAILED — {date} — {duration}ms
  Run #{id} — FAILED — {date} — {duration}ms
  ... (last 5)

Failure Message:
  {failure_message}

Stack Trace (excerpt):
  {stack_trace — first 20 lines}

AI Root Cause Analysis:
  Category:   {category} (confidence: {pct}%)
  Root Cause: {one-line summary}
  Analysis:   {paragraph}
  Suggested Fix: {actionable steps}

Links:
  Platform test page: {platform_url}/tests/{testId}
  Latest run:         {platform_url}/executions/{runId}
  CI build:           {ci_run_url}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Reporter: Test Automation Platform [bot]
```

### 9.2 Slack Digest (Daily)

```
Test Quality Digest — {date}
─────────────────────────────
New failures today:     12  (5 on main branch)
Newly flaky tests:       3
Tests back to green:     8
Open JIRA tickets:      47

Top flaky tests this week:
  1. PaymentFlowTest#processRefund    Score: 0.78  [PAY-1234]
  2. SearchTest#filterByCategory      Score: 0.71  [SRCH-445]
  3. AuthTest#sessionExpiry           Score: 0.65  [AUTH-89]

Teams needing attention:
  Search   — pass rate dropped 11% this week
  Payments — 31 flaky tests, 8 broken

View full report: {platform_url}/org/dashboard
```

### 9.3 CI/CD Integration

```yaml
# GitHub Actions — works for every team, any framework
- name: Publish test results to platform
  if: always()
  uses: your-org/test-platform-action@v1
  with:
    team-id: ${{ vars.PLATFORM_TEAM_ID }}
    project-id: ${{ vars.PLATFORM_PROJECT_ID }}
    api-key: ${{ secrets.PLATFORM_API_KEY }}
    format: junit-xml                        # or cucumber-json, testng, allure, playwright
    results-path: target/surefire-reports/
    environment: staging
```

```groovy
// Jenkinsfile — platform plugin
testPlatform(
    teamId: 'payments',
    projectId: 'payment-service',
    format: 'JUNIT_XML',
    resultsPath: 'target/surefire-reports/',
    environment: 'staging'
)
```

---

## 10. Observability Design

### 10.1 Platform Metrics (Micrometer → Prometheus)

```
# Ingestion
platform.ingestion.requests.total{team, format, status}
platform.ingestion.processing.duration{format}
platform.ingestion.tests.ingested.total{team, project}

# Quality metrics (custom gauges, updated per run)
platform.quality.pass_rate{team, project, branch}
platform.quality.flakiness_score{team, project, test_id}
platform.quality.broken_tests.count{team, project}
platform.quality.execution.duration_p95{team, project}

# Integration
platform.jira.tickets.created.total{team, issue_type}
platform.jira.tickets.closed.total{team}
platform.jira.api.duration{operation}
```

### 10.2 Grafana Dashboards

**Dashboard 1 — Org Quality Health:**
- Cross-team pass rate heatmap (team × week)
- Org-wide flaky test count over time
- Failure category distribution (AI-classified pie)
- Teams below SLA threshold

**Dashboard 2 — Team Quality:**
- Daily pass/fail bar chart
- Top 10 flakiest tests (table with scores)
- Execution duration trend (p50/p95)
- JIRA ticket backlog trend

**Dashboard 3 — Platform Health:**
- Ingestion request rate + error rate
- Processing latency (p50/p95/p99)
- Kafka consumer lag
- AI analysis queue depth

### 10.3 Alert Rules

```yaml
# Prometheus alerting rules
groups:
  - name: quality-alerts
    rules:
      - alert: TeamPassRateDropped
        expr: platform_quality_pass_rate < 0.80
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.team }} pass rate below 80%"

      - alert: HighFlakinessDetected
        expr: platform_quality_flakiness_score > 0.60
        labels:
          severity: warning
        annotations:
          summary: "Critical flaky test detected: {{ $labels.test_id }}"

      - alert: MainBranchBlocked
        expr: platform_quality_broken_tests_count{branch="main"} > 5
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.team }}/{{ $labels.project }} has {{ $value }} broken tests on main"
```

---

## 11. AI Failure Intelligence

### 11.1 Real-time Classification (per run)

Triggered immediately after each failed test is ingested:
- Claude API called with failure context + history
- Result stored in `failure_analyses` table
- Shown in portal test detail view

### 11.2 Nightly Batch Analysis (Spring Batch)

```
Job: NightlyFailureAnalysisJob  (runs at 2:00 AM)

Step 1: FetchUnanalyzedFailuresStep
  Read: test_case_results WHERE status=FAILED AND analyzed=false in last 24h

Step 2: ClassifyFailuresStep
  Processor: call Claude API for each failure (rate-limited: 10 req/s)
  Writer: persist FailureAnalysis records

Step 3: SimilarityGroupingStep
  Compute embeddings for new failures
  k-NN search against existing failures
  Group failures with similarity > 0.85

Step 4: IssueDecisionStep
  For each failure/group: run IssueDecisionEngine
  Create/update/close JIRA tickets

Step 5: DigestGenerationStep
  Aggregate analysis results
  Send Slack digest to team leads + architect
```

### 11.3 AI Use Boundaries

**AI accelerates:**
- Root cause classification at scale (hundreds of failures in minutes)
- Grouping similar failures across teams that humans would never correlate
- Suggesting fix direction based on stack trace pattern recognition
- Flakiness narrative: "This test has been flaky for 3 weeks, most likely due to..."

**AI does NOT do (platform enforces):**
- Auto-create or auto-merge code changes
- Close JIRA tickets without human confirmation (configurable)
- Suppress or silence test failures
- Replace actual failure investigation

---

## 12. Security & Access Control

### 12.1 Authentication

- Portal: SSO via OIDC (Okta, Google Workspace, Azure AD)
- API: API Key per team (scoped to team's data only)
- Service-to-service: JWT with short TTL

### 12.2 RBAC Roles

| Role | Access |
|---|---|
| PLATFORM_ADMIN | All teams, all data, platform config |
| ARCHITECT | Read all teams, manage org-level settings |
| TEAM_LEAD | Full access to own team, read-only other teams |
| DEVELOPER | Read/write own team, no other teams |
| VIEWER | Read-only, own team |

### 12.3 Data Isolation

- Each team's test results are scoped by `team_id`
- API key validation enforces team boundary at ingestion
- OpenSearch indices use team-scoped aliases
- Portal BFF adds team filter on all queries based on JWT claims

---

## 13. Infrastructure & Deployment

### 13.1 Kubernetes Deployment

```yaml
# Per-service deployment
Services:
  platform-api-gateway    replicas: 2    memory: 512Mi  cpu: 500m
  platform-ingestion      replicas: 3    memory: 1Gi    cpu: 1000m
  platform-core           replicas: 2    memory: 1Gi    cpu: 500m
  platform-analytics      replicas: 2    memory: 2Gi    cpu: 1000m
  platform-ai             replicas: 1    memory: 512Mi  cpu: 250m
  platform-integration    replicas: 2    memory: 512Mi  cpu: 250m
  platform-portal         replicas: 2    memory: 256Mi  cpu: 250m

Infrastructure:
  PostgreSQL              StatefulSet, PVC 100Gi
  OpenSearch              StatefulSet, PVC 200Gi, 3-node cluster
  Redis                   StatefulSet, PVC 10Gi
  Kafka                   KRaft mode, 3 brokers, PVC 50Gi each
  MinIO                   StatefulSet, PVC 500Gi (screenshots/videos)
```

### 13.2 Local Development (Docker Compose)

```yaml
# docker-compose.yml
services:
  postgres:     image: postgres:17
  opensearch:   image: opensearchproject/opensearch:3.5.0
  redis:        image: redis:8.6.1
  kafka:        image: apache/kafka:4.2.0          # KRaft-native; no ZooKeeper needed
  minio:        image: minio/minio
  grafana:      image: grafana/grafana:12.4.0
  prometheus:   image: prom/prometheus:v3.10.0
  jaeger:       image: jaegertracing/all-in-one

# Teams can run the full platform locally with:
# docker-compose up -d
```

### 13.3 Platform CI/CD (GitHub Actions)

```
On PR:
  - Build all modules
  - Unit tests + integration tests (Testcontainers)
  - Static analysis (SpotBugs, Checkstyle)
  - Docker image build + scan (Trivy)

On merge to main:
  - All above + E2E tests
  - Push Docker images to registry
  - Deploy to staging (Helm upgrade)
  - Smoke tests against staging

On release tag:
  - Deploy to production
  - Run smoke tests
  - Notify Slack
```

---

## 14. Implementation Phases

### Phase 1 — Foundation (Weeks 1–4)
**Goal:** First team can push results, architect sees data.

- [ ] Maven multi-module project scaffold
- [ ] PostgreSQL schema + Flyway migrations
- [ ] `platform-core`: Team, Project, TestExecution, TestCaseResult domain
- [ ] `platform-ingestion`: JUnit XML parser + normalizer + persistence
- [ ] Basic REST API: ingest + query executions
- [ ] Docker Compose local stack
- [ ] Onboard 1 pilot team (JUnit 5)
- [ ] Basic portal: execution list per project

**Deliverable:** Pilot team's results visible in portal. Architect can query runs.

---

### Phase 2 — Multi-Framework Ingestion (Weeks 5–7)
**Goal:** All existing teams can onboard without changing their test code.

- [ ] Add parsers: Cucumber JSON, TestNG, Allure, Playwright, Newman
- [ ] `platform-sdk`: JUnit 5 extension, TestNG listener, Cucumber plugin
- [ ] GitHub Actions action (`test-platform-action`)
- [ ] Jenkins pipeline step
- [ ] Onboard all teams
- [ ] Portal: per-team execution history + pass/fail trend chart

**Deliverable:** All teams publishing results. Basic cross-team visibility.

---

### Phase 3 — Analytics & Dashboards (Weeks 8–11)
**Goal:** Architect has real-time org quality view. Flakiness is visible.

- [ ] Flakiness scoring algorithm + Kafka consumer
- [ ] Trend analysis service (daily/weekly pass rates, MTTR)
- [ ] Quality gate API (PASS/FAIL for CI pipelines)
- [ ] Org dashboard: quality health matrix, flakiness leaderboard
- [ ] Team dashboard: trend charts, top flaky tests, duration outliers
- [ ] OpenSearch integration + failure full-text search
- [ ] Prometheus metrics + Grafana dashboards

**Deliverable:** Architect has org-wide dashboard. Teams see their flakiness ranking.

---

### Phase 4 — JIRA Integration (Weeks 12–14)
**Goal:** Test failures automatically create and manage JIRA tickets.

- [ ] `platform-integration`: JIRA client + IssueDecisionEngine
- [ ] Ticket lifecycle manager (create / update / close / reopen)
- [ ] Duplicate detector
- [ ] Team config UI for JIRA project key, thresholds, issue type mapping
- [ ] Bidirectional link display in portal
- [ ] Manual link/unlink from portal

**Deliverable:** JIRA tickets auto-created for failures. Closed when tests go green.

---

### Phase 5 — AI Failure Intelligence (Weeks 15–17)
**Goal:** Every failure has an AI-classified root cause and suggested fix.

- [ ] Claude API client + FailureClassificationService
- [ ] Real-time classification on ingest
- [ ] OpenSearch k-NN vector index for similarity
- [ ] Similar failures grouping across teams
- [ ] AI analysis card in test detail view
- [ ] Nightly batch job (Spring Batch)
- [ ] Slack digest with AI-classified failure summary

**Deliverable:** AI root cause visible per failure. Nightly digest sent to leads.

---

### Phase 6 — Alerts & Quality Gates (Weeks 18–19)
**Goal:** Platform proactively notifies teams before issues escalate.

- [ ] Alert rule engine (configurable per team)
- [ ] Slack + email + webhook notification dispatch
- [ ] Prometheus alerting rules
- [ ] Quality gate enforcement in CI (fail build if gate fails)
- [ ] Release quality report (auto-generated per release tag)

**Deliverable:** Teams alerted automatically. Release quality reports automated.

---

### Phase 7 — Hardening & Scale (Weeks 20–22)
**Goal:** Production-ready, secure, observable, documented.

- [ ] RBAC implementation (Spring Security + OIDC)
- [ ] API key rotation + audit log
- [ ] Performance testing (10k results/minute ingestion)
- [ ] Full Kubernetes deployment (Helm charts)
- [ ] Disaster recovery + backup procedures
- [ ] API documentation (Swagger UI)
- [ ] Team onboarding runbook
- [ ] Load testing report

**Deliverable:** Platform ready for all teams in production.

---

## 15. Repository Structure

```
test-automation-platform/
│
├── platform-api-gateway/           # Spring Cloud Gateway
│   ├── src/
│   └── pom.xml
│
├── platform-ingestion/             # Result parsing + normalization
│   ├── src/
│   └── pom.xml
│
├── platform-core/                  # Domain model + persistence
│   ├── src/
│   └── pom.xml
│
├── platform-analytics/             # Flakiness, trends, alerts
│   ├── src/
│   └── pom.xml
│
├── platform-ai/                    # Claude API + failure intelligence
│   ├── src/
│   └── pom.xml
│
├── platform-integration/           # JIRA, Linear, GitHub Issues
│   ├── src/
│   └── pom.xml
│
├── platform-portal/                # React UI + BFF
│   ├── backend/src/                # Spring Boot BFF
│   ├── frontend/src/               # React app
│   └── pom.xml
│
├── platform-sdk/                   # Team onboarding library
│   ├── src/
│   └── pom.xml
│
├── platform-common/                # Shared DTOs, utils, UnifiedTestResult
│   ├── src/
│   └── pom.xml
│
├── infrastructure/
│   ├── docker/
│   │   └── docker-compose.yml      # Full local stack
│   ├── k8s/
│   │   ├── namespace.yaml
│   │   ├── platform-ingestion/
│   │   ├── platform-core/
│   │   ├── platform-analytics/
│   │   ├── platform-ai/
│   │   ├── platform-integration/
│   │   └── platform-portal/
│   ├── helm/
│   │   └── test-automation-platform/
│   ├── grafana/
│   │   └── dashboards/
│   ├── prometheus/
│   │   └── rules/
│   └── logstash/
│       └── pipelines/
│
├── .github/
│   ├── workflows/
│   │   ├── build.yml
│   │   ├── deploy-staging.yml
│   │   └── deploy-production.yml
│   └── actions/
│       └── test-platform-action/   # Reusable GitHub Action for teams
│
├── docs/
│   ├── architecture/
│   │   ├── overview.md
│   │   ├── data-model.md
│   │   └── adr/                    # Architecture Decision Records
│   ├── api/
│   │   └── openapi.yaml
│   ├── onboarding/
│   │   ├── junit5.md
│   │   ├── cucumber.md
│   │   ├── testng.md
│   │   └── playwright.md
│   └── runbooks/
│       ├── deployment.md
│       └── incident-response.md
│
└── pom.xml                         # Parent POM (multi-module)
```

---

## 16. Non-Functional Requirements

| Requirement | Target |
|---|---|
| Ingestion throughput | 10,000 test results / minute |
| Ingestion latency (p99) | < 3 seconds end-to-end |
| Portal load time | < 2 seconds (org dashboard) |
| AI classification latency | < 10 seconds per failure |
| Platform uptime | 99.5% (not on critical path of test execution) |
| Data retention | 12 months (PostgreSQL), 90 days (OpenSearch logs) |
| API response time (p95) | < 500ms |
| JIRA ticket creation latency | < 30 seconds after failure ingested |
| Concurrent teams | 50+ |
| Max test results per run | 10,000 test cases |

---

## 17. Risk & Trade-offs

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JIRA API rate limiting | Medium | Medium | Queue-based dispatch, exponential backoff, team-level rate limits |
| Claude API cost at scale | Medium | Medium | Batch nightly analysis for non-critical, real-time only for main branch failures |
| Parser format variations | High | Medium | Parser versioning, fuzzy matching, fallback to generic JUnit parser |
| Teams not onboarding | High | High | Zero-code CI step (curl) as absolute fallback — no SDK required |
| Kafka consumer lag during CI peaks | Low | Low | Consumer group scaling, HPA on analytics service |
| False positive JIRA ticket spam | Medium | High | Duplicate detector, min consecutive failure threshold (default 3), manual override |
| AI hallucinated fix suggestions | Low | Medium | Display confidence score, label clearly as "AI suggestion", never auto-apply |
| Data volume growth | Low | Medium | Partition PostgreSQL tables by month, OpenSearch ILM policy |

### Key Architectural Decisions (ADRs)

**ADR-001: Single ingestion API over framework-specific SDKs**
Decision: Primary integration via HTTP multipart POST; SDK is optional convenience layer.
Rationale: Teams can onboard in one CI step with zero code changes.

**ADR-002: Kafka for result events over synchronous persistence**
Decision: Parse → Kafka → async consumers for DB write + analytics + AI + integration.
Rationale: Decouples ingestion latency from downstream processing. Enables replay.

**ADR-003: PostgreSQL + OpenSearch dual storage**
Decision: PostgreSQL for structured queries, OpenSearch for full-text and k-NN.
Rationale: SQL for dashboards/trends, OpenSearch for failure search and similarity.

**ADR-004: Claude API over self-hosted LLM**
Decision: Use Claude API (claude-sonnet-4-6) for failure classification.
Rationale: Quality of analysis outweighs cost. Nightly batching controls spend.

**ADR-005: IssueDecisionEngine — no auto-close without configurable confirmation**
Decision: Auto-close is opt-in per team (default: off for close, on for create/update).
Rationale: Accidental ticket closure has higher blast radius than missed closure.

---

## 18. Team Onboarding Guide

### Option A — CI step only (zero code change, 5 minutes)

```yaml
# Add to any CI/CD pipeline, regardless of framework
- name: Publish test results
  if: always()
  run: |
    curl -sS -X POST https://platform.yourorg.com/api/v1/results/ingest \
      -H "X-API-Key: $PLATFORM_API_KEY" \
      -F "teamId=my-team" \
      -F "projectId=my-service" \
      -F "format=JUNIT_XML" \
      -F "branch=$BRANCH_NAME" \
      -F "environment=staging" \
      -F "files=@target/surefire-reports/"
```

### Option B — SDK (JUnit 5, 10 minutes)

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.yourorg.platform</groupId>
  <artifactId>platform-sdk</artifactId>
  <version>1.0.0</version>
  <scope>test</scope>
</dependency>
```

```java
// Add to test class (or base test class)
@ExtendWith(PlatformReportingExtension.class)
@PlatformProject(team = "my-team", project = "my-service")
class MyServiceTest {
  // No other changes
}
```

```properties
# src/test/resources/platform.properties
platform.url=https://platform.yourorg.com
platform.api-key=${PLATFORM_API_KEY}
platform.environment=staging
```

### What teams get immediately after onboarding

- Test execution history visible in portal
- Pass/fail trends from day 1
- Flakiness scores computed after 10+ runs
- AI failure analysis on every failed test
- JIRA tickets auto-managed (once configured)
- Slack alerts on quality threshold breaches

---

*Document version: 1.1 | Last updated: 2026-03-07 | Tech stack versions verified against latest stable releases*
*Owner: Test Automation Architect*
