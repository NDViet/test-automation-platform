import { useState } from 'react'
import { useProjectId, useProjectFilter } from '@/components/layout/ProjectLayout'
import { usePageWidth } from '@/components/layout/PageWidth'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { PageHeader } from '@/components/ui'
import { Sparkles, ShieldCheck, AlertTriangle, Loader2, LayoutGrid, Users } from 'lucide-react'
import type { CoverageRow, CoverageGroup } from '@/lib/types'

function statusBadge(s: string | null) {
  if (!s) return <span className="text-xs text-fg-subtle">—</span>
  const up = s.toUpperCase()
  const cls =
    up === 'PASSED' || up === 'PASS'
      ? 'text-success bg-success-bg'
      : up === 'FAILED' || up === 'FAIL'
        ? 'text-danger bg-danger-bg'
        : up === 'BLOCKED'
          ? 'text-warning bg-warning-bg'
          : 'text-neutral bg-neutral-bg'
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
  return p >= 80 ? 'text-success' : p >= 50 ? 'text-warning' : 'text-danger'
}

// Stacked coverage bar: automated (success) · manual (info) · gap (danger)
function CoverageBar({ g }: { g: CoverageGroup }) {
  const pct = (n: number) => (g.total ? `${((n / g.total) * 100).toFixed(1)}%` : '0%')
  return (
    <div className="flex h-2.5 rounded-full overflow-hidden bg-surface-muted w-full min-w-[140px]">
      <div className="bg-success" style={{ width: pct(g.coveredByAutomation) }} title={`${g.coveredByAutomation} automated`} />
      <div className="bg-info" style={{ width: pct(g.manualOnly) }} title={`${g.manualOnly} manual`} />
      <div className="bg-danger" style={{ width: pct(g.uncovered) }} title={`${g.uncovered} gaps`} />
    </div>
  )
}

const seg = (active: boolean) =>
  cn(
    'flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-md transition-colors',
    active ? 'bg-surface text-fg shadow-xs' : 'text-fg-muted hover:text-fg',
  )

export default function CoverageMatrixPage() {
  usePageWidth('wide')
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
    mutationFn: (reqId: string) =>
      api.generateTestCasesFromAI(projectId!, { requirementIds: [reqId] }),
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
      <PageHeader
        title="Requirements Coverage"
        icon={<ShieldCheck size={20} />}
        description="Coverage by Area / Team — track where it's improving and where the gaps are."
      />

      {/* Summary cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-surface rounded-lg border border-border shadow-xs p-4">
          <p className="text-xs text-fg-muted">Automation coverage</p>
          <p className={`text-3xl font-bold ${overallPctColor}`}>{data.automationCoveragePct}%</p>
          <p className="text-xs text-fg-subtle mt-1">
            {data.coveredByAutomation}/{data.totalRequirements} requirements
          </p>
        </div>
        <StatCard label="Automated" value={data.coveredByAutomation} accent="text-success" />
        <StatCard label="Manual only" value={data.coveredManualOnly} accent="text-info" />
        <StatCard label="Uncovered (gaps)" value={data.uncovered} accent="text-danger" />
      </div>

      {/* Group-by rollup */}
      <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-border">
          <h2 className="text-sm font-semibold text-fg">
            Coverage by {groupBy === 'area' ? 'Area' : 'Team'}
          </h2>
          <div className="flex gap-1 bg-surface-muted p-1 rounded-lg">
            <button
              onClick={() => {
                setGroupBy('area')
                setSelectedGroup(null)
              }}
              className={seg(groupBy === 'area')}
            >
              <LayoutGrid size={13} /> Area
            </button>
            <button
              onClick={() => {
                setGroupBy('team')
                setSelectedGroup(null)
              }}
              className={seg(groupBy === 'team')}
            >
              <Users size={13} /> Team
            </button>
          </div>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold text-fg-muted uppercase tracking-wider border-b border-border">
              <th className="px-4 py-2.5">{groupBy === 'area' ? 'Area' : 'Team'}</th>
              <th className="px-4 py-2.5 w-56">Coverage</th>
              <th className="px-4 py-2.5 w-24 text-right">Covered</th>
              <th className="px-4 py-2.5 w-24 text-right">Automation</th>
              <th className="px-4 py-2.5 w-20 text-right">Gaps</th>
              <th className="px-4 py-2.5 w-16 text-right">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {groups.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-fg-muted">
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
                    active ? 'bg-primary-subtle' : 'hover:bg-surface-muted',
                  )}
                >
                  <td className="px-4 py-2.5 font-medium text-fg max-w-xs truncate" title={g.label}>
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
                  <td className="px-4 py-2.5 text-right tabular-nums text-fg">{g.covered}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums">
                    <span className={pctColorCls(g.automationPct)}>{g.automationPct}%</span>
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums">
                    <span className={g.uncovered > 0 ? 'text-danger font-medium' : 'text-fg-subtle'}>
                      {g.uncovered}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-fg-muted">{g.total}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Requirements list — only after an Area/Team is selected */}
      {!selectedGroup ? (
        <div className="bg-surface rounded-lg border border-border shadow-xs py-12 text-center">
          <p className="text-sm text-fg-muted">Select an Area or Team above to see its requirements.</p>
          <p className="text-xs text-fg-subtle mt-1">Then switch between covered and uncovered.</p>
        </div>
      ) : (
        <div>
          <div className="flex items-center gap-3 mb-2">
            <h2 className="text-sm font-semibold text-fg">Requirements</h2>
            <span className="text-xs px-2 py-0.5 rounded-full bg-primary-subtle text-primary-subtle-fg">
              {groupBy}: {groupBy === 'area' ? shortPath(selectedGroup) : selectedGroup}
              <button onClick={() => setSelectedGroup(null)} className="ml-1.5 hover:opacity-70">
                ✕
              </button>
            </span>
            <div className="ml-auto flex gap-1 bg-surface-muted p-1 rounded-lg">
              <button onClick={() => setListMode('covered')} className={seg(listMode === 'covered')}>
                Covered
              </button>
              <button
                onClick={() => setListMode('uncovered')}
                className={seg(listMode === 'uncovered')}
              >
                Uncovered
              </button>
            </div>
            <span className="text-xs text-fg-subtle">{rows.length} shown</span>
          </div>

          <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs font-semibold text-fg-muted uppercase tracking-wider border-b border-border">
                  <th className="px-4 py-3">Requirement</th>
                  <th className="px-4 py-3 w-40">Area / Team</th>
                  <th className="px-4 py-3 w-20 text-center">Auto</th>
                  <th className="px-4 py-3 w-20 text-center">Manual</th>
                  <th className="px-4 py-3 w-24">Last status</th>
                  <th className="px-4 py-3 w-28 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-10 text-center text-sm text-fg-muted">
                      {listMode === 'uncovered'
                        ? 'No uncovered requirements 🎉'
                        : 'No covered requirements yet.'}
                    </td>
                  </tr>
                )}
                {rows.slice(0, 500).map(row => {
                  const state = coverageState(row)
                  return (
                    <tr key={row.requirementId} className={state === 'gap' ? 'bg-danger-bg/40' : ''}>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {state === 'automated' && (
                            <ShieldCheck size={14} className="text-success shrink-0" />
                          )}
                          {state === 'gap' && (
                            <AlertTriangle size={14} className="text-danger shrink-0" />
                          )}
                          {row.externalId && (
                            <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-surface-muted text-fg-muted shrink-0">
                              {row.externalId}
                            </span>
                          )}
                          <span className="text-fg truncate">{row.title}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div
                          className="text-xs text-fg-muted truncate"
                          title={`${row.areaPath ?? ''} · ${row.teamName ?? ''}`}
                        >
                          {shortPath(row.areaPath)}
                        </div>
                        <div className="text-[11px] text-fg-subtle truncate">
                          {row.teamName ?? '(unassigned)'}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={row.automatedCases > 0 ? 'text-success font-medium' : 'text-fg-subtle'}>
                          {row.automatedCases}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={row.manualCases > 0 ? 'text-info' : 'text-fg-subtle'}>
                          {row.manualCases}
                        </span>
                      </td>
                      <td className="px-4 py-3">{statusBadge(row.lastStatus)}</td>
                      <td className="px-4 py-3 text-right">
                        {state === 'gap' && (
                          <button
                            onClick={() => generateMutation.mutate(row.requirementId)}
                            disabled={generateMutation.isPending}
                            className="inline-flex items-center gap-1 text-xs font-medium text-primary-subtle-fg bg-primary-subtle border border-primary-subtle rounded-md px-2 py-1 hover:brightness-95 disabled:opacity-50"
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
              <p className="px-4 py-2 text-xs text-fg-subtle border-t border-border">
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
    <div className="bg-surface rounded-lg border border-border shadow-xs p-4">
      <p className="text-xs text-fg-muted">{label}</p>
      <p className={`text-3xl font-bold ${accent}`}>{value}</p>
    </div>
  )
}
