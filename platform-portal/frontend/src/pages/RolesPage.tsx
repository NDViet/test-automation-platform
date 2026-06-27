import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { ProjectSelect, TeamSelect } from '@/components/ScopeSelectors'
import { ShieldCheck, Trash2, UserPlus, Search } from 'lucide-react'
import type { PlatformRole, CredentialScope } from '@/lib/types'

const ORG_ROLES: PlatformRole[] = ['ORG_ADMIN', 'VIEWER']
const TEAM_ROLES: PlatformRole[] = ['TEAM_ADMIN', 'TEAM_MEMBER', 'VIEWER']

function roleColor(r: string): string {
  switch (r) {
    case 'ORG_ADMIN':
      return 'text-purple-700 bg-purple-100'
    case 'TEAM_ADMIN':
      return 'text-blue-700 bg-blue-100'
    case 'TEAM_MEMBER':
      return 'text-green-700 bg-green-100'
    default:
      return 'text-slate-600 bg-slate-100'
  }
}

export default function RolesPage() {
  const qc = useQueryClient()
  const [scope, setScope] = useState<Exclude<CredentialScope, 'PROJECT'>>('ORG')
  const [projectId, setProjectId] = useState('')
  const [teamId, setTeamId] = useState('')
  const [actor, setActor] = useState(() => localStorage.getItem('platform.actor') ?? '')
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<PlatformRole>('VIEWER')
  const [err, setErr] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  // ORG scope is global (scopeId = null/undefined); TEAM scope targets a sub-team.
  const effectiveScopeId = scope === 'ORG' ? undefined : teamId || undefined

  const {
    data: members,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['rbac-members', scope, effectiveScopeId ?? 'org'],
    queryFn: () => api.rbacMembers(scope, effectiveScopeId),
    enabled: scope === 'ORG' || !!effectiveScopeId,
  })

  const saveActor = (v: string) => {
    setActor(v)
    localStorage.setItem('platform.actor', v)
  }

  const grantMutation = useMutation({
    mutationFn: () =>
      api.grantRole(
        { userId: userId.trim(), scope, teamId: scope === 'ORG' ? null : effectiveScopeId, role },
        actor,
      ),
    onSuccess: () => {
      setUserId('')
      setErr(null)
      void qc.invalidateQueries({ queryKey: ['rbac-members'] })
    },
    onError: (e: Error) =>
      setErr(
        e.message.includes('403') ? 'Forbidden — your acting user lacks permission.' : e.message,
      ),
  })

  const revokeMutation = useMutation({
    mutationFn: (id: string) => api.revokeRole(id, actor),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['rbac-members'] }),
    onError: (e: Error) =>
      setErr(
        e.message.includes('400')
          ? 'Cannot revoke the last ORG_ADMIN.'
          : e.message.includes('403')
            ? 'Forbidden — your acting user lacks permission.'
            : e.message,
      ),
  })

  const roleOptions = scope === 'ORG' ? ORG_ROLES : TEAM_ROLES

  const q = search.trim().toLowerCase()
  const filtered = (members ?? []).filter(
    m => !q || m.userId.toLowerCase().includes(q) || m.role.toLowerCase().includes(q),
  )

  return (
    <div className="flex flex-col gap-6 h-full min-h-0">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
          <ShieldCheck size={22} /> Roles & Access
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Assign platform roles. Org-wide roles are managed by ORG_ADMINs; team roles by
          TEAM_ADMINs.
        </p>
      </div>

      {/* Acting-as (no SSO yet) */}
      <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 flex items-center gap-3">
        <span className="text-sm text-amber-800">Acting as:</span>
        <input
          value={actor}
          onChange={e => saveActor(e.target.value)}
          placeholder="your user id (e.g. alice)"
          className="text-sm border border-amber-300 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-amber-500"
        />
        <span className="text-xs text-amber-600">
          Until SSO is wired, this header (X-Actor) identifies you for authorization.
        </span>
      </div>

      {/* Scope */}
      <div className="flex items-center gap-3">
        <div className="inline-flex rounded-lg border border-slate-200 overflow-hidden">
          {(['ORG', 'TEAM'] as const).map(s => (
            <button
              key={s}
              onClick={() => setScope(s)}
              className={`px-4 py-1.5 text-sm font-medium ${scope === s ? 'bg-blue-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}
            >
              {s === 'ORG' ? 'Organization' : 'Team'}
            </button>
          ))}
        </div>
        {scope === 'TEAM' && (
          <>
            <ProjectSelect value={projectId} onChange={setProjectId} />
            <TeamSelect projectId={projectId} value={teamId} onChange={setTeamId} />
          </>
        )}
      </div>

      {err && <ErrorMessage message={err} />}

      {/* Grant form */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
        <h2 className="font-semibold text-slate-900 mb-3 flex items-center gap-2">
          <UserPlus size={16} /> Grant role
        </h2>
        <div className="flex items-end gap-3">
          <div className="flex-1">
            <label className="block text-xs font-medium text-slate-700 mb-1">User ID</label>
            <input
              value={userId}
              onChange={e => setUserId(e.target.value)}
              placeholder="e.g. bob"
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1">Role</label>
            <select
              value={role}
              onChange={e => setRole(e.target.value as PlatformRole)}
              className="text-sm border border-slate-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {roleOptions.map(r => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>
          <button
            onClick={() => userId.trim() && grantMutation.mutate()}
            disabled={!userId.trim() || grantMutation.isPending || (scope === 'TEAM' && !teamId)}
            className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {grantMutation.isPending ? 'Granting…' : 'Grant'}
          </button>
        </div>
      </div>

      {/* Members list */}
      {isLoading && <LoadingSpinner message="Loading members…" />}
      {error && <ErrorMessage message="Failed to load members." onRetry={() => void refetch()} />}
      {!isLoading && !error && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm flex flex-col flex-1 min-h-0">
          {/* Search header */}
          <div className="flex items-center gap-3 px-5 py-3 border-b border-slate-100">
            <div className="relative flex-1">
              <Search
                size={15}
                className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="Search by user or role…"
                className="w-full text-sm border border-slate-200 rounded-lg pl-9 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <span className="text-xs text-slate-400 shrink-0">
              {q ? `${filtered.length} of ${members?.length ?? 0}` : `${members?.length ?? 0}`}{' '}
              member
              {(members?.length ?? 0) === 1 ? '' : 's'}
            </span>
          </div>

          {/* Scrollable body */}
          <div className="flex-1 min-h-0 overflow-y-auto divide-y divide-slate-50">
            {(!members || members.length === 0) && (
              <p className="px-5 py-8 text-center text-sm text-slate-500">
                No role assignments at this scope.
              </p>
            )}
            {members && members.length > 0 && filtered.length === 0 && (
              <p className="px-5 py-8 text-center text-sm text-slate-500">
                No members match “{search}”.
              </p>
            )}
            {filtered.map(m => (
              <div key={m.id} className="px-5 py-3 flex items-center justify-between gap-4">
                <div className="flex items-center gap-3 min-w-0">
                  <span className="text-sm font-medium text-slate-900 truncate">{m.userId}</span>
                  <span className={`text-xs px-1.5 py-0.5 rounded shrink-0 ${roleColor(m.role)}`}>
                    {m.role}
                  </span>
                  <span className="text-xs text-slate-400 shrink-0">
                    {m.grantedBy && `by ${m.grantedBy} · `}
                    {relativeTime(m.grantedAt)}
                  </span>
                </div>
                <button
                  onClick={() => revokeMutation.mutate(m.id)}
                  disabled={revokeMutation.isPending}
                  className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg shrink-0"
                  title="Revoke"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
