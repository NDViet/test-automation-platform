import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import { severityVariant } from '@/lib/status'
import { Button, PageHeader, StatusBadge } from '@/components/ui'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'

export default function AlertsPage() {
  const [days, setDays] = useState(7)

  const {
    data: alerts,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['alerts', days],
    queryFn: () => api.alerts(days),
    refetchInterval: 30_000,
  })

  if (isLoading) return <LoadingSpinner message="Loading alerts…" />
  if (error) return <ErrorMessage message="Failed to load alerts." onRetry={() => void refetch()} />

  const list = alerts ?? []
  const sevTint = { CRITICAL: 'text-danger', HIGH: 'text-warning', MEDIUM: 'text-info' } as const

  return (
    <div className="space-y-6">
      <PageHeader
        title="Alerts"
        icon={<Bell size={20} />}
        description="Organization-wide alert history"
        actions={
          <div className="flex items-center gap-1.5">
            {[7, 14, 30].map(d => (
              <Button
                key={d}
                size="sm"
                variant={days === d ? 'primary' : 'secondary'}
                onClick={() => setDays(d)}
              >
                {d}d
              </Button>
            ))}
          </div>
        }
      />

      {/* Summary */}
      <div className="grid grid-cols-3 gap-4">
        {(['CRITICAL', 'HIGH', 'MEDIUM'] as const).map(sev => {
          const count = list.filter(a => a.severity === sev).length
          return (
            <div key={sev} className="bg-surface rounded-lg border border-border shadow-xs p-4">
              <p className="text-xs text-fg-muted uppercase tracking-wide">{sev}</p>
              <p className={`text-3xl font-bold mt-1 ${sevTint[sev]}`}>{count}</p>
            </div>
          )
        })}
      </div>

      {/* Alert list */}
      <div className="bg-surface rounded-lg border border-border shadow-xs divide-y divide-border">
        {list.length === 0 && (
          <p className="px-5 py-12 text-center text-sm text-fg-muted">
            No alerts in the last {days} days.
          </p>
        )}
        {list.map(a => (
          <div key={a.id} className="px-5 py-4">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <StatusBadge variant={severityVariant(a.severity)}>{a.severity}</StatusBadge>
                  <span className="text-sm font-semibold text-fg">{a.ruleName}</span>
                </div>
                <p className="text-sm text-fg-muted mt-1">{a.message}</p>
                <div className="flex items-center gap-3 mt-1">
                  {a.projectId && (
                    <span className="text-xs text-fg-subtle">Project: {a.projectId}</span>
                  )}
                  {a.runId && (
                    <span className="text-xs font-mono text-fg-subtle">
                      Run: {a.runId.slice(0, 12)}…
                    </span>
                  )}
                </div>
              </div>
              <div className="shrink-0 text-right flex flex-col items-end gap-1">
                <p className="text-xs text-fg-subtle">{relativeTime(a.firedAt)}</p>
                <StatusBadge variant={a.delivered ? 'success' : 'neutral'}>
                  {a.delivered ? 'Delivered' : 'Pending'}
                </StatusBadge>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
