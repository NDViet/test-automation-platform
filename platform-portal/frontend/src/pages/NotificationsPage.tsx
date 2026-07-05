import { useState } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { NotificationPref, NotificationPrefForm } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, PageHeader, StatusBadge } from '@/components/ui'
import { Bell, Plus, Pencil, Trash2, X } from 'lucide-react'

const CHANNELS = ['SLACK', 'EMAIL', 'WEBHOOK']
const EVENTS = ['RUN_COMPLETED', 'RUN_HAS_FAILURES', 'REVIEW_REQUESTED', 'GATE_RESULT']

const inputCls =
  'w-full border border-border-strong rounded-md px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

function PrefModal({
  projectId,
  existing,
  onClose,
}: {
  projectId: string
  existing?: NotificationPref
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [channel, setChannel] = useState(existing?.channel ?? 'SLACK')
  const [destination, setDestination] = useState(existing?.destination ?? '')
  const [events, setEvents] = useState<string[]>(existing?.events ?? [])
  const [enabled, setEnabled] = useState(existing?.enabled ?? true)
  const [error, setError] = useState<string | null>(null)

  const toggle = (e: string) =>
    setEvents(prev => (prev.includes(e) ? prev.filter(x => x !== e) : [...prev, e]))

  const save = useMutation({
    mutationFn: () => {
      const body: NotificationPrefForm = {
        channel,
        destination: destination.trim(),
        events,
        enabled,
      }
      return existing
        ? api.updateNotificationPref(projectId, existing.id, body)
        : api.createNotificationPref(projectId, body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-prefs', projectId] })
      onClose()
    },
    onError: (e: Error) => setError(e.message),
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <h2 className="font-semibold text-fg">{existing ? 'Edit route' : 'New route'}</h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium text-fg-muted block mb-1">Channel</label>
              <select
                value={channel}
                onChange={e => setChannel(e.target.value)}
                className={inputCls}
              >
                {CHANNELS.map(c => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <label className="flex items-end gap-2 text-sm text-fg pb-2">
              <input
                type="checkbox"
                checked={enabled}
                onChange={e => setEnabled(e.target.checked)}
              />
              Enabled
            </label>
          </div>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">
              Destination (webhook URL or email)
            </label>
            <input
              value={destination}
              onChange={e => setDestination(e.target.value)}
              className={inputCls}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-fg-muted block mb-1">Events</label>
            <div className="space-y-1">
              {EVENTS.map(e => (
                <label key={e} className="flex items-center gap-2 text-sm text-fg">
                  <input type="checkbox" checked={events.includes(e)} onChange={() => toggle(e)} />
                  {e.replace(/_/g, ' ').toLowerCase()}
                </label>
              ))}
            </div>
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
            disabled={!destination.trim()}
          >
            Save
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function NotificationsPage() {
  const { projectId } = useProject()
  const queryClient = useQueryClient()
  const [modal, setModal] = useState<{ open: boolean; pref?: NotificationPref }>({ open: false })

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['notification-prefs', projectId],
    queryFn: () => api.notificationPrefs(projectId),
  })
  const prefs: NotificationPref[] = Array.isArray(data) ? data : []
  const del = useMutation({
    mutationFn: (id: string) => api.deleteNotificationPref(projectId, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notification-prefs', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Loading notifications…" />
  if (error)
    return <ErrorMessage message="Failed to load notifications." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Notifications"
        icon={<Bell size={20} />}
        description="Route quality events (runs, failures, reviews, gate results) to Slack, email, or a webhook."
        actions={
          <Button onClick={() => setModal({ open: true })}>
            <Plus size={15} /> New route
          </Button>
        }
      />

      <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
        {prefs.length === 0 ? (
          <div className="py-16 text-center">
            <Bell size={32} className="mx-auto text-fg-subtle mb-3" />
            <p className="text-sm text-fg-muted">No notification routes yet.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-fg-muted border-b border-border">
                <th className="px-4 py-2.5 font-medium">Channel</th>
                <th className="px-4 py-2.5 font-medium">Destination</th>
                <th className="px-4 py-2.5 font-medium">Events</th>
                <th className="px-4 py-2.5 font-medium">State</th>
                <th className="px-4 py-2.5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {prefs.map(p => (
                <tr key={p.id} className="hover:bg-surface-muted">
                  <td className="px-4 py-2.5 font-medium text-fg">{p.channel}</td>
                  <td className="px-4 py-2.5 text-xs font-mono text-fg-muted truncate max-w-xs">
                    {p.destination}
                  </td>
                  <td className="px-4 py-2.5 text-xs text-fg-subtle">
                    {p.events.length ? p.events.join(', ').toLowerCase() : '—'}
                  </td>
                  <td className="px-4 py-2.5">
                    <StatusBadge variant={p.enabled ? 'success' : 'neutral'}>
                      {p.enabled ? 'enabled' : 'off'}
                    </StatusBadge>
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => setModal({ open: true, pref: p })}
                        title="Edit"
                        className="p-1.5 text-fg-subtle hover:text-primary hover:bg-primary-subtle rounded-md"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => del.mutate(p.id)}
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
        <PrefModal
          projectId={projectId}
          existing={modal.pref}
          onClose={() => setModal({ open: false })}
        />
      )}
    </div>
  )
}
