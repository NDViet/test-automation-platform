export interface StreamingReporterOptions {
  /** Base URL of the platform ingestion service, e.g. http://localhost:8081 */
  endpoint: string
  /** X-API-Key header value. Omit if auth is disabled. */
  apiKey?: string
  /**
   * Organisation slug — the FIRST path segment in the portal URL.
   * Example: portal URL is `/my-org/my-project/…` → set `orgSlug: 'my-org'`
   */
  orgSlug: string
  /**
   * Project slug — the SECOND path segment in the portal URL.
   * Example: portal URL is `/my-org/my-project/…` → set `projectSlug: 'my-project'`
   */
  projectSlug: string
  /**
   * Team slug — optional. Attributes this run to a specific team within the project.
   * Find the slug in the portal under Teams & Structure → copy the team's slug chip.
   * If omitted, the run is attributed to the project but no specific team.
   */
  teamSlug?: string
  /**
   * Area slug — optional. Attributes this run to an area or component (e.g. "frontend", "payment-api").
   * For ADO users: find it in Teams & Structure → copy the area slug chip.
   * For non-ADO users: any meaningful label you choose.
   */
  areaSlug?: string
  /** Git branch. Auto-detected from CI env vars if omitted. */
  branch?: string
  /** Deployment environment label. Defaults to 'ci' in CI, 'local' otherwise. */
  environment?: string
  /** Git commit SHA. Auto-detected from CI env vars if omitted. */
  commitSha?: string
  /** CI run URL. Auto-detected from CI env vars if omitted. */
  ciRunUrl?: string
  /** CI provider slug ("github", "gitlab", "circleci", …). Auto-detected if omitted. */
  ciProvider?: string
  /** Workflow / pipeline name. Auto-detected from CI env vars if omitted. */
  workflow?: string
  /** Trigger event name ("push", "pull_request", "schedule", …). Auto-detected if omitted. */
  trigger?: string
  /** Pull request / merge request number. Auto-detected from CI env vars if omitted. */
  prNumber?: number
  /** Sequential run number within the CI project. Auto-detected if omitted. */
  runNumber?: string
  /** Re-run attempt counter (1-based). Auto-detected from GitHub Actions if omitted. */
  runAttempt?: number
  /** Log a line to stdout for every test result pushed. Default: false. */
  verbose?: boolean
  /** HTTP request timeout in milliseconds. Default: 15000. */
  timeoutMs?: number
}

// ── Wire types for the streaming API ────────────────────────────────────────

export interface StartRunRequest {
  orgSlug: string
  projectSlug: string
  teamSlug?: string
  areaSlug?: string
  branch?: string
  environment?: string
  commitSha?: string
  ciRunUrl?: string
  ciProvider?: string
  workflow?: string
  trigger?: string
  prNumber?: number
  runNumber?: string
  runAttempt?: number
}

export interface StartRunResponse {
  runId: string
}

export interface TestEventRequest {
  testId: string
  displayName: string
  suiteName: string
  tags: string[]
  status: 'passed' | 'failed' | 'skipped' | 'timedOut' | 'interrupted'
  durationMs: number
  failureMessage?: string
  stackTrace?: string
  retryCount: number

  // Tier-1: test location and execution context
  /** Spec file path relative to the project root, e.g. "tests/checkout/payment.spec.ts". */
  specFile?: string
  /** Playwright project name: "chromium", "firefox", "webkit", "Mobile Chrome", etc. */
  browser?: string
  /** Playwright built-in + user-defined non-tag annotations (fixme, slow, fail, skip, custom). */
  annotations?: { type: string; description?: string }[]

  // Tier-2: visual artifacts and parallelism
  hasScreenshot?: boolean
  hasVideo?: boolean
  /** Playwright worker slot index (0-based). */
  workerIndex?: number

  // TIA: Test Impact Analysis declarations (set via tia.covers() / tia.component() / tia.route())
  coveredFiles?: string[]
  coveredComponents?: string[]
  coveredRoutes?: string[]
  /** Arbitrary key-value metadata (owner, jira, team, etc.) set via tia.label(). */
  labels?: { key: string; value: string }[]
}

export interface FinishRunResponse {
  runId: string
  status: string
  testCount: number
  processingUrl: string
}
