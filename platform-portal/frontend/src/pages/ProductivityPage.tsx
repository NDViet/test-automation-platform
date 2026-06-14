import { useState, useEffect } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Gauge, Timer, AlertTriangle, Layers, X, ExternalLink, Loader2, Check, CheckCircle2, Hourglass } from 'lucide-react'
import type { AreaProductivity, OverThresholdItem, LeadAreaStat, LeadItem } from '@/lib/types'

function leaf(path: string) { const i = path.lastIndexOf('\\'); return i >= 0 ? path.slice(i + 1) : path }
function fmtH(h: number | null) { return h == null ? '—' : `${h.toFixed(1)}h` }
function fmtDate(iso: string | null) { return iso ? new Date(iso).toLocaleDateString() : '—' }
function statusBadge(s: string) {
  return s === 'OPEN' ? 'text-blue-700 bg-blue-100' : s === 'IN_PROGRESS' ? 'text-yellow-700 bg-yellow-100'
    : s === 'BLOCKED' ? 'text-red-700 bg-red-100' : s === 'DONE' ? 'text-green-700 bg-green-100' : 'text-slate-600 bg-slate-100'
}

// Drill target: either started-WIP items, or completed lead-time items.
type Drill =
  | { kind: 'wip'; area: string | undefined; over: boolean; title: string }
  | { kind: 'lead'; area: string | undefined; title: string }

export default function ProductivityPage() {
  const { projectId } = useProject()
  const qc = useQueryClient()
  const [drill, setDrill] = useState<Drill | null>(null)

  const cycleQ = useQuery({ queryKey: ['productivity-by-area', projectId], queryFn: () => api.productivityByArea(projectId) })
  const leadQ  = useQuery({ queryKey: ['productivity-lead', projectId], queryFn: () => api.productivityLeadByArea(projectId) })

  if (cycleQ.isLoading) return <LoadingSpinner message="Computing cycle times…" />
  if (cycleQ.error || !cycleQ.data) return <ErrorMessage message="Failed to load productivity metrics." />
  const c = cycleQ.data
  const lead = leadQ.data
  const pctOver = c.totalWip > 0 ? Math.round((c.totalOver / c.totalWip) * 100) : 0

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-3">
            <Gauge size={20} className="text-slate-400" />
            <h1 className="text-2xl font-bold text-slate-900">Productivity</h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">Cycle time (work in progress) and lead time (created → completed), by area.</p>
        </div>
        <ThresholdEditor projectId={projectId} current={c.thresholdHours}
          onSaved={() => qc.invalidateQueries({ queryKey: ['productivity-by-area', projectId] })} />
      </div>

      {/* ── Cycle time ─────────────────────────────────────────────── */}
      <SectionTitle icon={Timer} title="Cycle time" subtitle="Running age of started-but-not-completed work" />
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Kpi icon={Timer} label="In progress (started)" value={c.totalWip} tint="text-slate-700"
          onClick={() => setDrill({ kind: 'wip', area: undefined, over: false, title: 'In progress' })} />
        <Kpi icon={AlertTriangle} label={`Over ${fmtH(c.thresholdHours)} target`} value={c.totalOver} tint="text-red-600"
          onClick={() => setDrill({ kind: 'wip', area: undefined, over: true, title: `Over ${fmtH(c.thresholdHours)} target` })} />
        <Kpi icon={Gauge} label="% over target" value={`${pctOver}%`} tint={pctOver > 30 ? 'text-red-600' : 'text-amber-600'} />
        <Kpi icon={Layers} label="Areas needing action" value={c.areasAffected} tint="text-slate-700" />
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        <div className="flex items-center gap-2 px-5 py-3.5 border-b border-slate-100">
          <Layers size={16} className="text-slate-400" />
          <h2 className="text-sm font-semibold text-slate-700">Cycle time by Area</h2>
          <span className="text-xs text-slate-400">target {fmtH(c.thresholdHours)}</span>
        </div>
        {c.areas.length === 0 ? (
          <Empty text="No started work in progress. (Cycle time needs work-item history — run Sync work-item history on the Quality page.)" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                  <th className="px-5 py-2 font-medium">Area</th>
                  <th className="px-3 py-2 font-medium text-right">In progress</th>
                  <th className="px-3 py-2 font-medium text-right">Over target</th>
                  <th className="px-3 py-2 font-medium text-right">% over</th>
                  <th className="px-3 py-2 font-medium text-right">Avg age</th>
                  <th className="px-5 py-2 font-medium text-right">Max age</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {c.areas.map((a: AreaProductivity) => {
                  const pct = a.wip > 0 ? Math.round((a.overThreshold / a.wip) * 100) : 0
                  return (
                    <tr key={a.area} className="hover:bg-slate-50">
                      <td className="px-5 py-2.5 text-slate-700" title={a.area}>{leaf(a.area)}
                        <span className="text-xs text-slate-400 font-mono ml-2 hidden lg:inline">{a.area}</span>
                      </td>
                      <NumCell value={a.wip} onClick={() => setDrill({ kind: 'wip', area: a.area, over: false, title: `In progress · ${leaf(a.area)}` })} />
                      <NumCell value={a.overThreshold} danger onClick={() => setDrill({ kind: 'wip', area: a.area, over: true, title: `Over target · ${leaf(a.area)}` })} />
                      <td className="px-3 py-2.5 text-right tabular-nums">
                        <span className={cn('px-1.5 py-0.5 rounded text-xs', pct > 50 ? 'bg-red-100 text-red-700' : pct > 0 ? 'bg-amber-100 text-amber-700' : 'text-slate-400')}>{pct}%</span>
                      </td>
                      <td className="px-3 py-2.5 text-right tabular-nums text-slate-600">{fmtH(a.avgHours)}</td>
                      <td className="px-5 py-2.5 text-right tabular-nums text-slate-600">{fmtH(a.maxHours)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Lead time ──────────────────────────────────────────────── */}
      <SectionTitle icon={Hourglass} title="Lead time" subtitle="From work-item creation until completion" />
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Kpi icon={CheckCircle2} label="Completed" value={lead?.totalCompleted ?? 0} tint="text-green-600"
          onClick={() => setDrill({ kind: 'lead', area: undefined, title: 'Completed (lead time)' })} />
        <Kpi icon={Hourglass} label="Avg lead time" value={fmtH(lead?.avgHours ?? null)} tint="text-slate-700" />
        <Kpi icon={Hourglass} label="Max lead time" value={fmtH(lead?.maxHours ?? null)} tint="text-amber-600" />
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        <div className="flex items-center gap-2 px-5 py-3.5 border-b border-slate-100">
          <Layers size={16} className="text-slate-400" />
          <h2 className="text-sm font-semibold text-slate-700">Lead time by Area</h2>
        </div>
        {leadQ.isLoading ? <LoadingSpinner /> : !lead || lead.areas.length === 0 ? (
          <Empty text="No completed items with history. (Lead time needs work-item history — run Sync work-item history on the Quality page.)" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                  <th className="px-5 py-2 font-medium">Area</th>
                  <th className="px-3 py-2 font-medium text-right">Completed</th>
                  <th className="px-3 py-2 font-medium text-right">Avg lead</th>
                  <th className="px-5 py-2 font-medium text-right">Max lead</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {lead.areas.map((a: LeadAreaStat) => (
                  <tr key={a.area} className="hover:bg-slate-50">
                    <td className="px-5 py-2.5 text-slate-700" title={a.area}>{leaf(a.area)}
                      <span className="text-xs text-slate-400 font-mono ml-2 hidden lg:inline">{a.area}</span>
                    </td>
                    <NumCell value={a.completed} accent onClick={() => setDrill({ kind: 'lead', area: a.area, title: `Completed · ${leaf(a.area)}` })} />
                    <td className="px-3 py-2.5 text-right tabular-nums text-slate-600">{fmtH(a.avgHours)}</td>
                    <td className="px-5 py-2.5 text-right tabular-nums text-slate-600">{fmtH(a.maxHours)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {drill && <DrillModal projectId={projectId} drill={drill} onClose={() => setDrill(null)} />}
    </div>
  )
}

function SectionTitle({ icon: Icon, title, subtitle }: { icon: typeof Timer; title: string; subtitle: string }) {
  return (
    <div className="flex items-baseline gap-2 mt-1">
      <Icon size={16} className="text-slate-400 self-center" />
      <h2 className="text-base font-semibold text-slate-800">{title}</h2>
      <span className="text-xs text-slate-400">{subtitle}</span>
    </div>
  )
}

function Kpi({ icon: Icon, label, value, tint, onClick }: { icon: typeof Timer; label: string; value: number | string; tint: string; onClick?: () => void }) {
  const clickable = !!onClick && value !== 0 && value !== '0'
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm px-4 py-3">
      <div className="flex items-center gap-1.5 text-xs text-slate-500 mb-1"><Icon size={13} /> {label}</div>
      {clickable
        ? <button onClick={onClick} className={`text-2xl font-bold ${tint} hover:underline decoration-dotted underline-offset-4`}>{value}</button>
        : <div className={`text-2xl font-bold ${tint}`}>{value}</div>}
    </div>
  )
}

function NumCell({ value, onClick, danger, accent }: { value: number; onClick: () => void; danger?: boolean; accent?: boolean }) {
  const cls = danger ? 'font-medium text-red-600' : accent ? 'font-medium text-green-600' : 'text-slate-700'
  return (
    <td className="px-3 py-2.5 text-right tabular-nums">
      {value > 0
        ? <button onClick={onClick} className={`${cls} hover:underline decoration-dotted underline-offset-2`}>{value}</button>
        : <span className="text-slate-300">0</span>}
    </td>
  )
}

function DrillModal({ projectId, drill, onClose }: { projectId: string; drill: Drill; onClose: () => void }) {
  const isLead = drill.kind === 'lead'
  const wipQ = useQuery({
    queryKey: ['prod-wip', projectId, drill.area, drill.kind === 'wip' ? drill.over : null],
    queryFn: () => api.productivityWipItems(projectId, drill.area, drill.kind === 'wip' ? drill.over : true),
    enabled: drill.kind === 'wip',
  })
  const leadQ = useQuery({
    queryKey: ['prod-lead-items', projectId, drill.area],
    queryFn: () => api.productivityLeadItems(projectId, drill.area),
    enabled: isLead,
  })
  const q = isLead ? leadQ : wipQ
  const items = (q.data ?? []) as (OverThresholdItem[] | LeadItem[])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-xl w-full max-w-3xl mx-4 max-h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-200">
          <h2 className="text-sm font-semibold text-slate-800">{drill.title} {q.data && <span className="text-slate-400 font-normal">({items.length})</span>}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>
        <div className="overflow-y-auto flex-1">
          {q.isLoading ? <LoadingSpinner /> : q.error ? <ErrorMessage message="Failed to load." /> :
           items.length === 0 ? <Empty text="No items." /> : (
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-white">
                <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                  <th className="px-5 py-2 font-medium">Item</th>
                  <th className="px-3 py-2 font-medium">Type</th>
                  <th className="px-3 py-2 font-medium">Assignee</th>
                  {isLead
                    ? <><th className="px-3 py-2 font-medium">Created</th><th className="px-3 py-2 font-medium">Completed</th><th className="px-3 py-2 font-medium text-right">Lead</th></>
                    : <><th className="px-3 py-2 font-medium">Status</th><th className="px-3 py-2 font-medium text-right">Age</th></>}
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {isLead
                  ? (items as LeadItem[]).map(w => (
                    <tr key={w.id} className="hover:bg-slate-50 align-top">
                      <td className="px-5 py-2"><div className="text-slate-800">{w.title}</div>{w.externalId && <span className="text-xs font-mono text-slate-400">#{w.externalId}</span>}</td>
                      <td className="px-3 py-2"><Badge label={w.issueType} colorClass="text-slate-600 bg-slate-100" /></td>
                      <td className="px-3 py-2 text-xs text-slate-500">{w.assignedTo ?? '—'}</td>
                      <td className="px-3 py-2 text-xs text-slate-500">{fmtDate(w.createdDate)}</td>
                      <td className="px-3 py-2 text-xs text-slate-500">{fmtDate(w.completedAt)}</td>
                      <td className="px-3 py-2 text-right tabular-nums font-medium text-slate-700">{fmtH(w.leadHours)}</td>
                      <td className="px-3 py-2 text-right">{w.sourceUrl && <a href={w.sourceUrl} target="_blank" rel="noreferrer" className="text-slate-300 hover:text-blue-600 inline-block"><ExternalLink size={13} /></a>}</td>
                    </tr>
                  ))
                  : (items as OverThresholdItem[]).map(w => (
                    <tr key={w.id} className="hover:bg-slate-50 align-top">
                      <td className="px-5 py-2"><div className="text-slate-800">{w.title}</div>{w.externalId && <span className="text-xs font-mono text-slate-400">#{w.externalId}</span>}</td>
                      <td className="px-3 py-2"><Badge label={w.issueType} colorClass="text-slate-600 bg-slate-100" /></td>
                      <td className="px-3 py-2 text-xs text-slate-500">{w.assignedTo ?? '—'}</td>
                      <td className="px-3 py-2"><Badge label={w.status} colorClass={statusBadge(w.status)} /></td>
                      <td className="px-3 py-2 text-right tabular-nums font-medium text-red-600">{fmtH(w.cycleHours)}</td>
                      <td className="px-3 py-2 text-right">{w.sourceUrl && <a href={w.sourceUrl} target="_blank" rel="noreferrer" className="text-slate-300 hover:text-blue-600 inline-block"><ExternalLink size={13} /></a>}</td>
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

function ThresholdEditor({ projectId, current, onSaved }: { projectId: string; current: number; onSaved: () => void }) {
  const [val, setVal] = useState(String(current))
  useEffect(() => { setVal(String(current)) }, [current])
  const save = useMutation({ mutationFn: () => api.setProductivityThreshold(projectId, Number(val)), onSuccess: onSaved })
  const dirty = Number(val) !== current && Number(val) > 0
  return (
    <div className="flex items-end gap-2">
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">Cycle-time target (hours)</label>
        <input type="number" min={0.5} step={0.5} value={val} onChange={e => setVal(e.target.value)}
          className="w-28 border border-slate-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <button onClick={() => save.mutate()} disabled={!dirty || save.isPending}
        className="flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-40 transition-colors">
        {save.isPending ? <Loader2 size={13} className="animate-spin" /> : save.isSuccess && !dirty ? <Check size={13} /> : null}
        Save
      </button>
    </div>
  )
}

function Empty({ text }: { text: string }) { return <p className="px-5 py-10 text-sm text-slate-500 text-center">{text}</p> }
