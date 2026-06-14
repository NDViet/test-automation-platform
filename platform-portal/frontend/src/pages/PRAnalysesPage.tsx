import { useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime, cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { ChevronRight, ExternalLink, GitPullRequest, CheckCircle, XCircle, Clock, AlertCircle } from 'lucide-react'
import type { PrAnalysis } from '@/lib/types'

const STATUS_META: Record<string, { color: string; icon: React.ReactNode }> = {
  COMPLETED:       { color: 'text-green-700 bg-green-100',  icon: <CheckCircle size={13} /> },
  FAILED:          { color: 'text-red-700 bg-red-100',      icon: <XCircle size={13} /> },
  RUNNING:         { color: 'text-blue-700 bg-blue-100',    icon: <Clock size={13} /> },
  PENDING:         { color: 'text-slate-600 bg-slate-100',  icon: <Clock size={13} /> },
  AWAITING_REVIEW: { color: 'text-yellow-700 bg-yellow-100', icon: <AlertCircle size={13} /> },
}

function statusMeta(s: string) {
  return STATUS_META[s] ?? { color: 'text-slate-600 bg-slate-100', icon: null }
}

function prLabel(refUrl: string | null): string {
  if (!refUrl) return 'Unknown PR'
  const m = refUrl.match(/github\.com\/([^/]+\/[^/]+)\/pull\/(\d+)/)
  return m ? `${m[1]} #${m[2]}` : refUrl
}

function durationLabel(a: PrAnalysis): string {
  if (!a.startedAt || !a.completedAt) return '—'
  const ms = new Date(a.completedAt).getTime() - new Date(a.startedAt).getTime()
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.round(ms / 60000)}m`
}

export default function PRAnalysesPage() {
  const { projectId, base } = useProject()
  const navigate = useNavigate()

  const { data, isLoading, error } = useQuery({
    queryKey: ['pr-analyses', projectId],
    queryFn:  () => api.prAnalyses(projectId!, 30),
    enabled:  !!projectId,
  })

  if (isLoading) return <LoadingSpinner message="Loading PR analyses…" />
  if (error)     return <ErrorMessage  message="Failed to load PR analyses." />

  const items: PrAnalysis[] = Array.isArray(data) ? data : []

  const completed = items.filter(i => i.status === 'COMPLETED').length
  const failed    = items.filter(i => i.status === 'FAILED').length
  const running   = items.filter(i => i.status === 'RUNNING' || i.status === 'PENDING').length

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <div>
        <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
          <button onClick={() => navigate('/')} className="hover:text-blue-600">Overview</button>
          <ChevronRight size={14} />
          <button onClick={() => navigate(base)} className="hover:text-blue-600">
            {projectId}
          </button>
          <ChevronRight size={14} />
          <span className="text-slate-700">PR Analyses</span>
        </div>
        <div className="flex items-center gap-3">
          <GitPullRequest size={20} className="text-slate-400" />
          <h1 className="text-2xl font-bold text-slate-900">PR Analyses</h1>
        </div>
        <p className="text-sm text-slate-500 mt-1">
          GitHub pull requests analyzed by the AnalysisNode — coverage gaps, TIA risk, and test recommendations.
        </p>
      </div>

      {/* Stats */}
      {items.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Completed', value: completed, color: 'text-green-600' },
            { label: 'Failed',    value: failed,    color: 'text-red-600' },
            { label: 'Running',   value: running,   color: 'text-blue-600' },
          ].map(s => (
            <div key={s.label} className="bg-white rounded-xl border border-slate-200 shadow-sm px-5 py-4">
              <p className="text-xs text-slate-500">{s.label}</p>
              <p className={cn('text-2xl font-bold', s.color)}>{s.value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        <div className="px-5 py-4 border-b border-slate-100">
          <h2 className="font-semibold text-slate-900">Recent PR Analyses</h2>
        </div>

        {items.length === 0 ? (
          <p className="px-5 py-12 text-sm text-slate-500 text-center">
            No PR analyses yet. Connect a GitHub integration and push a pull request to trigger the AnalysisNode.
          </p>
        ) : (
          <div className="divide-y divide-slate-50">
            {items.map(a => {
              const { color: sc } = statusMeta(a.status)
              return (
                <div key={a.workflowId} className="px-5 py-4">
                  <div className="flex items-start justify-between gap-4">
                    {/* Left: PR link + summary */}
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <Badge
                          label={a.status.replace('_', ' ')}
                          colorClass={sc}
                        />
                        {a.refUrl ? (
                          <a
                            href={a.refUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="flex items-center gap-1 text-sm font-medium text-blue-700 hover:underline truncate"
                          >
                            {prLabel(a.refUrl)}
                            <ExternalLink size={11} className="shrink-0" />
                          </a>
                        ) : (
                          <span className="text-sm font-medium text-slate-500">Unknown PR</span>
                        )}
                      </div>

                      {a.summary ? (
                        <p className="text-xs text-slate-600 leading-relaxed line-clamp-3">{a.summary}</p>
                      ) : (
                        <p className="text-xs text-slate-400 italic">No summary available.</p>
                      )}
                    </div>

                    {/* Right: meta */}
                    <div className="shrink-0 text-right space-y-1">
                      <p className="text-xs text-slate-500">{relativeTime(a.createdAt)}</p>
                      <p className="text-xs text-slate-400">Duration: {durationLabel(a)}</p>
                      {(a.totalInputTokens > 0 || a.totalOutputTokens > 0) && (
                        <p className="text-xs text-slate-400">
                          {(a.totalInputTokens + a.totalOutputTokens).toLocaleString()} tokens
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
