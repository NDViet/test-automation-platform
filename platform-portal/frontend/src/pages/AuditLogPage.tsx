import { useState } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import type { AuditEvent } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { PageHeader, StatusBadge } from '@/components/ui'
import type { StatusVariant } from '@/components/ui/StatusBadge'
import { ScrollText } from 'lucide-react'

function outcomeVariant(outcome: string | null): StatusVariant {
  switch (outcome) {
    case 'SUCCESS':
      return 'success'
    case 'FAILURE':
      return 'danger'
    case 'BLOCKED':
      return 'warning'
    default:
      return 'neutral'
  }
}

function actorLabel(e: AuditEvent): string {
  if (e.actorUsername) return e.actorUsername
  if (e.actorKeyPrefix) return `key:${e.actorKeyPrefix}`
  return 'system'
}

export default function AuditLogPage() {
  const { projectId } = useProject()
  const [resourceType, setResourceType] = useState('')
  const [days, setDays] = useState(30)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['audit-log', projectId, resourceType, days],
    queryFn: () => api.auditLog(projectId, { resourceType: resourceType || undefined, days }),
  })
  const events: AuditEvent[] = Array.isArray(data) ? data : []

  const selectCls = 'border border-border-strong rounded-md px-2 py-1.5 text-sm bg-surface text-fg'

  if (isLoading) return <LoadingSpinner message="Loading audit log…" />
  if (error)
    return <ErrorMessage message="Failed to load audit log." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Audit Log"
        icon={<ScrollText size={20} />}
        description="Who did what — approvals, edits, deletes, gate overrides, and more."
        actions={
          <div className="flex items-center gap-2">
            <select
              value={resourceType}
              onChange={e => setResourceType(e.target.value)}
              className={selectCls}
              aria-label="Resource type"
            >
              <option value="">All resources</option>
              <option value="TEST_CASE">Test cases</option>
              <option value="RELEASE">Releases</option>
              <option value="FAILURE_CLUSTER">Failure clusters</option>
              <option value="TEST_RUN">Test runs</option>
            </select>
            <select
              value={days}
              onChange={e => setDays(Number(e.target.value))}
              className={selectCls}
              aria-label="Time window"
            >
              <option value={7}>7 days</option>
              <option value={30}>30 days</option>
              <option value={90}>90 days</option>
            </select>
          </div>
        }
      />

      <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
        {events.length === 0 ? (
          <div className="py-16 text-center">
            <ScrollText size={32} className="mx-auto text-fg-subtle mb-3" />
            <p className="text-sm text-fg-muted">No audit events in this window.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-fg-muted border-b border-border">
                <th className="px-4 py-2.5 font-medium">When</th>
                <th className="px-4 py-2.5 font-medium">Actor</th>
                <th className="px-4 py-2.5 font-medium">Event</th>
                <th className="px-4 py-2.5 font-medium">Resource</th>
                <th className="px-4 py-2.5 font-medium">Outcome</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {events.map(e => (
                <tr key={e.id} className="hover:bg-surface-muted">
                  <td className="px-4 py-2.5 text-xs text-fg-subtle whitespace-nowrap">
                    {e.occurredAt ? relativeTime(e.occurredAt) : '—'}
                  </td>
                  <td className="px-4 py-2.5 text-fg">{actorLabel(e)}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-fg">{e.eventType}</td>
                  <td className="px-4 py-2.5 text-xs text-fg-muted">
                    {e.resourceType}
                    {e.resourceId ? (
                      <span className="text-fg-subtle"> · {e.resourceId.slice(0, 8)}</span>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="px-4 py-2.5">
                    <StatusBadge variant={outcomeVariant(e.outcome)}>
                      {(e.outcome ?? '—').toLowerCase()}
                    </StatusBadge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
