import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import { failureClusterStatusVariant } from '@/lib/status'
import type { FailureCluster } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, StatusBadge } from '@/components/ui'
import {
  Bug,
  ChevronRight,
  RefreshCw,
  ExternalLink,
  X,
  Link2,
  Ticket,
  RotateCcw,
} from 'lucide-react'

const FILTERS: { label: string; value: string }[] = [
  { label: 'All', value: '' },
  { label: 'Open', value: 'OPEN' },
  { label: 'Known Issue', value: 'KNOWN_ISSUE' },
  { label: 'Resolved', value: 'RESOLVED' },
]

function statusLabel(status: string): string {
  return status.replace('_', ' ').toLowerCase()
}

// ── Link-existing-defect modal ────────────────────────────────────────────────

function LinkDefectModal({
  cluster,
  projectId,
  onClose,
}: {
  cluster: FailureCluster
  projectId: string
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [externalId, setExternalId] = useState('')
  const [url, setUrl] = useState('')

  const mutation = useMutation({
    mutationFn: () =>
      api.linkFailureClusterDefect(projectId, cluster.id, {
        externalId: externalId.trim(),
        url: url.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['failure-clusters', projectId] })
      onClose()
    },
  })

  const inputCls =
    'w-full text-sm border border-border-strong rounded-md px-3 py-2 bg-transparent text-fg outline-none focus:border-primary'

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg">Link an existing defect</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <p className="text-sm text-fg-muted leading-relaxed">{cluster.title}</p>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">
              Defect ID (e.g. ADO work item id)
            </label>
            <input
              value={externalId}
              onChange={e => setExternalId(e.target.value)}
              placeholder="e.g. 12345"
              className={inputCls}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">URL (optional)</label>
            <input
              value={url}
              onChange={e => setUrl(e.target.value)}
              placeholder="https://dev.azure.com/…"
              className={inputCls}
            />
          </div>
          {mutation.isError && (
            <p className="text-xs text-danger">{(mutation.error as Error).message}</p>
          )}
        </div>
        <div className="px-6 py-4 border-t border-border flex gap-3 justify-end">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={() => mutation.mutate()}
            loading={mutation.isPending}
            disabled={!externalId.trim()}
          >
            Link defect
          </Button>
        </div>
      </div>
    </div>
  )
}

// ── Cluster card ──────────────────────────────────────────────────────────────

function ClusterCard({
  cluster,
  projectId,
  onLink,
}: {
  cluster: FailureCluster
  projectId: string
  onLink: (c: FailureCluster) => void
}) {
  const { base } = useProject()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['failure-clusters', projectId] })
  }

  const statusMutation = useMutation({
    mutationFn: (status: string) => api.setFailureClusterStatus(projectId, cluster.id, status),
    onSuccess: invalidate,
    onError: (e: Error) => setError(e.message),
  })
  const createDefectMutation = useMutation({
    mutationFn: () => api.createFailureClusterDefect(projectId, cluster.id, {}),
    onSuccess: invalidate,
    onError: (e: Error) => setError(e.message),
  })
  const retestMutation = useMutation({
    mutationFn: () => api.retestFailureCluster(projectId, cluster.id),
    onSuccess: res => {
      invalidate()
      navigate(`${base}/test-execution/manual/${res.runId}`)
    },
    onError: (e: Error) => setError(e.message),
  })

  const busy =
    statusMutation.isPending || createDefectMutation.isPending || retestMutation.isPending
  const hasDefect = !!cluster.defectExternalId

  return (
    <div className="bg-surface rounded-lg border border-border shadow-xs p-5 space-y-3">
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <StatusBadge variant={failureClusterStatusVariant(cluster.status)}>
            {statusLabel(cluster.status)}
          </StatusBadge>
          <StatusBadge variant="neutral">{cluster.source.toLowerCase()}</StatusBadge>
          <span className="text-xs text-fg-subtle shrink-0">
            {cluster.occurrences} occurrence{cluster.occurrences === 1 ? '' : 's'}
          </span>
        </div>
        <span className="text-xs text-fg-subtle shrink-0">
          last seen {relativeTime(cluster.lastSeenAt)}
        </span>
      </div>

      {/* Title + sample */}
      <div>
        <p className="text-sm font-medium text-fg leading-snug">
          {cluster.title || 'Unknown failure'}
        </p>
        {cluster.testRef && (
          <p className="text-xs text-fg-subtle mt-0.5 font-mono truncate">{cluster.testRef}</p>
        )}
        {cluster.sampleMessage && (
          <pre className="text-xs text-fg-muted mt-2 whitespace-pre-wrap break-all bg-surface-muted border border-border rounded-md px-3 py-2 max-h-32 overflow-auto">
            {cluster.sampleMessage}
          </pre>
        )}
      </div>

      {/* Linked defect */}
      {hasDefect && (
        <a
          href={cluster.defectUrl ?? '#'}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
        >
          <ExternalLink size={12} />
          {cluster.defectExternalId}
          {cluster.defectState ? ` · ${cluster.defectState}` : ''}
        </a>
      )}

      {error && <p className="text-xs text-danger">{error}</p>}

      {/* Actions */}
      <div className="flex items-center gap-2 flex-wrap pt-1">
        {cluster.status !== 'RESOLVED' && (
          <Button
            variant="subtle"
            size="sm"
            disabled={busy}
            onClick={() => statusMutation.mutate('RESOLVED')}
          >
            Resolve
          </Button>
        )}
        {cluster.status !== 'KNOWN_ISSUE' && (
          <Button
            variant="subtle"
            size="sm"
            disabled={busy}
            onClick={() => statusMutation.mutate('KNOWN_ISSUE')}
          >
            Known issue
          </Button>
        )}
        {cluster.status !== 'OPEN' && (
          <Button
            variant="subtle"
            size="sm"
            disabled={busy}
            onClick={() => statusMutation.mutate('OPEN')}
          >
            Reopen
          </Button>
        )}
        {!hasDefect && (
          <>
            <Button variant="secondary" size="sm" disabled={busy} onClick={() => onLink(cluster)}>
              <Link2 size={13} /> Link defect
            </Button>
            <Button
              variant="secondary"
              size="sm"
              loading={createDefectMutation.isPending}
              disabled={busy}
              onClick={() => createDefectMutation.mutate()}
            >
              <Ticket size={13} /> Create defect
            </Button>
          </>
        )}
        <Button
          variant="secondary"
          size="sm"
          loading={retestMutation.isPending}
          disabled={busy}
          onClick={() => retestMutation.mutate()}
        >
          <RotateCcw size={13} /> Retest
        </Button>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function FailuresPage() {
  const { projectId, base, project } = useProject()
  const navigate = useNavigate()
  const [filter, setFilter] = useState('')
  const [linking, setLinking] = useState<FailureCluster | null>(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['failure-clusters', projectId, filter],
    queryFn: () => api.failureClusters(projectId, filter || undefined),
  })

  const clusters: FailureCluster[] = Array.isArray(data) ? data : []
  const openCount = clusters.filter(c => c.status === 'OPEN').length

  if (isLoading) return <LoadingSpinner message="Loading failures…" />
  if (error)
    return (
      <ErrorMessage message="Failed to load failure clusters." onRetry={() => void refetch()} />
    )

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2 text-sm text-fg-muted mb-1">
          <button onClick={() => navigate('/')} className="hover:text-primary">
            Overview
          </button>
          <ChevronRight size={14} />
          <button onClick={() => navigate(base)} className="hover:text-primary">
            {project.name}
          </button>
          <ChevronRight size={14} />
          <span className="text-fg">Failures</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Bug size={20} className="text-fg-muted" />
            <h1 className="text-xl font-semibold tracking-tight text-fg">Failures</h1>
            {filter === '' && openCount > 0 && (
              <span className="inline-flex items-center justify-center min-w-6 h-6 px-2 text-xs font-bold text-white bg-danger rounded-full">
                {openCount}
              </span>
            )}
          </div>
          <button
            onClick={() => refetch()}
            className="p-2 text-fg-muted hover:text-fg hover:bg-surface-muted rounded-md"
            title="Refresh"
            aria-label="Refresh"
          >
            <RefreshCw size={16} />
          </button>
        </div>
        <p className="text-sm text-fg-muted mt-1">
          Recurring test failures grouped by signature. Link or file a tracker defect, suppress
          known issues, or retest to confirm a fix.
        </p>
      </div>

      {/* Filter tabs */}
      <div className="flex gap-1 flex-wrap">
        {FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={cn(
              'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
              filter === f.value
                ? 'bg-primary text-primary-fg'
                : 'bg-surface border border-border text-fg-muted hover:bg-surface-muted',
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* List */}
      {clusters.length === 0 ? (
        <div className="bg-surface rounded-lg border border-border shadow-xs px-5 py-20 text-center">
          <Bug size={36} className="mx-auto mb-3 text-fg-subtle" />
          <p className="text-sm font-medium text-fg-muted">No failure clusters</p>
          <p className="text-xs text-fg-subtle mt-1">
            Failing automated results and manual executions group here as they recur.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {clusters.map(c => (
            <ClusterCard key={c.id} cluster={c} projectId={projectId} onLink={setLinking} />
          ))}
        </div>
      )}

      {linking && (
        <LinkDefectModal cluster={linking} projectId={projectId} onClose={() => setLinking(null)} />
      )}
    </div>
  )
}
