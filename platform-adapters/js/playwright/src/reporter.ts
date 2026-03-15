import type {
  Reporter,
  FullConfig,
  Suite,
  TestCase,
  TestResult,
  FullResult,
} from '@playwright/test/reporter'

import { PlatformReporterOptions, PlaywrightReport, ReportSuite, ReportSpec } from './types'
import { publishReport } from './publisher'
import { detectBranch, detectCommitSha, detectCiRunUrl, isCI } from './env'

export default class PlatformReporter implements Reporter {
  private readonly opts: Required<PlatformReporterOptions>
  private rootSuite: Suite | null = null
  private startTime: number = 0

  constructor(options: PlatformReporterOptions) {
    if (!options.endpoint) throw new Error('[PlatformReporter] endpoint is required')
    if (!options.teamId)   throw new Error('[PlatformReporter] teamId is required')
    if (!options.projectId) throw new Error('[PlatformReporter] projectId is required')

    this.opts = {
      endpoint:       options.endpoint,
      apiKey:         options.apiKey ?? '',
      teamId:         options.teamId,
      projectId:      options.projectId,
      branch:         options.branch       ?? detectBranch()   ?? 'unknown',
      environment:    options.environment  ?? (isCI() ? 'ci' : 'local'),
      commitSha:      options.commitSha    ?? detectCommitSha() ?? '',
      ciRunUrl:       options.ciRunUrl     ?? detectCiRunUrl()  ?? '',
      printSummary:   options.printSummary ?? true,
      timeoutMs:      options.timeoutMs    ?? 30_000,
      coveredModules: options.coveredModules ?? [],
    }
  }

  onBegin(_config: FullConfig, suite: Suite): void {
    this.rootSuite = suite
    this.startTime = Date.now()
  }

  // onTestEnd is intentionally not overridden — we read results from the Suite tree in onEnd
  // to get the final consolidated status after retries.

  async onEnd(_result: FullResult): Promise<void> {
    if (!this.rootSuite) return

    const report = this.buildReport(this.rootSuite)
    const totalSpecs = this.countSpecs(report.suites)

    if (totalSpecs === 0) {
      if (this.opts.printSummary) {
        console.log('[PlatformReporter] No test results found — skipping publish.')
      }
      return
    }

    try {
      const published = await publishReport({
        endpoint:    this.opts.endpoint,
        apiKey:      this.opts.apiKey || undefined,
        teamId:      this.opts.teamId,
        projectId:   this.opts.projectId,
        branch:      this.opts.branch || undefined,
        environment: this.opts.environment || undefined,
        commitSha:   this.opts.commitSha   || undefined,
        ciRunUrl:    this.opts.ciRunUrl    || undefined,
        timeoutMs:   this.opts.timeoutMs,
        report,
      })

      if (this.opts.printSummary) {
        console.log(
          `[PlatformReporter] ✓ Published ${totalSpecs} tests → runId=${published.runId}`
        )
      }
    } catch (err) {
      // Never fail the test run — log the error and continue
      console.error(`[PlatformReporter] Failed to publish results: ${(err as Error).message}`)
    }
  }

  // ── Report building ──────────────────────────────────────────────────────

  private buildReport(root: Suite): PlaywrightReport {
    // root has children: one Suite per file (project > file > describe > test)
    const fileSuites: ReportSuite[] = []

    for (const projectSuite of root.suites) {
      // Each project suite contains file-level suites
      for (const fileSuite of projectSuite.suites) {
        const reportSuite = this.convertSuite(fileSuite)
        if (reportSuite) fileSuites.push(reportSuite)
      }
    }

    return { suites: fileSuites }
  }

  private convertSuite(suite: Suite): ReportSuite | null {
    const file = suite.location?.file ?? suite.title

    const specs: ReportSpec[] = []
    const childSuites: ReportSuite[] = []

    // Direct tests (not inside a describe block)
    for (const test of suite.tests) {
      const spec = this.convertTest(test)
      if (spec) specs.push(spec)
    }

    // Nested describe blocks
    for (const child of suite.suites) {
      const childReport = this.convertSuite(child)
      if (childReport) childSuites.push(childReport)
    }

    if (specs.length === 0 && childSuites.length === 0) return null

    return {
      title: suite.title,
      file,
      ...(childSuites.length > 0 && { suites: childSuites }),
      ...(specs.length > 0 && { specs }),
    }
  }

  private convertTest(test: TestCase): ReportSpec | null {
    if (test.results.length === 0) return null

    // Use the last result (final attempt after retries)
    const lastResult = test.results[test.results.length - 1]!

    // Collect coveredModules: per-test annotation takes precedence over global config
    // Usage in test: test.info().annotations.push({ type: 'coveredModules', description: 'src/services/payment.ts' })
    const perTestModules = test.annotations
      .filter(a => a.type === 'coveredModules' && a.description)
      .map(a => a.description!)

    const coveredModules = perTestModules.length > 0
      ? perTestModules
      : this.opts.coveredModules

    return {
      title: test.title,
      tests: [{ results: [this.convertResult(lastResult)] }],
      ...(coveredModules && coveredModules.length > 0 && { coveredModules }),
    }
  }

  private convertResult(result: TestResult) {
    const mapped = this.mapStatus(result.status)
    return {
      status: mapped,
      duration: result.duration,
      ...(result.error && {
        error: {
          message: result.error.message ?? String(result.error),
          stack: result.error.stack ?? result.error.message ?? String(result.error),
        },
      }),
    }
  }

  private mapStatus(status: TestResult['status']): ReportSpec['tests'][0]['results'][0]['status'] {
    switch (status) {
      case 'passed':      return 'passed'
      case 'failed':      return 'failed'
      case 'skipped':     return 'skipped'
      case 'timedOut':    return 'timedOut'
      case 'interrupted': return 'interrupted'
      default:            return 'failed'
    }
  }

  private countSpecs(suites: ReportSuite[]): number {
    let count = 0
    for (const s of suites) {
      count += s.specs?.length ?? 0
      if (s.suites) count += this.countSpecs(s.suites)
    }
    return count
  }
}
