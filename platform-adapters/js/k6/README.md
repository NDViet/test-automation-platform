# @ndviet/adapter-k6

K6 `handleSummary` adapter — publishes load-test results and performance metrics
(p90/p95 response times, throughput, error rate, VU count) to the Test Automation Platform.

## Quick start

Copy `src/index.js` into your K6 project directory (or use the bundled `dist/platform-k6-adapter.js`).

### Option A — Drop-in handleSummary

```javascript
// load-test.js
import http from 'k6/http';
import { sleep } from 'k6';
import { platformHandleSummary } from './platform-k6-adapter.js';

export const options = {
  vus: 50,
  duration: '2m',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  http.get('https://api.example.com/health');
  sleep(1);
}

// Publish results to the platform automatically
export { platformHandleSummary as handleSummary };
```

### Option B — Wrap your own handleSummary

```javascript
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { publishToPlatform } from './platform-k6-adapter.js';

export function handleSummary(data) {
  publishToPlatform(data);   // fire-and-forget
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data),
  };
}
```

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `PLATFORM_URL` | Yes | Base URL, e.g. `https://platform.internal` |
| `PLATFORM_API_KEY` | Yes | API key (`X-API-Key` header) |
| `PLATFORM_PROJECT_ID` | Yes | Project slug (auto-created on first run) |
| `PLATFORM_TEAM_ID` | Yes | Team slug (auto-created on first run) |
| `PLATFORM_BRANCH` | No | Git branch (auto-detected from CI) |
| `PLATFORM_ENVIRONMENT` | No | Environment label: `staging`, `production` |
| `PLATFORM_COMMIT_SHA` | No | Commit SHA (auto-detected from CI) |
| `PLATFORM_SUITE_NAME` | No | Load-test scenario label |
| `PLATFORM_CI_RUN_URL` | No | Link to CI job (auto-detected for GitHub Actions) |

## CI auto-detection

Branch and commit SHA are automatically read from CI environment variables for:
GitHub Actions, GitLab CI, Jenkins, CircleCI, Azure DevOps, Bitbucket, Travis CI, Buildkite.

## Run with environment variables

```bash
k6 run \
  -e PLATFORM_URL=https://platform.internal \
  -e PLATFORM_API_KEY=plat_xxxx \
  -e PLATFORM_PROJECT_ID=payment-service \
  -e PLATFORM_TEAM_ID=payments-team \
  -e PLATFORM_ENVIRONMENT=staging \
  -e PLATFORM_SUITE_NAME=checkout-flow \
  load-test.js
```

## What gets published

The adapter sends the full K6 `--summary-export` JSON to the platform ingestion API
(`POST /api/v1/results/ingest` with `format=K6`).

The platform ingestion service extracts:

| Signal | Source in K6 JSON |
|---|---|
| Check pass/fail | `root_group.checks[].passes` / `.fails` |
| Avg response time | `metrics.http_req_duration.avg` |
| p90 / p95 / p99 | `metrics.http_req_duration.p(90)` / `p(95)` / `p(99)` |
| Throughput (req/s) | `metrics.http_reqs.rate` |
| Error rate | `metrics.http_req_failed.fails / (passes + fails)` |
| Peak VUs | `metrics.vus_max.value` |

All signals are stored in the `performance_metrics` table and visible in the
**K6 Performance Dashboard** in Grafana.
