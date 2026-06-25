import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { TestCaseExecution, TestRun } from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import {
  CheckCircle,
  XCircle,
  MinusCircle,
  SkipForward,
  ChevronLeft,
  Loader2,
  AlertCircle,
} from 'lucide-react'

// ── Color helpers ─────────────────────────────────────────────────────────────

function execStatusColor(status: string): string {
  switch (status) {
    case 'PASSED':
      return 'text-green-700 bg-green-100'
    case 'FAILED':
      return 'text-red-700 bg-red-100'
    case 'BLOCKED':
      return 'text-orange-700 bg-orange-100'
    case 'SKIPPED':
      return 'text-slate-500 bg-slate-100'
    case 'PENDING':
      return 'text-blue-600 bg-blue-50'
    default:
      return 'text-slate-600 bg-slate-100'
  }
}

function runStatusColor(status: string): string {
  switch (status) {
    case 'IN_PROGRESS':
      return 'text-blue-700 bg-blue-100'
    case 'COMPLETED':
      return 'text-green-700 bg-green-100'
    case 'ABORTED':
      return 'text-red-700 bg-red-100'
    default:
      return 'text-slate-600 bg-slate-100'
  }
}

function envColor(env: string): string {
  switch (env?.toUpperCase()) {
    case 'PROD':
      return 'text-red-700 bg-red-100'
    case 'STAGING':
      return 'text-orange-700 bg-orange-100'
    case 'DEV':
      return 'text-blue-700 bg-blue-100'
    default:
      return 'text-slate-600 bg-slate-100'
  }
}

// ── Progress bar ──────────────────────────────────────────────────────────────

function ProgressSection({ run }: { run: TestRun }) {
  const { totalTests, passed, failed, blocked, skipped, pending } = run

  const segments = [
    { label: 'Passed', count: passed, color: 'bg-green-500' },
    { label: 'Failed', count: failed, color: 'bg-red-500' },
    { label: 'Blocked', count: blocked, color: 'bg-orange-400' },
    { label: 'Skipped', count: skipped, color: 'bg-slate-300' },
    { label: 'Pending', count: pending, color: 'bg-blue-200' },
  ]

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
      <div className="flex items-center gap-6 mb-4 flex-wrap">
        {segments.map(seg => (
          <div key={seg.label} className="flex items-center gap-2">
            <div className={cn('w-3 h-3 rounded-full', seg.color)} />
            <span className="text-sm">
              <span className="font-semibold text-slate-900">{seg.count}</span>
              <span className="text-slate-500 ml-1">{seg.label}</span>
            </span>
          </div>
        ))}
        {totalTests > 0 && (
          <div className="ml-auto text-sm font-semibold text-slate-700">{totalTests} total</div>
        )}
      </div>

      {totalTests > 0 && (
        <div className="flex h-3 rounded-full overflow-hidden bg-slate-100">
          {segments.map(
            seg =>
              seg.count > 0 && (
                <div
                  key={seg.label}
                  className={seg.color}
                  style={{ width: `${(seg.count / totalTests) * 100}%` }}
                  title={`${seg.label}: ${seg.count}`}
                />
              ),
          )}
        </div>
      )}
    </div>
  )
}

// ── Execution row ──────────────────────────────────────────────────────────────

function ExecutionRow({
  exec,
  projectId,
  runId,
  onUpdated,
}: {
  exec: TestCaseExecution
  projectId: string
  runId: string
  onUpdated: (updated: TestCaseExecution) => void
}) {
  const [showActualResult, setShowActualResult] = useState(exec.status === 'FAILED')
  const [actualResult, setActualResult] = useState(exec.actualResult ?? '')
  const [optimisticStatus, setOptimisticStatus] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (body: { status: string; actualResult?: string }) =>
      api.updateExecution(projectId, runId, exec.id, body),
    onSuccess: updated => {
      onUpdated(updated)
      setOptimisticStatus(null)
    },
    onError: () => setOptimisticStatus(null),
  })

  const currentStatus = optimisticStatus ?? exec.status

  function handleStatusClick(status: string) {
    setOptimisticStatus(status)
    if (status === 'FAILED') setShowActualResult(true)
    else setShowActualResult(false)
    mutation.mutate({
      status,
      actualResult: status === 'FAILED' ? actualResult || undefined : undefined,
    })
  }

  function handleActualResultBlur() {
    if (currentStatus === 'FAILED' && actualResult !== (exec.actualResult ?? '')) {
      mutation.mutate({ status: 'FAILED', actualResult: actualResult || undefined })
    }
  }

  const actionButtons = [
    {
      status: 'PASSED',
      icon: CheckCircle,
      label: 'Pass',
      className: 'text-green-600 hover:bg-green-50',
    },
    { status: 'FAILED', icon: XCircle, label: 'Fail', className: 'text-red-600 hover:bg-red-50' },
    {
      status: 'BLOCKED',
      icon: MinusCircle,
      label: 'Block',
      className: 'text-orange-500 hover:bg-orange-50',
    },
    {
      status: 'SKIPPED',
      icon: SkipForward,
      label: 'Skip',
      className: 'text-slate-400 hover:bg-slate-50',
    },
  ]

  return (
    <div
      className={cn(
        'px-5 py-3 transition-colors',
        currentStatus === 'FAILED' ? 'bg-red-50/30' : '',
        currentStatus === 'PASSED' ? 'bg-green-50/30' : '',
      )}
    >
      <div className="flex items-center gap-3">
        {/* Status badge */}
        <div className="w-20 shrink-0">
          <Badge label={currentStatus} colorClass={execStatusColor(currentStatus)} />
        </div>

        {/* Title */}
        <p className="flex-1 text-sm text-slate-900 truncate">{exec.testCaseTitle}</p>

        {/* Action buttons */}
        <div className="flex items-center gap-0.5 shrink-0">
          {mutation.isPending && <Loader2 size={14} className="animate-spin text-slate-400 mr-1" />}
          {actionButtons.map(btn => {
            const Icon = btn.icon
            const isActive = currentStatus === btn.status
            return (
              <button
                key={btn.status}
                onClick={() => handleStatusClick(btn.status)}
                disabled={mutation.isPending}
                title={btn.label}
                className={cn(
                  'p-1.5 rounded-lg transition-colors disabled:opacity-40',
                  btn.className,
                  isActive ? 'ring-2 ring-current ring-offset-1' : '',
                )}
              >
                <Icon size={16} />
              </button>
            )
          })}
        </div>
      </div>

      {/* Actual result textarea — shown when FAILED */}
      {showActualResult && (
        <div className="mt-2 ml-[92px]">
          <textarea
            value={actualResult}
            onChange={e => setActualResult(e.target.value)}
            onBlur={handleActualResultBlur}
            rows={2}
            placeholder="Describe actual result (optional)"
            className="w-full border border-red-200 rounded-lg px-3 py-2 text-xs text-slate-700 resize-none focus:outline-none focus:ring-1 focus:ring-red-400 bg-white"
          />
        </div>
      )}

      {/* Executed by / at */}
      {exec.executedAt && (
        <div className="mt-1 ml-[92px] text-xs text-slate-400">
          {exec.executedBy ? `${exec.executedBy} · ` : ''}
          {relativeTime(exec.executedAt)}
        </div>
      )}
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function TestRunExecutionPage() {
  const { projectId, base } = useProject()
  const { runId } = useParams<{ runId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // Local optimistic state for executions list
  const [localExecutions, setLocalExecutions] = useState<TestCaseExecution[] | null>(null)

  const {
    data: run,
    isLoading: runLoading,
    error: runError,
    refetch: runRefetch,
  } = useQuery({
    queryKey: ['testRun', projectId, runId],
    queryFn: () => api.testRun(projectId!, runId!),
    enabled: !!projectId && !!runId,
  })

  const {
    data: executions = [],
    isLoading: execLoading,
    error: execError,
    refetch: execRefetch,
  } = useQuery({
    queryKey: ['runExecutions', projectId, runId],
    queryFn: () => api.runExecutions(projectId!, runId!),
    enabled: !!projectId && !!runId,
    select: data => {
      setLocalExecutions(null) // reset optimistic on fresh fetch
      return data
    },
  })

  const completeMutation = useMutation({
    mutationFn: () => api.completeTestRun(projectId!, runId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['testRun', projectId, runId] })
      queryClient.invalidateQueries({ queryKey: ['testRuns', projectId] })
    },
  })

  if (runLoading || execLoading) return <LoadingSpinner message="Loading test run…" />
  if (runError || !run)
    return <ErrorMessage message="Failed to load test run." onRetry={() => void runRefetch()} />
  if (execError)
    return <ErrorMessage message="Failed to load executions." onRetry={() => void execRefetch()} />

  const displayExecutions = localExecutions ?? executions
  const pendingCount = displayExecutions.filter(e => e.status === 'PENDING').length

  function handleExecutionUpdated(updated: TestCaseExecution) {
    const base = localExecutions ?? executions
    setLocalExecutions(base.map(e => (e.id === updated.id ? updated : e)))
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <button
          onClick={() => navigate(`${base}/test-runs`)}
          className="flex items-center gap-1 text-sm text-slate-500 hover:text-blue-600 transition-colors mb-2"
        >
          <ChevronLeft size={14} /> Test Runs
        </button>
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-2xl font-bold text-slate-900">{run.name}</h1>
              {run.releaseVersion && (
                <span className="text-sm px-2 py-0.5 bg-slate-100 text-slate-600 rounded font-mono">
                  {run.releaseVersion}
                </span>
              )}
              <Badge label={run.environment} colorClass={envColor(run.environment)} />
              <Badge label={run.status.replace('_', ' ')} colorClass={runStatusColor(run.status)} />
            </div>
            {run.triggeredBy && (
              <p className="text-sm text-slate-500 mt-1">Triggered by {run.triggeredBy}</p>
            )}
          </div>
          {run.status === 'IN_PROGRESS' && (
            <button
              onClick={() => completeMutation.mutate()}
              disabled={completeMutation.isPending || pendingCount > 0}
              className={cn(
                'flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors shrink-0',
                pendingCount === 0
                  ? 'text-white bg-green-600 hover:bg-green-700'
                  : 'text-slate-400 bg-slate-100 cursor-not-allowed',
              )}
              title={pendingCount > 0 ? `${pendingCount} test cases still pending` : 'Complete run'}
            >
              {completeMutation.isPending ? (
                <Loader2 size={15} className="animate-spin" />
              ) : (
                <CheckCircle size={15} />
              )}
              Complete Run
              {pendingCount > 0 && (
                <span className="text-xs opacity-70">({pendingCount} pending)</span>
              )}
            </button>
          )}
        </div>
      </div>

      {/* Progress */}
      <ProgressSection run={run} />

      {/* Pending warning */}
      {pendingCount > 0 && run.status === 'IN_PROGRESS' && (
        <div className="flex items-center gap-2 px-4 py-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-700">
          <AlertCircle size={15} className="shrink-0" />
          {pendingCount} test {pendingCount === 1 ? 'case' : 'cases'} still pending — click Pass /
          Fail / Block / Skip to record results.
        </div>
      )}

      {/* Execution list */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        <div className="px-5 py-3 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900 text-sm">Test Case Results</h2>
          <span className="text-xs text-slate-400">{displayExecutions.length} test cases</span>
        </div>
        {displayExecutions.length === 0 && (
          <div className="py-12 text-center text-sm text-slate-500">No test cases in this run.</div>
        )}
        <div className="divide-y divide-slate-50">
          {displayExecutions.map(exec => (
            <ExecutionRow
              key={exec.id}
              exec={exec}
              projectId={projectId!}
              runId={runId!}
              onUpdated={handleExecutionUpdated}
            />
          ))}
        </div>
      </div>
    </div>
  )
}
