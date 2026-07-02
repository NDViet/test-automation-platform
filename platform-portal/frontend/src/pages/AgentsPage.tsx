import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Pencil, Trash2, Copy, Bot, Info } from 'lucide-react'
import { api } from '@/lib/api'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, Input, Select, PageHeader, StatusBadge } from '@/components/ui'
import type { Agent, AgentForm, AgentScope } from '@/lib/types'

const MODEL_ROLES = ['', 'STANDARD', 'COMPLEX', 'SUMMARIZER']
const CONTEXT_KEYS = [
  'requirements',
  'executionHistory',
  'attachedFiles',
  'prDiff',
  'existingCoverage',
] as const

const EMPTY_FORM: AgentForm = {
  name: '',
  description: '',
  persona: '',
  systemTemplateId: null,
  userTemplateId: null,
  skillIds: [],
  modelRole: '',
  modelId: '',
  contextConfig: {},
  maxRounds: 3,
  enabled: true,
}

/**
 * Manage reusable agents at org or project scope. Project view shows the project's own agents plus
 * inherited org agents (read-only, clonable). The editor composes by reference — system/user prompt
 * templates and skills are picked from the project's existing ones.
 */
export default function AgentsPage() {
  const qc = useQueryClient()
  const actor = localStorage.getItem('platform.actor') ?? ''

  const [scopeKind, setScopeKind] = useState<AgentScope>('projects')
  const [scopeId, setScopeId] = useState<string>('')
  const [editing, setEditing] = useState<Agent | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<AgentForm>(EMPTY_FORM)

  const orgs = useQuery({ queryKey: ['organizations'], queryFn: () => api.organizations() })
  const projects = useQuery({ queryKey: ['projects'], queryFn: () => api.projects() })

  const agentsQuery = useQuery({
    queryKey: ['agents', scopeKind, scopeId],
    queryFn: () =>
      scopeKind === 'projects' ? api.effectiveAgents(scopeId) : api.agents('orgs', scopeId),
    enabled: !!scopeId,
  })

  const templates = useQuery({
    queryKey: ['ai-prompt-templates', scopeId],
    queryFn: () => api.aiPromptTemplates(scopeId),
    enabled: scopeKind === 'projects' && !!scopeId,
  })
  const skills = useQuery({
    queryKey: ['ai-skills', scopeId],
    queryFn: () => api.aiSkills(scopeId),
    enabled: scopeKind === 'projects' && !!scopeId,
  })

  const save = useMutation({
    mutationFn: () => {
      const body: AgentForm = {
        ...form,
        modelRole: form.modelRole || null,
        modelId: form.modelId?.trim() || null,
        description: form.description?.trim() || null,
        persona: form.persona?.trim() || null,
      }
      return editing && !editing.inherited
        ? api.updateAgent(scopeKind, scopeId, editing.id, body, actor)
        : api.createAgent(scopeKind, scopeId, body, actor)
    },
    onSuccess: () => {
      setShowForm(false)
      setEditing(null)
      void qc.invalidateQueries({ queryKey: ['agents', scopeKind, scopeId] })
    },
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.deleteAgent(scopeKind, scopeId, id, actor),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['agents', scopeKind, scopeId] }),
  })

  const ownerOptions = useMemo(() => {
    if (scopeKind === 'orgs') return (orgs.data ?? []).map(o => ({ id: o.id, label: o.name }))
    return (projects.data ?? []).map(p => ({ id: p.id, label: `${p.orgName} / ${p.name}` }))
  }, [scopeKind, orgs.data, projects.data])

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setShowForm(true)
  }

  function openEdit(a: Agent, asClone: boolean) {
    setEditing(asClone ? null : a)
    setForm({
      name: asClone ? `${a.name} (copy)` : a.name,
      description: a.description ?? '',
      persona: a.persona ?? '',
      systemTemplateId: a.systemTemplateId,
      userTemplateId: a.userTemplateId,
      skillIds: a.skillIds,
      modelRole: a.modelRole ?? '',
      modelId: a.modelId ?? '',
      contextConfig: a.contextConfig ?? {},
      maxRounds: a.maxRounds,
      enabled: a.enabled,
    })
    setShowForm(true)
  }

  return (
    <div className="space-y-6 max-w-4xl h-full min-h-0 overflow-y-auto scrollbar-thin pr-1">
      <PageHeader
        title="Agents"
        icon={<Bot size={20} />}
        description="Reusable AI agents (persona + prompt templates + skills + model + context) assignable to platform tasks. Org agents are inherited by every project; a project agent of the same name overrides it."
      />

      {/* Scope picker */}
      <div className="flex flex-wrap items-end gap-3 bg-surface rounded-lg border border-border p-4">
        <label className="text-sm">
          <span className="block text-fg-muted mb-1">Scope</span>
          <Select
            containerClassName="w-auto min-w-[10rem]"
            value={scopeKind}
            onChange={e => {
              setScopeKind(e.target.value as AgentScope)
              setScopeId('')
            }}
          >
            <option value="projects">Project</option>
            <option value="orgs">Organization</option>
          </Select>
        </label>
        <label className="text-sm flex-1 min-w-56">
          <span className="block text-fg-muted mb-1">
            {scopeKind === 'orgs' ? 'Organization' : 'Project'}
          </span>
          <Select value={scopeId} onChange={e => setScopeId(e.target.value)}>
            <option value="">Select…</option>
            {ownerOptions.map(o => (
              <option key={o.id} value={o.id}>
                {o.label}
              </option>
            ))}
          </Select>
        </label>
        <Button onClick={openCreate} disabled={!scopeId}>
          <Plus size={15} /> New agent
        </Button>
      </div>

      {!scopeId && (
        <p className="text-sm text-fg-subtle">Pick a scope and owner to manage agents.</p>
      )}

      {scopeId && agentsQuery.isLoading && <LoadingSpinner message="Loading agents…" />}
      {scopeId && agentsQuery.error && (
        <ErrorMessage message="Failed to load agents." onRetry={() => void agentsQuery.refetch()} />
      )}

      {scopeId && agentsQuery.data && (
        <div className="bg-surface rounded-lg border border-border divide-y divide-border">
          {agentsQuery.data.length === 0 && (
            <p className="px-5 py-6 text-sm text-fg-subtle">No agents yet.</p>
          )}
          {agentsQuery.data.map(a => (
            <div key={a.id} className="px-5 py-4 flex items-center gap-3">
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-fg flex items-center gap-2">
                  {a.name}
                  {a.inherited && <StatusBadge variant="warning">inherited (org)</StatusBadge>}
                  {!a.enabled && <StatusBadge variant="neutral">disabled</StatusBadge>}
                </p>
                <p className="text-xs text-fg-muted truncate">
                  {a.description || a.persona || '—'} · model{' '}
                  {a.modelId || a.modelRole || 'default'} · {a.skillIds.length} skill(s)
                </p>
              </div>
              {a.inherited ? (
                <button
                  onClick={() => openEdit(a, true)}
                  className="flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
                  title="Clone to this project"
                >
                  <Copy size={14} /> Clone
                </button>
              ) : (
                <>
                  <button
                    onClick={() => openEdit(a, false)}
                    className="text-fg-subtle hover:text-fg"
                    aria-label="Edit"
                  >
                    <Pencil size={16} />
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(`Delete agent "${a.name}"?`)) remove.mutate(a.id)
                    }}
                    className="text-fg-subtle hover:text-danger"
                    aria-label="Delete"
                  >
                    <Trash2 size={16} />
                  </button>
                </>
              )}
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <AgentEditor
          form={form}
          setForm={setForm}
          templates={(templates.data ?? []).map(t => ({ id: t.id, label: `${t.kind}: ${t.name}` }))}
          skills={(skills.data ?? []).map(s => ({ id: s.id, label: s.name }))}
          orgScope={scopeKind === 'orgs'}
          saving={save.isPending}
          error={save.isError ? (save.error as Error).message : null}
          onCancel={() => {
            setShowForm(false)
            setEditing(null)
          }}
          onSave={() => save.mutate()}
          title={editing ? 'Edit agent' : 'New agent'}
        />
      )}
    </div>
  )
}

function AgentEditor({
  form,
  setForm,
  templates,
  skills,
  orgScope,
  saving,
  error,
  onCancel,
  onSave,
  title,
}: {
  form: AgentForm
  setForm: (f: AgentForm) => void
  templates: { id: string; label: string }[]
  skills: { id: string; label: string }[]
  orgScope: boolean
  saving: boolean
  error: string | null
  onCancel: () => void
  onSave: () => void
  title: string
}) {
  const ctx = (form.contextConfig ?? {}) as Record<string, boolean>
  const toggleCtx = (k: string) => setForm({ ...form, contextConfig: { ...ctx, [k]: !ctx[k] } })
  const toggleSkill = (id: string) =>
    setForm({
      ...form,
      skillIds: form.skillIds.includes(id)
        ? form.skillIds.filter(s => s !== id)
        : [...form.skillIds, id],
    })
  const textarea =
    'w-full border border-border-strong rounded-md px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="px-5 py-4 border-b border-border">
          <h2 className="text-lg font-semibold text-fg">{title}</h2>
        </div>
        <div className="px-5 py-4 space-y-4">
          {error && (
            <p className="text-sm text-danger bg-danger-bg rounded-lg px-3 py-2">{error}</p>
          )}
          {orgScope && (
            <div className="flex gap-2 text-xs text-info bg-info-bg border border-info-border rounded-lg px-3 py-2">
              <Info size={14} className="shrink-0 mt-0.5" />
              Template/skill pickers are project-scoped in this version; for an org agent set the
              persona and model, and leave templates blank to use the task seed.
            </div>
          )}
          <Field label="Name">
            <Input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label="Description">
            <Input
              value={form.description ?? ''}
              onChange={e => setForm({ ...form, description: e.target.value })}
            />
          </Field>
          <Field label="Persona (prepended to the system prompt)">
            <textarea
              value={form.persona ?? ''}
              onChange={e => setForm({ ...form, persona: e.target.value })}
              rows={2}
              className={textarea}
            />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="System template">
              <Select
                value={form.systemTemplateId ?? ''}
                onChange={e => setForm({ ...form, systemTemplateId: e.target.value || null })}
                disabled={orgScope}
              >
                <option value="">(task seed)</option>
                {templates.map(t => (
                  <option key={t.id} value={t.id}>
                    {t.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="User template">
              <Select
                value={form.userTemplateId ?? ''}
                onChange={e => setForm({ ...form, userTemplateId: e.target.value || null })}
                disabled={orgScope}
              >
                <option value="">(task seed)</option>
                {templates.map(t => (
                  <option key={t.id} value={t.id}>
                    {t.label}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Field label="Model role">
              <Select
                value={form.modelRole ?? ''}
                onChange={e => setForm({ ...form, modelRole: e.target.value })}
              >
                {MODEL_ROLES.map(r => (
                  <option key={r} value={r}>
                    {r || '(task default)'}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Model id (override)">
              <Input
                value={form.modelId ?? ''}
                onChange={e => setForm({ ...form, modelId: e.target.value })}
                placeholder="e.g. claude-sonnet-4-6, gpt-4o"
                className="font-mono"
              />
            </Field>
            <Field label="Max rounds">
              <Input
                type="number"
                min={0}
                max={5}
                value={form.maxRounds ?? 3}
                onChange={e => setForm({ ...form, maxRounds: Number(e.target.value) })}
              />
            </Field>
          </div>
          {!orgScope && skills.length > 0 && (
            <Field label="Skills">
              <div className="flex flex-wrap gap-2">
                {skills.map(s => (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => toggleSkill(s.id)}
                    className={`text-xs rounded-full px-3 py-1 border ${
                      form.skillIds.includes(s.id)
                        ? 'bg-primary text-primary-fg border-primary'
                        : 'bg-surface text-fg-muted border-border'
                    }`}
                  >
                    {s.label}
                  </button>
                ))}
              </div>
            </Field>
          )}
          <Field label="Inject context">
            <div className="flex flex-wrap gap-3">
              {CONTEXT_KEYS.map(k => (
                <label key={k} className="flex items-center gap-1.5 text-sm text-fg-muted">
                  <input
                    type="checkbox"
                    className="accent-primary"
                    checked={!!ctx[k]}
                    onChange={() => toggleCtx(k)}
                  />
                  {k}
                </label>
              ))}
            </div>
          </Field>
          <label className="flex items-center gap-2 text-sm text-fg">
            <input
              type="checkbox"
              className="accent-primary"
              checked={form.enabled ?? true}
              onChange={e => setForm({ ...form, enabled: e.target.checked })}
            />
            Enabled
          </label>
        </div>
        <div className="px-5 py-4 border-t border-border flex justify-end gap-3">
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button onClick={onSave} disabled={!form.name.trim()} loading={saving}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm font-medium text-fg mb-1">{label}</label>
      {children}
    </div>
  )
}
