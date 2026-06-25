import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { TestRun, CreateTestRunForm } from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Plus, PlayCircle, Trash2, CheckCircle, X, Loader2 } from 'lucide-react'

// ── Color helpers ─────────────────────────────────────────────────────────────

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

function RunProgressBar({ run }: { run: TestRun }) {
  const { totalTests, passed, failed, blocked, skipped } = run
  if (!totalTests) return <div className="h-1.5 bg-slate-100 rounded-full w-full" />

  const pct = (n: number) => `${((n / totalTests) * 100).toFixed(1)}%`
  const passRate = passed / totalTests

  return (
    <div className="space-y-1">
      <div className="flex h-2 rounded-full overflow-hidden bg-slate-100 w-full">
        <div className="bg-green-500" style={{ width: pct(passed) }} />
        <div className="bg-red-500" style={{ width: pct(failed) }} />
        <div className="bg-orange-400" style={{ width: pct(blocked) }} />
        <div className="bg-slate-300" style={{ width: pct(skipped) }} />
      </div>
      <div className="flex items-center gap-3 text-xs text-slate-500">
        <span className="text-green-600 font-medium">{passed} passed</span>
        {failed > 0 && <span className="text-red-600">{failed} failed</span>}
        {blocked > 0 && <span className="text-orange-600">{blocked} blocked</span>}
        {skipped > 0 && <span className="text-slate-400">{skipped} skipped</span>}
        <span
          className="ml-auto font-medium"
          style={{ color: passRate >= 0.9 ? '#16a34a' : passRate >= 0.8 ? '#ca8a04' : '#dc2626' }}
        >
          {totalTests > 0 ? `${(passRate * 100).toFixed(0)}%` : '—'}
        </span>
      </div>
    </div>
  )
}

// ── New Test Run Modal ────────────────────────────────────────────────────────

function NewTestRunModal({ projectId, onClose }: { projectId: string; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [releaseVersion, setReleaseVersion] = useState('')
  const [environment, setEnvironment] = useState('DEV')
  const [triggeredBy, setTriggeredBy] = useState('')
  const [selectedTcIds, setSelectedTcIds] = useState<Set<string>>(new Set())
  const [environmentId, setEnvironmentId] = useState('') // '' = use label below
  const [matrixType, setMatrixType] = useState<'FULL' | 'PAIRWISE'>('FULL')
  // Monitoring dimensions (run-level tags)
  const [releaseId, setReleaseId] = useState('')
  const [iterationPath, setIterationPath] = useState('')
  const [areaPath, setAreaPath] = useState('')
  const [teamId, setTeamId] = useState('')
  const [caseSearch, setCaseSearch] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Scope-filtered, searchable picker — narrows by the Monitoring scope (sprint/area/team)
  // and matches test-case id / title / linked requirement id. Scales to large catalogs.
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
  // Suite selection is tracked separately from individual picks; a suite contributes its
  // resolved cases (cached) and can be toggled off to remove them.
  const [selectedSuiteIds, setSelectedSuiteIds] = useState<Set<string>>(new Set())
  const [suiteResolved, setSuiteResolved] = useState<Record<string, string[]>>({})
  const [suiteSearch, setSuiteSearch] = useState('')

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

  // Cases contributed by the currently-selected suites (union of their resolved ids).
  const suiteContributedIds = new Set<string>()
  selectedSuiteIds.forEach(sid =>
    (suiteResolved[sid] ?? []).forEach(id => suiteContributedIds.add(id)),
  )
  const effectiveCount = new Set<string>([...selectedTcIds, ...suiteContributedIds]).size
  const filteredSuites = suites.filter(
    s => !suiteSearch.trim() || s.name.toLowerCase().includes(suiteSearch.toLowerCase()),
  )

  const [newEnvName, setNewEnvName] = useState('')
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
      queryClient.invalidateQueries({ queryKey: ['testRuns', projectId] })
      onClose()
    },
    onError: (err: Error) => setError(err.message),
  })

  function toggleTc(id: string) {
    setSelectedTcIds(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  // Visible (filtered) set; "select all" acts on what's currently shown, preserving
  // any selections made under other filters.
  const visibleIds = testCases.map(tc => tc.id)
  const allVisibleSelected = visibleIds.length > 0 && visibleIds.every(id => selectedTcIds.has(id))

  function toggleAll() {
    setSelectedTcIds(prev => {
      const next = new Set(prev)
      if (allVisibleSelected) visibleIds.forEach(id => next.delete(id))
      else visibleIds.forEach(id => next.add(id))
      return next
    })
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
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
      releaseVersion: releaseVersion.trim() || undefined,
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
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-xl mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">New Test Run</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {error && <ErrorMessage message={error} />}

          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1">Run Name *</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. Sprint 42 Regression"
              autoFocus
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Release Version
              </label>
              <input
                type="text"
                value={releaseVersion}
                onChange={e => setReleaseVersion(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="e.g. v2.4.0"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Environment</label>
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
                      const env = environments.find(x => x.id === v)
                      if (env) setEnvironment(env.name)
                    }
                  }}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="DEV">DEV</option>
                  <option value="STAGING">STAGING</option>
                  <option value="PROD">PROD</option>
                </select>
              )}
            </div>
          </div>

          {/* Inline environment creator */}
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={newEnvName}
              onChange={e => setNewEnvName(e.target.value)}
              className="flex-1 border border-slate-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="New named environment (e.g. PROD-EU)"
            />
            <button
              type="button"
              onClick={() => newEnvName.trim() && createEnvMutation.mutate()}
              disabled={!newEnvName.trim() || createEnvMutation.isPending}
              className="px-3 py-1.5 text-xs font-medium border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50"
            >
              {createEnvMutation.isPending ? 'Adding…' : '+ Add env'}
            </button>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Triggered By</label>
              <input
                type="text"
                value={triggeredBy}
                onChange={e => setTriggeredBy(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="e.g. john.doe@example.com"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Matrix (parametrized cases)
              </label>
              <select
                value={matrixType}
                onChange={e => setMatrixType(e.target.value as 'FULL' | 'PAIRWISE')}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="FULL">Full (every combination)</option>
                <option value="PAIRWISE">Pairwise (fewer runs)</option>
              </select>
            </div>
          </div>

          {/* Monitoring dimensions — used to roll up execution by Release / Sprint / Area / Team */}
          <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-3 space-y-3">
            <p className="text-xs font-semibold text-slate-600">Monitoring scope (optional)</p>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">Release</label>
                <select
                  value={releaseId}
                  onChange={e => setReleaseId(e.target.value)}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                <label className="block text-xs font-medium text-slate-700 mb-1">Team</label>
                <select
                  value={teamId}
                  onChange={e => setTeamId(e.target.value)}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">— none —</option>
                  {teams.map(t => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">
                  Sprint (Iteration)
                </label>
                <select
                  value={iterationPath}
                  onChange={e => setIterationPath(e.target.value)}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                <label className="block text-xs font-medium text-slate-700 mb-1">Area</label>
                <select
                  value={areaPath}
                  onChange={e => setAreaPath(e.target.value)}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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

          {/* Reuse suites instead of re-picking cases — searchable, toggleable */}
          {suites.length > 0 && (
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                From test suites{' '}
                <span className="font-normal text-slate-400">
                  ({selectedSuiteIds.size} selected · +{suiteContributedIds.size} cases)
                </span>
              </label>
              {suites.length > 6 && (
                <input
                  type="text"
                  value={suiteSearch}
                  onChange={e => setSuiteSearch(e.target.value)}
                  placeholder="Filter suites…"
                  className="w-full border border-slate-200 rounded-lg px-3 py-1.5 text-sm mb-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              )}
              <div className="border border-slate-200 rounded-lg max-h-40 overflow-y-auto divide-y divide-slate-50">
                {filteredSuites.length === 0 && (
                  <div className="px-3 py-2 text-xs text-slate-400 text-center">
                    No suites match.
                  </div>
                )}
                {filteredSuites.map(s => (
                  <label
                    key={s.id}
                    className="flex items-center gap-2.5 px-3 py-1.5 cursor-pointer hover:bg-slate-50"
                  >
                    <input
                      type="checkbox"
                      checked={selectedSuiteIds.has(s.id)}
                      onChange={() => toggleSuite(s.id)}
                      className="rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0"
                    />
                    <span className="text-sm text-slate-800 flex-1 truncate">{s.name}</span>
                    {s.selectionMode === 'SMART' && (
                      <span className="text-[10px] text-blue-500 shrink-0">✦ smart</span>
                    )}
                    {s.planType && (
                      <span className="text-[10px] uppercase text-slate-400 shrink-0">
                        {s.planType}
                      </span>
                    )}
                    <span className="text-xs text-slate-400 shrink-0">{s.caseCount}</span>
                  </label>
                ))}
              </div>
            </div>
          )}

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="block text-xs font-medium text-slate-700">
                Test Cases{' '}
                <span className="font-normal text-slate-400">
                  ({selectedTcIds.size} picked individually)
                </span>
              </label>
              {testCases.length > 0 && (
                <button
                  type="button"
                  onClick={toggleAll}
                  className="text-xs text-blue-600 hover:text-blue-700"
                >
                  {allVisibleSelected ? 'Deselect shown' : 'Select shown'}
                </button>
              )}
            </div>

            {/* Search by test-case title/id or linked requirement id */}
            <input
              type="text"
              value={caseSearch}
              onChange={e => setCaseSearch(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm mb-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Search by title, test-case id, or requirement id (e.g. 13849)…"
            />
            {(iterationPath || areaPath || teamId) && (
              <p className="text-xs text-slate-400 mb-2">
                Filtered to the monitoring scope above (sprint / area / team).
              </p>
            )}

            {tcLoading && (
              <div className="text-sm text-slate-400 py-3 text-center">Loading test cases…</div>
            )}
            {!tcLoading && testCases.length === 0 && (
              <div className="text-sm text-slate-500 py-3 text-center bg-slate-50 rounded-lg">
                No approved test cases match this scope/search.
              </div>
            )}
            {testCases.length > 0 && (
              <div className="border border-slate-200 rounded-lg max-h-52 overflow-y-auto divide-y divide-slate-50">
                {testCases.map(tc => (
                  <label
                    key={tc.id}
                    className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-slate-50 transition-colors"
                  >
                    <input
                      type="checkbox"
                      checked={selectedTcIds.has(tc.id) || suiteContributedIds.has(tc.id)}
                      onChange={() => toggleTc(tc.id)}
                      className="rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm text-slate-800 truncate">
                        {tc.title}
                        {suiteContributedIds.has(tc.id) && !selectedTcIds.has(tc.id) && (
                          <span className="ml-2 text-[10px] text-blue-500">via suite</span>
                        )}
                      </div>
                      {tc.requirementExternalIds.length > 0 && (
                        <div className="text-xs text-slate-400 truncate">
                          req {tc.requirementExternalIds.slice(0, 6).join(', ')}
                        </div>
                      )}
                    </div>
                    <span className="text-xs text-slate-400 shrink-0">{tc.priority}</span>
                  </label>
                ))}
              </div>
            )}
            <p className="text-xs text-slate-500 mt-1 font-medium">
              {effectiveCount} case{effectiveCount === 1 ? '' : 's'} to run
              <span className="font-normal text-slate-400">
                {' '}
                · {selectedTcIds.size} individual + {suiteContributedIds.size} from suites · showing{' '}
                {testCases.length}
              </span>
            </p>
          </div>
        </form>

        <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit as unknown as React.MouseEventHandler}
            disabled={mutation.isPending}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors flex items-center gap-2"
          >
            {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
            Create Run
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

type StatusTab = 'ALL' | 'IN_PROGRESS' | 'COMPLETED'

export default function TestRunsPage() {
  const { projectId, base } = useProject()
  const { filter } = useProjectFilter() // project-wide Area / Team / Iteration scope
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [tab, setTab] = useState<StatusTab>('ALL')
  const [showNewModal, setShowNewModal] = useState(false)

  const {
    data: runs = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['testRuns', projectId],
    queryFn: () => api.testRuns(projectId!),
    enabled: !!projectId,
  })

  const completeMutation = useMutation({
    mutationFn: (runId: string) => api.completeTestRun(projectId!, runId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['testRuns', projectId] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (runId: string) => api.deleteTestRun(projectId!, runId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['testRuns', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading test runs…" />
  if (error)
    return <ErrorMessage message="Failed to load test runs." onRetry={() => void refetch()} />

  // Honor the project scope filter against each run's own dimension tags.
  const scoped = runs.filter(
    r =>
      (!filter.teamId || r.teamId === filter.teamId) &&
      (!filter.area || r.areaPath === filter.area) &&
      (!filter.iteration || r.iterationPath === filter.iteration),
  )

  const filtered = tab === 'ALL' ? scoped : scoped.filter(r => r.status === tab)

  const tabs: { key: StatusTab; label: string }[] = [
    { key: 'ALL', label: 'All' },
    { key: 'IN_PROGRESS', label: 'In Progress' },
    { key: 'COMPLETED', label: 'Completed' },
  ]

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Test Runs</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            {scoped.length} runs{scoped.length !== runs.length ? ` (of ${runs.length})` : ''}
          </p>
        </div>
        <button
          onClick={() => setShowNewModal(true)}
          className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={15} />
          New Test Run
        </button>
      </div>

      {/* Status tabs */}
      <div className="flex gap-1 bg-slate-100 p-1 rounded-lg w-fit">
        {tabs.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
              tab === t.key
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-500 hover:text-slate-700',
            )}
          >
            {t.label}
            {t.key !== 'ALL' && (
              <span className="ml-1.5 text-xs text-slate-400">
                {scoped.filter(r => r.status === t.key).length}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Runs list */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        {filtered.length === 0 && (
          <div className="py-16 text-center">
            <PlayCircle size={32} className="mx-auto text-slate-300 mb-3" />
            <p className="text-sm text-slate-500">No test runs found.</p>
            <p className="text-xs text-slate-400 mt-1">
              Create a test run to start executing test cases.
            </p>
          </div>
        )}
        <div className="divide-y divide-slate-50">
          {filtered.map(run => (
            <div
              key={run.id}
              onClick={() => navigate(`${base}/test-runs/${run.id}`)}
              className="px-5 py-4 hover:bg-slate-50 cursor-pointer transition-colors"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <p className="font-medium text-slate-900 text-sm">{run.name}</p>
                    {run.releaseVersion && (
                      <span className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-600 rounded font-mono">
                        {run.releaseVersion}
                      </span>
                    )}
                    <Badge
                      label={run.status.replace('_', ' ')}
                      colorClass={runStatusColor(run.status)}
                    />
                    <Badge label={run.environment} colorClass={envColor(run.environment)} />
                  </div>
                  <div className="mt-2">
                    <RunProgressBar run={run} />
                  </div>
                  <div className="flex items-center gap-3 mt-1.5 text-xs text-slate-400">
                    {run.triggeredBy && <span>by {run.triggeredBy}</span>}
                    <span>{relativeTime(run.createdAt)}</span>
                    {run.completedAt && <span>· completed {relativeTime(run.completedAt)}</span>}
                  </div>
                </div>
                <div
                  className="flex items-center gap-2 shrink-0"
                  onClick={e => e.stopPropagation()}
                >
                  {run.status === 'IN_PROGRESS' && (
                    <button
                      onClick={() => completeMutation.mutate(run.id)}
                      disabled={completeMutation.isPending}
                      title="Complete run"
                      className="p-1.5 text-green-600 hover:bg-green-50 rounded-lg transition-colors disabled:opacity-50"
                    >
                      {completeMutation.isPending ? (
                        <Loader2 size={15} className="animate-spin" />
                      ) : (
                        <CheckCircle size={15} />
                      )}
                    </button>
                  )}
                  <button
                    onClick={() => deleteMutation.mutate(run.id)}
                    disabled={deleteMutation.isPending}
                    title="Delete run"
                    className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {showNewModal && (
        <NewTestRunModal projectId={projectId!} onClose={() => setShowNewModal(false)} />
      )}
    </div>
  )
}
