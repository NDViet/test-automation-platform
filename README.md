# Test Automation Platform

Cross-team quality intelligence platform for automated testing. It ingests test results, normalizes execution history, computes quality signals, manages test cases and requirements, classifies failures with AI, and runs agentic workflows for impact analysis, test generation, and flaky-test healing.

## Demo

[![Test Automation Platform demo](https://img.youtube.com/vi/8oQqHW5LBU0/0.jpg)](https://www.youtube.com/watch?v=8oQqHW5LBU0)

## What's in v2.0

Commit `b4640398780f0f339bb3a9bf5e83b271ab4e1b01` initializes the v2.0 platform revamp:

- `platform-agent`: Agent Hub for workflow orchestration, node routing, PR impact analysis, test generation, automation generation, review decisions, and flaky-test fixes.
- `platform-storage`: shared blob storage with filesystem, MinIO, and S3-compatible backends.
- `platform-integration-config`: integration adapter registry with initial Jira Cloud and GitHub adapter skeletons.
- Canonical quality model: requirements, test cases, traceability edges, test suites, manual test runs, agent workflows, review requests, token budgets, GitHub PR tracking, and impact analyses.
- Portal expansion: requirements, PR analyses, impact analyses, project settings, test case management, test runs, flaky tests, API keys, and AI settings.
- Database migrations extended through `V44`.

## Architecture

```text
                          Test Automation Platform

  Test frameworks and CI
  JUnit 5 / TestNG / Cucumber / Allure / Playwright / Newman / k6 / Gatling / JMeter
                 |
                 v
  +----------------------+       Kafka        +----------------------+
  | platform-ingestion   |  test.results.raw  | platform-analytics   |
  | :8081                +------------------->| :8082                |
  | parse, normalize, DB |                    | flakiness, TIA, logs |
  +----------+-----------+                    +----------+-----------+
             |                                           |
             |                                           v
             |                                +----------------------+
             |                                | platform-ai          |
             |                                | :8084                |
             |                                | Claude/OpenAI        |
             |                                +----------------------+
             |
             v
  +----------------------+                    +----------------------+
  | PostgreSQL 17        |<------------------>| platform-agent       |
  | source of truth      |                    | :8086, host :8087   |
  +----------------------+                    | agent workflows      |
             ^                                +----------+-----------+
             |                                           |
  +----------+-----------+                    +----------v-----------+
  | platform-portal      |                    | MinIO / S3 / FS      |
  | :8085                |                    | artifacts, diffs,    |
  | BFF + React SPA      |                    | checkpoints          |
  +----------------------+                    +----------------------+

  Supporting services: Redis, OpenSearch, Logstash, Loki, Promtail, Grafana,
  Prometheus, and Flyway.
```

## Maven modules

| Module | Port | Role |
|---|---:|---|
| `platform-common` | - | Shared DTOs, enums, Kafka topics, agent contracts, integration contracts, storage contracts |
| `platform-storage` | - | BlobStore auto-configuration for filesystem, MinIO, and S3-compatible storage |
| `platform-core` | - | JPA domain, repositories, Flyway migrations `V1` through `V44` |
| `platform-ingestion` | 8081 | Result ingestion, parsers, project/team management, requirements, integrations, TCM APIs |
| `platform-analytics` | 8082 | Flakiness, trends, quality gates, release reports, Test Impact Analysis, alerting, log search |
| `platform-integration` | 8083 | Jira ticket lifecycle for repeated failures and flaky tests |
| `platform-integration-config` | - | Project-scoped integration adapter registry and adapter contracts |
| `platform-ai` | 8084 | Failure classification, batch analysis, Claude/OpenAI provider routing, AI settings |
| `platform-portal` | 8085 | Spring Boot BFF plus embedded React SPA |
| `platform-agent` | 8086 | Agent Hub, node registry, workflows, webhooks, review gateway, impact/test-generation/healing flows |
| `platform-testkit` | - | Rich Java instrumentation for JUnit 5, TestNG, Cucumber, Playwright-assisted tests |
| `platform-adapters` | - | Java, Playwright, and k6 publishing adapters |

## Runtime services

| Service | Local URL | Notes |
|---|---|---|
| Platform Portal | http://localhost:8085 | Main UI |
| Ingestion API | http://localhost:8081/swagger-ui.html | Result, project, requirement, integration, and TCM APIs |
| Analytics API | http://localhost:8082/swagger-ui.html | Flakiness, TIA, quality gates, alerts, logs |
| Integration API | http://localhost:8083/swagger-ui.html | Jira lifecycle service |
| AI API | http://localhost:8084/swagger-ui.html | Failure analysis and provider settings |
| Agent Hub | http://localhost:8087 | Host port maps to container port `8086` |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Prometheus | http://localhost:9090 | Metrics |
| OpenSearch | http://localhost:9200 | Log search backend |
| MinIO Console | http://localhost:9001 | `platform` / `platform123` |
| PostgreSQL | localhost:5432 | db `platform`, user `platform`, password `platform` |
| Kafka | localhost:9092 | KRaft mode |
| Redis | localhost:6379 | Agent checkpoints |
| Loki | http://localhost:3100 | Log aggregation |
| Prometheus | http://localhost:9090 | Metrics + k6 real-time (remote write) |

## Quick start with Docker

No Java or Maven is required for the pre-built image path. Docker Compose starts infrastructure, runs Flyway through the `db-migrate` one-shot container, and then starts the platform services.

```bash
git clone https://github.com/ndviet/test-automation-platform.git
cd test-automation-platform

docker compose --profile services pull
docker compose --profile services up -d
docker compose ps
```

Stop the stack:

```bash
docker compose --profile services down

# Wipes database, Kafka, Redis, OpenSearch, MinIO, Grafana, Loki, and Prometheus data.
docker compose --profile services down -v
```

## Configuration

Create a `.env` file in the project root when you need non-default settings. Docker Compose reads it automatically.

```bash
# AI providers
ANTHROPIC_API_KEY=sk-ant-...
AI_PROVIDER=claude
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o

# Ingestion auth, disabled by default for local development
API_KEY_AUTH_ENABLED=false

# Agent integrations
GITHUB_TOKEN=ghp_...
GITHUB_WEBHOOK_SECRET=...
JIRA_WEBHOOK_SECRET=...
LINEAR_WEBHOOK_SECRET=...
SLACK_BOT_TOKEN=xoxb-...
SLACK_SIGNING_SECRET=...
SLACK_APPROVAL_CHANNEL=#agent-approvals
```

Storage is selected with `platform.storage.type`:

| Type | Use case |
|---|---|
| `filesystem` | Local service runs without object storage |
| `minio` | Docker Compose local stack |
| `s3` | AWS S3 or compatible production storage |

Compose configures MinIO automatically and creates these buckets: `platform-artifacts`, `platform-knowledge`, `platform-checkpoints`, and `platform-diffs`.

## Build from source

### Prerequisites

| Tool | Version |
|---|---:|
| Java | 21 |
| Maven | 3.9+ |
| Docker + Docker Compose | 27+ |
| Node.js | 20+ for portal and JS adapter development |

### Build all Java modules

```bash
mvn -B clean package -DskipTests
```

### Build deployable services only

```bash
mvn -B clean package -DskipTests \
  -pl platform-ingestion,platform-analytics,platform-integration,platform-ai,platform-portal,platform-agent \
  -am
```

### Run tests

```bash
mvn test
```

### Build JS adapters

```bash
cd platform-adapters/js/playwright
npm ci
npm run build

cd ../k6
npm install --ignore-scripts
npm run bundle
```

### Build and run local images

```bash
docker compose --profile services up -d --build
```

### Run one service from source

Start infrastructure first:

```bash
docker compose up -d
```

Then run the service you are changing:

```bash
cd platform-ingestion
mvn spring-boot:run -Dspring-boot.run.profiles=local

cd platform-agent
mvn spring-boot:run -Dspring-boot.run.profiles=local

cd platform-portal/frontend
npm install
npm run dev
```

## Submitting test results

### HTTP upload

```bash
curl -X POST http://localhost:8081/api/v1/results/ingest \
  -F "file=@target/surefire-reports/TEST-example.xml" \
  -F "format=JUNIT_XML" \
  -F "teamId=my-team" \
  -F "projectId=my-project"
```

Supported formats:

`JUNIT_XML`, `CUCUMBER_JSON`, `TESTNG`, `ALLURE`, `PLAYWRIGHT`, `NEWMAN`, `PLATFORM_NATIVE`, `K6`, `GATLING`, `JMETER`.

### Playwright adapter

```bash
# .npmrc
@ndviet:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=YOUR_GITHUB_TOKEN

npm install @ndviet/adapter-playwright
```

```typescript
// playwright.config.ts
export default defineConfig({
  reporter: [
    ['list'],
    ['@ndviet/adapter-playwright', {
      endpoint: process.env.PLATFORM_URL,
      apiKey: process.env.PLATFORM_API_KEY,
      teamId: 'my-team',
      projectId: 'my-project',
    }],
  ],
})
```

### k6 adapter

```javascript
// load-test.js
import { platformHandleSummary } from './platform-k6-adapter.js'
export { platformHandleSummary as handleSummary }
```

```bash
k6 run \
  -e PLATFORM_URL=http://localhost:8081 \
  -e PLATFORM_API_KEY=plat_xxxx \
  -e PLATFORM_TEAM_ID=my-team \
  -e PLATFORM_PROJECT_ID=my-project \
  load-test.js
```

### Java adapter and testkit

Use `platform-adapter-java` for existing suites that only need auto-publishing. Use `platform-testkit-java` for rich step tracking, tracing, retries, Playwright diagnostics, and native JSON publishing.

```xml
<dependency>
  <groupId>org.ndviet</groupId>
  <artifactId>platform-testkit-java</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

See:

- `platform-adapters/java/USAGE.md`
- `platform-testkit/java/USAGE.md`
- `platform-adapters/js/playwright/README.md`
- `platform-adapters/js/k6/README.md`

## Main APIs

### Ingestion and platform management

| Area | Endpoints |
|---|---|
| Results | `POST /api/v1/results/ingest` |
| Coverage | `POST /api/v1/coverage` |
| Teams | `GET/POST /api/v1/teams`, `PUT/DELETE /api/v1/teams/{id}` |
| Projects | `GET/POST /api/v1/projects`, `PUT/DELETE /api/v1/projects/{id}` |
| Executions | `GET /api/v1/projects/{projectId}/executions`, `GET /api/v1/executions/{runId}` |
| Requirements | `GET /api/v1/projects/{projectId}/requirements`, `GET /api/v1/projects/{projectId}/requirements/stats` |
| Integrations | `GET/POST /api/v1/projects/{projectId}/integrations`, `DELETE /api/v1/projects/{projectId}/integrations/{configId}` |
| API keys | `POST /api/v1/api-keys`, `GET /api/v1/api-keys`, `DELETE /api/v1/api-keys/{id}` |
| Test suites | `GET/POST /api/v1/projects/{projectId}/test-suites` |
| Test cases | `GET/POST /api/v1/projects/{projectId}/test-cases`, review and automation actions under `/test-cases/{tcId}` |
| Manual test runs | `GET/POST /api/v1/projects/{projectId}/test-runs`, execution updates under `/test-runs/{runId}` |

### Analytics

| Area | Endpoints |
|---|---|
| Flakiness | `GET /api/v1/analytics/{projectId}/flakiness`, `POST /api/v1/analytics/{projectId}/flakiness/recompute` |
| Trends | `GET /api/v1/analytics/{projectId}/trends/pass-rate`, `/trends/mttr`, `/trends/duration` |
| Quality gates | `GET /api/v1/analytics/{projectId}/quality-gate`, `GET /api/v1/projects/{projectId}/quality-gate/ci` |
| Test Impact Analysis | `GET /api/v1/analytics/{projectId}/impact`, `GET /api/v1/analytics/{projectId}/impact/summary` |
| TIA trends | `GET /api/v1/analytics/tia/summary`, project TIA trend endpoints under `/api/v1/analytics/{projectId}/tia/*` |
| Alerts | `GET /api/v1/alerts/projects/{projectId}`, `GET /api/v1/alerts/org` |
| Release report | `GET /api/v1/projects/{projectId}/release-report` |
| Logs | `GET /api/v1/logs/runs/{runId}`, `GET /api/v1/logs/tests/{testId}`, `GET /api/v1/logs/runs` |

### AI and Agent Hub

| Area | Endpoints |
|---|---|
| Failure analyses | `GET /api/v1/projects/{projectId}/analyses`, `POST /api/v1/projects/{projectId}/results/{resultId}/analyse` |
| AI settings | `GET/PUT /api/v1/ai/settings`, `POST /api/v1/ai/settings/test` |
| Agent workflows | `POST /hub/workflows`, `GET /hub/workflows`, `GET /hub/workflows/{workflowId}` |
| Node registry | `POST /hub/nodes/register`, `POST /hub/nodes/{nodeId}/heartbeat`, `GET /hub/nodes` |
| Test generation | `POST /hub/test-cases/{projectId}/generate`, `POST /hub/test-cases/{projectId}/{testCaseId}/generate-automation` |
| Impact analysis | `GET/POST /hub/impact/{projectId}`, `GET /hub/impact/{projectId}/prs` |
| Flaky healing | `POST /hub/healing/{projectId}/trigger` |
| Review decisions | `GET /hub/reviews`, `POST /hub/reviews/{reviewRequestId}/decide` |
| Webhooks | `/hub/webhooks/github`, `/hub/webhooks/jira`, `/hub/webhooks/linear`, `/hub/webhooks/slack/interactions` |

## Portal features

The React portal is served by `platform-portal` and backed by `/api/portal/**`.

| Route | Purpose |
|---|---|
| `/` | Organization summary, teams, projects, recent quality signal |
| `/projects/:projectId` | Project quality detail |
| `/projects/:projectId/requirements` | Requirement inventory and stats |
| `/projects/:projectId/pr-analyses` | Agent PR analysis history |
| `/projects/:projectId/impact-analyses` | AI impact analysis workflow UI |
| `/projects/:projectId/settings` | Project, team, and integration configuration |
| `/projects/:projectId/test-cases` | Test suites, manual test cases, review, AI generation, automation trigger |
| `/projects/:projectId/test-runs` | Manual test runs and execution updates |
| `/projects/:projectId/flaky-tests` | Flakiness recompute and healing trigger |
| `/runs/:runId` | Ingested run detail |
| `/alerts` | Alert history |
| `/settings/api-keys` | Ingestion API key management |
| `/settings/ai` | Claude/OpenAI settings and connectivity test |

## Observability

| Signal | Where |
|---|---|
| Dashboards | Grafana at http://localhost:3000 |
| Metrics | Prometheus at http://localhost:9090 |
| Service logs | Loki through Grafana Explore |
| Test execution logs | OpenSearch indices `test_execution_logs-*` |
| k6 performance | Prometheus (remote write) plus Grafana dashboards |
| TIA trends | Grafana TIA dashboard and analytics APIs |

## Database migrations

Flyway migrations live in `platform-core/src/main/resources/db/migration/` and currently run from `V1` through `V44`.

They run automatically in Docker through the `db-migrate` service and are also available to Spring Boot services through the shared core module.

```bash
docker compose run --rm db-migrate
```

## CI templates

The repository includes Test Impact Analysis helper templates under `platform-ci-templates/`:

- GitHub Actions: `platform-ci-templates/github-actions/test-impact-analysis.yml`
- Azure DevOps: `platform-ci-templates/azure-devops/test-impact-analysis.yml`
- Jenkins: `platform-ci-templates/jenkins/PlatformTIA.groovy`

These templates calculate changed files, call the analytics TIA endpoints, emit Maven/Gradle test filters, and can fall back to full-suite execution based on risk.

## Project structure

```text
test-automation-platform/
|-- platform-common/              # Shared platform, agent, integration, and storage contracts
|-- platform-storage/             # Blob storage auto-config and implementations
|-- platform-core/                # JPA domain, repositories, Flyway migrations
|-- platform-ingestion/           # Result parsers, REST ingest, management, TCM APIs
|-- platform-analytics/           # Flakiness, trends, quality gates, TIA, alerts, log search
|-- platform-integration/         # Jira ticket lifecycle service
|-- platform-integration-config/  # Integration adapter registry and initial adapters
|-- platform-ai/                  # Failure classification and AI settings
|-- platform-agent/               # Agent Hub, workflows, nodes, webhooks, review gateway
|-- platform-portal/              # Spring Boot BFF and React SPA
|-- platform-testkit/             # Rich Java test instrumentation
|-- platform-adapters/            # Java, Playwright, and k6 adapters
|-- platform-ci-templates/        # TIA templates for CI systems
|-- infrastructure/               # Grafana, Prometheus, Loki, Promtail, Logstash, OpenSearch
|-- helm/                         # Kubernetes chart
`-- docker-compose.yml            # Full local stack
```
