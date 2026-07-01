import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { WorkItem, WorkItemType } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button } from '@/components/ui'
import {
  Inbox,
  ChevronRight,
  RefreshCw,
  ExternalLink,
  CheckCircle,
  XCircle,
  Loader2,
  GitPullRequest,
  FileText,
  Sparkles,
  AlertCircle,
  ArrowRight,
  Clock,
  X,
  User,
} from 'lucide-react'

// Outline action-button classes for reject/approve affordances.
const rejectBtn =
  'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-danger border border-danger-border rounded-md hover:bg-danger-bg disabled:opacity-50'
const approveBtn =
  'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-success border border-success-border rounded-md hover:bg-success-bg disabled:opacity-50'

// ── Type metadata ─────────────────────────────────────────────────────────────

const TYPE_META: Record<WorkItemType, { label: string; color: string; icon: React.ReactNode }> = {
  TEST_CASE_REVIEW: {
    label: 'Test Case Review',
    color: 'text-info bg-info-bg',
    icon: <FileText size={14} className="text-info" />,
  },
  AUTOMATION_PR: {
    label: 'Automation PR',
    color: 'text-primary-subtle-fg bg-primary-subtle',
    icon: <GitPullRequest size={14} className="text-primary" />,
  },
  AGENT_REVIEW: {
    label: 'Agent Review',
    color: 'text-warning bg-warning-bg',
    icon: <Sparkles size={14} className="text-warning" />,
  },
  WORKFLOW: {
    label: 'Workflow',
    color: 'text-neutral bg-neutral-bg',
    icon: <Loader2 size={14} className="text-fg-muted" />,
  },
  IMPACT_ANALYSIS: {
    label: 'Impact Analysis',
    color: 'text-success bg-success-bg',
    icon: <CheckCircle size={14} className="text-success" />,
  },
}

function typeMeta(t: WorkItemType): { label: string; color: string; icon: React.ReactNode } {
  return TYPE_META[t] ?? TYPE_META.WORKFLOW
}

function statusIcon(status: string, itemType: WorkItemType): React.ReactNode {
  if (itemType === 'WORKFLOW') {
    if (status === 'RUNNING') return <Loader2 size={13} className="animate-spin text-primary" />
    if (status === 'AWAITING_REVIEW') return <AlertCircle size={13} className="text-warning" />
    if (status === 'PENDING') return <Clock size={13} className="text-fg-subtle" />
  }
  if (status === 'PENDING') return <Clock size={13} className="text-warning" />
  if (status === 'COMPLETED') return <CheckCircle size={13} className="text-success" />
  return null
}

// ── Filter tabs ───────────────────────────────────────────────────────────────

const FILTERS: { label: string; value: WorkItemType | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Awaiting Review', value: 'TEST_CASE_REVIEW' },
  { label: 'Agent Requests', value: 'AGENT_REVIEW' },
  { label: 'PRs to Merge', value: 'AUTOMATION_PR' },
  { label: 'Impact Analyses', value: 'IMPACT_ANALYSIS' },
  { label: 'In Progress', value: 'WORKFLOW' },
]

// ── Approve/Reject modal ──────────────────────────────────────────────────────

function DecideModal({
  item,
  action,
  projectId,
  onClose,
}: {
  item: WorkItem
  action: 'approve' | 'reject'
  projectId: string
  onClose: () => void
}) {
  const [decidedBy, setDecidedBy] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () =>
      action === 'approve'
        ? api.approveReviewRequest(
            projectId,
            item.metadata.reviewRequestId as string,
            decidedBy || 'portal-user',
          )
        : api.rejectReviewRequest(
            projectId,
            item.metadata.reviewRequestId as string,
            decidedBy || 'portal-user',
          ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-items', projectId] })
      onClose()
    },
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg">
            {action === 'approve' ? 'Approve' : 'Reject'} Review Request
          </h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <p className="text-sm text-fg-muted leading-relaxed">{item.title}</p>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">
              Your name (optional)
            </label>
            <div className="flex items-center gap-2 border border-border-strong rounded-md px-3 py-2">
              <User size={14} className="text-fg-subtle" />
              <input
                value={decidedBy}
                onChange={e => setDecidedBy(e.target.value)}
                placeholder="e.g. John Smith"
                className="flex-1 text-sm outline-none bg-transparent text-fg"
              />
            </div>
          </div>
          {mutation.isError && (
            <p className="text-xs text-danger">Failed to submit decision. Please try again.</p>
          )}
        </div>
        <div className="px-6 py-4 border-t border-border flex gap-3 justify-end">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant={action === 'approve' ? 'primary' : 'danger'}
            onClick={() => mutation.mutate()}
            loading={mutation.isPending}
          >
            {action === 'approve' ? 'Approve' : 'Reject'}
          </Button>
        </div>
      </div>
    </div>
  )
}

// ── Test case approve/reject ──────────────────────────────────────────────────

function TestCaseActions({ item, projectId }: { item: WorkItem; projectId: string }) {
  const queryClient = useQueryClient()
  const tcId = item.metadata.testCaseId as string

  const approveMutation = useMutation({
    mutationFn: () => api.approveTestCase(projectId, tcId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-items', projectId] }),
  })
  const rejectMutation = useMutation({
    mutationFn: () => api.rejectTestCase(projectId, tcId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-items', projectId] }),
  })

  const busy = approveMutation.isPending || rejectMutation.isPending

  return (
    <div className="flex gap-2">
      <button onClick={() => rejectMutation.mutate()} disabled={busy} className={rejectBtn}>
        {rejectMutation.isPending ? (
          <Loader2 size={11} className="animate-spin" />
        ) : (
          <XCircle size={11} />
        )}
        Reject
      </button>
      <button onClick={() => approveMutation.mutate()} disabled={busy} className={approveBtn}>
        {approveMutation.isPending ? (
          <Loader2 size={11} className="animate-spin" />
        ) : (
          <CheckCircle size={11} />
        )}
        Approve
      </button>
    </div>
  )
}

// ── Work Item Card ────────────────────────────────────────────────────────────

function WorkItemCard({
  item,
  projectId,
  onDecide,
}: {
  item: WorkItem
  projectId: string
  onDecide: (item: WorkItem, action: 'approve' | 'reject') => void
}) {
  const navigate = useNavigate()
  const { base } = useProject()
  const { label, color, icon } = typeMeta(item.itemType)

  function primaryAction() {
    switch (item.itemType) {
      case 'TEST_CASE_REVIEW':
        navigate(`${base}/test-cases`)
        break
      case 'AUTOMATION_PR':
        if (item.actionUrl) window.open(item.actionUrl, '_blank', 'noreferrer')
        break
      case 'IMPACT_ANALYSIS':
        navigate(`${base}/impact-analyses`)
        break
      case 'WORKFLOW':
        // just informational
        break
    }
  }

  return (
    <div className="bg-surface rounded-lg border border-border shadow-xs p-5 space-y-3">
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          {icon}
          <span className={cn('text-xs px-2 py-0.5 rounded-full font-medium shrink-0', color)}>
            {label}
          </span>
          <span className="text-xs text-fg-subtle shrink-0">{relativeTime(item.createdAt)}</span>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {statusIcon(item.status, item.itemType)}
          <span className="text-xs text-fg-muted">{item.status.replace('_', ' ')}</span>
        </div>
      </div>

      {/* Title + description */}
      <div>
        <p className="text-sm font-medium text-fg leading-snug">{item.title}</p>
        <p className="text-xs text-fg-muted mt-1 leading-relaxed">{item.description}</p>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2 pt-1">
        {item.itemType === 'TEST_CASE_REVIEW' && (
          <>
            <TestCaseActions item={item} projectId={projectId} />
            <button
              onClick={() => navigate(`${base}/test-cases`)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-fg border border-border-strong rounded-md hover:bg-surface-muted ml-1"
            >
              <ArrowRight size={11} />
              View Details
            </button>
          </>
        )}

        {item.itemType === 'AGENT_REVIEW' && (
          <>
            <button onClick={() => onDecide(item, 'reject')} className={rejectBtn}>
              <XCircle size={11} />
              Reject
            </button>
            <button onClick={() => onDecide(item, 'approve')} className={approveBtn}>
              <CheckCircle size={11} />
              Approve
            </button>
          </>
        )}

        {item.itemType === 'AUTOMATION_PR' && item.actionUrl && (
          <a
            href={item.actionUrl}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-primary border border-primary-subtle rounded-md hover:bg-primary-subtle"
          >
            <ExternalLink size={11} />
            Review on GitHub
          </a>
        )}

        {item.itemType === 'IMPACT_ANALYSIS' && (
          <button onClick={primaryAction} className={approveBtn}>
            <ArrowRight size={11} />
            View Suggestions
          </button>
        )}

        {item.itemType === 'WORKFLOW' && (
          <span className="text-xs text-fg-subtle italic">
            {item.status === 'RUNNING'
              ? 'AI is working…'
              : item.status === 'AWAITING_REVIEW'
                ? 'Awaiting decision'
                : 'Queued'}
          </span>
        )}
      </div>

      {/* Agent review artifact manifest preview */}
      {item.itemType === 'AGENT_REVIEW' && item.metadata.artifactManifest != null && (
        <div className="bg-surface-muted border border-border rounded-lg px-3 py-2">
          <p className="text-xs font-medium text-fg-muted mb-1">Artifacts</p>
          <pre className="text-xs text-fg-muted whitespace-pre-wrap break-all">
            {JSON.stringify(item.metadata.artifactManifest as object, null, 2).slice(0, 400)}
          </pre>
        </div>
      )}
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function ReviewQueuePage() {
  const { projectId, base, project } = useProject()
  const navigate = useNavigate()
  const [filter, setFilter] = useState<WorkItemType | 'ALL'>('ALL')
  const [decide, setDecide] = useState<{ item: WorkItem; action: 'approve' | 'reject' } | null>(
    null,
  )

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['work-items', projectId],
    queryFn: () => api.workItems(projectId!),
    enabled: !!projectId,
    refetchInterval: query => {
      const list = Array.isArray(query.state.data) ? (query.state.data as WorkItem[]) : []
      return list.some(
        i => i.itemType === 'WORKFLOW' && (i.status === 'RUNNING' || i.status === 'PENDING'),
      )
        ? 8000
        : 30000
    },
  })

  const items: WorkItem[] = Array.isArray(data) ? data : []

  const filtered = filter === 'ALL' ? items : items.filter(i => i.itemType === filter)

  const counts: Partial<Record<WorkItemType | 'ALL', number>> = { ALL: items.length }
  items.forEach(i => {
    counts[i.itemType] = (counts[i.itemType] ?? 0) + 1
  })

  const pendingCount = items.filter(i => i.status === 'PENDING').length

  if (isLoading) return <LoadingSpinner message="Loading review queue…" />
  if (error)
    return <ErrorMessage message="Failed to load work items." onRetry={() => void refetch()} />

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
          <span className="text-fg">Review Queue</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Inbox size={20} className="text-fg-muted" />
            <h1 className="text-xl font-semibold tracking-tight text-fg">Review Queue</h1>
            {pendingCount > 0 && (
              <span className="inline-flex items-center justify-center w-6 h-6 text-xs font-bold text-white bg-danger rounded-full">
                {pendingCount}
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
          Everything that needs your attention — test case approvals, PRs to review, agent
          decisions, and more.
        </p>
      </div>

      {/* Filter tabs */}
      <div className="flex gap-1 flex-wrap">
        {FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={cn(
              'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
              filter === f.value
                ? 'bg-primary text-primary-fg'
                : 'bg-surface border border-border text-fg-muted hover:bg-surface-muted',
            )}
          >
            {f.label}
            {counts[f.value] !== undefined && counts[f.value]! > 0 && (
              <span
                className={cn(
                  'text-xs px-1.5 py-0.5 rounded-full font-medium',
                  filter === f.value
                    ? 'bg-primary-hover text-white'
                    : 'bg-surface-muted text-fg-muted',
                )}
              >
                {counts[f.value]}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Items */}
      {filtered.length === 0 ? (
        <div className="bg-surface rounded-lg border border-border shadow-xs px-5 py-20 text-center">
          <Inbox size={36} className="mx-auto mb-3 text-fg-subtle" />
          <p className="text-sm font-medium text-fg-muted">
            {filter === 'ALL'
              ? 'No work items right now'
              : `No items in "${FILTERS.find(f => f.value === filter)?.label}"`}
          </p>
          <p className="text-xs text-fg-subtle mt-1">
            {filter === 'ALL'
              ? 'Trigger an impact analysis, generate test cases, or run a flaky fix to see items here.'
              : 'Try a different filter.'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(item => (
            <WorkItemCard
              key={`${item.itemType}-${item.id}`}
              item={item}
              projectId={projectId!}
              onDecide={(i, a) => setDecide({ item: i, action: a })}
            />
          ))}
        </div>
      )}

      {/* Approve/Reject modal */}
      {decide && (
        <DecideModal
          item={decide.item}
          action={decide.action}
          projectId={projectId!}
          onClose={() => setDecide(null)}
        />
      )}
    </div>
  )
}
