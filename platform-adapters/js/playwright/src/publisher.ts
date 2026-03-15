import { PlaywrightReport } from './types'

export interface PublishOptions {
  endpoint: string
  apiKey?: string
  teamId: string
  projectId: string
  branch?: string
  environment?: string
  commitSha?: string
  ciRunUrl?: string
  timeoutMs: number
  report: PlaywrightReport
}

export interface PublishResult {
  runId: string
  accepted: number
}

export async function publishReport(opts: PublishOptions): Promise<PublishResult> {
  const url = `${opts.endpoint.replace(/\/$/, '')}/api/v1/results/ingest`

  const form = new FormData()
  form.append('teamId', opts.teamId)
  form.append('projectId', opts.projectId)
  form.append('format', 'PLAYWRIGHT')
  if (opts.branch)    form.append('branch', opts.branch)
  if (opts.environment) form.append('environment', opts.environment)
  if (opts.commitSha) form.append('commitSha', opts.commitSha)
  if (opts.ciRunUrl)  form.append('ciRunUrl', opts.ciRunUrl)

  const reportJson = JSON.stringify(opts.report)
  form.append(
    'files',
    new Blob([reportJson], { type: 'application/json' }),
    'playwright-report.json'
  )

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), opts.timeoutMs)

  try {
    const headers: Record<string, string> = {}
    if (opts.apiKey) headers['X-API-Key'] = opts.apiKey

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: form,
      signal: controller.signal,
    })

    if (!response.ok) {
      const body = await response.text().catch(() => '(no body)')
      throw new Error(`Platform ingestion returned ${response.status}: ${body}`)
    }

    const result = await response.json() as { runId: string; accepted: number }
    return result
  } finally {
    clearTimeout(timer)
  }
}
