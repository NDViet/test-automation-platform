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
import { Button } from '@/components/ui'
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
      return 'text-success bg-success-bg'
    case 'FAILED':
      return 'text-danger bg-danger-bg'
    case 'BLOCKED':
      return 'text-warning bg-warning-bg'
    case 'SKIPPED':
      return 'text-neutral bg-neutral-bg'
    case 'PENDING':
      return 'text-info bg-info-bg'
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

function runStatusColor(status: string): string {
  switch (status) {
    case 'IN_PROGRESS':
      return 'text-info bg-info-bg'
    case 'COMPLETED':
      return 'text-success bg-success-bg'
    case 'ABORTED':
      return 'text-danger bg-danger-bg'
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

function envColor(env: string): string {
  switch (env?.toUpperCase()) {
    case 'PROD':
      return 'text-danger bg-danger-bg'
    case 'STAGING':
      return 'text-warning bg-warning-bg'
    case 'DEV':
      return 'text-info bg-info-bg'
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

// ── Progress bar ──────────────────────────────────────────────────────────────

function ProgressSection({ run }: { run: TestRun }) {
  const { totalTests, passed, failed, blocked, skipped, pending } = run

  const segments = [
    { label: 'Passed', count: passed, color: 'bg-success' },
    { label: 'Failed', count: failed, color: 'bg-danger' },
    { label: 'Blocked', count: blocked, color: 'bg-warning' },
    { label: 'Skipped', count: skipped, color: 'bg-border-strong' },
    { label: 'Pending', count: pending, color: 'bg-info/40' },
  ]

  return (
    <div className="bg-surface rounded-xl border border-border shadow-sm p-5">
      <div className="flex items-center gap-6 mb-4 flex-wrap">
        {segments.map(seg => (
          <div key={seg.label} className="flex items-center gap-2">
            <div className={cn('w-3 h-3 rounded-full', seg.color)} />
            <span className="text-sm">
              <span className="font-semibold text-fg">{seg.count}</span>
              <span className="text-fg-muted ml-1">{seg.label}</span>
            </span>
          </div>
        ))}
        {totalTests > 0 && (
          <div className="ml-auto text-sm font-semibold text-fg-muted">{totalTests} total</div>
        )}
      </div>

      {totalTests > 0 && (
        <div className="flex h-3 rounded-full overflow-hidden bg-surface-muted">
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
      className: 'text-success hover:bg-success-bg',
    },
    { status: 'FAILED', icon: XCircle, label: 'Fail', className: 'text-danger hover:bg-danger-bg' },
    {
      status: 'BLOCKED',
      icon: MinusCircle,
      label: 'Block',
      className: 'text-warning hover:bg-warning-bg',
    },
    {
      status: 'SKIPPED',
      icon: SkipForward,
      label: 'Skip',
      className: 'text-fg-subtle hover:bg-surface-muted',
    },
  ]

  return (
    <div
      className={cn(
        'px-5 py-3 transition-colors',
        currentStatus === 'FAILED' ? 'bg-danger-bg/40' : '',
        currentStatus === 'PASSED' ? 'bg-success-bg/40' : '',
      )}
    >
      <div className="flex items-center gap-3">
        {/* Status badge */}
        <div className="w-20 shrink-0">
          <Badge label={currentStatus} colorClass={execStatusColor(currentStatus)} />
        </div>

        {/* Title */}
        <p className="flex-1 text-sm text-fg truncate">{exec.testCaseTitle}</p>

        {/* Action buttons (hidden when the run is read-only / completed) */}
        {editable && (
          <div className="flex items-center gap-0.5 shrink-0">
            {mutation.isPending && (
              <Loader2 size={14} className="animate-spin text-fg-subtle mr-1" />
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
            className="w-full border border-danger-border rounded-lg px-3 py-2 text-xs text-fg resize-none focus:outline-none focus:ring-1 focus:ring-danger/40 bg-surface read-only:bg-surface-muted"
          />
        </div>
      )}

      {/* Executed by / at */}
      {exec.executedAt && (
        <div className="mt-1 ml-[92px] text-xs text-fg-subtle">
          {exec.executedBy ? `${exec.executedBy} · ` : ''}
          {relativeTime(exec.executedAt)}
        </div>
      )}

      {/* Linked ADO defect */}
      <div className="mt-2 ml-[92px]">
        {exec.defectId ? (
          <div className="flex items-center gap-2 text-xs">
            <Bug size={13} className="text-danger shrink-0" />
            <a
              href={exec.defectUrl ?? '#'}
              target="_blank"
              rel="noreferrer"
              className="font-mono text-primary hover:underline shrink-0"
            >
              #{exec.defectId}
            </a>
            {exec.defectState && (
              <Badge label={exec.defectState} colorClass="text-neutral bg-neutral-bg" />
            )}
            {exec.defectTitle && (
              <span className="text-fg-muted truncate max-w-md">{exec.defectTitle}</span>
            )}
            {editable && (
              <button
                onClick={() => unlinkDefect.mutate()}
                disabled={unlinkDefect.isPending}
                title="Unlink defect"
                className="p-0.5 text-fg-subtle hover:text-danger rounded"
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
                className="w-44 border border-border-strong rounded-lg px-2 py-1 text-xs font-mono bg-surface text-fg focus:outline-none focus:ring-1 focus:ring-primary/30"
              />
              <button
                onClick={() => linkDefect.mutate()}
                disabled={!defectInput.trim() || linkDefect.isPending}
                className="flex items-center gap-1 px-2 py-1 text-xs font-medium text-fg-muted border border-border rounded-lg hover:bg-surface-muted disabled:opacity-40"
              >
                {linkDefect.isPending ? (
                  <Loader2 size={12} className="animate-spin" />
                ) : (
                  <Bug size={12} />
                )}
                Link defect
              </button>
              {linkDefect.isError && (
                <span className="text-xs text-danger">{(linkDefect.error as Error).message}</span>
              )}
            </div>
          )
        )}
      </div>

      {/* Evidence attachments */}
      <div className="mt-2 ml-[92px]">
        <button
          onClick={() => setShowEvidence(v => !v)}
          className="flex items-center gap-1.5 text-xs text-fg-muted hover:text-primary"
        >
          <Paperclip size={12} />
          Evidence{showEvidence ? '' : '…'}
        </button>
        {showEvidence && (
          <div className="mt-2 space-y-1.5">
            {attachmentsQ.isLoading && (
              <div className="text-xs text-fg-subtle flex items-center gap-1">
                <Loader2 size={12} className="animate-spin" /> Loading…
              </div>
            )}
            {!attachmentsQ.isLoading && attachments.length === 0 && (
              <div className="text-xs text-fg-subtle">No evidence attached.</div>
            )}
            {attachments.map(a => (
              <div key={a.id} className="flex items-center gap-2 text-xs">
                <Paperclip size={12} className="text-fg-subtle shrink-0" />
                <a
                  href={api.attachmentDownloadUrl(projectId, runId, a.id)}
                  target="_blank"
                  rel="noreferrer"
                  className="text-primary hover:underline truncate max-w-xs flex items-center gap-1"
                >
                  {a.fileName} <Download size={11} />
                </a>
                <span className="text-fg-subtle shrink-0">{formatBytes(a.sizeBytes)}</span>
                {editable && (
                  <button
                    onClick={() => deleteAttachment.mutate(a.id)}
                    disabled={deleteAttachment.isPending}
                    title="Delete attachment"
                    className="p-0.5 text-fg-subtle hover:text-danger rounded"
                  >
                    <Trash2 size={12} />
                  </button>
                )}
              </div>
            ))}
            {editable && (
              <label className="inline-flex items-center gap-1.5 text-xs text-fg-muted cursor-pointer hover:text-primary">
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
              <div className="text-xs text-danger">{(uploadAttachment.error as Error).message}</div>
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
          className="flex items-center gap-1 text-sm text-fg-muted hover:text-primary transition-colors mb-2"
        >
          <ChevronLeft size={14} /> Test Execution
        </button>
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-2xl font-bold text-fg">{run.name}</h1>
              <Badge label={run.environment} colorClass={envColor(run.environment)} />
              <Badge label={run.status.replace('_', ' ')} colorClass={runStatusColor(run.status)} />
            </div>
            {run.triggeredBy && (
              <p className="text-sm text-fg-muted mt-1">Triggered by {run.triggeredBy}</p>
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
              <Button
                variant="secondary"
                onClick={() => setShowEditScope(true)}
                title="Edit scope (release / sprint / area / team) and environment"
              >
                <SlidersHorizontal size={15} /> Edit scope
              </Button>
            )}
            {editable && (
              <Button
                variant="secondary"
                onClick={() => setShowAddCases(true)}
                title="Add existing approved test cases to this run"
              >
                <Plus size={15} /> Add cases
              </Button>
            )}
            {run.status === 'IN_PROGRESS' && (
              <button
                onClick={handleComplete}
                disabled={completeMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-all shrink-0 text-white bg-success hover:brightness-95 disabled:opacity-50"
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
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-all shrink-0 text-primary bg-primary-subtle border border-primary-subtle hover:brightness-95 disabled:opacity-50"
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
        <div className="flex items-center gap-2 px-4 py-3 bg-info-bg border border-info-border rounded-lg text-sm text-info">
          <AlertCircle size={15} className="shrink-0" />
          {pendingCount} test {pendingCount === 1 ? 'case' : 'cases'} still pending — click Pass /
          Fail / Block / Skip to record results.
        </div>
      )}

      {/* Execution list */}
      <div className="bg-surface rounded-xl border border-border shadow-sm">
        <div className="px-5 py-3 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg text-sm">Test Case Results</h2>
          <span className="text-xs text-fg-subtle">{displayExecutions.length} test cases</span>
        </div>
        {displayExecutions.length === 0 && (
          <div className="py-12 text-center text-sm text-fg-muted">No test cases in this run.</div>
        )}
        <div className="divide-y divide-border">
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
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-surface-muted text-fg-muted">
      <span className="text-fg-subtle">{label}:</span> {value}
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
      <div className="bg-surface rounded-xl shadow-md w-full max-w-lg mx-4 max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="font-semibold text-fg">Edit scope & environment</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg">
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
          {save.isError && <p className="text-sm text-danger">{(save.error as Error).message}</p>}
        </div>
        <div className="px-5 py-4 border-t border-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => save.mutate()} loading={save.isPending}>
            Save scope
          </Button>
        </div>
      </div>
    </div>
  )
}

const scopeInput =
  'w-full text-sm border border-border-strong rounded-lg px-3 py-2 bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-fg-muted mb-1">{label}</label>
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
      <div className="bg-surface rounded-xl shadow-md w-full max-w-xl mx-4 max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div>
            <h2 className="font-semibold text-fg">Add test cases</h2>
            <p className="text-xs text-fg-muted mt-0.5">
              Approved cases in this run&apos;s scope · {selected.size} selected
            </p>
          </div>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg">
            <X size={18} />
          </button>
        </div>
        <div className="px-5 py-3 border-b border-border">
          <input
            value={q}
            onChange={e => setQ(e.target.value)}
            placeholder="Filter by title or id…"
            className={scopeInput}
          />
        </div>
        <div className="flex-1 overflow-y-auto divide-y divide-border">
          {isLoading && (
            <div className="py-10 text-center text-sm text-fg-subtle flex items-center justify-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Loading approved cases…
            </div>
          )}
          {!isLoading && shown.length === 0 && (
            <div className="py-10 text-center text-sm text-fg-muted">
              No approved cases available to add in this scope.
            </div>
          )}
          {shown.map(c => (
            <label
              key={c.id}
              className="flex items-center gap-3 px-5 py-2.5 cursor-pointer hover:bg-surface-muted"
            >
              <input
                type="checkbox"
                checked={selected.has(c.id)}
                onChange={() => toggle(c.id)}
                className="rounded border-border-strong accent-primary shrink-0"
              />
              {c.externalId && (
                <span className="text-xs font-mono text-fg-subtle shrink-0">{c.externalId}</span>
              )}
              <span className="text-sm text-fg flex-1 truncate">{c.title}</span>
            </label>
          ))}
        </div>
        <div className="px-5 py-4 border-t border-border flex justify-between items-center">
          <span className="text-xs text-fg-subtle">{available.length} available</span>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button
              onClick={() => addMutation.mutate()}
              disabled={selected.size === 0}
              loading={addMutation.isPending}
            >
              Add {selected.size > 0 ? selected.size : ''} case{selected.size === 1 ? '' : 's'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
