import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import AdoOnboardWizard from '@/components/AdoOnboardWizard'
import { OrganizationSelect, ProjectSelect, TeamSelect } from '@/components/ScopeSelectors'
import {
  Plus,
  Trash2,
  Plug,
  CheckCircle2,
  XCircle,
  ShieldCheck,
  X,
  Loader2,
  RefreshCw,
  UserCog,
} from 'lucide-react'
import type {
  Credential,
  CredentialScope,
  SaveCredentialForm,
  GithubRepo,
  AzureOrg,
} from '@/lib/types'

type FieldSpec = { key: string; label: string; placeholder?: string }
type TypeSpec = { params: FieldSpec[]; secrets: FieldSpec[]; baseUrl?: string }

// Non-secret params vs secret fields per integration type.
const TYPE_SPECS: Record<string, TypeSpec> = {
  JIRA_CLOUD: {
    baseUrl: 'https://your-org.atlassian.net',
    params: [
      { key: 'email', label: 'Account email', placeholder: 'bot@acme.com' },
      { key: 'project_key', label: 'Project key', placeholder: 'PAY' },
    ],
    secrets: [{ key: 'apiToken', label: 'API token' }],
  },
  // PAT-first onboarding: a token can span multiple Azure orgs — pick which to manage
  // after saving (like GitHub repos). Only the token + display name are needed up front.
  AZURE_DEVOPS_BOARDS: {
    baseUrl: 'https://dev.azure.com',
    params: [],
    secrets: [{ key: 'pat', label: 'Personal access token' }],
  },
  // GitHub onboards from a PAT alone — repos are discovered and selected after saving.
  GITHUB_ISSUES: {
    baseUrl: 'https://api.github.com',
    params: [],
    secrets: [{ key: 'pat', label: 'Personal access token' }],
  },
  GITHUB: {
    baseUrl: 'https://api.github.com',
    params: [],
    secrets: [{ key: 'pat', label: 'Personal access token' }],
  },
  AZURE_DEVOPS_REPOS: {
    baseUrl: 'https://dev.azure.com',
    params: [
      { key: 'organization', label: 'Organization' },
      { key: 'project', label: 'Project' },
      { key: 'repository', label: 'Repository' },
    ],
    secrets: [{ key: 'pat', label: 'Personal access token' }],
  },
  LINEAR: { params: [], secrets: [{ key: 'token', label: 'API key' }] },
}
const TYPES = Object.keys(TYPE_SPECS)

const emptyForm = (): SaveCredentialForm => ({
  scope: 'ORG',
  integrationType: 'AZURE_DEVOPS_BOARDS',
  displayName: '',
  baseUrl: '',
  connectionParams: {},
  secret: {},
  enabled: true,
})

export default function AdminIntegrationsPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [showAdoOnboard, setShowAdoOnboard] = useState(false)
  const [scope, setScope] = useState<CredentialScope>('ORG')
  const [orgId, setOrgId] = useState<string>('')
  const [projectId, setProjectId] = useState<string>('')
  const [teamId, setTeamId] = useState<string>('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<SaveCredentialForm>(emptyForm())
  const [testResult, setTestResult] = useState<Record<string, { ok: boolean; message: string }>>({})
  const [syncResult, setSyncResult] = useState<Record<string, { ok: boolean; message: string }>>({})
  const [adminResult, setAdminResult] = useState<Record<string, { ok: boolean; message: string }>>(
    {},
  )
  const [repoCred, setRepoCred] = useState<Credential | null>(null)
  const [orgCred, setOrgCred] = useState<Credential | null>(null)

  // ADO-first: every scope is keyed by an id — ORG=organization, PROJECT=project, TEAM=sub-team.
  const effectiveScopeId = scope === 'ORG' ? orgId : scope === 'PROJECT' ? projectId : teamId

  const {
    data: creds,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['credentials', scope, effectiveScopeId || 'none'],
    queryFn: () => api.credentials(scope, effectiveScopeId),
    enabled: !!effectiveScopeId,
  })

  const saveMutation = useMutation({
    mutationFn: () =>
      api.saveCredential({
        ...form,
        scope,
        scopeId: effectiveScopeId,
        // strip blank secret values so an update keeps the existing secret
        secret: Object.fromEntries(
          Object.entries(form.secret ?? {}).filter(([, v]) => v && v.trim()),
        ),
      }),
    onSuccess: () => {
      setShowForm(false)
      setForm(emptyForm())
      void qc.invalidateQueries({ queryKey: ['credentials'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteCredential(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['credentials'] }),
  })

  const testMutation = useMutation({
    mutationFn: (id: string) => api.testCredential(id),
    onSuccess: (res, id) => setTestResult(prev => ({ ...prev, [id]: res })),
    onError: (_e, id) =>
      setTestResult(prev => ({ ...prev, [id]: { ok: false, message: 'Request failed' } })),
  })

  // Pull an ADO org's structure into the platform on demand (projects, teams, areas, users, RBAC).
  const syncMutation = useMutation({
    mutationFn: (c: Credential) =>
      api.adoResync({ organizationId: c.scopeId as string, credentialId: c.id }),
    onSuccess: (res, c) => {
      const failed = res.structure.failures.length
      const msg =
        `Synced ${res.projects.total} project(s), ${res.structure.teamsCreated} new team(s), ` +
        `${res.members.membersSeen} member(s)` +
        (failed ? ` · ${failed} project(s) had sync errors` : '')
      setSyncResult(prev => ({ ...prev, [c.id]: { ok: failed === 0, message: msg } }))
    },
    onError: (e, c) =>
      setSyncResult(prev => ({
        ...prev,
        [c.id]: { ok: false, message: (e as Error).message || 'Sync failed' },
      })),
  })

  // Fetch the PAT owner ("me") from Azure DevOps and grant them org admin.
  const claimAdminMutation = useMutation({
    mutationFn: (c: Credential) => api.adoClaimAdmin(c.id),
    onSuccess: (res, c) => {
      const msg = res.alreadyAdmin
        ? `${res.email} is already an org admin`
        : `${res.email} is now an org admin`
      setAdminResult(prev => ({ ...prev, [c.id]: { ok: true, message: msg } }))
    },
    onError: (e, c) =>
      setAdminResult(prev => ({
        ...prev,
        [c.id]: { ok: false, message: (e as Error).message || 'Could not resolve your profile' },
      })),
  })

  const spec = TYPE_SPECS[form.integrationType] ?? { params: [], secrets: [] }
  // Token (primary secret) is mandatory when creating a credential.
  const primarySecretKey = spec.secrets[0]?.key
  const tokenMissing = !!primarySecretKey && !(form.secret?.[primarySecretKey] ?? '').trim()
  const cannotSave = !form.displayName.trim() || !effectiveScopeId || tokenMissing
  const setParam = (k: string, v: string) =>
    setForm(f => ({ ...f, connectionParams: { ...f.connectionParams, [k]: v } }))
  const setSecret = (k: string, v: string) =>
    setForm(f => ({ ...f, secret: { ...f.secret, [k]: v } }))

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
            <Plug size={22} /> Integration Credentials
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Centralized credentials (Admin PAT) that projects and teams inherit. Secrets are
            encrypted at rest and resolved by precedence{' '}
            <strong>Team → Project → Organization</strong>.
          </p>
        </div>
        <button
          onClick={() => setShowAdoOnboard(true)}
          title="Import another Azure DevOps organization (projects, teams, members) into the platform"
          className="shrink-0 flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 text-slate-700 text-sm font-medium rounded-lg hover:bg-slate-50"
        >
          <RefreshCw size={15} /> Import Azure DevOps org
        </button>
      </div>

      {/* Scope selector */}
      <div className="flex items-center gap-3">
        <div className="inline-flex rounded-lg border border-slate-200 overflow-hidden">
          {(['ORG', 'PROJECT', 'TEAM'] as CredentialScope[]).map(s => (
            <button
              key={s}
              onClick={() => setScope(s)}
              className={`px-4 py-1.5 text-sm font-medium ${scope === s ? 'bg-blue-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}
            >
              {s === 'ORG' ? 'Organization' : s === 'PROJECT' ? 'Project' : 'Team'}
            </button>
          ))}
        </div>
        {scope === 'ORG' && <OrganizationSelect value={orgId} onChange={setOrgId} />}
        {scope === 'PROJECT' && <ProjectSelect value={projectId} onChange={setProjectId} />}
        {scope === 'TEAM' && (
          <>
            <ProjectSelect value={projectId} onChange={setProjectId} />
            <TeamSelect projectId={projectId} value={teamId} onChange={setTeamId} />
          </>
        )}
        <button
          onClick={() => {
            setForm({ ...emptyForm(), scope })
            setShowForm(v => !v)
          }}
          className="ml-auto flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700"
        >
          <Plus size={16} /> Add credential
        </button>
      </div>

      {/* Add/Edit form */}
      {showForm && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Type">
              <select
                value={form.integrationType}
                onChange={e =>
                  setForm(f => ({
                    ...f,
                    integrationType: e.target.value,
                    connectionParams: {},
                    secret: {},
                    baseUrl: TYPE_SPECS[e.target.value]?.baseUrl ?? '',
                  }))
                }
                className={inputCls}
              >
                {TYPES.map(t => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Display name">
              <input
                className={inputCls}
                value={form.displayName}
                placeholder="e.g. Acme Azure DevOps"
                onChange={e => setForm(f => ({ ...f, displayName: e.target.value }))}
              />
            </Field>
          </div>

          <Field label="Base URL">
            <input
              className={inputCls}
              value={form.baseUrl ?? ''}
              placeholder={spec.baseUrl}
              onChange={e => setForm(f => ({ ...f, baseUrl: e.target.value }))}
            />
          </Field>

          {spec.params.length > 0 && (
            <div className="grid grid-cols-2 gap-4">
              {spec.params.map(p => (
                <Field key={p.key} label={p.label}>
                  <input
                    className={inputCls}
                    placeholder={p.placeholder}
                    value={form.connectionParams?.[p.key] ?? ''}
                    onChange={e => setParam(p.key, e.target.value)}
                  />
                </Field>
              ))}
            </div>
          )}

          <div className="grid grid-cols-2 gap-4">
            {spec.secrets.map(s => (
              <Field key={s.key} label={`${s.label} (secret)`}>
                <input
                  type="password"
                  autoComplete="new-password"
                  className={inputCls}
                  placeholder="•••••• (leave blank to keep existing)"
                  value={form.secret?.[s.key] ?? ''}
                  onChange={e => setSecret(s.key, e.target.value)}
                />
              </Field>
            ))}
          </div>

          <div className="flex items-center justify-between pt-2">
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input
                type="checkbox"
                checked={form.enabled ?? true}
                onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))}
              />
              Enabled
            </label>
            <div className="flex gap-2">
              <button
                onClick={() => {
                  setShowForm(false)
                  setForm(emptyForm())
                }}
                className="px-4 py-2 text-sm text-slate-600 rounded-lg hover:bg-slate-100"
              >
                Cancel
              </button>
              <button
                onClick={() => void saveMutation.mutate()}
                disabled={cannotSave || saveMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                <ShieldCheck size={16} /> {saveMutation.isPending ? 'Saving…' : 'Save credential'}
              </button>
            </div>
          </div>
          {cannotSave && (
            <p className="text-xs text-amber-600">
              {!effectiveScopeId &&
                (scope === 'ORG'
                  ? 'Select an organization above — create one first in Settings → Organization if there are none. '
                  : `Select a ${scope.toLowerCase()} above. `)}
              {!form.displayName.trim() && 'Display name is required. '}
              {tokenMissing && `${spec.secrets[0]?.label} is required.`}
            </p>
          )}
          {saveMutation.isError && (
            <p className="text-xs text-red-600">
              Failed to save. Check fields and PLATFORM_CRED_KEY.
            </p>
          )}
        </div>
      )}

      {/* List */}
      {isLoading && <LoadingSpinner message="Loading credentials…" />}
      {error && (
        <ErrorMessage message="Failed to load credentials." onRetry={() => void refetch()} />
      )}
      {!isLoading && !error && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-50">
          {(!creds || creds.length === 0) && (
            <p className="px-5 py-8 text-center text-sm text-slate-500">
              No credentials at this scope. Add one — projects will inherit it.
            </p>
          )}
          {(creds ?? []).map((c: Credential) => {
            const r = testResult[c.id]
            return (
              <div key={c.id} className="px-5 py-4 flex items-center justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-900">{c.displayName}</span>
                    <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                      {c.integrationType}
                    </span>
                    {!c.enabled && (
                      <span className="text-xs px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">
                        disabled
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-3 mt-0.5 text-xs text-slate-400">
                    {c.baseUrl && <span className="truncate">{c.baseUrl}</span>}
                    <span>{c.hasSecret ? '🔒 secret set' : '⚠ no secret'}</span>
                    <span>updated {relativeTime(c.updatedAt)}</span>
                  </div>
                  {r && (
                    <div
                      className={`flex items-center gap-1 mt-1 text-xs ${r.ok ? 'text-green-600' : 'text-red-600'}`}
                    >
                      {r.ok ? <CheckCircle2 size={13} /> : <XCircle size={13} />} {r.message}
                    </div>
                  )}
                  {syncResult[c.id] && (
                    <div
                      className={`flex items-center gap-1 mt-1 text-xs ${syncResult[c.id].ok ? 'text-green-600' : 'text-amber-600'}`}
                    >
                      {syncResult[c.id].ok ? <CheckCircle2 size={13} /> : <XCircle size={13} />}{' '}
                      {syncResult[c.id].message}
                    </div>
                  )}
                  {adminResult[c.id] && (
                    <div
                      className={`flex items-center gap-1 mt-1 text-xs ${adminResult[c.id].ok ? 'text-green-600' : 'text-red-600'}`}
                    >
                      {adminResult[c.id].ok ? <CheckCircle2 size={13} /> : <XCircle size={13} />}{' '}
                      {adminResult[c.id].message}
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {(c.integrationType === 'GITHUB' || c.integrationType === 'GITHUB_ISSUES') && (
                    <button
                      onClick={() => setRepoCred(c)}
                      className="px-3 py-1.5 text-xs font-medium border border-slate-200 rounded-lg hover:bg-slate-50"
                    >
                      Repositories
                    </button>
                  )}
                  {c.integrationType === 'AZURE_DEVOPS_BOARDS' && (
                    <button
                      onClick={() => setOrgCred(c)}
                      className="px-3 py-1.5 text-xs font-medium border border-slate-200 rounded-lg hover:bg-slate-50"
                    >
                      Organizations
                    </button>
                  )}
                  {c.integrationType === 'AZURE_DEVOPS_BOARDS' &&
                    c.scope === 'ORG' &&
                    c.scopeId && (
                      <button
                        onClick={() => void syncMutation.mutate(c)}
                        disabled={syncMutation.isPending && syncMutation.variables?.id === c.id}
                        title="Pull projects, teams, areas, users and roles from Azure DevOps into the platform"
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50"
                      >
                        {syncMutation.isPending && syncMutation.variables?.id === c.id ? (
                          <>
                            <Loader2 size={13} className="animate-spin" /> Syncing…
                          </>
                        ) : (
                          <>
                            <RefreshCw size={13} /> Sync to platform
                          </>
                        )}
                      </button>
                    )}
                  {c.integrationType === 'AZURE_DEVOPS_BOARDS' && (
                    <button
                      onClick={() => void claimAdminMutation.mutate(c)}
                      disabled={
                        claimAdminMutation.isPending && claimAdminMutation.variables?.id === c.id
                      }
                      title="Fetch your Azure DevOps profile (me) and make yourself an org admin"
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50"
                    >
                      {claimAdminMutation.isPending && claimAdminMutation.variables?.id === c.id ? (
                        <>
                          <Loader2 size={13} className="animate-spin" /> Resolving…
                        </>
                      ) : (
                        <>
                          <UserCog size={13} /> Set me as admin
                        </>
                      )}
                    </button>
                  )}
                  <button
                    onClick={() => void testMutation.mutate(c.id)}
                    disabled={testMutation.isPending}
                    className="px-3 py-1.5 text-xs font-medium border border-slate-200 rounded-lg hover:bg-slate-50"
                  >
                    {testMutation.isPending && testMutation.variables === c.id
                      ? 'Testing…'
                      : 'Test connection'}
                  </button>
                  <button
                    onClick={() => void deleteMutation.mutate(c.id)}
                    className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                    title="Delete"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {repoCred && <RepoSelectModal credential={repoCred} onClose={() => setRepoCred(null)} />}
      {orgCred && <OrgSelectModal credential={orgCred} onClose={() => setOrgCred(null)} />}

      <AdoOnboardWizard
        open={showAdoOnboard}
        onClose={() => setShowAdoOnboard(false)}
        onDone={slug => {
          setShowAdoOnboard(false)
          void qc.invalidateQueries({ queryKey: ['credentials'] })
          void qc.invalidateQueries({ queryKey: ['organizations'] })
          navigate(`/${slug}`)
        }}
      />
    </div>
  )
}

// ── Azure DevOps: discover orgs the PAT can access and pick which to manage ──────────
function OrgSelectModal({ credential, onClose }: { credential: Credential; onClose: () => void }) {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [seeded, setSeeded] = useState(false)

  const {
    data: orgs = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['azureOrgs', credential.id],
    queryFn: () => api.azureOrgs(credential.id),
    select: (data: AzureOrg[]) => {
      if (!seeded) {
        setSelected(new Set(data.filter(o => o.managed).map(o => o.accountName)))
        setSeeded(true)
      }
      return data
    },
  })

  const save = useMutation({
    mutationFn: () =>
      api.setAzureOrgs(
        credential.id,
        orgs.filter(o => selected.has(o.accountName)),
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['azureOrgs', credential.id] })
      onClose()
    },
  })

  const toggle = (name: string) =>
    setSelected(p => {
      const n = new Set(p)
      n.has(name) ? n.delete(name) : n.add(name)
      return n
    })
  const shown = orgs.filter(
    o => !search.trim() || o.accountName.toLowerCase().includes(search.toLowerCase()),
  )

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-xl mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div>
            <h2 className="font-semibold text-slate-900">Manage organizations</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              {credential.displayName} · {selected.size} selected
            </p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && (
            <ErrorMessage
              message="Failed to list organizations — check the PAT and its scopes."
              onRetry={() => void refetch()}
            />
          )}
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Filter organizations…"
            className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {isLoading && (
            <div className="py-8 text-center text-sm text-slate-400 flex items-center justify-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Discovering organizations via the PAT…
            </div>
          )}
          {!isLoading && !error && shown.length === 0 && (
            <div className="py-8 text-center text-sm text-slate-500">No organizations.</div>
          )}
          {shown.length > 0 && (
            <div className="border border-slate-200 rounded-lg max-h-80 overflow-y-auto divide-y divide-slate-50">
              {shown.map(o => (
                <label
                  key={o.accountName}
                  className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-slate-50"
                >
                  <input
                    type="checkbox"
                    checked={selected.has(o.accountName)}
                    onChange={() => toggle(o.accountName)}
                    className="rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0"
                  />
                  <span className="text-sm text-slate-800 flex-1 truncate">{o.accountName}</span>
                </label>
              ))}
            </div>
          )}
        </div>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-between items-center">
          <span className="text-xs text-slate-400">
            {orgs.length} accessible · {selected.size} managed
          </span>
          <div className="flex gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              onClick={() => save.mutate()}
              disabled={save.isPending || isLoading}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
            >
              {save.isPending && <Loader2 size={14} className="animate-spin" />}Save selection
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── GitHub: discover accessible repos from the PAT and pick which to manage ──────────
function RepoSelectModal({ credential, onClose }: { credential: Credential; onClose: () => void }) {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [seeded, setSeeded] = useState(false)

  const {
    data: repos = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['githubRepos', credential.id],
    queryFn: () => api.githubRepos(credential.id),
    select: (data: GithubRepo[]) => {
      if (!seeded) {
        setSelected(new Set(data.filter(r => r.managed).map(r => r.fullName)))
        setSeeded(true)
      }
      return data
    },
  })

  const save = useMutation({
    mutationFn: () =>
      api.setGithubRepos(
        credential.id,
        repos.filter(r => selected.has(r.fullName)),
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['githubRepos', credential.id] })
      onClose()
    },
  })

  const toggle = (full: string) =>
    setSelected(p => {
      const n = new Set(p)
      n.has(full) ? n.delete(full) : n.add(full)
      return n
    })
  const shown = repos.filter(
    r => !search.trim() || r.fullName.toLowerCase().includes(search.toLowerCase()),
  )

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-xl mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div>
            <h2 className="font-semibold text-slate-900">Manage repositories</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              {credential.displayName} · {selected.size} selected
            </p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && (
            <ErrorMessage
              message="Failed to list repositories — check the PAT and its scopes."
              onRetry={() => void refetch()}
            />
          )}
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Filter repositories…"
            className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {isLoading && (
            <div className="py-8 text-center text-sm text-slate-400 flex items-center justify-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Discovering repositories via the PAT…
            </div>
          )}
          {!isLoading && !error && shown.length === 0 && (
            <div className="py-8 text-center text-sm text-slate-500">No repositories.</div>
          )}
          {shown.length > 0 && (
            <div className="border border-slate-200 rounded-lg max-h-80 overflow-y-auto divide-y divide-slate-50">
              {shown.map(r => (
                <label
                  key={r.fullName}
                  className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-slate-50"
                >
                  <input
                    type="checkbox"
                    checked={selected.has(r.fullName)}
                    onChange={() => toggle(r.fullName)}
                    className="rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0"
                  />
                  <span className="text-sm text-slate-800 flex-1 truncate">{r.fullName}</span>
                  {r.isPrivate && (
                    <span className="text-[10px] uppercase text-slate-400 shrink-0">private</span>
                  )}
                  {r.defaultBranch && (
                    <span className="text-[10px] text-slate-400 shrink-0">{r.defaultBranch}</span>
                  )}
                </label>
              ))}
            </div>
          )}
        </div>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-between items-center">
          <span className="text-xs text-slate-400">
            {repos.length} accessible · {selected.size} managed
          </span>
          <div className="flex gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              onClick={() => save.mutate()}
              disabled={save.isPending || isLoading}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
            >
              {save.isPending && <Loader2 size={14} className="animate-spin" />}Save selection
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

const inputCls =
  'w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-700 mb-1">{label}</label>
      {children}
    </div>
  )
}
