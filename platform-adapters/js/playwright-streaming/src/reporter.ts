import path from 'path'
import type {
  Reporter,
  FullConfig,
  Suite,
  TestCase,
  TestResult,
  FullResult,
} from '@playwright/test/reporter'

import { StreamingReporterOptions, TestEventRequest } from './types'
import { StreamingClient } from './client'
import {
  detectBranch, detectCommitSha, detectCiRunUrl, isCI,
  detectCiProvider, detectWorkflow, detectTrigger,
  detectPrNumber, detectRunNumber, detectRunAttempt,
} from './env'

/**
 * Playwright reporter that streams each test result to the Test Automation Platform
 * the moment it finishes — no waiting for the entire suite to complete.
 *
 * Usage in playwright.config.ts:
 *
 *   import { defineConfig } from '@playwright/test'
 *
 *   export default defineConfig({
 *     reporter: [
 *       ['list'],
 *       ['/path/to/playwright-streaming/src/reporter.ts', {
 *         endpoint:    process.env.PLATFORM_URL    ?? 'http://localhost:8081',
 *         apiKey:      process.env.PLATFORM_API_KEY,
 *         orgSlug:     process.env.PLATFORM_ORG_SLUG,
 *         projectSlug: process.env.PLATFORM_PROJECT_SLUG,
 *         teamSlug:    process.env.PLATFORM_TEAM_SLUG,   // optional
 *         environment: process.env.TEST_ENV ?? 'local',
 *       }],
 *     ],
 *   })
 */
export default class PlatformStreamingReporter implements Reporter {
  private readonly opts!: Required<Omit<StreamingReporterOptions, 'teamSlug' | 'areaSlug' | 'ciProvider' | 'workflow' | 'trigger' | 'prNumber' | 'runNumber' | 'runAttempt'>> & {
    teamSlug?: string; areaSlug?: string
    ciProvider?: string; workflow?: string; trigger?: string
    prNumber?: number; runNumber?: string; runAttempt?: number
  }
  private client!: StreamingClient
  private runId: string | null = null
  private enabled = true
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private rootDir = process.cwd()

  constructor(options: StreamingReporterOptions) {
    if (!options.endpoint)    { this.enabled = false; console.warn('[PlatformStream] endpoint is required — reporter disabled');    return }
    if (!options.orgSlug)     { this.enabled = false; console.warn('[PlatformStream] orgSlug is required — reporter disabled');     return }
    if (!options.projectSlug) { this.enabled = false; console.warn('[PlatformStream] projectSlug is required — reporter disabled'); return }

    this.opts = {
      endpoint:    options.endpoint,
      apiKey:      options.apiKey      ?? '',
      orgSlug:     options.orgSlug,
      projectSlug: options.projectSlug,
      teamSlug:    options.teamSlug,
      areaSlug:    options.areaSlug,
      branch:      options.branch      ?? detectBranch()      ?? 'unknown',
      environment: options.environment ?? (isCI() ? 'ci' : 'local'),
      commitSha:   options.commitSha   ?? detectCommitSha()   ?? '',
      ciRunUrl:    options.ciRunUrl    ?? detectCiRunUrl()    ?? '',
      ciProvider:  options.ciProvider  ?? detectCiProvider(),
      workflow:    options.workflow     ?? detectWorkflow(),
      trigger:     options.trigger     ?? detectTrigger(),
      prNumber:    options.prNumber    ?? detectPrNumber(),
      runNumber:   options.runNumber   ?? detectRunNumber(),
      runAttempt:  options.runAttempt  ?? detectRunAttempt(),
      verbose:     options.verbose     ?? false,
      timeoutMs:   options.timeoutMs   ?? 15_000,
    }

    this.client = new StreamingClient(
      this.opts.endpoint,
      this.opts.apiKey || undefined,
      this.opts.timeoutMs
    )
  }

  async onBegin(config: FullConfig, _suite: Suite): Promise<void> {
    if (!this.enabled) return
    this.rootDir = config.rootDir || process.cwd()

    try {
      this.runId = await this.client.startRun({
        orgSlug:     this.opts.orgSlug,
        projectSlug: this.opts.projectSlug,
        teamSlug:    this.opts.teamSlug,
        areaSlug:    this.opts.areaSlug,
        branch:      this.opts.branch      || undefined,
        environment: this.opts.environment || undefined,
        commitSha:   this.opts.commitSha   || undefined,
        ciRunUrl:    this.opts.ciRunUrl    || undefined,
        ciProvider:  this.opts.ciProvider,
        workflow:    this.opts.workflow,
        trigger:     this.opts.trigger,
        prNumber:    this.opts.prNumber,
        runNumber:   this.opts.runNumber,
        runAttempt:  this.opts.runAttempt,
      })
      console.log(`[PlatformStream] Run started → runId=${this.runId} (${this.opts.endpoint})`)

      // Send a heartbeat every 5 minutes so the server doesn't treat a slow test as a zombie.
      // The interval fires independently of pushTest calls, so a 35-minute E2E test stays alive.
      const intervalMs = 5 * 60 * 1000
      this.heartbeatTimer = setInterval(() => {
        if (this.runId) this.client.heartbeat(this.runId)
      }, intervalMs)
    } catch (err) {
      console.error(`[PlatformStream] Could not start run: ${(err as Error).message}`)
      this.enabled = false
    }
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    if (!this.enabled || !this.runId) return

    const req = this.buildTestEvent(test, result)

    // Upload a trace if Playwright attached one — regardless of test status.
    // This respects whatever trace: setting the user chose in playwright.config.ts
    // ('on', 'retain-on-failure', 'on-first-retry', etc.) without any coupling here.
    const att = result.attachments.find(a => a.name === 'trace')
    const traceData: string | Buffer | undefined = att?.path ?? att?.body

    if (traceData) {
      // Chain pushTest → uploadTrace in a single pending promise so the trace is
      // uploaded as soon as the DB row exists — no waiting for the run to finish.
      this.client.pushTestWithTrace(this.runId, req, traceData)
    } else {
      this.client.pushTest(this.runId, req)
    }

    if (this.opts.verbose) {
      console.log(`[PlatformStream] → ${req.status.padEnd(11)} ${req.displayName} (${req.durationMs}ms)`)
    }
  }

  async onEnd(_result: FullResult): Promise<void> {
    if (!this.enabled || !this.runId) return

    // Stop heartbeats — the run is finishing normally.
    if (this.heartbeatTimer !== null) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }

    // Drain all pending push+trace chains started in onTestEnd.
    // By the time this resolves every trace has been uploaded.
    await this.client.awaitPending()

    // Finalize the run (publishes Kafka event for alerts / quality gates).
    // Non-critical for trace visibility — traces are already stored above.
    try {
      const response = await this.client.finishRun(this.runId)
      console.log(
        `[PlatformStream] Run finished → runId=${response.runId} ` +
        `tests=${response.testCount} ${this.opts.endpoint}${response.processingUrl}`
      )
    } catch (err) {
      console.error(`[PlatformStream] Failed to finish run: ${(err as Error).message}`)
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private buildTestEvent(test: TestCase, result: TestResult): TestEventRequest {
    const titlePath = test.titlePath()
    const testId    = titlePath.join(' > ')
    const suiteName = titlePath.slice(0, -1).join(' > ') || test.parent?.title || ''

    // Spec file relative to the Playwright config root (forward slashes on all platforms)
    const specFile = path.relative(this.rootDir, test.location.file).replace(/\\/g, '/')

    // Playwright project name (browser / device) — walk up the suite ancestor chain
    const browser = this.resolveProjectName(test)

    // Classify all annotations by namespace prefix
    const tags: string[]                                  = [...(test.tags ?? [])]
    const annotations: { type: string; description?: string }[] = []
    const coveredFiles: string[]                          = []
    const coveredComponents: string[]                     = []
    const coveredRoutes: string[]                         = []
    const labels: { key: string; value: string }[]        = []

    for (const ann of test.annotations) {
      if (ann.type === 'tag') {
        if (ann.description) tags.push(ann.description)
      } else if (ann.type === 'tia:file') {
        if (ann.description) coveredFiles.push(ann.description)
      } else if (ann.type === 'tia:component') {
        if (ann.description) coveredComponents.push(ann.description)
      } else if (ann.type === 'tia:route') {
        if (ann.description) coveredRoutes.push(ann.description)
      } else if (ann.type.startsWith('label:')) {
        const key = ann.type.slice('label:'.length)
        if (key && ann.description != null) labels.push({ key, value: ann.description })
      } else {
        // Playwright built-ins (fixme, slow, fail, skip) and user-defined non-system annotations
        annotations.push({ type: ann.type, description: ann.description })
      }
    }

    const hasScreenshot = result.attachments.some(a => a.name === 'screenshot')
    const hasVideo      = result.attachments.some(a => a.name === 'video')

    const error = result.error
    return {
      testId,
      displayName:  test.title,
      suiteName,
      specFile,
      browser,
      tags,
      annotations:        annotations.length       > 0 ? annotations       : undefined,
      status:             result.status as TestEventRequest['status'],
      durationMs:         result.duration,
      failureMessage:     error ? (error.message ?? String(error)) : undefined,
      stackTrace:         error?.stack ?? undefined,
      retryCount:         result.retry,
      workerIndex:        result.workerIndex,
      hasScreenshot,
      hasVideo,
      coveredFiles:       coveredFiles.length       > 0 ? coveredFiles       : undefined,
      coveredComponents:  coveredComponents.length  > 0 ? coveredComponents  : undefined,
      coveredRoutes:      coveredRoutes.length      > 0 ? coveredRoutes      : undefined,
      labels:             labels.length             > 0 ? labels             : undefined,
    }
  }

  /** Walk the suite ancestor chain to find the Playwright project name. */
  private resolveProjectName(test: TestCase): string | undefined {
    let suite: Suite | undefined = test.parent
    while (suite) {
      const p = suite.project?.()
      if (p?.name) return p.name
      suite = suite.parent
    }
    return undefined
  }
}
