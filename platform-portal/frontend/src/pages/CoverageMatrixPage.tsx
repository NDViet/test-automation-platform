import { useState } from 'react'
import { useProjectId, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Sparkles, ShieldCheck, AlertTriangle, Loader2, LayoutGrid, Users } from 'lucide-react'
import type { CoverageRow, CoverageGroup } from '@/lib/types'

function statusBadge(s: string | null) {
  if (!s) return <span className="text-xs text-slate-400">—</span>
  const up = s.toUpperCase()
  const cls =
    up === 'PASSED' || up === 'PASS'
      ? 'text-green-700 bg-green-100'
      : up === 'FAILED' || up === 'FAIL'
        ? 'text-red-700 bg-red-100'
        : up === 'BLOCKED'
          ? 'text-orange-700 bg-orange-100'
          : 'text-slate-600 bg-slate-100'
  return <span className={`text-xs px-1.5 py-0.5 rounded ${cls}`}>{up}</span>
}

function coverageState(row: CoverageRow): 'automated' | 'manual' | 'gap' {
  if (row.automatedCases > 0) return 'automated'
  if (row.manualCases > 0) return 'manual'
  return 'gap'
}
function shortPath(p: string | null): string {
  if (!p) return '(no area)'
  const x = p.split('\\')
  return x[x.length - 1]
}
function pctColorCls(p: number): string {
  return p >= 80 ? 'text-green-600' : p >= 50 ? 'text-amber-600' : 'text-red-600'
}

// Stacked coverage bar: automated (green) · manual (blue) · gap (red)
function CoverageBar({ g }: { g: CoverageGroup }) {
  const pct = (n: number) => (g.total ? `${((n / g.total) * 100).toFixed(1)}%` : '0%')
  return (
    <div className="flex h-2.5 rounded-full overflow-hidden bg-slate-100 w-full min-w-[140px]">
      <div
        className="bg-green-500"
        style={{ width: pct(g.coveredByAutomation) }}
        title={`${g.coveredByAutomation} automated`}
      />
      <div
        className="bg-blue-400"
        style={{ width: pct(g.manualOnly) }}
        title={`${g.manualOnly} manual`}
      />
      <div
        className="bg-red-300"
        style={{ width: pct(g.uncovered) }}
        title={`${g.uncovered} gaps`}
      />
    </div>
  )
}

export default function CoverageMatrixPage() {
  const projectId = useProjectId()
  const { filter } = useProjectFilter()
  const qc = useQueryClient()
  const [listMode, setListMode] = useState<'covered' | 'uncovered'>('covered')
  const [groupBy, setGroupBy] = useState<'area' | 'team'>('area')
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['coverage', projectId, filter.area, filter.teamId, filter.iteration],
    queryFn: () =>
      api.coverage(projectId!, {
        area: filter.area || undefined,
        team: filter.teamId || undefined,
        iteration: filter.iteration || undefined,
      }),
    enabled: !!projectId,
  })

  const generateMutation = useMutation({
    mutationFn: (reqId: string) => api.generateTestCasesFromAI(projectId!, [reqId]),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['coverage', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Computing coverage…" />
  if (error)
    return <ErrorMessage message="Failed to load coverage." onRetry={() => void refetch()} />
  if (!data) return null

  const groups = groupBy === 'area' ? data.byArea : data.byTeam
  const groupKey = (r: CoverageRow) =>
    groupBy === 'area' ? (r.areaPath ?? '(no area)') : (r.teamName ?? '(unassigned)')
  const labelFor = (g: CoverageGroup) => (groupBy === 'area' ? shortPath(g.label) : g.label)

  // Requirements list is shown only after an Area/Team is picked, then switched
  // between covered and uncovered (default covered).
  const rows = selectedGroup
    ? data.requirements
        .filter(r => groupKey(r) === selectedGroup)
        .filter(r =>
          listMode === 'uncovered' ? coverageState(r) === 'gap' : coverageState(r) !== 'gap',
        )
    : []

  const overallPctColor = pctColorCls(data.automationCoveragePct)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Requirements Coverage</h1>
        <p className="text-sm text-slate-500 mt-1">
          Coverage by Area / Team — track where it's improving and where the gaps are.
        </p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
          <p className="text-xs text-slate-500">Automation coverage</p>
          <p className={`text-3xl font-bold ${overallPctColor}`}>{data.automationCoveragePct}%</p>
          <p className="text-xs text-slate-400 mt-1">
            {data.coveredByAutomation}/{data.totalRequirements} requirements
          </p>
        </div>
        <StatCard label="Automated" value={data.coveredByAutomation} accent="text-green-600" />
        <StatCard label="Manual only" value={data.coveredManualOnly} accent="text-blue-600" />
        <StatCard label="Uncovered (gaps)" value={data.uncovered} accent="text-red-600" />
      </div>

      {/* Group-by rollup */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
          <h2 className="text-sm font-semibold text-slate-700">
            Coverage by {groupBy === 'area' ? 'Area' : 'Team'}
          </h2>
          <div className="flex gap-1 bg-slate-100 p-1 rounded-lg">
            <button
              onClick={() => {
                setGroupBy('area')
                setSelectedGroup(null)
              }}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-md',
                groupBy === 'area'
                  ? 'bg-white text-slate-900 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700',
              )}
            >
              <LayoutGrid size={13} /> Area
            </button>
            <button
              onClick={() => {
                setGroupBy('team')
                setSelectedGroup(null)
              }}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-md',
                groupBy === 'team'
                  ? 'bg-white text-slate-900 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700',
              )}
            >
              <Users size={13} /> Team
            </button>
          </div>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-slate-100">
              <th className="px-4 py-2.5">{groupBy === 'area' ? 'Area' : 'Team'}</th>
              <th className="px-4 py-2.5 w-56">Coverage</th>
              <th className="px-4 py-2.5 w-24 text-right">Covered</th>
              <th className="px-4 py-2.5 w-24 text-right">Automation</th>
              <th className="px-4 py-2.5 w-20 text-right">Gaps</th>
              <th className="px-4 py-2.5 w-16 text-right">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50">
            {groups.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                  No data.
                </td>
              </tr>
            )}
            {groups.map(g => {
              const active = selectedGroup === g.label
              return (
                <tr
                  key={g.label}
                  onClick={() => setSelectedGroup(active ? null : g.label)}
                  className={cn(
                    'cursor-pointer transition-colors',
                    active ? 'bg-blue-50' : 'hover:bg-slate-50',
                  )}
                >
                  <td
                    className="px-4 py-2.5 font-medium text-slate-800 max-w-xs truncate"
                    title={g.label}
                  >
                    {labelFor(g)}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center gap-2">
                      <CoverageBar g={g} />
                      <span
                        className={`text-xs font-semibold tabular-nums ${pctColorCls(g.coveragePct)}`}
                      >
                        {g.coveragePct}%
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">
                    {g.covered}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums">
                    <span className={pctColorCls(g.automationPct)}>{g.automationPct}%</span>
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums">
                    <span
                      className={g.uncovered > 0 ? 'text-red-600 font-medium' : 'text-slate-300'}
                    >
                      {g.uncovered}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-500">{g.total}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Requirements list — only after an Area/Team is selected */}
      {!selectedGroup ? (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm py-12 text-center">
          <p className="text-sm text-slate-500">
            Select an Area or Team above to see its requirements.
          </p>
          <p className="text-xs text-slate-400 mt-1">Then switch between covered and uncovered.</p>
        </div>
      ) : (
        <div>
          <div className="flex items-center gap-3 mb-2">
            <h2 className="text-sm font-semibold text-slate-700">Requirements</h2>
            <span className="text-xs px-2 py-0.5 rounded-full bg-blue-100 text-blue-700">
              {groupBy}: {groupBy === 'area' ? shortPath(selectedGroup) : selectedGroup}
              <button onClick={() => setSelectedGroup(null)} className="ml-1.5 hover:text-blue-900">
                ✕
              </button>
            </span>
            <div className="ml-auto flex gap-1 bg-slate-100 p-1 rounded-lg">
              <button
                onClick={() => setListMode('covered')}
                className={cn(
                  'px-3 py-1 text-xs font-medium rounded-md',
                  listMode === 'covered'
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700',
                )}
              >
                Covered
              </button>
              <button
                onClick={() => setListMode('uncovered')}
                className={cn(
                  'px-3 py-1 text-xs font-medium rounded-md',
                  listMode === 'uncovered'
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700',
                )}
              >
                Uncovered
              </button>
            </div>
            <span className="text-xs text-slate-400">{rows.length} shown</span>
          </div>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-slate-100">
                  <th className="px-4 py-3">Requirement</th>
                  <th className="px-4 py-3 w-40">Area / Team</th>
                  <th className="px-4 py-3 w-20 text-center">Auto</th>
                  <th className="px-4 py-3 w-20 text-center">Manual</th>
                  <th className="px-4 py-3 w-24">Last status</th>
                  <th className="px-4 py-3 w-28 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-10 text-center text-sm text-slate-500">
                      {listMode === 'uncovered'
                        ? 'No uncovered requirements 🎉'
                        : 'No covered requirements yet.'}
                    </td>
                  </tr>
                )}
                {rows.slice(0, 500).map(row => {
                  const state = coverageState(row)
                  return (
                    <tr key={row.requirementId} className={state === 'gap' ? 'bg-red-50/40' : ''}>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {state === 'automated' && (
                            <ShieldCheck size={14} className="text-green-500 shrink-0" />
                          )}
                          {state === 'gap' && (
                            <AlertTriangle size={14} className="text-red-500 shrink-0" />
                          )}
                          {row.externalId && (
                            <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 shrink-0">
                              {row.externalId}
                            </span>
                          )}
                          <span className="text-slate-800 truncate">{row.title}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div
                          className="text-xs text-slate-600 truncate"
                          title={`${row.areaPath ?? ''} · ${row.teamName ?? ''}`}
                        >
                          {shortPath(row.areaPath)}
                        </div>
                        <div className="text-[11px] text-slate-400 truncate">
                          {row.teamName ?? '(unassigned)'}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span
                          className={
                            row.automatedCases > 0 ? 'text-green-700 font-medium' : 'text-slate-300'
                          }
                        >
                          {row.automatedCases}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={row.manualCases > 0 ? 'text-blue-700' : 'text-slate-300'}>
                          {row.manualCases}
                        </span>
                      </td>
                      <td className="px-4 py-3">{statusBadge(row.lastStatus)}</td>
                      <td className="px-4 py-3 text-right">
                        {state === 'gap' && (
                          <button
                            onClick={() => generateMutation.mutate(row.requirementId)}
                            disabled={generateMutation.isPending}
                            className="inline-flex items-center gap-1 text-xs font-medium text-purple-700 bg-purple-50 border border-purple-200 rounded-lg px-2 py-1 hover:bg-purple-100 disabled:opacity-50"
                            title="Generate a test case for this requirement (AI)"
                          >
                            {generateMutation.isPending &&
                            generateMutation.variables === row.requirementId ? (
                              <Loader2 size={12} className="animate-spin" />
                            ) : (
                              <Sparkles size={12} />
                            )}
                            Generate test
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {rows.length > 500 && (
              <p className="px-4 py-2 text-xs text-slate-400 border-t border-slate-100">
                Showing first 500 of {rows.length}.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function StatCard({ label, value, accent }: { label: string; value: number; accent: string }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`text-3xl font-bold ${accent}`}>{value}</p>
    </div>
  )
}
