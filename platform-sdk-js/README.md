# @platform/playwright-reporter

Custom Playwright reporter that automatically publishes test results to the Test Automation Platform after every run. Zero test-code changes required — configure once in `playwright.config.ts`.

## How it works

1. Hooks into Playwright's `Reporter` interface
2. Collects test results from the Suite tree after all retries complete
3. On run completion, serialises results as Playwright JSON and POSTs to the platform ingestion endpoint
4. Never fails your test run — errors are logged and swallowed

## Installation

```bash
npm install @platform/playwright-reporter
```

## Configuration

Add the reporter to your `playwright.config.ts`:

```typescript
import { defineConfig } from '@playwright/test'

export default defineConfig({
  reporter: [
    ['list'],                          // keep your existing reporters
    ['html', { open: 'never' }],
    ['@platform/playwright-reporter', {
      endpoint:   'http://platform-ingestion:8081',   // ingestion service URL
      apiKey:     process.env.PLATFORM_API_KEY,        // from env, not hardcoded
      teamId:     'team-frontend',
      projectId:  'proj-checkout',
      // branch, commitSha, ciRunUrl — auto-detected from CI env vars
      // environment defaults to 'ci' in CI, 'local' otherwise
    }],
  ],
})
```

### Options

| Option         | Type      | Required | Default                        | Description                                  |
|----------------|-----------|----------|--------------------------------|----------------------------------------------|
| `endpoint`     | `string`  | ✓        | —                              | Base URL of the ingestion service            |
| `apiKey`       | `string`  | —        | —                              | `X-API-Key` header value                     |
| `teamId`       | `string`  | ✓        | —                              | Team slug registered in the platform         |
| `projectId`    | `string`  | ✓        | —                              | Project slug registered in the platform      |
| `branch`       | `string`  | —        | Auto-detected from CI env      | Git branch name                              |
| `environment`  | `string`  | —        | `'ci'` in CI, `'local'` local  | Environment label                            |
| `commitSha`    | `string`  | —        | Auto-detected from CI env      | Git commit SHA                               |
| `ciRunUrl`     | `string`  | —        | Auto-detected from CI env      | Link to the CI build                         |
| `printSummary` | `boolean` | —        | `true`                         | Print a `runId` line to stdout after publish |
| `timeoutMs`    | `number`  | —        | `30000`                        | HTTP POST timeout in milliseconds            |

### Auto-detected CI variables

Branch, commit SHA, and CI run URL are automatically read from these CI environments — no configuration needed:

| CI Provider       | Branch                   | Commit SHA           |
|-------------------|--------------------------|----------------------|
| GitHub Actions    | `GITHUB_REF_NAME`        | `GITHUB_SHA`         |
| GitLab CI         | `CI_COMMIT_REF_NAME`     | `CI_COMMIT_SHA`      |
| Jenkins           | `GIT_BRANCH`             | `GIT_COMMIT`         |
| CircleCI          | `CIRCLE_BRANCH`          | `CIRCLE_SHA1`        |
| Azure DevOps      | `BUILD_SOURCEBRANCH`     | `BUILD_SOURCEVERSION`|
| Bitbucket         | `BITBUCKET_BRANCH`       | `BITBUCKET_COMMIT`   |
| Travis CI         | `TRAVIS_BRANCH`          | `TRAVIS_COMMIT`      |
| Buildkite         | `BUILDKITE_BRANCH`       | `BUILDKITE_COMMIT`   |

## GitHub Actions example

```yaml
- name: Run Playwright tests
  run: npx playwright test
  env:
    PLATFORM_API_KEY: ${{ secrets.PLATFORM_API_KEY }}
```

No other changes needed — branch, commit SHA, and run URL are detected automatically.

## Retry handling

Playwright retries are correctly handled: only the **final** result of each test (after all retry attempts) is published. A test that passed on retry is reported as `passed`.

## Local development

```bash
npm run build          # compile TypeScript → dist/
npm run build:watch    # watch mode
```
