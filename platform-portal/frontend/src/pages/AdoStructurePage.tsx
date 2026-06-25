import { useState } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Users, Loader2, RefreshCw, FolderTree, CalendarRange, UsersRound, Layers, Copy, Check } from 'lucide-react'
import type { AdoTeam, AdoArea, AdoIteration, AdoUser } from '@/lib/types'

type Tab = 'teams' | 'areas' | 'iterations' | 'users'

function SlugChip({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  function copy(e: React.MouseEvent) {
    e.stopPropagation()
    navigator.clipboard.writeText(value).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }
  return (
    <span className="inline-flex items-center gap-1 bg-slate-100 rounded px-2 py-0.5 font-mono text-xs text-slate-600">
      {value}
      <button onClick={copy} title="Copy slug" className="text-slate-400 hover:text-slate-700 transition-colors">
        {copied ? <Check size={11} className="text-green-600" /> : <Copy size={11} />}
      </button>
    </span>
  )
}

export default function AdoStructurePage() {
  const { projectId, project } = useProject()
  const qc = useQueryClient()
  const [tab, setTab] = useState<Tab>('teams')
  const [error, setError] = useState<string | null>(null)

  const summaryQ = useQuery({ queryKey: ['ado-summary', projectId], queryFn: () => api.adoSummary(projectId) })

  const sync = useMutation({
    mutationFn: () => api.syncAdoStructure(projectId),
    onSuccess: (r) => {
      if (!r.success) { setError(r.error ?? 'Sync failed'); return }
      setError(null)
      qc.invalidateQueries({ queryKey: ['ado-summary', projectId] })
      qc.invalidateQueries({ queryKey: ['ado-teams', projectId] })
      qc.invalidateQueries({ queryKey: ['ado-areas', projectId] })
      qc.invalidateQueries({ queryKey: ['ado-iterations', projectId] })
      qc.invalidateQueries({ queryKey: ['ado-users', projectId] })
    },
    onError: (e: Error) => setError(e.message),
  })

  const s = summaryQ.data
  const tabs: { key: Tab; label: string; icon: typeof Users; count?: number }[] = [
    { key: 'teams',      label: 'Teams',      icon: UsersRound,   count: s?.teams },
    { key: 'areas',      label: 'Areas',      icon: FolderTree,   count: s?.areas },
    { key: 'iterations', label: 'Iterations', icon: CalendarRange, count: s?.iterations },
    { key: 'users',      label: 'Users',      icon: Users,        count: s?.users },
  ]

  const empty = s && s.teams === 0 && s.areas === 0 && s.iterations === 0 && s.users === 0

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-3">
            <Layers size={20} className="text-slate-400" />
            <h1 className="text-2xl font-bold text-slate-900">Teams &amp; Structure</h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">Azure DevOps teams, area paths, iterations, and people synced for this project.</p>
          <p className="text-xs text-slate-400 mt-1">
            Adapter config: <code className="font-mono text-slate-600">PLATFORM_ORG_SLUG=<span className="text-blue-600">{project.orgSlug}</span></code> · <code className="font-mono text-slate-600">PLATFORM_PROJECT_SLUG=<span className="text-blue-600">{project.slug}</span></code> · <code className="font-mono text-slate-500">PLATFORM_TEAM_SLUG</code> · <code className="font-mono text-slate-500">PLATFORM_AREA_SLUG</code> — pick team &amp; area slugs from the lists below.
          </p>
        </div>
        <button onClick={() => sync.mutate()} disabled={sync.isPending}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
          {sync.isPending ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
          {sync.isPending ? 'Syncing…' : 'Sync from ADO'}
        </button>
      </div>

      {error && <ErrorMessage message={error} />}
      {sync.data?.success && (
        <div className="rounded-lg bg-green-50 border border-green-200 px-3 py-2 text-sm text-green-700">
          Synced {sync.data.teams} teams, {sync.data.areas} areas, {sync.data.iterations} iterations, {sync.data.users} users.
        </div>
      )}
      {empty && !sync.isPending && (
        <div className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-2.5 text-sm text-amber-800">
          Nothing synced yet — click <strong>Sync from ADO</strong> to pull teams, areas, iterations, and users.
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200">
        {tabs.map(({ key, label, icon: Icon, count }) => (
          <button key={key} onClick={() => setTab(key)}
            className={cn('flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
              tab === key ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-800')}>
            <Icon size={14} /> {label}
            {count != null && <span className="text-xs text-slate-400">({count})</span>}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        {tab === 'teams'      && <TeamsTab projectId={projectId} />}
        {tab === 'areas'      && <AreasTab projectId={projectId} />}
        {tab === 'iterations' && <IterationsTab projectId={projectId} />}
        {tab === 'users'      && <UsersTab projectId={projectId} />}
      </div>
    </div>
  )
}

function indent(path: string | null) {
  if (!path) return 0
  return (path.match(/\\/g) || []).length
}
function leaf(path: string) { const i = path.lastIndexOf('\\'); return i >= 0 ? path.slice(i + 1) : path }

function TeamsTab({ projectId }: { projectId: string }) {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['ado-teams', projectId], queryFn: () => api.adoTeams(projectId) })
  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage message="Failed to load teams." onRetry={() => void refetch()} />
  const teams = data ?? []
  if (!teams.length) return <Empty text="No teams synced." />
  return (
    <div className="divide-y divide-slate-50">
      {teams.map((t: AdoTeam) => (
        <div key={t.id} className="px-5 py-3.5">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-slate-900">{t.name}</span>
            {t.slug && <SlugChip value={t.slug} />}
            <Badge label={`${t.memberCount} member${t.memberCount !== 1 ? 's' : ''}`} colorClass="text-slate-600 bg-slate-100" />
          </div>
          {t.description && <p className="text-xs text-slate-500 mt-0.5">{t.description}</p>}
          {t.defaultAreaPath && <p className="text-xs text-slate-500 mt-1">Default area: <span className="font-mono text-slate-600">{t.defaultAreaPath}</span></p>}
          {t.areaPaths?.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-1.5">
              {t.areaPaths.map(p => <span key={p} className="text-xs font-mono bg-slate-50 text-slate-600 rounded px-1.5 py-0.5">{p}</span>)}
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function AreasTab({ projectId }: { projectId: string }) {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['ado-areas', projectId], queryFn: () => api.adoAreas(projectId) })
  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage message="Failed to load areas." onRetry={() => void refetch()} />
  const areas = data ?? []
  if (!areas.length) return <Empty text="No area paths synced." />
  return (
    <div className="divide-y divide-slate-50">
      {areas.map((a: AdoArea) => (
        <div key={a.id} className="py-2 flex items-center gap-2" style={{ paddingLeft: `${20 + indent(a.path) * 18}px`, paddingRight: '20px' }}>
          <FolderTree size={13} className="text-slate-300 shrink-0" />
          <span className="text-sm text-slate-700">{leaf(a.path)}</span>
          {a.slug && <SlugChip value={a.slug} />}
          <span className="text-xs font-mono text-slate-400 truncate ml-auto">{a.path}</span>
        </div>
      ))}
    </div>
  )
}

function IterationsTab({ projectId }: { projectId: string }) {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['ado-iterations', projectId], queryFn: () => api.adoIterations(projectId) })
  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage message="Failed to load iterations." onRetry={() => void refetch()} />
  const iters = data ?? []
  if (!iters.length) return <Empty text="No iterations synced." />
  const fmt = (d: string | null) => d ? new Date(d).toLocaleDateString() : '—'
  return (
    <div className="divide-y divide-slate-50">
      {iters.map((it: AdoIteration) => (
        <div key={it.id} className="py-2 flex items-center gap-3" style={{ paddingLeft: `${20 + indent(it.path) * 18}px`, paddingRight: '20px' }}>
          <CalendarRange size={13} className="text-slate-300 shrink-0" />
          <span className="text-sm text-slate-700 flex-1 min-w-0 truncate">{leaf(it.path)}</span>
          {(it.startDate || it.finishDate) && (
            <span className="text-xs text-slate-500 shrink-0">{fmt(it.startDate)} → {fmt(it.finishDate)}</span>
          )}
        </div>
      ))}
    </div>
  )
}

const QUALITY_ROLES = ['QA', 'QE', 'SDET']
function qualityColor(role: string | null) {
  switch (role) {
    case 'QA':   return 'text-emerald-700 bg-emerald-100'
    case 'QE':   return 'text-teal-700 bg-teal-100'
    case 'SDET': return 'text-violet-700 bg-violet-100'
    default:     return 'text-slate-600 bg-slate-100'
  }
}

function UsersTab({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const [qualityOnly, setQualityOnly] = useState(false)
  const [search, setSearch] = useState('')
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['ado-users', projectId], queryFn: () => api.adoUsers(projectId) })

  const setRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string | null }) => api.setUserQualityRole(projectId, userId, role),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['ado-users', projectId] })
      qc.invalidateQueries({ queryKey: ['ado-summary', projectId] })
    },
  })

  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage message="Failed to load users." onRetry={() => void refetch()} />
  let users = data ?? []
  if (!users.length) return <Empty text="No users synced." />

  if (search) users = users.filter((u: AdoUser) =>
    (u.displayName ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (u.email ?? '').toLowerCase().includes(search.toLowerCase()))
  if (qualityOnly) users = users.filter((u: AdoUser) => u.qualityRole)

  return (
    <div>
      <div className="px-5 py-3 border-b border-slate-100 flex items-center gap-3">
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search users…"
          className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer select-none">
          <input type="checkbox" checked={qualityOnly} onChange={e => setQualityOnly(e.target.checked)} className="accent-blue-600" />
          Quality roles only
        </label>
      </div>
      <div className="divide-y divide-slate-50">
        {users.map((u: AdoUser) => (
          <div key={u.id} className="px-5 py-2.5 flex items-center gap-3">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-slate-800 truncate">{u.displayName ?? u.uniqueName}</p>
              {u.email && <p className="text-xs text-slate-400 truncate">{u.email}</p>}
            </div>
            <div className="flex items-center gap-2 shrink-0">
              {u.teamMember && <span className="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5">Team member</span>}
              <div className="flex gap-1">
                {QUALITY_ROLES.map(role => (
                  <button key={role}
                    onClick={() => setRole.mutate({ userId: u.id, role: u.qualityRole === role ? null : role })}
                    className={cn('text-xs rounded px-1.5 py-0.5 transition-colors',
                      u.qualityRole === role
                        ? qualityColor(role)
                        : 'text-slate-400 bg-slate-50 hover:bg-slate-100')}>
                    {role}
                  </button>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function Empty({ text }: { text: string }) {
  return <p className="px-5 py-8 text-sm text-slate-400 text-center">{text}</p>
}
