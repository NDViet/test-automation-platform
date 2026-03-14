import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import { formatPassRate, passRateColor, relativeTime, cn } from '@/lib/utils'
import StatCard from '@/components/StatCard'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import Badge from '@/components/Badge'
import { Activity, AlertTriangle, FolderOpen, TrendingUp } from 'lucide-react'

export default function OrgOverview() {
  const navigate = useNavigate()

  const { data, isLoading, error } = useQuery({
    queryKey: ['overview'],
    queryFn: () => api.overview(7),
    refetchInterval: 60_000,
  })

  const { data: projects, isLoading: projectsLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.projects(),
  })

  if (isLoading) return <LoadingSpinner message="Loading overview…" />
  if (error) return <ErrorMessage message="Failed to load overview data." />

  const summary = data?.summary
  const alerts = data?.recentAlerts ?? []

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Organization Overview</h1>
        <p className="text-sm text-slate-500 mt-1">Quality health across all teams and projects</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Overall Pass Rate"
          value={formatPassRate(summary?.overallPassRate)}
          icon={TrendingUp}
          colorClass={passRateColor(summary?.overallPassRate ?? 0)}
        />
        <StatCard
          title="Total Projects"
          value={summary?.totalProjects ?? 0}
          icon={FolderOpen}
          colorClass="text-blue-600"
        />
        <StatCard
          title="Total Runs (7d)"
          value={summary?.totalRuns ?? 0}
          icon={Activity}
          colorClass="text-slate-600"
        />
        <StatCard
          title="Critical Flaky Tests"
          value={summary?.criticalFlakyTests ?? 0}
          icon={AlertTriangle}
          colorClass={(summary?.criticalFlakyTests ?? 0) > 0 ? 'text-red-600' : 'text-green-600'}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Projects table */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="font-semibold text-slate-900">Projects</h2>
          </div>
          {projectsLoading ? (
            <LoadingSpinner message="Loading projects…" />
          ) : (
            <div className="divide-y divide-slate-50">
              {(projects ?? []).length === 0 && (
                <p className="px-5 py-8 text-sm text-slate-500 text-center">No projects yet.</p>
              )}
              {(projects ?? []).map(p => {
                const ps = summary?.projects?.find(s => s.projectId === p.id)
                return (
                  <button
                    key={p.id}
                    onClick={() => navigate(`/projects/${p.id}`)}
                    className="w-full text-left px-5 py-3.5 hover:bg-slate-50 transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium text-slate-900">{p.name}</p>
                        <p className="text-xs text-slate-400">{p.teamName} · {p.slug}</p>
                      </div>
                      <div className="text-right">
                        <p className={cn('text-sm font-semibold', passRateColor(ps?.passRate ?? 0))}>
                          {formatPassRate(ps?.passRate)}
                        </p>
                        <p className="text-xs text-slate-400">{ps?.totalRuns ?? 0} runs</p>
                      </div>
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Recent alerts */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="font-semibold text-slate-900">Recent Alerts</h2>
          </div>
          <div className="divide-y divide-slate-50">
            {alerts.length === 0 && (
              <p className="px-5 py-8 text-sm text-slate-500 text-center">No recent alerts.</p>
            )}
            {alerts.slice(0, 8).map((a: import('@/lib/types').Alert) => (
              <div key={a.id} className="px-5 py-3.5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-900 truncate">{a.ruleName}</p>
                    <p className="text-xs text-slate-500 truncate mt-0.5">{a.message}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <Badge
                      label={a.severity}
                      colorClass={
                        a.severity === 'CRITICAL' ? 'text-red-700 bg-red-100' :
                        a.severity === 'HIGH'     ? 'text-orange-700 bg-orange-100' :
                                                    'text-yellow-700 bg-yellow-100'
                      }
                    />
                    <p className="text-xs text-slate-400">{relativeTime(a.firedAt)}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
