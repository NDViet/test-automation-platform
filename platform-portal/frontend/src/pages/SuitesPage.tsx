import { useState } from 'react'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { TestSuite, CreateTestSuiteForm, SelectableTestCase } from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Plus, Pencil, Trash2, X, Loader2, Layers, ListChecks, Sparkles } from 'lucide-react'

const PLAN_TYPES = ['REGRESSION', 'SMOKE', 'SANITY', 'FUNCTIONAL', 'FEATURE', 'DOMAIN', 'CUSTOM']
const STATUSES = ['APPROVED', 'DRAFT', 'UNDER_REVIEW', 'DEPRECATED']

function planColor(t: string | null): string {
  switch (t) {
    case 'REGRESSION': return 'text-purple-700 bg-purple-100'
    case 'SMOKE':      return 'text-orange-700 bg-orange-100'
    case 'SANITY':     return 'text-teal-700 bg-teal-100'
    case 'FEATURE':    return 'text-blue-700 bg-blue-100'
    case 'DOMAIN':     return 'text-indigo-700 bg-indigo-100'
    default:           return 'text-slate-600 bg-slate-100'
  }
}
function shortPath(p: string | null): string {
  if (!p) return ''; const x = p.split('\\'); return x[x.length - 1]
}

// ── Suite create/edit modal ─────────────────────────────────────────────────────
function SuiteModal({ projectId, existing, onClose }: {
  projectId: string; existing?: TestSuite; onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateTestSuiteForm>({
    name: existing?.name ?? '',
    description: existing?.description ?? '',
    planType: existing?.planType ?? 'REGRESSION',
    areaPath: existing?.areaPath ?? '',
    teamId: existing?.teamId ?? '',
    selectionMode: existing?.selectionMode ?? 'STATIC',
    filterIteration: existing?.filterIteration ?? '',
    filterStatus: existing?.filterStatus ?? 'APPROVED',
    filterTags: existing?.filterTags ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof CreateTestSuiteForm, v: string) => setForm(f => ({ ...f, [k]: v }))
  const smart = form.selectionMode === 'SMART'

  const { data: areas = [] } = useQuery({ queryKey: ['adoAreas', projectId], queryFn: () => api.adoAreas(projectId) })
  const { data: teams = [] } = useQuery({ queryKey: ['adoTeams', projectId], queryFn: () => api.adoTeams(projectId) })
  const { data: iterations = [] } = useQuery({ queryKey: ['adoIterations', projectId], queryFn: () => api.adoIterations(projectId) })

  const mutation = useMutation({
    mutationFn: (body: CreateTestSuiteForm) =>
      existing ? api.updateTestSuite(projectId, existing.id, body) : api.createTestSuite(projectId, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['testSuites', projectId] }); onClose() },
    onError: (err: Error) => setError(err.message),
  })

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim()) { setError('Name is required'); return }
    mutation.mutate({ ...form, name: form.name.trim() })
  }
  const cls = 'w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">{existing ? 'Edit Suite' : 'New Suite'}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>
        <form onSubmit={submit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {error && <ErrorMessage message={error} />}
          <div className="grid grid-cols-3 gap-3">
            <div className="col-span-2">
              <label className="block text-xs font-medium text-slate-700 mb-1">Name *</label>
              <input className={cls} value={form.name} onChange={e => set('name', e.target.value)}
                     placeholder="e.g. Search · Regression" autoFocus />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Type</label>
              <select className={cls} value={form.planType} onChange={e => set('planType', e.target.value)}>
                {PLAN_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
          </div>

          {/* Ownership scope */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Team (owner)</label>
              <select className={cls} value={form.teamId} onChange={e => set('teamId', e.target.value)}>
                <option value="">— none —</option>
                {teams.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Area</label>
              <select className={cls} value={form.areaPath} onChange={e => set('areaPath', e.target.value)}>
                <option value="">— none —</option>
                {areas.map(a => <option key={a.id} value={a.path}>{a.path}</option>)}
              </select>
            </div>
          </div>

          {/* Membership mode */}
          <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-3 space-y-3">
            <div className="flex gap-2">
              {(['STATIC', 'SMART'] as const).map(m => (
                <button key={m} type="button" onClick={() => set('selectionMode', m)}
                        className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border ${
                          form.selectionMode === m ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600'}`}>
                  {m === 'STATIC' ? <ListChecks size={13} /> : <Sparkles size={13} />}
                  {m === 'STATIC' ? 'Static (curated)' : 'Smart (auto)'}
                </button>
              ))}
            </div>
            {smart ? (
              <>
                <p className="text-xs text-slate-500">
                  Auto-includes cases matching Team/Area above plus the filters below. New matching cases join automatically.
                </p>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">Sprint</label>
                    <select className={cls} value={form.filterIteration} onChange={e => set('filterIteration', e.target.value)}>
                      <option value="">— any —</option>
                      {iterations.map(it => <option key={it.id} value={it.path}>{it.path}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">Case status</label>
                    <select className={cls} value={form.filterStatus} onChange={e => set('filterStatus', e.target.value)}>
                      <option value="">— any —</option>
                      {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">Tags (comma-separated, match any)</label>
                  <input className={cls} value={form.filterTags} onChange={e => set('filterTags', e.target.value)}
                         placeholder="e.g. checkout, payments" />
                </div>
              </>
            ) : (
              <p className="text-xs text-slate-500">
                Curated set — after saving, use <b>Manage cases</b> to pick members (scope-filtered picker).
              </p>
            )}
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1">Description</label>
            <textarea className={cls} rows={2} value={form.description} onChange={e => set('description', e.target.value)} />
          </div>
        </form>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50">Cancel</button>
          <button onClick={submit as unknown as React.MouseEventHandler} disabled={mutation.isPending}
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2">
            {mutation.isPending && <Loader2 size={14} className="animate-spin" />}{existing ? 'Save' : 'Create'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Static member management modal (scoped picker) ──────────────────────────────
function MembersModal({ projectId, suite, onClose }: {
  projectId: string; suite: TestSuite; onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [seeded, setSeeded] = useState(false)

  // current members (seed selection once)
  useQuery({
    queryKey: ['suiteCases', projectId, suite.id],
    queryFn: () => api.suiteCases(projectId, suite.id),
    select: (data: SelectableTestCase[]) => {
      if (!seeded) { setSelected(new Set(data.map(c => c.id))); setSeeded(true) }
      return data
    },
  })

  // candidate cases scoped to the suite's team/area + search
  const { data: candidates = [], isLoading } = useQuery({
    queryKey: ['selectableTestCases', projectId, suite.areaPath, suite.teamId, search],
    queryFn: () => api.selectableTestCases(projectId, {
      status: 'APPROVED', area: suite.areaPath || undefined, teamId: suite.teamId || undefined, q: search.trim() || undefined,
    }),
  })

  const save = useMutation({
    mutationFn: () => api.replaceSuiteMembers(projectId, suite.id, Array.from(selected)),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['testSuites', projectId] }); onClose() },
  })

  const toggle = (id: string) => setSelected(p => { const n = new Set(p); n.has(id) ? n.delete(id) : n.add(id); return n })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-xl mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div>
            <h2 className="font-semibold text-slate-900">Manage cases · {suite.name}</h2>
            <p className="text-xs text-slate-500 mt-0.5">{selected.size} selected{suite.teamName ? ` · scoped to ${suite.teamName}` : ''}</p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          <input value={search} onChange={e => setSearch(e.target.value)}
                 placeholder="Search by title, test-case id, or requirement id…"
                 className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          {isLoading && <div className="text-sm text-slate-400 py-3 text-center">Loading…</div>}
          {!isLoading && candidates.length === 0 && <div className="text-sm text-slate-500 py-3 text-center bg-slate-50 rounded-lg">No matching cases.</div>}
          {candidates.length > 0 && (
            <div className="border border-slate-200 rounded-lg max-h-72 overflow-y-auto divide-y divide-slate-50">
              {candidates.map(c => (
                <label key={c.id} className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-slate-50">
                  <input type="checkbox" checked={selected.has(c.id)} onChange={() => toggle(c.id)}
                         className="rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm text-slate-800 truncate">{c.title}</div>
                    {c.requirementExternalIds.length > 0 &&
                      <div className="text-xs text-slate-400 truncate">req {c.requirementExternalIds.slice(0, 6).join(', ')}</div>}
                  </div>
                  <span className="text-xs text-slate-400 shrink-0">{c.priority}</span>
                </label>
              ))}
            </div>
          )}
        </div>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50">Cancel</button>
          <button onClick={() => save.mutate()} disabled={save.isPending}
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2">
            {save.isPending && <Loader2 size={14} className="animate-spin" />}Save members
          </button>
        </div>
      </div>
    </div>
  )
}

export default function SuitesPage() {
  const { projectId } = useProject()
  const queryClient = useQueryClient()
  const [modal, setModal] = useState<{ kind: 'edit' | 'members'; suite?: TestSuite } | null>(null)

  const { filter } = useProjectFilter()   // project-wide Team / Area scope
  const { data: allSuites = [], isLoading, error } = useQuery({
    queryKey: ['testSuites', projectId],
    queryFn: () => api.testSuites(projectId!),
    enabled: !!projectId,
  })
  // Team default-area map → lets an Area filter match team-owned suites (suite area may be unset).
  const { data: teams = [] } = useQuery({
    queryKey: ['adoTeams', projectId],
    queryFn: () => api.adoTeams(projectId!),
    enabled: !!projectId,
  })
  const teamArea = new Map(teams.map(t => [t.id, t.defaultAreaPath ?? '']))

  // Suites are owned by a Team/Area — honor the project scope filter.
  // Area matches the suite's own area OR the area owned by the suite's team (subtree).
  const suites = allSuites.filter(s => {
    if (filter.teamId && s.teamId !== filter.teamId) return false
    if (filter.area) {
      const ownerArea = s.teamId ? teamArea.get(s.teamId) : undefined
      const byOwnTeam = !!ownerArea && filter.area.startsWith(ownerArea)
      if (s.areaPath !== filter.area && !byOwnTeam) return false
    }
    return true
  })
  const del = useMutation({
    mutationFn: (id: string) => api.deleteTestSuite(projectId!, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['testSuites', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading suites…" />
  if (error) return <ErrorMessage message="Failed to load suites." />

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Test Suites</h1>
          <p className="text-sm text-slate-500 mt-0.5">{suites.length} reusable suites · pick them when creating a run instead of re-selecting cases</p>
        </div>
        <button onClick={() => setModal({ kind: 'edit' })}
                className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700">
          <Plus size={15} /> New Suite
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        {suites.length === 0 && (
          <div className="py-16 text-center"><Layers size={32} className="mx-auto text-slate-300 mb-3" />
            <p className="text-sm text-slate-500">No suites yet.</p></div>
        )}
        {suites.length > 0 && (
          <table className="w-full text-sm">
            <thead><tr className="text-left text-xs text-slate-500 border-b border-slate-100">
              <th className="px-4 py-2.5 font-medium">Suite</th><th className="px-4 py-2.5 font-medium">Type</th>
              <th className="px-4 py-2.5 font-medium">Mode</th><th className="px-4 py-2.5 font-medium">Scope</th>
              <th className="px-4 py-2.5 font-medium text-right">Cases</th><th className="px-4 py-2.5"></th>
            </tr></thead>
            <tbody className="divide-y divide-slate-50">
              {suites.map(s => (
                <tr key={s.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2.5 font-medium text-slate-800">{s.name}</td>
                  <td className="px-4 py-2.5"><Badge label={s.planType ?? '—'} colorClass={planColor(s.planType)} /></td>
                  <td className="px-4 py-2.5">
                    <span className={`text-xs font-medium ${s.selectionMode === 'SMART' ? 'text-blue-600' : 'text-slate-500'}`}>
                      {s.selectionMode === 'SMART' ? '✦ Smart' : 'Static'}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-xs text-slate-500">
                    {s.teamName && <span className="font-mono bg-slate-100 rounded px-1.5 py-0.5 mr-1">{s.teamName}</span>}
                    {s.areaPath && <span className="font-mono bg-slate-100 rounded px-1.5 py-0.5">{shortPath(s.areaPath)}</span>}
                    {!s.teamName && !s.areaPath && <span className="text-slate-400">global</span>}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">{s.caseCount}</td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      {s.selectionMode !== 'SMART' && (
                        <button onClick={() => setModal({ kind: 'members', suite: s })} title="Manage cases"
                                className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"><ListChecks size={14} /></button>
                      )}
                      <button onClick={() => setModal({ kind: 'edit', suite: s })} title="Edit"
                              className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"><Pencil size={14} /></button>
                      <button onClick={() => del.mutate(s.id)} title="Delete"
                              className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg"><Trash2 size={14} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {modal?.kind === 'edit' && <SuiteModal projectId={projectId!} existing={modal.suite} onClose={() => setModal(null)} />}
      {modal?.kind === 'members' && modal.suite && <MembersModal projectId={projectId!} suite={modal.suite} onClose={() => setModal(null)} />}
    </div>
  )
}
