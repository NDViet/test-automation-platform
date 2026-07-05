import { useState } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Dataset, DatasetForm } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, PageHeader } from '@/components/ui'
import { Database, Plus, Pencil, Trash2, X } from 'lucide-react'

const inputCls =
  'w-full border border-border-strong rounded-md px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

function rowsToText(rows: Record<string, string>): string {
  return Object.entries(rows)
    .map(([k, v]) => `${k}=${v}`)
    .join('\n')
}
function textToRows(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of text.split('\n')) {
    const i = line.indexOf('=')
    if (i > 0) out[line.slice(0, i).trim()] = line.slice(i + 1).trim()
  }
  return out
}

function DatasetModal({
  projectId,
  existing,
  onClose,
}: {
  projectId: string
  existing?: Dataset
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(existing?.name ?? '')
  const [env, setEnv] = useState(existing?.env ?? '')
  const [rowsText, setRowsText] = useState(existing ? rowsToText(existing.rows) : '')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const body: DatasetForm = { name: name.trim(), env: env.trim(), rows: textToRows(rowsText) }
      return existing
        ? api.updateDataset(projectId, existing.id, body)
        : api.createDataset(projectId, body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['datasets', projectId] })
      onClose()
    },
    onError: (e: Error) => setError(e.message),
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg">{existing ? 'Edit dataset' : 'New dataset'}</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium text-fg-muted block mb-1">Name</label>
              <input value={name} onChange={e => setName(e.target.value)} className={inputCls} />
            </div>
            <div>
              <label className="text-xs font-medium text-fg-muted block mb-1">Env (optional)</label>
              <input value={env} onChange={e => setEnv(e.target.value)} className={inputCls} />
            </div>
          </div>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">
              Rows (one key=value per line)
            </label>
            <textarea
              value={rowsText}
              onChange={e => setRowsText(e.target.value)}
              rows={6}
              placeholder={'username=alice\npassword=secret'}
              className={inputCls + ' font-mono'}
            />
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
            disabled={!name.trim()}
          >
            Save
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function DatasetsPage() {
  const { projectId } = useProject()
  const queryClient = useQueryClient()
  const [modal, setModal] = useState<{ open: boolean; dataset?: Dataset }>({ open: false })

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['datasets', projectId],
    queryFn: () => api.datasets(projectId),
  })
  const datasets: Dataset[] = Array.isArray(data) ? data : []
  const del = useMutation({
    mutationFn: (id: string) => api.deleteDataset(projectId, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['datasets', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading datasets…" />
  if (error)
    return <ErrorMessage message="Failed to load datasets." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Datasets"
        icon={<Database size={20} />}
        description="Reusable test-data sets (key/value) scoped to this project and optional environment."
        actions={
          <Button onClick={() => setModal({ open: true })}>
            <Plus size={15} /> New dataset
          </Button>
        }
      />

      <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
        {datasets.length === 0 ? (
          <div className="py-16 text-center">
            <Database size={32} className="mx-auto text-fg-subtle mb-3" />
            <p className="text-sm text-fg-muted">No datasets yet.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-fg-muted border-b border-border">
                <th className="px-4 py-2.5 font-medium">Name</th>
                <th className="px-4 py-2.5 font-medium">Env</th>
                <th className="px-4 py-2.5 font-medium">Keys</th>
                <th className="px-4 py-2.5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {datasets.map(d => (
                <tr key={d.id} className="hover:bg-surface-muted">
                  <td className="px-4 py-2.5 font-medium text-fg">{d.name}</td>
                  <td className="px-4 py-2.5 text-xs text-fg-muted">{d.env || '—'}</td>
                  <td className="px-4 py-2.5 text-xs text-fg-subtle font-mono">
                    {Object.keys(d.rows).join(', ') || '—'}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => setModal({ open: true, dataset: d })}
                        title="Edit"
                        className="p-1.5 text-fg-subtle hover:text-primary hover:bg-primary-subtle rounded-md"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => del.mutate(d.id)}
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
        <DatasetModal
          projectId={projectId}
          existing={modal.dataset}
          onClose={() => setModal({ open: false })}
        />
      )}
    </div>
  )
}
