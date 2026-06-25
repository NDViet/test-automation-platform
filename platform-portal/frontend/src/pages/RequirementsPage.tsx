import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProject, useProjectFilter } from '@/components/layout/ProjectLayout'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime, cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import RichText from '@/components/RichText'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import {
  ChevronRight,
  ChevronDown,
  ChevronLeft,
  Search,
  X,
  ExternalLink,
  FileText,
  GitBranch,
  List,
  Network,
} from 'lucide-react'
import type { Requirement, RequirementRef } from '@/lib/types'

const PAGE_SIZE_OPTIONS = [25, 50, 100]
const MAX_TREE_ROOTS = 300

// ── Colour helpers ────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<string, string> = {
  OPEN: 'text-blue-700 bg-blue-100',
  IN_PROGRESS: 'text-yellow-700 bg-yellow-100',
  DONE: 'text-green-700 bg-green-100',
  BLOCKED: 'text-red-700 bg-red-100',
  REJECTED: 'text-slate-600 bg-slate-100',
  CLOSED: 'text-slate-600 bg-slate-100',
}
const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: 'text-red-700 bg-red-100',
  HIGH: 'text-orange-700 bg-orange-100',
  MEDIUM: 'text-yellow-700 bg-yellow-100',
  LOW: 'text-slate-600 bg-slate-100',
}
const ISSUE_TYPE_COLORS: Record<string, string> = {
  EPIC: 'text-purple-700 bg-purple-100',
  CAPABILITY: 'text-fuchsia-700 bg-fuchsia-100',
  ENABLER: 'text-indigo-700 bg-indigo-100',
  FEATURE: 'text-sky-700 bg-sky-100',
  REQUIREMENT: 'text-cyan-700 bg-cyan-100',
  STORY: 'text-blue-700 bg-blue-100',
  BUG: 'text-red-700 bg-red-100',
  DEFECT: 'text-red-700 bg-red-100',
  TASK: 'text-slate-600 bg-slate-100',
  SUBTASK: 'text-teal-700 bg-teal-100',
  SPIKE: 'text-orange-700 bg-orange-100',
}
function statusColor(s: string) {
  return STATUS_COLORS[s?.toUpperCase()] ?? 'text-slate-600 bg-slate-100'
}
function priorityColor(p: string | null) {
  return PRIORITY_COLORS[(p ?? '').toUpperCase()] ?? 'text-slate-600 bg-slate-100'
}
function issueTypeColor(t: string) {
  return ISSUE_TYPE_COLORS[t?.toUpperCase()] ?? 'text-slate-600 bg-slate-100'
}
function fmtDate(iso: string | null) {
  return iso
    ? new Date(iso).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    : '—'
}

// ── Tree data structure ───────────────────────────────────────────────────────

interface TreeNode extends Requirement {
  children: TreeNode[]
}

function buildTree(items: Requirement[]): TreeNode[] {
  const map = new Map<string, TreeNode>()
  items.forEach(r => map.set(r.id, { ...r, children: [] }))
  const roots: TreeNode[] = []
  map.forEach(node => {
    if (node.parentId && map.has(node.parentId)) map.get(node.parentId)!.children.push(node)
    else roots.push(node)
  })
  // Siblings ordered by creation date, newest first.
  function sortNodes(nodes: TreeNode[]) {
    nodes.sort((a, b) => {
      const da = a.createdDate ? Date.parse(a.createdDate) : 0
      const db = b.createdDate ? Date.parse(b.createdDate) : 0
      if (db !== da) return db - da
      return (b.externalId ?? '').localeCompare(a.externalId ?? '')
    })
    nodes.forEach(n => sortNodes(n.children))
  }
  sortNodes(roots)
  return roots
}

// ── Detail panel (sticky) ───────────────────────────────────────────────────────

function RelationRow({ r, onNavigate }: { r: RequirementRef; onNavigate: (id: string) => void }) {
  return (
    <div className="flex items-center gap-1.5 group">
      <button
        onClick={() => onNavigate(r.id)}
        className="flex-1 min-w-0 flex items-center gap-1.5 text-left rounded px-1.5 py-1 hover:bg-slate-50"
      >
        <Badge label={r.issueType} colorClass={issueTypeColor(r.issueType)} />
        {r.externalId && (
          <span className="text-xs font-mono text-slate-400 shrink-0">#{r.externalId}</span>
        )}
        <span className="text-xs text-slate-700 truncate">{r.title}</span>
      </button>
      {r.sourceUrl && (
        <a
          href={r.sourceUrl}
          target="_blank"
          rel="noreferrer"
          title="Open in Azure DevOps"
          className="text-slate-300 hover:text-blue-600 shrink-0"
        >
          <ExternalLink size={11} />
        </a>
      )}
    </div>
  )
}

function RequirementDetail({
  req,
  projectId,
  onClose,
  onNavigate,
}: {
  req: Requirement
  projectId: string
  onClose: () => void
  onNavigate: (id: string) => void
}) {
  const externalHref = req.sourceUrl ?? (req.externalId?.startsWith('http') ? req.externalId : null)
  const { data: relations } = useQuery({
    queryKey: ['requirement-relations', projectId, req.id],
    queryFn: () => api.requirementRelations(projectId, req.id),
  })
  return (
    <div
      className="w-96 shrink-0 bg-white rounded-xl border border-slate-200 shadow-sm flex flex-col
                    sticky top-2 self-start max-h-[calc(100vh-1rem)]"
    >
      <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-100 shrink-0">
        <p className="text-sm font-medium text-slate-700">Detail</p>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
          <X size={16} />
        </button>
      </div>
      <div className="overflow-y-auto p-5 space-y-5 flex-1">
        <div>
          <div className="flex items-center gap-2 flex-wrap mb-2">
            <Badge label={req.issueType} colorClass={issueTypeColor(req.issueType)} />
            <Badge label={req.status} colorClass={statusColor(req.status)} />
            {req.priority && (
              <Badge label={req.priority} colorClass={priorityColor(req.priority)} />
            )}
          </div>
          <h3 className="text-base font-semibold text-slate-900 leading-snug">{req.title}</h3>
          {req.externalId && (
            <div className="flex items-center gap-2 mt-1.5">
              <span className="text-xs font-mono text-slate-500">#{req.externalId}</span>
              {externalHref && (
                <a
                  href={externalHref}
                  target="_blank"
                  rel="noreferrer"
                  title="Open the original work item in Azure DevOps"
                  className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-700 hover:underline"
                >
                  Open in Azure DevOps <ExternalLink size={11} />
                </a>
              )}
            </div>
          )}
        </div>

        {(relations?.parent || (relations?.children?.length ?? 0) > 0) && (
          <div className="space-y-2">
            {relations?.parent && (
              <div>
                <p className="text-xs font-medium text-slate-500 mb-1">Parent</p>
                <RelationRow r={relations.parent} onNavigate={onNavigate} />
              </div>
            )}
            {relations && relations.children.length > 0 && (
              <div>
                <p className="text-xs font-medium text-slate-500 mb-1">
                  Children ({relations.children.length})
                </p>
                <div className="space-y-0.5">
                  {relations.children.map(c => (
                    <RelationRow key={c.id} r={c} onNavigate={onNavigate} />
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {req.description && (
          <div>
            <p className="text-xs font-medium text-slate-500 mb-1">Description</p>
            <RichText className="rich-text">{req.description}</RichText>
          </div>
        )}

        {Array.isArray(req.acceptanceCriteria) && req.acceptanceCriteria.length > 0 && (
          <div>
            <p className="text-xs font-medium text-slate-500 mb-1">Acceptance Criteria</p>
            <ul className="space-y-1">
              {req.acceptanceCriteria.map((ac, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-slate-700">
                  <span className="mt-0.5 shrink-0 w-4 h-4 rounded-full border border-slate-300 flex items-center justify-center text-xs text-slate-400">
                    {i + 1}
                  </span>
                  <span>{typeof ac === 'string' ? ac : JSON.stringify(ac)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {req.changeSummary && (
          <div className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-2.5">
            <p className="text-xs font-medium text-amber-800 mb-0.5">Recent Change</p>
            <p className="text-xs text-amber-700">{req.changeSummary}</p>
          </div>
        )}

        <div className="border-t border-slate-100 pt-3 space-y-1">
          {req.createdDate && (
            <p className="text-xs text-slate-500">Created {fmtDate(req.createdDate)}</p>
          )}
          {req.syncedAt && (
            <p className="text-xs text-slate-400">Synced {relativeTime(req.syncedAt)}</p>
          )}
          <p className="text-xs text-slate-400">Updated {relativeTime(req.updatedAt)}</p>
        </div>
      </div>
    </div>
  )
}

// ── Tree search helpers ───────────────────────────────────────────────────────

interface TreeFilter {
  q: string
  status: string
  issueType: string
}
function filterActive(f: TreeFilter): boolean {
  return !!(f.q || f.status || f.issueType)
}

/** Text-only match (for highlight). */
function nodeMatchesQuery(node: TreeNode, q: string): boolean {
  if (!q) return true
  return node.title.toLowerCase().includes(q) || (node.externalId ?? '').toLowerCase().includes(q)
}
/** Full filter match: search ∧ status ∧ type. */
function nodeMatches(node: TreeNode, f: TreeFilter): boolean {
  if (!nodeMatchesQuery(node, f.q)) return false
  if (f.status && node.status !== f.status) return false
  if (f.issueType && node.issueType !== f.issueType) return false
  return true
}
function computeVisibleIds(nodes: TreeNode[], f: TreeFilter): Set<string> | null {
  if (!filterActive(f)) return null
  const visible = new Set<string>()
  function addSubtree(node: TreeNode) {
    visible.add(node.id)
    node.children.forEach(addSubtree)
  }
  function check(node: TreeNode): boolean {
    const self = nodeMatches(node, f)
    // A matched node reveals its whole subtree (so its children are visible too).
    if (self) addSubtree(node)
    let childHit = false
    node.children.forEach(c => {
      if (check(c)) childHit = true
    })
    if (self || childHit) {
      visible.add(node.id)
      return true
    }
    return false
  }
  nodes.forEach(check)
  return visible
}
function highlightMatch(text: string, q: string): React.ReactNode {
  if (!q) return text
  const idx = text.toLowerCase().indexOf(q)
  if (idx === -1) return text
  return (
    <>
      {text.slice(0, idx)}
      <mark className="bg-yellow-200 text-yellow-900 rounded-sm px-0.5">
        {text.slice(idx, idx + q.length)}
      </mark>
      {text.slice(idx + q.length)}
    </>
  )
}

// ── Tree node (recursive) ─────────────────────────────────────────────────────

function TreeNodeRow({
  node,
  depth,
  selected,
  onSelect,
  expanded,
  onToggle,
  visibleIds,
  searchQuery,
}: {
  node: TreeNode
  depth: number
  selected: Requirement | null
  onSelect: (r: Requirement) => void
  expanded: Set<string>
  onToggle: (id: string) => void
  visibleIds: Set<string> | null
  searchQuery: string
}) {
  if (visibleIds && !visibleIds.has(node.id)) return null
  const hasVisibleChild = node.children.some(c => !visibleIds || visibleIds.has(c.id))
  const isExpanded = visibleIds ? hasVisibleChild : expanded.has(node.id)
  const hasChildren = node.children.length > 0
  const isSelected = selected?.id === node.id
  const isSelfMatch = !!searchQuery && nodeMatchesQuery(node, searchQuery)
  return (
    <>
      <div
        className={cn(
          'flex items-center gap-1 pr-4 py-2 cursor-pointer hover:bg-slate-50 transition-colors',
          isSelected && 'bg-blue-50 hover:bg-blue-50',
          isSelfMatch && !isSelected && 'bg-yellow-50 hover:bg-yellow-50',
        )}
        style={{ paddingLeft: `${depth * 20 + 12}px` }}
        onClick={() => onSelect(node)}
      >
        <button
          className={cn(
            'shrink-0 w-5 h-5 flex items-center justify-center rounded text-slate-400',
            hasChildren ? 'hover:text-slate-700 hover:bg-slate-200' : 'invisible',
          )}
          onClick={e => {
            e.stopPropagation()
            if (!visibleIds) onToggle(node.id)
          }}
        >
          {isExpanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </button>
        {depth > 0 && <span className="shrink-0 w-1.5 h-1.5 rounded-full bg-slate-300 mr-1" />}
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <Badge label={node.issueType} colorClass={issueTypeColor(node.issueType)} />
          {node.externalId && (
            <span className="text-xs font-mono text-slate-400 shrink-0">
              {highlightMatch(node.externalId, searchQuery)}
            </span>
          )}
          <span
            className={cn(
              'text-sm truncate',
              isSelected ? 'font-semibold text-blue-900' : 'text-slate-800',
            )}
          >
            {highlightMatch(node.title, searchQuery)}
          </span>
          {node.changeSummary && (
            <span className="text-xs text-amber-500 shrink-0" title={node.changeSummary}>
              ⚡
            </span>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0 ml-2">
          <span className="text-xs text-slate-400 hidden sm:inline" title="Created date">
            {fmtDate(node.createdDate)}
          </span>
          {hasChildren && <span className="text-xs text-slate-400">{node.children.length}</span>}
          <Badge label={node.status} colorClass={statusColor(node.status)} />
        </div>
      </div>
      {isExpanded && hasChildren && (
        <div className="relative">
          <div
            className="absolute top-0 bottom-0 border-l border-slate-200"
            style={{ left: `${depth * 20 + 20}px` }}
          />
          {node.children.map(child => (
            <TreeNodeRow
              key={child.id}
              node={child}
              depth={depth + 1}
              selected={selected}
              onSelect={onSelect}
              expanded={expanded}
              onToggle={onToggle}
              visibleIds={visibleIds}
              searchQuery={searchQuery}
            />
          ))}
        </div>
      )}
    </>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

type ViewMode = 'list' | 'tree'

export default function RequirementsPage() {
  const { projectId, base, project } = useProject()
  const { filter } = useProjectFilter() // project-wide Area / Team / Iteration scope
  const navigate = useNavigate()

  const [viewMode, setViewMode] = useState<ViewMode>('tree')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('') // debounced
  const [status, setStatus] = useState('')
  const [issueType, setIssueType] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(50)
  const [selected, setSelected] = useState<Requirement | null>(null)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  // Debounce the list search; reset to first page when any filter changes.
  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [searchInput])
  useEffect(() => {
    setPage(0)
  }, [status, issueType, size])

  const { data: stats } = useQuery({
    queryKey: ['req-stats', projectId],
    queryFn: () => api.requirementStats(projectId!),
    enabled: !!projectId,
  })

  // List = server-paginated. Tree = full fetch (hierarchy needs all), only when active.
  const scope = {
    area: filter.area || undefined,
    team: filter.teamId || undefined,
    iteration: filter.iteration || undefined,
  }
  const listQ = useQuery({
    queryKey: [
      'requirements-page',
      projectId,
      page,
      size,
      status,
      issueType,
      search,
      filter.area,
      filter.teamId,
      filter.iteration,
    ],
    queryFn: () =>
      api.requirementsPage(projectId!, {
        page,
        size,
        status: status || undefined,
        issueType: issueType || undefined,
        search: search || undefined,
        ...scope,
      }),
    enabled: !!projectId && viewMode === 'list',
    placeholderData: keepPreviousData,
  })
  const treeQ = useQuery({
    queryKey: ['requirements-all', projectId, filter.area, filter.teamId, filter.iteration],
    queryFn: () => api.requirements(projectId!, scope),
    enabled: !!projectId && viewMode === 'tree',
  })

  useEffect(() => {
    if (viewMode === 'tree') setExpanded(new Set())
  }, [viewMode, treeQ.data]) // start collapsed

  function toggleNode(id: string) {
    setExpanded(prev => {
      const n = new Set(prev)
      n.has(id) ? n.delete(id) : n.add(id)
      return n
    })
  }

  // Open a related requirement (parent/child) in the detail panel — works in any view
  // since the item may not be on the current page; fetch the full record by id.
  async function navigateToRequirement(id: string) {
    try {
      setSelected(await api.requirementDetail(projectId!, id))
    } catch {
      /* ignore — item may have been removed */
    }
  }

  const issueTypeOptions = stats ? Object.keys(stats.byIssueType).sort() : []

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
            <button onClick={() => navigate('/')} className="hover:text-blue-600">
              Overview
            </button>
            <ChevronRight size={14} />
            <button onClick={() => navigate(base)} className="hover:text-blue-600">
              {project.name}
            </button>
            <ChevronRight size={14} />
            <span className="text-slate-700">Requirements</span>
          </div>
          <div className="flex items-center gap-3">
            <FileText size={20} className="text-slate-400" />
            <h1 className="text-2xl font-bold text-slate-900">Requirements</h1>
            {stats && (
              <span className="text-sm text-slate-500 font-normal">{stats.total} total</span>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border border-slate-200 overflow-hidden">
            {(['list', 'tree'] as ViewMode[]).map(mode => (
              <button
                key={mode}
                onClick={() => {
                  setViewMode(mode)
                  setSelected(null)
                }}
                className={cn(
                  'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors',
                  viewMode === mode ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-50',
                )}
              >
                {mode === 'list' ? <List size={13} /> : <Network size={13} />}
                {mode === 'list' ? 'List' : 'Tree'}
              </button>
            ))}
          </div>
          <button
            onClick={() => navigate(`${base}/pr-analyses`)}
            className="flex items-center gap-1.5 px-3 py-1.5 border border-slate-200 rounded-lg text-sm text-slate-600 hover:bg-slate-50 transition-colors"
          >
            <GitBranch size={14} /> PR Analyses
          </button>
        </div>
      </div>

      {/* Filters (shared by List and Tree) */}
      <div className="flex gap-2 flex-wrap items-center">
        <div className="relative flex-1 min-w-[16rem]">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search by title or key…"
            className="w-full pl-8 pr-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {searchInput && (
            <button
              onClick={() => setSearchInput('')}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X size={14} />
            </button>
          )}
        </div>
        <select
          value={status}
          onChange={e => setStatus(e.target.value)}
          className="text-sm border border-slate-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All statuses</option>
          {stats &&
            Object.keys(stats.byStatus)
              .sort()
              .map(s => (
                <option key={s} value={s}>
                  {s} ({stats.byStatus[s]})
                </option>
              ))}
        </select>
        <select
          value={issueType}
          onChange={e => setIssueType(e.target.value)}
          className="text-sm border border-slate-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All types</option>
          {issueTypeOptions.map(t => (
            <option key={t} value={t}>
              {t} ({stats!.byIssueType[t]})
            </option>
          ))}
        </select>
        {(status || issueType || searchInput) && (
          <button
            onClick={() => {
              setStatus('')
              setIssueType('')
              setSearchInput('')
            }}
            className="text-sm text-slate-500 hover:text-slate-800 px-2"
          >
            Clear
          </button>
        )}
      </div>

      {/* Split pane: list/tree + sticky detail */}
      <div className="flex gap-4 items-start">
        <div className="flex-1 min-w-0 bg-white rounded-xl border border-slate-200 shadow-sm">
          {viewMode === 'list' ? listView() : treeView()}
        </div>
        {selected && (
          <RequirementDetail
            req={selected}
            projectId={projectId!}
            onClose={() => setSelected(null)}
            onNavigate={navigateToRequirement}
          />
        )}
      </div>
    </div>
  )

  // ── List view (server-paginated) ──────────────────────────────────────────────
  function listView() {
    if (listQ.isLoading) return <LoadingSpinner message="Loading requirements…" />
    if (listQ.error)
      return (
        <ErrorMessage message="Failed to load requirements." onRetry={() => void listQ.refetch()} />
      )
    const data = listQ.data
    const items = data?.content ?? []
    const totalPages = data?.totalPages ?? 0
    const total = data?.totalElements ?? 0

    if (items.length === 0) {
      return (
        <p className="px-5 py-12 text-sm text-slate-500 text-center">
          No requirements match. {!stats?.total && 'Sync an integration to populate this list.'}
        </p>
      )
    }
    return (
      <div className="flex flex-col">
        <div className="divide-y divide-slate-50">
          {items.map(r => (
            <button
              key={r.id}
              onClick={() => setSelected(selected?.id === r.id ? null : r)}
              className={cn(
                'w-full text-left px-5 py-3.5 hover:bg-slate-50 transition-colors',
                selected?.id === r.id && 'bg-blue-50 hover:bg-blue-50',
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                    <Badge label={r.issueType} colorClass={issueTypeColor(r.issueType)} />
                    {r.externalId && (
                      <span className="text-xs font-mono text-slate-400">#{r.externalId}</span>
                    )}
                    {r.sourceUrl && (
                      <span
                        role="link"
                        tabIndex={0}
                        title="Open the original work item in Azure DevOps (new tab)"
                        onClick={e => {
                          e.stopPropagation()
                          window.open(r.sourceUrl!, '_blank', 'noopener,noreferrer')
                        }}
                        onKeyDown={e => {
                          if (e.key === 'Enter') {
                            e.stopPropagation()
                            window.open(r.sourceUrl!, '_blank', 'noopener,noreferrer')
                          }
                        }}
                        className="text-slate-300 hover:text-blue-600 cursor-pointer"
                      >
                        <ExternalLink size={11} />
                      </span>
                    )}
                  </div>
                  <p className="text-sm font-medium text-slate-900 truncate">{r.title}</p>
                  {r.changeSummary && (
                    <p className="text-xs text-amber-600 mt-0.5 truncate">⚡ {r.changeSummary}</p>
                  )}
                </div>
                <div className="flex flex-col items-end gap-1 shrink-0">
                  <Badge label={r.status} colorClass={statusColor(r.status)} />
                  {r.priority && (
                    <Badge label={r.priority} colorClass={priorityColor(r.priority)} />
                  )}
                  <p className="text-xs text-slate-500" title="Created date">
                    Created {fmtDate(r.createdDate)}
                  </p>
                  <p className="text-xs text-slate-400">Updated {relativeTime(r.updatedAt)}</p>
                </div>
              </div>
            </button>
          ))}
        </div>
        {/* Pagination footer */}
        <div className="flex items-center justify-between gap-3 px-5 py-3 border-t border-slate-100 text-sm">
          <div className="flex items-center gap-2 text-slate-500">
            <span>
              Page {page + 1} of {Math.max(totalPages, 1)} · {total} item{total !== 1 ? 's' : ''}
            </span>
            <select
              value={size}
              onChange={e => setSize(Number(e.target.value))}
              className="text-xs border border-slate-200 rounded px-1.5 py-1 bg-white"
            >
              {PAGE_SIZE_OPTIONS.map(n => (
                <option key={n} value={n}>
                  {n}/page
                </option>
              ))}
            </select>
            {listQ.isFetching && <span className="text-xs text-slate-400">updating…</span>}
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page <= 0}
              className="flex items-center gap-1 px-2.5 py-1 border border-slate-200 rounded-lg disabled:opacity-40 hover:bg-slate-50"
            >
              <ChevronLeft size={14} /> Prev
            </button>
            <button
              onClick={() => setPage(p => (p + 1 < totalPages ? p + 1 : p))}
              disabled={page + 1 >= totalPages}
              className="flex items-center gap-1 px-2.5 py-1 border border-slate-200 rounded-lg disabled:opacity-40 hover:bg-slate-50"
            >
              Next <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ── Tree view (full hierarchy, bounded render) ────────────────────────────────
  function treeView() {
    if (treeQ.isLoading) return <LoadingSpinner message="Loading hierarchy…" />
    if (treeQ.error)
      return (
        <ErrorMessage message="Failed to load requirements." onRetry={() => void treeQ.refetch()} />
      )
    const all = treeQ.data ?? []
    const treeData = buildTree(all)
    const f = { q: search.trim().toLowerCase(), status, issueType }
    const visibleIds = computeVisibleIds(treeData, f)
    const roots = visibleIds ? treeData : treeData.slice(0, MAX_TREE_ROOTS)
    const capped = !visibleIds && treeData.length > MAX_TREE_ROOTS

    if (all.length === 0) {
      return <p className="px-5 py-12 text-sm text-slate-500 text-center">No requirements found.</p>
    }
    if (visibleIds && visibleIds.size === 0) {
      return (
        <p className="px-5 py-8 text-sm text-slate-500 text-center">
          No requirements match the current filters.
        </p>
      )
    }
    return (
      <div className="py-2">
        {roots.map(root => (
          <TreeNodeRow
            key={root.id}
            node={root}
            depth={0}
            selected={selected}
            onSelect={r => setSelected(selected?.id === r.id ? null : r)}
            expanded={expanded}
            onToggle={toggleNode}
            visibleIds={visibleIds}
            searchQuery={f.q}
          />
        ))}
        {capped && (
          <p className="px-5 py-3 text-xs text-amber-600 border-t border-slate-100">
            Showing first {MAX_TREE_ROOTS} of {treeData.length} top-level items — search to narrow,
            or use the List view.
          </p>
        )}
      </div>
    )
  }
}
