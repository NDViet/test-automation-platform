import { useState, useRef, useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import type { ManagedTestCase, TestSuite, CreateTestCaseForm, Requirement, IntegrationConfig, CaseProperty } from '@/lib/types'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import Markdown from '@/components/Markdown'
import MarkdownEditor from '@/components/MarkdownEditor'
import {
  Plus, Sparkles, Bot, ExternalLink, ChevronRight, ChevronDown, X,
  FolderOpen, Layers, Loader2, Search, GitBranch, AlertTriangle, Link2, Pencil, Star,
} from 'lucide-react'

// ── Color helpers ──────────────────────────────────────────────────────────────

function statusColor(status: string): string {
  switch (status) {
    case 'DRAFT':        return 'text-slate-600 bg-slate-100'
    case 'UNDER_REVIEW': return 'text-yellow-700 bg-yellow-100'
    case 'APPROVED':     return 'text-green-700 bg-green-100'
    case 'DEPRECATED':   return 'text-red-700 bg-red-100'
    default:             return 'text-slate-600 bg-slate-100'
  }
}

function priorityColor(priority: string): string {
  switch (priority) {
    case 'CRITICAL': return 'text-red-700 bg-red-100'
    case 'HIGH':     return 'text-orange-700 bg-orange-100'
    case 'MEDIUM':   return 'text-yellow-700 bg-yellow-100'
    case 'LOW':      return 'text-slate-600 bg-slate-100'
    default:         return 'text-slate-600 bg-slate-100'
  }
}

function automationColor(automationStatus: string): string {
  switch (automationStatus) {
    case 'GENERATING':  return 'text-blue-700 bg-blue-100'
    case 'PR_CREATED':  return 'text-purple-700 bg-purple-100'
    case 'PR_MERGED':   return 'text-green-700 bg-green-100'
    case 'FAILED':      return 'text-red-700 bg-red-100'
    default:            return 'text-slate-500 bg-slate-100'
  }
}

function coverageColor(status: string): string {
  switch (status) {
    case 'COVERED':     return 'text-green-700 bg-green-100'
    case 'PARTIAL':     return 'text-yellow-700 bg-yellow-100'
    case 'NOT_COVERED': return 'text-red-700 bg-red-100'
    default:            return 'text-slate-500 bg-slate-100'
  }
}

// ── Step editor row ────────────────────────────────────────────────────────────

interface StepRow {
  action: string
  expectedResult: string
  notes: string
}

function StepEditor({ steps, onChange }: {
  steps: StepRow[]
  onChange: (steps: StepRow[]) => void
}) {
  function updateStep(i: number, field: keyof StepRow, value: string) {
    const next = steps.map((s, idx) => idx === i ? { ...s, [field]: value } : s)
    onChange(next)
  }
  function addStep() {
    onChange([...steps, { action: '', expectedResult: '', notes: '' }])
  }
  function removeStep(i: number) {
    onChange(steps.filter((_, idx) => idx !== i))
  }

  return (
    <div className="space-y-2">
      {steps.map((step, i) => (
        <div key={i} className="flex gap-2 items-start rounded-lg border border-slate-100 bg-slate-50/50 p-2">
          <span className="text-xs text-slate-400 font-mono mt-2 w-5 shrink-0 text-right">{i + 1}.</span>
          <div className="flex-1 space-y-1">
            <input
              type="text"
              placeholder="Action — what the tester does"
              value={step.action}
              onChange={e => updateStep(i, 'action', e.target.value)}
              className="w-full border border-slate-200 rounded px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <input
              type="text"
              placeholder="Expected result"
              value={step.expectedResult}
              onChange={e => updateStep(i, 'expectedResult', e.target.value)}
              className="w-full border border-slate-200 rounded px-2 py-1 text-xs text-slate-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <input
              type="text"
              placeholder="Notes (optional)"
              value={step.notes}
              onChange={e => updateStep(i, 'notes', e.target.value)}
              className="w-full border border-slate-200 rounded px-2 py-1 text-xs text-slate-400 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <button
            type="button"
            onClick={() => removeStep(i)}
            className="mt-1.5 text-slate-400 hover:text-red-500 transition-colors"
            title="Remove step"
          >
            <X size={14} />
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={addStep}
        className="text-xs text-blue-600 hover:text-blue-700 font-medium flex items-center gap-1 mt-1"
      >
        <Plus size={12} /> Add step
      </button>
    </div>
  )
}

// ── New Test Case Modal ────────────────────────────────────────────────────────

function FormSection({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="mb-2">
        <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{title}</h3>
        {hint && <p className="text-[11px] text-slate-400 mt-0.5">{hint}</p>}
      </div>
      <div className="space-y-3">{children}</div>
    </div>
  )
}

// ── Requirement multi-select (optional, many; one can be ★ primary/source) ─────

function RequirementMultiSelect({
  requirements, value, onChange, primaryId, onPrimaryChange,
}: {
  requirements: Requirement[]
  value: string[]
  onChange: (ids: string[]) => void
  /** The id marked as the primary/source requirement (origin), or '' for none. */
  primaryId: string
  onPrimaryChange: (id: string) => void
}) {
  const [search, setSearch] = useState('')
  const byId = new Map(requirements.map(r => [r.id, r]))
  const selected = new Set(value)
  const filtered = requirements.filter(r =>
    !selected.has(r.id) && (
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      (r.externalId ?? '').toLowerCase().includes(search.toLowerCase())
    ),
  )
  function add(id: string) { if (!selected.has(id)) onChange([...value, id]) }
  function remove(id: string) {
    onChange(value.filter(v => v !== id))
    if (primaryId === id) onPrimaryChange('')   // removing the primary clears it
  }
  // Primary first, then the rest.
  const ordered = [...value].sort((a, b) => (a === primaryId ? -1 : b === primaryId ? 1 : 0))
  return (
    <div>
      <div className="flex flex-wrap gap-1 mb-1.5">
        {value.length === 0 && <span className="text-xs text-slate-400 italic">None linked (optional)</span>}
        {ordered.map(id => {
          const r = byId.get(id)
          const isPrimary = id === primaryId
          return (
            <span key={id} className={cn('inline-flex items-center gap-1 text-xs rounded px-1.5 py-0.5',
              isPrimary ? 'bg-blue-100 text-blue-800' : 'bg-blue-50 text-blue-700')}>
              <button type="button"
                onClick={() => onPrimaryChange(isPrimary ? '' : id)}
                title={isPrimary ? 'Primary (source) requirement — click to unset' : 'Mark as primary (source) requirement'}
                className={isPrimary ? 'text-amber-500' : 'text-slate-300 hover:text-amber-500'}>
                <Star size={11} fill={isPrimary ? 'currentColor' : 'none'} />
              </button>
              {r?.externalId && <span className="font-mono opacity-60">{r.externalId}</span>}
              <span className="truncate max-w-[170px]">{r?.title ?? id.slice(0, 8) + '…'}</span>
              <button type="button" onClick={() => remove(id)} className="hover:text-red-500"><X size={10} /></button>
            </span>
          )
        })}
      </div>
      <div className="border border-slate-200 rounded-lg">
        <div className="flex items-center gap-1 px-2 py-1.5 border-b border-slate-100">
          <Search size={12} className="text-slate-400" />
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Search requirements to link…" className="flex-1 text-xs outline-none" />
        </div>
        <div className="max-h-40 overflow-y-auto">
          {filtered.length === 0 ? (
            <p className="text-xs text-slate-400 px-3 py-2">No matching requirements</p>
          ) : filtered.slice(0, 30).map(r => (
            <button type="button" key={r.id} onClick={() => add(r.id)}
              className="w-full text-left px-3 py-1.5 text-xs flex items-center gap-2 hover:bg-blue-50">
              <Plus size={11} className="text-slate-300 shrink-0" />
              {r.externalId && <span className="text-slate-400 font-mono shrink-0">{r.externalId}</span>}
              <span className="text-slate-700 truncate">{r.title}</span>
            </button>
          ))}
        </div>
      </div>
      <p className="text-[11px] text-slate-400 mt-1">Click the ★ to mark one as the primary/source requirement (optional).</p>
    </div>
  )
}

// ── Acceptance-criteria picker (chosen from the linked requirements' ACs) ──────

function acToString(ac: unknown): string {
  if (typeof ac === 'string') return ac
  if (ac && typeof ac === 'object') {
    const o = ac as Record<string, unknown>
    const v = o.text ?? o.criteria ?? o.label ?? o.description ?? o.value
    if (typeof v === 'string') return v
  }
  return String(ac)
}

function AcceptanceCriteriaPicker({
  requirements, linkedReqIds, value, onChange,
}: {
  requirements: Requirement[]
  linkedReqIds: string[]
  value: string[]
  onChange: (refs: string[]) => void
}) {
  const linkedSet = new Set(linkedReqIds)
  const groups = requirements
    .filter(r => linkedSet.has(r.id))
    .map(r => ({ req: r, acs: (r.acceptanceCriteria ?? []).map(acToString).filter(s => s.trim()) }))
    .filter(g => g.acs.length > 0)
  const hasAcs = groups.length > 0
  const selected = new Set(value)
  function toggle(s: string) {
    const next = new Set(value)
    if (next.has(s)) next.delete(s); else next.add(s)
    onChange([...next])
  }

  // Always allow free text too (and to preserve legacy/AI-set refs not in any list).
  const known = new Set(groups.flatMap(g => g.acs))
  const custom = value.filter(v => !known.has(v))

  if (!hasAcs) {
    return (
      <div>
        <input type="text" value={value.join(', ')}
          onChange={e => onChange(e.target.value.split(',').map(s => s.trim()).filter(Boolean))}
          className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="comma-separated, e.g. AC-1, AC-2" />
        <p className="text-[11px] text-slate-400 mt-1">
          {linkedReqIds.length === 0
            ? 'Link a requirement above to pick its acceptance criteria; otherwise enter refs manually.'
            : 'The linked requirement(s) have no structured acceptance criteria — enter refs manually.'}
        </p>
      </div>
    )
  }
  return (
    <div className="space-y-2">
      {groups.map(g => (
        <div key={g.req.id} className="border border-slate-200 rounded-lg overflow-hidden">
          <p className="px-2.5 py-1.5 bg-slate-50 text-[11px] font-medium text-slate-500 border-b border-slate-100">
            {g.req.externalId && <span className="font-mono mr-1">{g.req.externalId}</span>}{g.req.title}
          </p>
          <div className="divide-y divide-slate-50">
            {g.acs.map((ac, i) => (
              <label key={i} className="flex items-start gap-2 px-2.5 py-1.5 text-xs cursor-pointer hover:bg-blue-50">
                <input type="checkbox" checked={selected.has(ac)} onChange={() => toggle(ac)} className="mt-0.5 shrink-0" />
                <span className="text-slate-700">{ac}</span>
              </label>
            ))}
          </div>
        </div>
      ))}
      {custom.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {custom.map(c => (
            <span key={c} className="inline-flex items-center gap-1 text-xs bg-slate-100 text-slate-600 rounded px-1.5 py-0.5">
              {c}
              <button type="button" onClick={() => toggle(c)} className="hover:text-red-500"><X size={10} /></button>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

function TestCaseFormModal({
  projectId,
  suites,
  editing,
  onClose,
}: {
  projectId: string
  suites: TestSuite[]
  editing?: ManagedTestCase | null
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const isEdit = !!editing
  const [title, setTitle] = useState(editing?.title ?? '')
  const [description, setDescription] = useState(editing?.description ?? '')
  const [preconditions, setPreconditions] = useState(editing?.preconditions ?? '')
  const [expectedResult, setExpectedResult] = useState(editing?.expectedResult ?? '')
  const [priority, setPriority] = useState(editing?.priority ?? 'MEDIUM')
  const [suiteId, setSuiteId] = useState(editing?.suiteId ?? '')
  // Full linked-requirement set (the primary/source, if any, is one of these).
  const [linkedReqIds, setLinkedReqIds] = useState<string[]>(() => {
    const set = new Set(editing?.linkedRequirementIds ?? [])
    if (editing?.sourceRequirementId) set.add(editing.sourceRequirementId)
    return [...set]
  })
  const [primaryReqId, setPrimaryReqId] = useState(editing?.sourceRequirementId ?? '')
  const [acRefs, setAcRefs] = useState<string[]>(editing?.acRefs ?? [])
  const [steps, setSteps] = useState<StepRow[]>(
    editing?.steps?.length
      ? editing.steps.map(s => ({ action: s.action ?? '', expectedResult: s.expectedResult ?? '', notes: s.notes ?? '' }))
      : [{ action: '', expectedResult: '', notes: '' }],
  )
  const [error, setError] = useState<string | null>(null)

  // For the optional "source requirement" link.
  const { data: requirements = [] } = useQuery({
    queryKey: ['requirements', projectId],
    queryFn: () => api.requirements(projectId),
  })

  const mutation = useMutation({
    mutationFn: (body: CreateTestCaseForm) =>
      isEdit ? api.updateTestCase(projectId, editing!.id, body) : api.createTestCase(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['testCases', projectId] })
      onClose()
    },
    onError: (err: Error) => setError(err.message),
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim()) { setError('Title is required'); return }
    // The whole link set is sent (so edits can replace/clear it); the ★ primary, if
    // any, becomes sourceRequirementId and is guaranteed to be in the set.
    const linkedRequirementIds = Array.from(new Set([
      ...(primaryReqId ? [primaryReqId] : []),
      ...linkedReqIds,
    ]))
    mutation.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      preconditions: preconditions.trim() || undefined,
      expectedResult: expectedResult.trim() || undefined,
      priority,
      suiteId: suiteId || undefined,
      sourceRequirementId: primaryReqId || undefined,
      linkedRequirementIds,
      acRefs: acRefs.length ? acRefs : undefined,
      steps: steps.filter(s => s.action.trim()).map(s => ({
        action: s.action.trim(),
        expectedResult: s.expectedResult.trim() || undefined,
        notes: s.notes.trim() || undefined,
      })),
    })
  }

  const inputCls = 'w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 max-h-[92vh] flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">{isEdit ? `Edit ${editing!.externalId ?? 'Test Case'}` : 'New Test Case'}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-5 py-4 space-y-6">
          {error && <ErrorMessage message={error} />}

          {/* Details */}
          <FormSection title="Details">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Title *</label>
              <input type="text" value={title} onChange={e => setTitle(e.target.value)}
                className={inputCls} placeholder="e.g. Checkout applies the correct order total" autoFocus />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">Priority</label>
                <select value={priority} onChange={e => setPriority(e.target.value)} className={inputCls}>
                  {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(p => <option key={p} value={p}>{p}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">Suite / Plan</label>
                <select value={suiteId} onChange={e => setSuiteId(e.target.value)} className={inputCls}>
                  <option value="">— None —</option>
                  {suites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
            </div>
            {!isEdit && (
              <p className="text-[11px] text-slate-400">New cases start as <strong>DRAFT</strong> — submit for review to confirm.</p>
            )}
          </FormSection>

          {/* Specification */}
          <FormSection title="Specification" hint="Markdown supported — use the Preview tab to check formatting.">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Preconditions</label>
              <MarkdownEditor value={preconditions} onChange={setPreconditions} rows={2}
                placeholder="State the system must be in before running" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Description</label>
              <MarkdownEditor value={description} onChange={setDescription} rows={3}
                placeholder="What this test verifies and why" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Expected result (overall)</label>
              <MarkdownEditor value={expectedResult} onChange={setExpectedResult} rows={2}
                placeholder="The overall pass condition" />
            </div>
          </FormSection>

          {/* Steps */}
          <FormSection title="Steps" hint="Each step: action → expected result, with optional notes.">
            <StepEditor steps={steps} onChange={setSteps} />
          </FormSection>

          {/* Traceability */}
          <FormSection title="Traceability">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Linked requirements <span className="text-slate-400 font-normal">(optional · a test case may cover any number, or none)</span>
              </label>
              <RequirementMultiSelect
                requirements={requirements}
                value={linkedReqIds}
                onChange={setLinkedReqIds}
                primaryId={primaryReqId}
                onPrimaryChange={setPrimaryReqId}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Acceptance criteria <span className="text-slate-400 font-normal">(which ACs of the linked requirements this case validates)</span>
              </label>
              <AcceptanceCriteriaPicker
                requirements={requirements}
                linkedReqIds={linkedReqIds}
                value={acRefs}
                onChange={setAcRefs}
              />
            </div>
          </FormSection>
        </form>

        <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
          <button type="button" onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors">
            Cancel
          </button>
          <button type="button" onClick={handleSubmit as unknown as React.MouseEventHandler}
            disabled={mutation.isPending}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors flex items-center gap-2">
            {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
            {isEdit ? 'Save changes' : 'Create'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Generate from AI Modal ─────────────────────────────────────────────────────

const ISSUE_TYPE_COLOR: Record<string, string> = {
  EPIC:    'text-purple-700 bg-purple-100',
  STORY:   'text-blue-700 bg-blue-100',
  BUG:     'text-red-700 bg-red-100',
  TASK:    'text-slate-600 bg-slate-100',
  SUBTASK: 'text-teal-700 bg-teal-100',
}
function reqTypeColor(t: string) { return ISSUE_TYPE_COLOR[t?.toUpperCase()] ?? 'text-slate-600 bg-slate-100' }

// ── Tree helpers for GenerateAIModal ─────────────────────────────────────────

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
  // Sort by externalId within each level for stable ordering
  const sort = (nodes: ReqTree[]) => {
    nodes.sort((a, b) => (a.externalId ?? a.id).localeCompare(b.externalId ?? b.id))
    nodes.forEach(n => sort(n.children))
  }
  sort(roots)
  return roots
}

function collectIds(node: ReqTree): string[] {
  return [node.id, ...node.children.flatMap(collectIds)]
}

type CheckState = 'checked' | 'unchecked' | 'indeterminate'

function nodeCheckState(node: ReqTree, selected: Set<string>): CheckState {
  const ids = collectIds(node)
  const count = ids.filter(id => selected.has(id)).length
  if (count === 0) return 'unchecked'
  if (count === ids.length) return 'checked'
  return 'indeterminate'
}

// Checkbox that supports indeterminate state via ref
function TreeCheckbox({ state, onChange }: { state: CheckState; onChange: () => void }) {
  const ref = useRef<HTMLInputElement>(null)
  const setRef = useCallback((el: HTMLInputElement | null) => {
    (ref as React.MutableRefObject<HTMLInputElement | null>).current = el
    if (el) el.indeterminate = state === 'indeterminate'
  }, [state])
  return (
    <input
      ref={setRef}
      type="checkbox"
      checked={state === 'checked'}
      onChange={onChange}
      className="shrink-0 accent-purple-600 cursor-pointer"
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
  const state = nodeCheckState(node, selected)
  const isExpanded = expanded.has(node.id)
  const hasChildren = node.children.length > 0
  const isSelected = state === 'checked'

  return (
    <>
      <div
        className={cn(
          'flex items-center gap-2 py-2 pr-4 hover:bg-slate-50 transition-colors group',
          isSelected && 'bg-purple-50 hover:bg-purple-50',
          state === 'indeterminate' && 'bg-purple-50/50 hover:bg-purple-50/50',
        )}
        style={{ paddingLeft: `${12 + indent * 20}px` }}
      >
        {/* Expand/collapse toggle */}
        <button
          type="button"
          onClick={() => hasChildren && onToggleExpand(node.id)}
          className={cn('shrink-0 text-slate-400', hasChildren ? 'hover:text-slate-600 cursor-pointer' : 'invisible')}
          tabIndex={-1}
        >
          {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        </button>

        <TreeCheckbox state={state} onChange={() => onToggleSelect(node)} />

        <div className="min-w-0 flex-1 cursor-pointer" onClick={() => onToggleSelect(node)}>
          <div className="flex items-center gap-2 flex-wrap">
            <Badge label={node.issueType} colorClass={reqTypeColor(node.issueType)} />
            {node.externalId && (
              <span className="text-xs font-mono text-slate-400">{node.externalId}</span>
            )}
            {hasChildren && (
              <span className="text-xs text-slate-400">({node.children.length})</span>
            )}
          </div>
          <p className="text-sm text-slate-800 leading-snug">{node.title}</p>
        </div>
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

function GenerateAIModal({ projectId, onClose }: { projectId: string; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [search, setSearch]       = useState('')
  const [selected, setSelected]   = useState<Set<string>>(new Set())
  const [expanded, setExpanded]   = useState<Set<string>>(new Set())
  const [started, setStarted]     = useState(false)

  const { data: reqs, isLoading: reqsLoading } = useQuery({
    queryKey: ['requirements', projectId],
    queryFn:  () => api.requirements(projectId),
  })

  const allReqs = reqs ?? []
  const isSearching = search.trim().length > 0

  // When searching: flat filtered list. Otherwise: tree.
  const filtered = allReqs.filter((r: Requirement) =>
    r.title.toLowerCase().includes(search.toLowerCase()) ||
    (r.externalId ?? '').toLowerCase().includes(search.toLowerCase())
  )
  const tree = buildTree(allReqs)

  // Expand all epics (depth 0 nodes with children) by default once data loads
  const [autoExpanded, setAutoExpanded] = useState(false)
  if (!autoExpanded && allReqs.length > 0) {
    const rootsWithChildren = buildTree(allReqs).filter(n => n.children.length > 0)
    if (rootsWithChildren.length > 0) {
      setExpanded(new Set(rootsWithChildren.map(n => n.id)))
      setAutoExpanded(true)
    } else {
      setAutoExpanded(true)
    }
  }

  function toggleExpand(id: string) {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  function toggleNode(node: ReqTree) {
    const ids = collectIds(node)
    const state = nodeCheckState(node, selected)
    setSelected(prev => {
      const next = new Set(prev)
      if (state === 'checked') {
        ids.forEach(id => next.delete(id))
      } else {
        ids.forEach(id => next.add(id))
      }
      return next
    })
  }

  function selectAll()   { setSelected(new Set(allReqs.map((r: Requirement) => r.id))) }
  function deselectAll() { setSelected(new Set()) }

  const mutation = useMutation({
    mutationFn: () => api.generateTestCasesFromAI(projectId, Array.from(selected)),
    onSuccess: () => {
      setStarted(true)
      void queryClient.invalidateQueries({ queryKey: ['testCases', projectId] })
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 flex flex-col max-h-[85vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200 shrink-0">
          <div className="flex items-center gap-2">
            <Sparkles size={18} className="text-purple-600" />
            <h2 className="font-semibold text-slate-900">Generate Test Cases with AI</h2>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>

        {started ? (
          /* ── Success state ── */
          <>
            <div className="px-5 py-8 text-center space-y-3">
              <div className="w-12 h-12 rounded-full bg-green-100 flex items-center justify-center mx-auto">
                <Sparkles size={22} className="text-green-600" />
              </div>
              <p className="text-sm font-medium text-slate-800">Generation started!</p>
              <p className="text-xs text-slate-500">
                Claude is generating test cases for {selected.size} requirement{selected.size !== 1 ? 's' : ''}.
                This may take a minute — refresh the list to see new test cases as they appear.
              </p>
            </div>
            <div className="px-5 py-4 border-t border-slate-200 flex justify-end shrink-0">
              <button onClick={onClose} className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors">
                Close
              </button>
            </div>
          </>
        ) : (
          /* ── Selection state ── */
          <>
            <div className="px-5 pt-4 pb-3 shrink-0 space-y-3">
              <p className="text-sm text-slate-600">
                Select requirements for Claude to generate test cases from. Selecting an Epic selects all its children. Generated cases appear in <strong>DRAFT</strong> status.
              </p>
              {/* Search */}
              <div className="relative">
                <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  placeholder="Search requirements…"
                  className="w-full pl-8 pr-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                />
              </div>
              {/* Select all / deselect */}
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-500">
                  {selected.size} of {allReqs.length} selected
                </span>
                <div className="flex gap-3 text-xs">
                  <button onClick={selectAll}   className="text-purple-600 hover:text-purple-800">Select all</button>
                  <button onClick={deselectAll} className="text-slate-500 hover:text-slate-700">Deselect all</button>
                </div>
              </div>
            </div>

            {/* Requirement list */}
            <div className="flex-1 overflow-y-auto border-t border-b border-slate-100 min-h-0">
              {reqsLoading ? (
                <div className="py-8 flex justify-center"><Loader2 size={20} className="animate-spin text-slate-400" /></div>
              ) : allReqs.length === 0 ? (
                <p className="py-8 text-sm text-slate-500 text-center">
                  No requirements found. Sync an integration first.
                </p>
              ) : isSearching ? (
                /* Flat list when searching */
                filtered.length === 0 ? (
                  <p className="py-8 text-sm text-slate-500 text-center">No matching requirements.</p>
                ) : (
                  <div className="divide-y divide-slate-50">
                    {filtered.map((r: Requirement) => (
                      <label
                        key={r.id}
                        className={cn(
                          'flex items-start gap-3 px-5 py-3 cursor-pointer hover:bg-slate-50 transition-colors',
                          selected.has(r.id) && 'bg-purple-50 hover:bg-purple-50',
                        )}
                      >
                        <input
                          type="checkbox"
                          checked={selected.has(r.id)}
                          onChange={() => setSelected(prev => {
                            const next = new Set(prev)
                            next.has(r.id) ? next.delete(r.id) : next.add(r.id)
                            return next
                          })}
                          className="mt-0.5 shrink-0 accent-purple-600"
                        />
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <Badge label={r.issueType} colorClass={reqTypeColor(r.issueType)} />
                            {r.externalId && <span className="text-xs font-mono text-slate-400">{r.externalId}</span>}
                          </div>
                          <p className="text-sm text-slate-800 mt-0.5 leading-snug">{r.title}</p>
                        </div>
                      </label>
                    ))}
                  </div>
                )
              ) : (
                /* Tree view when not searching */
                <div>
                  {tree.map(root => (
                    <ReqTreeRow
                      key={root.id}
                      node={root}
                      selected={selected}
                      expanded={expanded}
                      onToggleSelect={toggleNode}
                      onToggleExpand={toggleExpand}
                      indent={0}
                    />
                  ))}
                </div>
              )}
            </div>

            {/* Footer */}
            <div className="px-5 py-4 shrink-0 flex items-center justify-between gap-3">
              {mutation.isError && (
                <p className="text-xs text-red-600 flex-1">{(mutation.error as Error).message}</p>
              )}
              <div className="flex gap-2 ml-auto">
                <button
                  onClick={onClose}
                  className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={() => mutation.mutate()}
                  disabled={selected.size === 0 || mutation.isPending}
                  className="px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-lg hover:bg-purple-700 disabled:opacity-50 transition-colors flex items-center gap-2"
                >
                  {mutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  <Sparkles size={14} />
                  Generate for {selected.size > 0 ? `${selected.size} req.` : '…'}
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ── Automation Target Modal ───────────────────────────────────────────────────

function AutomationTargetModal({
  projectId,
  tc,
  onConfirm,
  onClose,
}: {
  projectId: string
  tc: ManagedTestCase
  onConfirm: (githubConfigId: string) => void
  onClose: () => void
}) {
  const navigate = useNavigate()
  const { base } = useProject()
  const { data: integrations, isLoading } = useQuery({
    queryKey: ['integrations', projectId],
    queryFn:  () => api.integrations(projectId),
  })

  const testAutomationRepos = (integrations ?? []).filter(
    (c: IntegrationConfig) => c.integrationType === 'GITHUB' && c.repoType === 'TEST_AUTOMATION' && c.enabled
  )

  const [selectedRepo, setSelectedRepo] = useState<string>(
    testAutomationRepos.length === 1 ? testAutomationRepos[0].id : ''
  )

  // Auto-select once data loads
  useEffect(() => {
    if (testAutomationRepos.length === 1 && !selectedRepo) {
      setSelectedRepo(testAutomationRepos[0].id)
    }
  }, [testAutomationRepos.length])

  function repoDisplayName(cfg: IntegrationConfig) {
    return cfg.displayName || cfg.connectionParams?.repo || cfg.id
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4">

        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div className="flex items-center gap-2">
            <GitBranch size={17} className="text-purple-600" />
            <h2 className="font-semibold text-slate-900 text-sm">Generate Automation Code</h2>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={16} /></button>
        </div>

        <div className="px-5 py-4 space-y-4">
          {isLoading ? (
            <div className="py-4 flex justify-center"><Loader2 size={18} className="animate-spin text-slate-400" /></div>
          ) : testAutomationRepos.length === 0 ? (
            /* No TEST_AUTOMATION repo linked */
            <div className="space-y-3">
              <div className="flex gap-3 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-sm text-amber-800">
                <AlertTriangle size={16} className="shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium">No test automation repo linked</p>
                  <p className="text-xs mt-0.5">
                    Link at least one GitHub repository with role <strong>Test Automation</strong> in Project Settings before generating automation code.
                  </p>
                </div>
              </div>
              <button
                onClick={() => navigate(`${base}/settings`)}
                className="w-full py-2 text-sm font-medium text-blue-700 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors flex items-center justify-center gap-2"
              >
                <Link2 size={14} /> Go to Project Settings
              </button>
            </div>
          ) : testAutomationRepos.length === 1 ? (
            /* Single repo — show confirmation */
            <div className="space-y-3">
              <p className="text-sm text-slate-600">
                A pull request will be raised to:
              </p>
              <div className="flex items-center gap-3 bg-slate-50 border border-slate-200 rounded-lg px-4 py-3">
                <GitBranch size={16} className="text-slate-500 shrink-0" />
                <div>
                  <p className="text-sm font-medium text-slate-900">
                    {repoDisplayName(testAutomationRepos[0])}
                  </p>
                  {testAutomationRepos[0].connectionParams?.repo && (
                    <p className="text-xs text-slate-500 font-mono mt-0.5">
                      {testAutomationRepos[0].connectionParams.repo}
                    </p>
                  )}
                </div>
              </div>
              <p className="text-xs text-slate-500">
                Claude will generate automation code for <strong>{tc.title}</strong> and open a PR for review.
              </p>
            </div>
          ) : (
            /* Multiple repos — let user pick */
            <div className="space-y-3">
              <p className="text-sm text-slate-600">
                Select the test automation repository to raise the PR against:
              </p>
              <div className="space-y-2">
                {testAutomationRepos.map((cfg: IntegrationConfig) => (
                  <label
                    key={cfg.id}
                    className={cn(
                      'flex items-center gap-3 px-4 py-3 rounded-lg border cursor-pointer transition-colors',
                      selectedRepo === cfg.id
                        ? 'border-purple-400 bg-purple-50'
                        : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                    )}
                  >
                    <input
                      type="radio"
                      name="repoTarget"
                      value={cfg.id}
                      checked={selectedRepo === cfg.id}
                      onChange={() => setSelectedRepo(cfg.id)}
                      className="accent-purple-600"
                    />
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-slate-900">{repoDisplayName(cfg)}</p>
                      {cfg.connectionParams?.repo && (
                        <p className="text-xs text-slate-500 font-mono">{cfg.connectionParams.repo}</p>
                      )}
                    </div>
                  </label>
                ))}
              </div>
            </div>
          )}
        </div>

        {testAutomationRepos.length > 0 && (
          <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={() => selectedRepo && onConfirm(selectedRepo)}
              disabled={!selectedRepo}
              className="px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-lg hover:bg-purple-700 disabled:opacity-50 transition-colors flex items-center gap-2"
            >
              <Sparkles size={14} />
              Generate &amp; Raise PR
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Test Case Detail Panel ────────────────────────────────────────────────────

// ── Linked Requirements Section ───────────────────────────────────────────────

function LinkedRequirementsSection({
  tc,
  projectId,
}: {
  tc: ManagedTestCase
  projectId: string
}) {
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [search, setSearch] = useState('')

  const { data: allRequirements = [] } = useQuery({
    queryKey: ['requirements', projectId],
    queryFn: () => api.requirements(projectId),
    enabled: adding,
  })

  const linkedIds = new Set(tc.linkedRequirementIds ?? [])

  const filtered = allRequirements.filter(r =>
    !linkedIds.has(r.id) && (
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      (r.externalId ?? '').toLowerCase().includes(search.toLowerCase())
    )
  )

  const linkMutation = useMutation({
    mutationFn: (reqId: string) => api.linkRequirement(projectId, tc.id, reqId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['testCases', projectId] })
      setAdding(false)
      setSearch('')
    },
  })
  const unlinkMutation = useMutation({
    mutationFn: (reqId: string) => api.unlinkRequirement(projectId, tc.id, reqId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['testCases', projectId] }),
  })

  const { data: reqDetails } = useQuery({
    queryKey: ['requirements', projectId],
    queryFn: () => api.requirements(projectId),
    enabled: linkedIds.size > 0,
  })
  const reqMap = new Map((reqDetails ?? []).map(r => [r.id, r]))

  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Linked Requirements</p>
        <button
          onClick={() => setAdding(v => !v)}
          className="text-xs text-blue-600 hover:text-blue-800 flex items-center gap-0.5"
        >
          <Plus size={11} /> Link
        </button>
      </div>

      {adding && (
        <div className="mb-2 border border-slate-200 rounded-lg overflow-hidden">
          <div className="flex items-center gap-1 px-2 py-1.5 border-b border-slate-100">
            <Search size={12} className="text-slate-400" />
            <input
              autoFocus
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search requirements…"
              className="flex-1 text-xs outline-none"
            />
          </div>
          <div className="max-h-36 overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="text-xs text-slate-400 px-3 py-2">No unlinked requirements found</p>
            ) : filtered.slice(0, 12).map(r => (
              <button
                key={r.id}
                onClick={() => linkMutation.mutate(r.id)}
                disabled={linkMutation.isPending}
                className="w-full text-left px-3 py-1.5 text-xs hover:bg-blue-50 flex items-center gap-2 disabled:opacity-50"
              >
                {r.externalId && (
                  <span className="text-slate-400 font-mono shrink-0">{r.externalId}</span>
                )}
                <span className="text-slate-700 truncate">{r.title}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {linkedIds.size === 0 ? (
        <p className="text-xs text-slate-400 italic">No requirements linked</p>
      ) : (
        <div className="space-y-1">
          {Array.from(linkedIds).map(reqId => {
            const req = reqMap.get(reqId)
            return (
              <div key={reqId} className="flex items-center gap-1.5 group">
                <span className={cn(
                  'flex-1 text-xs truncate rounded px-1.5 py-0.5',
                  reqId === tc.sourceRequirementId
                    ? 'bg-blue-50 text-blue-700'
                    : 'bg-slate-50 text-slate-600'
                )}>
                  {req?.externalId && <span className="font-mono mr-1 opacity-60">{req.externalId}</span>}
                  {req?.title ?? reqId.slice(0, 8) + '…'}
                  {reqId === tc.sourceRequirementId && (
                    <span className="ml-1 opacity-60 text-[10px]">(source)</span>
                  )}
                </span>
                {req?.sourceUrl && (
                  <a href={req.sourceUrl} target="_blank" rel="noreferrer"
                     title="Open the requirement in Azure DevOps"
                     className="text-slate-300 hover:text-blue-600 shrink-0">
                    <ExternalLink size={11} />
                  </a>
                )}
                {reqId !== tc.sourceRequirementId && (
                  <button
                    onClick={() => unlinkMutation.mutate(reqId)}
                    disabled={unlinkMutation.isPending}
                    className="opacity-0 group-hover:opacity-100 text-slate-300 hover:text-red-400 transition-opacity"
                  >
                    <X size={11} />
                  </button>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ── Test Case Detail Panel ────────────────────────────────────────────────────

function TestCaseDetailPanel({
  tc,
  projectId,
  onClose,
  onEdit,
}: {
  tc: ManagedTestCase
  projectId: string
  onClose: () => void
  onEdit: (tc: ManagedTestCase) => void
}) {
  const queryClient = useQueryClient()
  const [showAutomationModal, setShowAutomationModal] = useState(false)

  const submitMutation = useMutation({
    mutationFn: () => api.submitForReview(projectId, tc.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['testCases', projectId] }),
  })
  const approveMutation = useMutation({
    mutationFn: () => api.approveTestCase(projectId, tc.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['testCases', projectId] }),
  })
  const rejectMutation = useMutation({
    mutationFn: () => api.rejectTestCase(projectId, tc.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['testCases', projectId] }),
  })
  const automationMutation = useMutation({
    mutationFn: (githubConfigId: string) => api.triggerAutomation(projectId, tc.id, githubConfigId),
    onSuccess: () => {
      setShowAutomationModal(false)
      void queryClient.invalidateQueries({ queryKey: ['testCases', projectId] })
    },
  })

  return (
    <div className="w-96 shrink-0 border-l border-slate-200 bg-white flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Detail</span>
        <div className="flex items-center gap-1">
          <button onClick={() => onEdit(tc)} title="Edit test case"
            className="flex items-center gap-1 text-xs font-medium text-slate-600 hover:text-blue-600 px-2 py-1 rounded hover:bg-slate-100">
            <Pencil size={13} /> Edit
          </button>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={16} /></button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-5">
        {/* Title + badges */}
        <div>
          <h3 className="font-semibold text-slate-900 text-sm leading-snug mb-2">{tc.title}</h3>
          <div className="flex flex-wrap gap-1.5">
            <Badge label={tc.priority} colorClass={priorityColor(tc.priority)} />
            <Badge label={tc.status.replace('_', ' ')} colorClass={statusColor(tc.status)} />
            {tc.coverageStatus && (
              <Badge label={tc.coverageStatus.replace('_', ' ')} colorClass={coverageColor(tc.coverageStatus)} />
            )}
          </div>
        </div>

        {/* Tags */}
        <CaseTagsCard projectId={projectId} caseId={tc.id} />

        {/* Description */}
        {tc.description && (
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Description</p>
            <Markdown>{tc.description}</Markdown>
          </div>
        )}

        {/* Preconditions */}
        {tc.preconditions && (
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Preconditions</p>
            <Markdown>{tc.preconditions}</Markdown>
          </div>
        )}

        {/* Expected result */}
        {tc.expectedResult && (
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Expected result</p>
            <Markdown>{tc.expectedResult}</Markdown>
          </div>
        )}

        {/* Steps */}
        {tc.steps && tc.steps.length > 0 && (
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Steps</p>
            <ol className="space-y-2">
              {tc.steps.map((step, i) => (
                <li key={step.id ?? i} className="flex gap-2.5 text-sm">
                  <span className="font-mono text-xs text-slate-400 shrink-0 mt-0.5 w-5 text-right">{i + 1}.</span>
                  <div>
                    <p className="text-slate-800">{step.action}</p>
                    {step.expectedResult && (
                      <p className="text-xs text-slate-500 mt-0.5">→ {step.expectedResult}</p>
                    )}
                  </div>
                </li>
              ))}
            </ol>
          </div>
        )}

        {/* Automation */}
        <div>
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Automation</p>
          <div className="flex items-center gap-2 flex-wrap">
            {tc.automationStatus === 'GENERATING' ? (
              <span className="inline-flex items-center gap-1 text-xs text-blue-600">
                <Loader2 size={12} className="animate-spin" /> Generating…
              </span>
            ) : (
              <Badge
                label={tc.automationStatus.replace('_', ' ')}
                colorClass={automationColor(tc.automationStatus)}
              />
            )}
            {tc.automationPrUrl && (tc.automationStatus === 'PR_CREATED' || tc.automationStatus === 'PR_MERGED') && (
              <a
                href={tc.automationPrUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-xs text-blue-600 hover:underline"
              >
                View PR <ExternalLink size={11} />
              </a>
            )}
          </div>
        </div>

        {/* acRefs */}
        {tc.acRefs && tc.acRefs.length > 0 && (
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Acceptance Criteria</p>
            <div className="flex flex-wrap gap-1">
              {tc.acRefs.map(ref => (
                <span key={ref} className="px-2 py-0.5 text-xs bg-slate-100 text-slate-600 rounded-full">{ref}</span>
              ))}
            </div>
          </div>
        )}

        {/* Parametrization properties (matrix axes) */}
        <CasePropertiesSection projectId={projectId} caseId={tc.id} />

        {/* Linked Requirements */}
        <LinkedRequirementsSection tc={tc} projectId={projectId} />

        {/* Impact Analysis attribution */}
        {tc.lastUpdatedByAnalysisId && (
          <div className="flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
            <Sparkles size={13} className="text-amber-500 shrink-0 mt-0.5" />
            <div>
              <p className="text-xs font-medium text-amber-800">Updated by Impact Analysis</p>
              <p className="text-[11px] text-amber-600 mt-0.5 font-mono break-all">
                {tc.lastUpdatedByAnalysisId.slice(0, 8)}…
              </p>
            </div>
          </div>
        )}

        {/* Timestamps */}
        <div className="text-xs text-slate-400 space-y-0.5 pt-2 border-t border-slate-100">
          {tc.createdBy && (
            <p>
              Created by {tc.createdBy === 'AGENT' ? (
                <span className="inline-flex items-center gap-0.5"><Bot size={11} /> Agent</span>
              ) : tc.createdBy}
            </p>
          )}
          {tc.updatedBy && tc.updatedBy !== tc.createdBy && (
            <p>
              Last updated by {tc.updatedBy === 'AGENT' ? (
                <span className="inline-flex items-center gap-0.5"><Bot size={11} /> Agent</span>
              ) : tc.updatedBy === 'IMPACT_ANALYSIS' ? (
                <span className="inline-flex items-center gap-0.5"><Sparkles size={11} /> Impact Analysis</span>
              ) : tc.updatedBy}
            </p>
          )}
          <p>Created {relativeTime(tc.createdAt)}</p>
          <p>Updated {relativeTime(tc.updatedAt)}</p>
          {tc.lastExecutedAt && <p>Last executed {relativeTime(tc.lastExecutedAt)}</p>}
        </div>
      </div>

      {/* Actions */}
      <div className="px-4 py-3 border-t border-slate-200 space-y-2">
        {tc.status === 'DRAFT' && (
          <button
            onClick={() => submitMutation.mutate()}
            disabled={submitMutation.isPending}
            className="w-full py-2 text-sm font-medium text-blue-700 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
          >
            {submitMutation.isPending && <Loader2 size={14} className="animate-spin" />}
            Submit for Review
          </button>
        )}
        {tc.status === 'UNDER_REVIEW' && (
          <div className="flex gap-2">
            <button
              onClick={() => approveMutation.mutate()}
              disabled={approveMutation.isPending}
              className="flex-1 py-2 text-sm font-medium text-green-700 bg-green-50 border border-green-200 rounded-lg hover:bg-green-100 disabled:opacity-50 transition-colors flex items-center justify-center gap-1"
            >
              {approveMutation.isPending && <Loader2 size={14} className="animate-spin" />}
              Approve
            </button>
            <button
              onClick={() => rejectMutation.mutate()}
              disabled={rejectMutation.isPending}
              className="flex-1 py-2 text-sm font-medium text-red-700 bg-red-50 border border-red-200 rounded-lg hover:bg-red-100 disabled:opacity-50 transition-colors flex items-center justify-center gap-1"
            >
              {rejectMutation.isPending && <Loader2 size={14} className="animate-spin" />}
              Reject
            </button>
          </div>
        )}
        {tc.status === 'APPROVED' && tc.automationStatus === 'NOT_STARTED' && (
          <button
            onClick={() => setShowAutomationModal(true)}
            disabled={automationMutation.isPending}
            className="w-full py-2 text-sm font-medium text-purple-700 bg-purple-50 border border-purple-200 rounded-lg hover:bg-purple-100 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
          >
            {automationMutation.isPending && <Loader2 size={14} className="animate-spin" />}
            <Sparkles size={14} />
            Generate Automation
          </button>
        )}
      </div>

      {showAutomationModal && (
        <AutomationTargetModal
          projectId={projectId}
          tc={tc}
          onConfirm={(configId) => automationMutation.mutate(configId)}
          onClose={() => setShowAutomationModal(false)}
        />
      )}
    </div>
  )
}

// ── Parametrization properties (matrix axes) ────────────────────────────────────

function CaseTagsCard({ projectId, caseId }: { projectId: string; caseId: string }) {
  const qc = useQueryClient()
  const [input, setInput] = useState('')

  const { data: tags = [] } = useQuery({
    queryKey: ['caseTags', projectId, caseId],
    queryFn: () => api.caseTags(projectId, caseId),
  })
  const { data: suggestions = [] } = useQuery({
    queryKey: ['tagSuggestions', projectId],
    queryFn: () => api.tagSuggestions(projectId),
  })

  const addMutation = useMutation({
    mutationFn: (name: string) => api.addCaseTag(projectId, caseId, name),
    onSuccess: (updated) => {
      qc.setQueryData(['caseTags', projectId, caseId], updated)
      setInput('')
      void qc.invalidateQueries({ queryKey: ['tagSuggestions', projectId] })
    },
  })
  const removeMutation = useMutation({
    mutationFn: (name: string) => api.removeCaseTag(projectId, caseId, name),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['caseTags', projectId, caseId] }),
  })

  const submit = () => { const v = input.trim(); if (v) addMutation.mutate(v) }

  return (
    <div>
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Tags</p>
      <div className="flex flex-wrap gap-1.5 mb-2">
        {tags.length === 0 && <span className="text-xs text-slate-400">No tags yet.</span>}
        {tags.map(t => (
          <span key={t} className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-700">
            {t}
            <button onClick={() => removeMutation.mutate(t)} className="text-slate-400 hover:text-red-500" title="Remove tag">
              <X size={11} />
            </button>
          </span>
        ))}
      </div>
      <div className="flex items-center gap-1.5">
        <input
          list={`tagsugg-${caseId}`}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); submit() } }}
          placeholder="Add tag…"
          className="flex-1 border border-slate-200 rounded px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <datalist id={`tagsugg-${caseId}`}>
          {suggestions.filter(s => !tags.includes(s)).map(s => <option key={s} value={s} />)}
        </datalist>
        <button onClick={submit} disabled={!input.trim() || addMutation.isPending}
          className="text-xs px-2 py-1 border border-slate-200 rounded hover:bg-slate-50 disabled:opacity-50">
          Add
        </button>
      </div>
    </div>
  )
}

function CasePropertiesSection({ projectId, caseId }: { projectId: string; caseId: string }) {
  const qc = useQueryClient()
  const { data } = useQuery({
    queryKey: ['caseProperties', projectId, caseId],
    queryFn: () => api.caseProperties(projectId, caseId),
  })
  const [rows, setRows] = useState<CaseProperty[]>([])
  const [dirty, setDirty] = useState(false)

  useEffect(() => {
    if (data) { setRows(data.length ? data : [{ name: '', value: '' }]); setDirty(false) }
  }, [data])

  const saveMutation = useMutation({
    mutationFn: () => api.replaceCaseProperties(projectId, caseId,
      rows.filter(r => r.name.trim() && r.value.trim())),
    onSuccess: () => { setDirty(false); void qc.invalidateQueries({ queryKey: ['caseProperties', projectId, caseId] }) },
  })

  const update = (i: number, field: keyof CaseProperty, v: string) => {
    setRows(rs => rs.map((r, idx) => idx === i ? { ...r, [field]: v } : r)); setDirty(true)
  }
  const addRow = () => { setRows(rs => [...rs, { name: '', value: '' }]); setDirty(true) }
  const removeRow = (i: number) => { setRows(rs => rs.filter((_, idx) => idx !== i)); setDirty(true) }

  // Full-matrix size = product of distinct value counts per property name.
  const byName = new Map<string, Set<string>>()
  rows.forEach(r => {
    if (r.name.trim() && r.value.trim()) {
      const s = byName.get(r.name.trim()) ?? new Set<string>()
      s.add(r.value.trim()); byName.set(r.name.trim(), s)
    }
  })
  let combos = byName.size === 0 ? 0 : 1
  byName.forEach(s => { combos *= s.size })

  return (
    <div>
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Parameters (matrix axes)</p>
      <div className="space-y-1.5">
        {rows.map((r, i) => (
          <div key={i} className="flex items-center gap-1.5">
            <input
              value={r.name}
              onChange={e => update(i, 'name', e.target.value)}
              placeholder="name (e.g. browser)"
              className="flex-1 min-w-0 border border-slate-200 rounded px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <input
              value={r.value}
              onChange={e => update(i, 'value', e.target.value)}
              placeholder="value (e.g. Chrome)"
              className="flex-1 min-w-0 border border-slate-200 rounded px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <button onClick={() => removeRow(i)} className="p-1 text-slate-300 hover:text-red-500" title="Remove">
              <X size={12} />
            </button>
          </div>
        ))}
      </div>
      <div className="flex items-center justify-between mt-2">
        <button onClick={addRow} className="text-xs text-slate-500 hover:text-blue-600 flex items-center gap-1">
          <Plus size={12} /> add value
        </button>
        <span className="text-[11px] text-slate-400">
          {combos > 0 ? `Full matrix → ${combos} execution${combos === 1 ? '' : 's'}/run` : 'No parameters'}
        </span>
      </div>
      {dirty && (
        <button
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
          className="mt-2 w-full py-1.5 text-xs font-medium text-white bg-blue-600 rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {saveMutation.isPending ? 'Saving…' : 'Save parameters'}
        </button>
      )}
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function TestCasesPage() {
  const { projectId } = useProject()
  const queryClient = useQueryClient()

  const [selectedSuiteId, setSelectedSuiteId] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [search, setSearch] = useState('')
  // Keep only the id selected; the panel reads the live row from the query so it
  // always reflects the latest data after an edit / link / status change.
  const [selectedTcId, setSelectedTcId] = useState<string | null>(null)
  const [showNewModal, setShowNewModal] = useState(false)
  const [editingTc, setEditingTc] = useState<ManagedTestCase | null>(null)
  const [editSuite, setEditSuite] = useState<TestSuite | null>(null)
  const [showAIModal, setShowAIModal] = useState(false)
  const [showNewSuiteForm, setShowNewSuiteForm] = useState(false)
  const [newSuiteName, setNewSuiteName] = useState('')
  const [newSuitePlanType, setNewSuitePlanType] = useState('')

  const { data: suites = [], isLoading: suitesLoading } = useQuery({
    queryKey: ['testSuites', projectId],
    queryFn: () => api.testSuites(projectId!),
    enabled: !!projectId,
  })

  const { data: testCases = [], isLoading: tcLoading, error: tcError } = useQuery({
    queryKey: ['testCases', projectId, statusFilter, selectedSuiteId, search],
    queryFn: () => api.testCases(projectId!, {
      status: statusFilter || undefined,
      suiteId: selectedSuiteId || undefined,
      search: search || undefined,
    }),
    enabled: !!projectId,
  })

  // Live selected test case derived from the query — reloads on any invalidation.
  const selectedTc = selectedTcId ? testCases.find(tc => tc.id === selectedTcId) ?? null : null

  const createSuiteMutation = useMutation({
    mutationFn: (input: { name: string; parentId?: string | null; planType?: string }) =>
      api.createTestSuite(projectId!, {
        name: input.name,
        parentId: input.parentId ?? undefined,
        planType: input.planType || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['testSuites', projectId] })
      setShowNewSuiteForm(false)
      setNewSuiteName('')
      setNewSuitePlanType('')
    },
  })

  const addSuite = () => {
    if (newSuiteName.trim()) {
      createSuiteMutation.mutate({
        name: newSuiteName.trim(),
        parentId: selectedSuiteId,   // nest under the selected suite (root if "All")
        planType: newSuitePlanType,
      })
    }
  }

  const deleteSuiteMutation = useMutation({
    mutationFn: (suiteId: string) => api.deleteTestSuite(projectId!, suiteId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['testSuites', projectId] })
      if (selectedSuiteId) setSelectedSuiteId(null)
    },
  })

  if (tcLoading && !testCases.length) return <LoadingSpinner message="Loading test cases…" />
  if (tcError) return <ErrorMessage message="Failed to load test cases." />

  const suiteName = suites.find(s => s.id === selectedSuiteId)?.name ?? 'All'

  // Render the suite tree (parent/child) with indentation.
  const renderSuiteNodes = (parentId: string | null, depth: number): React.ReactNode =>
    suites
      .filter(s => (s.parentId ?? null) === parentId)
      .map(suite => (
        <div key={suite.id}>
          <div className="group flex items-center gap-1 mx-1">
            <button
              onClick={() => setSelectedSuiteId(suite.id)}
              style={{ paddingLeft: 12 + depth * 14 }}
              className={cn(
                'flex-1 flex items-center gap-2 pr-3 py-2 text-sm rounded-lg transition-colors text-left',
                selectedSuiteId === suite.id
                  ? 'bg-blue-50 text-blue-700 font-medium'
                  : 'text-slate-600 hover:bg-slate-50',
              )}
            >
              <FolderOpen size={14} className="shrink-0" />
              <span className="truncate">{suite.name}</span>
              {suite.planType && (
                <span className="ml-auto text-[10px] px-1 py-0.5 rounded bg-slate-100 text-slate-500 shrink-0">
                  {suite.planType}
                </span>
              )}
            </button>
            <button
              onClick={() => setEditSuite(suite)}
              className="opacity-0 group-hover:opacity-100 p-1 text-slate-300 hover:text-blue-500 transition-all"
              title="Edit suite"
            >
              <Pencil size={11} />
            </button>
            <button
              onClick={() => deleteSuiteMutation.mutate(suite.id)}
              className="opacity-0 group-hover:opacity-100 p-1 text-slate-300 hover:text-red-500 transition-all"
              title="Delete suite"
            >
              <X size={12} />
            </button>
          </div>
          {renderSuiteNodes(suite.id, depth + 1)}
        </div>
      ))

  return (
    <div className="flex flex-col h-full space-y-0">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Test Cases</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            {testCases.length} {testCases.length === 1 ? 'test case' : 'test cases'}
            {selectedSuiteId ? ` in ${suiteName}` : ' total'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowAIModal(true)}
            className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-purple-700 bg-purple-50 border border-purple-200 rounded-lg hover:bg-purple-100 transition-colors"
          >
            <Sparkles size={15} />
            Generate from AI
          </button>
          <button
            onClick={() => setShowNewModal(true)}
            className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus size={15} />
            New Test Case
          </button>
        </div>
      </div>

      <div className="flex gap-5 min-h-0 flex-1">
        {/* Left sidebar — Suite tree */}
        <div className="w-56 shrink-0 flex flex-col">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm flex-1 flex flex-col">
            <div className="px-3 py-3 border-b border-slate-100">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                <Layers size={12} /> Suites
              </span>
            </div>
            <div className="flex-1 overflow-y-auto py-1">
              {/* All */}
              <button
                onClick={() => setSelectedSuiteId(null)}
                className={cn(
                  'w-full flex items-center gap-2 px-3 py-2 text-sm rounded-lg mx-1 transition-colors text-left',
                  selectedSuiteId === null
                    ? 'bg-blue-50 text-blue-700 font-medium'
                    : 'text-slate-600 hover:bg-slate-50'
                )}
              >
                <FolderOpen size={14} />
                All
              </button>

              {suitesLoading && (
                <div className="px-3 py-2 text-xs text-slate-400">Loading…</div>
              )}

              {renderSuiteNodes(null, 0)}
            </div>

            {/* New suite */}
            <div className="p-3 border-t border-slate-100">
              {showNewSuiteForm ? (
                <div className="space-y-1.5">
                  <p className="text-[10px] text-slate-400">
                    Adds under: <span className="font-medium text-slate-600">{selectedSuiteId ? suiteName : 'root'}</span>
                  </p>
                  <input
                    type="text"
                    autoFocus
                    value={newSuiteName}
                    onChange={e => setNewSuiteName(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter') addSuite()
                      if (e.key === 'Escape') { setShowNewSuiteForm(false); setNewSuiteName('') }
                    }}
                    placeholder="Suite / plan name"
                    className="w-full border border-slate-200 rounded px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                  <input
                    type="text"
                    value={newSuitePlanType}
                    onChange={e => setNewSuitePlanType(e.target.value)}
                    placeholder="Plan type (optional) — SMOKE, REGRESSION…"
                    className="w-full border border-slate-200 rounded px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                  <div className="flex gap-1">
                    <button
                      onClick={addSuite}
                      disabled={!newSuiteName.trim() || createSuiteMutation.isPending}
                      className="flex-1 py-1 text-xs font-medium text-white bg-blue-600 rounded hover:bg-blue-700 disabled:opacity-50"
                    >
                      Add
                    </button>
                    <button
                      onClick={() => { setShowNewSuiteForm(false); setNewSuiteName(''); setNewSuitePlanType('') }}
                      className="flex-1 py-1 text-xs font-medium text-slate-600 bg-slate-100 rounded hover:bg-slate-200"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => setShowNewSuiteForm(true)}
                  className="w-full flex items-center gap-1.5 text-xs text-slate-500 hover:text-blue-600 transition-colors"
                >
                  <Plus size={12} /> New Suite
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Main area */}
        <div className={cn('flex-1 min-w-0 flex gap-4', selectedTc ? '' : '')}>
          <div className="flex-1 min-w-0 flex flex-col gap-4">
            {/* Filter bar */}
            <div className="flex items-center gap-3">
              <select
                value={statusFilter}
                onChange={e => setStatusFilter(e.target.value)}
                className="border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              >
                <option value="">All statuses</option>
                <option value="DRAFT">Draft</option>
                <option value="UNDER_REVIEW">Under Review</option>
                <option value="APPROVED">Approved</option>
                <option value="DEPRECATED">Deprecated</option>
              </select>
              <input
                type="text"
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="Search test cases…"
                className="flex-1 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Test case list */}
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm flex-1">
              {tcLoading && (
                <div className="py-8 text-center text-sm text-slate-400">Loading…</div>
              )}
              {!tcLoading && testCases.length === 0 && (
                <div className="py-16 text-center">
                  <Layers size={32} className="mx-auto text-slate-300 mb-3" />
                  <p className="text-sm text-slate-500">No test cases found.</p>
                  <p className="text-xs text-slate-400 mt-1">Create one manually or generate from AI.</p>
                </div>
              )}
              <div className="divide-y divide-slate-50">
                {testCases.map(tc => (
                  <div
                    key={tc.id}
                    onClick={() => setSelectedTcId(tc.id === selectedTcId ? null : tc.id)}
                    className={cn(
                      'flex items-center gap-3 px-4 py-3 cursor-pointer transition-colors hover:bg-slate-50',
                      selectedTcId === tc.id ? 'bg-blue-50' : ''
                    )}
                  >
                    <div className="shrink-0">
                      <Badge label={tc.priority.slice(0, 1)} colorClass={priorityColor(tc.priority)} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="text-sm font-medium text-slate-900 truncate">{tc.title}</p>
                        {tc.createdBy === 'AGENT' && (
                          <Bot size={13} className="text-purple-500 shrink-0" aria-label="Created by Agent" />
                        )}
                      </div>
                      <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                        <Badge label={tc.status.replace('_', ' ')} colorClass={statusColor(tc.status)} />
                        {tc.automationStatus !== 'NOT_STARTED' && (
                          <span className={cn(
                            'inline-flex items-center gap-1 text-xs font-medium px-1.5 py-0.5 rounded-full',
                            automationColor(tc.automationStatus)
                          )}>
                            {tc.automationStatus === 'GENERATING' && <Loader2 size={10} className="animate-spin" />}
                            {tc.automationStatus.replace('_', ' ')}
                          </span>
                        )}
                        <span className="text-xs text-slate-400">{relativeTime(tc.updatedAt)}</span>
                      </div>
                    </div>
                    <ChevronRight size={14} className="text-slate-300 shrink-0" />
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Detail panel */}
          {selectedTc && (
            <TestCaseDetailPanel
              tc={selectedTc}
              projectId={projectId!}
              onClose={() => setSelectedTcId(null)}
              onEdit={tc => setEditingTc(tc)}
            />
          )}
        </div>
      </div>

      {/* Modals */}
      {(showNewModal || editingTc) && (
        <TestCaseFormModal
          key={editingTc?.id ?? 'new'}
          projectId={projectId!}
          suites={suites}
          editing={editingTc}
          onClose={() => { setShowNewModal(false); setEditingTc(null) }}
        />
      )}
      {showAIModal && (
        <GenerateAIModal
          projectId={projectId!}
          onClose={() => setShowAIModal(false)}
        />
      )}
      {editSuite && (
        <SuiteFormModal
          projectId={projectId!}
          suite={editSuite}
          suites={suites}
          onClose={() => setEditSuite(null)}
        />
      )}
    </div>
  )
}

function SuiteFormModal({
  projectId,
  suite,
  suites,
  onClose,
}: {
  projectId: string
  suite: TestSuite
  suites: TestSuite[]
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [name, setName] = useState(suite.name)
  const [planType, setPlanType] = useState(suite.planType ?? '')
  const [parentId, setParentId] = useState(suite.parentId ?? '')
  const [description, setDescription] = useState(suite.description ?? '')
  const [active, setActive] = useState(suite.active)
  const [error, setError] = useState<string | null>(null)

  const PLAN_TYPES = ['', 'SMOKE', 'REGRESSION', 'SANITY', 'FUNCTIONAL', 'INTEGRATION', 'ACCEPTANCE']

  const save = useMutation({
    mutationFn: () => api.updateTestSuite(projectId, suite.id, {
      name: name.trim(),
      description: description.trim() || undefined,
      parentId: parentId || null,
      planType: planType || undefined,
      active,
    }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['testSuites', projectId] }); onClose() },
    onError: (e: Error) => setError(e.message),
  })

  const inputCls = 'w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">Edit Suite / Plan</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>
        <div className="px-5 py-4 space-y-3">
          {error && <ErrorMessage message={error} />}
          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1">Name *</label>
            <input value={name} onChange={e => setName(e.target.value)} className={inputCls} autoFocus />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Plan type</label>
              <select value={planType} onChange={e => setPlanType(e.target.value)} className={inputCls}>
                {PLAN_TYPES.map(t => <option key={t} value={t}>{t || '— None —'}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">Parent</label>
              <select value={parentId} onChange={e => setParentId(e.target.value)} className={inputCls}>
                <option value="">— Root —</option>
                {suites.filter(s => s.id !== suite.id).map(s => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1">Description</label>
            <MarkdownEditor value={description} onChange={setDescription} rows={3}
              placeholder="What this plan covers" />
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} />
            Active
          </label>
        </div>
        <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
          <button onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50">
            Cancel
          </button>
          <button onClick={() => name.trim() && save.mutate()} disabled={!name.trim() || save.isPending}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2">
            {save.isPending && <Loader2 size={14} className="animate-spin" />} Save
          </button>
        </div>
      </div>
    </div>
  )
}
