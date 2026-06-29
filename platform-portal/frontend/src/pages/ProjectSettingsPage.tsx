import { useState, useEffect } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import Badge from '@/components/Badge'
import {
  Save,
  Trash2,
  Plus,
  X,
  AlertTriangle,
  CheckCircle,
  Pencil,
  RefreshCw,
  Building2,
  Lock,
} from 'lucide-react'
import CreateTeamModal from '@/components/CreateTeamModal'
import MappingRulesEditor from '@/components/MappingRulesEditor'
import AiSkillsManager from '@/components/AiSkillsManager'
import AiPromptTemplatesManager from '@/components/AiPromptTemplatesManager'
import type {
  SaveIntegrationConfigForm,
  IntegrationConfig,
  RepoType,
  InheritedCredential,
} from '@/lib/types'

type Tab = 'general' | 'teams' | 'integrations' | 'mapping' | 'ai' | 'github'

const TAB_LABELS: Record<Tab, string> = {
  general: 'General',
  teams: 'Teams',
  integrations: 'Integrations',
  mapping: 'Mapping',
  ai: 'AI',
  github: 'GitHub',
}

const INTEGRATION_TYPES = [
  'JIRA_CLOUD',
  'JIRA_SERVER',
  'AZURE_DEVOPS_BOARDS',
  'GITHUB_ISSUES',
  'LINEAR',
  'GITHUB',
]
const SYNC_DIRECTIONS = ['INBOUND', 'OUTBOUND', 'BIDIRECTIONAL']
// Integration types that support on-demand pull sync
const POLL_SUPPORTED = new Set([
  'GITHUB',
  'GITHUB_ISSUES',
  'AZURE_DEVOPS_BOARDS',
  'JIRA_CLOUD',
  'JIRA_SERVER',
])

interface ParamField {
  key: string
  desc: string
  required: boolean
}

const INTEGRATION_PARAMS: Record<string, ParamField[]> = {
  JIRA_CLOUD: [
    { key: 'baseUrl', required: true, desc: 'Cloud instance URL — https://yourorg.atlassian.net' },
    { key: 'email', required: true, desc: 'Atlassian account email that owns the API token' },
    {
      key: 'apiToken',
      required: true,
      desc: 'API token from id.atlassian.com → Security → API tokens',
    },
    { key: 'projectKey', required: true, desc: 'Jira project key prefix, e.g. PROJ or ENG' },
    {
      key: 'doneTransitionName',
      required: false,
      desc: 'Workflow transition name for "done" (default: Done)',
    },
    {
      key: 'reopenTransitionName',
      required: false,
      desc: 'Workflow transition name for "reopen" (default: Reopen)',
    },
  ],
  JIRA_SERVER: [
    { key: 'baseUrl', required: true, desc: 'Server base URL — https://jira.company.com' },
    { key: 'username', required: true, desc: 'Jira username for Basic auth' },
    { key: 'apiToken', required: true, desc: 'API token or password' },
    { key: 'projectKey', required: true, desc: 'Jira project key prefix, e.g. PROJ' },
    {
      key: 'doneTransitionName',
      required: false,
      desc: 'Workflow transition name for "done" (default: Done)',
    },
    {
      key: 'reopenTransitionName',
      required: false,
      desc: 'Workflow transition name for "reopen" (default: Reopen)',
    },
  ],
  LINEAR: [
    { key: 'teamId', required: true, desc: 'Linear team ID — from Settings → API or the team URL' },
    { key: 'apiKey', required: true, desc: 'Personal API key from Linear → Settings → API' },
    {
      key: 'webhookSecret',
      required: false,
      desc: 'Signing secret from Linear → Settings → API → Webhooks',
    },
  ],
  GITHUB: [
    {
      key: 'repoFullName',
      required: true,
      desc: 'Repository in owner/repo format, e.g. acme/backend',
    },
    {
      key: 'token',
      required: false,
      desc: 'Personal access token — required for private repos (or inherit Org credential)',
    },
    {
      key: 'integrationMode',
      required: false,
      desc: 'WEBHOOK (event-driven) or POLLING (scheduled, default)',
    },
    { key: 'branch', required: false, desc: 'Branch to watch for PRs (default: main)' },
  ],
  AZURE_DEVOPS_BOARDS: [
    {
      key: 'organization',
      required: true,
      desc: 'Azure DevOps organization, e.g. acme (from dev.azure.com/acme)',
    },
    { key: 'project', required: true, desc: 'Azure DevOps project that holds the work items' },
    { key: 'area_path', required: false, desc: 'Area path filter, e.g. Checkout\\Payments' },
    { key: 'pat', required: false, desc: 'PAT — leave blank to inherit the Org/Team credential' },
    {
      key: 'doneState',
      required: false,
      desc: 'Work-item state treated as closed (default: Closed)',
    },
    {
      key: 'reopenState',
      required: false,
      desc: 'Work-item state used to reopen (default: Active)',
    },
  ],
  GITHUB_ISSUES: [
    { key: 'owner', required: true, desc: 'Repository owner (org or user), e.g. acme' },
    { key: 'repo', required: true, desc: 'Repository name, e.g. checkout' },
    { key: 'pat', required: false, desc: 'PAT — leave blank to inherit the Org/Team credential' },
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
  inherited?: InheritedCredential[]
  onDone: () => void
}

function IntegrationForm({ initial, projectId, inherited, onDone }: IntegrationFormProps) {
  const qc = useQueryClient()
  const isEdit = !!initial

  const [formType, setFormType] = useState(initial?.integrationType ?? INTEGRATION_TYPES[0])
  const [formDisplayName, setFormDisplayName] = useState(initial?.displayName ?? '')
  const [formSyncDir, setFormSyncDir] = useState(initial?.syncDirection ?? 'INBOUND')
  const [formRepoType, setFormRepoType] = useState<RepoType>(initial?.repoType ?? 'GENERAL')
  const [formParams, setFormParams] = useState<KvPair[]>(
    initial?.connectionParams
      ? cfgToKvPairs(initial.connectionParams)
      : seedParams(initial?.integrationType ?? INTEGRATION_TYPES[0]),
  )
  const [formEnabled, setFormEnabled] = useState(initial?.enabled ?? true)

  // Re-seed params when type changes in Add mode
  useEffect(() => {
    if (!isEdit) setFormParams(seedParams(formType))
  }, [formType, isEdit])

  const mutation = useMutation({
    mutationFn: () => {
      const body: SaveIntegrationConfigForm = {
        id: initial?.id,
        integrationType: formType,
        displayName: formDisplayName || undefined,
        syncDirection: formSyncDir,
        repoType: formRepoType,
        connectionParams: kvToMap(formParams),
        enabled: formEnabled,
      }
      return api.saveIntegration(projectId, body)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['integrations', projectId] })
      onDone()
    },
  })

  function updateParam(idx: number, field: 'key' | 'value', val: string) {
    setFormParams(prev => prev.map((p, i) => (i === idx ? { ...p, [field]: val } : p)))
  }

  function unmaskParam(idx: number) {
    setFormParams(prev => prev.map((p, i) => (i === idx ? { ...p, value: '', masked: false } : p)))
  }

  const inheritedMatch = (inherited ?? []).find(c => c.integrationType === formType)

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
      <h3 className="text-sm font-semibold text-slate-900">
        {isEdit ? `Edit ${initial.integrationType}` : 'Add Integration'}
      </h3>

      {/* Inheritance notice — what this integration gets from the organization */}
      {inheritedMatch && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg px-4 py-3 text-sm text-blue-900 space-y-1">
          <p className="flex items-center gap-1.5 font-medium">
            <Building2 size={14} /> Inherits from organization credential
            {inheritedMatch.displayName ? ` “${inheritedMatch.displayName}”` : ''}
          </p>
          <p className="text-xs text-blue-700">
            {inheritedMatch.hasSecret
              ? 'A PAT/token is already provided by the organization — leave secret fields blank to use it.'
              : 'The organization credential has no secret set; you may need to provide one here.'}
            {inheritedMatch.baseUrl ? ` Base URL: ${inheritedMatch.baseUrl}.` : ''}
          </p>
          {Object.keys(inheritedMatch.connectionParams ?? {}).length > 0 && (
            <p className="text-xs text-blue-700 font-mono">
              Inherited:{' '}
              {Object.entries(inheritedMatch.connectionParams)
                .map(([k, v]) => `${k}=${v}`)
                .join(' · ')}{' '}
              — leave blank to inherit.
            </p>
          )}
        </div>
      )}

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
            {INTEGRATION_TYPES.map(t => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
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
          {SYNC_DIRECTIONS.map(d => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
      </div>

      {/* Repo Role — only meaningful for GitHub */}
      {formType === 'GITHUB' && (
        <div>
          <label className="block text-xs font-medium text-slate-700 mb-1">Repo Role</label>
          <div className="flex gap-2">
            {(
              [
                { value: 'GENERAL', label: 'General' },
                { value: 'CODEBASE', label: 'Codebase' },
                { value: 'TEST_AUTOMATION', label: 'Test Automation' },
              ] as { value: RepoType; label: string }[]
            ).map(opt => (
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
            {formRepoType === 'CODEBASE' && 'Source code read for test generation context.'}
            {formRepoType === 'TEST_AUTOMATION' && 'Automation code target — PRs are raised here.'}
            {formRepoType === 'GENERAL' && 'No specific role assigned.'}
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
                      <span className="text-xs font-mono font-medium text-slate-800">
                        {pair.key}
                      </span>
                      <span
                        className={`text-[10px] font-medium px-1 py-0.5 rounded leading-none ${
                          knownField.required
                            ? 'text-red-600 bg-red-50 border border-red-200'
                            : 'text-slate-400 bg-slate-100 border border-slate-200'
                        }`}
                      >
                        {knownField.required ? 'required' : 'optional'}
                      </span>
                    </div>
                    <p className="text-[10px] text-slate-400 leading-tight mt-0.5">
                      {knownField.desc}
                    </p>
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
        <label htmlFor="form-enabled" className="text-sm text-slate-700">
          Enabled
        </label>
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
  const { projectId } = useProject()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { section } = useParams<{ section: string }>()
  const tab: Tab = (Object.keys(TAB_LABELS) as Tab[]).includes(section as Tab)
    ? (section as Tab)
    : 'general'

  // ── General tab ──────────────────────────────────────────────────────────────
  const {
    data: detail,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => api.projectDetail(projectId!),
    enabled: !!projectId,
  })
  const project = detail?.project

  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [saveSuccess, setSaveSuccess] = useState(false)

  useEffect(() => {
    if (project) {
      setEditName(project.name)
      setEditDescription((project as unknown as { description?: string }).description ?? '')
    }
  }, [project])

  const saveMutation = useMutation({
    mutationFn: () =>
      api.updateProject(projectId!, {
        name: editName || undefined,
        description: editDescription || undefined,
      }),
    onSuccess: () => {
      setSaveSuccess(true)
      setTimeout(() => setSaveSuccess(false), 3000)
      void qc.invalidateQueries({ queryKey: ['project', projectId] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => api.deleteProject(projectId!),
    onSuccess: () => {
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
    queryFn: () => api.integrations(projectId!),
    enabled: !!projectId && tab === 'integrations',
  })

  const { data: inherited } = useQuery({
    queryKey: ['inherited-integrations', projectId],
    queryFn: () => api.inheritedIntegrations(projectId!),
    enabled: !!projectId && tab === 'integrations',
  })

  // null = no form open; string id = editing that config; 'new' = adding new
  const [activeForm, setActiveForm] = useState<string | null>(null)
  const [syncResults, setSyncResults] = useState<Record<string, SyncResult> | null>(null)
  const [syncError, setSyncError] = useState<string | null>(null)

  const syncMutation = useMutation({
    mutationFn: () => api.syncIntegrations(projectId!),
    onSuccess: data => {
      const results = data.results as Record<string, SyncResult> | undefined
      setSyncResults(results ?? null)
      setSyncError(null)
      void qc.invalidateQueries({ queryKey: ['integrations', projectId] })
    },
    onError: e => setSyncError((e as Error).message),
  })

  const deleteIntMutation = useMutation({
    mutationFn: (configId: string) => api.deleteIntegration(projectId!, configId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['integrations', projectId] }),
  })

  function handleDeleteInt(cfg: IntegrationConfig) {
    if (confirm(`Remove ${cfg.integrationType} integration? This cannot be undone.`)) {
      void deleteIntMutation.mutate(cfg.id)
    }
  }

  if (isLoading) return <LoadingSpinner message="Loading project…" />
  if (error || !project)
    return <ErrorMessage message="Failed to load project." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">{TAB_LABELS[tab]}</h1>
        <p className="text-sm text-slate-500 mt-1">
          {project.name} · {project.slug}
        </p>
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
                <label className="block text-sm font-medium text-slate-700 mb-1">Description</label>
                <textarea
                  value={editDescription}
                  onChange={e => setEditDescription(e.target.value)}
                  placeholder="Brief description of this project…"
                  rows={3}
                  className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
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

      {/* ── Teams ── */}
      {tab === 'teams' && <ProjectTeams projectId={projectId!} />}

      {/* ── Integrations ── */}
      {tab === 'integrations' && (
        <div className="space-y-4">
          {/* Inherited from organization — read-only summary */}
          {(inherited ?? []).length > 0 && (
            <div className="bg-white rounded-xl border border-blue-200 shadow-sm">
              <div className="px-5 py-3 border-b border-blue-100 flex items-center gap-2">
                <Building2 size={15} className="text-blue-500" />
                <h3 className="text-sm font-semibold text-slate-900">
                  Inherited from Organization
                </h3>
                <span className="text-xs text-slate-400">
                  used automatically unless overridden below
                </span>
              </div>
              <div className="divide-y divide-slate-50">
                {(inherited ?? []).map(c => (
                  <div
                    key={c.integrationType}
                    className="px-5 py-3 flex items-center justify-between gap-4"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <Badge label={c.integrationType} colorClass="text-blue-700 bg-blue-100" />
                        {c.displayName && (
                          <span className="text-sm text-slate-700">{c.displayName}</span>
                        )}
                        <span className="inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                          <Lock size={11} /> {c.hasSecret ? 'secret set' : 'no secret'}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 mt-0.5 truncate">
                        {c.baseUrl && <span className="font-mono">{c.baseUrl}</span>}
                        {Object.keys(c.connectionParams ?? {}).length > 0 && (
                          <span className="font-mono">
                            {c.baseUrl ? ' · ' : ''}
                            {Object.entries(c.connectionParams)
                              .map(([k, v]) => `${k}=${v}`)
                              .join(' · ')}
                          </span>
                        )}
                      </p>
                    </div>
                    <Link
                      to="/settings/integrations"
                      className="text-xs text-blue-600 hover:text-blue-700 whitespace-nowrap shrink-0"
                    >
                      Manage at org →
                    </Link>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Sync now toolbar */}
          {(() => {
            const hasPollSupport = (integrations ?? []).some(c =>
              POLL_SUPPORTED.has(c.integrationType),
            )
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
                  {res.success ? (
                    <CheckCircle size={13} className="text-green-600 mt-0.5 shrink-0" />
                  ) : (
                    <AlertTriangle size={13} className="text-amber-500 mt-0.5 shrink-0" />
                  )}
                  <span>
                    <span className="font-mono font-medium">{type}</span>
                    {' — '}
                    {res.message}
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
                          <Badge
                            label={cfg.integrationType}
                            colorClass="text-blue-700 bg-blue-100"
                          />
                          {cfg.displayName && (
                            <span className="text-sm text-slate-700">{cfg.displayName}</span>
                          )}
                          {cfg.integrationType === 'GITHUB' &&
                            cfg.repoType &&
                            cfg.repoType !== 'GENERAL' && (
                              <Badge
                                label={
                                  cfg.repoType === 'TEST_AUTOMATION'
                                    ? 'Test Automation'
                                    : 'Codebase'
                                }
                                colorClass={
                                  cfg.repoType === 'TEST_AUTOMATION'
                                    ? 'text-purple-700 bg-purple-100'
                                    : 'text-amber-700 bg-amber-100'
                                }
                              />
                            )}
                          <Badge
                            label={cfg.enabled ? 'enabled' : 'disabled'}
                            colorClass={
                              cfg.enabled
                                ? 'text-green-700 bg-green-100'
                                : 'text-slate-500 bg-slate-100'
                            }
                          />
                        </div>
                        <p className="text-xs text-slate-500">
                          Direction: {cfg.syncDirection}
                          {cfg.lastSyncedAt && ` · Last synced: ${relativeTime(cfg.lastSyncedAt)}`}
                          {cfg.consecutiveErrors > 0 && (
                            <span className="text-red-600">
                              {' '}
                              · {cfg.consecutiveErrors} error
                              {cfg.consecutiveErrors !== 1 ? 's' : ''}
                            </span>
                          )}
                        </p>
                        {!POLL_SUPPORTED.has(cfg.integrationType) && (
                          <p className="text-xs text-slate-400 italic mt-0.5">
                            Webhook-only — trigger from Linear → Settings → API → Webhooks to sync
                            immediately.
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
                      inherited={inherited}
                      onDone={() => setActiveForm(null)}
                    />
                  )}
                </div>
              ))}

              {/* Add form or button */}
              {activeForm === 'new' ? (
                <IntegrationForm
                  projectId={projectId!}
                  inherited={inherited}
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

      {/* ── Mapping ── */}
      {tab === 'mapping' && (
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            Mapping Suggester rules for this project. Overrides the organization default; resets
            fall back to it.
          </p>
          <MappingRulesEditor scope="PROJECT" id={projectId!} />
        </div>
      )}

      {/* ── AI ── */}
      {tab === 'ai' && project && (
        <div className="space-y-10">
          <ProjectAiSettings projectId={project.id} />
          <AiSkillsManager projectId={project.id} />
          <AiPromptTemplatesManager projectId={project.id} />
        </div>
      )}

      {/* ── GitHub ── */}
      {tab === 'github' && project && (
        <GitHubReposTab projectId={project.id} orgId={project.orgId} />
      )}
    </div>
  )
}

/**
 * Per-project AI overrides on top of the Org default. Shows the effective
 * (merged Org→Team→Project) value for each key and lets a project pin its own.
 */
function ProjectAiSettings({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)

  const { data: effective, isLoading } = useQuery({
    queryKey: ['scoped-ai', projectId],
    queryFn: () => api.scopedAiEffective(projectId),
  })

  const val = (key: string) => (key in draft ? draft[key] : (effective?.[key] ?? ''))
  const setVal = (key: string, v: string) => {
    setDraft(d => ({ ...d, [key]: v }))
    setSaved(false)
  }

  const saveMutation = useMutation({
    mutationFn: async () => {
      // PUT only the keys the user changed; backend stores one key/value at a time.
      for (const [key, value] of Object.entries(draft)) {
        await api.setScopedAi('PROJECT', projectId, key, value)
      }
    },
    onSuccess: () => {
      setDraft({})
      setSaved(true)
      void qc.invalidateQueries({ queryKey: ['scoped-ai', projectId] })
    },
  })

  if (isLoading) return <LoadingSpinner message="Loading AI settings…" />

  const provider = val('ai.provider') || 'anthropic'

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="bg-blue-50 border border-blue-200 rounded-lg px-4 py-3 text-sm text-blue-800">
        These override the organization defaults for this project only. Blank = inherit.
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-100">
        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">Enable AI analysis</p>
            <p className="text-xs text-slate-500">Effective: {effective?.['ai.enabled'] ?? '—'}</p>
          </div>
          <select
            className={aiInputCls}
            value={val('ai.enabled')}
            onChange={e => setVal('ai.enabled', e.target.value)}
          >
            <option value="">Inherit</option>
            <option value="true">Enabled</option>
            <option value="false">Disabled</option>
          </select>
        </div>

        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">Real-time analysis</p>
            <p className="text-xs text-slate-500">
              Effective: {effective?.['ai.realtime.enabled'] ?? '—'}
            </p>
          </div>
          <select
            className={aiInputCls}
            value={val('ai.realtime.enabled')}
            onChange={e => setVal('ai.realtime.enabled', e.target.value)}
          >
            <option value="">Inherit</option>
            <option value="true">Enabled</option>
            <option value="false">Disabled</option>
          </select>
        </div>

        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">Provider</p>
            <p className="text-xs text-slate-500">Effective: {effective?.['ai.provider'] ?? '—'}</p>
          </div>
          <select
            className={aiInputCls}
            value={val('ai.provider')}
            onChange={e => setVal('ai.provider', e.target.value)}
          >
            <option value="">Inherit</option>
            <option value="anthropic">anthropic</option>
            <option value="openai">openai</option>
          </select>
        </div>

        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">Model</p>
            <p className="text-xs text-slate-500">Effective: {effective?.['ai.model'] ?? '—'}</p>
          </div>
          <input
            className={aiInputCls}
            value={val('ai.model')}
            placeholder={provider === 'openai' ? 'gpt-4o' : 'claude-sonnet-4-6'}
            onChange={e => setVal('ai.model', e.target.value)}
          />
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={() => void saveMutation.mutate()}
          disabled={Object.keys(draft).length === 0 || saveMutation.isPending}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50"
        >
          {saveMutation.isPending ? 'Saving…' : 'Save overrides'}
        </button>
        {saved && <span className="text-sm text-green-600">Saved.</span>}
        {saveMutation.isError && <span className="text-sm text-red-600">Failed to save.</span>}
      </div>
    </div>
  )
}

const aiInputCls =
  'text-sm border border-slate-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-[10rem]'

// ── GitHub repo assignments ────────────────────────────────────────────────────

const REPO_ROLES: { value: string; label: string; activeClass: string }[] = [
  { value: 'GENERAL', label: 'General', activeClass: 'bg-slate-700 text-white border-slate-700' },
  { value: 'CODEBASE', label: 'Codebase', activeClass: 'bg-amber-600 text-white border-amber-600' },
  {
    value: 'TEST_AUTOMATION',
    label: 'Test Auto',
    activeClass: 'bg-purple-600 text-white border-purple-600',
  },
]

function GitHubReposTab({ projectId, orgId }: { projectId: string; orgId: string }) {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [assignments, setAssignments] = useState<Map<string, string>>(new Map())
  const [dirty, setDirty] = useState(false)
  const [saveOk, setSaveOk] = useState(false)

  // Sync interval local state
  const [syncInterval, setSyncInterval] = useState<number | null>(null)
  const [syncIntervalSaved, setSyncIntervalSaved] = useState(false)

  // Find org-level GitHub credential
  const { data: allCreds = [] } = useQuery({
    queryKey: ['credentials', 'ORG', orgId],
    queryFn: () => api.credentials('ORG', orgId),
  })
  const githubCred = allCreds.find(c => c.integrationType === 'GITHUB')

  // Initialize sync interval from credential
  useEffect(() => {
    if (githubCred && syncInterval === null) {
      setSyncInterval(githubCred.syncIntervalMinutes ?? 0)
    }
  }, [githubCred, syncInterval])

  // Cached repos for that credential
  const { data: cacheData, isLoading: cacheLoading } = useQuery({
    queryKey: ['github-repo-cache', githubCred?.id],
    queryFn: () => api.cachedGitHubRepos(githubCred!.id),
    enabled: !!githubCred,
  })

  // Existing project assignments
  const { data: existingAssignments = [] } = useQuery({
    queryKey: ['project-github-repos', projectId],
    queryFn: () => api.projectGitHubRepos(projectId),
  })

  // Initialize local state from loaded assignments (only when not dirty)
  useEffect(() => {
    if (!dirty) {
      const m = new Map<string, string>()
      existingAssignments.forEach(a => m.set(a.repoFullName, a.role))
      setAssignments(m)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existingAssignments])

  const syncMutation = useMutation({
    mutationFn: () => api.syncGitHubRepos(githubCred!.id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['github-repo-cache', githubCred?.id] })
    },
  })

  const syncIntervalMutation = useMutation({
    mutationFn: () => api.updateSyncInterval(githubCred!.id, syncInterval ?? 0),
    onSuccess: () => {
      setSyncIntervalSaved(true)
      void qc.invalidateQueries({ queryKey: ['credentials', 'ORG', orgId] })
      setTimeout(() => setSyncIntervalSaved(false), 3000)
    },
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const toSave = Array.from(assignments.entries()).map(([repoFullName, role]) => ({
        repoFullName,
        role,
        credentialId: githubCred!.id,
      }))
      return api.setProjectGitHubRepos(projectId, toSave)
    },
    onSuccess: () => {
      setSaveOk(true)
      setDirty(false)
      void qc.invalidateQueries({ queryKey: ['project-github-repos', projectId] })
      setTimeout(() => setSaveOk(false), 3000)
    },
  })

  function toggleRepo(fullName: string, checked: boolean) {
    setAssignments(prev => {
      const m = new Map(prev)
      if (checked) m.set(fullName, 'GENERAL')
      else m.delete(fullName)
      return m
    })
    setDirty(true)
    setSaveOk(false)
  }

  function setRole(fullName: string, role: string) {
    setAssignments(prev => new Map(prev).set(fullName, role))
    setDirty(true)
    setSaveOk(false)
  }

  const visibleRepos = (cacheData?.repos ?? []).filter(
    r => !search || r.fullName.toLowerCase().includes(search.toLowerCase()),
  )

  if (!allCreds.length && !githubCred) {
    return <LoadingSpinner message="Loading credentials…" />
  }

  if (!githubCred) {
    return (
      <div className="bg-amber-50 border border-amber-200 rounded-xl px-5 py-8 text-center space-y-2">
        <p className="text-sm font-medium text-amber-900">
          No GitHub integration configured at org level
        </p>
        <p className="text-xs text-amber-700">
          Go to{' '}
          <Link to="/settings/integrations" className="underline hover:text-amber-900">
            Admin → Integrations
          </Link>{' '}
          and add a GitHub credential with a Personal Access Token.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Sync status + interval */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-100">
        <div className="px-5 py-4 flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-slate-900">GitHub Repo Cache</p>
            <p className="text-xs text-slate-500 mt-0.5">
              {cacheData?.syncedAt
                ? `${cacheData.totalCount} repos cached · last synced ${relativeTime(cacheData.syncedAt)}`
                : 'Cache is empty — click Sync to fetch repos from GitHub.'}
            </p>
            {syncMutation.isError && (
              <p className="text-xs text-red-600 mt-1">Sync failed — check your PAT scopes.</p>
            )}
          </div>
          <button
            onClick={() => void syncMutation.mutate()}
            disabled={syncMutation.isPending}
            className="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50 transition-colors whitespace-nowrap"
          >
            <RefreshCw size={13} className={syncMutation.isPending ? 'animate-spin' : ''} />
            {syncMutation.isPending ? 'Syncing…' : 'Sync from GitHub'}
          </button>
        </div>

        {/* Auto-sync interval */}
        <div className="px-5 py-4 flex items-center gap-4">
          <div className="flex-1">
            <p className="text-sm font-medium text-slate-900">Auto-sync interval</p>
            <p className="text-xs text-slate-500 mt-0.5">
              Set to 0 to disable auto-sync. Applies to the org GitHub credential.
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <input
              type="number"
              min={0}
              max={1440}
              value={syncInterval ?? 0}
              onChange={e => setSyncInterval(Math.max(0, parseInt(e.target.value) || 0))}
              className="w-20 text-sm border border-slate-200 rounded-lg px-3 py-1.5 text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <span className="text-xs text-slate-500">min</span>
            <button
              onClick={() => void syncIntervalMutation.mutate()}
              disabled={
                syncIntervalMutation.isPending ||
                syncInterval === (githubCred?.syncIntervalMinutes ?? 0)
              }
              className="px-3 py-1.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {syncIntervalMutation.isPending ? 'Saving…' : 'Save'}
            </button>
            {syncIntervalSaved && <CheckCircle size={14} className="text-green-500 shrink-0" />}
          </div>
        </div>
      </div>

      {/* Repo picker */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100 flex items-center gap-3">
          <input
            type="search"
            placeholder="Search repos…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <span className="text-xs text-slate-400 whitespace-nowrap shrink-0">
            {assignments.size} assigned
          </span>
        </div>

        {cacheLoading ? (
          <div className="py-8">
            <LoadingSpinner message="Loading cache…" />
          </div>
        ) : visibleRepos.length === 0 ? (
          <p className="px-5 py-8 text-sm text-slate-500 text-center">
            {!cacheData?.totalCount
              ? 'No repos in cache — click Sync to fetch from GitHub.'
              : 'No repos match the search.'}
          </p>
        ) : (
          <div className="max-h-[480px] overflow-y-auto divide-y divide-slate-50">
            {visibleRepos.map(repo => {
              const assigned = assignments.has(repo.fullName)
              const role = assignments.get(repo.fullName) ?? 'GENERAL'
              return (
                <div
                  key={repo.fullName}
                  className={`px-4 py-3 flex items-center gap-3 ${assigned ? 'bg-blue-50/40' : 'hover:bg-slate-50'}`}
                >
                  <input
                    type="checkbox"
                    checked={assigned}
                    onChange={e => toggleRepo(repo.fullName, e.target.checked)}
                    className="rounded border-slate-300 accent-blue-600 shrink-0"
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-mono text-slate-900 truncate">
                        {repo.fullName}
                      </span>
                      {repo.isPrivate && (
                        <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-500 border border-slate-200 shrink-0">
                          private
                        </span>
                      )}
                      {repo.defaultBranch && (
                        <span className="text-[10px] text-slate-400 font-mono shrink-0">
                          {repo.defaultBranch}
                        </span>
                      )}
                    </div>
                  </div>
                  {assigned && (
                    <div className="flex gap-1 shrink-0">
                      {REPO_ROLES.map(r => (
                        <button
                          key={r.value}
                          type="button"
                          onClick={() => setRole(repo.fullName, r.value)}
                          className={`px-2 py-1 text-[11px] font-medium rounded-md border transition-colors ${
                            role === r.value
                              ? r.activeClass
                              : 'bg-white text-slate-500 border-slate-200 hover:border-slate-300'
                          }`}
                        >
                          {r.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Save footer */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => void saveMutation.mutate()}
          disabled={!dirty || saveMutation.isPending}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
        >
          <Save size={14} />
          {saveMutation.isPending ? 'Saving…' : 'Save Assignments'}
        </button>
        {saveOk && (
          <span className="text-sm text-green-600 flex items-center gap-1">
            <CheckCircle size={13} /> Saved
          </span>
        )}
        {saveMutation.isError && (
          <span className="text-sm text-red-600">Failed to save — please try again.</span>
        )}
        {!dirty && assignments.size > 0 && (
          <span className="text-xs text-slate-400">
            {assignments.size} repo{assignments.size !== 1 ? 's' : ''} assigned
          </span>
        )}
      </div>
    </div>
  )
}

/**
 * Sub-teams within this project (ADO-first: Org → Project → Team). Teams scope
 * RBAC assignments, API keys and credential overrides below the project.
 */
function ProjectTeams({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)

  const { data: teams, isLoading } = useQuery({
    queryKey: ['teams', projectId],
    queryFn: () => api.teams(projectId),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteTeam(projectId, id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['teams', projectId] }),
  })

  function handleDelete(id: string, name: string) {
    if (confirm(`Delete team "${name}"? This cannot be undone.`)) void deleteMutation.mutate(id)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-slate-500">
          Teams partition this project for access control and credential overrides.
        </p>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={13} /> New Team
        </button>
      </div>

      {isLoading ? (
        <LoadingSpinner message="Loading teams…" />
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-50">
          {(teams ?? []).length === 0 && (
            <p className="px-5 py-8 text-sm text-slate-500 text-center">
              No teams in this project yet.
            </p>
          )}
          {(teams ?? []).map(t => (
            <div key={t.id} className="px-5 py-3.5 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-900">{t.name}</p>
                <p className="text-xs text-slate-400 font-mono">{t.slug}</p>
              </div>
              <button
                title="Delete team"
                onClick={() => handleDelete(t.id, t.name)}
                className="text-slate-400 hover:text-red-600 transition-colors"
              >
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
      )}

      <CreateTeamModal
        open={showCreate}
        projectId={projectId}
        onClose={() => setShowCreate(false)}
        onCreated={() => void qc.invalidateQueries({ queryKey: ['teams', projectId] })}
      />
    </div>
  )
}
