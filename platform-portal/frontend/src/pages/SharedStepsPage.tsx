import { useState } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { SharedStep, SharedStepForm } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, PageHeader } from '@/components/ui'
import { ListChecks, Plus, Pencil, Trash2, X } from 'lucide-react'

const inputCls =
  'w-full border border-border-strong rounded-md px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

function SharedStepModal({
  projectId,
  existing,
  onClose,
}: {
  projectId: string
  existing?: SharedStep
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [action, setAction] = useState(existing?.action ?? '')
  const [expected, setExpected] = useState(existing?.expectedResult ?? '')
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const body: SharedStepForm = {
        action: action.trim(),
        expectedResult: expected.trim(),
        notes: notes.trim(),
      }
      return existing
        ? api.updateSharedStep(projectId, existing.id, body)
        : api.createSharedStep(projectId, body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shared-steps', projectId] })
      onClose()
    },
    onError: (e: Error) => setError(e.message),
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg">
            {existing ? 'Edit shared step' : 'New shared step'}
          </h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">Action</label>
            <textarea
              value={action}
              onChange={e => setAction(e.target.value)}
              rows={2}
              className={inputCls}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">Expected result</label>
            <textarea
              value={expected}
              onChange={e => setExpected(e.target.value)}
              rows={2}
              className={inputCls}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">Notes</label>
            <input value={notes} onChange={e => setNotes(e.target.value)} className={inputCls} />
          </div>
          {error && <p className="text-xs text-danger">{error}</p>}
        </div>
        <div className="px-6 py-4 border-t border-border flex gap-3 justify-end">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={() => save.mutate()}
            loading={save.isPending}
            disabled={!action.trim()}
          >
            Save
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function SharedStepsPage() {
  const { projectId } = useProject()
  const queryClient = useQueryClient()
  const [modal, setModal] = useState<{ open: boolean; step?: SharedStep }>({ open: false })

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['shared-steps', projectId],
    queryFn: () => api.sharedSteps(projectId),
  })
  const steps: SharedStep[] = Array.isArray(data) ? data : []
  const del = useMutation({
    mutationFn: (id: string) => api.deleteSharedStep(projectId, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['shared-steps', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading shared steps…" />
  if (error)
    return <ErrorMessage message="Failed to load shared steps." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Shared Steps"
        icon={<ListChecks size={20} />}
        description="A reusable step library. Editing a shared step updates every case that references it."
        actions={
          <Button onClick={() => setModal({ open: true })}>
            <Plus size={15} /> New shared step
          </Button>
        }
      />

      <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
        {steps.length === 0 ? (
          <div className="py-16 text-center">
            <ListChecks size={32} className="mx-auto text-fg-subtle mb-3" />
            <p className="text-sm text-fg-muted">No shared steps yet.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-fg-muted border-b border-border">
                <th className="px-4 py-2.5 font-medium">Action</th>
                <th className="px-4 py-2.5 font-medium">Expected</th>
                <th className="px-4 py-2.5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {steps.map(s => (
                <tr key={s.id} className="hover:bg-surface-muted">
                  <td className="px-4 py-2.5 text-fg">{s.action}</td>
                  <td className="px-4 py-2.5 text-xs text-fg-muted">{s.expectedResult || '—'}</td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => setModal({ open: true, step: s })}
                        title="Edit"
                        className="p-1.5 text-fg-subtle hover:text-primary hover:bg-primary-subtle rounded-md"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => del.mutate(s.id)}
                        title="Delete"
                        className="p-1.5 text-fg-subtle hover:text-danger hover:bg-danger-bg rounded-md"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {modal.open && (
        <SharedStepModal
          projectId={projectId}
          existing={modal.step}
          onClose={() => setModal({ open: false })}
        />
      )}
    </div>
  )
}
