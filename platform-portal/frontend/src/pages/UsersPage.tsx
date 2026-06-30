import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { UserPlus, KeyRound, ShieldCheck, Trash2, Plus, Search } from 'lucide-react'
import { api } from '@/lib/api'
import type { AdminUser } from '@/lib/types'

const ROLES = ['VIEWER', 'TESTER', 'PROJECT_ADMIN', 'ORG_ADMIN'] as const

/**
 * Super-admin / org-admin user administration: create users, enable/disable, reset password, and
 * grant/revoke role grants (written to user_roles). The backend enforces all guards; this surfaces
 * the server's error message on failure (e.g. last-admin protection).
 */
export default function UsersPage() {
  const qc = useQueryClient()
  const { data: users, isLoading } = useQuery({
    queryKey: ['admin-users'],
    queryFn: api.adminUsers,
  })
  const { data: orgs } = useQuery({ queryKey: ['organizations'], queryFn: api.organizations })
  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => api.projects() })

  const [error, setError] = useState<string | null>(null)
  const invalidate = () => qc.invalidateQueries({ queryKey: ['admin-users'] })
  const onErr = (e: unknown) => setError(e instanceof Error ? e.message : 'Request failed')

  // Quick filter: matches username, display name, email, or any granted role.
  const [filter, setFilter] = useState('')
  const q = filter.trim().toLowerCase()
  const filtered = (users ?? []).filter(
    u =>
      !q ||
      u.username.toLowerCase().includes(q) ||
      (u.displayName ?? '').toLowerCase().includes(q) ||
      (u.email ?? '').toLowerCase().includes(q) ||
      u.roles.some(r => r.role.toLowerCase().includes(q)),
  )

  // Create-user form
  const [form, setForm] = useState({ username: '', displayName: '', email: '', tempPassword: '' })
  const createMut = useMutation({
    mutationFn: () => api.adminCreateUser(form),
    onSuccess: () => {
      setForm({ username: '', displayName: '', email: '', tempPassword: '' })
      setError(null)
      invalidate()
    },
    onError: onErr,
  })

  const enableMut = useMutation({
    mutationFn: (v: { id: string; enabled: boolean }) => api.adminSetUserEnabled(v.id, v.enabled),
    onSuccess: invalidate,
    onError: onErr,
  })
  const resetMut = useMutation({
    mutationFn: (v: { id: string; pw: string }) => api.adminResetPassword(v.id, v.pw),
    onSuccess: () => setError(null),
    onError: onErr,
  })
  const revokeMut = useMutation({
    mutationFn: (grantId: string) => api.adminRevokeRole(grantId),
    onSuccess: invalidate,
    onError: onErr,
  })

  return (
    <div className="mx-auto w-full max-w-5xl flex-1 min-h-0 overflow-y-auto space-y-6 p-6">
      <header>
        <h1 className="text-xl font-semibold text-slate-800">Users &amp; Roles</h1>
        <p className="text-sm text-slate-500">
          Create users and manage their role grants. Admin-created users must change their password
          on first sign-in.
        </p>
      </header>

      {error && (
        <div className="rounded-md border border-rose-200 bg-rose-50 px-4 py-2 text-sm text-rose-700">
          {error}
        </div>
      )}

      {/* Create user */}
      <section className="rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
          <UserPlus size={16} /> New user
        </h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
          <input
            className="rounded border border-slate-300 px-3 py-2 text-sm"
            placeholder="username"
            value={form.username}
            onChange={e => setForm({ ...form, username: e.target.value })}
          />
          <input
            className="rounded border border-slate-300 px-3 py-2 text-sm"
            placeholder="display name"
            value={form.displayName}
            onChange={e => setForm({ ...form, displayName: e.target.value })}
          />
          <input
            className="rounded border border-slate-300 px-3 py-2 text-sm"
            placeholder="email (optional)"
            value={form.email}
            onChange={e => setForm({ ...form, email: e.target.value })}
          />
          <input
            className="rounded border border-slate-300 px-3 py-2 text-sm"
            placeholder="temp password"
            type="text"
            value={form.tempPassword}
            onChange={e => setForm({ ...form, tempPassword: e.target.value })}
          />
        </div>
        <button
          className="mt-3 inline-flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
          disabled={!form.username || !form.tempPassword || createMut.isPending}
          onClick={() => createMut.mutate()}
        >
          <UserPlus size={15} /> Create user
        </button>
      </section>

      {/* User list */}
      <section className="space-y-3">
        {/* Quick filter */}
        <div className="flex items-center gap-3">
          <div className="relative flex-1">
            <Search
              size={15}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <input
              className="w-full rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm"
              placeholder="Filter by username, name, email, or role…"
              value={filter}
              onChange={e => setFilter(e.target.value)}
            />
          </div>
          <span className="shrink-0 text-xs text-slate-500">
            {filtered.length} of {(users ?? []).length}
          </span>
        </div>

        {isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {!isLoading && filtered.length === 0 && (
          <p className="py-6 text-center text-sm text-slate-400">No users match “{filter}”.</p>
        )}
        {filtered.map(u => (
          <UserCard
            key={u.id}
            user={u}
            orgs={orgs ?? []}
            projects={projects ?? []}
            onToggleEnabled={() => enableMut.mutate({ id: u.id, enabled: !u.enabled })}
            onReset={() => {
              const pw = window.prompt(`New temporary password for ${u.username}:`)
              if (pw) resetMut.mutate({ id: u.id, pw })
            }}
            onRevoke={grantId => revokeMut.mutate(grantId)}
            onGranted={invalidate}
            onError={onErr}
          />
        ))}
      </section>
    </div>
  )
}

function UserCard({
  user,
  orgs,
  projects,
  onToggleEnabled,
  onReset,
  onRevoke,
  onGranted,
  onError,
}: {
  user: AdminUser
  orgs: { id: string; name: string }[]
  projects: { id: string; name: string }[]
  onToggleEnabled: () => void
  onReset: () => void
  onRevoke: (grantId: string) => void
  onGranted: () => void
  onError: (e: unknown) => void
}) {
  const [role, setRole] = useState<(typeof ROLES)[number]>('VIEWER')
  const [scopeId, setScopeId] = useState('')
  const isOrgRole = role === 'ORG_ADMIN'

  const grantMut = useMutation({
    mutationFn: () =>
      api.adminGrantRole(user.id, {
        role,
        scope: isOrgRole ? 'ORG' : 'PROJECT',
        scopeId,
      }),
    onSuccess: () => {
      setScopeId('')
      onGranted()
    },
    onError,
  })

  const targets = isOrgRole ? orgs : projects

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <span className="font-medium text-slate-800">{user.username}</span>
          {user.displayName && (
            <span className="ml-2 text-sm text-slate-500">{user.displayName}</span>
          )}
          {user.superAdmin && (
            <span className="ml-2 rounded bg-violet-100 px-1.5 py-0.5 text-[11px] font-medium text-violet-700">
              super-admin
            </span>
          )}
          {!user.enabled && (
            <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-[11px] text-slate-600">
              disabled
            </span>
          )}
          {user.mustChangePassword && (
            <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-[11px] text-amber-700">
              must change pw
            </span>
          )}
        </div>
        <div className="flex gap-2">
          <button
            className="inline-flex items-center gap-1 rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
            onClick={onReset}
          >
            <KeyRound size={13} /> Reset password
          </button>
          <button
            className="inline-flex items-center gap-1 rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-40"
            onClick={onToggleEnabled}
            disabled={user.superAdmin && user.enabled}
            title={user.superAdmin && user.enabled ? 'Super-admins stay enabled' : undefined}
          >
            {user.enabled ? 'Disable' : 'Enable'}
          </button>
        </div>
      </div>

      {/* Role grants */}
      <div className="mt-3 flex flex-wrap gap-1.5">
        {user.roles.length === 0 && <span className="text-xs text-slate-400">No role grants</span>}
        {user.roles.map(g => (
          <span
            key={g.id}
            className="inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600"
          >
            <ShieldCheck size={11} />
            {g.role} · {g.scope.toLowerCase()}
            <button
              className="text-slate-400 hover:text-rose-600"
              title="Revoke"
              onClick={() => onRevoke(g.id)}
            >
              <Trash2 size={11} />
            </button>
          </span>
        ))}
      </div>

      {/* Grant a role */}
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <select
          className="rounded border border-slate-300 px-2 py-1 text-xs"
          value={role}
          onChange={e => {
            setRole(e.target.value as (typeof ROLES)[number])
            setScopeId('')
          }}
        >
          {ROLES.map(r => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
        <select
          className="rounded border border-slate-300 px-2 py-1 text-xs"
          value={scopeId}
          onChange={e => setScopeId(e.target.value)}
        >
          <option value="">{isOrgRole ? 'Select organization…' : 'Select project…'}</option>
          {targets.map(t => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <button
          className="inline-flex items-center gap-1 rounded bg-blue-600 px-2 py-1 text-xs font-medium text-white disabled:opacity-50"
          disabled={!scopeId || grantMut.isPending}
          onClick={() => grantMut.mutate()}
        >
          <Plus size={13} /> Grant
        </button>
      </div>
    </div>
  )
}
