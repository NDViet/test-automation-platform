import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { formatDuration, statusColor, relativeTime, cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button } from '@/components/ui'
import type { TestCase } from '@/lib/types'
import {
  ChevronRight,
  ChevronDown,
  ExternalLink,
  Play,
  Download,
  Camera,
  Video,
  Globe,
} from 'lucide-react'

type StatusFilter = 'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED' | 'BROKEN'

export default function RunDetail() {
  const { runId } = useParams<{ runId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const expandResultId = searchParams.get('expandResult')

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [expanded, setExpanded] = useState<Set<string>>(() =>
    expandResultId ? new Set([expandResultId]) : new Set(),
  )
  const didAutoReset = useRef(false)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['run', runId],
    queryFn: () => api.runDetail(runId!),
    enabled: !!runId,
  })

  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => api.projects() })

  useEffect(() => {
    if (!expandResultId || !data?.testCases || didAutoReset.current) return
    didAutoReset.current = true
    const target = data.testCases.find(tc => tc.id === expandResultId)
    if (target && statusFilter !== 'ALL' && target.status !== statusFilter) {
      setStatusFilter('ALL')
    }
  }, [data, expandResultId, statusFilter])

  useEffect(() => {
    if (!isLoading && expandResultId) {
      const el = document.getElementById(`result-${expandResultId}`)
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }, [isLoading, expandResultId])

  if (isLoading) return <LoadingSpinner message="Loading run details…" />
  if (error || !data)
    return <ErrorMessage message="Failed to load run details." onRetry={() => void refetch()} />

  const { summary: s, testCases } = data
  const proj = projects?.find(p => p.id === s.projectId)
  const projectHref = proj ? `/${proj.orgSlug}/${proj.slug}` : `/projects/${s.projectId}`

  const filtered = (testCases ?? []).filter(
    tc => statusFilter === 'ALL' || tc.status === statusFilter,
  )

  const toggle = (id: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const statusCounts = (testCases ?? []).reduce(
    (acc, tc) => {
      acc[tc.status] = (acc[tc.status] ?? 0) + 1
      return acc
    },
    {} as Record<string, number>,
  )

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-fg-muted">
        <button onClick={() => navigate('/')} className="hover:text-primary">
          Overview
        </button>
        <ChevronRight size={14} />
        <button onClick={() => navigate(projectHref)} className="hover:text-primary">
          {s.projectName}
        </button>
        <ChevronRight size={14} />
        <span className="font-mono text-fg">{runId?.slice(0, 16)}…</span>
      </div>

      {/* Run summary */}
      <div className="bg-surface rounded-lg border border-border shadow-xs p-5">
        <div className="flex flex-wrap items-start gap-6">
          {[
            ['Run ID', <span className="font-mono">{s.runId}</span>],
            ['Branch', s.branch ?? '—'],
            ['Environment', s.environment ?? '—'],
            ['CI Provider', s.ciProvider ?? '—'],
            ['Mode', s.executionMode ?? 'UNKNOWN'],
            ['Duration', formatDuration(s.durationMs)],
            ['Executed', relativeTime(s.executedAt)],
          ].map(([label, value], i) => (
            <div key={i}>
              <p className="text-xs text-fg-muted uppercase tracking-wide">{label}</p>
              <p className="text-sm text-fg mt-0.5">{value}</p>
            </div>
          ))}
          {s.ciRunUrl && (
            <div>
              <p className="text-xs text-fg-muted uppercase tracking-wide">CI Run</p>
              <a
                href={s.ciRunUrl}
                target="_blank"
                rel="noreferrer"
                className="text-sm text-primary hover:underline flex items-center gap-1 mt-0.5"
              >
                View <ExternalLink size={12} />
              </a>
            </div>
          )}
        </div>

        {/* Result bar */}
        <div className="mt-4 flex items-center gap-1 h-2 rounded-full overflow-hidden">
          {s.totalTests > 0 && (
            <>
              <div className="bg-success h-full" style={{ width: `${(s.passed / s.totalTests) * 100}%` }} />
              <div className="bg-danger h-full" style={{ width: `${(s.failed / s.totalTests) * 100}%` }} />
              <div className="bg-warning h-full" style={{ width: `${(s.broken / s.totalTests) * 100}%` }} />
              <div className="bg-border-strong h-full" style={{ width: `${(s.skipped / s.totalTests) * 100}%` }} />
            </>
          )}
        </div>
        <div className="mt-2 flex gap-4 text-xs text-fg-muted">
          <span className="text-success">✓ {s.passed} passed</span>
          <span className="text-danger">✗ {s.failed} failed</span>
          {s.broken > 0 && <span className="text-warning">⚠ {s.broken} broken</span>}
          {s.skipped > 0 && <span className="text-fg-muted">◌ {s.skipped} skipped</span>}
          <span className="text-fg-subtle ml-auto">{s.totalTests} total</span>
        </div>
      </div>

      {/* Filter tabs */}
      <div className="flex items-center gap-2">
        {(['ALL', 'FAILED', 'BROKEN', 'PASSED', 'SKIPPED'] as StatusFilter[]).map(f => (
          <Button
            key={f}
            size="sm"
            variant={statusFilter === f ? 'primary' : 'secondary'}
            onClick={() => setStatusFilter(f)}
          >
            {f} {f !== 'ALL' && statusCounts[f] ? `(${statusCounts[f]})` : ''}
          </Button>
        ))}
      </div>

      {/* Test cases */}
      <div className="bg-surface rounded-lg border border-border shadow-xs divide-y divide-border">
        {filtered.length === 0 && (
          <p className="px-5 py-8 text-center text-sm text-fg-muted">No tests match this filter.</p>
        )}
        {filtered.map((tc: TestCase) => {
          const isOpen = expanded.has(tc.id)
          const hasFail = !!(tc.failureMessage || tc.stackTrace)
          const hasTrace = !!tc.hasTrace
          const isExpandable = hasFail || hasTrace
          const isTarget = tc.id === expandResultId

          const traceViewerUrl = `${window.location.origin}/pw-trace/index.html?trace=${encodeURIComponent(`${window.location.origin}/api/portal/traces/${tc.id}`)}`

          return (
            <div
              key={tc.id}
              id={`result-${tc.id}`}
              className={cn('group', isTarget && 'ring-2 ring-inset ring-primary rounded-sm')}
            >
              {/* ── Row header ── */}
              <div
                onClick={() => isExpandable && toggle(tc.id)}
                className={cn(
                  'w-full text-left px-5 py-3.5 flex items-center gap-3',
                  isExpandable ? 'cursor-pointer hover:bg-surface-muted' : 'cursor-default',
                  'transition-colors',
                  isTarget && !isOpen && 'bg-primary-subtle',
                )}
              >
                {/* Expand chevron */}
                {isExpandable ? (
                  isOpen ? (
                    <ChevronDown size={14} className="text-fg-subtle shrink-0" />
                  ) : (
                    <ChevronRight size={14} className="text-fg-subtle shrink-0" />
                  )
                ) : (
                  <div className="w-3.5 shrink-0" />
                )}

                <Badge label={tc.status} colorClass={statusColor(tc.status)} />

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium text-fg truncate">{tc.displayName}</p>
                    {tc.hasScreenshot && (
                      <span title="Has screenshot">
                        <Camera size={12} className="text-fg-subtle shrink-0" />
                      </span>
                    )}
                    {tc.hasVideo && (
                      <span title="Has video">
                        <Video size={12} className="text-fg-subtle shrink-0" />
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <p className="text-xs text-fg-subtle truncate">{tc.className}</p>
                    {tc.browser && (
                      <span className="inline-flex items-center gap-0.5 text-[10px] text-fg-subtle">
                        <Globe size={9} /> {tc.browser}
                      </span>
                    )}
                    {tc.retryCount > 0 && (
                      <span className="text-[10px] text-warning font-medium">
                        {tc.retryCount} retr{tc.retryCount === 1 ? 'y' : 'ies'}
                      </span>
                    )}
                  </div>
                </div>

                <span className="text-xs text-fg-subtle shrink-0">
                  {formatDuration(tc.durationMs)}
                </span>

                {/* Trace / attachment quick-actions — visible on row hover */}
                {hasTrace && (
                  <div
                    className="shrink-0 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"
                    onClick={e => e.stopPropagation()}
                  >
                    <a
                      href={traceViewerUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      title="Open in Playwright Trace Viewer"
                      className="flex items-center gap-1 px-2 py-1 rounded text-[11px] font-medium
                                 bg-primary-subtle text-primary-subtle-fg border border-primary-subtle
                                 hover:brightness-95 transition-all"
                    >
                      <Play size={10} />
                      Trace
                    </a>
                    <a
                      href={api.traceUrl(tc.id)}
                      download={`trace-${tc.id}.zip`}
                      title="Download trace ZIP"
                      className="flex items-center gap-1 px-2 py-1 rounded text-[11px] font-medium
                                 bg-surface-muted text-fg-muted border border-border
                                 hover:bg-border transition-colors"
                    >
                      <Download size={10} />
                    </a>
                  </div>
                )}
              </div>

              {/* ── Expanded detail ── */}
              {isOpen && (
                <div className="px-5 pb-5 space-y-3 border-t border-border pt-3">
                  {/* Trace viewer — shown prominently at the top of the expanded section */}
                  {hasTrace && (
                    <div className="flex items-center gap-2 p-3 bg-primary-subtle border border-primary-subtle rounded-lg">
                      <Play size={14} className="text-primary shrink-0" />
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-semibold text-primary-subtle-fg">
                          Playwright Trace
                        </p>
                        <p className="text-[11px] text-primary-subtle-fg/80 truncate font-mono mt-0.5">
                          {tc.specFile ?? tc.displayName}
                        </p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <a
                          href={traceViewerUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold
                                     bg-primary text-primary-fg hover:bg-primary-hover transition-colors"
                        >
                          <Play size={11} />
                          Open Trace
                        </a>
                        <a
                          href={api.traceUrl(tc.id)}
                          download={`trace-${tc.id}.zip`}
                          title="Download trace ZIP — view with: npx playwright show-trace trace.zip"
                          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium
                                     bg-surface border border-border text-primary
                                     hover:bg-surface-muted transition-colors"
                        >
                          <Download size={11} />
                          .zip
                        </a>
                      </div>
                    </div>
                  )}

                  {/* Failure message */}
                  {tc.failureMessage && (
                    <div className="bg-danger-bg border border-danger-border rounded-lg p-3">
                      <p className="text-xs font-semibold text-danger mb-1">Failure message</p>
                      <p className="text-xs text-danger font-mono whitespace-pre-wrap break-words">
                        {tc.failureMessage}
                      </p>
                    </div>
                  )}

                  {/* Stack trace */}
                  {tc.stackTrace && (
                    <div className="bg-slate-900 rounded-lg p-3 overflow-x-auto">
                      <p className="text-xs font-semibold text-slate-300 mb-2">Stack trace</p>
                      <pre className="text-xs text-slate-300 font-mono whitespace-pre-wrap break-words leading-relaxed">
                        {tc.stackTrace.split('\n').slice(0, 20).join('\n')}
                        {tc.stackTrace.split('\n').length > 20 &&
                          '\n… (truncated, view full in Grafana logs)'}
                      </pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
