import { useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import { runStatusVariant } from '@/lib/status'
import type { TestCaseExecution } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { StatusBadge } from '@/components/ui'
import { ClipboardList, ChevronRight } from 'lucide-react'

export default function MyQueuePage() {
  const { projectId, base, project } = useProject()
  const navigate = useNavigate()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['my-queue', projectId],
    queryFn: () => api.myQueue(projectId),
  })

  const items: TestCaseExecution[] = Array.isArray(data) ? data : []
  const pending = items.filter(e => e.status === 'PENDING').length

  if (isLoading) return <LoadingSpinner message="Loading your queue…" />
  if (error)
    return <ErrorMessage message="Failed to load your queue." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center gap-2 text-sm text-fg-muted mb-1">
          <button onClick={() => navigate(base)} className="hover:text-primary">
            {project.name}
          </button>
          <ChevronRight size={14} />
          <span className="text-fg">My Queue</span>
        </div>
        <div className="flex items-center gap-3">
          <ClipboardList size={20} className="text-fg-muted" />
          <h1 className="text-xl font-semibold tracking-tight text-fg">My Queue</h1>
          {pending > 0 && (
            <span className="inline-flex items-center justify-center min-w-6 h-6 px-2 text-xs font-bold text-white bg-warning rounded-full">
              {pending}
            </span>
          )}
        </div>
        <p className="text-sm text-fg-muted mt-1">
          Test executions assigned to you in this project.
        </p>
      </div>

      {items.length === 0 ? (
        <div className="bg-surface rounded-lg border border-border shadow-xs px-5 py-20 text-center">
          <ClipboardList size={36} className="mx-auto mb-3 text-fg-subtle" />
          <p className="text-sm font-medium text-fg-muted">Nothing assigned to you</p>
          <p className="text-xs text-fg-subtle mt-1">
            Executions assigned to you across the project's runs show up here.
          </p>
        </div>
      ) : (
        <div className="bg-surface rounded-lg border border-border shadow-xs divide-y divide-border">
          {items.map(e => (
            <button
              key={e.id}
              onClick={() => navigate(`${base}/test-execution/manual/${e.testRunId}`)}
              className="w-full flex items-center justify-between gap-3 px-5 py-3 text-left hover:bg-surface-muted"
            >
              <div className="min-w-0">
                <p className="text-sm font-medium text-fg truncate">
                  {e.testCaseTitle || e.testCaseId}
                </p>
                <p className="text-xs text-fg-subtle">
                  {e.executedAt ? `updated ${relativeTime(e.executedAt)}` : 'not started'}
                </p>
              </div>
              <StatusBadge variant={runStatusVariant(e.status)}>
                {e.status.toLowerCase()}
              </StatusBadge>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
