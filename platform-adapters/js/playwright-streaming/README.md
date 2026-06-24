# @ndviet/playwright-streaming-reporter

Playwright reporter that streams each test result to the Test Automation Platform the moment it finishes — no waiting for the entire suite to complete. Results appear in the portal in real time.

## Installation

```bash
npm install @ndviet/playwright-streaming-reporter
```

**Peer dependency:** `@playwright/test >= 1.40.0`

## Setup

Add the reporter to your `playwright.config.ts`:

```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  reporter: [
    ['list'],
    ['@ndviet/playwright-streaming-reporter', {
      endpoint:    process.env.PLATFORM_URL,
      apiKey:      process.env.PLATFORM_API_KEY,
      orgSlug:     'my-org',
      projectSlug: 'my-project',
    }],
  ],
});
```

## Configuration

### Required options

| Option | Type | Description |
|---|---|---|
| `endpoint` | `string` | Base URL of the platform ingestion service, e.g. `http://localhost:8081` |
| `orgSlug` | `string` | Organisation slug (first path segment of your portal URL) |
| `projectSlug` | `string` | Project slug (second path segment of your portal URL) |

### Optional options

| Option | Type | Default | Description |
|---|---|---|---|
| `apiKey` | `string` | — | API key sent as the `X-API-Key` header |
| `teamSlug` | `string` | — | Attributes the run to a specific team |
| `areaSlug` | `string` | — | Attributes the run to an area or component |
| `branch` | `string` | Auto-detected | Git branch name |
| `environment` | `string` | `ci` / `local` | Deployment environment label |
| `commitSha` | `string` | Auto-detected | Git commit SHA |
| `ciRunUrl` | `string` | Auto-detected | Link to the CI job |
| `ciProvider` | `string` | Auto-detected | CI provider slug, e.g. `github`, `gitlab` |
| `workflow` | `string` | Auto-detected | Workflow / pipeline name |
| `trigger` | `string` | Auto-detected | Event that triggered the run, e.g. `push`, `pull_request` |
| `prNumber` | `number` | Auto-detected | Pull request / merge request number |
| `runNumber` | `string` | Auto-detected | Sequential run number within the CI project |
| `runAttempt` | `number` | Auto-detected | Re-run attempt counter (1-based) |
| `verbose` | `boolean` | `false` | Log a line to stdout for every test result streamed |
| `timeoutMs` | `number` | `15000` | HTTP request timeout in milliseconds |

## Difference from `@ndviet/adapter-playwright`

| | `adapter-playwright` | `playwright-streaming-reporter` |
|---|---|---|
| When results are sent | After the full suite finishes | As each test finishes |
| Portal visibility | End of run | Real-time |
| Trace upload | No | Yes (if Playwright trace is enabled) |
| Heartbeat (long runs) | No | Yes (every 5 minutes) |

Use the streaming reporter when you want live visibility into running test suites or when suites run for more than a few minutes.

## Trace upload

If Playwright is configured to capture traces, the reporter uploads them automatically:

```typescript
// playwright.config.ts
export default defineConfig({
  use: {
    trace: 'retain-on-failure', // or 'on', 'on-first-retry'
  },
  reporter: [
    ['@ndviet/playwright-streaming-reporter', { /* ... */ }],
  ],
});
```

## Test Impact Analysis annotations

Tag tests with the code they cover:

```typescript
import { test } from '@playwright/test';

test('payment flow', async ({ page }) => {
  test.info().annotations.push({ type: 'tia:file',      description: 'src/services/payment.ts' });
  test.info().annotations.push({ type: 'tia:component', description: 'CheckoutForm' });
  test.info().annotations.push({ type: 'tia:route',     description: '/api/checkout' });
  // ...
});
```

Add arbitrary labels for filtering in the portal:

```typescript
test.info().annotations.push({ type: 'label:owner', description: 'payments-team' });
test.info().annotations.push({ type: 'label:jira',  description: 'PAY-1234' });
```

## Conditional activation (recommended)

Activate the reporter only when `PLATFORM_URL` is set so local runs without a `.env` are unaffected:

```typescript
// playwright.config.ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  reporter: [
    ['list'],
    ['html', { open: 'on-failure' }],
    ['junit', { outputFile: 'test-results/junit-report.xml' }],
    ['json', { outputFile: 'playwright-results.json' }],
    // Real-time streaming to the Test Automation Platform.
    // Activated when PLATFORM_URL is set in .env — safe to omit locally.
    ...(process.env.PLATFORM_URL
      ? ([
          [
            '@ndviet/playwright-streaming-reporter',
            {
              endpoint:    process.env.PLATFORM_URL,
              apiKey:      process.env.PLATFORM_API_KEY,
              orgSlug:     process.env.PLATFORM_ORG_SLUG,
              projectSlug: process.env.PLATFORM_PROJECT_SLUG,
              teamSlug:    process.env.PLATFORM_TEAM_SLUG,
              areaSlug:    process.env.PLATFORM_AREA_SLUG,
              environment: (process.env.TEST_ENV ?? 'local').toLowerCase(),
              verbose:     process.env.PLATFORM_VERBOSE === 'true',
            },
          ],
        ] as Parameters<typeof defineConfig>[0]['reporter'])
      : []),
  ],
});
```

`.env` for local development (reporter stays inactive when this file is absent):

```dotenv
PLATFORM_URL=http://localhost:8081
PLATFORM_API_KEY=plat_xxxx
PLATFORM_ORG_SLUG=my-org
PLATFORM_PROJECT_SLUG=my-project
PLATFORM_TEAM_SLUG=frontend
PLATFORM_AREA_SLUG=checkout
TEST_ENV=local
# PLATFORM_VERBOSE=true
```

## CI example (GitHub Actions)

```yaml
- name: Run Playwright tests
  run: npx playwright test
  env:
    PLATFORM_URL: ${{ secrets.PLATFORM_URL }}
    PLATFORM_API_KEY: ${{ secrets.PLATFORM_API_KEY }}
    PLATFORM_ORG_SLUG: my-org
    PLATFORM_PROJECT_SLUG: my-project
    PLATFORM_TEAM_SLUG: frontend
    TEST_ENV: ci
```

## CI auto-detection

Branch, commit SHA, CI run URL, workflow name, trigger, and PR number are automatically detected for:
GitHub Actions · GitLab CI · Jenkins · CircleCI · Azure DevOps · Bitbucket Pipelines · Travis CI · Buildkite · TeamCity

## Requirements

- Node.js >= 18.0.0
- `@playwright/test` >= 1.40.0

## License

MIT
