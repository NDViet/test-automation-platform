import { useState, useEffect, useRef, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type {
  ImpactAnalysis,
  ImpactAnalysisSuggestion,
  CodabasePr,
  Requirement,
  LinkedPr,
  CreateImpactAnalysisForm,
} from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, Input, Select } from '@/components/ui'
import {
  GitPullRequest,
  ChevronRight,
  Plus,
  X,
  ExternalLink,
  ChevronDown,
  ChevronUp,
  Loader2,
  Sparkles,
  RefreshCw,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Clock,
  ArrowRight,
  FileText,
  Code2,
  PlusCircle,
  Pencil,
} from 'lucide-react'

// ── Status helpers ─────────────────────────────────────────────────────────────

const STATUS_META: Record<string, { color: string; icon: React.ReactNode }> = {
  COMPLETED: { color: 'text-success bg-success-bg', icon: <CheckCircle size={13} /> },
  FAILED: { color: 'text-danger bg-danger-bg', icon: <XCircle size={13} /> },
  RUNNING: {
    color: 'text-info bg-info-bg',
    icon: <Loader2 size={13} className="animate-spin" />,
  },
  DRAFT: { color: 'text-neutral bg-neutral-bg', icon: <Clock size={13} /> },
}
function statusMeta(s: string) {
  return STATUS_META[s] ?? STATUS_META.DRAFT
}

function priorityColor(p: string) {
  switch (p) {
    case 'HIGH':
      return 'text-danger bg-danger-bg'
    case 'MEDIUM':
      return 'text-warning bg-warning-bg'
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

function suggestionIcon(type: ImpactAnalysisSuggestion['type']) {
  switch (type) {
    case 'UPDATE_MANUAL_TEST':
      return <FileText size={15} className="text-info shrink-0" />
    case 'CREATE_AUTOMATED_TEST':
      return <PlusCircle size={15} className="text-success shrink-0" />
    case 'UPDATE_AUTOMATION':
      return <Code2 size={15} className="text-teal-600 shrink-0" />
  }
}

function suggestionLabel(type: ImpactAnalysisSuggestion['type']) {
  switch (type) {
    case 'UPDATE_MANUAL_TEST':
      return 'Update Manual Test'
    case 'CREATE_AUTOMATED_TEST':
      return 'Create Automated Test'
    case 'UPDATE_AUTOMATION':
      return 'Update Automation'
  }
}

// ── Requirement tree helpers ──────────────────────────────────────────────────

type ReqTree = Requirement & { children: ReqTree[] }

function buildTree(reqs: Requirement[]): ReqTree[] {
  const map = new Map<string, ReqTree>()
  reqs.forEach(r => map.set(r.id, { ...r, children: [] }))
  const roots: ReqTree[] = []
  map.forEach(node => {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

function collectIds(node: ReqTree): string[] {
  return [node.id, ...node.children.flatMap(collectIds)]
}

type CheckState = 'checked' | 'unchecked' | 'indeterminate'

function nodeCheckState(node: ReqTree, selected: Set<string>): CheckState {
  const ids = collectIds(node)
  const checkedCount = ids.filter(id => selected.has(id)).length
  if (checkedCount === 0) return 'unchecked'
  if (checkedCount === ids.length) return 'checked'
  return 'indeterminate'
}

function TreeCheckbox({ state, onChange }: { state: CheckState; onChange: () => void }) {
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (ref.current) {
      ref.current.indeterminate = state === 'indeterminate'
    }
  }, [state])
  return (
    <input
      ref={ref}
      type="checkbox"
      checked={state === 'checked'}
      onChange={onChange}
      className="rounded border-border-strong accent-primary cursor-pointer"
    />
  )
}

function ReqTreeRow({
  node,
  selected,
  expanded,
  onToggleSelect,
  onToggleExpand,
  indent,
}: {
  node: ReqTree
  selected: Set<string>
  expanded: Set<string>
  onToggleSelect: (node: ReqTree) => void
  onToggleExpand: (id: string) => void
  indent: number
}) {
  const hasChildren = node.children.length > 0
  const isExpanded = expanded.has(node.id)
  const state = nodeCheckState(node, selected)

  return (
    <>
      <div
        className="flex items-center gap-2 px-3 py-1.5 hover:bg-surface-muted rounded"
        style={{ paddingLeft: `${12 + indent * 20}px` }}
      >
        <TreeCheckbox state={state} onChange={() => onToggleSelect(node)} />
        {hasChildren ? (
          <button onClick={() => onToggleExpand(node.id)} className="text-fg-subtle hover:text-fg">
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
        ) : (
          <span className="w-[14px]" />
        )}
        <span className="text-sm text-fg truncate flex-1">{node.title}</span>
        <span className="text-xs text-fg-subtle shrink-0">{node.issueType}</span>
      </div>
      {hasChildren &&
        isExpanded &&
        node.children.map(child => (
          <ReqTreeRow
            key={child.id}
            node={child}
            selected={selected}
            expanded={expanded}
            onToggleSelect={onToggleSelect}
            onToggleExpand={onToggleExpand}
            indent={indent + 1}
          />
        ))}
    </>
  )
}

// ── Create Modal ──────────────────────────────────────────────────────────────

function CreateModal({
  projectId,
  existing,
  onClose,
  onSubmit,
}: {
  projectId: string
  existing?: ImpactAnalysis
  onClose: () => void
  onSubmit: (form: CreateImpactAnalysisForm) => void
}) {
  const isEdit = !!existing
  const [step, setStep] = useState<'prs' | 'reqs'>('prs')
  const [name, setName] = useState(existing?.name ?? 'Impact Analysis')
  const [selectedPrs, setSelectedPrs] = useState<Set<string>>(
    () => new Set((existing?.linkedPrs ?? []).map(p => `${p.repoFullName}#${p.prNumber}`)),
  )
  const [selectedReqs, setSelectedReqs] = useState<Set<string>>(
    () => new Set(existing?.linkedRequirementIds ?? []),
  )
  const [expandedReqs, setExpandedReqs] = useState<Set<string>>(new Set())
  const [reqSearch, setReqSearch] = useState('')
  const [repoFilter, setRepoFilter] = useState('') // '' = all repos
  const [prSearch, setPrSearch] = useState('')
  const [customPrompt, setCustomPrompt] = useState(existing?.customPrompt ?? '')

  // Preserve details for already-linked PRs that may no longer be in the open-PR list.
  const existingByKey = useMemo(
    () => new Map((existing?.linkedPrs ?? []).map(p => [`${p.repoFullName}#${p.prNumber}`, p])),
    [existing],
  )

  const { data: prs, isLoading: prsLoading } = useQuery({
    queryKey: ['codebase-prs', projectId],
    queryFn: () => api.codebasePrs(projectId),
    enabled: !!projectId,
  })

  const { data: reqs, isLoading: reqsLoading } = useQuery({
    queryKey: ['requirements', projectId],
    queryFn: () => api.requirements(projectId),
    enabled: !!projectId,
  })

  // Whether any Codebase repo is configured — via the GitHub tab (repo assignments) or the
  // Integrations tab (GITHUB config, repoType=CODEBASE). Lets the empty state tell "not configured"
  // apart from "configured, but no open PRs right now".
  const { data: repoAssignments = [] } = useQuery({
    queryKey: ['project-github-repos', projectId],
    queryFn: () => api.projectGitHubRepos(projectId),
    enabled: !!projectId,
  })
  const { data: projectIntegrations = [] } = useQuery({
    queryKey: ['integrations', projectId],
    queryFn: () => api.integrations(projectId),
    enabled: !!projectId,
  })
  const codebaseConfigured =
    repoAssignments.some(a => a.role === 'CODEBASE') ||
    projectIntegrations.some(
      c => c.integrationType === 'GITHUB' && c.repoType === 'CODEBASE' && c.enabled,
    )

  // Auto-expand top-level nodes on first load
  const autoExpanded = useRef(false)
  useEffect(() => {
    if (reqs && !autoExpanded.current) {
      autoExpanded.current = true
      const tree = buildTree(reqs)
      setExpandedReqs(new Set(tree.filter(n => n.children.length > 0).map(n => n.id)))
    }
  }, [reqs])

  const prList = Array.isArray(prs) ? prs : []
  const reqList = Array.isArray(reqs) ? reqs : []
  const reqTree = buildTree(reqList)

  // Repo filter + PR search over the loaded PRs (multiple repos in one job).
  const repoOptions = [...new Set(prList.map(pr => pr.repoFullName))].sort()
  const shownPrs = prList.filter(pr => {
    if (repoFilter && pr.repoFullName !== repoFilter) return false
    if (prSearch) {
      const q = prSearch.toLowerCase()
      return pr.title.toLowerCase().includes(q) || String(pr.number).includes(q)
    }
    return true
  })

  const filteredReqs = reqSearch
    ? reqList.filter(
        r =>
          r.title.toLowerCase().includes(reqSearch.toLowerCase()) ||
          (r.externalId && r.externalId.toLowerCase().includes(reqSearch.toLowerCase())),
      )
    : null

  function togglePr(key: string) {
    setSelectedPrs(prev => {
      const next = new Set(prev)
      next.has(key) ? next.delete(key) : next.add(key)
      return next
    })
  }

  function toggleReqNode(node: ReqTree) {
    const ids = collectIds(node)
    const allIn = ids.every(id => selectedReqs.has(id))
    setSelectedReqs(prev => {
      const next = new Set(prev)
      if (allIn) {
        ids.forEach(id => next.delete(id))
      } else {
        ids.forEach(id => next.add(id))
      }
      return next
    })
  }

  function toggleExpandReq(id: string) {
    setExpandedReqs(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  function buildPrKey(pr: CodabasePr) {
    return `${pr.repoFullName}#${pr.number}`
  }

  function handleSubmit() {
    // Build from every selected key — fresh open PRs (with body) or previously-linked PRs that
    // may have since closed (from the existing job), so editing never silently drops a PR.
    const linked: LinkedPr[] = Array.from(selectedPrs)
      .map(key => {
        const pr = prList.find(p => buildPrKey(p) === key)
        if (pr)
          return {
            repoFullName: pr.repoFullName,
            prNumber: pr.number,
            prUrl: pr.html_url,
            prTitle: pr.title,
            prBody: pr.body ?? undefined,
          }
        return existingByKey.get(key)
      })
      .filter((p): p is LinkedPr => !!p)
    onSubmit({
      name,
      linkedPrs: linked,
      linkedRequirementIds: Array.from(selectedReqs),
      customPrompt: customPrompt.trim() || undefined,
    })
  }

  const canProceed = step === 'prs' ? selectedPrs.size > 0 : true

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-2xl shadow-lg w-full max-w-2xl flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="px-6 py-5 border-b border-border flex items-center justify-between">
          <div>
            <h2 className="font-semibold text-fg">
              {isEdit ? 'Edit Impact Analysis' : 'New Impact Analysis'}
            </h2>
            <p className="text-xs text-fg-muted mt-0.5">
              Step {step === 'prs' ? '1' : '2'} of 2 —{' '}
              {step === 'prs' ? 'Select PRs' : 'Requirements & instructions'}
            </p>
          </div>
          <button onClick={onClose} className="text-fg-subtle hover:text-fg">
            <X size={18} />
          </button>
        </div>

        {/* Name field (always visible) */}
        <div className="px-6 pt-4">
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Analysis name"
            className={inputCls}
          />
        </div>

        {/* Step content */}
        <div className="flex-1 overflow-y-auto px-6 py-4">
          {step === 'prs' ? (
            prsLoading ? (
              <LoadingSpinner message="Loading PRs from Codebase repos…" />
            ) : prList.length === 0 ? (
              <div className="text-center py-12 text-sm text-fg-muted">
                <AlertTriangle size={32} className="mx-auto mb-3 text-warning" />
                {codebaseConfigured ? (
                  <>
                    <p className="font-medium">No open pull requests</p>
                    <p className="mt-1">
                      Codebase repos are configured for this project, but none have open PRs right
                      now. Open a PR in a Codebase repo and it will appear here.
                    </p>
                  </>
                ) : (
                  <>
                    <p className="font-medium">No Codebase repos configured</p>
                    <p className="mt-1">
                      In Project Settings, mark a GitHub repo as{' '}
                      <span className="font-mono bg-surface-muted px-1 rounded">Codebase</span> —
                      under the <span className="font-medium">GitHub</span> tab (repo roles) or the{' '}
                      <span className="font-medium">Integrations</span> tab — to list its PRs here.
                    </p>
                  </>
                )}
              </div>
            ) : (
              <div className="space-y-2">
                {/* Repo filter + PR search */}
                <div className="flex gap-2">
                  <Select
                    value={repoFilter}
                    onChange={e => setRepoFilter(e.target.value)}
                    containerClassName="w-56"
                    aria-label="Filter by repository"
                  >
                    <option value="">All repos ({prList.length})</option>
                    {repoOptions.map(r => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </Select>
                  <Input
                    type="search"
                    value={prSearch}
                    onChange={e => setPrSearch(e.target.value)}
                    placeholder="Search PR title or #number…"
                    aria-label="Search pull requests"
                  />
                </div>
                <p className="text-xs text-fg-muted pt-1">
                  {shownPrs.length === prList.length
                    ? 'Select one or more PRs to analyse:'
                    : `Showing ${shownPrs.length} of ${prList.length} PRs`}
                </p>
                {shownPrs.length === 0 && (
                  <p className="px-4 py-6 text-sm text-fg-muted text-center">
                    No PRs match the filter.
                  </p>
                )}
                {shownPrs.map(pr => {
                  const key = buildPrKey(pr)
                  const checked = selectedPrs.has(key)
                  return (
                    <label
                      key={key}
                      className={cn(
                        'flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-colors',
                        checked
                          ? 'border-primary/40 bg-primary-subtle'
                          : 'border-border hover:border-border-strong',
                      )}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => togglePr(key)}
                        className="rounded border-border-strong accent-primary mt-0.5"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-fg truncate">{pr.title}</span>
                          <a
                            href={pr.html_url}
                            target="_blank"
                            rel="noreferrer"
                            onClick={e => e.stopPropagation()}
                            className="text-primary hover:brightness-110 shrink-0"
                          >
                            <ExternalLink size={12} />
                          </a>
                        </div>
                        <p className="text-xs text-fg-muted mt-0.5">
                          {pr.repoFullName} · PR #{pr.number} · {pr.user} · {pr.head_ref} →{' '}
                          {pr.base_ref}
                        </p>
                        {pr.body && (
                          <p className="text-xs text-fg-subtle mt-1 line-clamp-2">{pr.body}</p>
                        )}
                      </div>
                    </label>
                  )
                })}
              </div>
            )
          ) : reqsLoading ? (
            <LoadingSpinner message="Loading requirements…" />
          ) : reqList.length === 0 ? (
            <p className="text-sm text-fg-muted text-center py-12">
              No requirements found for this project.
            </p>
          ) : (
            <>
              <input
                value={reqSearch}
                onChange={e => setReqSearch(e.target.value)}
                placeholder="Search requirements…"
                className={cn(inputCls, 'mb-3')}
              />
              <div className="border border-border rounded-xl overflow-hidden">
                {filteredReqs ? (
                  filteredReqs.length === 0 ? (
                    <p className="px-4 py-6 text-sm text-fg-muted text-center">No matches.</p>
                  ) : (
                    filteredReqs.map(r => (
                      <label
                        key={r.id}
                        className="flex items-center gap-2 px-3 py-2 hover:bg-surface-muted cursor-pointer"
                      >
                        <input
                          type="checkbox"
                          checked={selectedReqs.has(r.id)}
                          onChange={() =>
                            setSelectedReqs(prev => {
                              const next = new Set(prev)
                              next.has(r.id) ? next.delete(r.id) : next.add(r.id)
                              return next
                            })
                          }
                          className="rounded border-border-strong accent-primary"
                        />
                        {r.externalId && (
                          <span className="text-xs font-mono text-primary bg-primary-subtle px-1.5 py-0.5 rounded shrink-0">
                            {r.externalId}
                          </span>
                        )}
                        <span className="text-sm text-fg truncate flex-1">{r.title}</span>
                        <span className="text-xs text-fg-subtle shrink-0">{r.issueType}</span>
                      </label>
                    ))
                  )
                ) : (
                  reqTree.map(node => (
                    <ReqTreeRow
                      key={node.id}
                      node={node}
                      selected={selectedReqs}
                      expanded={expandedReqs}
                      onToggleSelect={toggleReqNode}
                      onToggleExpand={toggleExpandReq}
                      indent={0}
                    />
                  ))
                )}
              </div>
            </>
          )}
        </div>

        {/* Instructions for the AI (step 2) */}
        {step === 'reqs' && (
          <div className="px-6 pb-2">
            <label className="block text-xs font-medium text-fg-muted mb-1">
              Instructions for the AI <span className="font-normal text-fg-subtle">(optional)</span>
            </label>
            <textarea
              value={customPrompt}
              onChange={e => setCustomPrompt(e.target.value)}
              rows={3}
              placeholder="e.g. Focus on payment edge cases; ignore doc-only changes."
              className={cn(inputCls, 'resize-none')}
              aria-label="Instructions for the AI"
            />
          </div>
        )}

        {/* Footer */}
        <div className="px-6 py-4 border-t border-border flex items-center justify-between">
          <div className="text-sm text-fg-muted">
            {step === 'prs'
              ? `${selectedPrs.size} PR${selectedPrs.size !== 1 ? 's' : ''} selected`
              : selectedReqs.size > 0
                ? `${selectedReqs.size} requirement${selectedReqs.size !== 1 ? 's' : ''} selected`
                : 'No requirements selected (optional)'}
          </div>
          <div className="flex gap-3">
            {step === 'reqs' && (
              <Button variant="secondary" onClick={() => setStep('prs')}>
                Back
              </Button>
            )}
            {step === 'prs' ? (
              <Button disabled={!canProceed} onClick={() => setStep('reqs')}>
                Next: Requirements
                <ArrowRight size={14} />
              </Button>
            ) : (
              <Button disabled={!canProceed} onClick={handleSubmit}>
                <Sparkles size={14} />
                {isEdit ? 'Save & Re-run' : 'Create & Analyse'}
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Suggestion Card ───────────────────────────────────────────────────────────

function SuggestionCard({
  suggestion,
  analysisId,
  projectId,
}: {
  suggestion: ImpactAnalysisSuggestion
  analysisId: string
  projectId: string
}) {
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [applying, setApplying] = useState(false)
  const [tcSearch, setTcSearch] = useState('')
  const [applied, setApplied] = useState(false)

  const { data: testCases = [], isLoading: tcLoading } = useQuery({
    queryKey: ['testCases', projectId],
    queryFn: () => api.testCases(projectId),
    enabled: applying && !suggestion.testCaseId,
  })

  const applyMutation = useMutation({
    mutationFn: (tcId: string) =>
      api.applyImpactSuggestion(projectId, tcId, {
        analysisId,
        title: suggestion.title,
        description: suggestion.details ?? undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['testCases', projectId] })
      setApplying(false)
      setApplied(true)
    },
  })

  const filteredTcs = testCases.filter(
    tc =>
      tc.title.toLowerCase().includes(tcSearch.toLowerCase()) ||
      (tc.externalId ?? '').toLowerCase().includes(tcSearch.toLowerCase()),
  )

  return (
    <div className="border border-border rounded-xl p-4 space-y-2">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          {suggestionIcon(suggestion.type)}
          <span className="text-sm font-medium text-fg truncate">{suggestion.title}</span>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span
            className={cn(
              'text-xs px-2 py-0.5 rounded-full font-medium',
              priorityColor(suggestion.priority),
            )}
          >
            {suggestion.priority}
          </span>
          <Badge
            label={suggestionLabel(suggestion.type)}
            colorClass={
              suggestion.type === 'UPDATE_MANUAL_TEST'
                ? 'text-info bg-info-bg'
                : suggestion.type === 'CREATE_AUTOMATED_TEST'
                  ? 'text-success bg-success-bg'
                  : 'text-teal-700 bg-teal-100'
            }
          />
        </div>
      </div>

      <p className="text-xs text-fg-muted leading-relaxed">{suggestion.reason}</p>

      {suggestion.details && (
        <>
          <button
            onClick={() => setExpanded(e => !e)}
            className="flex items-center gap-1 text-xs text-primary hover:underline"
          >
            {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
            {expanded ? 'Hide details' : 'Show details'}
          </button>
          {expanded && (
            <pre className="text-xs bg-surface-muted border border-border rounded-lg p-3 whitespace-pre-wrap font-mono leading-relaxed text-fg">
              {suggestion.details}
            </pre>
          )}
        </>
      )}

      {/* Apply action for UPDATE_MANUAL_TEST */}
      {suggestion.type === 'UPDATE_MANUAL_TEST' && (
        <div className="pt-1">
          {applied ? (
            <span className="inline-flex items-center gap-1 text-xs text-success font-medium">
              <CheckCircle size={12} /> Applied — test case is now under review
            </span>
          ) : suggestion.testCaseId ? (
            <button
              onClick={() => applyMutation.mutate(suggestion.testCaseId!)}
              disabled={applyMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-primary border border-primary-subtle rounded-lg hover:bg-primary-subtle disabled:opacity-50"
            >
              {applyMutation.isPending ? (
                <Loader2 size={11} className="animate-spin" />
              ) : (
                <ArrowRight size={11} />
              )}
              Apply to linked test case
            </button>
          ) : applying ? (
            <div className="border border-border rounded-lg overflow-hidden">
              <div className="flex items-center gap-1 px-2 py-1.5 border-b border-border bg-surface-muted">
                <span className="text-xs text-fg-muted font-medium flex-1">
                  Select test case to update
                </span>
                <button onClick={() => setApplying(false)} className="text-fg-subtle hover:text-fg">
                  <X size={12} />
                </button>
              </div>
              <div className="px-2 py-1.5 border-b border-border">
                <input
                  autoFocus
                  value={tcSearch}
                  onChange={e => setTcSearch(e.target.value)}
                  placeholder="Search test cases…"
                  className="w-full text-xs bg-transparent text-fg placeholder:text-fg-subtle outline-none"
                />
              </div>
              <div className="max-h-36 overflow-y-auto">
                {tcLoading ? (
                  <div className="flex justify-center py-3">
                    <Loader2 size={14} className="animate-spin text-fg-subtle" />
                  </div>
                ) : filteredTcs.length === 0 ? (
                  <p className="text-xs text-fg-subtle px-3 py-2">No test cases found</p>
                ) : (
                  filteredTcs.slice(0, 10).map(tc => (
                    <button
                      key={tc.id}
                      onClick={() => applyMutation.mutate(tc.id)}
                      disabled={applyMutation.isPending}
                      className="w-full text-left px-3 py-1.5 text-xs hover:bg-primary-subtle flex items-center gap-2 disabled:opacity-50"
                    >
                      <span
                        className={cn(
                          'shrink-0 px-1.5 py-0.5 rounded text-[10px] font-medium',
                          tc.status === 'APPROVED'
                            ? 'bg-success-bg text-success'
                            : tc.status === 'DRAFT'
                              ? 'bg-neutral-bg text-neutral'
                              : 'bg-warning-bg text-warning',
                        )}
                      >
                        {tc.status}
                      </span>
                      <span className="text-fg truncate">{tc.title}</span>
                    </button>
                  ))
                )}
              </div>
            </div>
          ) : (
            <button
              onClick={() => setApplying(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-primary border border-primary-subtle rounded-lg hover:bg-primary-subtle"
            >
              <ArrowRight size={11} />
              Apply to test case
            </button>
          )}
        </div>
      )}
    </div>
  )
}

// ── Detail Panel ──────────────────────────────────────────────────────────────

function DetailPanel({
  analysis,
  projectId,
  onClose,
  onEdit,
}: {
  analysis: ImpactAnalysis
  projectId: string
  onClose: () => void
  onEdit: () => void
}) {
  const { color: sc, icon: si } = statusMeta(analysis.status)
  const suggestions = analysis.suggestions?.suggestions ?? []

  return (
    <div className="bg-surface rounded-xl border border-border shadow-sm flex flex-col h-full">
      {/* Header */}
      <div className="px-5 py-4 border-b border-border flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span
              className={cn(
                'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium',
                sc,
              )}
            >
              {si}
              {analysis.status}
            </span>
          </div>
          <h3 className="font-semibold text-fg text-sm">{analysis.name}</h3>
          <p className="text-xs text-fg-muted mt-0.5">{relativeTime(analysis.createdAt)}</p>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={onEdit}
            disabled={analysis.status === 'RUNNING'}
            title={
              analysis.status === 'RUNNING'
                ? 'Analysis is running'
                : 'Edit PRs / requirements / instructions and re-run'
            }
            className="p-1 text-fg-subtle hover:text-primary disabled:opacity-40 disabled:hover:text-fg-subtle"
          >
            <Pencil size={14} />
          </button>
          <button onClick={onClose} className="p-1 text-fg-subtle hover:text-fg">
            <X size={16} />
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-5 space-y-5">
        {/* Linked PRs */}
        <div>
          <p className="text-xs font-semibold text-fg-muted uppercase tracking-wider mb-2">
            Linked PRs
          </p>
          <div className="space-y-1">
            {analysis.linkedPrs.map((pr, i) => (
              <a
                key={i}
                href={pr.prUrl}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-2 text-xs text-primary hover:underline"
              >
                <GitPullRequest size={12} />
                {pr.repoFullName} #{pr.prNumber} — {pr.prTitle}
                <ExternalLink size={11} />
              </a>
            ))}
          </div>
        </div>

        {/* Requirements */}
        <div>
          <p className="text-xs font-semibold text-fg-muted uppercase tracking-wider mb-2">
            Linked Requirements ({analysis.linkedRequirementIds.length})
          </p>
          <p className="text-xs text-fg-subtle">
            {analysis.linkedRequirementIds.length} requirement(s) linked
          </p>
        </div>

        {/* AI Analysis */}
        {analysis.status === 'RUNNING' && (
          <div className="flex items-center gap-3 py-8 justify-center text-fg-muted">
            <Loader2 size={20} className="animate-spin text-primary" />
            <span className="text-sm">AI is analysing PR changes and requirements…</span>
          </div>
        )}

        {analysis.status === 'FAILED' && (
          <div className="bg-danger-bg border border-danger-border rounded-xl p-4 text-sm text-danger">
            <p className="font-medium mb-1">Analysis failed</p>
            <p className="text-xs">{analysis.summary ?? 'An error occurred during analysis.'}</p>
          </div>
        )}

        {analysis.status === 'COMPLETED' && (
          <>
            {analysis.suggestions?.summary && (
              <div className="bg-info-bg border border-info-border rounded-xl p-4">
                <p className="text-xs font-semibold text-info mb-1">Summary</p>
                <p className="text-sm text-fg leading-relaxed">{analysis.suggestions.summary}</p>
              </div>
            )}

            {suggestions.length === 0 ? (
              <p className="text-sm text-fg-muted text-center py-6">No suggestions generated.</p>
            ) : (
              <div>
                <p className="text-xs font-semibold text-fg-muted uppercase tracking-wider mb-3">
                  Suggestions ({suggestions.length})
                </p>
                <div className="space-y-3">
                  {suggestions.map((s, i) => (
                    <SuggestionCard
                      key={i}
                      suggestion={s}
                      analysisId={analysis.id}
                      projectId={projectId}
                    />
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

const inputCls =
  'w-full border border-border-strong rounded-lg px-3 py-2 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary'

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function ImpactAnalysesPage() {
  const { projectId, base } = useProject()
  const { analysisId } = useParams<{ analysisId?: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<ImpactAnalysis | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(analysisId ?? null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['impact-analyses', projectId],
    queryFn: () => api.impactAnalyses(projectId!),
    enabled: !!projectId,
    refetchInterval: query => {
      const list = Array.isArray(query.state.data) ? (query.state.data as ImpactAnalysis[]) : []
      return list.some(a => a.status === 'RUNNING') ? 5000 : false
    },
  })

  const createMutation = useMutation({
    mutationFn: (form: CreateImpactAnalysisForm) => api.createImpactAnalysis(projectId!, form),
    onSuccess: created => {
      queryClient.invalidateQueries({ queryKey: ['impact-analyses', projectId] })
      setShowCreate(false)
      setSelectedId(created.id)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, form }: { id: string; form: CreateImpactAnalysisForm }) =>
      api.updateImpactAnalysis(projectId!, id, form),
    onSuccess: updated => {
      queryClient.invalidateQueries({ queryKey: ['impact-analyses', projectId] })
      setEditing(null)
      setSelectedId(updated.id)
    },
  })

  const analyses: ImpactAnalysis[] = Array.isArray(data) ? data : []
  const selected = analyses.find(a => a.id === selectedId) ?? null

  const completed = analyses.filter(a => a.status === 'COMPLETED').length
  const running = analyses.filter(a => a.status === 'RUNNING').length
  const failed = analyses.filter(a => a.status === 'FAILED').length

  if (isLoading) return <LoadingSpinner message="Loading impact analyses…" />
  if (error)
    return <ErrorMessage message="Failed to load impact analyses." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6">
      {/* Breadcrumb + header */}
      <div>
        <div className="flex items-center gap-2 text-sm text-fg-muted mb-1">
          <button onClick={() => navigate('/')} className="hover:text-primary">
            Overview
          </button>
          <ChevronRight size={14} />
          <button onClick={() => navigate(base)} className="hover:text-primary">
            {projectId}
          </button>
          <ChevronRight size={14} />
          <span className="text-fg">Impact Analyses</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <GitPullRequest size={20} className="text-fg-subtle" />
            <h1 className="text-2xl font-bold text-fg">Impact Analyses</h1>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => refetch()}
              className="p-2 text-fg-muted hover:text-fg hover:bg-surface-muted rounded-lg"
              title="Refresh"
            >
              <RefreshCw size={16} />
            </button>
            <Button onClick={() => setShowCreate(true)}>
              <Plus size={14} />
              New Analysis
            </Button>
          </div>
        </div>
        <p className="text-sm text-fg-muted mt-1">
          Link pull requests from Codebase repos with requirements to get AI-powered test coverage
          suggestions.
        </p>
      </div>

      {/* Stats */}
      {analyses.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Completed', value: completed, color: 'text-success' },
            { label: 'Running', value: running, color: 'text-info' },
            { label: 'Failed', value: failed, color: 'text-danger' },
          ].map(s => (
            <div
              key={s.label}
              className="bg-surface rounded-xl border border-border shadow-sm px-5 py-4"
            >
              <p className="text-xs text-fg-muted">{s.label}</p>
              <p className={cn('text-2xl font-bold', s.color)}>{s.value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Main content — list + detail */}
      <div className={cn('grid gap-6', selected ? 'grid-cols-2' : 'grid-cols-1')}>
        {/* List */}
        <div className="bg-surface rounded-xl border border-border shadow-sm">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="font-semibold text-fg">All Analyses</h2>
          </div>

          {analyses.length === 0 ? (
            <div className="px-5 py-16 text-center">
              <GitPullRequest size={32} className="mx-auto mb-3 text-fg-subtle" />
              <p className="text-sm font-medium text-fg-muted">No impact analyses yet</p>
              <p className="text-xs text-fg-subtle mt-1">
                Create one by linking PRs from Codebase repos with requirements.
              </p>
              <Button className="mt-4 mx-auto" onClick={() => setShowCreate(true)}>
                <Plus size={14} />
                New Analysis
              </Button>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {analyses.map(a => {
                const { color: sc, icon: si } = statusMeta(a.status)
                const isSelected = a.id === selectedId
                return (
                  <button
                    key={a.id}
                    onClick={() => setSelectedId(isSelected ? null : a.id)}
                    className={cn(
                      'w-full text-left px-5 py-4 transition-colors',
                      isSelected ? 'bg-primary-subtle' : 'hover:bg-surface-muted',
                    )}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <span
                            className={cn(
                              'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium',
                              sc,
                            )}
                          >
                            {si}
                            {a.status}
                          </span>
                          {a.suggestions && (
                            <span className="text-xs text-fg-subtle">
                              {a.suggestions.suggestions?.length ?? 0} suggestion
                              {(a.suggestions.suggestions?.length ?? 0) !== 1 ? 's' : ''}
                            </span>
                          )}
                        </div>
                        <p className="text-sm font-medium text-fg truncate">{a.name}</p>
                        <p className="text-xs text-fg-muted mt-0.5">
                          {a.linkedPrs.length} PR{a.linkedPrs.length !== 1 ? 's' : ''} ·{' '}
                          {a.linkedRequirementIds.length} req
                          {a.linkedRequirementIds.length !== 1 ? 's' : ''} ·{' '}
                          {relativeTime(a.createdAt)}
                        </p>
                      </div>
                      <ChevronRight
                        size={14}
                        className={cn(
                          'mt-1 shrink-0 transition-transform',
                          isSelected && 'rotate-90',
                        )}
                      />
                    </div>

                    {a.suggestions?.summary && (
                      <p className="text-xs text-fg-muted mt-2 line-clamp-2 leading-relaxed">
                        {a.suggestions.summary}
                      </p>
                    )}
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Detail panel */}
        {selected && (
          <DetailPanel
            analysis={selected}
            projectId={projectId!}
            onClose={() => setSelectedId(null)}
            onEdit={() => setEditing(selected)}
          />
        )}
      </div>

      {/* Create / Edit modal */}
      {(showCreate || editing) && (
        <CreateModal
          projectId={projectId!}
          existing={editing ?? undefined}
          onClose={() => {
            setShowCreate(false)
            setEditing(null)
          }}
          onSubmit={form => {
            if (editing) updateMutation.mutate({ id: editing.id, form })
            else createMutation.mutate(form)
          }}
        />
      )}

      {/* Create error */}
      {createMutation.isError && (
        <div className="fixed bottom-4 right-4 bg-danger text-white text-sm px-4 py-3 rounded-xl shadow-lg flex items-center gap-2">
          <AlertTriangle size={14} />
          Failed to create analysis. Check that Codebase repos are configured.
          <button onClick={() => createMutation.reset()} className="ml-2 hover:opacity-80">
            <X size={14} />
          </button>
        </div>
      )}
    </div>
  )
}
