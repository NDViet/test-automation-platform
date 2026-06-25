import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { WorkItem, WorkItemType } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import {
  Inbox, ChevronRight, RefreshCw, ExternalLink, CheckCircle,
  XCircle, Loader2, GitPullRequest, FileText, Sparkles,
  AlertCircle, ArrowRight, Clock, X, User,
} from 'lucide-react'

// ── Type metadata ─────────────────────────────────────────────────────────────

const TYPE_META: Record<WorkItemType, { label: string; color: string; icon: React.ReactNode }> = {
  TEST_CASE_REVIEW: {
    label: 'Test Case Review',
    color: 'text-blue-700 bg-blue-100',
    icon: <FileText size={14} className="text-blue-500" />,
  },
  AUTOMATION_PR: {
    label: 'Automation PR',
    color: 'text-purple-700 bg-purple-100',
    icon: <GitPullRequest size={14} className="text-purple-500" />,
  },
  AGENT_REVIEW: {
    label: 'Agent Review',
    color: 'text-amber-700 bg-amber-100',
    icon: <Sparkles size={14} className="text-amber-500" />,
  },
  WORKFLOW: {
    label: 'Workflow',
    color: 'text-slate-600 bg-slate-100',
    icon: <Loader2 size={14} className="text-slate-500" />,
  },
  IMPACT_ANALYSIS: {
    label: 'Impact Analysis',
    color: 'text-green-700 bg-green-100',
    icon: <CheckCircle size={14} className="text-green-500" />,
  },
}

function typeMeta(t: WorkItemType): { label: string; color: string; icon: React.ReactNode } {
  return TYPE_META[t] ?? TYPE_META.WORKFLOW
}

function statusIcon(status: string, itemType: WorkItemType): React.ReactNode {
  if (itemType === 'WORKFLOW') {
    if (status === 'RUNNING')          return <Loader2 size={13} className="animate-spin text-blue-500" />
    if (status === 'AWAITING_REVIEW')  return <AlertCircle size={13} className="text-amber-500" />
    if (status === 'PENDING')          return <Clock size={13} className="text-slate-400" />
  }
  if (status === 'PENDING')    return <Clock size={13} className="text-amber-500" />
  if (status === 'COMPLETED')  return <CheckCircle size={13} className="text-green-500" />
  return null
}

// ── Filter tabs ───────────────────────────────────────────────────────────────

const FILTERS: { label: string; value: WorkItemType | 'ALL' }[] = [
  { label: 'All',              value: 'ALL' },
  { label: 'Awaiting Review',  value: 'TEST_CASE_REVIEW' },
  { label: 'Agent Requests',   value: 'AGENT_REVIEW' },
  { label: 'PRs to Merge',     value: 'AUTOMATION_PR' },
  { label: 'Impact Analyses',  value: 'IMPACT_ANALYSIS' },
  { label: 'In Progress',      value: 'WORKFLOW' },
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
    mutationFn: () => action === 'approve'
      ? api.approveReviewRequest(projectId, item.metadata.reviewRequestId as string, decidedBy || 'portal-user')
      : api.rejectReviewRequest(projectId, item.metadata.reviewRequestId as string, decidedBy || 'portal-user'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-items', projectId] })
      onClose()
    },
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">
            {action === 'approve' ? 'Approve' : 'Reject'} Review Request
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700">
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <p className="text-sm text-slate-600 leading-relaxed">{item.title}</p>
          <div>
            <label className="text-xs font-medium text-slate-500 block mb-1">Your name (optional)</label>
            <div className="flex items-center gap-2 border border-slate-200 rounded-lg px-3 py-2">
              <User size={14} className="text-slate-400" />
              <input
                value={decidedBy}
                onChange={e => setDecidedBy(e.target.value)}
                placeholder="e.g. John Smith"
                className="flex-1 text-sm outline-none"
              />
            </div>
          </div>
          {mutation.isError && (
            <p className="text-xs text-red-600">Failed to submit decision. Please try again.</p>
          )}
        </div>
        <div className="px-6 py-4 border-t border-slate-100 flex gap-3 justify-end">
          <button onClick={onClose} className="px-4 py-2 text-sm text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50">
            Cancel
          </button>
          <button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending}
            className={cn(
              'flex items-center gap-2 px-4 py-2 text-sm text-white rounded-lg disabled:opacity-50',
              action === 'approve' ? 'bg-green-600 hover:bg-green-700' : 'bg-red-600 hover:bg-red-700',
            )}
          >
            {mutation.isPending && <Loader2 size={13} className="animate-spin" />}
            {action === 'approve' ? 'Approve' : 'Reject'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Test case approve/reject ──────────────────────────────────────────────────

function TestCaseActions({
  item,
  projectId,
}: {
  item: WorkItem
  projectId: string
}) {
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
      <button
        onClick={() => rejectMutation.mutate()}
        disabled={busy}
        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-red-700 border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50"
      >
        {rejectMutation.isPending ? <Loader2 size={11} className="animate-spin" /> : <XCircle size={11} />}
        Reject
      </button>
      <button
        onClick={() => approveMutation.mutate()}
        disabled={busy}
        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-green-700 border border-green-200 rounded-lg hover:bg-green-50 disabled:opacity-50"
      >
        {approveMutation.isPending ? <Loader2 size={11} className="animate-spin" /> : <CheckCircle size={11} />}
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
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-3">
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          {icon}
          <span className={cn('text-xs px-2 py-0.5 rounded-full font-medium shrink-0', color)}>
            {label}
          </span>
          <span className="text-xs text-slate-400 shrink-0">{relativeTime(item.createdAt)}</span>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {statusIcon(item.status, item.itemType)}
          <span className="text-xs text-slate-500">{item.status.replace('_', ' ')}</span>
        </div>
      </div>

      {/* Title + description */}
      <div>
        <p className="text-sm font-medium text-slate-800 leading-snug">{item.title}</p>
        <p className="text-xs text-slate-500 mt-1 leading-relaxed">{item.description}</p>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2 pt-1">
        {item.itemType === 'TEST_CASE_REVIEW' && (
          <>
            <TestCaseActions item={item} projectId={projectId} />
            <button
              onClick={() => navigate(`${base}/test-cases`)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50 ml-1"
            >
              <ArrowRight size={11} />
              View Details
            </button>
          </>
        )}

        {item.itemType === 'AGENT_REVIEW' && (
          <>
            <button
              onClick={() => onDecide(item, 'reject')}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-red-700 border border-red-200 rounded-lg hover:bg-red-50"
            >
              <XCircle size={11} />
              Reject
            </button>
            <button
              onClick={() => onDecide(item, 'approve')}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-green-700 border border-green-200 rounded-lg hover:bg-green-50"
            >
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
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-blue-700 border border-blue-200 rounded-lg hover:bg-blue-50"
          >
            <ExternalLink size={11} />
            Review on GitHub
          </a>
        )}

        {item.itemType === 'IMPACT_ANALYSIS' && (
          <button
            onClick={primaryAction}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-green-700 border border-green-200 rounded-lg hover:bg-green-50"
          >
            <ArrowRight size={11} />
            View Suggestions
          </button>
        )}

        {item.itemType === 'WORKFLOW' && (
          <span className="text-xs text-slate-400 italic">
            {item.status === 'RUNNING' ? 'AI is working…' : item.status === 'AWAITING_REVIEW' ? 'Awaiting decision' : 'Queued'}
          </span>
        )}
      </div>

      {/* Agent review artifact manifest preview */}
      {item.itemType === 'AGENT_REVIEW' && item.metadata.artifactManifest != null && (
        <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
          <p className="text-xs font-medium text-slate-600 mb-1">Artifacts</p>
          <pre className="text-xs text-slate-500 whitespace-pre-wrap break-all">
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
  const navigate      = useNavigate()
  const [filter, setFilter]   = useState<WorkItemType | 'ALL'>('ALL')
  const [decide, setDecide]   = useState<{ item: WorkItem; action: 'approve' | 'reject' } | null>(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['work-items', projectId],
    queryFn:  () => api.workItems(projectId!),
    enabled:  !!projectId,
    refetchInterval: (query) => {
      const list = Array.isArray(query.state.data) ? query.state.data as WorkItem[] : []
      return list.some(i => i.itemType === 'WORKFLOW' && (i.status === 'RUNNING' || i.status === 'PENDING')) ? 8000 : 30000
    },
  })

  const items: WorkItem[] = Array.isArray(data) ? data : []

  const filtered = filter === 'ALL' ? items : items.filter(i => i.itemType === filter)

  const counts: Partial<Record<WorkItemType | 'ALL', number>> = { ALL: items.length }
  items.forEach(i => { counts[i.itemType] = (counts[i.itemType] ?? 0) + 1 })

  const pendingCount = items.filter(i => i.status === 'PENDING').length

  if (isLoading) return <LoadingSpinner message="Loading review queue…" />
  if (error)     return <ErrorMessage  message="Failed to load work items." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
          <button onClick={() => navigate('/')} className="hover:text-blue-600">Overview</button>
          <ChevronRight size={14} />
          <button onClick={() => navigate(base)} className="hover:text-blue-600">{project.name}</button>
          <ChevronRight size={14} />
          <span className="text-slate-700">Review Queue</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Inbox size={20} className="text-slate-400" />
            <h1 className="text-2xl font-bold text-slate-900">Review Queue</h1>
            {pendingCount > 0 && (
              <span className="inline-flex items-center justify-center w-6 h-6 text-xs font-bold text-white bg-red-500 rounded-full">
                {pendingCount}
              </span>
            )}
          </div>
          <button
            onClick={() => refetch()}
            className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg"
            title="Refresh"
          >
            <RefreshCw size={16} />
          </button>
        </div>
        <p className="text-sm text-slate-500 mt-1">
          Everything that needs your attention — test case approvals, PRs to review, agent decisions, and more.
        </p>
      </div>

      {/* Filter tabs */}
      <div className="flex gap-1 flex-wrap">
        {FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={cn(
              'flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
              filter === f.value
                ? 'bg-blue-600 text-white'
                : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50',
            )}
          >
            {f.label}
            {counts[f.value] !== undefined && counts[f.value]! > 0 && (
              <span className={cn(
                'text-xs px-1.5 py-0.5 rounded-full font-medium',
                filter === f.value ? 'bg-blue-500 text-white' : 'bg-slate-100 text-slate-600',
              )}>
                {counts[f.value]}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Items */}
      {filtered.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm px-5 py-20 text-center">
          <Inbox size={36} className="mx-auto mb-3 text-slate-300" />
          <p className="text-sm font-medium text-slate-500">
            {filter === 'ALL' ? 'No work items right now' : `No items in "${FILTERS.find(f => f.value === filter)?.label}"`}
          </p>
          <p className="text-xs text-slate-400 mt-1">
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
