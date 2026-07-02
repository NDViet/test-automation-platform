import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { usePageWidth } from '@/components/layout/PageWidth'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { FlakinessItem, FailureAnalysis, IntegrationConfig } from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, Input } from '@/components/ui'
import {
  Zap,
  RefreshCw,
  Sparkles,
  X,
  GitBranch,
  AlertTriangle,
  Link2,
  ChevronRight,
  Loader2,
  CheckCircle,
} from 'lucide-react'

type Classification = FlakinessItem['classification']

function classColor(c: Classification) {
  switch (c) {
    case 'CRITICAL_FLAKY':
      return 'text-danger bg-danger-bg'
    case 'FLAKY':
      return 'text-warning bg-warning-bg'
    case 'WATCH':
      return 'text-info bg-info-bg'
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

function scoreBarColor(c: Classification) {
  switch (c) {
    case 'CRITICAL_FLAKY':
      return 'bg-danger'
    case 'FLAKY':
      return 'bg-warning'
    case 'WATCH':
      return 'bg-info'
    default:
      return 'bg-border-strong'
  }
}

function shortTestId(testId: string): { cls: string; method: string } {
  const dot = testId.lastIndexOf('.')
  const hash = testId.indexOf('#')
  if (hash > 0) {
    const cls = testId.substring(dot + 1, hash)
    const method = testId.substring(hash + 1)
    return { cls, method }
  }
  if (dot > 0) {
    return { cls: testId.substring(dot + 1), method: '' }
  }
  return { cls: testId, method: '' }
}

function categoryColor(category: string) {
  switch (category?.toUpperCase()) {
    case 'FLAKY_TEST':
      return 'text-warning bg-warning-bg'
    case 'APPLICATION_BUG':
      return 'text-danger bg-danger-bg'
    case 'TEST_DEFECT':
      return 'text-info bg-info-bg'
    case 'ENVIRONMENT':
      return 'text-primary-subtle-fg bg-primary-subtle'
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

// ── Fix Target Modal ──────────────────────────────────────────────────────────

function FixTargetModal({
  projectId,
  item,
  onConfirm,
  onClose,
}: {
  projectId: string
  item: FlakinessItem
  onConfirm: (githubConfigId: string) => void
  onClose: () => void
}) {
  const navigate = useNavigate()
  const { base } = useProject()
  const { data: integrations, isLoading } = useQuery({
    queryKey: ['integrations', projectId],
    queryFn: () => api.integrations(projectId),
  })

  const repos = (integrations ?? []).filter(
    (c: IntegrationConfig) =>
      c.integrationType === 'GITHUB' && c.repoType === 'TEST_AUTOMATION' && c.enabled,
  )
  const [selectedRepo, setSelectedRepo] = useState<string>(repos.length === 1 ? repos[0].id : '')

  function repoLabel(cfg: IntegrationConfig) {
    return cfg.displayName || cfg.connectionParams?.repo || cfg.id
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md mx-4">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <Sparkles size={16} className="text-primary" />
            <h2 className="text-sm font-semibold text-fg">Fix with AI</h2>
          </div>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
            <X size={16} />
          </button>
        </div>

        <div className="px-5 py-4 space-y-4">
          <div className="bg-surface-muted rounded-lg px-4 py-3 text-xs text-fg font-mono break-all">
            {item.testId}
          </div>

          {isLoading ? (
            <div className="py-4 flex justify-center">
              <Loader2 size={18} className="animate-spin text-fg-subtle" />
            </div>
          ) : repos.length === 0 ? (
            <div className="space-y-3">
              <div className="flex gap-3 bg-warning-bg border border-warning-border rounded-lg px-4 py-3 text-sm text-warning">
                <AlertTriangle size={15} className="shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium">No test automation repo linked</p>
                  <p className="text-xs mt-0.5">
                    Link a GitHub repository with role <strong>Test Automation</strong> in Project
                    Settings.
                  </p>
                </div>
              </div>
              <button
                onClick={() => navigate(`${base}/settings`)}
                className="w-full py-2 text-sm font-medium text-primary bg-primary-subtle border border-primary-subtle rounded-lg hover:brightness-95 transition-all flex items-center justify-center gap-2"
              >
                <Link2 size={14} /> Go to Project Settings
              </button>
            </div>
          ) : repos.length === 1 ? (
            <div className="space-y-2">
              <p className="text-sm text-fg-muted">
                Claude will read the failing test, generate a minimal fix, and raise a draft PR to:
              </p>
              <div className="flex items-center gap-3 bg-surface-muted border border-border rounded-lg px-4 py-3">
                <GitBranch size={15} className="text-fg-muted shrink-0" />
                <div>
                  <p className="text-sm font-medium text-fg">{repoLabel(repos[0])}</p>
                  {repos[0].connectionParams?.repo && (
                    <p className="text-xs text-fg-muted font-mono">
                      {repos[0].connectionParams.repo}
                    </p>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              <p className="text-sm text-fg-muted">Select the target repository for the fix PR:</p>
              {repos.map((cfg: IntegrationConfig) => (
                <label
                  key={cfg.id}
                  className={cn(
                    'flex items-center gap-3 px-4 py-3 rounded-lg border cursor-pointer transition-colors',
                    selectedRepo === cfg.id
                      ? 'border-primary/40 bg-primary-subtle'
                      : 'border-border hover:border-border-strong',
                  )}
                >
                  <input
                    type="radio"
                    name="fixRepo"
                    value={cfg.id}
                    checked={selectedRepo === cfg.id}
                    onChange={() => setSelectedRepo(cfg.id)}
                    className="accent-primary"
                  />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-fg">{repoLabel(cfg)}</p>
                    {cfg.connectionParams?.repo && (
                      <p className="text-xs text-fg-muted font-mono">{cfg.connectionParams.repo}</p>
                    )}
                  </div>
                </label>
              ))}
            </div>
          )}
        </div>

        {repos.length > 0 && (
          <div className="px-5 py-4 border-t border-border flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button
              onClick={() =>
                (selectedRepo || repos[0]?.id) && onConfirm(selectedRepo || repos[0]?.id)
              }
              disabled={!selectedRepo && repos.length > 1}
            >
              <Sparkles size={13} /> Fix with AI
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Detail Panel ──────────────────────────────────────────────────────────────

function DetailPanel({
  item,
  analysis,
  projectId,
  onClose,
}: {
  item: FlakinessItem
  analysis: FailureAnalysis | undefined
  projectId: string
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [showFixModal, setShowFixModal] = useState(false)
  const [fixStarted, setFixStarted] = useState(false)

  const fixMutation = useMutation({
    mutationFn: (githubConfigId: string) =>
      api.triggerFlakyFix(projectId, { testId: item.testId, flakyId: item.id, githubConfigId }),
    onSuccess: () => {
      setShowFixModal(false)
      setFixStarted(true)
      void queryClient.invalidateQueries({ queryKey: ['flakiness', projectId] })
    },
  })

  const { cls, method } = shortTestId(item.testId)
  const canFix = item.classification === 'FLAKY' || item.classification === 'CRITICAL_FLAKY'

  return (
    <div className="w-96 shrink-0 border-l border-border bg-surface flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <span className="text-xs font-semibold text-fg-muted uppercase tracking-wider">Detail</span>
        <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
          <X size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-5">
        {/* Test identity */}
        <div>
          <p className="text-xs font-semibold text-fg-muted uppercase tracking-wider mb-1">Test</p>
          <p className="text-sm font-medium text-fg">
            {cls}
            {method ? `#${method}` : ''}
          </p>
          <p className="text-xs text-fg-subtle font-mono mt-0.5 break-all">{item.testId}</p>
        </div>

        {/* Score */}
        <div>
          <p className="text-xs font-semibold text-fg-muted uppercase tracking-wider mb-2">
            Flakiness Score
          </p>
          <div className="flex items-center gap-3 mb-2">
            <div className="flex-1 h-2 bg-surface-muted rounded-full overflow-hidden">
              <div
                className={cn(
                  'h-full rounded-full transition-all',
                  scoreBarColor(item.classification),
                )}
                style={{ width: `${Math.min(item.score * 100, 100)}%` }}
              />
            </div>
            <span className="text-sm font-bold text-fg w-10 text-right">
              {(item.score * 100).toFixed(0)}
            </span>
          </div>
          <Badge
            label={item.classification.replace('_', ' ')}
            colorClass={classColor(item.classification)}
          />
          <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-fg-muted">
            <span>Total runs</span> <span className="font-medium text-fg">{item.totalRuns}</span>
            <span>Failures</span> <span className="font-medium text-fg">{item.failureCount}</span>
            <span>Failure rate</span>{' '}
            <span className="font-medium text-fg">{(item.failureRate * 100).toFixed(1)}%</span>
            {item.lastFailedAt && (
              <>
                <span>Last failed</span>
                <span className="font-medium text-fg">{relativeTime(item.lastFailedAt)}</span>
              </>
            )}
            {item.lastPassedAt && (
              <>
                <span>Last passed</span>
                <span className="font-medium text-fg">{relativeTime(item.lastPassedAt)}</span>
              </>
            )}
          </div>
        </div>

        {/* AI Analysis */}
        {analysis ? (
          <div className="space-y-3">
            <p className="text-xs font-semibold text-fg-muted uppercase tracking-wider">
              AI Analysis
            </p>
            <div className="flex items-center gap-2 flex-wrap">
              <Badge
                label={analysis.category.replace('_', ' ')}
                colorClass={categoryColor(analysis.category)}
              />
              <span className="text-xs text-fg-muted">
                {(analysis.confidence * 100).toFixed(0)}% confidence
              </span>
              {analysis.flakyCandidate && (
                <Badge label="Flaky candidate" colorClass="text-warning bg-warning-bg" />
              )}
            </div>
            <div>
              <p className="text-xs font-semibold text-fg-muted mb-1">Root Cause</p>
              <p className="text-sm text-fg leading-snug">{analysis.rootCause}</p>
            </div>
            <div>
              <p className="text-xs font-semibold text-fg-muted mb-1">Analysis</p>
              <p className="text-sm text-fg leading-snug whitespace-pre-wrap">
                {analysis.detailedAnalysis}
              </p>
            </div>
            <div>
              <p className="text-xs font-semibold text-fg-muted mb-1">Suggested Fix</p>
              <p className="text-sm text-fg leading-snug whitespace-pre-wrap">
                {analysis.suggestedFix}
              </p>
            </div>
            <p className="text-xs text-fg-subtle">Analysed {relativeTime(analysis.analysedAt)}</p>
          </div>
        ) : (
          <div className="text-sm text-fg-subtle italic">
            No AI analysis yet — trigger analysis from the header button.
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="px-4 py-3 border-t border-border space-y-2">
        {fixStarted ? (
          <div className="flex items-center gap-2 text-sm text-success py-1">
            <CheckCircle size={15} className="shrink-0" />
            Fix started — a draft PR will appear in the linked repo shortly.
          </div>
        ) : canFix ? (
          <Button
            className="w-full"
            onClick={() => setShowFixModal(true)}
            loading={fixMutation.isPending}
          >
            {!fixMutation.isPending && <Sparkles size={14} />}
            {fixMutation.isPending ? 'Starting…' : 'Fix with AI'}
          </Button>
        ) : (
          <p className="text-xs text-fg-subtle text-center py-1">
            Fix with AI is available for FLAKY and CRITICAL_FLAKY tests.
          </p>
        )}
        {fixMutation.isError && (
          <p className="text-xs text-danger">{(fixMutation.error as Error).message}</p>
        )}
      </div>

      {showFixModal && (
        <FixTargetModal
          projectId={projectId}
          item={item}
          onConfirm={cfgId => fixMutation.mutate(cfgId)}
          onClose={() => setShowFixModal(false)}
        />
      )}
    </div>
  )
}

// ── Score Bar Cell ────────────────────────────────────────────────────────────

function ScoreBar({ score, classification }: { score: number; classification: Classification }) {
  return (
    <div className="flex items-center gap-2 w-24">
      <div className="flex-1 h-1.5 bg-surface-muted rounded-full overflow-hidden">
        <div
          className={cn('h-full rounded-full', scoreBarColor(classification))}
          style={{ width: `${Math.min(score * 100, 100)}%` }}
        />
      </div>
      <span className="text-xs font-mono text-fg-muted w-7 text-right">
        {(score * 100).toFixed(0)}
      </span>
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

const TABS: { label: string; value: Classification | '' }[] = [
  { label: 'All', value: '' },
  { label: 'Critical', value: 'CRITICAL_FLAKY' },
  { label: 'Flaky', value: 'FLAKY' },
  { label: 'Watch', value: 'WATCH' },
  { label: 'Stable', value: 'STABLE' },
]

export default function FlakyTestsPage() {
  usePageWidth('wide')
  const { projectId } = useProject()
  const qc = useQueryClient()

  const [tab, setTab] = useState<Classification | ''>('')
  const [hidePermanent, setHideP] = useState(true)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<FlakinessItem | null>(null)
  const [recomputeDone, setRDone] = useState(false)

  const {
    data: flakyList,
    isLoading: flakyLoading,
    error: flakyError,
    refetch: flakyRefetch,
  } = useQuery({
    queryKey: ['flakiness', projectId],
    queryFn: () => api.flakiness(projectId!, 100),
    enabled: !!projectId,
  })

  const { data: analyses } = useQuery({
    queryKey: ['analyses', projectId],
    queryFn: () => api.analyses(projectId!),
    enabled: !!projectId,
  })

  const analysisMap = new Map<string, FailureAnalysis>(
    (analyses ?? []).map((a: FailureAnalysis) => [a.testId, a]),
  )

  const recomputeMutation = useMutation({
    mutationFn: () => api.recomputeFlakiness(projectId!),
    onSuccess: () => {
      setRDone(true)
      void qc.invalidateQueries({ queryKey: ['flakiness', projectId] })
      void qc.invalidateQueries({ queryKey: ['analyses', projectId] })
      setTimeout(() => setRDone(false), 4000)
    },
  })

  const items: FlakinessItem[] = flakyList ?? []

  const filtered = items.filter(item => {
    if (tab && item.classification !== tab) return false
    if (hidePermanent) {
      const a = analysisMap.get(item.testId)
      if (a && a.category === 'APPLICATION_BUG' && a.confidence >= 0.8 && !a.flakyCandidate)
        return false
    }
    if (search) {
      return item.testId.toLowerCase().includes(search.toLowerCase())
    }
    return true
  })

  const counts: Record<string, number> = {}
  items.forEach(i => {
    counts[i.classification] = (counts[i.classification] ?? 0) + 1
  })

  if (flakyLoading) return <LoadingSpinner message="Loading flaky tests…" />
  if (flakyError)
    return (
      <ErrorMessage message="Failed to load flakiness data." onRetry={() => void flakyRefetch()} />
    )

  return (
    <div className="flex-1 flex flex-col h-full min-h-0 overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border bg-surface shrink-0">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold tracking-tight text-fg flex items-center gap-2">
              <Zap size={20} className="text-warning" />
              Flaky Tests
            </h1>
            <p className="text-sm text-fg-muted mt-0.5">
              {items.length} tests tracked ·{' '}
              {(counts['CRITICAL_FLAKY'] ?? 0) + (counts['FLAKY'] ?? 0)} actionable
            </p>
          </div>
          <div className="flex items-center gap-3">
            {recomputeDone && (
              <span className="text-xs text-success flex items-center gap-1">
                <CheckCircle size={13} /> Scores refreshed
              </span>
            )}
            <Button
              onClick={() => {
                void recomputeMutation.mutate()
                void api.analyseNow(48)
              }}
              loading={recomputeMutation.isPending}
            >
              {!recomputeMutation.isPending && <RefreshCw size={14} />}
              {recomputeMutation.isPending ? 'Analysing…' : 'Trigger Analysis'}
            </Button>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex items-center gap-1 mt-4">
          {TABS.map(t => (
            <button
              key={t.value}
              onClick={() => setTab(t.value)}
              className={cn(
                'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
                tab === t.value
                  ? 'bg-primary text-primary-fg'
                  : 'text-fg-muted hover:bg-surface-muted',
              )}
            >
              {t.label}
              {t.value && counts[t.value] != null && (
                <span
                  className={cn(
                    'ml-1.5 text-xs px-1.5 py-0.5 rounded-full',
                    tab === t.value ? 'bg-white/25 text-white' : 'bg-surface-muted text-fg-muted',
                  )}
                >
                  {counts[t.value]}
                </span>
              )}
            </button>
          ))}

          <div className="ml-auto flex items-center gap-4">
            {/* Hide permanent failures toggle */}
            <label className="flex items-center gap-2 text-xs text-fg-muted cursor-pointer select-none">
              <button
                role="switch"
                aria-checked={hidePermanent}
                onClick={() => setHideP(v => !v)}
                className={cn(
                  'relative inline-flex h-5 w-9 shrink-0 rounded-full border-2 border-transparent transition-colors',
                  hidePermanent ? 'bg-primary' : 'bg-border-strong',
                )}
              >
                <span
                  className={cn(
                    'inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform',
                    hidePermanent ? 'translate-x-4' : 'translate-x-0',
                  )}
                />
              </button>
              Hide permanent failures
            </label>

            <Input
              type="search"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search tests…"
              aria-label="Search tests"
              className="w-52"
            />
          </div>
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 flex min-h-0 overflow-hidden">
        {/* List */}
        <div className={cn('flex-1 overflow-y-auto', selected ? 'border-r border-border' : '')}>
          {filtered.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-center gap-3">
              <Zap size={36} className="text-fg-subtle" />
              <p className="text-fg-muted text-sm">
                {items.length === 0
                  ? 'No flakiness data yet — run tests and trigger an analysis.'
                  : 'No tests match the current filters.'}
              </p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-surface border-b border-border z-10">
                <tr className="text-xs font-semibold text-fg-muted uppercase tracking-wider">
                  <th className="text-left px-5 py-3">Test</th>
                  <th className="text-left px-4 py-3">Score</th>
                  <th className="text-left px-4 py-3">Classification</th>
                  <th className="text-right px-4 py-3">Failures</th>
                  <th className="text-right px-4 py-3">Rate</th>
                  <th className="text-left px-4 py-3">AI Category</th>
                  <th className="text-left px-4 py-3">Last Failed</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.map(item => {
                  const analysis = analysisMap.get(item.testId)
                  const { cls, method } = shortTestId(item.testId)
                  const isSelected = selected?.id === item.id
                  const canFix =
                    item.classification === 'FLAKY' || item.classification === 'CRITICAL_FLAKY'

                  return (
                    <tr
                      key={item.id}
                      onClick={() => setSelected(isSelected ? null : item)}
                      className={cn(
                        'cursor-pointer hover:bg-surface-muted transition-colors',
                        isSelected && 'bg-primary-subtle hover:bg-primary-subtle',
                      )}
                    >
                      <td className="px-5 py-3">
                        <p className="font-medium text-fg truncate max-w-xs">{cls}</p>
                        {method && (
                          <p className="text-xs text-fg-subtle font-mono truncate max-w-xs">
                            #{method}
                          </p>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <ScoreBar score={item.score} classification={item.classification} />
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          label={item.classification.replace('_', ' ')}
                          colorClass={classColor(item.classification)}
                        />
                      </td>
                      <td className="px-4 py-3 text-right text-fg">{item.failureCount}</td>
                      <td className="px-4 py-3 text-right text-fg">
                        {(item.failureRate * 100).toFixed(1)}%
                      </td>
                      <td className="px-4 py-3">
                        {analysis ? (
                          <Badge
                            label={analysis.category.replace('_', ' ')}
                            colorClass={categoryColor(analysis.category)}
                          />
                        ) : (
                          <span className="text-xs text-fg-subtle">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-fg-muted text-xs whitespace-nowrap">
                        {item.lastFailedAt ? relativeTime(item.lastFailedAt) : '—'}
                      </td>
                      <td className="px-4 py-3">
                        {canFix && (
                          <button
                            onClick={e => {
                              e.stopPropagation()
                              setSelected(item)
                            }}
                            className="flex items-center gap-1 text-xs text-primary hover:underline font-medium whitespace-nowrap"
                          >
                            <Sparkles size={12} /> Fix
                            <ChevronRight size={12} />
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* Detail panel */}
        {selected && (
          <DetailPanel
            item={selected}
            analysis={analysisMap.get(selected.testId)}
            projectId={projectId!}
            onClose={() => setSelected(null)}
          />
        )}
      </div>
    </div>
  )
}
