import { useState } from 'react'
import { useProjectId } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Sparkles, ShieldCheck, AlertTriangle, Loader2 } from 'lucide-react'
import type { CoverageRow } from '@/lib/types'

function statusBadge(s: string | null) {
  if (!s) return <span className="text-xs text-slate-400">—</span>
  const up = s.toUpperCase()
  const cls = up === 'PASSED' || up === 'PASS' ? 'text-green-700 bg-green-100'
    : up === 'FAILED' || up === 'FAIL' ? 'text-red-700 bg-red-100'
    : up === 'BLOCKED' ? 'text-orange-700 bg-orange-100'
    : 'text-slate-600 bg-slate-100'
  return <span className={`text-xs px-1.5 py-0.5 rounded ${cls}`}>{up}</span>
}

function coverageState(row: CoverageRow): 'automated' | 'manual' | 'gap' {
  if (row.automatedCases > 0) return 'automated'
  if (row.manualCases > 0) return 'manual'
  return 'gap'
}

export default function CoverageMatrixPage() {
  const projectId = useProjectId()
  const qc = useQueryClient()
  const [gapsOnly, setGapsOnly] = useState(false)

  const { data, isLoading, error } = useQuery({
    queryKey: ['coverage', projectId],
    queryFn: () => api.coverage(projectId!),
    enabled: !!projectId,
  })

  const generateMutation = useMutation({
    mutationFn: (reqId: string) => api.generateTestCasesFromAI(projectId!, [reqId]),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['coverage', projectId] }),
  })

  if (isLoading) return <LoadingSpinner message="Computing coverage…" />
  if (error) return <ErrorMessage message="Failed to load coverage." />
  if (!data) return null

  const rows = gapsOnly ? data.requirements.filter(r => coverageState(r) === 'gap') : data.requirements
  const pctColor = data.automationCoveragePct >= 80 ? 'text-green-600'
    : data.automationCoveragePct >= 60 ? 'text-amber-600' : 'text-red-600'

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Requirements Coverage</h1>
        <p className="text-sm text-slate-500 mt-1">
          Which requirements are covered by curated test cases — and where the gaps are.
        </p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
          <p className="text-xs text-slate-500">Automation coverage</p>
          <p className={`text-3xl font-bold ${pctColor}`}>{data.automationCoveragePct}%</p>
          <p className="text-xs text-slate-400 mt-1">{data.coveredByAutomation}/{data.totalRequirements} requirements</p>
        </div>
        <StatCard label="Automated" value={data.coveredByAutomation} accent="text-green-600" />
        <StatCard label="Manual only" value={data.coveredManualOnly} accent="text-blue-600" />
        <StatCard label="Uncovered (gaps)" value={data.uncovered} accent="text-red-600" />
      </div>

      {/* Controls */}
      <div className="flex items-center gap-3">
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={gapsOnly} onChange={e => setGapsOnly(e.target.checked)} />
          Show gaps only
        </label>
        <span className="text-xs text-slate-400">{rows.length} shown</span>
      </div>

      {/* Matrix */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-slate-100">
              <th className="px-4 py-3">Requirement</th>
              <th className="px-4 py-3 w-24 text-center">Automated</th>
              <th className="px-4 py-3 w-24 text-center">Manual</th>
              <th className="px-4 py-3 w-28">Last status</th>
              <th className="px-4 py-3 w-28 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50">
            {rows.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-500">
                {gapsOnly ? 'No coverage gaps 🎉' : 'No requirements found.'}
              </td></tr>
            )}
            {rows.map(row => {
              const state = coverageState(row)
              return (
                <tr key={row.requirementId} className={state === 'gap' ? 'bg-red-50/40' : ''}>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      {state === 'automated' && <ShieldCheck size={14} className="text-green-500 shrink-0" />}
                      {state === 'gap' && <AlertTriangle size={14} className="text-red-500 shrink-0" />}
                      {row.externalId && (
                        <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 shrink-0">
                          {row.externalId}
                        </span>
                      )}
                      <span className="text-slate-800 truncate">{row.title}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className={row.automatedCases > 0 ? 'text-green-700 font-medium' : 'text-slate-300'}>
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
                        {generateMutation.isPending && generateMutation.variables === row.requirementId
                          ? <Loader2 size={12} className="animate-spin" />
                          : <Sparkles size={12} />}
                        Generate test
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
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
