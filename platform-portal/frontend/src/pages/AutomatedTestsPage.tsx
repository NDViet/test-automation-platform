import { useState, useRef, useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useProject } from '@/components/layout/ProjectLayout'
import { api } from '@/lib/api'
import { cn, relativeTime, formatDuration } from '@/lib/utils'
import type { AutomatedTestSummary, TestTrendPoint, RecentRun, ExecutionSummary } from '@/lib/types'
import { AreaChart, Area, Tooltip as RCTooltip, ResponsiveContainer } from 'recharts'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Search, ExternalLink, ChevronRight, X, Play, GitBranch, FlaskConical, List, Calendar, ChevronDown, Camera, Video, Monitor, FileCode, Tag } from 'lucide-react'

// ── Date range ────────────────────────────────────────────────────────────────

type DateRange = { from: Date; to: Date; label: string }

function startOfDay(d: Date): Date {
  const r = new Date(d); r.setHours(0, 0, 0, 0); return r
}
function endOfDay(d: Date): Date {
  const r = new Date(d); r.setHours(23, 59, 59, 999); return r
}
function daysAgo(n: number): Date {
  const d = new Date(); d.setDate(d.getDate() - n); return startOfDay(d)
}
function toLocalInput(d: Date): string {
  // datetime-local input value format: "YYYY-MM-DDTHH:mm"
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function formatRangeLabel(from: Date, to: Date): string {
  const fmt = (d: Date) =>
    d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
  return `${fmt(from)} – ${fmt(to)}`
}

const PRESETS: { label: string; days: number }[] = [
  { label: 'Today',    days: 0  },
  { label: 'Last 7d',  days: 7  },
  { label: 'Last 14d', days: 14 },
  { label: 'Last 30d', days: 30 },
  { label: 'Last 90d', days: 90 },
]

function makePreset(days: number, label: string): DateRange {
  const now = new Date()
  return { from: daysAgo(days), to: endOfDay(now), label }
}

function DateRangePicker({ value, onChange }: { value: DateRange; onChange: (r: DateRange) => void }) {
  const [open, setOpen]       = useState(false)
  const [fromStr, setFromStr] = useState(toLocalInput(value.from))
  const [toStr, setToStr]     = useState(toLocalInput(value.to))
  const ref = useRef<HTMLDivElement>(null)

  // Close on outside click
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  function applyPreset(days: number, label: string) {
    const r = makePreset(days, label)
    setFromStr(toLocalInput(r.from))
    setToStr(toLocalInput(r.to))
    onChange(r)
    setOpen(false)
  }

  function applyCustom() {
    const from = new Date(fromStr)
    const to   = new Date(toStr)
    if (isNaN(from.getTime()) || isNaN(to.getTime()) || from > to) return
    onChange({ from, to, label: formatRangeLabel(from, to) })
    setOpen(false)
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-2 px-3 py-1.5 text-sm font-medium rounded-lg border border-slate-200 bg-white shadow-sm hover:bg-slate-50 transition-colors whitespace-nowrap"
      >
        <Calendar size={14} className="text-slate-400 shrink-0" />
        <span className="text-slate-700">{value.label}</span>
        <ChevronDown size={13} className={cn('text-slate-400 transition-transform', open && 'rotate-180')} />
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1.5 z-50 bg-white border border-slate-200 rounded-xl shadow-lg p-4 min-w-[300px]">
          {/* Preset chips */}
          <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide mb-2">Quick select</p>
          <div className="flex flex-wrap gap-1.5 mb-4">
            {PRESETS.map(p => (
              <button
                key={p.label}
                onClick={() => applyPreset(p.days, p.label)}
                className={cn(
                  'px-3 py-1 text-xs font-medium rounded-full border transition-colors',
                  value.label === p.label
                    ? 'bg-blue-600 text-white border-blue-600'
                    : 'bg-white text-slate-600 border-slate-200 hover:border-blue-400 hover:text-blue-600'
                )}
              >
                {p.label}
              </button>
            ))}
          </div>

          {/* Divider */}
          <div className="border-t border-slate-100 mb-4" />

          {/* Custom range */}
          <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide mb-2">Custom range</p>
          <div className="space-y-2">
            <div>
              <label className="text-xs text-slate-500 block mb-1">From</label>
              <input
                type="datetime-local"
                value={fromStr}
                onChange={e => setFromStr(e.target.value)}
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="text-xs text-slate-500 block mb-1">To</label>
              <input
                type="datetime-local"
                value={toStr}
                onChange={e => setToStr(e.target.value)}
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              onClick={applyCustom}
              className="w-full mt-1 px-4 py-1.5 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              Apply
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Colour helpers ────────────────────────────────────────────────────────────

type StatusFilter = 'ALL' | 'PASSED' | 'FAILED' | 'FLAKY' | 'SKIPPED'
type ViewMode = 'tests' | 'runs'

function lastStatusColor(status: string): string {
  switch (status) {
    case 'PASSED':  return 'text-green-700 bg-green-100'
    case 'FAILED':
    case 'BROKEN':  return 'text-red-700 bg-red-100'
    case 'SKIPPED': return 'text-slate-600 bg-slate-100'
    default:        return 'text-orange-700 bg-orange-100'
  }
}

function passRateColor(rate: number): string {
  if (rate >= 0.9) return 'bg-green-500'
  if (rate >= 0.7) return 'bg-yellow-400'
  return 'bg-red-500'
}

// ── Trend chart (SVG stacked bar, one bar per day) ────────────────────────────

function TrendChart({ data }: { data: TestTrendPoint[] }) {
  const [hovered, setHovered] = useState<number | null>(null)

  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center h-32 text-sm text-slate-400">
        No execution data for this period
      </div>
    )
  }

  const maxTotal = Math.max(...data.map(d => d.total), 1)
  const BAR_W = 10
  const GAP = 4
  const CHART_H = 90
  const LABEL_H = 18
  const totalW = Math.max(data.length * (BAR_W + GAP) - GAP, 60)

  const showLabelEvery = Math.max(1, Math.ceil(data.length / 7))

  return (
    <div className="relative select-none">
      {hovered !== null && (
        <div className="absolute top-0 left-0 bg-slate-800 text-white text-xs rounded-lg px-2.5 py-2 pointer-events-none z-10 shadow-lg min-w-[110px]">
          <p className="text-slate-300 mb-1">{data[hovered].date}</p>
          <p className="text-green-400">✓ {data[hovered].passed} passed</p>
          {data[hovered].failed > 0 && (
            <p className="text-red-400">✗ {data[hovered].failed} failed</p>
          )}
          {data[hovered].skipped > 0 && (
            <p className="text-slate-400">◌ {data[hovered].skipped} skipped</p>
          )}
          <p className="text-slate-300 mt-1 border-t border-slate-700 pt-1">
            {Math.round(data[hovered].passRate * 100)}% pass rate
          </p>
        </div>
      )}

      <svg
        viewBox={`0 0 ${totalW} ${CHART_H + LABEL_H}`}
        className="w-full"
        style={{ height: CHART_H + LABEL_H + 4 }}
      >
        {/* Horizontal grid */}
        {[0, 0.5, 1].map(f => {
          const y = CHART_H * (1 - f)
          return (
            <line key={f} x1={0} x2={totalW} y1={y} y2={y}
              stroke="#e2e8f0" strokeWidth="1" strokeDasharray={f === 0 ? '' : '2 2'} />
          )
        })}

        {data.map((d, i) => {
          const x = i * (BAR_W + GAP)
          const passH  = (d.passed  / maxTotal) * CHART_H
          const failH  = (d.failed  / maxTotal) * CHART_H
          const skipH  = (d.skipped / maxTotal) * CHART_H
          const isHov  = hovered === i

          return (
            <g key={d.date}
               onMouseEnter={() => setHovered(i)}
               onMouseLeave={() => setHovered(null)}
               style={{ cursor: 'default' }}
            >
              {/* Hover highlight */}
              <rect x={x - 1} y={0} width={BAR_W + 2} height={CHART_H}
                fill={isHov ? '#f1f5f9' : 'transparent'} />

              {/* Skipped (bottom) */}
              {skipH > 0 && (
                <rect x={x} y={CHART_H - skipH} width={BAR_W} height={skipH}
                  fill="#cbd5e1" rx="1" />
              )}
              {/* Failed (above skipped) */}
              {failH > 0 && (
                <rect x={x} y={CHART_H - skipH - failH} width={BAR_W} height={failH}
                  fill="#f87171" rx="1" />
              )}
              {/* Passed (top) */}
              {passH > 0 && (
                <rect x={x} y={CHART_H - skipH - failH - passH} width={BAR_W} height={passH}
                  fill="#4ade80" rx="1" />
              )}

              {/* Date label */}
              {i % showLabelEvery === 0 && (
                <text x={x + BAR_W / 2} y={CHART_H + LABEL_H - 2}
                  textAnchor="middle" fontSize="8" fill="#94a3b8">
                  {d.date.slice(5)}
                </text>
              )}
            </g>
          )
        })}
      </svg>

      <div className="flex items-center gap-4 mt-2 text-xs text-slate-500">
        <span className="flex items-center gap-1.5">
          <span className="inline-block w-2.5 h-2.5 bg-green-400 rounded-sm" /> Passed
        </span>
        <span className="flex items-center gap-1.5">
          <span className="inline-block w-2.5 h-2.5 bg-red-400 rounded-sm" /> Failed
        </span>
        <span className="flex items-center gap-1.5">
          <span className="inline-block w-2.5 h-2.5 bg-slate-300 rounded-sm" /> Skipped
        </span>
      </div>
    </div>
  )
}

// ── Pass-rate mini bar ────────────────────────────────────────────────────────

function PassRateBar({ rate, total }: { rate: number; total: number }) {
  const pct = Math.round(rate * 100)
  return (
    <div className="flex items-center gap-2 min-w-[90px]">
      <div className="flex-1 h-1.5 bg-slate-100 rounded-full overflow-hidden">
        <div
          className={cn('h-full rounded-full transition-all', passRateColor(rate))}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className={cn(
        'text-xs font-medium tabular-nums w-9 text-right',
        rate >= 0.9 ? 'text-green-700' : rate >= 0.7 ? 'text-yellow-700' : 'text-red-700'
      )}>
        {total === 0 ? '—' : `${pct}%`}
      </span>
    </div>
  )
}

// ── Trend sparklines ─────────────────────────────────────────────────────────

interface SparkDatum { label: string; [key: string]: number | string }

function SparkCard({
  title,
  value,
  sub,
  color,
  data,
  dataKey,
  formatter,
}: {
  title: string
  value: string
  sub?: string
  color: string
  data: SparkDatum[]
  dataKey: string
  formatter?: (v: number) => string
}) {
  const half = Math.floor(data.length / 2)
  const avg = (slice: SparkDatum[]) =>
    slice.length === 0 ? 0 : slice.reduce((s, d) => s + ((d[dataKey] as number) ?? 0), 0) / slice.length
  const prev = avg(data.slice(0, half))
  const curr = avg(data.slice(half))
  const delta = prev === 0 ? 0 : ((curr - prev) / prev) * 100
  const up = delta >= 0

  const gradId = `spark-grad-${dataKey}`

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm px-4 pt-3 pb-0 overflow-hidden">
      <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">{title}</p>
      <div className="flex items-end justify-between mt-0.5">
        <p className="text-2xl font-bold text-slate-900">{value}</p>
        {data.length >= 4 && prev !== curr && (
          <span className={cn(
            'text-xs font-semibold mb-1',
            up ? 'text-green-600' : 'text-red-500'
          )}>
            {up ? '↑' : '↓'} {Math.abs(delta).toFixed(0)}%
          </span>
        )}
      </div>
      {sub && <p className="text-[11px] text-slate-400">{sub}</p>}
      <div className="mt-2 -mx-4">
        <ResponsiveContainer width="100%" height={52}>
          <AreaChart data={data} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={color} stopOpacity={0.18} />
                <stop offset="95%" stopColor={color} stopOpacity={0} />
              </linearGradient>
            </defs>
            <RCTooltip
              contentStyle={{ fontSize: 11, borderRadius: 8, border: '1px solid #e2e8f0', padding: '4px 8px' }}
              formatter={(v) => [formatter ? formatter(v as number) : String(v), title]}
              labelFormatter={(_, payload) => (payload?.[0]?.payload?.label as string | undefined) ?? ''}
            />
            <Area type="monotone" dataKey={dataKey} stroke={color} strokeWidth={1.5}
              fill={`url(#${gradId})`} dot={false} activeDot={{ r: 3 }} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function TrendSparklines({
  filteredExecs,
  uniqueTests,
  execPassRate,
  execRunCount,
  failingNow,
}: {
  filteredExecs: ExecutionSummary[]
  uniqueTests: number
  execPassRate: number
  execRunCount: number
  failingNow: number
}) {
  // One data point per execution, sorted oldest → newest
  const data: SparkDatum[] = useMemo(() => {
    return [...filteredExecs]
      .sort((a, b) => new Date(a.executedAt).getTime() - new Date(b.executedAt).getTime())
      .map(e => ({
        label: `${e.executedAt.slice(0, 10)}${e.branch ? ' · ' + e.branch : ''}`,
        totalTests: e.totalTests,
        passRate: Math.round(e.passRate * 1000) / 10,
        failed: e.failed + (e.broken ?? 0),
      }))
  }, [filteredExecs])

  if (data.length < 2) return null

  return (
    <div className="grid grid-cols-3 gap-3">
      <SparkCard
        title="Unique Tests / Run"
        value={uniqueTests.toLocaleString()}
        sub="distinct test IDs in period"
        color="#3b82f6"
        data={data}
        dataKey="totalTests"
        formatter={v => v.toLocaleString()}
      />
      <SparkCard
        title="Pass Rate / Run"
        value={execRunCount === 0 ? '—' : `${Math.round(execPassRate * 100)}%`}
        sub="avg across execution runs"
        color="#22c55e"
        data={data}
        dataKey="passRate"
        formatter={v => `${v}%`}
      />
      <SparkCard
        title="Currently Failing"
        value={failingNow.toLocaleString()}
        sub="tests with last status failed"
        color="#ef4444"
        data={data}
        dataKey="failed"
        formatter={v => v.toLocaleString()}
      />
    </div>
  )
}

// ── Detail panel ──────────────────────────────────────────────────────────────

function DetailPanel({
  test,
  projectId,
  base,
  onClose,
}: {
  test: AutomatedTestSummary
  projectId: string
  base: string
  onClose: () => void
}) {
  const [detailRange, setDetailRange] = useState<DateRange>(makePreset(30, 'Last 30d'))

  const detailDays = Math.max(1, Math.ceil((detailRange.to.getTime() - detailRange.from.getTime()) / 86_400_000))

  const { data, isLoading } = useQuery({
    queryKey: ['automated-test-detail', projectId, test.testId, detailDays],
    queryFn: () => api.automatedTestDetail(projectId, test.testId, detailDays),
  })

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-start justify-between gap-3 pb-4 border-b border-slate-100">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-slate-900 break-words leading-snug">
            {test.displayName}
          </p>
          {test.suiteName && (
            <p className="text-xs text-slate-400 mt-0.5 truncate">{test.suiteName}</p>
          )}
          {test.specFile && (
            <p className="text-[11px] text-slate-400 font-mono truncate mt-1" title={test.specFile}>
              {test.specFile}
            </p>
          )}
          {(test.tags.length > 0 || test.browsers.length > 0 || test.annotationTypes.length > 0) && (
            <div className="flex flex-wrap gap-1 mt-2">
              {test.tags.map(t => (
                <span key={`tag:${t}`} className="text-xs bg-violet-50 text-violet-700 px-1.5 py-0.5 rounded font-mono">
                  {t}
                </span>
              ))}
              {test.browsers.map(b => (
                <span key={`br:${b}`} className="inline-flex items-center gap-0.5 text-xs bg-sky-50 text-sky-700 px-1.5 py-0.5 rounded">
                  <Monitor size={10} />{b}
                </span>
              ))}
              {test.annotationTypes.map(a => (
                <span key={`ann:${a}`} className="text-xs bg-amber-50 text-amber-700 px-1.5 py-0.5 rounded">
                  @{a}
                </span>
              ))}
            </div>
          )}
          {(test.hasScreenshot || test.hasVideo) && (
            <div className="flex items-center gap-2 mt-1.5 text-xs text-slate-400">
              {test.hasScreenshot && (
                <span className="flex items-center gap-1"><Camera size={11} /> Screenshots</span>
              )}
              {test.hasVideo && (
                <span className="flex items-center gap-1"><Video size={11} /> Video</span>
              )}
            </div>
          )}
        </div>
        <button onClick={onClose}
          className="shrink-0 text-slate-400 hover:text-slate-600 transition-colors p-1 rounded">
          <ChevronRight size={16} />
        </button>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-3 gap-3 py-4 border-b border-slate-100">
        <div>
          <p className="text-xs text-slate-500">Runs</p>
          <p className="text-xl font-bold text-slate-900">{test.totalRuns}</p>
        </div>
        <div>
          <p className="text-xs text-slate-500">Pass Rate</p>
          <p className={cn('text-xl font-bold', test.passRate >= 0.9 ? 'text-green-700' : test.passRate >= 0.7 ? 'text-yellow-700' : 'text-red-700')}>
            {Math.round(test.passRate * 100)}%
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-500">Avg Duration</p>
          <p className="text-xl font-bold text-slate-900">{formatDuration(test.avgDurationMs)}</p>
        </div>
      </div>

      {/* Trend section */}
      <div className="py-4 border-b border-slate-100">
        <div className="flex items-center justify-between mb-3">
          <p className="text-xs font-semibold text-slate-700 uppercase tracking-wide">
            Execution Trend
          </p>
          <DateRangePicker value={detailRange} onChange={setDetailRange} />
        </div>

        {isLoading
          ? <div className="h-28 flex items-center justify-center"><LoadingSpinner message="" /></div>
          : <TrendChart data={data?.trend ?? []} />
        }
      </div>

      {/* Recent runs */}
      <div className="flex-1 overflow-y-auto pt-4">
        <p className="text-xs font-semibold text-slate-700 uppercase tracking-wide mb-3">
          Recent Runs
        </p>
        {isLoading && <LoadingSpinner message="" />}
        {!isLoading && (data?.recentRuns ?? []).length === 0 && (
          <p className="text-sm text-slate-400 text-center py-4">No runs found</p>
        )}
        <div className="space-y-1.5">
          {(data?.recentRuns ?? []).map((run: RecentRun, i: number) => (
            <div key={i}
              className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-slate-50 transition-colors group">
              <Badge label={run.status} colorClass={lastStatusColor(run.status)} />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 text-xs text-slate-600 flex-wrap">
                  <span className="truncate">{relativeTime(run.runAt)}</span>
                  {run.browser && (
                    <span className="flex items-center gap-0.5 text-sky-600 font-medium">
                      <Monitor size={10} />
                      {run.browser}
                    </span>
                  )}
                  {run.environment && (
                    <span className="text-slate-400 truncate">{run.environment}</span>
                  )}
                  {run.durationMs != null && (
                    <span className="text-slate-400">{formatDuration(run.durationMs)}</span>
                  )}
                  {run.hasScreenshot && (
                    <span title="Has screenshot"><Camera size={11} className="text-slate-400 shrink-0" /></span>
                  )}
                  {run.hasVideo && (
                    <span title="Has video"><Video size={11} className="text-slate-400 shrink-0" /></span>
                  )}
                </div>
                {run.specFile && (
                  <p className="text-[11px] text-slate-400 font-mono truncate mt-0.5" title={run.specFile}>
                    {run.specFile}
                  </p>
                )}
                {run.failureMessage && (
                  <p className="text-xs text-red-600 truncate mt-0.5">{run.failureMessage}</p>
                )}
              </div>
              <div className="shrink-0 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                {run.hasTrace && run.resultId && (
                  <>
                    <a
                      href={`${window.location.origin}/pw-trace/index.html?trace=${encodeURIComponent(`${window.location.origin}/api/portal/traces/${run.resultId}`)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      title="Open Trace Viewer"
                      className="flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium
                                 bg-violet-100 text-violet-700 hover:bg-violet-200 transition-colors"
                      onClick={e => e.stopPropagation()}
                    >
                      <Play size={9} />
                      Trace
                    </a>
                    <a
                      href={api.traceUrl(run.resultId)}
                      download={`trace-${run.resultId}.zip`}
                      title="Download trace — view with: npx playwright show-trace trace.zip"
                      className="flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium
                                 bg-slate-100 text-slate-500 hover:bg-slate-200 transition-colors"
                      onClick={e => e.stopPropagation()}
                    >
                      ↓
                    </a>
                  </>
                )}
                {run.runId && (
                  <a
                    href={`${base}/runs/${run.runId}${run.resultId ? `?expandResult=${run.resultId}` : ''}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    title="View run"
                    onClick={e => e.stopPropagation()}
                    className="text-slate-300 hover:text-blue-600 transition-colors"
                  >
                    <ExternalLink size={13} />
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Run status helpers ────────────────────────────────────────────────────────

function runStatusColor(exec: ExecutionSummary): string {
  if (exec.status === 'RUNNING') return 'text-blue-700 bg-blue-100'
  if (exec.failed > 0 || exec.broken > 0) return 'text-red-700 bg-red-100'
  if (exec.skipped > 0 && exec.passed === 0) return 'text-slate-600 bg-slate-100'
  return 'text-green-700 bg-green-100'
}

function runStatusLabel(exec: ExecutionSummary): string {
  if (exec.status === 'RUNNING') return 'RUNNING'
  if (exec.failed > 0 || exec.broken > 0) return 'FAILED'
  if (exec.skipped > 0 && exec.passed === 0) return 'SKIPPED'
  return 'PASSED'
}

// ── Execution Runs view ───────────────────────────────────────────────────────

function RunsView({
  filteredExecs,
  isLoading,
  error,
  onRetry,
  base,
}: {
  filteredExecs: ExecutionSummary[]
  isLoading: boolean
  error: Error | null
  onRetry: () => void
  base: string
}) {
  const [search, setSearch] = useState('')

  const filtered = filteredExecs.filter(e => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      e.branch?.toLowerCase().includes(q) ||
      e.environment?.toLowerCase().includes(q) ||
      e.commitSha?.toLowerCase().includes(q)
    )
  })

  const totalRuns    = filtered.length
  const avgPassRate  = totalRuns > 0 ? filtered.reduce((s, e) => s + e.passRate, 0) / totalRuns : 0
  const failedRuns   = filtered.filter(e => e.failed > 0 || e.broken > 0).length
  const totalTests   = filtered.reduce((s, e) => s + e.totalTests, 0)

  return (
    <div className="space-y-4">
      {/* Run-level KPIs */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: 'Total Runs',      value: totalRuns.toString() },
          { label: 'Avg Pass Rate',   value: totalRuns === 0 ? '—' : `${Math.round(avgPassRate * 100)}%`,
            color: avgPassRate >= 0.9 ? 'text-green-700' : avgPassRate >= 0.7 ? 'text-yellow-700' : 'text-red-700' },
          { label: 'Runs with Failures', value: failedRuns.toString(),
            color: failedRuns > 0 ? 'text-red-700' : 'text-slate-900' },
          { label: 'Total Tests Run', value: totalTests.toLocaleString() },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-white rounded-xl border border-slate-200 shadow-sm px-4 py-3">
            <p className="text-xs text-slate-500">{label}</p>
            <p className={cn('text-2xl font-bold mt-0.5', color ?? 'text-slate-900')}>{value}</p>
          </div>
        ))}
      </div>

      {/* Search */}
      <div className="relative max-w-sm">
        <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          placeholder="Filter by branch, environment or commit…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full pl-8 pr-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Runs table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="grid grid-cols-[1fr_110px_180px_80px_80px_40px] gap-3 px-4 py-2.5
                        text-xs font-semibold text-slate-500 uppercase tracking-wide
                        border-b border-slate-100 bg-slate-50">
          <span>Run</span>
          <span>Pass Rate</span>
          <span>Results</span>
          <span>Duration</span>
          <span>When</span>
          <span />
        </div>

        {isLoading && <LoadingSpinner message="Loading runs…" />}
        {error     && <ErrorMessage message="Failed to load execution runs." onRetry={onRetry} />}

        {!isLoading && !error && filtered.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-400">
            No execution runs found for this period.
          </p>
        )}

        <div className="divide-y divide-slate-50">
          {filtered.map(exec => (
            <div
              key={exec.id}
              className="grid grid-cols-[1fr_110px_180px_80px_80px_40px] gap-3 px-4 py-3 items-center hover:bg-slate-50 transition-colors"
            >
              {/* Run identity */}
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <Badge label={runStatusLabel(exec)} colorClass={runStatusColor(exec)} />
                  {exec.branch && (
                    <span className="flex items-center gap-1 text-xs text-slate-600 font-mono truncate max-w-[180px]">
                      <GitBranch size={11} className="shrink-0 text-slate-400" />
                      {exec.branch}
                    </span>
                  )}
                  {exec.environment && (
                    <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">
                      {exec.environment}
                    </span>
                  )}
                </div>
                {exec.commitSha && (
                  <p className="text-[11px] text-slate-400 font-mono mt-0.5 truncate">
                    {exec.commitSha.slice(0, 10)}
                  </p>
                )}
              </div>

              {/* Pass rate */}
              <PassRateBar rate={exec.passRate} total={exec.totalTests} />

              {/* Counts */}
              <div className="flex items-center gap-2 text-xs tabular-nums">
                <span className="text-green-700 font-medium">{exec.passed}✓</span>
                {(exec.failed > 0 || exec.broken > 0) && (
                  <span className="text-red-600 font-medium">{exec.failed + exec.broken}✗</span>
                )}
                {exec.skipped > 0 && (
                  <span className="text-slate-400">{exec.skipped} skip</span>
                )}
                <span className="text-slate-300">/ {exec.totalTests}</span>
              </div>

              {/* Duration */}
              <span className="text-xs text-slate-500 tabular-nums">
                {exec.durationMs > 0 ? formatDuration(exec.durationMs) : '—'}
              </span>

              {/* When */}
              <span className="text-xs text-slate-400">{relativeTime(exec.executedAt)}</span>

              {/* Link */}
              <a
                href={`${base}/runs/${exec.runId}`}
                target="_blank"
                rel="noopener noreferrer"
                title="View run detail"
                className="text-slate-300 hover:text-blue-600 transition-colors justify-self-center"
              >
                <ExternalLink size={13} />
              </a>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Filter chip row ───────────────────────────────────────────────────────────

function FilterChipRow({
  icon,
  label,
  items,
  selected,
  onToggle,
  colorActive,
}: {
  icon: React.ReactNode
  label: string
  items: string[]
  selected: string[]
  onToggle: (item: string) => void
  colorActive: string
}) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <span className="flex items-center gap-1 text-[11px] font-semibold text-slate-400 uppercase tracking-wide shrink-0">
        {icon}
        {label}
      </span>
      {items.map(item => (
        <button
          key={item}
          onClick={() => onToggle(item)}
          className={cn(
            'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border transition-colors',
            selected.includes(item)
              ? colorActive
              : 'bg-white text-slate-600 border-slate-200 hover:border-slate-400'
          )}
        >
          {item}
          {selected.includes(item) && <X size={10} className="ml-0.5" />}
        </button>
      ))}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AutomatedTestsPage() {
  const { projectId, base } = useProject()
  const [dateRange, setDateRange]               = useState<DateRange>(makePreset(7, 'Last 7d'))
  const [viewMode, setViewMode]                 = useState<ViewMode>('tests')
  const [search, setSearch]                     = useState('')
  const [statusFilter, setStatusFilter]         = useState<StatusFilter>('ALL')
  const [selectedTags, setSelectedTags]         = useState<string[]>([])
  const [selectedBrowsers, setSelectedBrowsers] = useState<string[]>([])
  const [selectedAnnotations, setSelectedAnnotations] = useState<string[]>([])
  const [labelKey, setLabelKey]                 = useState('')
  const [labelValue, setLabelValue]             = useState('')
  const [specFilePrefix, setSpecFilePrefix]     = useState('')
  const [selected, setSelected]                 = useState<AutomatedTestSummary | null>(null)

  // Executions fetched once, shared by both tabs for consistent KPIs.
  const { data: allExecutions, isLoading: execsLoading, error: execsError, refetch: execsRefetch } = useQuery({
    queryKey: ['executions-full', projectId],
    queryFn: () => api.executions(projectId, 500),
  })

  const filteredExecs: ExecutionSummary[] = (allExecutions ?? []).filter(e => {
    const t = new Date(e.executedAt).getTime()
    return t >= dateRange.from.getTime() && t <= dateRange.to.getTime()
  })

  const execRunCount = filteredExecs.length
  const execPassRate = execRunCount > 0
    ? filteredExecs.reduce((s, e) => s + e.passRate, 0) / execRunCount
    : 0

  // Analytics backend uses days (always relative to now) — from-date drives lookback window.
  const days = Math.max(1, Math.ceil((Date.now() - dateRange.from.getTime()) / 86_400_000))

  const { data: availableTags } = useQuery({
    queryKey: ['automated-test-tags', projectId, days],
    queryFn: () => api.automatedTestTags(projectId, days),
    enabled: viewMode === 'tests',
  })

  const { data: availableBrowsers } = useQuery({
    queryKey: ['automated-test-browsers', projectId, days],
    queryFn: () => api.automatedTestBrowsers(projectId, days),
    enabled: viewMode === 'tests',
  })

  const { data: availableAnnotationTypes } = useQuery({
    queryKey: ['automated-test-annotation-types', projectId, days],
    queryFn: () => api.automatedTestAnnotationTypes(projectId, days),
    enabled: viewMode === 'tests',
  })

  const { data: availableLabelKeys } = useQuery({
    queryKey: ['automated-test-label-keys', projectId, days],
    queryFn: () => api.automatedTestLabelKeys(projectId, days),
    enabled: viewMode === 'tests',
  })

  const { data: availableLabelValues } = useQuery({
    queryKey: ['automated-test-label-values', projectId, days, labelKey],
    queryFn: () => api.automatedTestLabelValues(projectId, days, labelKey),
    enabled: viewMode === 'tests' && labelKey !== '',
  })

  const { data: tests, isLoading: testsLoading, error: testsError, refetch: testsRefetch } = useQuery({
    queryKey: ['automated-tests', projectId, days, search, statusFilter,
               selectedTags, selectedBrowsers, selectedAnnotations,
               labelKey, labelValue, specFilePrefix],
    queryFn: () => api.automatedTests(projectId, {
      days,
      search:          search          || undefined,
      status:          statusFilter,
      tags:            selectedTags.length         > 0 ? selectedTags         : undefined,
      browsers:        selectedBrowsers.length     > 0 ? selectedBrowsers     : undefined,
      annotationTypes: selectedAnnotations.length  > 0 ? selectedAnnotations  : undefined,
      labelKey:        labelKey        || undefined,
      labelValue:      labelValue      || undefined,
      specFile:        specFilePrefix  || undefined,
    }),
    enabled: viewMode === 'tests',
  })

  function toggle(list: string[], setList: (v: string[]) => void, item: string) {
    setList(list.includes(item) ? list.filter(x => x !== item) : [...list, item])
  }

  const hasActiveFilters = selectedTags.length > 0 || selectedBrowsers.length > 0 ||
    selectedAnnotations.length > 0 || labelKey !== '' || specFilePrefix !== ''

  function clearAllFilters() {
    setSelectedTags([])
    setSelectedBrowsers([])
    setSelectedAnnotations([])
    setLabelKey('')
    setLabelValue('')
    setSpecFilePrefix('')
  }

  const uniqueTests = tests?.length ?? 0
  const failingNow  = tests?.filter(t => t.lastStatus === 'FAILED' || t.lastStatus === 'BROKEN').length ?? 0

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Automated Tests</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            All tests discovered from CI ingestion — pass rates, trends, recent results
          </p>
        </div>
        <DateRangePicker value={dateRange} onChange={setDateRange} />
      </div>

      {/* View tabs */}
      <div className="flex border-b border-slate-200">
        <button
          onClick={() => setViewMode('tests')}
          className={cn(
            'flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors',
            viewMode === 'tests'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'
          )}
        >
          <List size={14} />
          By Individual Test
        </button>
        <button
          onClick={() => setViewMode('runs')}
          className={cn(
            'flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors',
            viewMode === 'runs'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'
          )}
        >
          <FlaskConical size={14} />
          By Execution Group
        </button>
      </div>

      {/* ── By Execution Group tab ── */}
      {viewMode === 'runs' && (
        <RunsView
          filteredExecs={filteredExecs}
          isLoading={execsLoading}
          error={execsError}
          onRetry={() => void execsRefetch()}
          base={base}
        />
      )}

      {/* ── By Individual Test tab ── */}
      {viewMode === 'tests' && (<>
        {/* Trend sparklines — one data point per execution run */}
        <TrendSparklines
          filteredExecs={filteredExecs}
          uniqueTests={uniqueTests}
          execPassRate={execPassRate}
          execRunCount={execRunCount}
          failingNow={failingNow}
        />

        {/* Row 1: Search + status chips */}
        <div className="flex items-center gap-3 flex-wrap">
          <div className="relative flex-1 min-w-[200px] max-w-sm">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search by name, suite, or file…"
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div className="flex gap-1">
            {(['ALL', 'PASSED', 'FAILED', 'FLAKY', 'SKIPPED'] as StatusFilter[]).map(f => (
              <button key={f}
                onClick={() => setStatusFilter(f)}
                className={cn(
                  'px-3 py-1.5 text-xs font-medium rounded-lg transition-colors',
                  statusFilter === f
                    ? 'bg-blue-600 text-white'
                    : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
                )}
              >
                {f}
              </button>
            ))}
          </div>
          {hasActiveFilters && (
            <button onClick={clearAllFilters}
              className="text-xs text-slate-400 hover:text-red-500 transition-colors flex items-center gap-1">
              <X size={11} /> Clear filters
            </button>
          )}
        </div>

        {/* Row 2: Tag chips */}
        {(availableTags ?? []).length > 0 && (
          <FilterChipRow
            icon={<Tag size={11} />}
            label="Tags"
            items={availableTags ?? []}
            selected={selectedTags}
            onToggle={item => toggle(selectedTags, setSelectedTags, item)}
            colorActive="bg-violet-600 text-white border-violet-600"
          />
        )}

        {/* Row 3: Browser chips */}
        {(availableBrowsers ?? []).length > 0 && (
          <FilterChipRow
            icon={<Monitor size={11} />}
            label="Browser"
            items={availableBrowsers ?? []}
            selected={selectedBrowsers}
            onToggle={item => toggle(selectedBrowsers, setSelectedBrowsers, item)}
            colorActive="bg-sky-600 text-white border-sky-600"
          />
        )}

        {/* Row 4: Annotation type chips */}
        {(availableAnnotationTypes ?? []).length > 0 && (
          <FilterChipRow
            icon={<span className="text-[10px] font-bold">@</span>}
            label="Annotations"
            items={availableAnnotationTypes ?? []}
            selected={selectedAnnotations}
            onToggle={item => toggle(selectedAnnotations, setSelectedAnnotations, item)}
            colorActive="bg-amber-500 text-white border-amber-500"
          />
        )}

        {/* Row 5: Label key/value + spec file filter */}
        {((availableLabelKeys ?? []).length > 0 || true) && (
          <div className="flex items-center gap-3 flex-wrap">
            {/* Spec file prefix filter */}
            <div className="relative">
              <FileCode size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Spec file prefix…"
                value={specFilePrefix}
                onChange={e => setSpecFilePrefix(e.target.value)}
                className="pl-7 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg w-48 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Label key select */}
            {(availableLabelKeys ?? []).length > 0 && (
              <div className="flex items-center gap-1.5">
                <span className="text-xs text-slate-400">Label:</span>
                <select
                  value={labelKey}
                  onChange={e => { setLabelKey(e.target.value); setLabelValue('') }}
                  className="text-xs border border-slate-200 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                >
                  <option value="">Any key</option>
                  {(availableLabelKeys ?? []).map(k => (
                    <option key={k} value={k}>{k}</option>
                  ))}
                </select>
                {labelKey && (
                  <select
                    value={labelValue}
                    onChange={e => setLabelValue(e.target.value)}
                    className="text-xs border border-slate-200 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                  >
                    <option value="">Any value</option>
                    {(availableLabelValues ?? []).map(v => (
                      <option key={v} value={v}>{v}</option>
                    ))}
                  </select>
                )}
                {labelKey && (
                  <button onClick={() => { setLabelKey(''); setLabelValue('') }}
                    className="text-slate-300 hover:text-slate-500 transition-colors">
                    <X size={12} />
                  </button>
                )}
              </div>
            )}
          </div>
        )}

        {/* Main split layout */}
        <div className={cn('flex gap-4', selected ? 'items-start' : '')}>
          {/* Test list */}
          <div className={cn('bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden min-w-0',
            selected ? 'flex-[3]' : 'flex-1')}>

            {/* Table header */}
            <div className="grid grid-cols-[1fr_120px_60px_80px_80px_70px] gap-3 px-4 py-2.5
                            text-xs font-semibold text-slate-500 uppercase tracking-wide
                            border-b border-slate-100 bg-slate-50">
              <span>Test</span>
              <span>Pass Rate</span>
              <span className="text-right">Runs</span>
              <span>Last Status</span>
              <span>Last Run</span>
              <span className="text-right">Avg</span>
            </div>

            {testsLoading && <LoadingSpinner message="Loading tests…" />}
            {testsError  && <ErrorMessage message="Failed to load automated tests." onRetry={() => void testsRefetch()} />}

            {!testsLoading && !testsError && (tests ?? []).length === 0 && (
              <p className="px-4 py-12 text-center text-sm text-slate-400">
                No automated tests found. Run your Playwright suite and stream results to the platform.
              </p>
            )}

            <div className="divide-y divide-slate-50">
              {(tests ?? []).map(t => {
                const isActive = selected?.testId === t.testId
                return (
                  <button
                    key={t.testId}
                    onClick={() => setSelected(isActive ? null : t)}
                    className={cn(
                      'w-full text-left grid grid-cols-[1fr_120px_60px_80px_80px_70px] gap-3 px-4 py-3',
                      'transition-colors hover:bg-slate-50 items-center',
                      isActive && 'bg-blue-50 hover:bg-blue-50',
                    )}
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5">
                        <p className="text-sm font-medium text-slate-900 truncate">{t.displayName}</p>
                        {t.hasScreenshot && (
                          <span title="Has screenshot"><Camera size={11} className="shrink-0 text-slate-400" /></span>
                        )}
                        {t.hasVideo && (
                          <span title="Has video"><Video size={11} className="shrink-0 text-slate-400" /></span>
                        )}
                      </div>
                      {t.suiteName && (
                        <p className="text-xs text-slate-400 truncate mt-0.5">{t.suiteName}</p>
                      )}
                      {t.specFile && (
                        <p className="text-[11px] text-slate-400 font-mono truncate mt-0.5"
                           title={t.specFile}>
                          {t.specFile}
                        </p>
                      )}
                      {(t.tags.length > 0 || t.browsers.length > 0 || t.annotationTypes.length > 0) && (
                        <div className="flex flex-wrap gap-1 mt-1">
                          {t.tags.map(tag => (
                            <span
                              key={`tag:${tag}`}
                              onClick={e => { e.stopPropagation(); toggle(selectedTags, setSelectedTags, tag) }}
                              className={cn(
                                'inline-block px-1.5 py-0.5 rounded text-[10px] font-mono cursor-pointer transition-colors',
                                selectedTags.includes(tag)
                                  ? 'bg-violet-100 text-violet-700 ring-1 ring-violet-300'
                                  : 'bg-slate-100 text-slate-500 hover:bg-violet-100 hover:text-violet-700'
                              )}
                            >
                              {tag}
                            </span>
                          ))}
                          {t.browsers.map(b => (
                            <span
                              key={`br:${b}`}
                              onClick={e => { e.stopPropagation(); toggle(selectedBrowsers, setSelectedBrowsers, b) }}
                              className={cn(
                                'inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] cursor-pointer transition-colors',
                                selectedBrowsers.includes(b)
                                  ? 'bg-sky-100 text-sky-700 ring-1 ring-sky-300'
                                  : 'bg-slate-100 text-slate-500 hover:bg-sky-100 hover:text-sky-700'
                              )}
                            >
                              <Monitor size={9} />
                              {b}
                            </span>
                          ))}
                          {t.annotationTypes.map(a => (
                            <span
                              key={`ann:${a}`}
                              onClick={e => { e.stopPropagation(); toggle(selectedAnnotations, setSelectedAnnotations, a) }}
                              className={cn(
                                'inline-block px-1.5 py-0.5 rounded text-[10px] cursor-pointer transition-colors',
                                selectedAnnotations.includes(a)
                                  ? 'bg-amber-100 text-amber-700 ring-1 ring-amber-300'
                                  : 'bg-slate-100 text-slate-500 hover:bg-amber-100 hover:text-amber-700'
                              )}
                            >
                              @{a}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                    <PassRateBar rate={t.passRate} total={t.totalRuns} />
                    <span className="text-sm text-slate-600 text-right tabular-nums">{t.totalRuns}</span>
                    <Badge label={t.lastStatus} colorClass={lastStatusColor(t.lastStatus)} />
                    <span className="text-xs text-slate-400">{relativeTime(t.lastRunAt)}</span>
                    <span className="text-xs text-slate-400 text-right tabular-nums">
                      {formatDuration(t.avgDurationMs)}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Detail panel */}
          {selected && (
            <div className="flex-[2] bg-white rounded-xl border border-slate-200 shadow-sm p-5 sticky top-4"
                 style={{ maxHeight: 'calc(100vh - 120px)', overflowY: 'auto' }}>
              <DetailPanel
                test={selected}
                projectId={projectId}
                base={base}
                onClose={() => setSelected(null)}
              />
            </div>
          )}
        </div>
      </>)}
    </div>
  )
}
