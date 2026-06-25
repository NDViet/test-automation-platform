import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { formatDuration, statusColor, relativeTime, cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
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
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <button onClick={() => navigate('/')} className="hover:text-blue-600">
          Overview
        </button>
        <ChevronRight size={14} />
        <button onClick={() => navigate(projectHref)} className="hover:text-blue-600">
          {s.projectName}
        </button>
        <ChevronRight size={14} />
        <span className="font-mono text-slate-700">{runId?.slice(0, 16)}…</span>
      </div>

      {/* Run summary */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
        <div className="flex flex-wrap items-start gap-6">
          <div>
            <p className="text-xs text-slate-500 uppercase tracking-wide">Run ID</p>
            <p className="font-mono text-sm text-slate-800 mt-0.5">{s.runId}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase tracking-wide">Branch</p>
            <p className="text-sm text-slate-800 mt-0.5">{s.branch ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase tracking-wide">Environment</p>
            <p className="text-sm text-slate-800 mt-0.5">{s.environment ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase tracking-wide">CI Provider</p>
            <p className="text-sm text-slate-800 mt-0.5">{s.ciProvider ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase tracking-wide">Mode</p>
            <p className="text-sm text-slate-800 mt-0.5">{s.executionMode ?? 'UNKNOWN'}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase tracking-wide">Duration</p>
            <p className="text-sm text-slate-800 mt-0.5">{formatDuration(s.durationMs)}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase tracking-wide">Executed</p>
            <p className="text-sm text-slate-800 mt-0.5">{relativeTime(s.executedAt)}</p>
          </div>
          {s.ciRunUrl && (
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wide">CI Run</p>
              <a
                href={s.ciRunUrl}
                target="_blank"
                rel="noreferrer"
                className="text-sm text-blue-600 hover:underline flex items-center gap-1 mt-0.5"
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
              <div
                className="bg-green-500 h-full"
                style={{ width: `${(s.passed / s.totalTests) * 100}%` }}
              />
              <div
                className="bg-red-500 h-full"
                style={{ width: `${(s.failed / s.totalTests) * 100}%` }}
              />
              <div
                className="bg-orange-400 h-full"
                style={{ width: `${(s.broken / s.totalTests) * 100}%` }}
              />
              <div
                className="bg-slate-300 h-full"
                style={{ width: `${(s.skipped / s.totalTests) * 100}%` }}
              />
            </>
          )}
        </div>
        <div className="mt-2 flex gap-4 text-xs text-slate-600">
          <span className="text-green-700">✓ {s.passed} passed</span>
          <span className="text-red-700">✗ {s.failed} failed</span>
          {s.broken > 0 && <span className="text-orange-700">⚠ {s.broken} broken</span>}
          {s.skipped > 0 && <span className="text-slate-500">◌ {s.skipped} skipped</span>}
          <span className="text-slate-400 ml-auto">{s.totalTests} total</span>
        </div>
      </div>

      {/* Filter tabs */}
      <div className="flex items-center gap-2">
        {(['ALL', 'FAILED', 'BROKEN', 'PASSED', 'SKIPPED'] as StatusFilter[]).map(f => (
          <button
            key={f}
            onClick={() => setStatusFilter(f)}
            className={cn(
              'px-3 py-1.5 text-xs font-medium rounded-lg transition-colors',
              statusFilter === f
                ? 'bg-blue-600 text-white'
                : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50',
            )}
          >
            {f} {f !== 'ALL' && statusCounts[f] ? `(${statusCounts[f]})` : ''}
          </button>
        ))}
      </div>

      {/* Test cases */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-50">
        {filtered.length === 0 && (
          <p className="px-5 py-8 text-center text-sm text-slate-500">
            No tests match this filter.
          </p>
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
              className={cn('group', isTarget && 'ring-2 ring-inset ring-blue-400 rounded-sm')}
            >
              {/* ── Row header ── */}
              <div
                onClick={() => isExpandable && toggle(tc.id)}
                className={cn(
                  'w-full text-left px-5 py-3.5 flex items-center gap-3',
                  isExpandable ? 'cursor-pointer hover:bg-slate-50' : 'cursor-default',
                  'transition-colors',
                  isTarget && !isOpen && 'bg-blue-50',
                )}
              >
                {/* Expand chevron */}
                {isExpandable ? (
                  isOpen ? (
                    <ChevronDown size={14} className="text-slate-400 shrink-0" />
                  ) : (
                    <ChevronRight size={14} className="text-slate-400 shrink-0" />
                  )
                ) : (
                  <div className="w-3.5 shrink-0" />
                )}

                <Badge label={tc.status} colorClass={statusColor(tc.status)} />

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium text-slate-900 truncate">{tc.displayName}</p>
                    {tc.hasScreenshot && (
                      <span title="Has screenshot">
                        <Camera size={12} className="text-slate-400 shrink-0" />
                      </span>
                    )}
                    {tc.hasVideo && (
                      <span title="Has video">
                        <Video size={12} className="text-slate-400 shrink-0" />
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <p className="text-xs text-slate-400 truncate">{tc.className}</p>
                    {tc.browser && (
                      <span className="inline-flex items-center gap-0.5 text-[10px] text-slate-400">
                        <Globe size={9} /> {tc.browser}
                      </span>
                    )}
                    {tc.retryCount > 0 && (
                      <span className="text-[10px] text-amber-600 font-medium">
                        {tc.retryCount} retr{tc.retryCount === 1 ? 'y' : 'ies'}
                      </span>
                    )}
                  </div>
                </div>

                <span className="text-xs text-slate-400 shrink-0">
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
                                 bg-violet-50 text-violet-700 border border-violet-200
                                 hover:bg-violet-100 transition-colors"
                    >
                      <Play size={10} />
                      Trace
                    </a>
                    <a
                      href={api.traceUrl(tc.id)}
                      download={`trace-${tc.id}.zip`}
                      title="Download trace ZIP"
                      className="flex items-center gap-1 px-2 py-1 rounded text-[11px] font-medium
                                 bg-slate-100 text-slate-500 border border-slate-200
                                 hover:bg-slate-200 transition-colors"
                    >
                      <Download size={10} />
                    </a>
                  </div>
                )}
              </div>

              {/* ── Expanded detail ── */}
              {isOpen && (
                <div className="px-5 pb-5 space-y-3 border-t border-slate-50 pt-3">
                  {/* Trace viewer — shown prominently at the top of the expanded section */}
                  {hasTrace && (
                    <div className="flex items-center gap-2 p-3 bg-violet-50 border border-violet-200 rounded-lg">
                      <Play size={14} className="text-violet-600 shrink-0" />
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-semibold text-violet-800">Playwright Trace</p>
                        <p className="text-[11px] text-violet-600 truncate font-mono mt-0.5">
                          {tc.specFile ?? tc.displayName}
                        </p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <a
                          href={traceViewerUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold
                                     bg-violet-600 text-white hover:bg-violet-700 transition-colors"
                        >
                          <Play size={11} />
                          Open Trace
                        </a>
                        <a
                          href={api.traceUrl(tc.id)}
                          download={`trace-${tc.id}.zip`}
                          title="Download trace ZIP — view with: npx playwright show-trace trace.zip"
                          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium
                                     bg-white border border-violet-200 text-violet-700
                                     hover:bg-violet-50 transition-colors"
                        >
                          <Download size={11} />
                          .zip
                        </a>
                      </div>
                    </div>
                  )}

                  {/* Failure message */}
                  {tc.failureMessage && (
                    <div className="bg-red-50 border border-red-100 rounded-lg p-3">
                      <p className="text-xs font-semibold text-red-700 mb-1">Failure message</p>
                      <p className="text-xs text-red-700 font-mono whitespace-pre-wrap break-words">
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
