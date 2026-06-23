import { useState } from 'react'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Release, CreateReleaseForm } from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Plus, Pencil, Trash2, X, Loader2, Rocket, Link2 } from 'lucide-react'

const STATES = ['PLANNED', 'IN_PROGRESS', 'RELEASED', 'ARCHIVED']
const TYPES = ['VERSION', 'SPRINT', 'MILESTONE']

function stateColor(s: string): string {
  switch (s) {
    case 'IN_PROGRESS': return 'text-blue-700 bg-blue-100'
    case 'RELEASED':    return 'text-green-700 bg-green-100'
    case 'ARCHIVED':    return 'text-slate-500 bg-slate-100'
    default:            return 'text-amber-700 bg-amber-100'
  }
}

function MapChip({ label }: { label: string }) {
  return <span className="inline-block text-xs font-mono bg-slate-100 text-slate-600 rounded px-1.5 py-0.5 mr-1 mb-1">{label}</span>
}

function ReleaseModal({ projectId, existing, onClose }: {
  projectId: string; existing?: Release; onClose: () => void
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

  const { data: iterations = [] } = useQuery({ queryKey: ['adoIterations', projectId], queryFn: () => api.adoIterations(projectId) })
  const { data: areas = [] } = useQuery({ queryKey: ['adoAreas', projectId], queryFn: () => api.adoAreas(projectId) })
  const { data: teams = [] } = useQuery({ queryKey: ['adoTeams', projectId], queryFn: () => api.adoTeams(projectId) })

  const mutation = useMutation({
    mutationFn: (body: CreateReleaseForm) =>
      existing ? api.updateRelease(projectId, existing.id, body) : api.createRelease(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['releases', projectId] })
      onClose()
    },
    onError: (err: Error) => setError(err.message),
  })

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim()) { setError('Name is required'); return }
    mutation.mutate({ ...form, name: form.name.trim() })
  }

  const inputCls = 'w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">{existing ? 'Edit Release' : 'New Release'}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>
        <form onSubmit={submit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {error && <ErrorMessage message={error} />}
          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1">Name *</label>
            <input className={inputCls} value={form.name} onChange={e => set('name', e.target.value)}
                   placeholder="e.g. Connect v11.13" autoFocus />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Type</label>
              <select className={inputCls} value={form.releaseType} onChange={e => set('releaseType', e.target.value)}>
                {TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">State</label>
              <select className={inputCls} value={form.state} onChange={e => set('state', e.target.value)}>
                {STATES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Target date</label>
              <input type="date" className={inputCls} value={form.targetDate ?? ''} onChange={e => set('targetDate', e.target.value)} />
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-3 space-y-3">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
              <Link2 size={13} /> Scope mapping (optional · narrows what's in this release)
            </div>
            <p className="text-xs text-slate-500">
              A release lives in a Sprint but is narrowed by Area/Team. Set any combination — they're AND-combined.
              Leave all blank for a standalone release.
            </p>
            <div className="grid grid-cols-1 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">Iteration (Sprint)</label>
                <select className={inputCls} value={form.mapIterationPath} onChange={e => set('mapIterationPath', e.target.value)}>
                  <option value="">— any —</option>
                  {iterations.map(it => <option key={it.id} value={it.path}>{it.path}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">Team</label>
                  <select className={inputCls} value={form.mapTeamId} onChange={e => set('mapTeamId', e.target.value)}>
                    <option value="">— any —</option>
                    {teams.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">Area</label>
                  <select className={inputCls} value={form.mapAreaPath} onChange={e => set('mapAreaPath', e.target.value)}>
                    <option value="">— any —</option>
                    {areas.map(a => <option key={a.id} value={a.path}>{a.path}</option>)}
                  </select>
                </div>
              </div>
              <button type="button" onClick={() => setShowAdvanced(v => !v)} className="text-xs text-blue-600 hover:text-blue-700 w-fit">
                {showAdvanced ? '− Hide advanced matchers' : '+ Advanced (tag / field)'}
              </button>
              {showAdvanced && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">Tag</label>
                    <input className={inputCls} value={form.mapTag ?? ''} onChange={e => set('mapTag', e.target.value)} placeholder="label value" />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-xs font-medium text-slate-700 mb-1">Field</label>
                      <input className={inputCls} value={form.mappingField ?? ''} onChange={e => set('mappingField', e.target.value)} placeholder="ref name" />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-700 mb-1">= Value</label>
                      <input className={inputCls} value={form.mappingValue ?? ''} onChange={e => set('mappingValue', e.target.value)} placeholder="expected" />
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </form>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50">Cancel</button>
          <button onClick={submit as unknown as React.MouseEventHandler} disabled={mutation.isPending}
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2">
            {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
            {existing ? 'Save' : 'Create'}
          </button>
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

export default function ReleasesPage() {
  const { projectId } = useProject()
  const { filter } = useProjectFilter()   // project-wide Area / Team / Iteration scope
  const queryClient = useQueryClient()
  const [modal, setModal] = useState<{ open: boolean; release?: Release }>({ open: false })

  const { data: allReleases = [], isLoading, error } = useQuery({
    queryKey: ['releases', projectId],
    queryFn: () => api.releases(projectId!),
    enabled: !!projectId,
  })
  // Honor the project scope against each release's composite mapping.
  const releases = allReleases.filter(r =>
    (!filter.teamId    || r.mapTeamId === filter.teamId) &&
    (!filter.area      || r.mapAreaPath === filter.area) &&
    (!filter.iteration || r.mapIterationPath === filter.iteration))

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteRelease(projectId!, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['releases', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading releases…" />
  if (error) return <ErrorMessage message="Failed to load releases." />

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Releases</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            {releases.length}{releases.length !== allReleases.length ? ` of ${allReleases.length}` : ''} releases · a sprint can hold several (one per team)
          </p>
        </div>
        <button onClick={() => setModal({ open: true })}
                className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700">
          <Plus size={15} /> New Release
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        {releases.length === 0 && (
          <div className="py-16 text-center">
            <Rocket size={32} className="mx-auto text-slate-300 mb-3" />
            <p className="text-sm text-slate-500">No releases yet.</p>
          </div>
        )}
        {releases.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                <th className="px-4 py-2.5 font-medium">Release</th>
                <th className="px-4 py-2.5 font-medium">State</th>
                <th className="px-4 py-2.5 font-medium">Scope mapping</th>
                <th className="px-4 py-2.5 font-medium text-right">Reqs</th>
                <th className="px-4 py-2.5 font-medium text-right">Runs</th>
                <th className="px-4 py-2.5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {releases.map(r => (
                <tr key={r.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2.5">
                    <div className="font-medium text-slate-800">{r.name}</div>
                    <div className="text-xs text-slate-400">{r.releaseType}{r.targetDate ? ` · ${r.targetDate}` : ''}</div>
                  </td>
                  <td className="px-4 py-2.5"><Badge label={r.state.replace('_', ' ')} colorClass={stateColor(r.state)} /></td>
                  <td className="px-4 py-2.5 max-w-sm">
                    {!r.mapIterationPath && !r.mapAreaPath && !r.mapTeamId && !r.mapTag && !r.mappingField
                      ? <span className="text-slate-400">standalone</span>
                      : <div className="flex flex-wrap">
                          {r.mapIterationPath && <MapChip label={`sprint: ${shortPath(r.mapIterationPath)}`} />}
                          {r.mapTeamName && <MapChip label={`team: ${r.mapTeamName}`} />}
                          {r.mapAreaPath && <MapChip label={`area: ${shortPath(r.mapAreaPath)}`} />}
                          {r.mapTag && <MapChip label={`tag: ${r.mapTag}`} />}
                          {r.mappingField && <MapChip label={`${r.mappingField}=${r.mappingValue}`} />}
                        </div>}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">{r.mappedRequirementCount}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">{r.linkedRunCount}</td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={() => setModal({ open: true, release: r })} title="Edit"
                              className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"><Pencil size={14} /></button>
                      <button onClick={() => deleteMutation.mutate(r.id)} title="Delete"
                              className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg"><Trash2 size={14} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {modal.open && (
        <ReleaseModal projectId={projectId!} existing={modal.release} onClose={() => setModal({ open: false })} />
      )}
    </div>
  )
}
