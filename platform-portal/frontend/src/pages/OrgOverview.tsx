import { useState } from 'react'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { formatPassRate, passRateColor, relativeTime } from '@/lib/utils'
import { severityVariant } from '@/lib/status'
import StatCard from '@/components/StatCard'
import LoadingSpinner from '@/components/LoadingSpinner'
import { Button, PageHeader, StatusBadge } from '@/components/ui'
import CreateProjectModal from '@/components/CreateProjectModal'
import {
  Activity,
  AlertTriangle,
  FolderOpen,
  TrendingUp,
  Plus,
  ArrowRight,
  CheckCircle,
  Zap,
} from 'lucide-react'
import type { Project, Alert } from '@/lib/types'

function rateColor(p: number): string {
  return p >= 80 ? '#16a34a' : p >= 50 ? '#ca8a04' : '#dc2626'
}

function PassBar({ pct }: { pct: number }) {
  return (
    <div className="h-1.5 rounded-full bg-surface-muted overflow-hidden w-20 shrink-0">
      <div
        className="h-full rounded-full transition-all"
        style={{ width: `${Math.min(100, Math.max(0, pct))}%`, background: rateColor(pct) }}
      />
    </div>
  )
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
  const projectIds = new Set(projects.map(p => p.id))

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
  const totalRuns = projectSummaries.reduce((s, p) => s + (p.totalRuns ?? 0), 0)
  const flakyCount = projectSummaries.reduce((s, p) => s + (p.flakyTests ?? 0), 0)
  const healthyCount = projectSummaries.filter(p => pct(p.passRate ?? 0) >= 80).length

  if (projectsLoading || overviewLoading) return <LoadingSpinner message="Loading…" />

  return (
    <div className="space-y-6">
      <PageHeader
        title={org?.displayName ?? org?.name ?? orgSlug ?? 'Organization'}
        description={
          <>
            {projects.length} project{projects.length !== 1 ? 's' : ''} · Quality overview (7d)
            {org?.slug ? <span className="font-mono ml-2 text-fg-subtle">@{org.slug}</span> : null}
          </>
        }
        actions={
          <Button size="sm" onClick={() => setShowCreateProject(true)}>
            <Plus size={13} /> New Project
          </Button>
        }
      />

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
          colorClass={healthyCount === projects.length ? 'text-success' : 'text-warning'}
        />
        <StatCard title="Total Runs (7d)" value={totalRuns} icon={Activity} colorClass="text-primary" />
        <StatCard
          title="Flaky Tests"
          value={flakyCount}
          icon={Zap}
          colorClass={flakyCount > 0 ? 'text-warning' : 'text-success'}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Project quality table */}
        <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
          <div className="px-5 py-3.5 border-b border-border flex items-center justify-between">
            <h2 className="text-sm font-semibold text-fg flex items-center gap-2">
              <FolderOpen size={14} className="text-fg-muted" /> Projects
            </h2>
          </div>

          {projects.length === 0 ? (
            <p className="px-5 py-10 text-sm text-fg-muted text-center">No projects yet.</p>
          ) : (
            <div className="divide-y divide-border">
              {projects.map(p => {
                const ps = projectSummaries.find(s => s.projectId === p.slug)
                const pr = pct(ps?.passRate ?? 0)
                const healthy = pr >= 80
                return (
                  <button
                    key={p.id}
                    onClick={() => navigate(`/${p.orgSlug}/${p.slug}`)}
                    className="w-full text-left px-5 py-3 hover:bg-surface-muted transition-colors group"
                  >
                    <div className="flex items-center gap-3">
                      {/* Health dot */}
                      <div
                        className={`w-2 h-2 rounded-full shrink-0 ${healthy ? 'bg-success' : pr >= 50 ? 'bg-warning' : 'bg-danger'}`}
                      />

                      {/* Name + slug */}
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-fg truncate group-hover:text-primary">
                          {p.name}
                        </p>
                        <p className="text-[11px] text-fg-subtle font-mono">{p.slug}</p>
                      </div>

                      {/* Pass rate bar + value */}
                      <div className="flex items-center gap-2 shrink-0">
                        <PassBar pct={pr} />
                        <span
                          className="text-xs font-semibold tabular-nums w-12 text-right"
                          style={{ color: rateColor(pr) }}
                        >
                          {ps && ps.totalRuns > 0 ? formatPassRate(pr) : '—'}
                        </span>
                      </div>

                      {/* Runs */}
                      <div className="text-right shrink-0 w-14">
                        <p className="text-xs text-fg-muted tabular-nums">{ps?.totalRuns ?? 0} runs</p>
                        {(ps?.flakyTests ?? 0) > 0 && (
                          <p className="text-[10px] text-warning">{ps!.flakyTests} flaky</p>
                        )}
                      </div>

                      <ArrowRight
                        size={13}
                        className="text-fg-subtle group-hover:text-primary shrink-0"
                      />
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Recent alerts */}
        <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
          <div className="px-5 py-3.5 border-b border-border flex items-center justify-between">
            <h2 className="text-sm font-semibold text-fg flex items-center gap-2">
              <AlertTriangle size={14} className="text-fg-muted" /> Recent Alerts
            </h2>
            {orgAlerts.length > 0 && (
              <span className="text-xs text-fg-subtle">
                {orgAlerts.length} alert{orgAlerts.length !== 1 ? 's' : ''}
              </span>
            )}
          </div>

          {orgAlerts.length === 0 ? (
            <div className="flex flex-col items-center justify-center px-5 py-10 gap-2">
              <CheckCircle size={24} className="text-success" />
              <p className="text-sm text-fg-muted">No alerts — all projects healthy</p>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {orgAlerts.slice(0, 8).map((a: Alert) => {
                const proj = projects.find(p => p.id === a.projectId)
                return (
                  <div key={a.id} className="px-5 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-fg truncate">{a.ruleName}</p>
                        <p className="text-xs text-fg-muted truncate mt-0.5">{a.message}</p>
                        {proj && (
                          <button
                            onClick={() => navigate(`/${proj.orgSlug}/${proj.slug}`)}
                            className="text-[10px] text-primary hover:underline mt-0.5 font-mono"
                          >
                            {proj.name}
                          </button>
                        )}
                      </div>
                      <div className="flex flex-col items-end gap-1 shrink-0">
                        <StatusBadge variant={severityVariant(a.severity)}>{a.severity}</StatusBadge>
                        <p className="text-[10px] text-fg-subtle">{relativeTime(a.firedAt)}</p>
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
