import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Pencil, Trash2, X, Sparkles } from 'lucide-react'
import { api } from '@/lib/api'
import type { AiSkill, AiSkillForm } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'

const ACTOR = 'portal-user'

/**
 * Project-scoped CRUD for reusable AI generation skills. A skill is a named instruction set the
 * user can attach to a test-case generation run to steer the agent.
 */
export default function AiSkillsManager({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<AiSkill | null>(null)
  const [creating, setCreating] = useState(false)

  const {
    data: skills,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['ai-skills', projectId],
    queryFn: () => api.aiSkills(projectId),
  })

  const deleteMutation = useMutation({
    mutationFn: (skillId: string) => api.deleteAiSkill(projectId, skillId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ai-skills', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading skills…" />
  if (error) return <ErrorMessage message={(error as Error).message} />

  return (
    <div className="space-y-4 max-w-2xl">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-slate-900 flex items-center gap-1.5">
            <Sparkles className="w-4 h-4 text-blue-500" /> Generation skills
          </p>
          <p className="text-xs text-slate-500">
            Reusable instruction sets you can attach to an AI test-case generation run.
          </p>
        </div>
        <button
          onClick={() => setCreating(true)}
          className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <Plus className="w-4 h-4" /> New skill
        </button>
      </div>

      {skills && skills.length === 0 && (
        <p className="text-sm text-slate-500 bg-slate-50 border border-slate-200 rounded-lg px-4 py-6 text-center">
          No skills yet. Create one to capture domain heuristics for the agent.
        </p>
      )}

      <div className="space-y-2">
        {skills?.map(skill => (
          <div
            key={skill.id}
            className="bg-white rounded-xl border border-slate-200 shadow-sm px-4 py-3 flex items-start justify-between gap-3"
          >
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <p className="text-sm font-medium text-slate-900 truncate">{skill.name}</p>
                {!skill.enabled && (
                  <span className="text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">
                    disabled
                  </span>
                )}
              </div>
              {skill.description && (
                <p className="text-xs text-slate-500 mt-0.5">{skill.description}</p>
              )}
              <p className="text-xs text-slate-400 mt-1 line-clamp-2 whitespace-pre-wrap">
                {skill.instructions}
              </p>
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <button
                onClick={() => setEditing(skill)}
                className="p-1.5 text-slate-400 hover:text-slate-700"
                title="Edit"
              >
                <Pencil className="w-4 h-4" />
              </button>
              <button
                onClick={() => {
                  if (confirm(`Delete skill "${skill.name}"?`)) deleteMutation.mutate(skill.id)
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
        <SkillModal
          projectId={projectId}
          skill={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function SkillModal({
  projectId,
  skill,
  onClose,
}: {
  projectId: string
  skill: AiSkill | null
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [form, setForm] = useState<AiSkillForm>({
    name: skill?.name ?? '',
    description: skill?.description ?? '',
    instructions: skill?.instructions ?? '',
    enabled: skill?.enabled ?? true,
  })
  const [err, setErr] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      skill
        ? api.updateAiSkill(projectId, skill.id, form, ACTOR)
        : api.createAiSkill(projectId, form, ACTOR),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['ai-skills', projectId] })
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
    if (!form.instructions.trim()) {
      setErr('Instructions are required')
      return
    }
    mutation.mutate()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">{skill ? 'Edit skill' : 'New skill'}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={submit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {err && <p className="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{err}</p>}
          <div>
            <label className={lbl}>Name *</label>
            <input
              type="text"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              className={inp}
              placeholder="e.g. API testing heuristics"
              autoFocus
            />
          </div>
          <div>
            <label className={lbl}>Description</label>
            <input
              type="text"
              value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              className={inp}
              placeholder="Short summary (optional)"
            />
          </div>
          <div>
            <label className={lbl}>Instructions *</label>
            <textarea
              value={form.instructions}
              onChange={e => setForm(f => ({ ...f, instructions: e.target.value }))}
              className={`${inp} min-h-[140px] font-mono`}
              placeholder="Guidance appended to the system prompt during generation…"
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))}
            />
            Enabled (selectable in generation runs)
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
