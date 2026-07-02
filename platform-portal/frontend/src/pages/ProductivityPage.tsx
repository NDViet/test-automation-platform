import { useState, useEffect } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { usePageWidth } from '@/components/layout/PageWidth'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, Input, PageHeader } from '@/components/ui'
import {
  Gauge,
  Timer,
  AlertTriangle,
  Layers,
  X,
  ExternalLink,
  Check,
  CheckCircle2,
  Hourglass,
} from 'lucide-react'
import type { AreaProductivity, OverThresholdItem, LeadAreaStat, LeadItem } from '@/lib/types'

function leaf(path: string) {
  const i = path.lastIndexOf('\\')
  return i >= 0 ? path.slice(i + 1) : path
}
function fmtH(h: number | null) {
  return h == null ? '—' : `${h.toFixed(1)}h`
}
function fmtDate(iso: string | null) {
  return iso ? new Date(iso).toLocaleDateString() : '—'
}
function statusBadge(s: string) {
  return s === 'OPEN'
    ? 'text-info bg-info-bg'
    : s === 'IN_PROGRESS'
      ? 'text-warning bg-warning-bg'
      : s === 'BLOCKED'
        ? 'text-danger bg-danger-bg'
        : s === 'DONE'
          ? 'text-success bg-success-bg'
          : 'text-neutral bg-neutral-bg'
}

// Drill target: either started-WIP items, or completed lead-time items.
type Drill =
  | { kind: 'wip'; area: string | undefined; over: boolean; title: string }
  | { kind: 'lead'; area: string | undefined; title: string }

export default function ProductivityPage() {
  usePageWidth('wide')
  const { projectId } = useProject()
  const qc = useQueryClient()
  const [drill, setDrill] = useState<Drill | null>(null)

  const cycleQ = useQuery({
    queryKey: ['productivity-by-area', projectId],
    queryFn: () => api.productivityByArea(projectId),
  })
  const leadQ = useQuery({
    queryKey: ['productivity-lead', projectId],
    queryFn: () => api.productivityLeadByArea(projectId),
  })

  if (cycleQ.isLoading) return <LoadingSpinner message="Computing cycle times…" />
  if (cycleQ.error || !cycleQ.data)
    return (
      <ErrorMessage
        message="Failed to load productivity metrics."
        onRetry={() => void cycleQ.refetch()}
      />
    )
  const c = cycleQ.data
  const lead = leadQ.data
  const pctOver = c.totalWip > 0 ? Math.round((c.totalOver / c.totalWip) * 100) : 0

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Productivity"
        icon={<Gauge size={20} />}
        description="Cycle time (work in progress) and lead time (created → completed), by area."
        actions={
          <ThresholdEditor
            projectId={projectId}
            current={c.thresholdHours}
            onSaved={() => qc.invalidateQueries({ queryKey: ['productivity-by-area', projectId] })}
          />
        }
      />

      {/* ── Cycle time ─────────────────────────────────────────────── */}
      <SectionTitle
        icon={Timer}
        title="Cycle time"
        subtitle="Running age of started-but-not-completed work"
      />
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Kpi
          icon={Timer}
          label="In progress (started)"
          value={c.totalWip}
          tint="text-fg"
          onClick={() =>
            setDrill({ kind: 'wip', area: undefined, over: false, title: 'In progress' })
          }
        />
        <Kpi
          icon={AlertTriangle}
          label={`Over ${fmtH(c.thresholdHours)} target`}
          value={c.totalOver}
          tint="text-danger"
          onClick={() =>
            setDrill({
              kind: 'wip',
              area: undefined,
              over: true,
              title: `Over ${fmtH(c.thresholdHours)} target`,
            })
          }
        />
        <Kpi
          icon={Gauge}
          label="% over target"
          value={`${pctOver}%`}
          tint={pctOver > 30 ? 'text-danger' : 'text-warning'}
        />
        <Kpi icon={Layers} label="Areas needing action" value={c.areasAffected} tint="text-fg" />
      </div>

      <div className="bg-surface rounded-lg border border-border shadow-xs">
        <div className="flex items-center gap-2 px-5 py-3.5 border-b border-border">
          <Layers size={16} className="text-fg-muted" />
          <h2 className="text-sm font-semibold text-fg">Cycle time by Area</h2>
          <span className="text-xs text-fg-subtle">target {fmtH(c.thresholdHours)}</span>
        </div>
        {c.areas.length === 0 ? (
          <Empty text="No started work in progress. (Cycle time needs work-item history — run Sync work-item history on the Quality page.)" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-fg-muted border-b border-border">
                  <th className="px-5 py-2 font-medium">Area</th>
                  <th className="px-3 py-2 font-medium text-right">In progress</th>
                  <th className="px-3 py-2 font-medium text-right">Over target</th>
                  <th className="px-3 py-2 font-medium text-right">% over</th>
                  <th className="px-3 py-2 font-medium text-right">Avg age</th>
                  <th className="px-5 py-2 font-medium text-right">Max age</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {c.areas.map((a: AreaProductivity) => {
                  const pct = a.wip > 0 ? Math.round((a.overThreshold / a.wip) * 100) : 0
                  return (
                    <tr key={a.area} className="hover:bg-surface-muted">
                      <td className="px-5 py-2.5 text-fg" title={a.area}>
                        {leaf(a.area)}
                        <span className="text-xs text-fg-subtle font-mono ml-2 hidden lg:inline">
                          {a.area}
                        </span>
                      </td>
                      <NumCell
                        value={a.wip}
                        onClick={() =>
                          setDrill({
                            kind: 'wip',
                            area: a.area,
                            over: false,
                            title: `In progress · ${leaf(a.area)}`,
                          })
                        }
                      />
                      <NumCell
                        value={a.overThreshold}
                        danger
                        onClick={() =>
                          setDrill({
                            kind: 'wip',
                            area: a.area,
                            over: true,
                            title: `Over target · ${leaf(a.area)}`,
                          })
                        }
                      />
                      <td className="px-3 py-2.5 text-right tabular-nums">
                        <span
                          className={cn(
                            'px-1.5 py-0.5 rounded text-xs',
                            pct > 50
                              ? 'bg-danger-bg text-danger'
                              : pct > 0
                                ? 'bg-warning-bg text-warning'
                                : 'text-fg-subtle',
                          )}
                        >
                          {pct}%
                        </span>
                      </td>
                      <td className="px-3 py-2.5 text-right tabular-nums text-fg-muted">
                        {fmtH(a.avgHours)}
                      </td>
                      <td className="px-5 py-2.5 text-right tabular-nums text-fg-muted">
                        {fmtH(a.maxHours)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Lead time ──────────────────────────────────────────────── */}
      <SectionTitle
        icon={Hourglass}
        title="Lead time"
        subtitle="From work-item creation until completion"
      />
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Kpi
          icon={CheckCircle2}
          label="Completed"
          value={lead?.totalCompleted ?? 0}
          tint="text-success"
          onClick={() =>
            setDrill({ kind: 'lead', area: undefined, title: 'Completed (lead time)' })
          }
        />
        <Kpi
          icon={Hourglass}
          label="Avg lead time"
          value={fmtH(lead?.avgHours ?? null)}
          tint="text-fg"
        />
        <Kpi
          icon={Hourglass}
          label="Max lead time"
          value={fmtH(lead?.maxHours ?? null)}
          tint="text-warning"
        />
      </div>

      <div className="bg-surface rounded-lg border border-border shadow-xs">
        <div className="flex items-center gap-2 px-5 py-3.5 border-b border-border">
          <Layers size={16} className="text-fg-muted" />
          <h2 className="text-sm font-semibold text-fg">Lead time by Area</h2>
        </div>
        {leadQ.isLoading ? (
          <LoadingSpinner />
        ) : !lead || lead.areas.length === 0 ? (
          <Empty text="No completed items with history. (Lead time needs work-item history — run Sync work-item history on the Quality page.)" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-fg-muted border-b border-border">
                  <th className="px-5 py-2 font-medium">Area</th>
                  <th className="px-3 py-2 font-medium text-right">Completed</th>
                  <th className="px-3 py-2 font-medium text-right">Avg lead</th>
                  <th className="px-5 py-2 font-medium text-right">Max lead</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {lead.areas.map((a: LeadAreaStat) => (
                  <tr key={a.area} className="hover:bg-surface-muted">
                    <td className="px-5 py-2.5 text-fg" title={a.area}>
                      {leaf(a.area)}
                      <span className="text-xs text-fg-subtle font-mono ml-2 hidden lg:inline">
                        {a.area}
                      </span>
                    </td>
                    <NumCell
                      value={a.completed}
                      accent
                      onClick={() =>
                        setDrill({
                          kind: 'lead',
                          area: a.area,
                          title: `Completed · ${leaf(a.area)}`,
                        })
                      }
                    />
                    <td className="px-3 py-2.5 text-right tabular-nums text-fg-muted">
                      {fmtH(a.avgHours)}
                    </td>
                    <td className="px-5 py-2.5 text-right tabular-nums text-fg-muted">
                      {fmtH(a.maxHours)}
                    </td>
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

function SectionTitle({
  icon: Icon,
  title,
  subtitle,
}: {
  icon: typeof Timer
  title: string
  subtitle: string
}) {
  return (
    <div className="flex items-baseline gap-2 mt-1">
      <Icon size={16} className="text-fg-muted self-center" />
      <h2 className="text-base font-semibold text-fg">{title}</h2>
      <span className="text-xs text-fg-subtle">{subtitle}</span>
    </div>
  )
}

function Kpi({
  icon: Icon,
  label,
  value,
  tint,
  onClick,
}: {
  icon: typeof Timer
  label: string
  value: number | string
  tint: string
  onClick?: () => void
}) {
  const clickable = !!onClick && value !== 0 && value !== '0'
  return (
    <div className="bg-surface rounded-lg border border-border shadow-xs px-4 py-3">
      <div className="flex items-center gap-1.5 text-xs text-fg-muted mb-1">
        <Icon size={13} /> {label}
      </div>
      {clickable ? (
        <button
          onClick={onClick}
          className={`text-2xl font-bold ${tint} hover:underline decoration-dotted underline-offset-4`}
        >
          {value}
        </button>
      ) : (
        <div className={`text-2xl font-bold ${tint}`}>{value}</div>
      )}
    </div>
  )
}

function NumCell({
  value,
  onClick,
  danger,
  accent,
}: {
  value: number
  onClick: () => void
  danger?: boolean
  accent?: boolean
}) {
  const cls = danger ? 'font-medium text-danger' : accent ? 'font-medium text-success' : 'text-fg'
  return (
    <td className="px-3 py-2.5 text-right tabular-nums">
      {value > 0 ? (
        <button
          onClick={onClick}
          className={`${cls} hover:underline decoration-dotted underline-offset-2`}
        >
          {value}
        </button>
      ) : (
        <span className="text-fg-subtle">0</span>
      )}
    </td>
  )
}

function DrillModal({
  projectId,
  drill,
  onClose,
}: {
  projectId: string
  drill: Drill
  onClose: () => void
}) {
  const isLead = drill.kind === 'lead'
  const wipQ = useQuery({
    queryKey: ['prod-wip', projectId, drill.area, drill.kind === 'wip' ? drill.over : null],
    queryFn: () =>
      api.productivityWipItems(projectId, drill.area, drill.kind === 'wip' ? drill.over : true),
    enabled: drill.kind === 'wip',
  })
  const leadItemsQ = useQuery({
    queryKey: ['prod-lead-items', projectId, drill.area],
    queryFn: () => api.productivityLeadItems(projectId, drill.area),
    enabled: isLead,
  })
  const q = isLead ? leadItemsQ : wipQ
  const items = (q.data ?? []) as OverThresholdItem[] | LeadItem[]

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-xl shadow-md w-full max-w-3xl mx-4 max-h-[85vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-border">
          <h2 className="text-sm font-semibold text-fg">
            {drill.title}{' '}
            {q.data && <span className="text-fg-subtle font-normal">({items.length})</span>}
          </h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="overflow-y-auto flex-1">
          {q.isLoading ? (
            <LoadingSpinner />
          ) : q.error ? (
            <ErrorMessage message="Failed to load." onRetry={() => void q.refetch()} />
          ) : items.length === 0 ? (
            <Empty text="No items." />
          ) : (
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-surface">
                <tr className="text-left text-xs text-fg-muted border-b border-border">
                  <th className="px-5 py-2 font-medium">Item</th>
                  <th className="px-3 py-2 font-medium">Type</th>
                  <th className="px-3 py-2 font-medium">Assignee</th>
                  {isLead ? (
                    <>
                      <th className="px-3 py-2 font-medium">Created</th>
                      <th className="px-3 py-2 font-medium">Completed</th>
                      <th className="px-3 py-2 font-medium text-right">Lead</th>
                    </>
                  ) : (
                    <>
                      <th className="px-3 py-2 font-medium">Status</th>
                      <th className="px-3 py-2 font-medium text-right">Age</th>
                    </>
                  )}
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {isLead
                  ? (items as LeadItem[]).map(w => (
                      <tr key={w.id} className="hover:bg-surface-muted align-top">
                        <td className="px-5 py-2">
                          <div className="text-fg">{w.title}</div>
                          {w.externalId && (
                            <span className="text-xs font-mono text-fg-subtle">
                              #{w.externalId}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-2">
                          <Badge label={w.issueType} colorClass="text-neutral bg-neutral-bg" />
                        </td>
                        <td className="px-3 py-2 text-xs text-fg-muted">{w.assignedTo ?? '—'}</td>
                        <td className="px-3 py-2 text-xs text-fg-muted">
                          {fmtDate(w.createdDate)}
                        </td>
                        <td className="px-3 py-2 text-xs text-fg-muted">
                          {fmtDate(w.completedAt)}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums font-medium text-fg">
                          {fmtH(w.leadHours)}
                        </td>
                        <td className="px-3 py-2 text-right">
                          {w.sourceUrl && (
                            <a
                              href={w.sourceUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-fg-subtle hover:text-primary inline-block"
                            >
                              <ExternalLink size={13} />
                            </a>
                          )}
                        </td>
                      </tr>
                    ))
                  : (items as OverThresholdItem[]).map(w => (
                      <tr key={w.id} className="hover:bg-surface-muted align-top">
                        <td className="px-5 py-2">
                          <div className="text-fg">{w.title}</div>
                          {w.externalId && (
                            <span className="text-xs font-mono text-fg-subtle">
                              #{w.externalId}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-2">
                          <Badge label={w.issueType} colorClass="text-neutral bg-neutral-bg" />
                        </td>
                        <td className="px-3 py-2 text-xs text-fg-muted">{w.assignedTo ?? '—'}</td>
                        <td className="px-3 py-2">
                          <Badge label={w.status} colorClass={statusBadge(w.status)} />
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums font-medium text-danger">
                          {fmtH(w.cycleHours)}
                        </td>
                        <td className="px-3 py-2 text-right">
                          {w.sourceUrl && (
                            <a
                              href={w.sourceUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-fg-subtle hover:text-primary inline-block"
                            >
                              <ExternalLink size={13} />
                            </a>
                          )}
                        </td>
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

function ThresholdEditor({
  projectId,
  current,
  onSaved,
}: {
  projectId: string
  current: number
  onSaved: () => void
}) {
  const [val, setVal] = useState(String(current))
  useEffect(() => {
    setVal(String(current))
  }, [current])
  const save = useMutation({
    mutationFn: () => api.setProductivityThreshold(projectId, Number(val)),
    onSuccess: onSaved,
  })
  const dirty = Number(val) !== current && Number(val) > 0
  return (
    <div className="flex items-end gap-2">
      <div>
        <label className="block text-xs font-medium text-fg-muted mb-1">
          Cycle-time target (hours)
        </label>
        <Input
          type="number"
          min={0.5}
          step={0.5}
          value={val}
          onChange={e => setVal(e.target.value)}
          className="w-28"
          aria-label="Cycle-time target hours"
        />
      </div>
      <Button size="sm" onClick={() => save.mutate()} disabled={!dirty} loading={save.isPending}>
        {!save.isPending && save.isSuccess && !dirty ? <Check size={13} /> : null}
        Save
      </Button>
    </div>
  )
}

function Empty({ text }: { text: string }) {
  return <p className="px-5 py-10 text-sm text-fg-muted text-center">{text}</p>
}
