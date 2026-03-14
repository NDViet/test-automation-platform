import { defineConfig, devices } from '@playwright/test'

/**
 * Example Playwright config showing platform reporter integration.
 *
 * 1. npm install @platform/playwright-reporter
 * 2. Set PLATFORM_API_KEY env var (get it from the portal: Settings → API Keys)
 * 3. Update teamId / projectId to match your project in the platform
 * 4. Run: npx playwright test
 *
 * Results will appear in the platform portal within seconds of the run finishing.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 4 : undefined,

  reporter: [
    // Keep standard reporters
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],

    // Platform reporter — publishes to the quality dashboard
    ['@platform/playwright-reporter', {
      endpoint:  process.env['PLATFORM_ENDPOINT'] ?? 'http://localhost:8081',
      apiKey:    process.env['PLATFORM_API_KEY'],
      teamId:    'team-frontend',           // ← change to your team slug
      projectId: 'proj-my-app',            // ← change to your project slug
      // branch, commitSha, ciRunUrl auto-detected from CI environment
      // environment defaults to 'ci' in CI, 'local' on your machine
    }],
  ],

  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:3000',
    trace: 'on-first-retry',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox',  use: { ...devices['Desktop Firefox'] } },
  ],
})
