import { useState, useEffect } from 'react'
import { useProject } from '@/components/layout/ProjectLayout'
import { usePageWidth } from '@/components/layout/PageWidth'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, PageHeader, StatusBadge, type StatusVariant } from '@/components/ui'
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts'
import {
  ShieldAlert,
  Bug,
  CheckCircle2,
  Ban,
  TrendingUp,
  TrendingDown,
  Users,
  X,
  ExternalLink,
  History,
  Loader2,
  Activity,
} from 'lucide-react'
import { relativeTime } from '@/lib/utils'
import type { LabelValue, EngineerStat, QualityWorkItem, ActivityEvent } from '@/lib/types'

type Drill = {
  person: string
  email?: string
  attribution: string
  type: string
  status: string
  title: string
  involvementKind?: string
}

// Categorical chart colors — azure primary leads, semantic hues for status.
const STATUS_COLORS: Record<string, string> = {
  OPEN: '#1f6feb',
  IN_PROGRESS: '#eab308',
  DONE: '#22c55e',
  BLOCKED: '#ef4444',
  REJECTED: '#94a3b8',
  CLOSED: '#94a3b8',
}
const PALETTE = [
  '#1f6feb',
  '#8b5cf6',
  '#ef4444',
  '#f59e0b',
  '#10b981',
  '#ec4899',
  '#14b8a6',
  '#64748b',
]

/** Work-item status → semantic badge variant. */
function wiStatusVariant(s: string): StatusVariant {
  switch (s) {
    case 'OPEN':
      return 'info'
    case 'DONE':
      return 'success'
    case 'BLOCKED':
      return 'danger'
    case 'IN_PROGRESS':
      return 'warning'
    default:
      return 'neutral'
  }
}
/** Quality-engineer role → badge variant. */
function roleVariant(r: string): StatusVariant {
  switch (r) {
    case 'QA':
      return 'success'
    case 'QE':
      return 'info'
    case 'SDET':
      return 'primary'
    default:
      return 'neutral'
  }
}

export default function QualityDashboardPage() {
  usePageWidth('wide')
  const { projectId, base } = useProject()
  const qc = useQueryClient()
  const [drill, setDrill] = useState<Drill | null>(null)
  const [activityFor, setActivityFor] = useState<{ name: string; email?: string } | null>(null)
  const [syncErr, setSyncErr] = useState<string | null>(null)
  const [syncing, setSyncing] = useState(false)
  const overviewQ = useQuery({
    queryKey: ['quality-overview', projectId],
    queryFn: () => api.qualityOverview(projectId),
  })
  const engineersQ = useQuery({
    queryKey: ['quality-engineers', projectId],
    queryFn: () => api.qualityEngineers(projectId),
  })

  const syncHistory = useMutation({
    mutationFn: () => api.syncQualityHistory(projectId),
    onSuccess: r => {
      if (!r.success) {
        setSyncErr(r.error ?? 'History sync failed')
        return
      }
      setSyncErr(null)
      setSyncing(true)
    },
    onError: (e: Error) => setSyncErr(e.message),
  })

  // While a background sync runs, poll its status and refresh metrics as events land.
  const statusQ = useQuery({
    queryKey: ['quality-history-status', projectId],
    queryFn: () => api.qualityHistoryStatus(projectId),
    enabled: syncing,
    refetchInterval: syncing ? 4000 : false,
  })
  useEffect(() => {
    if (!syncing || !statusQ.data) return
    qc.invalidateQueries({ queryKey: ['quality-overview', projectId] })
    qc.invalidateQueries({ queryKey: ['quality-engineers', projectId] })
    if (statusQ.data.running === false) setSyncing(false)
  }, [statusQ.data, statusQ.dataUpdatedAt]) // eslint-disable-line react-hooks/exhaustive-deps

  if (overviewQ.isLoading) return <LoadingSpinner message="Computing quality metrics…" />
  if (overviewQ.error || !overviewQ.data)
    return (
      <ErrorMessage
        message="Failed to load quality metrics."
        onRetry={() => void overviewQ.refetch()}
      />
    )
  const o = overviewQ.data
  const engineers = engineersQ.data ?? []

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Quality Dashboard"
        icon={<ShieldAlert size={20} />}
        description="Defect health and quality-engineer activity for this project."
        actions={
          <div className="flex flex-col items-end gap-1">
            <Button
              onClick={() => syncHistory.mutate()}
              disabled={syncHistory.isPending || syncing}
            >
              {syncHistory.isPending || syncing ? (
                <Loader2 size={14} className="animate-spin" />
              ) : (
                <History size={14} />
              )}
              {syncing
                ? 'Syncing in background…'
                : syncHistory.isPending
                  ? 'Starting…'
                  : 'Sync work-item history'}
            </Button>
            <span className="text-xs text-fg-subtle">
              {syncing
                ? `${o.historyEvents} events so far…`
                : o.historyEvents > 0
                  ? `${o.historyEvents} history events`
                  : 'History not synced yet'}
            </span>
          </div>
        }
      />
      {syncErr && <ErrorMessage message={syncErr} />}
      {syncing && (
        <div className="rounded-lg bg-info-bg border border-info-border px-3 py-2 text-sm text-info">
          History sync running in the background (one ADO call per work item — a full first run
          takes a few minutes). Metrics refresh automatically as events land.
        </div>
      )}

      {/* KPI cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <Kpi icon={Bug} label="Total Defects" value={o.totalDefects} tint="text-fg" />
        <Kpi icon={ShieldAlert} label="Open Defects" value={o.openDefects} tint="text-danger" />
        <Kpi icon={CheckCircle2} label="Done" value={o.doneDefects} tint="text-success" />
        <Kpi icon={Ban} label="Blocked" value={o.blockedDefects} tint="text-warning" />
        <Kpi icon={TrendingUp} label="Created (30d)" value={o.createdLast30} tint="text-primary" />
        <Kpi
          icon={TrendingDown}
          label="Resolved (30d)"
          value={o.resolvedLast30}
          tint="text-success"
        />
      </div>

      {o.totalDefects === 0 && (
        <div className="rounded-lg bg-warning-bg border border-warning-border px-3 py-2.5 text-sm text-warning">
          No defects found for this project yet. Sync work items to populate quality metrics.
        </div>
      )}

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Panel title="Defects by Status">
          <ResponsiveContainer width="100%" height={240}>
            <PieChart>
              <Pie
                data={o.byStatus}
                dataKey="value"
                nameKey="label"
                innerRadius={55}
                outerRadius={90}
                paddingAngle={2}
              >
                {o.byStatus.map((s, i) => (
                  <Cell
                    key={s.label}
                    fill={STATUS_COLORS[s.label] ?? PALETTE[i % PALETTE.length]}
                  />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </Panel>

        <Panel title="Defects by Iteration (open vs done)">
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={o.byIteration} margin={{ left: -16, bottom: 4 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#eef0f3" />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 10 }}
                interval={0}
                angle={-20}
                textAnchor="end"
                height={60}
              />
              <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
              <Tooltip />
              <Legend />
              <Bar dataKey="open" stackId="a" name="Open" fill="#1f6feb" />
              <Bar dataKey="done" stackId="a" name="Done" fill="#22c55e" />
            </BarChart>
          </ResponsiveContainer>
        </Panel>

        <Panel title="Defects by Priority">
          <SimpleBar data={o.byPriority} color="#1f6feb" />
        </Panel>

        <Panel title="Defects by Severity">
          <SimpleBar data={o.bySeverity} color="#f59e0b" />
        </Panel>
      </div>

      <Panel title="Top Areas by Defects">
        <HBars data={o.byArea} />
      </Panel>

      {/* Quality engineer activity */}
      <div className="bg-surface rounded-lg border border-border shadow-xs">
        <div className="flex items-center gap-2 px-5 py-3.5 border-b border-border">
          <Users size={16} className="text-fg-muted" />
          <h2 className="text-sm font-semibold text-fg">Quality Engineer Activity</h2>
          <span className="text-xs text-fg-subtle">({o.qualityEngineers} flagged)</span>
          {o.historyEvents === 0 && engineers.length > 0 && (
            <span className="text-xs text-warning ml-auto">
              History columns need a sync — click “Sync work-item history”.
            </span>
          )}
        </div>
        {engineers.length === 0 ? (
          <p className="px-5 py-10 text-sm text-fg-muted text-center">
            No quality engineers flagged yet. Go to{' '}
            <Link to={`${base}/teams`} className="text-primary hover:underline">
              Teams &amp; Structure → Users
            </Link>{' '}
            and set a QA/QE/SDET role.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-fg-muted border-b border-border">
                  <th className="px-5 py-2 font-medium">Engineer</th>
                  <th className="px-3 py-2 font-medium">Role</th>
                  <th
                    className="px-3 py-2 font-medium"
                    title="Defects this engineer authored (System.CreatedBy), with their current status"
                  >
                    Defects created (by status)
                  </th>
                  <th
                    className="px-3 py-2 font-medium text-right"
                    title="Defects marked Done where this engineer is the last/current assignee (snapshot proxy)"
                  >
                    Resolved (assignee)
                  </th>
                  <th
                    className="px-3 py-2 font-medium"
                    title="Non-defect work assigned to this engineer, by status"
                  >
                    Other work
                  </th>
                  <th
                    className="px-3 py-2 font-medium text-right bg-primary-subtle/50"
                    title="From history: defects this engineer actually transitioned to Done/Resolved/Closed"
                  >
                    Resolved (actual)
                  </th>
                  <th
                    className="px-3 py-2 font-medium text-right bg-primary-subtle/50"
                    title="From history: distinct items this engineer edited, was assigned, or changed state on"
                  >
                    Participated
                  </th>
                  <th
                    className="px-3 py-2 font-medium text-right bg-primary-subtle/50"
                    title="From history: defects this engineer reopened (Done → active)"
                  >
                    Reopened
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {engineers.map((e: EngineerStat) => {
                  return (
                    <tr key={e.name} className="hover:bg-surface-muted/60 align-top">
                      <td className="px-5 py-2.5">
                        <button
                          onClick={() =>
                            setActivityFor({ name: e.name, email: e.email ?? undefined })
                          }
                          title="View activity timeline"
                          className="text-fg hover:text-primary hover:underline decoration-dotted underline-offset-2 text-left flex items-center gap-1"
                        >
                          {e.name} <Activity size={11} className="text-fg-subtle" />
                        </button>
                        {e.email && <div className="text-xs text-fg-subtle">{e.email}</div>}
                      </td>
                      <td className="px-3 py-2.5">
                        <StatusBadge variant={roleVariant(e.role)}>{e.role}</StatusBadge>
                      </td>
                      <td className="px-3 py-2.5">
                        {e.defectsCreated === 0 ? (
                          <span className="text-fg-subtle">0</span>
                        ) : (
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <button
                              onClick={() =>
                                setDrill({
                                  person: e.name,
                                  email: e.email ?? undefined,
                                  attribution: 'creator',
                                  type: 'defect',
                                  status: 'any',
                                  title: `Defects created · ${e.name}`,
                                })
                              }
                              className="text-sm font-medium text-fg hover:underline decoration-dotted underline-offset-2"
                            >
                              {e.defectsCreated}
                            </button>
                            {e.createdByStatus.map(s => (
                              <button
                                key={s.label}
                                onClick={() =>
                                  setDrill({
                                    person: e.name,
                                    email: e.email ?? undefined,
                                    attribution: 'creator',
                                    type: 'defect',
                                    status: s.label,
                                    title: `Created defects · ${s.label} · ${e.name}`,
                                  })
                                }
                              >
                                <StatusBadge variant={wiStatusVariant(s.label)}>
                                  {s.label} {s.value}
                                </StatusBadge>
                              </button>
                            ))}
                          </div>
                        )}
                      </td>
                      <DrillCell
                        value={e.defectsResolved}
                        onClick={() =>
                          setDrill({
                            person: e.name,
                            email: e.email ?? undefined,
                            attribution: 'assignee',
                            type: 'defect',
                            status: 'done',
                            title: `Resolved defects (assignee) · ${e.name}`,
                          })
                        }
                        accent
                      />
                      <td className="px-3 py-2.5">
                        {e.otherTotal === 0 ? (
                          <span className="text-fg-subtle">0</span>
                        ) : (
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <button
                              onClick={() =>
                                setDrill({
                                  person: e.name,
                                  email: e.email ?? undefined,
                                  attribution: 'assignee',
                                  type: 'other',
                                  status: 'any',
                                  title: `Other work · ${e.name}`,
                                })
                              }
                              className="text-sm font-medium text-fg hover:underline decoration-dotted underline-offset-2"
                            >
                              {e.otherTotal}
                            </button>
                            {e.otherByStatus.map(s => (
                              <button
                                key={s.label}
                                onClick={() =>
                                  setDrill({
                                    person: e.name,
                                    email: e.email ?? undefined,
                                    attribution: 'assignee',
                                    type: 'other',
                                    status: s.label,
                                    title: `Other work · ${s.label} · ${e.name}`,
                                  })
                                }
                                className="text-xs"
                              >
                                <StatusBadge variant={wiStatusVariant(s.label)}>
                                  {s.label} {s.value}
                                </StatusBadge>
                              </button>
                            ))}
                          </div>
                        )}
                      </td>
                      <DrillCell
                        value={e.resolvedActual}
                        accent
                        onClick={() =>
                          setDrill({
                            person: e.name,
                            email: e.email ?? undefined,
                            attribution: '',
                            type: '',
                            status: '',
                            involvementKind: 'resolved',
                            title: `Resolved (actual) · ${e.name}`,
                          })
                        }
                      />
                      <DrillCell
                        value={e.participated}
                        onClick={() =>
                          setDrill({
                            person: e.name,
                            email: e.email ?? undefined,
                            attribution: '',
                            type: '',
                            status: '',
                            involvementKind: 'participated',
                            title: `Participated · ${e.name}`,
                          })
                        }
                      />
                      <DrillCell
                        value={e.reopened}
                        danger
                        onClick={() =>
                          setDrill({
                            person: e.name,
                            email: e.email ?? undefined,
                            attribution: '',
                            type: '',
                            status: '',
                            involvementKind: 'reopened',
                            title: `Reopened · ${e.name}`,
                          })
                        }
                      />
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {drill && <DrillModal projectId={projectId} drill={drill} onClose={() => setDrill(null)} />}
      {activityFor && (
        <ActivityModal
          projectId={projectId}
          person={activityFor.name}
          email={activityFor.email}
          label={activityFor.name}
          onClose={() => setActivityFor(null)}
        />
      )}
    </div>
  )
}

function ActivityModal({
  projectId,
  person,
  email,
  label,
  onClose,
}: {
  projectId: string
  person: string
  email?: string
  label: string
  onClose: () => void
}) {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['quality-activity', projectId, person, email],
    queryFn: () => api.qualityActivity(projectId, person, 80, email),
  })
  const events = data ?? []
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-xl shadow-md w-full max-w-2xl mx-4 max-h-[85vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-border">
          <h2 className="text-sm font-semibold text-fg flex items-center gap-2">
            <Activity size={15} /> Activity · {label}
          </h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="overflow-y-auto flex-1 p-4">
          {isLoading ? (
            <LoadingSpinner />
          ) : error ? (
            <ErrorMessage message="Failed to load activity." onRetry={() => void refetch()} />
          ) : events.length === 0 ? (
            <p className="py-10 text-sm text-fg-muted text-center">
              No history events. Sync work-item history first.
            </p>
          ) : (
            <ol className="relative border-l border-border ml-2">
              {events.map((ev: ActivityEvent, i) => (
                <li key={i} className="ml-4 pb-4">
                  <div className="absolute -left-1.5 w-3 h-3 rounded-full bg-border-strong mt-1" />
                  <div className="flex items-center gap-2 flex-wrap text-sm">
                    <span className="text-fg">{describe(ev)}</span>
                    {ev.externalId && (
                      <span className="text-xs font-mono text-fg-subtle">#{ev.externalId}</span>
                    )}
                    {ev.sourceUrl && (
                      <a
                        href={ev.sourceUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-fg-subtle hover:text-primary"
                      >
                        <ExternalLink size={11} />
                      </a>
                    )}
                  </div>
                  {ev.title && (
                    <div className="text-xs text-fg-muted truncate max-w-xl">{ev.title}</div>
                  )}
                  {ev.revisedAt && (
                    <div className="text-[11px] text-fg-subtle">{relativeTime(ev.revisedAt)}</div>
                  )}
                </li>
              ))}
            </ol>
          )}
        </div>
      </div>
    </div>
  )
}

function describe(ev: ActivityEvent): string {
  if (ev.eventType === 'STATE_CHANGE')
    return `Moved state ${ev.fromValue ?? '—'} → ${ev.toValue ?? '—'}`
  if (ev.eventType === 'ASSIGNMENT')
    return `Assignment ${ev.fromValue ?? '—'} → ${ev.toValue ?? '—'}`
  return ev.eventType
}

function DrillCell({
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
    <td className="py-2.5 px-3 text-right tabular-nums">
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
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: [
      'quality-drill',
      projectId,
      drill.person,
      drill.involvementKind ?? '',
      drill.attribution,
      drill.type,
      drill.status,
    ],
    queryFn: () =>
      drill.involvementKind
        ? api.qualityInvolvementItems(projectId, drill.person, drill.involvementKind, drill.email)
        : api.qualityWorkItems(projectId, {
            person: drill.person,
            email: drill.email,
            attribution: drill.attribution,
            type: drill.type,
            status: drill.status,
          }),
  })
  const items = data ?? []
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
            {data && <span className="text-fg-subtle font-normal">({items.length})</span>}
          </h2>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="overflow-y-auto flex-1">
          {isLoading ? (
            <LoadingSpinner />
          ) : error ? (
            <ErrorMessage message="Failed to load items." onRetry={() => void refetch()} />
          ) : items.length === 0 ? (
            <p className="px-5 py-10 text-sm text-fg-muted text-center">No items.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-surface">
                <tr className="text-left text-xs text-fg-muted border-b border-border">
                  <th className="px-5 py-2 font-medium">Item</th>
                  <th className="px-3 py-2 font-medium">Type</th>
                  <th className="px-3 py-2 font-medium">Status</th>
                  <th className="px-3 py-2 font-medium">Iteration</th>
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {items.map((w: QualityWorkItem) => (
                  <tr key={w.id} className="hover:bg-surface-muted/60 align-top">
                    <td className="px-5 py-2">
                      <div className="text-fg">{w.title}</div>
                      {w.externalId && (
                        <span className="text-xs font-mono text-fg-subtle">#{w.externalId}</span>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge variant="neutral">{w.issueType}</StatusBadge>
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge variant={wiStatusVariant(w.status)}>{w.status}</StatusBadge>
                    </td>
                    <td className="px-3 py-2 text-xs text-fg-muted">{shortIter(w.iterationPath)}</td>
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

function shortIter(path: string | null) {
  if (!path) return '—'
  const p = path.split('\\')
  return p.length > 1 ? p.slice(1).join(' / ') : path
}

function Kpi({
  icon: Icon,
  label,
  value,
  tint,
}: {
  icon: typeof Bug
  label: string
  value: number
  tint: string
}) {
  return (
    <div className="bg-surface rounded-lg border border-border shadow-xs px-4 py-3">
      <div className="flex items-center gap-1.5 text-xs text-fg-muted mb-1">
        <Icon size={13} /> {label}
      </div>
      <div className={`text-2xl font-bold ${tint}`}>{value}</div>
    </div>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-surface rounded-lg border border-border shadow-xs p-4">
      <h3 className="text-sm font-semibold text-fg mb-3">{title}</h3>
      {children}
    </div>
  )
}

function SimpleBar({ data, color }: { data: LabelValue[]; color: string }) {
  if (!data.length) return <Empty />
  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={data} margin={{ left: -16 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#eef0f3" />
        <XAxis dataKey="label" tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
        <Tooltip />
        <Bar dataKey="value" fill={color} radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}

function HBars({ data }: { data: LabelValue[] }) {
  if (!data.length) return <Empty />
  const max = Math.max(...data.map(d => d.value), 1)
  return (
    <div className="space-y-1.5">
      {data.map(d => (
        <div key={d.label} className="flex items-center gap-2">
          <span className="text-xs text-fg-muted w-64 truncate font-mono" title={d.label}>
            {d.label}
          </span>
          <div className="flex-1 bg-surface-muted rounded h-4 overflow-hidden">
            <div className="bg-primary h-4 rounded" style={{ width: `${(d.value / max) * 100}%` }} />
          </div>
          <span className="text-xs text-fg-muted tabular-nums w-8 text-right">{d.value}</span>
        </div>
      ))}
    </div>
  )
}

function Empty() {
  return <p className="text-sm text-fg-subtle text-center py-12">No data.</p>
}
