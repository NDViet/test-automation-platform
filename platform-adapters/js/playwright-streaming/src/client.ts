import { stat, readFile } from 'fs/promises'
import {
  FinishRunResponse,
  StartRunRequest,
  StartRunResponse,
  TestEventRequest,
} from './types'

/**
 * HTTP client for the platform streaming ingestion API.
 *
 * Test results are pushed fire-and-forget via pushTest(); the reporter
 * collects the pending promises and awaits them all in onEnd() before
 * closing the session — so no results are lost and the test runner is
 * never blocked waiting for an HTTP round-trip.
 */
export class StreamingClient {
  private readonly baseUrl: string
  private readonly headers: Record<string, string>
  private readonly timeoutMs: number
  private readonly pending: Promise<void>[] = []

  constructor(endpoint: string, apiKey: string | undefined, timeoutMs: number) {
    this.baseUrl   = endpoint.replace(/\/$/, '')
    this.headers   = {
      'Content-Type': 'application/json',
      ...(apiKey ? { 'X-API-Key': apiKey } : {}),
    }
    this.timeoutMs = timeoutMs
  }

  async startRun(req: StartRunRequest): Promise<string> {
    const res = await this.doPost<StartRunResponse>('/api/v1/stream/runs', req)
    return res.runId
  }

  /**
   * Fire-and-forget: push a test result with no trace.
   * Adds one promise to `pending`; call awaitPending() to drain.
   */
  pushTest(runId: string, req: TestEventRequest): void {
    const p = this.doPost<{ accepted: boolean }>(
      `/api/v1/stream/runs/${runId}/test`,
      req
    ).catch(err => {
      console.warn(`[PlatformStream] Failed to push test "${req.displayName}": ${(err as Error).message}`)
    })
    this.pending.push(p as Promise<void>)
  }

  /**
   * Fire-and-forget: push a test result AND immediately upload its trace as a
   * single chained promise. The trace upload starts only after the pushTest
   * response confirms the TestCaseResult row is in the DB — so the trace is
   * linked correctly even before the run finishes.
   *
   * Both operations share one slot in `pending`, so awaitPending() covers the
   * entire chain.
   */
  pushTestWithTrace(runId: string, req: TestEventRequest, traceData: string | Buffer): void {
    const chain = this.doPost<{ accepted: boolean }>(
      `/api/v1/stream/runs/${runId}/test`,
      req
    )
      .then(() => this.doMultipart(
        `/api/v1/stream/runs/${runId}/trace`,
        req.testId,
        traceData,
      ))
      .catch(err => {
        console.warn(
          `[PlatformStream] push+trace failed for "${req.displayName}": ${(err as Error).message}`
        )
      })
    this.pending.push(chain as Promise<void>)
  }

  /** Send a keep-alive ping so the server doesn't treat this session as a zombie. */
  async heartbeat(runId: string): Promise<void> {
    await this.doPost<void>(`/api/v1/stream/runs/${runId}/heartbeat`, {}).catch(err => {
      console.warn(`[PlatformStream] Heartbeat failed for runId=${runId}: ${(err as Error).message}`)
    })
  }

  async finishRun(runId: string): Promise<FinishRunResponse> {
    await this.awaitPending()
    return this.doPost<FinishRunResponse>(`/api/v1/stream/runs/${runId}/finish`, {})
  }

  /** Drain all queued fire-and-forget requests. */
  async awaitPending(): Promise<void> {
    await Promise.allSettled(this.pending)
    this.pending.length = 0
  }

  private async doMultipart(path: string, testId: string, traceData: string | Buffer): Promise<void> {
    let bytes: Buffer

    if (typeof traceData === 'string') {
      await stat(traceData)
      bytes = await readFile(traceData)
    } else {
      bytes = traceData
    }

    const form = new FormData()
    form.append('testId', testId)
    form.append('trace', new Blob([bytes], { type: 'application/zip' }), 'trace.zip')

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), this.timeoutMs * 3)
    try {
      const headers: Record<string, string> = {}
      if (this.headers['X-API-Key']) headers['X-API-Key'] = this.headers['X-API-Key']
      const res = await fetch(`${this.baseUrl}${path}`, {
        method: 'POST',
        headers,
        body: form,
        signal: controller.signal,
      })
      if (!res.ok) {
        const text = await res.text().catch(() => '')
        throw new Error(`HTTP ${res.status} ${res.statusText}: ${text}`)
      }
    } finally {
      clearTimeout(timer)
    }
  }

  private async doPost<T>(path: string, body: unknown): Promise<T> {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), this.timeoutMs)
    try {
      const res = await fetch(`${this.baseUrl}${path}`, {
        method: 'POST',
        headers: this.headers,
        body: JSON.stringify(body),
        signal: controller.signal,
      })
      if (!res.ok) {
        const text = await res.text().catch(() => '')
        throw new Error(`HTTP ${res.status} ${res.statusText}: ${text}`)
      }
      return res.json() as Promise<T>
    } finally {
      clearTimeout(timer)
    }
  }
}
