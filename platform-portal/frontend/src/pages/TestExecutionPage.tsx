import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { UnifiedExecutionItem, CreateTestRunForm } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button } from '@/components/ui'
import {
  Plus,
  Bot,
  User,
  GitBranch,
  Tag,
  ExternalLink,
  MonitorCheck,
  PlayCircle,
} from 'lucide-react'

// ── Helpers ───────────────────────────────────────────────────────────────────

type TypeTab = 'ALL' | 'MANUAL' | 'AUTOMATED'

function statusColors(type: string, status: string): string {
  if (type === 'AUTOMATED') {
    switch (status) {
      case 'RUNNING':
        return 'bg-info-bg text-info'
      case 'COMPLETED':
        return 'bg-success-bg text-success'
      default:
        return 'bg-neutral-bg text-neutral'
    }
  }
  switch (status) {
    case 'IN_PROGRESS':
      return 'bg-info-bg text-info'
    case 'COMPLETED':
      return 'bg-success-bg text-success'
    case 'ABANDONED':
      return 'bg-danger-bg text-danger'
    default:
      return 'bg-neutral-bg text-neutral'
  }
}

function statusLabel(status: string): string {
  return status.replace(/_/g, ' ')
}

function envColor(env: string | null): string {
  switch (env?.toUpperCase()) {
    case 'PROD':
      return 'text-danger bg-danger-bg border-danger-border'
    case 'STAGING':
      return 'text-warning bg-warning-bg border-warning-border'
    case 'CI':
    case 'DEV':
      return 'text-info bg-info-bg border-info-border'
    default:
      return 'text-fg-muted bg-surface-muted border-border'
  }
}

function ciProviderIcon(provider: string | null): string {
  switch (provider?.toLowerCase()) {
    case 'github':
      return '⬡'
    case 'gitlab':
      return '🦊'
    case 'circleci':
      return '◎'
    case 'azure-devops':
      return '☁'
    case 'jenkins':
      return '🏗'
    default:
      return '⚡'
  }
}

function shortSha(sha: string | null): string {
  return sha && sha.length >= 7 ? sha.slice(0, 7) : (sha ?? '')
}

// ── Pass-rate bar ─────────────────────────────────────────────────────────────

function PassBar({ item }: { item: UnifiedExecutionItem }) {
  const { totalTests, passed, failed, blocked, skipped, broken, pending, passRate } = item
  if (!totalTests) return <div className="h-1.5 bg-surface-muted rounded-full w-full" />

  const pct = (n: number) => `${((n / totalTests) * 100).toFixed(1)}%`
  const rateColor = passRate >= 0.9 ? '#16a34a' : passRate >= 0.75 ? '#ca8a04' : '#dc2626'

  return (
    <div className="space-y-1">
      <div className="flex h-1.5 rounded-full overflow-hidden bg-surface-muted">
        <div className="bg-success" style={{ width: pct(passed) }} />
        <div className="bg-danger" style={{ width: pct(failed) }} />
        {broken > 0 && <div className="bg-warning" style={{ width: pct(broken) }} />}
        {blocked > 0 && <div className="bg-warning/60" style={{ width: pct(blocked) }} />}
        {skipped > 0 && <div className="bg-border-strong" style={{ width: pct(skipped) }} />}
        {pending > 0 && <div className="bg-border" style={{ width: pct(pending) }} />}
      </div>
      <div className="flex items-center gap-2 text-xs text-fg-muted flex-wrap">
        <span className="text-success font-medium">{passed}✓</span>
        {failed > 0 && <span className="text-danger">{failed}✗</span>}
        {broken > 0 && <span className="text-warning">{broken} broken</span>}
        {blocked > 0 && <span className="text-warning">{blocked} blocked</span>}
        {pending > 0 && <span className="text-fg-subtle">{pending} pending</span>}
        {skipped > 0 && <span className="text-fg-subtle">{skipped} skipped</span>}
        <span className="ml-auto font-semibold tabular-nums" style={{ color: rateColor }}>
          {(passRate * 100).toFixed(0)}%
        </span>
      </div>
    </div>
  )
}

// ── Sprint linker ─────────────────────────────────────────────────────────────

function LinkSprintButton({
  item,
  projectId,
  onLinked,
}: {
  item: UnifiedExecutionItem
  projectId: string
  onLinked: () => void
}) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)

  const { data: iterations = [] } = useQuery({
    queryKey: ['adoIterations', projectId],
    queryFn: () => api.adoIterations(projectId),
    enabled: open,
  })

  const mutation = useMutation({
    mutationFn: (iterationPath: string) =>
      api.updateExecutionScope(projectId, item.runId, { iterationPath }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['unifiedExecutions', projectId] })
      setOpen(false)
      onLinked()
    },
  })

  const current = item.iterationPath ? item.iterationPath.split('\\').pop() : null

  return (
    <div className="relative" onClick={e => e.stopPropagation()}>
      <button
        title={current ? `Sprint: ${item.iterationPath}` : 'Link to sprint'}
        onClick={() => setOpen(o => !o)}
        className={cn(
          'flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded border transition-colors',
          current
            ? 'text-primary bg-primary-subtle border-primary-subtle hover:brightness-95'
            : 'text-fg-subtle border-border hover:text-primary hover:border-primary/40 hover:bg-primary-subtle',
        )}
      >
        <GitBranch size={9} />
        {current ?? 'Sprint'}
      </button>

      {open && (
        <div className="absolute right-0 top-6 z-20 w-64 bg-surface border border-border rounded-lg shadow-md py-1">
          <p className="px-3 py-1.5 text-[10px] font-semibold text-fg-subtle uppercase tracking-wide">
            Link to sprint
          </p>
          {current && (
            <button
              onClick={() => mutation.mutate('')}
              className="w-full text-left px-3 py-1.5 text-xs text-danger hover:bg-danger-bg"
            >
              ✕ Remove sprint link
            </button>
          )}
          {iterations.length === 0 && (
            <p className="px-3 py-2 text-xs text-fg-subtle">No sprints configured.</p>
          )}
          {iterations.map(it => (
            <button
              key={it.id}
              onClick={() => mutation.mutate(it.path)}
              disabled={mutation.isPending}
              className={cn(
                'w-full text-left px-3 py-1.5 text-xs hover:bg-surface-muted flex items-center justify-between',
                it.path === item.iterationPath ? 'text-primary font-medium' : 'text-fg',
              )}
            >
              <span className="truncate">{it.path.split('\\').pop()}</span>
              {it.path === item.iterationPath && <span className="text-primary">✓</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Single row ────────────────────────────────────────────────────────────────

function ExecutionRow({
  item,
  onClick,
  projectId,
  onLinked,
}: {
  item: UnifiedExecutionItem
  onClick: () => void
  projectId: string
  onLinked: () => void
}) {
  const isManual = item.type === 'MANUAL'

  return (
    <div
      onClick={onClick}
      className="px-5 py-3.5 hover:bg-surface-muted cursor-pointer transition-colors border-b border-border last:border-0"
    >
      <div className="flex items-start gap-3">
        {/* Type indicator */}
        <div className="mt-0.5 shrink-0">
          {isManual ? (
            <User size={15} className="text-info" />
          ) : (
            <Bot size={15} className="text-teal-600" />
          )}
        </div>

        {/* Main content */}
        <div className="flex-1 min-w-0 space-y-1.5">
          {/* Row 1: name + badges */}
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-medium text-sm text-fg truncate max-w-xs">{item.name}</span>

            {/* Type badge */}
            <span
              className={cn(
                'text-[10px] font-semibold uppercase tracking-wide px-1.5 py-0.5 rounded-full border',
                isManual
                  ? 'bg-info-bg text-info border-info-border'
                  : 'bg-teal-50 text-teal-700 border-teal-200',
              )}
            >
              {isManual ? 'Manual' : 'Automated'}
            </span>

            {/* Status */}
            <span
              className={cn(
                'text-[10px] font-medium px-1.5 py-0.5 rounded-full',
                statusColors(item.type, item.status),
              )}
            >
              {statusLabel(item.status)}
            </span>

            {/* Environment */}
            {item.environment && (
              <span
                className={cn(
                  'text-[10px] font-medium px-1.5 py-0.5 rounded border',
                  envColor(item.environment),
                )}
              >
                {item.environment.toUpperCase()}
              </span>
            )}

            {/* CI provider */}
            {!isManual && item.ciProvider && (
              <span className="text-[10px] text-fg-subtle" title={item.ciProvider}>
                {ciProviderIcon(item.ciProvider)} {item.ciProvider}
              </span>
            )}

            {/* Trigger type */}
            {!isManual && item.triggerType && (
              <span className="text-[10px] text-fg-subtle">
                {item.triggerType.replace('_', ' ').toLowerCase()}
              </span>
            )}
          </div>

          {/* Row 2: scope tags */}
          <div className="flex items-center gap-2 flex-wrap text-xs text-fg-subtle">
            {item.teamName && (
              <span className="flex items-center gap-1">
                <Tag size={10} /> {item.teamName}
              </span>
            )}
            {item.areaPath && <span className="text-fg-subtle font-light">·</span>}
            {item.areaPath && <span>{item.areaPath.split('\\').pop()}</span>}
            {item.iterationPath && (
              <>
                <span className="text-fg-subtle font-light">·</span>
                <span>{item.iterationPath.split('\\').pop()}</span>
              </>
            )}
            {item.releaseName && (
              <>
                <span className="text-fg-subtle font-light">·</span>
                <span className="text-fg-muted">{item.releaseName}</span>
              </>
            )}
            {/* Automated: branch / sha */}
            {!isManual && item.branch && (
              <span className="flex items-center gap-1">
                <GitBranch size={10} /> {item.branch}
              </span>
            )}
            {!isManual && item.commitSha && (
              <span className="font-mono text-[10px] bg-surface-muted px-1 rounded">
                {shortSha(item.commitSha)}
              </span>
            )}
            {item.triggeredBy && <span className="text-fg-subtle">by {item.triggeredBy}</span>}
          </div>

          {/* Row 3: pass rate bar */}
          {item.totalTests > 0 && (
            <div className="mt-0.5">
              <PassBar item={item} />
            </div>
          )}
          {item.totalTests === 0 && (
            <p className="text-xs text-fg-subtle italic">No test results yet</p>
          )}
        </div>

        {/* Right: sprint link + date + duration */}
        <div className="shrink-0 text-right space-y-1 min-w-[90px]">
          {!isManual && (
            <div className="flex justify-end">
              <LinkSprintButton item={item} projectId={projectId} onLinked={onLinked} />
            </div>
          )}
          <p className="text-xs text-fg-subtle">{relativeTime(item.date)}</p>
          {item.durationMs > 0 && (
            <p className="text-[10px] text-fg-subtle">
              {item.durationMs < 60_000
                ? `${(item.durationMs / 1000).toFixed(1)}s`
                : `${Math.round(item.durationMs / 60_000)}m`}
            </p>
          )}
          {item.ciRunUrl && (
            <a
              href={item.ciRunUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={e => e.stopPropagation()}
              className="text-[10px] text-primary hover:underline flex items-center justify-end gap-0.5"
            >
              CI <ExternalLink size={9} />
            </a>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Stats strip ───────────────────────────────────────────────────────────────

function StatsStrip({ items }: { items: UnifiedExecutionItem[] }) {
  const manual = items.filter(i => i.type === 'MANUAL').length
  const automated = items.filter(i => i.type === 'AUTOMATED').length
  const running = items.filter(i => ['IN_PROGRESS', 'RUNNING'].includes(i.status)).length
  const avgRate = items.length ? items.reduce((s, i) => s + i.passRate, 0) / items.length : null

  const rateColor =
    avgRate == null
      ? 'text-fg-subtle'
      : avgRate >= 0.9
        ? 'text-success'
        : avgRate >= 0.75
          ? 'text-warning'
          : 'text-danger'

  return (
    <div className="flex items-center gap-6 text-sm text-fg-muted">
      <span>{items.length} total</span>
      {manual > 0 && (
        <span className="flex items-center gap-1">
          <User size={13} className="text-info" />
          {manual} manual
        </span>
      )}
      {automated > 0 && (
        <span className="flex items-center gap-1">
          <Bot size={13} className="text-teal-500" />
          {automated} automated
        </span>
      )}
      {running > 0 && <span className="text-info">{running} active</span>}
      {avgRate != null && (
        <span className={cn('font-semibold tabular-nums', rateColor)}>
          {(avgRate * 100).toFixed(0)}% avg pass
        </span>
      )}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TestExecutionPage() {
  const { projectId, base } = useProject()
  const { filter } = useProjectFilter()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [typeTab, setTypeTab] = useState<TypeTab>('ALL')
  const [showNewModal, setShowNewModal] = useState(false)

  const {
    data: items = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: [
      'unifiedExecutions',
      projectId,
      typeTab,
      filter.teamId,
      filter.area,
      filter.iteration,
    ],
    queryFn: () =>
      api.unifiedExecutions(projectId!, {
        type: typeTab,
        teamId: filter.teamId || undefined,
        area: filter.area || undefined,
        iteration: filter.iteration || undefined,
        limit: 150,
      }),
    enabled: !!projectId,
  })

  function handleClick(item: UnifiedExecutionItem) {
    if (item.type === 'MANUAL') {
      navigate(`${base}/test-execution/manual/${item.runId}`)
    } else {
      navigate(`${base}/runs/${item.runId}`)
    }
  }

  const tabs: { key: TypeTab; label: string; icon: typeof User }[] = [
    { key: 'ALL', label: 'All', icon: MonitorCheck },
    { key: 'MANUAL', label: 'Manual', icon: User },
    { key: 'AUTOMATED', label: 'Automated', icon: Bot },
  ]

  if (isLoading) return <LoadingSpinner message="Loading executions…" />
  if (error)
    return <ErrorMessage message="Failed to load test executions." onRetry={() => void refetch()} />

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-fg">Test Execution</h1>
          <p className="text-sm text-fg-muted mt-0.5">
            All manual runs and automated CI executions, newest first.
          </p>
        </div>
        <Button onClick={() => setShowNewModal(true)}>
          <Plus size={15} /> New Manual Run
        </Button>
      </div>

      {/* Type tabs */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex gap-1 bg-surface-muted p-1 rounded-lg">
          {tabs.map(t => {
            const Icon = t.icon
            return (
              <button
                key={t.key}
                onClick={() => setTypeTab(t.key)}
                className={cn(
                  'flex items-center gap-1.5 px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
                  typeTab === t.key
                    ? 'bg-surface text-fg shadow-xs'
                    : 'text-fg-muted hover:text-fg',
                )}
              >
                <Icon size={13} />
                {t.label}
              </button>
            )
          })}
        </div>
        {items.length > 0 && <StatsStrip items={items} />}
      </div>

      {/* List */}
      <div className="bg-surface rounded-xl border border-border shadow-sm overflow-hidden">
        {items.length === 0 && (
          <div className="py-16 text-center">
            <PlayCircle size={32} className="mx-auto text-fg-subtle mb-3" />
            <p className="text-sm text-fg-muted">No executions found.</p>
            <p className="text-xs text-fg-subtle mt-1">
              {typeTab === 'MANUAL'
                ? 'Create a manual run to start executing test cases.'
                : typeTab === 'AUTOMATED'
                  ? 'Configure the Playwright streaming reporter to stream CI results here.'
                  : 'Create a manual run or configure the CI reporter to see results here.'}
            </p>
          </div>
        )}
        {items.map(item => (
          <ExecutionRow
            key={`${item.type}-${item.id}`}
            item={item}
            onClick={() => handleClick(item)}
            projectId={projectId!}
            onLinked={() =>
              queryClient.invalidateQueries({ queryKey: ['unifiedExecutions', projectId] })
            }
          />
        ))}
      </div>

      {showNewModal && projectId && (
        <NewManualRunModal
          projectId={projectId}
          onClose={() => setShowNewModal(false)}
          onCreated={() =>
            queryClient.invalidateQueries({ queryKey: ['unifiedExecutions', projectId] })
          }
        />
      )}
    </div>
  )
}

// ── New Manual Run Modal (extracted from TestRunsPage) ────────────────────────

function NewManualRunModal({
  projectId,
  onClose,
  onCreated,
}: {
  projectId: string
  onClose: () => void
  onCreated: () => void
}) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [environment, setEnvironment] = useState('DEV')
  const [triggeredBy, setTriggeredBy] = useState('')
  const [assigneeUserId, setAssigneeUserId] = useState('')
  const [selectedTcIds, setSelectedTcIds] = useState<Set<string>>(new Set())
  const [environmentId, setEnvironmentId] = useState('')
  const [matrixType, setMatrixType] = useState<'FULL' | 'PAIRWISE'>('FULL')
  const [releaseId, setReleaseId] = useState('')
  const [iterationPath, setIterationPath] = useState('')
  const [areaPath, setAreaPath] = useState('')
  const [teamId, setTeamId] = useState('')
  const [caseSearch, setCaseSearch] = useState('')
  const [selectedSuiteIds, setSelectedSuiteIds] = useState<Set<string>>(new Set())
  const [suiteResolved, setSuiteResolved] = useState<Record<string, string[]>>({})
  const [suiteSearch, setSuiteSearch] = useState('')
  const [newEnvName, setNewEnvName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: testCases = [], isLoading: tcLoading } = useQuery({
    queryKey: ['selectableTestCases', projectId, iterationPath, areaPath, teamId, caseSearch],
    queryFn: () =>
      api.selectableTestCases(projectId, {
        status: 'APPROVED',
        iteration: iterationPath || undefined,
        area: areaPath || undefined,
        teamId: teamId || undefined,
        q: caseSearch.trim() || undefined,
      }),
  })
  const { data: environments = [] } = useQuery({
    queryKey: ['environments', projectId],
    queryFn: () => api.environments(projectId),
  })
  const { data: releases = [] } = useQuery({
    queryKey: ['releases', projectId],
    queryFn: () => api.releases(projectId),
  })
  const { data: iterations = [] } = useQuery({
    queryKey: ['adoIterations', projectId],
    queryFn: () => api.adoIterations(projectId),
  })
  const { data: areas = [] } = useQuery({
    queryKey: ['adoAreas', projectId],
    queryFn: () => api.adoAreas(projectId),
  })
  const { data: teams = [] } = useQuery({
    queryKey: ['adoTeams', projectId],
    queryFn: () => api.adoTeams(projectId),
  })
  const { data: suites = [] } = useQuery({
    queryKey: ['testSuites', projectId],
    queryFn: () => api.testSuites(projectId),
  })
  const { data: assignableUsers = [] } = useQuery({
    queryKey: ['assignable-users', projectId],
    queryFn: () => api.assignableUsers(projectId),
  })

  const createEnvMutation = useMutation({
    mutationFn: () => api.createEnvironment(projectId, { name: newEnvName.trim() }),
    onSuccess: env => {
      setNewEnvName('')
      setEnvironmentId(env.id)
      setEnvironment(env.name)
      queryClient.invalidateQueries({ queryKey: ['environments', projectId] })
    },
  })

  const mutation = useMutation({
    mutationFn: (body: CreateTestRunForm) => api.createTestRun(projectId, body),
    onSuccess: () => {
      onCreated()
      onClose()
    },
    onError: (err: Error) => setError(err.message),
  })

  const suiteContributedIds = new Set<string>()
  selectedSuiteIds.forEach(sid =>
    (suiteResolved[sid] ?? []).forEach(id => suiteContributedIds.add(id)),
  )
  const effectiveCount = new Set([...selectedTcIds, ...suiteContributedIds]).size
  const filteredSuites = suites.filter(
    s => !suiteSearch.trim() || s.name.toLowerCase().includes(suiteSearch.toLowerCase()),
  )

  async function toggleSuite(suiteId: string) {
    setSelectedSuiteIds(prev => {
      const n = new Set(prev)
      n.has(suiteId) ? n.delete(suiteId) : n.add(suiteId)
      return n
    })
    if (!suiteResolved[suiteId]) {
      const cases = await queryClient.fetchQuery({
        queryKey: ['suiteCases', projectId, suiteId],
        queryFn: () => api.suiteCases(projectId, suiteId),
      })
      setSuiteResolved(prev => ({ ...prev, [suiteId]: cases.map(c => c.id) }))
    }
  }

  function toggleTc(id: string) {
    setSelectedTcIds(prev => {
      const n = new Set(prev)
      n.has(id) ? n.delete(id) : n.add(id)
      return n
    })
  }

  const visibleIds = testCases.map(tc => tc.id)
  const allVisibleSelected = visibleIds.length > 0 && visibleIds.every(id => selectedTcIds.has(id))
  function toggleAll() {
    setSelectedTcIds(prev => {
      const n = new Set(prev)
      if (allVisibleSelected) visibleIds.forEach(id => n.delete(id))
      else visibleIds.forEach(id => n.add(id))
      return n
    })
  }

  function handleSubmit(e?: React.FormEvent) {
    e?.preventDefault()
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    if (effectiveCount === 0) {
      setError('Select at least one test case or suite')
      return
    }
    mutation.mutate({
      name: name.trim(),
      environment,
      environmentId: environmentId || undefined,
      matrixType,
      triggeredBy: triggeredBy.trim() || undefined,
      testCaseIds: Array.from(selectedTcIds),
      suiteIds: selectedSuiteIds.size ? Array.from(selectedSuiteIds) : undefined,
      releaseId: releaseId || undefined,
      iterationPath: iterationPath || undefined,
      areaPath: areaPath || undefined,
      teamId: teamId || undefined,
      assigneeUserId: assigneeUserId || undefined,
    })
  }

  const inp =
    'w-full border border-border-strong rounded-lg px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'
  const lbl = 'block text-xs font-medium text-fg-muted mb-1'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-xl mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="font-semibold text-fg">New Manual Run</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg">
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {error && (
            <p className="text-sm text-danger bg-danger-bg rounded-lg px-3 py-2">{error}</p>
          )}

          <div>
            <label className={lbl}>Run Name *</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              className={inp}
              placeholder="e.g. Sprint 42 Regression"
              autoFocus
            />
          </div>

          <div>
            <div>
              <label className={lbl}>Environment</label>
              {environments.length > 0 ? (
                <select
                  value={environmentId || `label:${environment}`}
                  onChange={e => {
                    const v = e.target.value
                    if (v.startsWith('label:')) {
                      setEnvironmentId('')
                      setEnvironment(v.slice(6))
                    } else {
                      setEnvironmentId(v)
                      const ev = environments.find(x => x.id === v)
                      if (ev) setEnvironment(ev.name)
                    }
                  }}
                  className={inp}
                >
                  <optgroup label="Named environments">
                    {environments.map(env => (
                      <option key={env.id} value={env.id}>
                        {env.name}
                      </option>
                    ))}
                  </optgroup>
                  <optgroup label="Quick label">
                    <option value="label:DEV">DEV</option>
                    <option value="label:STAGING">STAGING</option>
                    <option value="label:PROD">PROD</option>
                  </optgroup>
                </select>
              ) : (
                <select
                  value={environment}
                  onChange={e => setEnvironment(e.target.value)}
                  className={inp}
                >
                  <option value="DEV">DEV</option>
                  <option value="STAGING">STAGING</option>
                  <option value="PROD">PROD</option>
                </select>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="text"
              value={newEnvName}
              onChange={e => setNewEnvName(e.target.value)}
              className="flex-1 border border-border-strong rounded-lg px-3 py-1.5 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary"
              placeholder="New named environment (e.g. PROD-EU)"
            />
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => newEnvName.trim() && createEnvMutation.mutate()}
              disabled={!newEnvName.trim() || createEnvMutation.isPending}
            >
              {createEnvMutation.isPending ? 'Adding…' : '+ Add env'}
            </Button>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={lbl}>Triggered By</label>
              <select
                value={triggeredBy}
                onChange={e => setTriggeredBy(e.target.value)}
                className={inp}
              >
                <option value="">— select a user —</option>
                {assignableUsers.map(u => (
                  <option key={u.id} value={u.username}>
                    {u.displayName || u.username}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={lbl}>Default Assignee</label>
              <select
                value={assigneeUserId}
                onChange={e => setAssigneeUserId(e.target.value)}
                className={inp}
              >
                <option value="">— unassigned —</option>
                {assignableUsers.map(u => (
                  <option key={u.id} value={u.id}>
                    {u.displayName || u.username}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={lbl}>Matrix (parametrized cases)</label>
              <select
                value={matrixType}
                onChange={e => setMatrixType(e.target.value as 'FULL' | 'PAIRWISE')}
                className={inp}
              >
                <option value="FULL">Full (every combination)</option>
                <option value="PAIRWISE">Pairwise (fewer runs)</option>
              </select>
            </div>
          </div>

          {/* Monitoring scope */}
          <div className="rounded-lg border border-border bg-surface-muted p-3 space-y-3">
            <p className="text-xs font-semibold text-fg-muted">Scope (optional)</p>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={lbl}>Release</label>
                <select
                  value={releaseId}
                  onChange={e => setReleaseId(e.target.value)}
                  className={inp}
                >
                  <option value="">— none —</option>
                  {releases.map(r => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className={lbl}>Team</label>
                <select value={teamId} onChange={e => setTeamId(e.target.value)} className={inp}>
                  <option value="">— none —</option>
                  {teams.map(t => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className={lbl}>Sprint (Iteration)</label>
                <select
                  value={iterationPath}
                  onChange={e => setIterationPath(e.target.value)}
                  className={inp}
                >
                  <option value="">— none —</option>
                  {iterations.map(it => (
                    <option key={it.id} value={it.path}>
                      {it.path}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className={lbl}>Area</label>
                <select
                  value={areaPath}
                  onChange={e => setAreaPath(e.target.value)}
                  className={inp}
                >
                  <option value="">— none —</option>
                  {areas.map(a => (
                    <option key={a.id} value={a.path}>
                      {a.path}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Suites */}
          {suites.length > 0 && (
            <div>
              <label className={lbl}>
                From test suites{' '}
                <span className="font-normal text-fg-subtle">
                  ({selectedSuiteIds.size} selected · +{suiteContributedIds.size} cases)
                </span>
              </label>
              {suites.length > 6 && (
                <input
                  type="text"
                  value={suiteSearch}
                  onChange={e => setSuiteSearch(e.target.value)}
                  placeholder="Filter suites…"
                  className={cn(inp, 'mb-1.5')}
                />
              )}
              <div className="border border-border rounded-lg max-h-36 overflow-y-auto divide-y divide-border">
                {filteredSuites.map(s => (
                  <label
                    key={s.id}
                    className="flex items-center gap-2.5 px-3 py-1.5 cursor-pointer hover:bg-surface-muted"
                  >
                    <input
                      type="checkbox"
                      checked={selectedSuiteIds.has(s.id)}
                      onChange={() => toggleSuite(s.id)}
                      className="rounded border-border-strong accent-primary shrink-0"
                    />
                    <span className="text-sm text-fg flex-1 truncate">{s.name}</span>
                    {s.selectionMode === 'SMART' && (
                      <span className="text-[10px] text-primary shrink-0">✦ smart</span>
                    )}
                    <span className="text-xs text-fg-subtle shrink-0">{s.caseCount}</span>
                  </label>
                ))}
              </div>
            </div>
          )}

          {/* Test Cases */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className={lbl}>
                Test Cases{' '}
                <span className="font-normal text-fg-subtle">({selectedTcIds.size} picked)</span>
              </label>
              {testCases.length > 0 && (
                <button
                  type="button"
                  onClick={toggleAll}
                  className="text-xs text-primary hover:underline"
                >
                  {allVisibleSelected ? 'Deselect shown' : 'Select shown'}
                </button>
              )}
            </div>
            <input
              type="text"
              value={caseSearch}
              onChange={e => setCaseSearch(e.target.value)}
              className={cn(inp, 'mb-2')}
              placeholder="Search by title, ID, or requirement ID…"
            />
            {tcLoading && <div className="text-sm text-fg-subtle py-3 text-center">Loading…</div>}
            {!tcLoading && testCases.length === 0 && (
              <div className="text-sm text-fg-muted py-3 text-center bg-surface-muted rounded-lg">
                No approved test cases match.
              </div>
            )}
            {testCases.length > 0 && (
              <div className="border border-border rounded-lg max-h-48 overflow-y-auto divide-y divide-border">
                {testCases.map(tc => (
                  <label
                    key={tc.id}
                    className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-surface-muted"
                  >
                    <input
                      type="checkbox"
                      checked={selectedTcIds.has(tc.id) || suiteContributedIds.has(tc.id)}
                      onChange={() => toggleTc(tc.id)}
                      className="rounded border-border-strong accent-primary shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm text-fg truncate">
                        {tc.title}
                        {suiteContributedIds.has(tc.id) && !selectedTcIds.has(tc.id) && (
                          <span className="ml-2 text-[10px] text-primary">via suite</span>
                        )}
                      </div>
                    </div>
                    <span className="text-xs text-fg-subtle shrink-0">{tc.priority}</span>
                  </label>
                ))}
              </div>
            )}
            <p className="text-xs text-fg-muted mt-1">
              {effectiveCount} case{effectiveCount === 1 ? '' : 's'} to run
            </p>
          </div>
        </form>

        <div className="px-5 py-4 border-t border-border flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" onClick={() => handleSubmit()} loading={mutation.isPending}>
            Create Run
          </Button>
        </div>
      </div>
    </div>
  )
}
