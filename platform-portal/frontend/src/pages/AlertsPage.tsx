import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { severityColor, relativeTime, cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'

export default function AlertsPage() {
  const [days, setDays] = useState(7)

  const { data: alerts, isLoading, error } = useQuery({
    queryKey: ['alerts', days],
    queryFn: () => api.alerts(days),
    refetchInterval: 30_000,
  })

  if (isLoading) return <LoadingSpinner message="Loading alerts…" />
  if (error) return <ErrorMessage message="Failed to load alerts." />

  const list = alerts ?? []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Alerts</h1>
          <p className="text-sm text-slate-500 mt-1">Organization-wide alert history</p>
        </div>
        <div className="flex items-center gap-2">
          {[7, 14, 30].map(d => (
            <button key={d}
              onClick={() => setDays(d)}
              className={cn('px-3 py-1.5 text-xs font-medium rounded-lg transition-colors',
                days === d ? 'bg-blue-600 text-white' : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
              )}
            >
              {d}d
            </button>
          ))}
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-4">
        {(['CRITICAL', 'HIGH', 'MEDIUM'] as const).map(sev => {
          const count = list.filter(a => a.severity === sev).length
          return (
            <div key={sev} className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
              <p className="text-xs text-slate-500 uppercase tracking-wide">{sev}</p>
              <p className={cn('text-3xl font-bold mt-1',
                sev === 'CRITICAL' ? 'text-red-600' :
                sev === 'HIGH'     ? 'text-orange-600' : 'text-yellow-600'
              )}>{count}</p>
            </div>
          )
        })}
      </div>

      {/* Alert list */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-50">
        {list.length === 0 && (
          <p className="px-5 py-12 text-center text-sm text-slate-500">
            No alerts in the last {days} days.
          </p>
        )}
        {list.map(a => (
          <div key={a.id} className="px-5 py-4">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <Badge label={a.severity} colorClass={severityColor(a.severity)} />
                  <span className="text-sm font-semibold text-slate-900">{a.ruleName}</span>
                </div>
                <p className="text-sm text-slate-600 mt-1">{a.message}</p>
                <div className="flex items-center gap-3 mt-1">
                  {a.projectId && (
                    <span className="text-xs text-slate-400">Project: {a.projectId}</span>
                  )}
                  {a.runId && (
                    <span className="text-xs font-mono text-slate-400">
                      Run: {a.runId.slice(0, 12)}…
                    </span>
                  )}
                </div>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs text-slate-400">{relativeTime(a.firedAt)}</p>
                <Badge
                  label={a.delivered ? 'Delivered' : 'Pending'}
                  colorClass={a.delivered ? 'text-green-700 bg-green-100' : 'text-gray-600 bg-gray-100'}
                />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
