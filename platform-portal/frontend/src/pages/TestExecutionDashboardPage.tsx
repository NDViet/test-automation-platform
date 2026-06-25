import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { ExecDimensionGroup, ReleaseCard } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { X, Rocket, CalendarRange, LayoutGrid, Users } from 'lucide-react'

type Dim = 'release' | 'sprint' | 'area' | 'team'

const DIMS: { key: Dim; label: string; icon: typeof Rocket }[] = [
  { key: 'release', label: 'Release', icon: Rocket },
  { key: 'sprint', label: 'Sprint', icon: CalendarRange },
  { key: 'area', label: 'Area', icon: LayoutGrid },
  { key: 'team', label: 'Team', icon: Users },
]

function rateColor(rate: number): string {
  return rate >= 90 ? '#16a34a' : rate >= 75 ? '#ca8a04' : '#dc2626'
}
function shortPath(p: string | null): string {
  if (!p) return ''
  const parts = p.split('\\')
  return parts[parts.length - 1]
}

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm px-4 py-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="text-xl font-bold text-slate-900 mt-0.5">{value}</p>
      {sub && <p className="text-xs text-slate-400 mt-0.5">{sub}</p>}
    </div>
  )
}

// ── Drill modal: runs for a dimension value ─────────────────────────────────────
function RunsModal({
  projectId,
  dim,
  value,
  title,
  subtitle,
  onClose,
}: {
  projectId: string
  dim: Dim
  value: string | null
  title: string
  subtitle: string
  onClose: () => void
}) {
  const navigate = useNavigate()
  const { base } = useProject()
  const { data: runs = [], isLoading } = useQuery({
    queryKey: ['execRuns', projectId, dim, value],
    queryFn: () => api.testExecutionRuns(projectId, dim, value),
  })
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 max-h-[85vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div>
            <h2 className="font-semibold text-slate-900">{title}</h2>
            <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {isLoading && <div className="py-10 text-center text-sm text-slate-400">Loading…</div>}
          {!isLoading && runs.length === 0 && (
            <div className="py-10 text-center text-sm text-slate-500">No runs.</div>
          )}
          {runs.length > 0 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                  <th className="px-4 py-2 font-medium">Run</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2 font-medium text-right">Pass/Total</th>
                  <th className="px-4 py-2 font-medium">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {runs.map(r => (
                  <tr
                    key={r.id}
                    className="hover:bg-slate-50 cursor-pointer"
                    onClick={() => navigate(`${base}/test-runs/${r.id}`)}
                  >
                    <td className="px-4 py-2 font-medium text-slate-800">{r.name}</td>
                    <td className="px-4 py-2 text-slate-600">{r.status.replace('_', ' ')}</td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      <span className="text-green-600 font-medium">{r.passed}</span>
                      <span className="text-slate-400"> / {r.total}</span>
                      {r.failed > 0 && <span className="text-red-600 ml-2">{r.failed}✕</span>}
                    </td>
                    <td className="px-4 py-2 text-slate-500">{relativeTime(r.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Release card ────────────────────────────────────────────────────────────────
function Card({ c, onOpen }: { c: ReleaseCard; onOpen: () => void }) {
  const executed = c.total - c.pending
  return (
    <button
      onClick={onOpen}
      className="text-left bg-white rounded-xl border border-slate-200 shadow-sm p-4 hover:border-blue-300 hover:shadow transition-all"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="font-semibold text-slate-900 truncate">{c.releaseName}</div>
          <div className="text-xs text-slate-400 mt-0.5">
            {c.iterationPath ? shortPath(c.iterationPath) : 'no sprint'}
            {c.areaPath ? ` · ${shortPath(c.areaPath)}` : ''}
          </div>
        </div>
        <span className="text-[10px] uppercase tracking-wide text-slate-400 shrink-0">
          {c.state.replace('_', ' ')}
        </span>
      </div>

      {/* pass-rate bar */}
      <div className="mt-3">
        <div className="flex justify-between text-xs mb-1">
          <span className="text-slate-500">Pass rate</span>
          <span className="font-semibold tabular-nums" style={{ color: rateColor(c.passRate) }}>
            {c.passRate}%
          </span>
        </div>
        <div className="flex h-2 rounded-full overflow-hidden bg-slate-100">
          {c.total > 0 && (
            <>
              <div className="bg-green-500" style={{ width: `${(c.passed / c.total) * 100}%` }} />
              <div className="bg-red-500" style={{ width: `${(c.failed / c.total) * 100}%` }} />
              <div className="bg-orange-400" style={{ width: `${(c.blocked / c.total) * 100}%` }} />
              <div className="bg-slate-300" style={{ width: `${(c.skipped / c.total) * 100}%` }} />
            </>
          )}
        </div>
      </div>

      {/* coverage */}
      <div className="mt-3">
        <div className="flex justify-between text-xs mb-1">
          <span className="text-slate-500">Coverage</span>
          <span className="font-semibold tabular-nums" style={{ color: rateColor(c.coveragePct) }}>
            {c.coveragePct}%{' '}
            <span className="text-slate-400 font-normal">
              ({c.coveredReqs}/{c.mappedReqs})
            </span>
          </span>
        </div>
        <div className="h-2 rounded-full overflow-hidden bg-slate-100">
          <div className="bg-blue-500 h-full" style={{ width: `${c.coveragePct}%` }} />
        </div>
      </div>

      <div className="mt-3 flex items-center gap-3 text-xs text-slate-400">
        <span>{c.runs} runs</span>
        <span>
          {executed}/{c.total} executed
        </span>
        {c.lastExecutedAt && <span className="ml-auto">{relativeTime(c.lastExecutedAt)}</span>}
      </div>
    </button>
  )
}

// ── Board view ──────────────────────────────────────────────────────────────────
function BoardView({ projectId }: { projectId: string }) {
  const { filter } = useProjectFilter() // project-wide Area / Team / Iteration scope
  const [drill, setDrill] = useState<ReleaseCard | null>(null)
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['execBoard', projectId, filter.area, filter.teamId, filter.iteration],
    queryFn: () =>
      api.testExecutionBoard(projectId, {
        iteration: filter.iteration || undefined,
        area: filter.area || undefined,
        team: filter.teamId || undefined,
      }),
  })

  if (isLoading) return <LoadingSpinner message="Loading…" />
  if (error)
    return <ErrorMessage message="Failed to load release board." onRetry={() => void refetch()} />
  if (!data) return null

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Kpi label="Releases" value={String(data.releaseCount)} />
        <Kpi label="Runs" value={String(data.runs)} />
        <Kpi label="Pass rate" value={`${data.passRate}%`} sub={`${data.passed} passed`} />
        <Kpi label="Coverage" value={`${data.coveragePct}%`} />
      </div>

      {data.groups.length === 0 && (
        <div className="bg-white rounded-xl border border-slate-200 py-16 text-center text-sm text-slate-500">
          No releases yet. Create releases (mapped to a sprint/team/area) to populate the board.
        </div>
      )}

      {data.groups.map(g => (
        <div key={g.teamId ?? 'none'}>
          <div className="flex items-center gap-2 mb-2">
            <Users size={14} className="text-slate-400" />
            <h3 className="text-sm font-semibold text-slate-700">{g.teamName}</h3>
            <span className="text-xs text-slate-400">{g.releases.length} releases</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {g.releases.map(c => (
              <Card key={c.releaseId} c={c} onOpen={() => setDrill(c)} />
            ))}
          </div>
        </div>
      ))}

      {drill && (
        <RunsModal
          projectId={projectId}
          dim="release"
          value={drill.releaseId}
          title={`Runs · ${drill.releaseName}`}
          subtitle={`${drill.runs} runs · ${drill.total} executions · coverage ${drill.coveragePct}%`}
          onClose={() => setDrill(null)}
        />
      )}
    </div>
  )
}

// ── Pivot view (flat dimension rollup) ──────────────────────────────────────────
function PivotView({ projectId }: { projectId: string }) {
  const { filter } = useProjectFilter() // project-wide Area / Team / Iteration scope
  const [dim, setDim] = useState<Dim>('sprint')
  const [drill, setDrill] = useState<ExecDimensionGroup | null>(null)
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['testExecution', projectId, dim, filter.area, filter.teamId, filter.iteration],
    queryFn: () =>
      api.testExecutionBy(projectId, dim, {
        area: filter.area || undefined,
        team: filter.teamId || undefined,
        iteration: filter.iteration || undefined,
      }),
  })
  return (
    <div className="space-y-4">
      <div className="flex gap-1 bg-slate-100 p-1 rounded-lg w-fit">
        {DIMS.map(d => {
          const Icon = d.icon
          return (
            <button
              key={d.key}
              onClick={() => setDim(d.key)}
              className={cn(
                'flex items-center gap-1.5 px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
                dim === d.key
                  ? 'bg-white text-slate-900 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700',
              )}
            >
              <Icon size={14} /> {d.label}
            </button>
          )
        })}
      </div>
      {isLoading && <LoadingSpinner message="Loading…" />}
      {error && <ErrorMessage message="Failed to load." onRetry={() => void refetch()} />}
      {data && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                <th className="px-4 py-2.5 font-medium">{DIMS.find(d => d.key === dim)?.label}</th>
                <th className="px-4 py-2.5 font-medium text-right">Runs</th>
                <th className="px-4 py-2.5 font-medium text-right">Total</th>
                <th className="px-4 py-2.5 font-medium text-right">Pass rate</th>
                <th className="px-4 py-2.5 font-medium">Last run</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {data.groups.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-slate-500">
                    No tagged runs.
                  </td>
                </tr>
              )}
              {data.groups.map(g => (
                <tr
                  key={g.key ?? '∅'}
                  className="hover:bg-slate-50 cursor-pointer"
                  onClick={() => setDrill(g)}
                >
                  <td
                    className="px-4 py-2.5 font-medium text-slate-800 max-w-xs truncate"
                    title={g.label}
                  >
                    {g.label}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-blue-600">{g.runs}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">{g.total}</td>
                  <td
                    className="px-4 py-2.5 text-right tabular-nums font-semibold"
                    style={{ color: rateColor(g.passRate) }}
                  >
                    {g.passRate}%
                  </td>
                  <td className="px-4 py-2.5 text-slate-500">
                    {g.lastExecutedAt ? relativeTime(g.lastExecutedAt) : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {drill && (
        <RunsModal
          projectId={projectId}
          dim={dim}
          value={drill.key}
          title={`Runs · ${drill.label}`}
          subtitle={`${dim} · ${drill.runs} runs · ${drill.total} executions`}
          onClose={() => setDrill(null)}
        />
      )}
    </div>
  )
}

export default function TestExecutionDashboardPage() {
  const { projectId } = useProject()
  const [view, setView] = useState<'board' | 'pivot'>('board')

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Test Execution</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Release readiness by team, and flat rollups by dimension.
          </p>
        </div>
        <div className="flex gap-1 bg-slate-100 p-1 rounded-lg">
          <button
            onClick={() => setView('board')}
            className={cn(
              'px-4 py-1.5 text-sm font-medium rounded-md',
              view === 'board'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-500 hover:text-slate-700',
            )}
          >
            Release board
          </button>
          <button
            onClick={() => setView('pivot')}
            className={cn(
              'px-4 py-1.5 text-sm font-medium rounded-md',
              view === 'pivot'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-500 hover:text-slate-700',
            )}
          >
            Pivot
          </button>
        </div>
      </div>

      {projectId &&
        (view === 'board' ? (
          <BoardView projectId={projectId} />
        ) : (
          <PivotView projectId={projectId} />
        ))}
    </div>
  )
}
