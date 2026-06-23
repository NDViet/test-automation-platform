import { Link } from 'react-router-dom'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime, pathLeaf } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import {
  FileText, ShieldCheck, Bug, Gauge, Rocket, ChevronRight, ArrowRight,
  MonitorCheck, CheckCircle, XCircle, AlertTriangle, GitBranch,
} from 'lucide-react'
import type { ExecutionSummary } from '@/lib/types'

function rateColor(p: number): string {
  return p >= 80 ? '#16a34a' : p >= 50 ? '#ca8a04' : '#dc2626'
}

function Kpi({ label, value, sub, accent, to }: {
  label: string; value: string; sub?: string; accent?: string; to?: string
}) {
  const body = (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 h-full hover:border-blue-300 transition-colors">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="text-2xl font-bold mt-0.5" style={accent ? { color: accent } : undefined}>{value}</p>
      {sub && <p className="text-xs text-slate-400 mt-0.5">{sub}</p>}
    </div>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

function Bar({ pct, color }: { pct: number; color: string }) {
  return (
    <div className="h-2 rounded-full bg-slate-100 overflow-hidden min-w-[80px]">
      <div className="h-full" style={{ width: `${Math.max(0, Math.min(100, pct))}%`, background: color }} />
    </div>
  )
}

function SectionHeader({ title, to, linkLabel }: { title: string; to: string; linkLabel: string }) {
  return (
    <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
      <h2 className="text-sm font-semibold text-slate-700">{title}</h2>
      <Link to={to} className="text-xs text-blue-600 hover:text-blue-700 flex items-center gap-1">
        {linkLabel} <ArrowRight size={12} />
      </Link>
    </div>
  )
}

// Mini sparkline of pass rate over the last N trend points
function PassRateSparkline({ points }: { points: { passRate: number }[] }) {
  if (points.length < 2) return null
  const W = 80, H = 28
  const rates = points.map(p => p.passRate)
  const min = Math.min(...rates), max = Math.max(...rates)
  const range = max - min || 1
  const xs = rates.map((_, i) => (i / (rates.length - 1)) * W)
  const ys = rates.map(r => H - ((r - min) / range) * (H - 4) - 2)
  const d = xs.map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${ys[i].toFixed(1)}`).join(' ')
  const lastColor = rateColor(rates[rates.length - 1])
  return (
    <svg width={W} height={H} className="shrink-0">
      <path d={d} fill="none" stroke={lastColor} strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" opacity="0.7" />
    </svg>
  )
}

function execPct(e: ExecutionSummary): number {
  return e.totalTests > 0 ? (e.passed / e.totalTests) * 100 : 0
}

function ExecutionRow({ exec, base }: { exec: ExecutionSummary; base: string }) {
  const pr = execPct(exec)
  const failed = exec.failed + (exec.broken ?? 0)
  return (
    <Link
      to={`${base}/automated-tests`}
      className="flex items-center gap-3 px-5 py-2.5 hover:bg-slate-50 transition-colors"
    >
      <div className="shrink-0">
        {pr >= 80
          ? <CheckCircle size={14} className="text-green-500" />
          : pr >= 50
            ? <AlertTriangle size={14} className="text-yellow-500" />
            : <XCircle size={14} className="text-red-500" />
        }
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-slate-800 truncate">{exec.suiteName || exec.sourceFormat}</p>
        <div className="flex items-center gap-2 mt-0.5">
          <GitBranch size={10} className="text-slate-400 shrink-0" />
          <span className="text-[11px] text-slate-400 font-mono truncate">{exec.branch || '—'}</span>
          <span className="text-[11px] text-slate-400">·</span>
          <span className="text-[11px] text-slate-400">{relativeTime(exec.executedAt)}</span>
        </div>
      </div>
      <div className="shrink-0 text-right">
        <p className="text-sm font-semibold tabular-nums" style={{ color: rateColor(pr) }}>{pr.toFixed(1)}%</p>
        <p className="text-[11px] text-slate-400 tabular-nums">{exec.totalTests} tests · {failed} fail</p>
      </div>
    </Link>
  )
}

export default function ProjectDetail() {
  const { project, projectId, base } = useProject()
  const { filter, active } = useProjectFilter()
  const scope = { area: filter.area || undefined, team: filter.teamId || undefined, iteration: filter.iteration || undefined }

  // Scoped quality signals
  const covQ = useQuery({
    queryKey: ['coverage', projectId, filter.area, filter.teamId, filter.iteration],
    queryFn: () => api.coverage(projectId!, scope), enabled: !!projectId,
  })
  const boardQ = useQuery({
    queryKey: ['execBoard', projectId, filter.area, filter.teamId, filter.iteration],
    queryFn: () => api.testExecutionBoard(projectId!, { area: filter.area || undefined, team: filter.teamId || undefined, iteration: filter.iteration || undefined }),
    enabled: !!projectId,
  })
  // Project-wide quality signals
  const qualQ  = useQuery({ queryKey: ['qualityOverview',    projectId], queryFn: () => api.qualityOverview(projectId!),    enabled: !!projectId })
  const prodQ  = useQuery({ queryKey: ['productivityByArea', projectId], queryFn: () => api.productivityByArea(projectId!), enabled: !!projectId })
  // Test automation signals
  const detailQ = useQuery({ queryKey: ['project', projectId], queryFn: () => api.projectDetail(projectId!), enabled: !!projectId })

  if (covQ.isLoading) return <LoadingSpinner message="Loading project quality…" />
  if (covQ.error || !covQ.data) return <ErrorMessage message="Failed to load project quality." />

  const cov   = covQ.data
  const board = boardQ.data
  const qual  = qualQ.data
  const prod  = prodQ.data

  const detail         = detailQ.data
  const recentExecs    = detail?.recentExecutions ?? []
  const trendPoints    = detail?.passRateTrend    ?? []
  const qualityGate    = detail?.qualityGate
  const flakyItems     = detail?.flakiness        ?? []

  const flakyCount     = flakyItems.filter(f => f.classification === 'FLAKY' || f.classification === 'CRITICAL_FLAKY').length
  const avgAutoPass    = recentExecs.length
    ? recentExecs.reduce((s, e) => s + execPct(e), 0) / recentExecs.length
    : null
  const totalAutoTests = recentExecs.reduce((s, e) => s + (e.totalTests ?? 0), 0)
  const totalFailed    = recentExecs.reduce((s, e) => s + (e.failed ?? 0) + (e.broken ?? 0), 0)

  const covered    = cov.coveredByAutomation + cov.coveredManualOnly
  const coveredPct = cov.totalRequirements ? Math.round(covered * 1000 / cov.totalRequirements) / 10 : 0

  // Flatten release board → release rows, worst coverage first
  const releases = (board?.groups ?? []).flatMap(g => g.releases.map(r => ({ ...r, teamName: g.teamName })))
    .sort((a, b) => a.coveragePct - b.coveragePct)

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
            <Link to={`/${project.orgSlug}`} className="hover:text-blue-600">Overview</Link>
            <ChevronRight size={14} /><span>{project?.orgName ?? ''}</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900">{project?.name ?? projectId}</h1>
          <p className="text-sm text-slate-500 mt-1">Project quality at a glance{active ? ' · scoped' : ''}</p>
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <Kpi label="Requirements" value={String(cov.totalRequirements)}
             sub={`${cov.uncovered} uncovered`} to={`${base}/requirements`} />
        <Kpi label="Test coverage" value={`${coveredPct}%`} accent={rateColor(coveredPct)}
             sub={`${cov.automationCoveragePct}% automated`} to={`${base}/coverage`} />
        <Kpi label="Release pass rate" value={board ? `${board.passRate}%` : '—'} accent={board ? rateColor(board.passRate) : undefined}
             sub={board ? `${board.runs} runs · ${board.releaseCount} releases` : ''} to={`${base}/test-execution`} />
        <Kpi label="Open defects" value={qual ? String(qual.openDefects) : '—'}
             accent={qual && qual.openDefects > 0 ? '#dc2626' : '#16a34a'}
             sub={qual ? `${qual.createdLast30} new · ${qual.resolvedLast30} resolved (30d)` : ''} to={`${base}/quality`} />
        <Kpi label={`Over SLA (${prod?.thresholdHours ?? 24}h)`} value={prod ? String(prod.totalOver) : '—'}
             accent={prod && prod.totalOver > 0 ? '#ca8a04' : '#16a34a'}
             sub={prod ? `${prod.totalWip} in progress` : ''} to={`${base}/productivity`} />
      </div>

      {/* ── Test Automation ── */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <SectionHeader title="Test Automation" to={`${base}/automated-tests`} linkLabel="Automated Tests" />

        {/* Automation KPIs */}
        <div className="grid grid-cols-2 md:grid-cols-4 divide-x divide-y md:divide-y-0 divide-slate-100 border-b border-slate-100">
          {/* Pass rate + sparkline */}
          <div className="px-5 py-4 flex items-center justify-between gap-3">
            <div>
              <p className="text-xs text-slate-500">Avg pass rate</p>
              <p className="text-2xl font-bold mt-0.5" style={{ color: avgAutoPass != null ? rateColor(avgAutoPass) : undefined }}>
                {avgAutoPass != null ? `${avgAutoPass.toFixed(1)}%` : '—'}
              </p>
              <p className="text-[11px] text-slate-400 mt-0.5">{recentExecs.length} recent runs</p>
            </div>
            {trendPoints.length >= 2 && <PassRateSparkline points={trendPoints.slice(-14)} />}
          </div>

          {/* Total tests */}
          <div className="px-5 py-4">
            <p className="text-xs text-slate-500">Tests executed</p>
            <p className="text-2xl font-bold text-slate-800 mt-0.5">{totalAutoTests.toLocaleString()}</p>
            <p className="text-[11px] text-slate-400 mt-0.5">
              <span className="text-red-500">{totalFailed} failed</span>
              {' · '}
              <span className="text-green-500">{(totalAutoTests - totalFailed).toLocaleString()} passed</span>
            </p>
          </div>

          {/* Flaky tests */}
          <Link to={`${base}/flaky-tests`} className="px-5 py-4 hover:bg-slate-50 transition-colors block">
            <p className="text-xs text-slate-500">Flaky tests</p>
            <p className="text-2xl font-bold mt-0.5" style={{ color: flakyCount > 0 ? '#ca8a04' : '#16a34a' }}>
              {flakyCount}
            </p>
            <p className="text-[11px] text-slate-400 mt-0.5">
              {flakyItems.filter(f => f.classification === 'CRITICAL_FLAKY').length} critical
            </p>
          </Link>

          {/* Quality gate */}
          <div className="px-5 py-4">
            <p className="text-xs text-slate-500">Quality gate</p>
            {qualityGate ? (
              <>
                <div className="flex items-center gap-1.5 mt-0.5">
                  {qualityGate.passed
                    ? <CheckCircle size={18} className="text-green-500 shrink-0" />
                    : <XCircle    size={18} className="text-red-500 shrink-0" />
                  }
                  <p className="text-xl font-bold" style={{ color: qualityGate.passed ? '#16a34a' : '#dc2626' }}>
                    {qualityGate.passed ? 'PASS' : 'FAIL'}
                  </p>
                </div>
                <p className="text-[11px] text-slate-400 mt-0.5">
                  {qualityGate.actualPassRate.toFixed(1)}% pass rate · {qualityGate.newFailures} new failures
                </p>
              </>
            ) : (
              <p className="text-xl font-bold text-slate-300 mt-0.5">—</p>
            )}
          </div>
        </div>

        {/* Recent executions */}
        {recentExecs.length === 0 ? (
          <div className="flex items-center gap-3 px-5 py-8 text-sm text-slate-500">
            <MonitorCheck size={18} className="text-slate-300" />
            No automated executions yet — push results via the ingestion API.
          </div>
        ) : (
          <div className="divide-y divide-slate-50">
            {recentExecs.slice(0, 6).map(e => (
              <ExecutionRow key={e.id} exec={e} base={base} />
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Release readiness */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <SectionHeader title="Release readiness" to={`${base}/test-execution`} linkLabel="Test Execution" />
          {releases.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">No releases with runs in scope.</p>
          ) : (
            <table className="w-full text-sm">
              <thead><tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                <th className="px-5 py-2 font-medium">Release</th>
                <th className="px-3 py-2 font-medium w-28">Pass</th>
                <th className="px-3 py-2 font-medium w-28">Coverage</th>
              </tr></thead>
              <tbody className="divide-y divide-slate-50">
                {releases.slice(0, 6).map(r => (
                  <tr key={r.releaseId} className="hover:bg-slate-50">
                    <td className="px-5 py-2">
                      <div className="font-medium text-slate-800 truncate">{r.releaseName}</div>
                      <div className="text-xs text-slate-400 truncate">{r.teamName}{r.iterationPath ? ` · ${pathLeaf(r.iterationPath)}` : ''}</div>
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-1.5"><Bar pct={r.passRate} color={rateColor(r.passRate)} />
                        <span className="text-xs tabular-nums" style={{ color: rateColor(r.passRate) }}>{r.passRate}%</span></div>
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-1.5"><Bar pct={r.coveragePct} color="#3b82f6" />
                        <span className="text-xs tabular-nums text-slate-500">{r.coveragePct}%</span></div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Coverage gaps by area */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <SectionHeader title="Coverage by area (lowest first)" to={`${base}/coverage`} linkLabel="Coverage" />
          {cov.byArea.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">No requirements in scope.</p>
          ) : (
            <table className="w-full text-sm">
              <thead><tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                <th className="px-5 py-2 font-medium">Area</th>
                <th className="px-3 py-2 font-medium w-32">Coverage</th>
                <th className="px-3 py-2 font-medium text-right w-16">Gaps</th>
              </tr></thead>
              <tbody className="divide-y divide-slate-50">
                {cov.byArea.slice(0, 6).map(a => (
                  <tr key={a.label} className="hover:bg-slate-50">
                    <td className="px-5 py-2 font-medium text-slate-800 max-w-[14rem] truncate" title={a.label}>{pathLeaf(a.label)}</td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-1.5"><Bar pct={a.coveragePct} color={rateColor(a.coveragePct)} />
                        <span className="text-xs tabular-nums" style={{ color: rateColor(a.coveragePct) }}>{a.coveragePct}%</span></div>
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums text-red-600">{a.uncovered}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Defects */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <SectionHeader title="Defects" to={`${base}/quality`} linkLabel="Quality" />
          {!qual ? <p className="px-5 py-10 text-center text-sm text-slate-500">No defect data.</p> : (
            <div className="p-5 space-y-4">
              <div className="grid grid-cols-3 gap-3">
                <div><p className="text-xs text-slate-500">Open</p><p className="text-xl font-bold text-red-600">{qual.openDefects}</p></div>
                <div><p className="text-xs text-slate-500">Blocked</p><p className="text-xl font-bold text-orange-600">{qual.blockedDefects}</p></div>
                <div><p className="text-xs text-slate-500">Done</p><p className="text-xl font-bold text-green-600">{qual.doneDefects}</p></div>
              </div>
              {qual.bySeverity.length > 0 && (
                <div>
                  <p className="text-xs text-slate-500 mb-1.5">By severity</p>
                  <div className="flex flex-wrap gap-2">
                    {qual.bySeverity.map(s => (
                      <span key={s.label} className="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-600">{s.label}: <b>{s.value}</b></span>
                    ))}
                  </div>
                </div>
              )}
              <p className="text-xs text-slate-400">{qual.totalDefects} total · {qual.qualityEngineers} quality engineers</p>
            </div>
          )}
        </div>
      </div>

      {/* Quick links */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        {[
          { to: `${base}/requirements`,   label: 'Requirements',  icon: FileText },
          { to: `${base}/coverage`,       label: 'Coverage',      icon: ShieldCheck },
          { to: `${base}/test-execution`, label: 'Test Execution', icon: Rocket },
          { to: `${base}/quality`,        label: 'Quality',       icon: Bug },
          { to: `${base}/productivity`,   label: 'Productivity',  icon: Gauge },
        ].map(l => {
          const Icon = l.icon
          return (
            <Link key={l.to} to={l.to}
              className="flex items-center gap-2 px-3 py-2.5 bg-white rounded-xl border border-slate-200 shadow-sm text-sm text-slate-600 hover:border-blue-300 hover:text-blue-700 transition-colors">
              <Icon size={15} /> {l.label}
            </Link>
          )
        })}
      </div>
    </div>
  )
}
