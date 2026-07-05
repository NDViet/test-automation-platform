import { useState } from 'react'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Release, CreateReleaseForm, GateEvaluation, QualityGate } from '@/lib/types'
import { gateStatusVariant } from '@/lib/status'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, PageHeader, StatusBadge } from '@/components/ui'
import { Plus, Pencil, Trash2, X, Rocket, Link2, ShieldCheck, Download } from 'lucide-react'

const STATES = ['PLANNED', 'IN_PROGRESS', 'RELEASED', 'ARCHIVED']
const TYPES = ['VERSION', 'SPRINT', 'MILESTONE']

function stateColor(s: string): string {
  switch (s) {
    case 'IN_PROGRESS':
      return 'text-info bg-info-bg'
    case 'RELEASED':
      return 'text-success bg-success-bg'
    case 'ARCHIVED':
      return 'text-neutral bg-neutral-bg'
    default:
      return 'text-warning bg-warning-bg'
  }
}

function MapChip({ label }: { label: string }) {
  return (
    <span className="inline-block text-xs font-mono bg-surface-muted text-fg-muted rounded px-1.5 py-0.5 mr-1 mb-1">
      {label}
    </span>
  )
}

const inputCls =
  'w-full border border-border-strong rounded-md px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

function ReleaseModal({
  projectId,
  existing,
  onClose,
}: {
  projectId: string
  existing?: Release
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateReleaseForm>({
    name: existing?.name ?? '',
    releaseType: existing?.releaseType ?? 'VERSION',
    externalId: existing?.externalId ?? '',
    targetDate: existing?.targetDate ?? '',
    state: existing?.state ?? 'PLANNED',
    mapIterationPath: existing?.mapIterationPath ?? '',
    mapAreaPath: existing?.mapAreaPath ?? '',
    mapTeamId: existing?.mapTeamId ?? '',
    mapTag: existing?.mapTag ?? '',
    mappingField: existing?.mappingField ?? '',
    mappingValue: existing?.mappingValue ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [showAdvanced, setShowAdvanced] = useState(!!(existing?.mapTag || existing?.mappingField))
  const set = (k: keyof CreateReleaseForm, v: string) => setForm(f => ({ ...f, [k]: v }))

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

  const mutation = useMutation({
    mutationFn: (body: CreateReleaseForm) =>
      existing
        ? api.updateRelease(projectId, existing.id, body)
        : api.createRelease(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['releases', projectId] })
      onClose()
    },
    onError: (err: Error) => setError(err.message),
  })

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim()) {
      setError('Name is required')
      return
    }
    mutation.mutate({ ...form, name: form.name.trim() })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-lg mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="font-semibold text-fg">{existing ? 'Edit Release' : 'New Release'}</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <form onSubmit={submit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {error && <ErrorMessage message={error} />}
          <div>
            <label className="block text-xs font-medium text-fg mb-1">Name *</label>
            <input
              className={inputCls}
              value={form.name}
              onChange={e => set('name', e.target.value)}
              placeholder="e.g. Connect v11.13"
              autoFocus
            />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-fg mb-1">Type</label>
              <select
                className={inputCls}
                value={form.releaseType}
                onChange={e => set('releaseType', e.target.value)}
              >
                {TYPES.map(t => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-fg mb-1">State</label>
              <select
                className={inputCls}
                value={form.state}
                onChange={e => set('state', e.target.value)}
              >
                {STATES.map(s => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-fg mb-1">Target date</label>
              <input
                type="date"
                className={inputCls}
                value={form.targetDate ?? ''}
                onChange={e => set('targetDate', e.target.value)}
              />
            </div>
          </div>

          <div className="rounded-lg border border-border bg-surface-muted/60 p-3 space-y-3">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-fg-muted">
              <Link2 size={13} /> Scope mapping (optional · narrows what's in this release)
            </div>
            <p className="text-xs text-fg-muted">
              A release lives in a Sprint but is narrowed by Area/Team. Set any combination —
              they're AND-combined. Leave all blank for a standalone release.
            </p>
            <div className="grid grid-cols-1 gap-3">
              <div>
                <label className="block text-xs font-medium text-fg mb-1">Iteration (Sprint)</label>
                <select
                  className={inputCls}
                  value={form.mapIterationPath}
                  onChange={e => set('mapIterationPath', e.target.value)}
                >
                  <option value="">— any —</option>
                  {iterations.map(it => (
                    <option key={it.id} value={it.path}>
                      {it.path}
                    </option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-fg mb-1">Team</label>
                  <select
                    className={inputCls}
                    value={form.mapTeamId}
                    onChange={e => set('mapTeamId', e.target.value)}
                  >
                    <option value="">— any —</option>
                    {teams.map(t => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-fg mb-1">Area</label>
                  <select
                    className={inputCls}
                    value={form.mapAreaPath}
                    onChange={e => set('mapAreaPath', e.target.value)}
                  >
                    <option value="">— any —</option>
                    {areas.map(a => (
                      <option key={a.id} value={a.path}>
                        {a.path}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowAdvanced(v => !v)}
                className="text-xs text-primary hover:underline w-fit"
              >
                {showAdvanced ? '− Hide advanced matchers' : '+ Advanced (tag / field)'}
              </button>
              {showAdvanced && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-fg mb-1">Tag</label>
                    <input
                      className={inputCls}
                      value={form.mapTag ?? ''}
                      onChange={e => set('mapTag', e.target.value)}
                      placeholder="label value"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-xs font-medium text-fg mb-1">Field</label>
                      <input
                        className={inputCls}
                        value={form.mappingField ?? ''}
                        onChange={e => set('mappingField', e.target.value)}
                        placeholder="ref name"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-fg mb-1">= Value</label>
                      <input
                        className={inputCls}
                        value={form.mappingValue ?? ''}
                        onChange={e => set('mappingValue', e.target.value)}
                        placeholder="expected"
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </form>
        <div className="px-5 py-4 border-t border-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={submit as unknown as React.MouseEventHandler}
            loading={mutation.isPending}
          >
            {existing ? 'Save' : 'Create'}
          </Button>
        </div>
      </div>
    </div>
  )
}

function shortPath(p: string | null): string {
  if (!p) return ''
  const parts = p.split('\\')
  return parts[parts.length - 1]
}

// ── Quality gate panel (per release) ──────────────────────────────────────────

function GatePanelModal({
  projectId,
  release,
  onClose,
}: {
  projectId: string
  release: Release
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [evaluation, setEvaluation] = useState<GateEvaluation | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['releases', projectId] })

  const evalMutation = useMutation({
    mutationFn: () => api.evaluateGate(projectId, release.id),
    onSuccess: r => {
      setEvaluation(r)
      setError(null)
      invalidate()
    },
    onError: (e: Error) => setError(e.message),
  })
  const overrideMutation = useMutation({
    mutationFn: () => api.overrideGate(projectId, release.id, reason),
    onSuccess: () => {
      invalidate()
      onClose()
    },
    onError: (e: Error) => setError(e.message),
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-lg">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldCheck size={18} className="text-fg-muted" />
            <h2 className="font-semibold text-fg">Quality gate — {release.name}</h2>
          </div>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>

        <div className="px-6 py-5 space-y-4">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-fg-muted">Current:</span>
            <StatusBadge variant={gateStatusVariant(release.gateStatus)}>
              {release.gateStatus ?? 'not evaluated'}
            </StatusBadge>
            {release.gateOverriddenBy && (
              <span className="text-xs text-fg-subtle">
                overridden by {release.gateOverriddenBy}
              </span>
            )}
          </div>
          {release.gateOverrideReason && (
            <p className="text-xs text-fg-muted italic">“{release.gateOverrideReason}”</p>
          )}

          {evaluation && (
            <div className="border border-border rounded-lg divide-y divide-border">
              {evaluation.criteria.length === 0 ? (
                <p className="text-xs text-fg-muted px-3 py-2">Gate disabled — always passes.</p>
              ) : (
                evaluation.criteria.map(c => (
                  <div key={c.name} className="flex items-center justify-between px-3 py-2 text-sm">
                    <span className="text-fg">{c.name}</span>
                    <span className="flex items-center gap-2">
                      <span className="text-xs text-fg-subtle">
                        {c.actual} / {c.threshold}
                      </span>
                      <StatusBadge variant={c.passed ? 'success' : 'danger'}>
                        {c.passed ? 'pass' : 'fail'}
                      </StatusBadge>
                    </span>
                  </div>
                ))
              )}
            </div>
          )}

          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">
              Override reason (audited)
            </label>
            <input
              value={reason}
              onChange={e => setReason(e.target.value)}
              placeholder="Why ship despite the gate?"
              className={inputCls}
            />
          </div>

          {error && <p className="text-xs text-danger">{error}</p>}
        </div>

        <div className="px-6 py-4 border-t border-border flex gap-3 justify-between">
          <Button
            variant="secondary"
            onClick={() => evalMutation.mutate()}
            loading={evalMutation.isPending}
          >
            Evaluate
          </Button>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={onClose}>
              Close
            </Button>
            <Button
              variant="danger"
              onClick={() => overrideMutation.mutate()}
              loading={overrideMutation.isPending}
              disabled={!reason.trim()}
            >
              Override
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Gate thresholds config ────────────────────────────────────────────────────

function GateConfigModal({ projectId, onClose }: { projectId: string; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['quality-gate', projectId],
    queryFn: () => api.gateConfig(projectId),
  })
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg">Quality gate thresholds</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        {isLoading || !data ? (
          <div className="px-6 py-10">
            <LoadingSpinner message="Loading gate…" />
          </div>
        ) : (
          <GateConfigForm projectId={projectId} initial={data} onClose={onClose} />
        )}
      </div>
    </div>
  )
}

function GateConfigForm({
  projectId,
  initial,
  onClose,
}: {
  projectId: string
  initial: QualityGate
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [enabled, setEnabled] = useState(initial.enabled)
  const [passRatePct, setPassRatePct] = useState(Math.round(initial.minPassRate * 100))
  const [coveragePct, setCoveragePct] = useState(initial.minCoveragePct)
  const [maxBlockers, setMaxBlockers] = useState(initial.maxOpenBlockers)
  const [maxFlaky, setMaxFlaky] = useState(initial.maxCriticalFlaky)
  const [requireSuites, setRequireSuites] = useState(initial.requireSuitesExecuted)
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      api.updateGateConfig(projectId, {
        enabled,
        minPassRate: passRatePct / 100,
        minCoveragePct: coveragePct,
        maxOpenBlockers: maxBlockers,
        maxCriticalFlaky: maxFlaky,
        requireSuitesExecuted: requireSuites,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quality-gate', projectId] })
      onClose()
    },
    onError: (e: Error) => setError(e.message),
  })

  const numRow = (label: string, value: number, set: (n: number) => void, suffix?: string) => (
    <div className="flex items-center justify-between gap-3">
      <label className="text-sm text-fg">{label}</label>
      <div className="flex items-center gap-1">
        <input
          type="number"
          value={value}
          onChange={e => set(Number(e.target.value))}
          className="w-24 border border-border-strong rounded-md px-2 py-1 text-sm bg-surface text-fg text-right"
        />
        {suffix && <span className="text-xs text-fg-subtle w-4">{suffix}</span>}
      </div>
    </div>
  )

  return (
    <>
      <div className="px-6 py-5 space-y-4">
        <label className="flex items-center gap-2 text-sm text-fg">
          <input type="checkbox" checked={enabled} onChange={e => setEnabled(e.target.checked)} />
          Enforce the gate on release
        </label>
        {numRow('Min pass rate', passRatePct, setPassRatePct, '%')}
        {numRow('Min coverage', coveragePct, setCoveragePct, '%')}
        {numRow('Max open blockers', maxBlockers, setMaxBlockers)}
        {numRow('Max critical-flaky', maxFlaky, setMaxFlaky)}
        <label className="flex items-center gap-2 text-sm text-fg">
          <input
            type="checkbox"
            checked={requireSuites}
            onChange={e => setRequireSuites(e.target.checked)}
          />
          Require suites to have been executed
        </label>
        {error && <p className="text-xs text-danger">{error}</p>}
      </div>
      <div className="px-6 py-4 border-t border-border flex gap-3 justify-end">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button variant="primary" onClick={() => save.mutate()} loading={save.isPending}>
          Save
        </Button>
      </div>
    </>
  )
}

export default function ReleasesPage() {
  const { projectId } = useProject()
  const { filter } = useProjectFilter() // project-wide Area / Team / Iteration scope
  const queryClient = useQueryClient()
  const [modal, setModal] = useState<{ open: boolean; release?: Release }>({ open: false })
  const [gateModal, setGateModal] = useState<Release | null>(null)
  const [configOpen, setConfigOpen] = useState(false)

  const {
    data: allReleases = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['releases', projectId],
    queryFn: () => api.releases(projectId!),
    enabled: !!projectId,
  })
  // Honor the project scope against each release's composite mapping.
  const releases = allReleases.filter(
    r =>
      (!filter.teamId || r.mapTeamId === filter.teamId) &&
      (!filter.area || r.mapAreaPath === filter.area) &&
      (!filter.iteration || r.mapIterationPath === filter.iteration),
  )

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteRelease(projectId!, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['releases', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading releases…" />
  if (error)
    return <ErrorMessage message="Failed to load releases." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Releases"
        icon={<Rocket size={20} />}
        description={`${releases.length}${
          releases.length !== allReleases.length ? ` of ${allReleases.length}` : ''
        } releases · a sprint can hold several (one per team)`}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="secondary" onClick={() => void api.downloadExecSummaryCsv(projectId)}>
              <Download size={15} /> Exec summary CSV
            </Button>
            <Button variant="secondary" onClick={() => setConfigOpen(true)}>
              <ShieldCheck size={15} /> Quality gate
            </Button>
            <Button onClick={() => setModal({ open: true })}>
              <Plus size={15} /> New Release
            </Button>
          </div>
        }
      />

      <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
        {releases.length === 0 && (
          <div className="py-16 text-center">
            <Rocket size={32} className="mx-auto text-fg-subtle mb-3" />
            <p className="text-sm text-fg-muted">No releases yet.</p>
          </div>
        )}
        {releases.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-fg-muted border-b border-border">
                <th className="px-4 py-2.5 font-medium">Release</th>
                <th className="px-4 py-2.5 font-medium">State</th>
                <th className="px-4 py-2.5 font-medium">Gate</th>
                <th className="px-4 py-2.5 font-medium">Scope mapping</th>
                <th className="px-4 py-2.5 font-medium text-right">Reqs</th>
                <th className="px-4 py-2.5 font-medium text-right">Runs</th>
                <th className="px-4 py-2.5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {releases.map(r => (
                <tr key={r.id} className="hover:bg-surface-muted">
                  <td className="px-4 py-2.5">
                    <div className="font-medium text-fg">{r.name}</div>
                    <div className="text-xs text-fg-subtle">
                      {r.releaseType}
                      {r.targetDate ? ` · ${r.targetDate}` : ''}
                    </div>
                  </td>
                  <td className="px-4 py-2.5">
                    <Badge label={r.state.replace('_', ' ')} colorClass={stateColor(r.state)} />
                  </td>
                  <td className="px-4 py-2.5">
                    <button
                      onClick={() => setGateModal(r)}
                      title="Quality gate"
                      className="inline-flex"
                    >
                      <StatusBadge variant={gateStatusVariant(r.gateStatus)}>
                        {r.gateStatus ?? '—'}
                      </StatusBadge>
                    </button>
                  </td>
                  <td className="px-4 py-2.5 max-w-sm">
                    {!r.mapIterationPath &&
                    !r.mapAreaPath &&
                    !r.mapTeamId &&
                    !r.mapTag &&
                    !r.mappingField ? (
                      <span className="text-fg-subtle">standalone</span>
                    ) : (
                      <div className="flex flex-wrap">
                        {r.mapIterationPath && (
                          <MapChip label={`sprint: ${shortPath(r.mapIterationPath)}`} />
                        )}
                        {r.mapTeamName && <MapChip label={`team: ${r.mapTeamName}`} />}
                        {r.mapAreaPath && <MapChip label={`area: ${shortPath(r.mapAreaPath)}`} />}
                        {r.mapTag && <MapChip label={`tag: ${r.mapTag}`} />}
                        {r.mappingField && (
                          <MapChip label={`${r.mappingField}=${r.mappingValue}`} />
                        )}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-fg">
                    {r.mappedRequirementCount}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-fg">
                    {r.linkedRunCount}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => setModal({ open: true, release: r })}
                        title="Edit"
                        aria-label={`Edit ${r.name}`}
                        className="p-1.5 text-fg-subtle hover:text-primary hover:bg-primary-subtle rounded-md"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => deleteMutation.mutate(r.id)}
                        title="Delete"
                        aria-label={`Delete ${r.name}`}
                        className="p-1.5 text-fg-subtle hover:text-danger hover:bg-danger-bg rounded-md"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {modal.open && (
        <ReleaseModal
          projectId={projectId!}
          existing={modal.release}
          onClose={() => setModal({ open: false })}
        />
      )}

      {gateModal && (
        <GatePanelModal
          projectId={projectId!}
          release={gateModal}
          onClose={() => setGateModal(null)}
        />
      )}

      {configOpen && (
        <GateConfigModal projectId={projectId!} onClose={() => setConfigOpen(false)} />
      )}
    </div>
  )
}
