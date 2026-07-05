import { useState } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import type { RunSchedule, RunScheduleForm, GitHubWorkflow } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, PageHeader, StatusBadge } from '@/components/ui'
import { CalendarClock, Plus, Pencil, Trash2, Play, X } from 'lucide-react'

const inputCls =
  'w-full border border-border-strong rounded-md px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

function ScheduleModal({
  projectId,
  existing,
  onClose,
}: {
  projectId: string
  existing?: RunSchedule
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const { data: workflows = [] } = useQuery({
    queryKey: ['github-workflows', projectId],
    queryFn: () => api.githubWorkflows(projectId),
  })

  const [name, setName] = useState(existing?.name ?? '')
  const [workflowKey, setWorkflowKey] = useState(
    existing ? `${existing.repoFullName}::${existing.workflowId}` : '',
  )
  const [ref, setRef] = useState(existing?.ref ?? 'main')
  const [interval, setInterval] = useState(existing?.intervalMinutes ?? 1440)
  const [enabled, setEnabled] = useState(existing?.enabled ?? true)
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const [repoFullName, workflowId] = workflowKey.split('::')
      const body: RunScheduleForm = {
        name: name.trim(),
        repoFullName,
        workflowId: Number(workflowId),
        ref: ref.trim() || 'main',
        inputs: {},
        intervalMinutes: interval,
        enabled,
      }
      return existing
        ? api.updateRunSchedule(projectId, existing.id, body)
        : api.createRunSchedule(projectId, body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['run-schedules', projectId] })
      onClose()
    },
    onError: (e: Error) => setError(e.message),
  })

  const valid = name.trim() && workflowKey && interval > 0

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg">{existing ? 'Edit schedule' : 'New schedule'}</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">Name</label>
            <input value={name} onChange={e => setName(e.target.value)} className={inputCls} />
          </div>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">Workflow</label>
            <select
              value={workflowKey}
              onChange={e => setWorkflowKey(e.target.value)}
              className={inputCls}
            >
              <option value="">— select a workflow —</option>
              {workflows.map((w: GitHubWorkflow) => (
                <option key={`${w.repoFullName}::${w.id}`} value={`${w.repoFullName}::${w.id}`}>
                  {w.name} ({w.repoFullName})
                </option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium text-fg-muted block mb-1">Ref (branch)</label>
              <input value={ref} onChange={e => setRef(e.target.value)} className={inputCls} />
            </div>
            <div>
              <label className="text-xs font-medium text-fg-muted block mb-1">
                Every (minutes)
              </label>
              <input
                type="number"
                value={interval}
                onChange={e => setInterval(Number(e.target.value))}
                className={inputCls}
              />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-fg">
            <input type="checkbox" checked={enabled} onChange={e => setEnabled(e.target.checked)} />
            Enabled
          </label>
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
            disabled={!valid}
          >
            Save
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function SchedulesPage() {
  const { projectId } = useProject()
  const queryClient = useQueryClient()
  const [modal, setModal] = useState<{ open: boolean; schedule?: RunSchedule }>({ open: false })

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['run-schedules', projectId],
    queryFn: () => api.runSchedules(projectId),
  })
  const schedules: RunSchedule[] = Array.isArray(data) ? data : []

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['run-schedules', projectId] })
  const del = useMutation({
    mutationFn: (id: string) => api.deleteRunSchedule(projectId, id),
    onSuccess: invalidate,
  })
  const dispatch = useMutation({
    mutationFn: (id: string) => api.dispatchRunSchedule(projectId, id),
    onSuccess: invalidate,
  })

  if (isLoading) return <LoadingSpinner message="Loading schedules…" />
  if (error)
    return <ErrorMessage message="Failed to load schedules." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Run Schedules"
        icon={<CalendarClock size={20} />}
        description="Dispatch a GitHub Actions workflow on a cadence. Results ingest via the CI reporter."
        actions={
          <Button onClick={() => setModal({ open: true })}>
            <Plus size={15} /> New schedule
          </Button>
        }
      />

      <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
        {schedules.length === 0 ? (
          <div className="py-16 text-center">
            <CalendarClock size={32} className="mx-auto text-fg-subtle mb-3" />
            <p className="text-sm text-fg-muted">No schedules yet.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-fg-muted border-b border-border">
                <th className="px-4 py-2.5 font-medium">Name</th>
                <th className="px-4 py-2.5 font-medium">Workflow</th>
                <th className="px-4 py-2.5 font-medium">Every</th>
                <th className="px-4 py-2.5 font-medium">Next run</th>
                <th className="px-4 py-2.5 font-medium">State</th>
                <th className="px-4 py-2.5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {schedules.map(s => (
                <tr key={s.id} className="hover:bg-surface-muted">
                  <td className="px-4 py-2.5 font-medium text-fg">{s.name}</td>
                  <td className="px-4 py-2.5 text-xs font-mono text-fg-muted">{s.repoFullName}</td>
                  <td className="px-4 py-2.5 tabular-nums text-fg">{s.intervalMinutes}m</td>
                  <td className="px-4 py-2.5 text-xs text-fg-subtle">
                    {s.nextRunAt ? relativeTime(s.nextRunAt) : '—'}
                  </td>
                  <td className="px-4 py-2.5">
                    <StatusBadge variant={s.enabled ? 'success' : 'neutral'}>
                      {s.enabled ? 'enabled' : 'off'}
                    </StatusBadge>
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => dispatch.mutate(s.id)}
                        disabled={dispatch.isPending}
                        title="Dispatch now"
                        className="p-1.5 text-fg-subtle hover:text-primary hover:bg-primary-subtle rounded-md"
                      >
                        <Play size={14} />
                      </button>
                      <button
                        onClick={() => setModal({ open: true, schedule: s })}
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
        <ScheduleModal
          projectId={projectId}
          existing={modal.schedule}
          onClose={() => setModal({ open: false })}
        />
      )}
    </div>
  )
}
