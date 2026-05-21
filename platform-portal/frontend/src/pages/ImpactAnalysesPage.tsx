import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type {
  ImpactAnalysis, ImpactAnalysisSuggestion, CodabasePr,
  Requirement, LinkedPr, CreateImpactAnalysisForm,
} from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import {
  GitPullRequest, ChevronRight, Plus, X, ExternalLink,
  ChevronDown, ChevronUp,
  Loader2, Sparkles, RefreshCw, AlertTriangle, CheckCircle,
  XCircle, Clock, ArrowRight, FileText, Code2, PlusCircle,
} from 'lucide-react'

// ── Status helpers ─────────────────────────────────────────────────────────────

const STATUS_META: Record<string, { color: string; icon: React.ReactNode }> = {
  COMPLETED: { color: 'text-green-700 bg-green-100',  icon: <CheckCircle size={13} /> },
  FAILED:    { color: 'text-red-700 bg-red-100',      icon: <XCircle size={13} /> },
  RUNNING:   { color: 'text-blue-700 bg-blue-100',    icon: <Loader2 size={13} className="animate-spin" /> },
  DRAFT:     { color: 'text-slate-600 bg-slate-100',  icon: <Clock size={13} /> },
}
function statusMeta(s: string) { return STATUS_META[s] ?? STATUS_META.DRAFT }

function priorityColor(p: string) {
  switch (p) {
    case 'HIGH':   return 'text-red-700 bg-red-100'
    case 'MEDIUM': return 'text-orange-700 bg-orange-100'
    default:       return 'text-slate-600 bg-slate-100'
  }
}

function suggestionIcon(type: ImpactAnalysisSuggestion['type']) {
  switch (type) {
    case 'UPDATE_MANUAL_TEST':    return <FileText size={15} className="text-blue-500 shrink-0" />
    case 'CREATE_AUTOMATED_TEST': return <PlusCircle size={15} className="text-green-500 shrink-0" />
    case 'UPDATE_AUTOMATION':     return <Code2 size={15} className="text-purple-500 shrink-0" />
  }
}

function suggestionLabel(type: ImpactAnalysisSuggestion['type']) {
  switch (type) {
    case 'UPDATE_MANUAL_TEST':    return 'Update Manual Test'
    case 'CREATE_AUTOMATED_TEST': return 'Create Automated Test'
    case 'UPDATE_AUTOMATION':     return 'Update Automation'
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
  if (checkedCount === 0)        return 'unchecked'
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
      className="rounded border-slate-300 cursor-pointer"
    />
  )
}

function ReqTreeRow({
  node, selected, expanded,
  onToggleSelect, onToggleExpand, indent,
}: {
  node: ReqTree
  selected: Set<string>
  expanded: Set<string>
  onToggleSelect: (node: ReqTree) => void
  onToggleExpand: (id: string) => void
  indent: number
}) {
  const hasChildren = node.children.length > 0
  const isExpanded  = expanded.has(node.id)
  const state       = nodeCheckState(node, selected)

  return (
    <>
      <div
        className="flex items-center gap-2 px-3 py-1.5 hover:bg-slate-50 rounded"
        style={{ paddingLeft: `${12 + indent * 20}px` }}
      >
        <TreeCheckbox state={state} onChange={() => onToggleSelect(node)} />
        {hasChildren ? (
          <button onClick={() => onToggleExpand(node.id)} className="text-slate-400 hover:text-slate-700">
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
        ) : (
          <span className="w-[14px]" />
        )}
        <span className="text-sm text-slate-700 truncate flex-1">{node.title}</span>
        <span className="text-xs text-slate-400 shrink-0">{node.issueType}</span>
      </div>
      {hasChildren && isExpanded && node.children.map(child => (
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
  onClose,
  onCreate,
}: {
  projectId: string
  onClose: () => void
  onCreate: (form: CreateImpactAnalysisForm) => void
}) {
  const [step, setStep]                     = useState<'prs' | 'reqs'>('prs')
  const [name, setName]                     = useState('Impact Analysis')
  const [selectedPrs, setSelectedPrs]       = useState<Set<string>>(new Set())
  const [selectedReqs, setSelectedReqs]     = useState<Set<string>>(new Set())
  const [expandedReqs, setExpandedReqs]     = useState<Set<string>>(new Set())
  const [reqSearch, setReqSearch]           = useState('')

  const { data: prs, isLoading: prsLoading } = useQuery({
    queryKey: ['codebase-prs', projectId],
    queryFn:  () => api.codebasePrs(projectId),
    enabled:  !!projectId,
  })

  const { data: reqs, isLoading: reqsLoading } = useQuery({
    queryKey: ['requirements', projectId],
    queryFn:  () => api.requirements(projectId),
    enabled:  !!projectId,
  })

  // Auto-expand top-level nodes on first load
  const autoExpanded = useRef(false)
  useEffect(() => {
    if (reqs && !autoExpanded.current) {
      autoExpanded.current = true
      const tree = buildTree(reqs)
      setExpandedReqs(new Set(tree.filter(n => n.children.length > 0).map(n => n.id)))
    }
  }, [reqs])

  const prList   = Array.isArray(prs) ? prs : []
  const reqList  = Array.isArray(reqs) ? reqs : []
  const reqTree  = buildTree(reqList)

  const filteredReqs = reqSearch
    ? reqList.filter(r =>
        r.title.toLowerCase().includes(reqSearch.toLowerCase()) ||
        (r.externalId && r.externalId.toLowerCase().includes(reqSearch.toLowerCase()))
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
    const ids    = collectIds(node)
    const allIn  = ids.every(id => selectedReqs.has(id))
    setSelectedReqs(prev => {
      const next = new Set(prev)
      if (allIn) { ids.forEach(id => next.delete(id)) }
      else       { ids.forEach(id => next.add(id)) }
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

  function handleCreate() {
    const linked: LinkedPr[] = prList
      .filter(pr => selectedPrs.has(buildPrKey(pr)))
      .map(pr => ({
        repoFullName: pr.repoFullName,
        prNumber:     pr.number,
        prUrl:        pr.html_url,
        prTitle:      pr.title,
      }))
    onCreate({ name, linkedPrs: linked, linkedRequirementIds: Array.from(selectedReqs) })
  }

  const canProceed = step === 'prs'
    ? selectedPrs.size > 0
    : true

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between">
          <div>
            <h2 className="font-semibold text-slate-900">New Impact Analysis</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Step {step === 'prs' ? '1' : '2'} of 2 — {step === 'prs' ? 'Select PRs' : 'Link Requirements'}
            </p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700">
            <X size={18} />
          </button>
        </div>

        {/* Name field (always visible) */}
        <div className="px-6 pt-4">
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Analysis name"
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* Step content */}
        <div className="flex-1 overflow-y-auto px-6 py-4">
          {step === 'prs' ? (
            prsLoading ? (
              <LoadingSpinner message="Loading PRs from Codebase repos…" />
            ) : prList.length === 0 ? (
              <div className="text-center py-12 text-sm text-slate-500">
                <AlertTriangle size={32} className="mx-auto mb-3 text-amber-400" />
                <p className="font-medium">No Codebase repos configured</p>
                <p className="mt-1">Go to Project Settings and mark a GitHub repo as <span className="font-mono bg-slate-100 px-1 rounded">Codebase</span> to list PRs here.</p>
              </div>
            ) : (
              <div className="space-y-2">
                <p className="text-xs text-slate-500 mb-3">Select one or more PRs to analyse:</p>
                {prList.map(pr => {
                  const key     = buildPrKey(pr)
                  const checked = selectedPrs.has(key)
                  return (
                    <label
                      key={key}
                      className={cn(
                        'flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-colors',
                        checked ? 'border-blue-400 bg-blue-50' : 'border-slate-200 hover:border-slate-300',
                      )}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => togglePr(key)}
                        className="rounded border-slate-300 mt-0.5"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-slate-800 truncate">{pr.title}</span>
                          <a
                            href={pr.html_url}
                            target="_blank"
                            rel="noreferrer"
                            onClick={e => e.stopPropagation()}
                            className="text-blue-500 hover:text-blue-700 shrink-0"
                          >
                            <ExternalLink size={12} />
                          </a>
                        </div>
                        <p className="text-xs text-slate-500 mt-0.5">
                          {pr.repoFullName} · PR #{pr.number} · {pr.user} · {pr.head_ref} → {pr.base_ref}
                        </p>
                        {pr.body && (
                          <p className="text-xs text-slate-400 mt-1 line-clamp-2">{pr.body}</p>
                        )}
                      </div>
                    </label>
                  )
                })}
              </div>
            )
          ) : (
            reqsLoading ? (
              <LoadingSpinner message="Loading requirements…" />
            ) : reqList.length === 0 ? (
              <p className="text-sm text-slate-500 text-center py-12">No requirements found for this project.</p>
            ) : (
              <>
                <input
                  value={reqSearch}
                  onChange={e => setReqSearch(e.target.value)}
                  placeholder="Search requirements…"
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <div className="border border-slate-200 rounded-xl overflow-hidden">
                  {filteredReqs ? (
                    filteredReqs.length === 0 ? (
                      <p className="px-4 py-6 text-sm text-slate-500 text-center">No matches.</p>
                    ) : (
                      filteredReqs.map(r => (
                        <label key={r.id} className="flex items-center gap-2 px-3 py-2 hover:bg-slate-50 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedReqs.has(r.id)}
                            onChange={() => setSelectedReqs(prev => {
                              const next = new Set(prev)
                              next.has(r.id) ? next.delete(r.id) : next.add(r.id)
                              return next
                            })}
                            className="rounded border-slate-300"
                          />
                          {r.externalId && (
                            <span className="text-xs font-mono text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded shrink-0">
                              {r.externalId}
                            </span>
                          )}
                          <span className="text-sm text-slate-700 truncate flex-1">{r.title}</span>
                          <span className="text-xs text-slate-400 shrink-0">{r.issueType}</span>
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
            )
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-slate-100 flex items-center justify-between">
          <div className="text-sm text-slate-500">
            {step === 'prs'
              ? `${selectedPrs.size} PR${selectedPrs.size !== 1 ? 's' : ''} selected`
              : selectedReqs.size > 0
                ? `${selectedReqs.size} requirement${selectedReqs.size !== 1 ? 's' : ''} selected`
                : 'No requirements selected (optional)'
            }
          </div>
          <div className="flex gap-3">
            {step === 'reqs' && (
              <button
                onClick={() => setStep('prs')}
                className="px-4 py-2 text-sm text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50"
              >
                Back
              </button>
            )}
            {step === 'prs' ? (
              <button
                disabled={!canProceed}
                onClick={() => setStep('reqs')}
                className="flex items-center gap-2 px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next: Requirements
                <ArrowRight size={14} />
              </button>
            ) : (
              <button
                disabled={!canProceed}
                onClick={handleCreate}
                className="flex items-center gap-2 px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Sparkles size={14} />
                Create & Analyse
              </button>
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
    mutationFn: (tcId: string) => api.applyImpactSuggestion(projectId, tcId, {
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

  const filteredTcs = testCases.filter(tc =>
    tc.title.toLowerCase().includes(tcSearch.toLowerCase()) ||
    (tc.externalId ?? '').toLowerCase().includes(tcSearch.toLowerCase())
  )

  return (
    <div className="border border-slate-200 rounded-xl p-4 space-y-2">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          {suggestionIcon(suggestion.type)}
          <span className="text-sm font-medium text-slate-800 truncate">{suggestion.title}</span>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className={cn('text-xs px-2 py-0.5 rounded-full font-medium', priorityColor(suggestion.priority))}>
            {suggestion.priority}
          </span>
          <Badge
            label={suggestionLabel(suggestion.type)}
            colorClass={
              suggestion.type === 'UPDATE_MANUAL_TEST'    ? 'text-blue-700 bg-blue-100'   :
              suggestion.type === 'CREATE_AUTOMATED_TEST' ? 'text-green-700 bg-green-100' :
              'text-purple-700 bg-purple-100'
            }
          />
        </div>
      </div>

      <p className="text-xs text-slate-600 leading-relaxed">{suggestion.reason}</p>

      {suggestion.details && (
        <>
          <button
            onClick={() => setExpanded(e => !e)}
            className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800"
          >
            {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
            {expanded ? 'Hide details' : 'Show details'}
          </button>
          {expanded && (
            <pre className="text-xs bg-slate-50 border border-slate-200 rounded-lg p-3 whitespace-pre-wrap font-mono leading-relaxed">
              {suggestion.details}
            </pre>
          )}
        </>
      )}

      {/* Apply action for UPDATE_MANUAL_TEST */}
      {suggestion.type === 'UPDATE_MANUAL_TEST' && (
        <div className="pt-1">
          {applied ? (
            <span className="inline-flex items-center gap-1 text-xs text-green-600 font-medium">
              <CheckCircle size={12} /> Applied — test case is now under review
            </span>
          ) : suggestion.testCaseId ? (
            <button
              onClick={() => applyMutation.mutate(suggestion.testCaseId!)}
              disabled={applyMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-blue-700 border border-blue-200 rounded-lg hover:bg-blue-50 disabled:opacity-50"
            >
              {applyMutation.isPending ? <Loader2 size={11} className="animate-spin" /> : <ArrowRight size={11} />}
              Apply to linked test case
            </button>
          ) : applying ? (
            <div className="border border-slate-200 rounded-lg overflow-hidden">
              <div className="flex items-center gap-1 px-2 py-1.5 border-b border-slate-100 bg-slate-50">
                <span className="text-xs text-slate-600 font-medium flex-1">Select test case to update</span>
                <button onClick={() => setApplying(false)} className="text-slate-400 hover:text-slate-600">
                  <X size={12} />
                </button>
              </div>
              <div className="px-2 py-1.5 border-b border-slate-100">
                <input
                  autoFocus
                  value={tcSearch}
                  onChange={e => setTcSearch(e.target.value)}
                  placeholder="Search test cases…"
                  className="w-full text-xs outline-none"
                />
              </div>
              <div className="max-h-36 overflow-y-auto">
                {tcLoading ? (
                  <div className="flex justify-center py-3"><Loader2 size={14} className="animate-spin text-slate-400" /></div>
                ) : filteredTcs.length === 0 ? (
                  <p className="text-xs text-slate-400 px-3 py-2">No test cases found</p>
                ) : filteredTcs.slice(0, 10).map(tc => (
                  <button
                    key={tc.id}
                    onClick={() => applyMutation.mutate(tc.id)}
                    disabled={applyMutation.isPending}
                    className="w-full text-left px-3 py-1.5 text-xs hover:bg-blue-50 flex items-center gap-2 disabled:opacity-50"
                  >
                    <span className={cn('shrink-0 px-1.5 py-0.5 rounded text-[10px] font-medium',
                      tc.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
                      tc.status === 'DRAFT' ? 'bg-slate-100 text-slate-600' :
                      'bg-yellow-100 text-yellow-700'
                    )}>{tc.status}</span>
                    <span className="text-slate-700 truncate">{tc.title}</span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <button
              onClick={() => setApplying(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-blue-700 border border-blue-200 rounded-lg hover:bg-blue-50"
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
}: {
  analysis: ImpactAnalysis
  projectId: string
  onClose: () => void
}) {
  const { color: sc, icon: si } = statusMeta(analysis.status)
  const suggestions = analysis.suggestions?.suggestions ?? []

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm flex flex-col h-full">
      {/* Header */}
      <div className="px-5 py-4 border-b border-slate-100 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className={cn('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium', sc)}>
              {si}{analysis.status}
            </span>
          </div>
          <h3 className="font-semibold text-slate-900 text-sm">{analysis.name}</h3>
          <p className="text-xs text-slate-500 mt-0.5">{relativeTime(analysis.createdAt)}</p>
        </div>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-700 shrink-0">
          <X size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-5 space-y-5">
        {/* Linked PRs */}
        <div>
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Linked PRs</p>
          <div className="space-y-1">
            {analysis.linkedPrs.map((pr, i) => (
              <a
                key={i}
                href={pr.prUrl}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-2 text-xs text-blue-700 hover:underline"
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
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
            Linked Requirements ({analysis.linkedRequirementIds.length})
          </p>
          <p className="text-xs text-slate-400">{analysis.linkedRequirementIds.length} requirement(s) linked</p>
        </div>

        {/* AI Analysis */}
        {analysis.status === 'RUNNING' && (
          <div className="flex items-center gap-3 py-8 justify-center text-slate-500">
            <Loader2 size={20} className="animate-spin text-blue-500" />
            <span className="text-sm">AI is analysing PR changes and requirements…</span>
          </div>
        )}

        {analysis.status === 'FAILED' && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-sm text-red-700">
            <p className="font-medium mb-1">Analysis failed</p>
            <p className="text-xs">{analysis.summary ?? 'An error occurred during analysis.'}</p>
          </div>
        )}

        {analysis.status === 'COMPLETED' && (
          <>
            {analysis.suggestions?.summary && (
              <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
                <p className="text-xs font-semibold text-blue-700 mb-1">Summary</p>
                <p className="text-sm text-blue-900 leading-relaxed">{analysis.suggestions.summary}</p>
              </div>
            )}

            {suggestions.length === 0 ? (
              <p className="text-sm text-slate-500 text-center py-6">No suggestions generated.</p>
            ) : (
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">
                  Suggestions ({suggestions.length})
                </p>
                <div className="space-y-3">
                  {suggestions.map((s, i) => (
                    <SuggestionCard key={i} suggestion={s} analysisId={analysis.id} projectId={projectId} />
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

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function ImpactAnalysesPage() {
  const { projectId, analysisId } = useParams<{ projectId: string; analysisId?: string }>()
  const navigate      = useNavigate()
  const queryClient   = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(analysisId ?? null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['impact-analyses', projectId],
    queryFn:  () => api.impactAnalyses(projectId!),
    enabled:  !!projectId,
    refetchInterval: (query) => {
      const list = Array.isArray(query.state.data) ? query.state.data as ImpactAnalysis[] : []
      return list.some(a => a.status === 'RUNNING') ? 5000 : false
    },
  })

  const createMutation = useMutation({
    mutationFn: (form: CreateImpactAnalysisForm) => api.createImpactAnalysis(projectId!, form),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['impact-analyses', projectId] })
      setShowCreate(false)
      setSelectedId(created.id)
    },
  })

  const analyses: ImpactAnalysis[] = Array.isArray(data) ? data : []
  const selected = analyses.find(a => a.id === selectedId) ?? null

  const completed = analyses.filter(a => a.status === 'COMPLETED').length
  const running   = analyses.filter(a => a.status === 'RUNNING').length
  const failed    = analyses.filter(a => a.status === 'FAILED').length

  if (isLoading) return <LoadingSpinner message="Loading impact analyses…" />
  if (error)     return <ErrorMessage  message="Failed to load impact analyses." />

  return (
    <div className="space-y-6">
      {/* Breadcrumb + header */}
      <div>
        <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
          <button onClick={() => navigate('/')} className="hover:text-blue-600">Overview</button>
          <ChevronRight size={14} />
          <button onClick={() => navigate(`/projects/${projectId}`)} className="hover:text-blue-600">
            {projectId}
          </button>
          <ChevronRight size={14} />
          <span className="text-slate-700">Impact Analyses</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <GitPullRequest size={20} className="text-slate-400" />
            <h1 className="text-2xl font-bold text-slate-900">Impact Analyses</h1>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => refetch()}
              className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg"
              title="Refresh"
            >
              <RefreshCw size={16} />
            </button>
            <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-2 px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              <Plus size={14} />
              New Analysis
            </button>
          </div>
        </div>
        <p className="text-sm text-slate-500 mt-1">
          Link pull requests from Codebase repos with requirements to get AI-powered test coverage suggestions.
        </p>
      </div>

      {/* Stats */}
      {analyses.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Completed', value: completed, color: 'text-green-600' },
            { label: 'Running',   value: running,   color: 'text-blue-600' },
            { label: 'Failed',    value: failed,    color: 'text-red-600' },
          ].map(s => (
            <div key={s.label} className="bg-white rounded-xl border border-slate-200 shadow-sm px-5 py-4">
              <p className="text-xs text-slate-500">{s.label}</p>
              <p className={cn('text-2xl font-bold', s.color)}>{s.value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Main content — list + detail */}
      <div className={cn('grid gap-6', selected ? 'grid-cols-2' : 'grid-cols-1')}>
        {/* List */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="font-semibold text-slate-900">All Analyses</h2>
          </div>

          {analyses.length === 0 ? (
            <div className="px-5 py-16 text-center">
              <GitPullRequest size={32} className="mx-auto mb-3 text-slate-300" />
              <p className="text-sm font-medium text-slate-500">No impact analyses yet</p>
              <p className="text-xs text-slate-400 mt-1">Create one by linking PRs from Codebase repos with requirements.</p>
              <button
                onClick={() => setShowCreate(true)}
                className="mt-4 flex items-center gap-2 px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 mx-auto"
              >
                <Plus size={14} />
                New Analysis
              </button>
            </div>
          ) : (
            <div className="divide-y divide-slate-50">
              {analyses.map(a => {
                const { color: sc, icon: si } = statusMeta(a.status)
                const isSelected = a.id === selectedId
                return (
                  <button
                    key={a.id}
                    onClick={() => setSelectedId(isSelected ? null : a.id)}
                    className={cn(
                      'w-full text-left px-5 py-4 transition-colors',
                      isSelected ? 'bg-blue-50' : 'hover:bg-slate-50',
                    )}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <span className={cn('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium', sc)}>
                            {si}{a.status}
                          </span>
                          {a.suggestions && (
                            <span className="text-xs text-slate-400">
                              {a.suggestions.suggestions?.length ?? 0} suggestion{(a.suggestions.suggestions?.length ?? 0) !== 1 ? 's' : ''}
                            </span>
                          )}
                        </div>
                        <p className="text-sm font-medium text-slate-800 truncate">{a.name}</p>
                        <p className="text-xs text-slate-500 mt-0.5">
                          {a.linkedPrs.length} PR{a.linkedPrs.length !== 1 ? 's' : ''} · {a.linkedRequirementIds.length} req{a.linkedRequirementIds.length !== 1 ? 's' : ''} · {relativeTime(a.createdAt)}
                        </p>
                      </div>
                      <ChevronRight size={14} className={cn('mt-1 shrink-0 transition-transform', isSelected && 'rotate-90')} />
                    </div>

                    {a.suggestions?.summary && (
                      <p className="text-xs text-slate-500 mt-2 line-clamp-2 leading-relaxed">
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
          />
        )}
      </div>

      {/* Create modal */}
      {showCreate && (
        <CreateModal
          projectId={projectId!}
          onClose={() => setShowCreate(false)}
          onCreate={form => createMutation.mutate(form)}
        />
      )}

      {/* Create error */}
      {createMutation.isError && (
        <div className="fixed bottom-4 right-4 bg-red-600 text-white text-sm px-4 py-3 rounded-xl shadow-lg flex items-center gap-2">
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
