export interface PlatformReporterOptions {
  /** Base URL of the platform ingestion service. e.g. http://localhost:8081 */
  endpoint: string
  /** API key for authentication (X-API-Key header). Skip if auth is disabled. */
  apiKey?: string
  /** Team identifier registered in the platform (e.g. 'team-frontend'). */
  teamId: string
  /** Project identifier registered in the platform (e.g. 'proj-checkout'). */
  projectId: string
  /**
   * Git branch name. Auto-detected from CI environment variables if omitted.
   * Priority: option → CI env → 'unknown'
   */
  branch?: string
  /**
   * Deployment environment label. Defaults to 'ci' when running in CI, 'local' otherwise.
   */
  environment?: string
  /** Git commit SHA. Auto-detected from CI env if omitted. */
  commitSha?: string
  /** Link to the CI run (e.g. GitHub Actions URL). Auto-detected from CI env if omitted. */
  ciRunUrl?: string
  /** Print a summary line to stdout after publishing. Default: true. */
  printSummary?: boolean
  /** Timeout for the HTTP POST in milliseconds. Default: 30000. */
  timeoutMs?: number
  /**
   * Optional static list of module paths / class names this entire project covers.
   * Applied to every test when per-test coveredModules are not specified.
   * Supports globs: 'src/services/payment*'
   * Used for Test Impact Analysis — the platform uses this to select tests on code changes.
   */
  coveredModules?: string[]
}

// Internal — Playwright JSON report structure (matches server-side PlaywrightParser)
export interface PlaywrightReport {
  suites: ReportSuite[]
}

export interface ReportSuite {
  title: string
  file: string
  suites?: ReportSuite[]
  specs?: ReportSpec[]
}

export interface ReportSpec {
  title: string
  tests: ReportTest[]
  /** Test Impact Analysis: module paths / class names this test covers. */
  coveredModules?: string[]
}

export interface ReportTest {
  results: ReportResult[]
}

export interface ReportResult {
  status: 'passed' | 'failed' | 'skipped' | 'timedOut' | 'interrupted'
  duration: number
  error?: {
    message: string
    stack?: string
  }
}
