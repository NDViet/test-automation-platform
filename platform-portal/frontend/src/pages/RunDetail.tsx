import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { formatDuration, statusColor, relativeTime, cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import type { TestCase } from '@/lib/types'
import { ChevronRight, ChevronDown, ExternalLink } from 'lucide-react'

type StatusFilter = 'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED' | 'BROKEN'

export default function RunDetail() {
  const { runId } = useParams<{ runId: string }>()
  const navigate = useNavigate()
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  const { data, isLoading, error } = useQuery({
    queryKey: ['run', runId],
    queryFn: () => api.runDetail(runId!),
    enabled: !!runId,
  })

  if (isLoading) return <LoadingSpinner message="Loading run details…" />
  if (error || !data) return <ErrorMessage message="Failed to load run details." />

  const { summary: s, testCases } = data

  const filtered = (testCases ?? []).filter(tc =>
    statusFilter === 'ALL' || tc.status === statusFilter,
  )

  const toggle = (id: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const statusCounts = (testCases ?? []).reduce((acc, tc) => {
    acc[tc.status] = (acc[tc.status] ?? 0) + 1
    return acc
  }, {} as Record<string, number>)

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <button onClick={() => navigate('/')} className="hover:text-blue-600">Overview</button>
        <ChevronRight size={14} />
        <button onClick={() => navigate(`/projects/${s.projectId}`)} className="hover:text-blue-600">
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
              <a href={s.ciRunUrl} target="_blank" rel="noreferrer"
                 className="text-sm text-blue-600 hover:underline flex items-center gap-1 mt-0.5">
                View <ExternalLink size={12} />
              </a>
            </div>
          )}
        </div>

        {/* Result bar */}
        <div className="mt-4 flex items-center gap-1 h-2 rounded-full overflow-hidden">
          {s.totalTests > 0 && <>
            <div className="bg-green-500 h-full" style={{ width: `${s.passed / s.totalTests * 100}%` }} />
            <div className="bg-red-500 h-full"   style={{ width: `${s.failed / s.totalTests * 100}%` }} />
            <div className="bg-orange-400 h-full" style={{ width: `${s.broken / s.totalTests * 100}%` }} />
            <div className="bg-slate-300 h-full"  style={{ width: `${s.skipped / s.totalTests * 100}%` }} />
          </>}
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
          <button key={f}
            onClick={() => setStatusFilter(f)}
            className={cn(
              'px-3 py-1.5 text-xs font-medium rounded-lg transition-colors',
              statusFilter === f
                ? 'bg-blue-600 text-white'
                : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
            )}
          >
            {f} {f !== 'ALL' && statusCounts[f] ? `(${statusCounts[f]})` : ''}
          </button>
        ))}
      </div>

      {/* Test cases */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-50">
        {filtered.length === 0 && (
          <p className="px-5 py-8 text-center text-sm text-slate-500">No tests match this filter.</p>
        )}
        {filtered.map((tc: TestCase) => {
          const isOpen = expanded.has(tc.id)
          const hasFail = !!(tc.failureMessage || tc.stackTrace)
          return (
            <div key={tc.id}>
              <button
                onClick={() => hasFail && toggle(tc.id)}
                className={cn(
                  'w-full text-left px-5 py-3.5 flex items-center gap-3',
                  hasFail ? 'cursor-pointer hover:bg-slate-50' : 'cursor-default',
                  'transition-colors',
                )}
              >
                {hasFail && (
                  isOpen
                    ? <ChevronDown size={14} className="text-slate-400 shrink-0" />
                    : <ChevronRight size={14} className="text-slate-400 shrink-0" />
                )}
                {!hasFail && <div className="w-3.5 shrink-0" />}
                <Badge label={tc.status} colorClass={statusColor(tc.status)} />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-900 truncate">{tc.displayName}</p>
                  <p className="text-xs text-slate-400 truncate">{tc.className}</p>
                </div>
                <span className="text-xs text-slate-400 shrink-0">{formatDuration(tc.durationMs)}</span>
              </button>

              {isOpen && hasFail && (
                <div className="px-5 pb-4 space-y-3">
                  {tc.failureMessage && (
                    <div className="bg-red-50 border border-red-100 rounded-lg p-3">
                      <p className="text-xs font-semibold text-red-700 mb-1">Failure message</p>
                      <p className="text-xs text-red-700 font-mono whitespace-pre-wrap break-words">
                        {tc.failureMessage}
                      </p>
                    </div>
                  )}
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
