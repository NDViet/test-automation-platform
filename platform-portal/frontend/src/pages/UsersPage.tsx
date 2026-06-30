import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { UserPlus, KeyRound, ShieldCheck, Trash2, Plus, Search, Users } from 'lucide-react'
import { api } from '@/lib/api'
import type { AdminUser } from '@/lib/types'
import { Button, Card, CardBody, Input, Select, PageHeader, StatusBadge } from '@/components/ui'

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
    <div className="mx-auto w-full max-w-4xl flex-1 min-h-0 overflow-y-auto scrollbar-thin space-y-5">
      <PageHeader
        title="Users & Roles"
        icon={<Users size={20} />}
        description="Create users and manage their role grants. Admin-created users must change their password on first sign-in."
      />

      {error && (
        <div
          role="alert"
          className="rounded-md border border-danger-border bg-danger-bg px-4 py-2 text-sm text-danger"
        >
          {error}
        </div>
      )}

      {/* Create user */}
      <Card>
        <CardBody>
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg">
            <UserPlus size={16} className="text-fg-muted" /> New user
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
            <Input
              placeholder="username"
              aria-label="Username"
              value={form.username}
              onChange={e => setForm({ ...form, username: e.target.value })}
            />
            <Input
              placeholder="display name"
              aria-label="Display name"
              value={form.displayName}
              onChange={e => setForm({ ...form, displayName: e.target.value })}
            />
            <Input
              placeholder="email (optional)"
              aria-label="Email"
              value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
            />
            <Input
              placeholder="temp password"
              aria-label="Temporary password"
              type="text"
              value={form.tempPassword}
              onChange={e => setForm({ ...form, tempPassword: e.target.value })}
            />
          </div>
          <Button
            className="mt-3"
            disabled={!form.username || !form.tempPassword}
            loading={createMut.isPending}
            onClick={() => createMut.mutate()}
          >
            <UserPlus size={15} /> Create user
          </Button>
        </CardBody>
      </Card>

      {/* User list */}
      <section className="space-y-3">
        {/* Quick filter */}
        <div className="flex items-center gap-3">
          <div className="relative flex-1">
            <Search
              size={15}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle"
            />
            <Input
              className="pl-9"
              placeholder="Filter by username, name, email, or role…"
              aria-label="Filter users"
              value={filter}
              onChange={e => setFilter(e.target.value)}
            />
          </div>
          <span className="shrink-0 text-xs text-fg-muted">
            {filtered.length} of {(users ?? []).length}
          </span>
        </div>

        {isLoading && <p className="text-sm text-fg-muted">Loading…</p>}
        {!isLoading && filtered.length === 0 && (
          <p className="py-6 text-center text-sm text-fg-subtle">No users match “{filter}”.</p>
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
    <Card>
      <CardBody>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-fg">{user.username}</span>
            {user.displayName && <span className="text-sm text-fg-muted">{user.displayName}</span>}
            {user.superAdmin && <StatusBadge variant="primary">super-admin</StatusBadge>}
            {!user.enabled && <StatusBadge variant="neutral">disabled</StatusBadge>}
            {user.mustChangePassword && (
              <StatusBadge variant="warning">must change pw</StatusBadge>
            )}
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={onReset}>
              <KeyRound size={13} /> Reset password
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={onToggleEnabled}
              disabled={user.superAdmin && user.enabled}
              title={user.superAdmin && user.enabled ? 'Super-admins stay enabled' : undefined}
            >
              {user.enabled ? 'Disable' : 'Enable'}
            </Button>
          </div>
        </div>

        {/* Role grants */}
        <div className="mt-3 flex flex-wrap gap-1.5">
          {user.roles.length === 0 && <span className="text-xs text-fg-subtle">No role grants</span>}
          {user.roles.map(g => (
            <StatusBadge key={g.id} variant="neutral" className="rounded-md">
              <ShieldCheck size={11} />
              {g.role} · {g.scope.toLowerCase()}
              <button
                className="text-fg-subtle hover:text-danger"
                title="Revoke role"
                aria-label={`Revoke ${g.role}`}
                onClick={() => onRevoke(g.id)}
              >
                <Trash2 size={11} />
              </button>
            </StatusBadge>
          ))}
        </div>

        {/* Grant a role */}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Select
            containerClassName="w-auto"
            aria-label="Role"
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
          </Select>
          <Select
            containerClassName="w-auto min-w-[12rem]"
            aria-label={isOrgRole ? 'Organization' : 'Project'}
            value={scopeId}
            onChange={e => setScopeId(e.target.value)}
          >
            <option value="">{isOrgRole ? 'Select organization…' : 'Select project…'}</option>
            {targets.map(t => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </Select>
          <Button
            size="sm"
            disabled={!scopeId}
            loading={grantMut.isPending}
            onClick={() => grantMut.mutate()}
          >
            <Plus size={13} /> Grant
          </Button>
        </div>
      </CardBody>
    </Card>
  )
}
