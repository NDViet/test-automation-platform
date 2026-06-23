import { useState } from 'react'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { formatPassRate, passRateColor, relativeTime } from '@/lib/utils'
import StatCard from '@/components/StatCard'
import LoadingSpinner from '@/components/LoadingSpinner'
import Badge from '@/components/Badge'
import CreateProjectModal from '@/components/CreateProjectModal'
import {
  Activity, AlertTriangle, FolderOpen, TrendingUp, Plus,
  ArrowRight, CheckCircle, Zap,
} from 'lucide-react'
import type { Project, Alert } from '@/lib/types'

function rateColor(p: number): string {
  return p >= 80 ? '#16a34a' : p >= 50 ? '#ca8a04' : '#dc2626'
}

function PassBar({ pct }: { pct: number }) {
  return (
    <div className="h-1.5 rounded-full bg-slate-100 overflow-hidden w-20 shrink-0">
      <div className="h-full rounded-full transition-all" style={{ width: `${Math.min(100, Math.max(0, pct))}%`, background: rateColor(pct) }} />
    </div>
  )
}

function alertSeverityClass(s: string) {
  if (s === 'CRITICAL') return 'text-red-700 bg-red-100'
  if (s === 'HIGH')     return 'text-orange-700 bg-orange-100'
  return 'text-yellow-700 bg-yellow-100'
}

export default function OrgOverview() {
  const { orgSlug } = useParams<{ orgSlug: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [showCreateProject, setShowCreateProject] = useState(false)

  const { data: organizations } = useQuery({
    queryKey: ['organizations'],
    queryFn: api.organizations,
  })

  const { data: projects = [], isLoading: projectsLoading } = useQuery({
    queryKey: ['projects', orgSlug],
    queryFn: () => api.projects(orgSlug),
    enabled: !!orgSlug,
  })

  const { data: overview, isLoading: overviewLoading } = useQuery({
    queryKey: ['overview'],
    queryFn: () => api.overview(7),
    refetchInterval: 60_000,
  })

  const org = organizations?.find(o => o.slug === orgSlug)
  if (organizations && !org) return <Navigate to="/" replace />

  function handleProjectCreated(project: Project) {
    void qc.invalidateQueries({ queryKey: ['projects', orgSlug] })
    void qc.invalidateQueries({ queryKey: ['projects'] })
    navigate(`/${project.orgSlug}/${project.slug}`)
  }

  const summary = overview?.summary

  // Backend returns projectId = slug and passRate as 0-1 fraction
  const projectSlugs = new Set(projects.map(p => p.slug))
  const projectIds   = new Set(projects.map(p => p.id))

  // Org-scoped project summaries (matched by slug)
  const projectSummaries = (summary?.projects ?? []).filter(ps => projectSlugs.has(ps.projectId))

  // Org-scoped alerts (matched by projectId UUID)
  const orgAlerts = (overview?.recentAlerts ?? []).filter(a => projectIds.has(a.projectId))

  // passRate from backend is 0-1 — convert to 0-100 for display
  const pct = (r: number) => r * 100

  // Aggregate stats
  const avgPassRate = projectSummaries.length
    ? pct(projectSummaries.reduce((s, p) => s + (p.passRate ?? 0), 0) / projectSummaries.length)
    : null
  const totalRuns   = projectSummaries.reduce((s, p) => s + (p.totalRuns ?? 0), 0)
  const flakyCount  = projectSummaries.reduce((s, p) => s + (p.flakyTests ?? 0), 0)
  const healthyCount = projectSummaries.filter(p => pct(p.passRate ?? 0) >= 80).length

  if (projectsLoading || overviewLoading) return <LoadingSpinner message="Loading…" />

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">
            {org?.displayName ?? org?.name ?? orgSlug}
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            {projects.length} project{projects.length !== 1 ? 's' : ''} · Quality overview (7d)
            {org?.slug ? <span className="font-mono ml-2 text-slate-400">@{org.slug}</span> : null}
          </p>
        </div>
        <button
          onClick={() => setShowCreateProject(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors shrink-0"
        >
          <Plus size={13} /> New Project
        </button>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Avg Pass Rate"
          value={avgPassRate != null ? formatPassRate(avgPassRate) : '—'}
          icon={TrendingUp}
          colorClass={passRateColor(avgPassRate ?? 0)}
        />
        <StatCard
          title="Healthy Projects"
          value={`${healthyCount} / ${projects.length}`}
          icon={CheckCircle}
          colorClass={healthyCount === projects.length ? 'text-green-600' : 'text-yellow-600'}
        />
        <StatCard
          title="Total Runs (7d)"
          value={totalRuns}
          icon={Activity}
          colorClass="text-blue-600"
        />
        <StatCard
          title="Flaky Tests"
          value={flakyCount}
          icon={Zap}
          colorClass={flakyCount > 0 ? 'text-orange-500' : 'text-green-600'}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Project quality table */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
              <FolderOpen size={14} className="text-slate-400" /> Projects
            </h2>
          </div>

          {projects.length === 0 ? (
            <p className="px-5 py-10 text-sm text-slate-500 text-center">No projects yet.</p>
          ) : (
            <div className="divide-y divide-slate-50">
              {projects.map(p => {
                const ps = projectSummaries.find(s => s.projectId === p.slug)
                const pr = pct(ps?.passRate ?? 0)
                const healthy = pr >= 80
                return (
                  <button
                    key={p.id}
                    onClick={() => navigate(`/${p.orgSlug}/${p.slug}`)}
                    className="w-full text-left px-5 py-3 hover:bg-slate-50 transition-colors group"
                  >
                    <div className="flex items-center gap-3">
                      {/* Health dot */}
                      <div className={`w-2 h-2 rounded-full shrink-0 ${healthy ? 'bg-green-500' : pr >= 50 ? 'bg-yellow-400' : 'bg-red-500'}`} />

                      {/* Name + slug */}
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-900 truncate group-hover:text-blue-700">{p.name}</p>
                        <p className="text-[11px] text-slate-400 font-mono">{p.slug}</p>
                      </div>

                      {/* Pass rate bar + value */}
                      <div className="flex items-center gap-2 shrink-0">
                        <PassBar pct={pr} />
                        <span className="text-xs font-semibold tabular-nums w-12 text-right" style={{ color: rateColor(pr) }}>
                          {ps && ps.totalRuns > 0 ? formatPassRate(pr) : '—'}
                        </span>
                      </div>

                      {/* Runs */}
                      <div className="text-right shrink-0 w-14">
                        <p className="text-xs text-slate-500 tabular-nums">{ps?.totalRuns ?? 0} runs</p>
                        {(ps?.flakyTests ?? 0) > 0 && (
                          <p className="text-[10px] text-orange-500">{ps!.flakyTests} flaky</p>
                        )}
                      </div>

                      <ArrowRight size={13} className="text-slate-300 group-hover:text-blue-400 shrink-0" />
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Recent alerts */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
              <AlertTriangle size={14} className="text-slate-400" /> Recent Alerts
            </h2>
            {orgAlerts.length > 0 && (
              <span className="text-xs text-slate-400">{orgAlerts.length} alert{orgAlerts.length !== 1 ? 's' : ''}</span>
            )}
          </div>

          {orgAlerts.length === 0 ? (
            <div className="flex flex-col items-center justify-center px-5 py-10 gap-2">
              <CheckCircle size={24} className="text-green-400" />
              <p className="text-sm text-slate-500">No alerts — all projects healthy</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-50">
              {orgAlerts.slice(0, 8).map((a: Alert) => {
                const proj = projects.find(p => p.id === a.projectId)
                return (
                  <div key={a.id} className="px-5 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-slate-900 truncate">{a.ruleName}</p>
                        <p className="text-xs text-slate-500 truncate mt-0.5">{a.message}</p>
                        {proj && (
                          <button
                            onClick={() => navigate(`/${proj.orgSlug}/${proj.slug}`)}
                            className="text-[10px] text-blue-500 hover:text-blue-700 mt-0.5 font-mono"
                          >
                            {proj.name}
                          </button>
                        )}
                      </div>
                      <div className="flex flex-col items-end gap-1 shrink-0">
                        <Badge
                          label={a.severity}
                          colorClass={alertSeverityClass(a.severity)}
                        />
                        <p className="text-[10px] text-slate-400">{relativeTime(a.firedAt)}</p>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      <CreateProjectModal
        open={showCreateProject}
        organizations={org ? [org] : (organizations ?? [])}
        onClose={() => setShowCreateProject(false)}
        onCreated={handleProjectCreated}
      />
    </div>
  )
}
