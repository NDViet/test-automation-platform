import { useState, useEffect, type ReactNode } from 'react'
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
  RotateCcw,
  Plus,
  X,
  Bug,
  Paperclip,
  Download,
  Trash2,
  SlidersHorizontal,
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
  editable,
  onUpdated,
}: {
  exec: TestCaseExecution
  projectId: string
  runId: string
  editable: boolean
  onUpdated: (updated: TestCaseExecution) => void
}) {
  const [showActualResult, setShowActualResult] = useState(exec.status === 'FAILED')
  const [actualResult, setActualResult] = useState(exec.actualResult ?? '')
  const [optimisticStatus, setOptimisticStatus] = useState<string | null>(null)

  const [defectInput, setDefectInput] = useState('')
  const [showEvidence, setShowEvidence] = useState(false)
  const qc = useQueryClient()

  const attachmentsQ = useQuery({
    queryKey: ['attachments', runId, exec.id],
    queryFn: () => api.listExecutionAttachments(projectId, runId, exec.id),
    enabled: showEvidence,
  })
  const attachments = attachmentsQ.data ?? []

  const uploadAttachment = useMutation({
    mutationFn: (file: File) => api.uploadExecutionAttachment(projectId, runId, exec.id, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['attachments', runId, exec.id] }),
  })
  const deleteAttachment = useMutation({
    mutationFn: (attachmentId: string) =>
      api.deleteExecutionAttachment(projectId, runId, attachmentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['attachments', runId, exec.id] }),
  })

  const mutation = useMutation({
    mutationFn: (body: { status: string; actualResult?: string }) =>
      api.updateExecution(projectId, runId, exec.id, body),
    onSuccess: updated => {
      onUpdated(updated)
      setOptimisticStatus(null)
    },
    onError: () => setOptimisticStatus(null),
  })

  const linkDefect = useMutation({
    mutationFn: () => api.linkExecutionDefect(projectId, runId, exec.id, defectInput.trim()),
    onSuccess: updated => {
      onUpdated(updated)
      setDefectInput('')
    },
  })

  const unlinkDefect = useMutation({
    mutationFn: () => api.unlinkExecutionDefect(projectId, runId, exec.id),
    onSuccess: () =>
      onUpdated({
        ...exec,
        defectId: null,
        defectUrl: null,
        defectTitle: null,
        defectState: null,
      }),
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

        {/* Action buttons (hidden when the run is read-only / completed) */}
        {editable && (
          <div className="flex items-center gap-0.5 shrink-0">
            {mutation.isPending && (
              <Loader2 size={14} className="animate-spin text-slate-400 mr-1" />
            )}
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
        )}
      </div>

      {/* Actual result textarea — shown when FAILED */}
      {showActualResult && (
        <div className="mt-2 ml-[92px]">
          <textarea
            value={actualResult}
            onChange={e => setActualResult(e.target.value)}
            onBlur={handleActualResultBlur}
            readOnly={!editable}
            rows={2}
            placeholder="Describe actual result (optional)"
            className="w-full border border-red-200 rounded-lg px-3 py-2 text-xs text-slate-700 resize-none focus:outline-none focus:ring-1 focus:ring-red-400 bg-white read-only:bg-slate-50"
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

      {/* Linked ADO defect */}
      <div className="mt-2 ml-[92px]">
        {exec.defectId ? (
          <div className="flex items-center gap-2 text-xs">
            <Bug size={13} className="text-red-500 shrink-0" />
            <a
              href={exec.defectUrl ?? '#'}
              target="_blank"
              rel="noreferrer"
              className="font-mono text-blue-600 hover:underline shrink-0"
            >
              #{exec.defectId}
            </a>
            {exec.defectState && (
              <Badge label={exec.defectState} colorClass="text-slate-600 bg-slate-100" />
            )}
            {exec.defectTitle && (
              <span className="text-slate-500 truncate max-w-md">{exec.defectTitle}</span>
            )}
            {editable && (
              <button
                onClick={() => unlinkDefect.mutate()}
                disabled={unlinkDefect.isPending}
                title="Unlink defect"
                className="p-0.5 text-slate-300 hover:text-red-600 rounded"
              >
                <X size={13} />
              </button>
            )}
          </div>
        ) : (
          editable && (
            <div className="flex items-center gap-2">
              <input
                value={defectInput}
                onChange={e => setDefectInput(e.target.value)}
                placeholder="Link ADO work item id…"
                className="w-44 border border-slate-200 rounded-lg px-2 py-1 text-xs font-mono focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
              <button
                onClick={() => linkDefect.mutate()}
                disabled={!defectInput.trim() || linkDefect.isPending}
                className="flex items-center gap-1 px-2 py-1 text-xs font-medium text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-40"
              >
                {linkDefect.isPending ? (
                  <Loader2 size={12} className="animate-spin" />
                ) : (
                  <Bug size={12} />
                )}
                Link defect
              </button>
              {linkDefect.isError && (
                <span className="text-xs text-red-600">{(linkDefect.error as Error).message}</span>
              )}
            </div>
          )
        )}
      </div>

      {/* Evidence attachments */}
      <div className="mt-2 ml-[92px]">
        <button
          onClick={() => setShowEvidence(v => !v)}
          className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-blue-600"
        >
          <Paperclip size={12} />
          Evidence{showEvidence ? '' : '…'}
        </button>
        {showEvidence && (
          <div className="mt-2 space-y-1.5">
            {attachmentsQ.isLoading && (
              <div className="text-xs text-slate-400 flex items-center gap-1">
                <Loader2 size={12} className="animate-spin" /> Loading…
              </div>
            )}
            {!attachmentsQ.isLoading && attachments.length === 0 && (
              <div className="text-xs text-slate-400">No evidence attached.</div>
            )}
            {attachments.map(a => (
              <div key={a.id} className="flex items-center gap-2 text-xs">
                <Paperclip size={12} className="text-slate-400 shrink-0" />
                <a
                  href={api.attachmentDownloadUrl(projectId, runId, a.id)}
                  target="_blank"
                  rel="noreferrer"
                  className="text-blue-600 hover:underline truncate max-w-xs flex items-center gap-1"
                >
                  {a.fileName} <Download size={11} />
                </a>
                <span className="text-slate-400 shrink-0">{formatBytes(a.sizeBytes)}</span>
                {editable && (
                  <button
                    onClick={() => deleteAttachment.mutate(a.id)}
                    disabled={deleteAttachment.isPending}
                    title="Delete attachment"
                    className="p-0.5 text-slate-300 hover:text-red-600 rounded"
                  >
                    <Trash2 size={12} />
                  </button>
                )}
              </div>
            ))}
            {editable && (
              <label className="inline-flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer hover:text-blue-600">
                {uploadAttachment.isPending ? (
                  <Loader2 size={12} className="animate-spin" />
                ) : (
                  <Plus size={12} />
                )}
                Add evidence
                <input
                  type="file"
                  className="hidden"
                  onChange={e => {
                    const f = e.target.files?.[0]
                    if (f) uploadAttachment.mutate(f)
                    e.target.value = ''
                  }}
                />
              </label>
            )}
            {uploadAttachment.isError && (
              <div className="text-xs text-red-600">
                {(uploadAttachment.error as Error).message}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function TestRunExecutionPage() {
  const { projectId, base } = useProject()
  const { runId } = useParams<{ runId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // Local optimistic state for executions list
  const [localExecutions, setLocalExecutions] = useState<TestCaseExecution[] | null>(null)
  const [showAddCases, setShowAddCases] = useState(false)
  const [showEditScope, setShowEditScope] = useState(false)

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
    dataUpdatedAt: execUpdatedAt,
  } = useQuery({
    queryKey: ['runExecutions', projectId, runId],
    queryFn: () => api.runExecutions(projectId!, runId!),
    enabled: !!projectId && !!runId,
  })

  // Drop optimistic overrides whenever fresh server data arrives. Must NOT setState during
  // render (that caused an infinite render loop — React #301); do it in an effect instead.
  useEffect(() => {
    setLocalExecutions(null)
  }, [execUpdatedAt])

  const completeMutation = useMutation({
    mutationFn: () => api.completeTestRun(projectId!, runId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['testRun', projectId, runId] })
      queryClient.invalidateQueries({ queryKey: ['testRuns', projectId] })
    },
  })

  const reopenMutation = useMutation({
    mutationFn: () => api.reopenTestRun(projectId!, runId!),
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
  const editable = run.status === 'IN_PROGRESS'

  function handleComplete() {
    if (
      pendingCount > 0 &&
      !window.confirm(
        `${pendingCount} test ${pendingCount === 1 ? 'case is' : 'cases are'} still pending — complete anyway?`,
      )
    ) {
      return
    }
    completeMutation.mutate()
  }

  function handleExecutionUpdated(updated: TestCaseExecution) {
    const base = localExecutions ?? executions
    setLocalExecutions(base.map(e => (e.id === updated.id ? updated : e)))
  }

  return (
    <div className="space-y-6 h-full min-h-0 overflow-y-auto pr-1">
      {/* Header */}
      <div>
        <button
          onClick={() => navigate(`${base}/test-execution`)}
          className="flex items-center gap-1 text-sm text-slate-500 hover:text-blue-600 transition-colors mb-2"
        >
          <ChevronLeft size={14} /> Test Execution
        </button>
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-2xl font-bold text-slate-900">{run.name}</h1>
              <Badge label={run.environment} colorClass={envColor(run.environment)} />
              <Badge label={run.status.replace('_', ' ')} colorClass={runStatusColor(run.status)} />
            </div>
            {run.triggeredBy && (
              <p className="text-sm text-slate-500 mt-1">Triggered by {run.triggeredBy}</p>
            )}
            {/* Scope summary */}
            <div className="flex items-center gap-2 mt-2 flex-wrap text-xs">
              <ScopeChip label="Release" value={run.releaseName} />
              <ScopeChip label="Sprint" value={shortPath(run.iterationPath)} />
              <ScopeChip label="Area" value={shortPath(run.areaPath)} />
              <ScopeChip label="Team" value={run.teamName} />
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {editable && (
              <button
                onClick={() => setShowEditScope(true)}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors shrink-0 text-slate-700 bg-white border border-slate-200 hover:bg-slate-50"
                title="Edit scope (release / sprint / area / team) and environment"
              >
                <SlidersHorizontal size={15} /> Edit scope
              </button>
            )}
            {editable && (
              <button
                onClick={() => setShowAddCases(true)}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors shrink-0 text-slate-700 bg-white border border-slate-200 hover:bg-slate-50"
                title="Add existing approved test cases to this run"
              >
                <Plus size={15} /> Add cases
              </button>
            )}
            {run.status === 'IN_PROGRESS' && (
              <button
                onClick={handleComplete}
                disabled={completeMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors shrink-0 text-white bg-green-600 hover:bg-green-700 disabled:opacity-50"
                title={
                  pendingCount > 0 ? `${pendingCount} test cases still pending` : 'Complete run'
                }
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
            {run.status === 'COMPLETED' && (
              <button
                onClick={() => reopenMutation.mutate()}
                disabled={reopenMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors shrink-0 text-blue-700 bg-blue-50 border border-blue-200 hover:bg-blue-100 disabled:opacity-50"
                title="Reopen this run to edit results or add cases"
              >
                {reopenMutation.isPending ? (
                  <Loader2 size={15} className="animate-spin" />
                ) : (
                  <RotateCcw size={15} />
                )}
                Reopen
              </button>
            )}
          </div>
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
              editable={editable}
              onUpdated={handleExecutionUpdated}
            />
          ))}
        </div>
      </div>

      {showAddCases && (
        <AddCasesModal
          projectId={projectId!}
          runId={runId!}
          run={run}
          existingCaseIds={new Set(displayExecutions.map(e => e.testCaseId))}
          onClose={() => setShowAddCases(false)}
          onAdded={() => {
            setShowAddCases(false)
            queryClient.invalidateQueries({ queryKey: ['testRun', projectId, runId] })
            queryClient.invalidateQueries({ queryKey: ['runExecutions', projectId, runId] })
          }}
        />
      )}

      {showEditScope && (
        <EditScopeModal
          projectId={projectId!}
          runId={runId!}
          run={run}
          onClose={() => setShowEditScope(false)}
          onSaved={() => {
            setShowEditScope(false)
            queryClient.invalidateQueries({ queryKey: ['testRun', projectId, runId] })
          }}
        />
      )}
    </div>
  )
}

// ── Scope chip + helpers ────────────────────────────────────────────────────────

function ScopeChip({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-100 text-slate-600">
      <span className="text-slate-400">{label}:</span> {value}
    </span>
  )
}

function shortPath(p?: string | null): string | null {
  if (!p) return null
  const parts = p.split('\\')
  return parts.length > 1 ? parts.slice(1).join(' / ') : p
}

// ── Edit-scope modal ────────────────────────────────────────────────────────────

function EditScopeModal({
  projectId,
  runId,
  run,
  onClose,
  onSaved,
}: {
  projectId: string
  runId: string
  run: TestRun
  onClose: () => void
  onSaved: () => void
}) {
  const [environment, setEnvironment] = useState(run.environment ?? 'STAGING')
  const [releaseId, setReleaseId] = useState(run.releaseId ?? '')
  const [iterationPath, setIterationPath] = useState(run.iterationPath ?? '')
  const [areaPath, setAreaPath] = useState(run.areaPath ?? '')
  const [teamId, setTeamId] = useState(run.teamId ?? '')

  const { data: releases = [] } = useQuery({
    queryKey: ['releases', projectId],
    queryFn: () => api.releases(projectId),
  })
  const { data: teams = [] } = useQuery({
    queryKey: ['adoTeams', projectId],
    queryFn: () => api.adoTeams(projectId),
  })
  const { data: areas = [] } = useQuery({
    queryKey: ['adoAreas', projectId],
    queryFn: () => api.adoAreas(projectId),
  })
  const { data: iterations = [] } = useQuery({
    queryKey: ['adoIterations', projectId],
    queryFn: () => api.adoIterations(projectId),
  })

  const save = useMutation({
    mutationFn: () =>
      api.updateTestRun(projectId, runId, {
        environment,
        releaseId: releaseId || undefined,
        iterationPath: iterationPath || undefined,
        areaPath: areaPath || undefined,
        teamId: teamId || undefined,
      }),
    onSuccess: onSaved,
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">Edit scope & environment</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          <Field label="Environment">
            <select
              value={environment}
              onChange={e => setEnvironment(e.target.value)}
              className={scopeInput}
            >
              {['DEV', 'STAGING', 'PROD'].map(v => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Release">
            <select
              value={releaseId}
              onChange={e => setReleaseId(e.target.value)}
              className={scopeInput}
            >
              <option value="">— none —</option>
              {releases.map(r => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Sprint">
            <select
              value={iterationPath}
              onChange={e => setIterationPath(e.target.value)}
              className={scopeInput}
            >
              <option value="">— none —</option>
              {iterations.map(i => (
                <option key={i.id} value={i.path}>
                  {shortPath(i.path)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Area">
            <select
              value={areaPath}
              onChange={e => setAreaPath(e.target.value)}
              className={scopeInput}
            >
              <option value="">— none —</option>
              {areas.map(a => (
                <option key={a.id} value={a.path}>
                  {shortPath(a.path)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Team">
            <select value={teamId} onChange={e => setTeamId(e.target.value)} className={scopeInput}>
              <option value="">— none —</option>
              {teams.map(t => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </Field>
          {save.isError && <p className="text-sm text-red-600">{(save.error as Error).message}</p>}
        </div>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            onClick={() => save.mutate()}
            disabled={save.isPending}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
          >
            {save.isPending && <Loader2 size={14} className="animate-spin" />}Save scope
          </button>
        </div>
      </div>
    </div>
  )
}

const scopeInput =
  'w-full text-sm border border-slate-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-700 mb-1">{label}</label>
      {children}
    </div>
  )
}

// ── Add-cases modal: scope-filtered, APPROVED-only picker ───────────────────────

function AddCasesModal({
  projectId,
  runId,
  run,
  existingCaseIds,
  onClose,
  onAdded,
}: {
  projectId: string
  runId: string
  run: TestRun
  existingCaseIds: Set<string>
  onClose: () => void
  onAdded: () => void
}) {
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const { data: cases = [], isLoading } = useQuery({
    queryKey: ['selectableCases', projectId, run.areaPath, run.iterationPath, run.teamId],
    queryFn: () =>
      api.selectableTestCases(projectId, {
        status: 'APPROVED',
        area: run.areaPath ?? undefined,
        iteration: run.iterationPath ?? undefined,
        teamId: run.teamId ?? undefined,
      }),
  })

  const addMutation = useMutation({
    mutationFn: () => api.addTestRunCases(projectId, runId, { testCaseIds: [...selected] }),
    onSuccess: onAdded,
  })

  // Cases already in the run can't be added again (idempotent).
  const available = cases.filter(c => !existingCaseIds.has(c.id))
  const shown = available.filter(
    c =>
      !q.trim() ||
      c.title.toLowerCase().includes(q.toLowerCase()) ||
      (c.externalId ?? '').toLowerCase().includes(q.toLowerCase()),
  )

  function toggle(id: string) {
    setSelected(prev => {
      const n = new Set(prev)
      n.has(id) ? n.delete(id) : n.add(id)
      return n
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-xl mx-4 max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div>
            <h2 className="font-semibold text-slate-900">Add test cases</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Approved cases in this run&apos;s scope · {selected.size} selected
            </p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>
        <div className="px-5 py-3 border-b border-slate-100">
          <input
            value={q}
            onChange={e => setQ(e.target.value)}
            placeholder="Filter by title or id…"
            className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div className="flex-1 overflow-y-auto divide-y divide-slate-50">
          {isLoading && (
            <div className="py-10 text-center text-sm text-slate-400 flex items-center justify-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Loading approved cases…
            </div>
          )}
          {!isLoading && shown.length === 0 && (
            <div className="py-10 text-center text-sm text-slate-500">
              No approved cases available to add in this scope.
            </div>
          )}
          {shown.map(c => (
            <label
              key={c.id}
              className="flex items-center gap-3 px-5 py-2.5 cursor-pointer hover:bg-slate-50"
            >
              <input
                type="checkbox"
                checked={selected.has(c.id)}
                onChange={() => toggle(c.id)}
                className="rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0"
              />
              {c.externalId && (
                <span className="text-xs font-mono text-slate-400 shrink-0">{c.externalId}</span>
              )}
              <span className="text-sm text-slate-800 flex-1 truncate">{c.title}</span>
            </label>
          ))}
        </div>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-between items-center">
          <span className="text-xs text-slate-400">{available.length} available</span>
          <div className="flex gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              onClick={() => addMutation.mutate()}
              disabled={selected.size === 0 || addMutation.isPending}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
            >
              {addMutation.isPending && <Loader2 size={14} className="animate-spin" />}
              Add {selected.size > 0 ? selected.size : ''} case{selected.size === 1 ? '' : 's'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
