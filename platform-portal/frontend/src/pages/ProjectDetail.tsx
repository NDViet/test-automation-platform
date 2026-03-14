import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import {
  formatDuration, formatPassRate, passRateColor, flakinessColor,
  relativeTime, cn,
} from '@/lib/utils'
import StatCard from '@/components/StatCard'
import PassRateChart from '@/components/PassRateChart'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import TestImpactPanel from '@/components/TestImpactPanel'
import { CheckCircle, XCircle, AlertTriangle, Clock, ChevronRight } from 'lucide-react'

export default function ProjectDetail() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [days, setDays] = useState(7)

  const { data, isLoading, error } = useQuery({
    queryKey: ['project', projectId, days],
    queryFn: () => api.projectDetail(projectId!, days),
    enabled: !!projectId,
  })

  const { data: trendData } = useQuery({
    queryKey: ['passRateTrend', projectId, 30],
    queryFn: () => api.passRateTrend(projectId!, 30),
    enabled: !!projectId,
  })

  if (isLoading) return <LoadingSpinner message="Loading project…" />
  if (error || !data) return <ErrorMessage message="Failed to load project data." />

  const { project, flakiness, qualityGate, recentExecutions } = data

  const latestRun = recentExecutions?.[0]
  const criticalFlaky = flakiness?.filter(f => f.classification === 'CRITICAL_FLAKY').length ?? 0

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
            <button onClick={() => navigate('/')} className="hover:text-blue-600">Overview</button>
            <ChevronRight size={14} />
            <span>{project?.teamName}</span>
            <ChevronRight size={14} />
          </div>
          <h1 className="text-2xl font-bold text-slate-900">{project?.name ?? projectId}</h1>
          <p className="text-sm text-slate-500 mt-1">{project?.slug}</p>
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

      {/* Quality gate banner */}
      {qualityGate && (
        <div className={cn('rounded-xl border p-4 flex items-start gap-3',
          qualityGate.passed ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200')}>
          {qualityGate.passed
            ? <CheckCircle className="text-green-600 shrink-0 mt-0.5" size={18} />
            : <XCircle className="text-red-600 shrink-0 mt-0.5" size={18} />}
          <div>
            <p className={cn('font-semibold text-sm', qualityGate.passed ? 'text-green-800' : 'text-red-800')}>
              Quality Gate {qualityGate.passed ? 'PASSED' : 'FAILED'}
            </p>
            {!qualityGate.passed && qualityGate.violations.length > 0 && (
              <ul className="mt-1 space-y-0.5">
                {qualityGate.violations.map((v, i) => (
                  <li key={i} className="text-xs text-red-700">• {v}</li>
                ))}
              </ul>
            )}
            <p className="text-xs text-slate-500 mt-1">
              Pass rate: {formatPassRate(qualityGate.actualPassRate)} · {qualityGate.newFailures} new failures
            </p>
          </div>
        </div>
      )}

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Pass Rate"
          value={formatPassRate(latestRun?.passRate)}
          subtitle="latest run"
          colorClass={passRateColor(latestRun?.passRate ?? 0)}
        />
        <StatCard
          title="Last Run Duration"
          value={formatDuration(latestRun?.durationMs)}
          icon={Clock}
          colorClass="text-slate-600"
        />
        <StatCard
          title="Critical Flaky"
          value={criticalFlaky}
          icon={AlertTriangle}
          colorClass={criticalFlaky > 0 ? 'text-red-600' : 'text-green-600'}
        />
        <StatCard
          title={`Total Runs (${days}d)`}
          value={recentExecutions?.length ?? 0}
          colorClass="text-blue-600"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Pass rate trend */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <h2 className="font-semibold text-slate-900 mb-4">Pass Rate (30 days)</h2>
          {trendData && trendData.length > 0
            ? <PassRateChart data={trendData} />
            : <p className="text-sm text-slate-500 py-8 text-center">No trend data available.</p>
          }
        </div>

        {/* Flaky tests */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="font-semibold text-slate-900">Flaky Tests</h2>
          </div>
          <div className="divide-y divide-slate-50 max-h-72 overflow-y-auto">
            {(!flakiness || flakiness.length === 0) && (
              <p className="px-5 py-8 text-sm text-slate-500 text-center">No flaky tests detected.</p>
            )}
            {(flakiness ?? []).filter(f => f.classification !== 'STABLE').slice(0, 10).map(f => (
              <div key={f.id} className="px-5 py-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-xs text-slate-700 truncate flex-1 font-mono"
                     title={f.testId}>
                    {f.testId.split('.').pop() ?? f.testId}
                  </p>
                  <Badge
                    label={f.classification.replace('_', ' ')}
                    colorClass={flakinessColor(f.classification)}
                  />
                </div>
                <p className="text-xs text-slate-400 mt-0.5">
                  Score: {f.score.toFixed(2)} · {(f.failureRate * 100).toFixed(0)}% failure rate
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Test Impact Analysis */}
      <TestImpactPanel projectId={projectId!} />

      {/* Recent runs */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        <div className="px-5 py-4 border-b border-slate-100">
          <h2 className="font-semibold text-slate-900">Recent Runs</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-xs text-slate-500 uppercase tracking-wide bg-slate-50">
                <th className="px-5 py-3 text-left font-medium">Run</th>
                <th className="px-4 py-3 text-left font-medium">Branch</th>
                <th className="px-4 py-3 text-left font-medium">Mode</th>
                <th className="px-4 py-3 text-right font-medium">Tests</th>
                <th className="px-4 py-3 text-right font-medium">Pass Rate</th>
                <th className="px-4 py-3 text-right font-medium">Duration</th>
                <th className="px-4 py-3 text-right font-medium">When</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {(recentExecutions ?? []).length === 0 && (
                <tr><td colSpan={7} className="px-5 py-8 text-center text-slate-500">No runs yet.</td></tr>
              )}
              {(recentExecutions ?? []).map(e => (
                <tr
                  key={e.id}
                  onClick={() => navigate(`/runs/${e.runId}`)}
                  className="hover:bg-slate-50 cursor-pointer transition-colors"
                >
                  <td className="px-5 py-3">
                    <span className="font-mono text-xs text-blue-600 hover:underline">
                      {e.runId.slice(0, 12)}…
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{e.branch ?? '—'}</td>
                  <td className="px-4 py-3">
                    <Badge
                      label={e.executionMode ?? 'UNKNOWN'}
                      colorClass={e.executionMode === 'PARALLEL' ? 'text-purple-700 bg-purple-100' : 'text-slate-600 bg-slate-100'}
                    />
                  </td>
                  <td className="px-4 py-3 text-right text-slate-600">
                    <span className="text-green-600">{e.passed}</span>
                    {' / '}
                    <span className="text-red-600">{e.failed}</span>
                    {' / '}
                    <span className="text-slate-400">{e.totalTests}</span>
                  </td>
                  <td className={cn('px-4 py-3 text-right font-semibold', passRateColor(e.passRate))}>
                    {formatPassRate(e.passRate)}
                  </td>
                  <td className="px-4 py-3 text-right text-slate-500">{formatDuration(e.durationMs)}</td>
                  <td className="px-4 py-3 text-right text-slate-400">{relativeTime(e.executedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
