# Test Automation Platform

Cross-team quality intelligence platform that unifies test results, detects flakiness, classifies failures with AI, and surfaces organisation-wide quality signals — without changing a line of test code.

## Architecture

```
                        ┌──────────────────────────────────────────┐
   Test Frameworks       │             Platform Services            │
   ─────────────────     │  ─────────────────────────────────────── │
   JUnit5 / TestNG       │  platform-ingestion  :8081               │
   Cucumber / Allure  ─► │  platform-analytics  :8082  ──► Grafana  │
   Playwright / Newman   │  platform-integration :8083  ──► JIRA    │
   K6 / Gatling / JMeter │  platform-ai         :8084  ──► Claude   │
   (via HTTP POST)       │  platform-portal     :8085  (React SPA)  │
                         └──────────────────────────────────────────┘
                                      │          │
                              PostgreSQL 17   Kafka 4.2 (KRaft)
                              OpenSearch 3    Redis 8
```

### Maven modules

| Module | Port | Role |
|---|---|---|
| `platform-common` | — | Shared DTOs (`UnifiedTestResult`, `TestCaseResultDto`, `PerformanceMetricsDto`) |
| `platform-core` | — | JPA entities, Flyway migrations (V1–V21), repositories |
| `platform-ingestion` | 8081 | Parsers for JUnit XML, Cucumber, TestNG, Allure, Playwright, Newman, K6, Gatling, JMeter; Kafka producer |
| `platform-analytics` | 8082 | Flakiness scoring, quality gates, release reports, Test Impact Analysis, alerts |
| `platform-integration` | 8083 | JIRA/Linear/GitHub Issues ticket lifecycle |
| `platform-ai` | 8084 | Claude-powered failure classification, similarity search, nightly batch |
| `platform-portal` | 8085 | Spring Boot BFF + React 19 SPA |
| `platform-testkit` | — | JUnit5 extension, `@AffectedBy`, `@Retryable`, `PlatformBaseTest`, native publisher |
| `platform-adapters` | — | Zero-code adapters: `@ndviet/adapter-playwright`, `@ndviet/adapter-k6` (npm) |

### JS adapters (npm — `@ndviet` scope)

| Package | Framework | Published to |
|---|---|---|
| `@ndviet/adapter-playwright` | Playwright | GitHub Packages |
| `@ndviet/adapter-k6` | K6 | GitHub Packages |

## Prerequisites

| Tool | Version |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| Docker + Docker Compose | 27+ |
| Node.js | 20+ (adapter development only) |

## Build

### All Java modules

```bash
mvn -B clean package -DskipTests
```

### Specific services only (faster)

```bash
mvn -B clean package -DskipTests \
  -pl platform-ingestion,platform-analytics,platform-integration,platform-ai,platform-portal \
  -am
```

### Run tests

```bash
mvn test
```

### Build JS adapters

```bash
# Playwright adapter (TypeScript → dist/)
cd platform-adapters/js/playwright
npm ci
npm run build

# K6 adapter (ESM bundle → dist/platform-k6-adapter.js)
cd platform-adapters/js/k6
npm install --ignore-scripts
npm run bundle
```

## Local deployment

### Step 1 — Start infrastructure

Start all backing services (PostgreSQL, Kafka, Redis, OpenSearch, Prometheus, Grafana, Loki):

```bash
docker compose up -d
```

This also runs Flyway migrations automatically via the `db-migrate` one-shot container.

### Step 2 — Start platform services

Platform services are gated behind the `services` profile so they don't start until their Docker images exist:

```bash
# Build all service images first
docker compose --profile services build

# Start everything (infrastructure + services)
docker compose --profile services up -d
```

Or build and start in one command:

```bash
docker compose --profile services up -d --build
```

### Step 3 — Verify

```bash
docker compose ps
```

| Service | URL | Default credentials |
|---|---|---|
| Platform Portal | http://localhost:8085 | — |
| Ingestion API | http://localhost:8081/swagger-ui.html | — |
| Analytics API | http://localhost:8082/swagger-ui.html | — |
| AI API | http://localhost:8084/swagger-ui.html | — |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | — |
| OpenSearch | http://localhost:9200 | — |
| Kafka | localhost:9092 | — |
| PostgreSQL | localhost:5432 db=platform | platform / platform |

### Environment variables (optional)

```bash
# AI service — Claude (default) or OpenAI
export ANTHROPIC_API_KEY=sk-ant-...
export AI_PROVIDER=claude        # or: openai

# OpenAI fallback
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-4o

# API key auth (disabled by default for local dev)
# Set to true in production and create keys via POST /api/v1/api-keys
export API_KEY_AUTH_ENABLED=false
```

Copy `.env.example` (if present) to `.env` — Docker Compose picks it up automatically.

### Run services locally (without Docker)

Useful when iterating on a single service. Start infrastructure first (Step 1), then:

```bash
# platform-ingestion
cd platform-ingestion
mvn spring-boot:run -Dspring-boot.run.profiles=local

# platform-analytics
cd platform-analytics
mvn spring-boot:run -Dspring-boot.run.profiles=local

# platform-portal (frontend dev server with hot-reload)
cd platform-portal/frontend
npm install
npm run dev    # proxies API calls to :8085
```

Default `local` profile connects to `localhost:5432`, `localhost:9092`, etc.

### Stop everything

```bash
docker compose --profile services down

# Remove volumes (wipes database and Kafka data)
docker compose --profile services down -v
```

## Submitting test results

### HTTP POST (zero code change)

```bash
# JUnit XML
curl -X POST http://localhost:8081/api/v1/results/ingest \
  -F "file=@target/surefire-reports/TEST-*.xml" \
  -F "format=JUNIT_XML" \
  -F "teamId=my-team" \
  -F "projectId=my-project"

# Playwright JSON
curl -X POST http://localhost:8081/api/v1/results/ingest \
  -F "file=@playwright-report/results.json" \
  -F "format=PLAYWRIGHT" \
  -F "teamId=my-team" \
  -F "projectId=my-project"

# K6 summary
curl -X POST http://localhost:8081/api/v1/results/ingest \
  -F "file=@summary.json" \
  -F "format=K6" \
  -F "teamId=my-team" \
  -F "projectId=my-project"
```

Supported formats: `JUNIT_XML`, `CUCUMBER_JSON`, `TESTNG_XML`, `ALLURE_JSON`, `PLAYWRIGHT`, `NEWMAN_JSON`, `K6`, `GATLING`, `JMETER_JTL`, `PLATFORM_NATIVE`

### Playwright adapter (npm)

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
      endpoint:  process.env.PLATFORM_URL,
      apiKey:    process.env.PLATFORM_API_KEY,
      teamId:    'my-team',
      projectId: 'my-project',
    }],
  ],
});
```

### K6 adapter

Download `dist/platform-k6-adapter.js` from the `@ndviet/adapter-k6` package and place it alongside your script:

```javascript
// load-test.js
import { platformHandleSummary } from './platform-k6-adapter.js';
export { platformHandleSummary as handleSummary };
```

```bash
k6 run \
  -e PLATFORM_URL=http://localhost:8081 \
  -e PLATFORM_API_KEY=plat_xxxx \
  -e PLATFORM_TEAM_ID=my-team \
  -e PLATFORM_PROJECT_ID=my-project \
  load-test.js
```

### JUnit5 / TestNG SDK (zero-config)

```xml
<dependency>
  <groupId>org.ndviet</groupId>
  <artifactId>platform-testkit</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

Extend `PlatformBaseTest` — results are published automatically after each test.

## Observability

| Signal | Where |
|---|---|
| Dashboards | Grafana → http://localhost:3000 (pre-provisioned) |
| Metrics | Prometheus → http://localhost:9090 |
| Logs | Loki (via Grafana Explore) |
| Traces | Jaeger (if enabled) |
| K6 performance | Grafana → K6 Performance Dashboard (InfluxDB datasource) |
| TIA trends | Grafana → TIA Dashboard |

## CI / GitHub Actions

| Workflow | Trigger | What it does |
|---|---|---|
| `build-docker.yml` | push `master`, tags `v*` | Build JARs → push Docker images to GHCR |
| `publish-maven.yml` | push `master`, tags `v*` | Publish `platform-testkit` to GitHub Packages (Maven) |
| `publish-adapter-playwright.yml` | push `platform-adapters/js/playwright/**` | Publish `@ndviet/adapter-playwright` to GitHub Packages (npm) |
| `publish-adapter-k6.yml` | push `platform-adapters/js/k6/**` | Publish `@ndviet/adapter-k6` to GitHub Packages (npm) |

Required secrets: `GH_PACKAGES_TOKEN` (PAT with `write:packages`), `ANTHROPIC_API_KEY`.

## Database migrations

Flyway migrations live in `platform-core/src/main/resources/db/migration/` (V1–V21).
They run automatically on startup — both via the `db-migrate` Docker Compose container and when any Spring Boot service starts.

To run manually:

```bash
docker compose run --rm db-migrate
```

## Project structure

```
test-automation-platform/
├── platform-common/          # Shared DTOs
├── platform-core/            # JPA domain + migrations
├── platform-ingestion/       # Parsers + REST ingest API (port 8081)
├── platform-analytics/       # Flakiness, TIA, quality gates (port 8082)
├── platform-integration/     # JIRA ticket lifecycle (port 8083)
├── platform-ai/              # Claude failure classification (port 8084)
├── platform-portal/          # BFF + React SPA (port 8085)
├── platform-testkit/         # JUnit5/TestNG SDK
├── platform-adapters/
│   └── js/
│       ├── playwright/       # @ndviet/adapter-playwright (npm)
│       └── k6/               # @ndviet/adapter-k6 (npm)
├── platform-ci-templates/    # GitHub Actions / GitLab / Jenkins / Azure DevOps
├── infrastructure/
│   ├── grafana/              # Dashboards + provisioning
│   ├── prometheus/           # Scrape config + alert rules
│   ├── loki/                 # Log aggregation config
│   ├── logstash/             # Log shipping pipeline
│   └── promtail/             # Docker log collector
├── helm/                     # Helm chart for Kubernetes
└── docker-compose.yml        # Full local stack
```
