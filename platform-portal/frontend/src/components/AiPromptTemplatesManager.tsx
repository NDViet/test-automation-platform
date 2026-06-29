import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Pencil, Trash2, X, FileText, Star } from 'lucide-react'
import { api } from '@/lib/api'
import type { AiPromptTemplate, AiPromptTemplateForm, PromptKind } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'

const ACTOR = 'portal-user'

/**
 * Project-scoped CRUD for AI generation prompt templates. SYSTEM and USER templates each have one
 * optional default; the default body pre-fills the generation form and is used when a run carries
 * no override.
 */
export default function AiPromptTemplatesManager({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<AiPromptTemplate | null>(null)
  const [creating, setCreating] = useState(false)

  const {
    data: templates,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['ai-prompt-templates', projectId],
    queryFn: () => api.aiPromptTemplates(projectId),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteAiPromptTemplate(projectId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ai-prompt-templates', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading prompt templates…" />
  if (error) return <ErrorMessage message={(error as Error).message} />

  return (
    <div className="space-y-4 max-w-2xl">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-slate-900 flex items-center gap-1.5">
            <FileText className="w-4 h-4 text-blue-500" /> Prompt templates
          </p>
          <p className="text-xs text-slate-500">
            Saved SYSTEM &amp; USER prompts. The default of each kind pre-fills the generation form.
          </p>
        </div>
        <button
          onClick={() => setCreating(true)}
          className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <Plus className="w-4 h-4" /> New template
        </button>
      </div>

      {templates && templates.length === 0 && (
        <p className="text-sm text-slate-500 bg-slate-50 border border-slate-200 rounded-lg px-4 py-6 text-center">
          No custom templates. Built-in defaults are used until you add one.
        </p>
      )}

      <div className="space-y-2">
        {templates?.map(t => (
          <div
            key={t.id}
            className="bg-white rounded-xl border border-slate-200 shadow-sm px-4 py-3 flex items-start justify-between gap-3"
          >
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                  {t.kind}
                </span>
                <p className="text-sm font-medium text-slate-900 truncate">{t.name}</p>
                {t.isDefault && (
                  <span className="inline-flex items-center gap-0.5 text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">
                    <Star className="w-3 h-3" /> default
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-400 mt-1 line-clamp-2 whitespace-pre-wrap">
                {t.body}
              </p>
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <button
                onClick={() => setEditing(t)}
                className="p-1.5 text-slate-400 hover:text-slate-700"
                title="Edit"
              >
                <Pencil className="w-4 h-4" />
              </button>
              <button
                onClick={() => {
                  if (confirm(`Delete template "${t.name}"?`)) deleteMutation.mutate(t.id)
                }}
                className="p-1.5 text-slate-400 hover:text-red-600"
                title="Delete"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {(creating || editing) && (
        <TemplateModal
          projectId={projectId}
          template={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function TemplateModal({
  projectId,
  template,
  onClose,
}: {
  projectId: string
  template: AiPromptTemplate | null
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [form, setForm] = useState<AiPromptTemplateForm>({
    kind: template?.kind ?? 'SYSTEM',
    name: template?.name ?? '',
    body: template?.body ?? '',
    isDefault: template?.isDefault ?? false,
  })
  const [err, setErr] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      template
        ? api.updateAiPromptTemplate(projectId, template.id, form, ACTOR)
        : api.createAiPromptTemplate(projectId, form, ACTOR),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['ai-prompt-templates', projectId] })
      onClose()
    },
    onError: e => setErr((e as Error).message),
  })

  const inp =
    'w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500'
  const lbl = 'block text-xs font-medium text-slate-700 mb-1'

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    if (!form.name.trim()) {
      setErr('Name is required')
      return
    }
    if (!form.body.trim()) {
      setErr('Body is required')
      return
    }
    mutation.mutate()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">
            {template ? 'Edit template' : 'New template'}
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={submit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {err && <p className="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{err}</p>}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={lbl}>Kind *</label>
              <select
                value={form.kind}
                onChange={e => setForm(f => ({ ...f, kind: e.target.value as PromptKind }))}
                className={inp}
                disabled={!!template}
              >
                <option value="SYSTEM">SYSTEM</option>
                <option value="USER">USER</option>
              </select>
            </div>
            <div>
              <label className={lbl}>Name *</label>
              <input
                type="text"
                value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                className={inp}
                placeholder="e.g. Strict QA system prompt"
                autoFocus
              />
            </div>
          </div>
          <div>
            <label className={lbl}>Body *</label>
            <textarea
              value={form.body}
              onChange={e => setForm(f => ({ ...f, body: e.target.value }))}
              className={`${inp} min-h-[180px] font-mono`}
              placeholder="Prompt text…"
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={form.isDefault}
              onChange={e => setForm(f => ({ ...f, isDefault: e.target.checked }))}
            />
            Default for this kind (pre-fills the generation form)
          </label>
        </form>
        <div className="flex items-center justify-end gap-2 px-5 py-4 border-t border-slate-200">
          <button
            onClick={onClose}
            className="px-3 py-2 text-sm text-slate-600 hover:text-slate-900"
          >
            Cancel
          </button>
          <button
            onClick={submit}
            disabled={mutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {mutation.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}
