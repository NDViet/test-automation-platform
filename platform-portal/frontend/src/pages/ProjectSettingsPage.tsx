import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import Badge from '@/components/Badge'
import {
  Save, Trash2, Plus, X, ChevronLeft, AlertTriangle,
  CheckCircle, Pencil, RefreshCw,
} from 'lucide-react'
import type { SaveIntegrationConfigForm, IntegrationConfig, RepoType } from '@/lib/types'

type Tab = 'general' | 'integrations'

const INTEGRATION_TYPES  = ['JIRA_CLOUD', 'JIRA_SERVER', 'LINEAR', 'GITHUB']
const SYNC_DIRECTIONS    = ['INBOUND', 'OUTBOUND', 'BIDIRECTIONAL']
// Integration types that support on-demand pull sync
const POLL_SUPPORTED     = new Set(['GITHUB', 'JIRA_CLOUD', 'JIRA_SERVER'])

interface ParamField {
  key: string
  desc: string
  required: boolean
}

const INTEGRATION_PARAMS: Record<string, ParamField[]> = {
  JIRA_CLOUD: [
    { key: 'baseUrl',              required: true,  desc: 'Cloud instance URL — https://yourorg.atlassian.net' },
    { key: 'email',                required: true,  desc: 'Atlassian account email that owns the API token' },
    { key: 'apiToken',             required: true,  desc: 'API token from id.atlassian.com → Security → API tokens' },
    { key: 'projectKey',           required: true,  desc: 'Jira project key prefix, e.g. PROJ or ENG' },
    { key: 'doneTransitionName',   required: false, desc: 'Workflow transition name for "done" (default: Done)' },
    { key: 'reopenTransitionName', required: false, desc: 'Workflow transition name for "reopen" (default: Reopen)' },
  ],
  JIRA_SERVER: [
    { key: 'baseUrl',              required: true,  desc: 'Server base URL — https://jira.company.com' },
    { key: 'username',             required: true,  desc: 'Jira username for Basic auth' },
    { key: 'apiToken',             required: true,  desc: 'API token or password' },
    { key: 'projectKey',           required: true,  desc: 'Jira project key prefix, e.g. PROJ' },
    { key: 'doneTransitionName',   required: false, desc: 'Workflow transition name for "done" (default: Done)' },
    { key: 'reopenTransitionName', required: false, desc: 'Workflow transition name for "reopen" (default: Reopen)' },
  ],
  LINEAR: [
    { key: 'teamId',        required: true,  desc: 'Linear team ID — from Settings → API or the team URL' },
    { key: 'apiKey',        required: true,  desc: 'Personal API key from Linear → Settings → API' },
    { key: 'webhookSecret', required: false, desc: 'Signing secret from Linear → Settings → API → Webhooks' },
  ],
  GITHUB: [
    { key: 'repoFullName',    required: true,  desc: 'Repository in owner/repo format, e.g. acme/backend' },
    { key: 'token',           required: false, desc: 'Personal access token — required for private repos' },
    { key: 'integrationMode', required: false, desc: 'WEBHOOK (event-driven) or POLLING (scheduled, default)' },
    { key: 'branch',          required: false, desc: 'Branch to watch for PRs (default: main)' },
  ],
}

function seedParams(type: string): KvPair[] {
  const fields = INTEGRATION_PARAMS[type]
  if (!fields?.length) return [{ key: '', value: '', masked: false }]
  return fields.map(f => ({ key: f.key, value: '', masked: false }))
}

// Keys whose values the API always masks with "***"
const SENSITIVE_PATTERNS = ['token', 'apikey', 'secret', 'password']
function isSensitive(key: string) {
  const lower = key.toLowerCase()
  return SENSITIVE_PATTERNS.some(p => lower.includes(p))
}

interface KvPair {
  key: string
  value: string
  /** true = originally "***" and user hasn't clicked Change yet */
  masked: boolean
}

function cfgToKvPairs(params: Record<string, string>): KvPair[] {
  const pairs = Object.entries(params).map(([key, value]) => ({
    key,
    value,
    masked: value === '***',
  }))
  return pairs.length ? pairs : [{ key: '', value: '', masked: false }]
}

function kvToMap(pairs: KvPair[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const { key, value, masked } of pairs) {
    if (!key.trim()) continue
    // Keep "***" if user didn't click Change — backend will skip this value
    result[key.trim()] = masked ? '***' : value
  }
  return result
}

// ── Inline integration form (add or edit) ─────────────────────────────────────

interface IntegrationFormProps {
  initial?: IntegrationConfig
  projectId: string
  onDone: () => void
}

function IntegrationForm({ initial, projectId, onDone }: IntegrationFormProps) {
  const qc = useQueryClient()
  const isEdit = !!initial

  const [formType,        setFormType]        = useState(initial?.integrationType ?? INTEGRATION_TYPES[0])
  const [formDisplayName, setFormDisplayName] = useState(initial?.displayName ?? '')
  const [formSyncDir,     setFormSyncDir]     = useState(initial?.syncDirection ?? 'INBOUND')
  const [formRepoType,    setFormRepoType]    = useState<RepoType>(initial?.repoType ?? 'GENERAL')
  const [formParams,      setFormParams]      = useState<KvPair[]>(
    initial?.connectionParams
      ? cfgToKvPairs(initial.connectionParams)
      : seedParams(initial?.integrationType ?? INTEGRATION_TYPES[0])
  )
  const [formEnabled, setFormEnabled] = useState(initial?.enabled ?? true)

  // Re-seed params when type changes in Add mode
  useEffect(() => {
    if (!isEdit) setFormParams(seedParams(formType))
  }, [formType, isEdit])

  const mutation = useMutation({
    mutationFn: () => {
      const body: SaveIntegrationConfigForm = {
        id:              initial?.id,
        integrationType: formType,
        displayName:     formDisplayName || undefined,
        syncDirection:   formSyncDir,
        repoType:        formRepoType,
        connectionParams: kvToMap(formParams),
        enabled:         formEnabled,
      }
      return api.saveIntegration(projectId, body)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['integrations', projectId] })
      onDone()
    },
  })

  function updateParam(idx: number, field: 'key' | 'value', val: string) {
    setFormParams(prev => prev.map((p, i) => i === idx ? { ...p, [field]: val } : p))
  }

  function unmaskParam(idx: number) {
    setFormParams(prev => prev.map((p, i) => i === idx ? { ...p, value: '', masked: false } : p))
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
      <h3 className="text-sm font-semibold text-slate-900">
        {isEdit ? `Edit ${initial.integrationType}` : 'Add Integration'}
      </h3>

      {/* Type — read-only when editing to preserve upsert key */}
      <div>
        <label className="block text-xs font-medium text-slate-700 mb-1">Integration Type</label>
        {isEdit ? (
          <p className="text-sm font-mono text-slate-800">{formType}</p>
        ) : (
          <select
            value={formType}
            onChange={e => setFormType(e.target.value)}
            className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {INTEGRATION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        )}
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 mb-1">Display Name</label>
        <input
          type="text"
          value={formDisplayName}
          onChange={e => setFormDisplayName(e.target.value)}
          placeholder="e.g. Acme Jira"
          className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-700 mb-1">Sync Direction</label>
        <select
          value={formSyncDir}
          onChange={e => setFormSyncDir(e.target.value)}
          className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {SYNC_DIRECTIONS.map(d => <option key={d} value={d}>{d}</option>)}
        </select>
      </div>

      {/* Repo Role — only meaningful for GitHub */}
      {(formType === 'GITHUB') && (
        <div>
          <label className="block text-xs font-medium text-slate-700 mb-1">Repo Role</label>
          <div className="flex gap-2">
            {([
              { value: 'GENERAL',          label: 'General' },
              { value: 'CODEBASE',         label: 'Codebase' },
              { value: 'TEST_AUTOMATION',  label: 'Test Automation' },
            ] as { value: RepoType; label: string }[]).map(opt => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setFormRepoType(opt.value)}
                className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${
                  formRepoType === opt.value
                    ? 'bg-blue-600 text-white border-blue-600'
                    : 'bg-white text-slate-700 border-slate-200 hover:border-blue-300'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            {formRepoType === 'CODEBASE'        && 'Source code read for test generation context.'}
            {formRepoType === 'TEST_AUTOMATION'  && 'Automation code target — PRs are raised here.'}
            {formRepoType === 'GENERAL'          && 'No specific role assigned.'}
          </p>
        </div>
      )}

      <div>
        <label className="block text-xs font-medium text-slate-700 mb-1">Connection Params</label>
        <div className="space-y-2">
          {formParams.map((pair, idx) => {
            const knownField = INTEGRATION_PARAMS[formType]?.find(f => f.key === pair.key)
            return (
              <div key={idx} className="flex items-start gap-2">
                {/* Key column */}
                {knownField ? (
                  <div className="w-44 shrink-0 pt-1.5">
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-mono font-medium text-slate-800">{pair.key}</span>
                      <span className={`text-[10px] font-medium px-1 py-0.5 rounded leading-none ${
                        knownField.required
                          ? 'text-red-600 bg-red-50 border border-red-200'
                          : 'text-slate-400 bg-slate-100 border border-slate-200'
                      }`}>
                        {knownField.required ? 'required' : 'optional'}
                      </span>
                    </div>
                    <p className="text-[10px] text-slate-400 leading-tight mt-0.5">{knownField.desc}</p>
                  </div>
                ) : (
                  <input
                    type="text"
                    placeholder="key"
                    value={pair.key}
                    onChange={e => updateParam(idx, 'key', e.target.value)}
                    className="w-36 shrink-0 text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
                  />
                )}

                {/* Value column */}
                {pair.masked ? (
                  <>
                    <span className="flex-1 text-sm text-slate-400 font-mono border border-slate-200 rounded-lg px-3 py-1.5 bg-slate-50 select-none">
                      ••••••••
                    </span>
                    <button
                      type="button"
                      onClick={() => unmaskParam(idx)}
                      className="shrink-0 text-xs text-blue-600 hover:text-blue-700 whitespace-nowrap border border-blue-200 rounded px-2 py-1.5"
                    >
                      Change
                    </button>
                  </>
                ) : (
                  <input
                    type={isSensitive(pair.key) ? 'password' : 'text'}
                    placeholder="value"
                    value={pair.value}
                    onChange={e => updateParam(idx, 'value', e.target.value)}
                    className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                )}

                <button
                  type="button"
                  onClick={() => setFormParams(prev => prev.filter((_, i) => i !== idx))}
                  className="shrink-0 text-slate-400 hover:text-red-600 transition-colors pt-2"
                >
                  <X size={15} />
                </button>
              </div>
            )
          })}
        </div>
        <button
          type="button"
          onClick={() => setFormParams(prev => [...prev, { key: '', value: '', masked: false }])}
          className="mt-2 flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700"
        >
          <Plus size={13} /> Add param
        </button>
      </div>

      <div className="flex items-center gap-2">
        <input
          id="form-enabled"
          type="checkbox"
          checked={formEnabled}
          onChange={e => setFormEnabled(e.target.checked)}
          className="rounded border-slate-300"
        />
        <label htmlFor="form-enabled" className="text-sm text-slate-700">Enabled</label>
      </div>

      {mutation.isError && (
        <p className="text-xs text-red-600">Failed to save — please try again.</p>
      )}

      <div className="flex items-center gap-3 pt-1">
        <button
          onClick={() => void mutation.mutate()}
          disabled={mutation.isPending}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
        >
          <Save size={14} />
          {mutation.isPending ? 'Saving…' : 'Save Integration'}
        </button>
        <button
          onClick={onDone}
          className="px-4 py-2 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
        >
          Cancel
        </button>
      </div>
    </div>
  )
}

// ── Sync result display ────────────────────────────────────────────────────────

interface SyncResult {
  success: boolean
  triggered?: number
  message: string
}

// ── Main page ──────────────────────────────────────────────────────────────────

export default function ProjectSettingsPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [tab, setTab] = useState<Tab>('general')

  // ── General tab ──────────────────────────────────────────────────────────────
  const { data: detail, isLoading, error } = useQuery({
    queryKey: ['project', projectId],
    queryFn:  () => api.projectDetail(projectId!),
    enabled:  !!projectId,
  })
  const project = detail?.project

  const [editName,    setEditName]    = useState('')
  const [editRepoUrl, setEditRepoUrl] = useState('')
  const [saveSuccess, setSaveSuccess] = useState(false)

  useEffect(() => {
    if (project) {
      setEditName(project.name)
      setEditRepoUrl((project as unknown as { repoUrl?: string }).repoUrl ?? '')
    }
  }, [project])

  const saveMutation = useMutation({
    mutationFn: () => api.updateProject(projectId!, {
      name:    editName   || undefined,
      repoUrl: editRepoUrl || undefined,
    }),
    onSuccess: () => {
      setSaveSuccess(true)
      setTimeout(() => setSaveSuccess(false), 3000)
      void qc.invalidateQueries({ queryKey: ['project', projectId] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => api.deleteProject(projectId!),
    onSuccess:  () => {
      void qc.invalidateQueries({ queryKey: ['projects'] })
      navigate('/')
    },
  })

  function handleDelete() {
    if (confirm(`Delete project "${project?.name}"? This cannot be undone.`)) {
      void deleteMutation.mutate()
    }
  }

  // ── Integrations tab ─────────────────────────────────────────────────────────
  const { data: integrations, isLoading: intLoading } = useQuery({
    queryKey: ['integrations', projectId],
    queryFn:  () => api.integrations(projectId!),
    enabled:  !!projectId && tab === 'integrations',
  })

  // null = no form open; string id = editing that config; 'new' = adding new
  const [activeForm,   setActiveForm]   = useState<string | null>(null)
  const [syncResults,  setSyncResults]  = useState<Record<string, SyncResult> | null>(null)
  const [syncError,    setSyncError]    = useState<string | null>(null)

  const syncMutation = useMutation({
    mutationFn: () => api.syncIntegrations(projectId!),
    onSuccess: (data) => {
      const results = data.results as Record<string, SyncResult> | undefined
      setSyncResults(results ?? null)
      setSyncError(null)
      void qc.invalidateQueries({ queryKey: ['integrations', projectId] })
    },
    onError: (e) => setSyncError((e as Error).message),
  })

  const deleteIntMutation = useMutation({
    mutationFn: (configId: string) => api.deleteIntegration(projectId!, configId),
    onSuccess:  () => void qc.invalidateQueries({ queryKey: ['integrations', projectId] }),
  })

  function handleDeleteInt(cfg: IntegrationConfig) {
    if (confirm(`Remove ${cfg.integrationType} integration? This cannot be undone.`)) {
      void deleteIntMutation.mutate(cfg.id)
    }
  }

  if (isLoading) return <LoadingSpinner message="Loading project…" />
  if (error || !project) return <ErrorMessage message="Failed to load project." />

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Breadcrumb */}
      <div>
        <Link
          to={`/projects/${projectId}`}
          className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-blue-600 mb-2"
        >
          <ChevronLeft size={14} />
          Back to {project.name}
        </Link>
        <h1 className="text-2xl font-bold text-slate-900">Project Settings</h1>
        <p className="text-sm text-slate-500 mt-1">{project.slug}</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200">
        {(['general', 'integrations'] as Tab[]).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 -mb-px transition-colors ${
              tab === t
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {/* ── General ── */}
      {tab === 'general' && (
        <div className="space-y-6">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-100">
            <div className="px-5 py-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Name</label>
                <input
                  type="text"
                  value={editName}
                  onChange={e => setEditName(e.target.value)}
                  className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Repository URL</label>
                <input
                  type="text"
                  value={editRepoUrl}
                  onChange={e => setEditRepoUrl(e.target.value)}
                  placeholder="https://github.com/org/repo"
                  className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>
            <div className="px-5 py-4 flex items-center gap-3">
              <button
                onClick={() => void saveMutation.mutate()}
                disabled={saveMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                <Save size={14} />
                {saveMutation.isPending ? 'Saving…' : 'Save Changes'}
              </button>
              {saveSuccess && (
                <span className="text-xs text-green-600 flex items-center gap-1">
                  <CheckCircle size={13} /> Saved
                </span>
              )}
              {saveMutation.isError && (
                <span className="text-xs text-red-600">Failed to save — please try again.</span>
              )}
            </div>
          </div>

          {/* Danger zone */}
          <div className="bg-white rounded-xl border border-red-200 shadow-sm">
            <div className="px-5 py-4 border-b border-red-100">
              <p className="text-sm font-semibold text-red-700 flex items-center gap-2">
                <AlertTriangle size={15} /> Danger Zone
              </p>
            </div>
            <div className="px-5 py-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-900">Delete this project</p>
                <p className="text-xs text-slate-500 mt-0.5">
                  Permanently removes all executions and data. This cannot be undone.
                </p>
              </div>
              <button
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors"
              >
                <Trash2 size={14} />
                {deleteMutation.isPending ? 'Deleting…' : 'Delete Project'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Integrations ── */}
      {tab === 'integrations' && (
        <div className="space-y-4">
          {/* Sync now toolbar */}
          {(() => {
            const hasPollSupport = (integrations ?? []).some(c => POLL_SUPPORTED.has(c.integrationType))
            return (
              <div className="flex items-center justify-between">
                <p className="text-xs text-slate-500">
                  Configure which external systems feed requirements into this project.
                </p>
                {(intLoading || hasPollSupport) && (
                  <button
                    onClick={() => {
                      setSyncResults(null)
                      setSyncError(null)
                      void syncMutation.mutate()
                    }}
                    disabled={syncMutation.isPending || intLoading}
                    className="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50 transition-colors"
                  >
                    <RefreshCw size={13} className={syncMutation.isPending ? 'animate-spin' : ''} />
                    {syncMutation.isPending ? 'Syncing…' : 'Sync Now'}
                  </button>
                )}
              </div>
            )
          })()}

          {/* Sync results */}
          {syncError && (
            <p className="text-xs text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2">
              Sync failed: {syncError}
            </p>
          )}
          {syncResults && (
            <div className="bg-slate-50 border border-slate-200 rounded-lg px-4 py-3 space-y-1">
              {Object.entries(syncResults).map(([type, res]) => (
                <div key={type} className="flex items-start gap-2 text-xs">
                  {res.success
                    ? <CheckCircle size={13} className="text-green-600 mt-0.5 shrink-0" />
                    : <AlertTriangle size={13} className="text-amber-500 mt-0.5 shrink-0" />}
                  <span>
                    <span className="font-mono font-medium">{type}</span>
                    {' — '}{res.message}
                  </span>
                </div>
              ))}
            </div>
          )}

          {intLoading ? (
            <LoadingSpinner message="Loading integrations…" />
          ) : (
            <>
              {(integrations ?? []).length === 0 && activeForm !== 'new' && (
                <p className="text-sm text-slate-500 text-center py-6">
                  No integrations configured yet.
                </p>
              )}

              {/* Config rows */}
              {(integrations ?? []).map(cfg => (
                <div key={cfg.id}>
                  {/* Row */}
                  {activeForm !== cfg.id && (
                    <div className="bg-white rounded-xl border border-slate-200 shadow-sm px-5 py-4 flex items-start justify-between gap-4">
                      <div className="space-y-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <Badge label={cfg.integrationType} colorClass="text-blue-700 bg-blue-100" />
                          {cfg.displayName && (
                            <span className="text-sm text-slate-700">{cfg.displayName}</span>
                          )}
                          {cfg.integrationType === 'GITHUB' && cfg.repoType && cfg.repoType !== 'GENERAL' && (
                            <Badge
                              label={cfg.repoType === 'TEST_AUTOMATION' ? 'Test Automation' : 'Codebase'}
                              colorClass={cfg.repoType === 'TEST_AUTOMATION'
                                ? 'text-purple-700 bg-purple-100'
                                : 'text-amber-700 bg-amber-100'}
                            />
                          )}
                          <Badge
                            label={cfg.enabled ? 'enabled' : 'disabled'}
                            colorClass={cfg.enabled
                              ? 'text-green-700 bg-green-100'
                              : 'text-slate-500 bg-slate-100'}
                          />
                        </div>
                        <p className="text-xs text-slate-500">
                          Direction: {cfg.syncDirection}
                          {cfg.lastSyncedAt && ` · Last synced: ${relativeTime(cfg.lastSyncedAt)}`}
                          {cfg.consecutiveErrors > 0 && (
                            <span className="text-red-600">
                              {' '}· {cfg.consecutiveErrors} error{cfg.consecutiveErrors !== 1 ? 's' : ''}
                            </span>
                          )}
                        </p>
                        {!POLL_SUPPORTED.has(cfg.integrationType) && (
                          <p className="text-xs text-slate-400 italic mt-0.5">
                            Webhook-only — trigger from Linear → Settings → API → Webhooks to sync immediately.
                          </p>
                        )}
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <button
                          title="Edit"
                          onClick={() => setActiveForm(cfg.id)}
                          className="text-slate-400 hover:text-blue-600 transition-colors"
                        >
                          <Pencil size={15} />
                        </button>
                        <button
                          title="Delete"
                          onClick={() => handleDeleteInt(cfg)}
                          className="text-slate-400 hover:text-red-600 transition-colors"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Inline edit form */}
                  {activeForm === cfg.id && (
                    <IntegrationForm
                      initial={cfg}
                      projectId={projectId!}
                      onDone={() => setActiveForm(null)}
                    />
                  )}
                </div>
              ))}

              {/* Add form or button */}
              {activeForm === 'new' ? (
                <IntegrationForm
                  projectId={projectId!}
                  onDone={() => setActiveForm(null)}
                />
              ) : (
                <button
                  onClick={() => setActiveForm('new')}
                  className="flex items-center gap-2 px-4 py-2 text-sm font-medium border border-dashed border-slate-300 rounded-lg text-slate-600 hover:border-blue-400 hover:text-blue-600 transition-colors w-full justify-center"
                >
                  <Plus size={15} /> Add Integration
                </button>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}
