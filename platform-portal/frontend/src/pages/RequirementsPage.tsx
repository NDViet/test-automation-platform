import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime, cn } from '@/lib/utils'
import Badge from '@/components/Badge'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import {
  ChevronRight, ChevronDown, Search, X,
  ExternalLink, FileText, GitBranch, List, Network,
} from 'lucide-react'
import type { Requirement } from '@/lib/types'

// ── Colour helpers ────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<string, string> = {
  OPEN:        'text-blue-700 bg-blue-100',
  IN_PROGRESS: 'text-yellow-700 bg-yellow-100',
  DONE:        'text-green-700 bg-green-100',
  CLOSED:      'text-slate-600 bg-slate-100',
}
const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: 'text-red-700 bg-red-100',
  HIGH:     'text-orange-700 bg-orange-100',
  MEDIUM:   'text-yellow-700 bg-yellow-100',
  LOW:      'text-slate-600 bg-slate-100',
}
const ISSUE_TYPE_COLORS: Record<string, string> = {
  EPIC:    'text-purple-700 bg-purple-100',
  STORY:   'text-blue-700 bg-blue-100',
  BUG:     'text-red-700 bg-red-100',
  TASK:    'text-slate-600 bg-slate-100',
  SUBTASK: 'text-teal-700 bg-teal-100',
  DEFECT:  'text-red-700 bg-red-100',
  SPIKE:   'text-orange-700 bg-orange-100',
}

function statusColor(s: string)        { return STATUS_COLORS[s?.toUpperCase()]    ?? 'text-slate-600 bg-slate-100' }
function priorityColor(p: string|null) { return PRIORITY_COLORS[(p??'').toUpperCase()] ?? 'text-slate-600 bg-slate-100' }
function issueTypeColor(t: string)     { return ISSUE_TYPE_COLORS[t?.toUpperCase()] ?? 'text-slate-600 bg-slate-100' }

// ── Tree data structure ───────────────────────────────────────────────────────

interface TreeNode extends Requirement {
  children: TreeNode[]
}

function buildTree(items: Requirement[]): TreeNode[] {
  const map = new Map<string, TreeNode>()
  items.forEach(r => map.set(r.id, { ...r, children: [] }))

  const roots: TreeNode[] = []
  map.forEach(node => {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })

  // Sort each level: EPIC first, then by issueType priority, then by externalId
  const typeOrder: Record<string, number> = { EPIC: 0, STORY: 1, TASK: 2, BUG: 3, DEFECT: 3, SUBTASK: 4, SPIKE: 5 }
  function sortNodes(nodes: TreeNode[]) {
    nodes.sort((a, b) => {
      const ta = typeOrder[a.issueType?.toUpperCase()] ?? 9
      const tb = typeOrder[b.issueType?.toUpperCase()] ?? 9
      if (ta !== tb) return ta - tb
      return (a.externalId ?? '').localeCompare(b.externalId ?? '')
    })
    nodes.forEach(n => sortNodes(n.children))
  }
  sortNodes(roots)
  return roots
}

// ── Detail panel ──────────────────────────────────────────────────────────────

function RequirementDetail({ req, onClose }: { req: Requirement; onClose: () => void }) {
  const externalHref = req.externalId?.startsWith('http') ? req.externalId : null

  return (
    <div className="w-96 shrink-0 bg-white rounded-xl border border-slate-200 shadow-sm flex flex-col">
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
            <Badge label={req.status}    colorClass={statusColor(req.status)} />
            {req.priority && <Badge label={req.priority} colorClass={priorityColor(req.priority)} />}
          </div>
          <h3 className="text-base font-semibold text-slate-900 leading-snug">{req.title}</h3>
          {req.externalId && (
            <div className="flex items-center gap-1 mt-1">
              <span className="text-xs font-mono text-slate-500">{req.externalId}</span>
              {externalHref && (
                <a href={externalHref} target="_blank" rel="noreferrer" className="text-slate-400 hover:text-blue-600">
                  <ExternalLink size={11} />
                </a>
              )}
            </div>
          )}
        </div>

        {req.description && (
          <div>
            <p className="text-xs font-medium text-slate-500 mb-1">Description</p>
            <p className="text-sm text-slate-700 whitespace-pre-wrap">{req.description}</p>
          </div>
        )}

        {Array.isArray(req.acceptanceCriteria) && req.acceptanceCriteria.length > 0 && (
          <div>
            <p className="text-xs font-medium text-slate-500 mb-1">Acceptance Criteria</p>
            <ul className="space-y-1">
              {req.acceptanceCriteria.map((ac, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-slate-700">
                  <span className="mt-0.5 shrink-0 w-4 h-4 rounded-full border border-slate-300 flex items-center justify-center text-xs text-slate-400">{i + 1}</span>
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
          {req.syncedAt && <p className="text-xs text-slate-400">Synced {relativeTime(req.syncedAt)}</p>}
          <p className="text-xs text-slate-400">Updated {relativeTime(req.updatedAt)}</p>
        </div>
      </div>
    </div>
  )
}

// ── Tree search helpers ───────────────────────────────────────────────────────

function nodeMatchesQuery(node: TreeNode, q: string): boolean {
  if (!q) return true
  return (
    node.title.toLowerCase().includes(q) ||
    (node.externalId ?? '').toLowerCase().includes(q)
  )
}

/** Returns the set of node IDs that should be visible given a search query.
 *  A node is visible if it matches OR any of its descendants match. */
function computeVisibleIds(nodes: TreeNode[], q: string): Set<string> | null {
  if (!q) return null
  const visible = new Set<string>()
  function check(node: TreeNode): boolean {
    const self = nodeMatchesQuery(node, q)
    const childHit = node.children.some(c => check(c))
    if (self || childHit) { visible.add(node.id); return true }
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
      <mark className="bg-yellow-200 text-yellow-900 rounded-sm px-0.5">{text.slice(idx, idx + q.length)}</mark>
      {text.slice(idx + q.length)}
    </>
  )
}

// ── Tree node (recursive) ─────────────────────────────────────────────────────

function TreeNodeRow({
  node, depth, selected, onSelect, expanded, onToggle, visibleIds, searchQuery,
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
  // When a search is active and this node is not in the visible set, skip it
  if (visibleIds && !visibleIds.has(node.id)) return null

  // During search, force-expand if any visible child exists
  const hasVisibleChild = node.children.some(c => !visibleIds || visibleIds.has(c.id))
  const isExpanded  = visibleIds ? hasVisibleChild : expanded.has(node.id)
  const hasChildren = node.children.length > 0
  const isSelected  = selected?.id === node.id
  const isSelfMatch = !!searchQuery && nodeMatchesQuery(node, searchQuery)

  return (
    <>
      <div
        className={cn(
          'flex items-center gap-1 pr-4 py-2 cursor-pointer hover:bg-slate-50 transition-colors group',
          isSelected && 'bg-blue-50 hover:bg-blue-50',
          isSelfMatch && !isSelected && 'bg-yellow-50 hover:bg-yellow-50',
        )}
        style={{ paddingLeft: `${depth * 20 + 12}px` }}
        onClick={() => onSelect(node)}
      >
        {/* Expand / collapse toggle */}
        <button
          className={cn(
            'shrink-0 w-5 h-5 flex items-center justify-center rounded text-slate-400',
            hasChildren ? 'hover:text-slate-700 hover:bg-slate-200' : 'invisible',
          )}
          onClick={e => { e.stopPropagation(); if (!visibleIds) onToggle(node.id) }}
        >
          {isExpanded
            ? <ChevronDown size={13} />
            : <ChevronRight size={13} />}
        </button>

        {/* Connector line dot */}
        {depth > 0 && (
          <span className="shrink-0 w-1.5 h-1.5 rounded-full bg-slate-300 mr-1" />
        )}

        {/* Badges + title */}
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <Badge label={node.issueType} colorClass={issueTypeColor(node.issueType)} />
          {node.externalId && (
            <span className="text-xs font-mono text-slate-400 shrink-0">
              {highlightMatch(node.externalId, searchQuery)}
            </span>
          )}
          <span className={cn(
            'text-sm truncate',
            isSelected ? 'font-semibold text-blue-900' : 'text-slate-800',
          )}>
            {highlightMatch(node.title, searchQuery)}
          </span>
          {node.changeSummary && (
            <span className="text-xs text-amber-500 shrink-0" title={node.changeSummary}>⚡</span>
          )}
        </div>

        {/* Right: status + count */}
        <div className="flex items-center gap-2 shrink-0 ml-2">
          {hasChildren && (
            <span className="text-xs text-slate-400">{node.children.length}</span>
          )}
          <Badge label={node.status} colorClass={statusColor(node.status)} />
        </div>
      </div>

      {/* Vertical guide line for children */}
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
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()

  const [viewMode,    setViewMode]    = useState<ViewMode>('tree')
  const [search,      setSearch]      = useState('')
  const [treeSearch,  setTreeSearch]  = useState('')
  const [status,      setStatus]      = useState('')
  const [issueType,   setIssueType]   = useState('')
  const [selected,    setSelected]    = useState<Requirement | null>(null)
  // Tree: set of expanded node IDs (epics auto-expanded)
  const [expanded,    setExpanded]    = useState<Set<string>>(new Set())

  const { data: stats } = useQuery({
    queryKey: ['req-stats', projectId],
    queryFn:  () => api.requirementStats(projectId!),
    enabled:  !!projectId,
  })

  // List mode: use filters. Tree mode: always fetch all (filters would create orphan gaps).
  const { data: reqs, isLoading, error } = useQuery({
    queryKey: ['requirements', projectId, viewMode === 'tree' ? '__all__' : status, viewMode === 'tree' ? '' : issueType, viewMode === 'tree' ? '' : search],
    queryFn:  () => viewMode === 'tree'
      ? api.requirements(projectId!)
      : api.requirements(projectId!, {
          status:    status    || undefined,
          issueType: issueType || undefined,
          search:    search    || undefined,
        }),
    enabled: !!projectId,
  })

  // Auto-expand epics whenever we have fresh data in tree mode
  useEffect(() => {
    if (viewMode === 'tree' && reqs) {
      const epicIds = new Set(
        reqs.filter((r: { issueType?: string }) => r.issueType?.toUpperCase() === 'EPIC').map((r: { id: string }) => r.id)
      )
      setExpanded(epicIds)
    }
  }, [viewMode, reqs])

  function toggleNode(id: string) {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  function expandAll(nodes: TreeNode[]) {
    const ids = new Set<string>()
    function collect(ns: TreeNode[]) { ns.forEach(n => { if (n.children.length) { ids.add(n.id); collect(n.children) } }) }
    collect(nodes)
    setExpanded(ids)
  }

  function collapseAll() { setExpanded(new Set()) }

  if (isLoading) return <LoadingSpinner message="Loading requirements…" />
  if (error)     return <ErrorMessage  message="Failed to load requirements." />

  const items      = reqs ?? []
  const treeData   = viewMode === 'tree' ? buildTree(items) : []
  const treeQ      = treeSearch.trim().toLowerCase()
  const visibleIds = viewMode === 'tree' ? computeVisibleIds(treeData, treeQ) : null
  const matchCount = visibleIds ? visibleIds.size : null

  return (
    <div className="flex flex-col h-full space-y-4">

      {/* Header ──────────────────────────────────────────────────────────────── */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
            <button onClick={() => navigate('/')} className="hover:text-blue-600">Overview</button>
            <ChevronRight size={14} />
            <button onClick={() => navigate(`/projects/${projectId}`)} className="hover:text-blue-600">{projectId}</button>
            <ChevronRight size={14} />
            <span className="text-slate-700">Requirements</span>
          </div>
          <div className="flex items-center gap-3">
            <FileText size={20} className="text-slate-400" />
            <h1 className="text-2xl font-bold text-slate-900">Requirements</h1>
            {stats && <span className="text-sm text-slate-500 font-normal">{stats.total} total</span>}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* View mode toggle */}
          <div className="flex rounded-lg border border-slate-200 overflow-hidden">
            {(['list', 'tree'] as ViewMode[]).map(mode => (
              <button
                key={mode}
                onClick={() => { setViewMode(mode); setSelected(null) }}
                className={cn(
                  'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors',
                  viewMode === mode
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-600 hover:bg-slate-50',
                )}
              >
                {mode === 'list' ? <List size={13} /> : <Network size={13} />}
                {mode === 'list' ? 'List' : 'Tree'}
              </button>
            ))}
          </div>

          <button
            onClick={() => navigate(`/projects/${projectId}/pr-analyses`)}
            className="flex items-center gap-1.5 px-3 py-1.5 border border-slate-200 rounded-lg text-sm text-slate-600 hover:bg-slate-50 transition-colors"
          >
            <GitBranch size={14} /> PR Analyses
          </button>
        </div>
      </div>

      {/* Status pills (list mode only) ──────────────────────────────────────── */}
      {viewMode === 'list' && stats && Object.keys(stats.byStatus).length > 0 && (
        <div className="flex gap-3 flex-wrap">
          {Object.entries(stats.byStatus).map(([s, n]) => (
            <button
              key={s}
              onClick={() => setStatus(status === s ? '' : s)}
              className={cn(
                'px-3 py-1 rounded-full text-xs font-medium border transition-colors',
                status === s
                  ? 'border-blue-400 bg-blue-50 text-blue-700'
                  : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50',
              )}
            >
              {s} · {n}
            </button>
          ))}
        </div>
      )}

      {/* Filters (list mode) / tree controls (tree mode) ─────────────────────── */}
      {viewMode === 'list' ? (
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search by title or key…"
              className="w-full pl-8 pr-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {search && (
              <button onClick={() => setSearch('')} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                <X size={14} />
              </button>
            )}
          </div>
          <select
            value={issueType}
            onChange={e => setIssueType(e.target.value)}
            className="text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
          >
            <option value="">All types</option>
            <option value="EPIC">Epic</option>
            <option value="STORY">Story</option>
            <option value="BUG">Bug</option>
            <option value="TASK">Task</option>
            <option value="SUBTASK">Subtask</option>
          </select>
          {(status || issueType || search) && (
            <button onClick={() => { setStatus(''); setIssueType(''); setSearch('') }}
              className="text-sm text-slate-500 hover:text-slate-800 px-2">
              Clear
            </button>
          )}
        </div>
      ) : (
        <div className="flex items-center gap-3">
          {/* Tree search */}
          <div className="relative flex-1 max-w-sm">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={treeSearch}
              onChange={e => setTreeSearch(e.target.value)}
              placeholder="Search by ID or title…"
              className="w-full pl-8 pr-8 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {treeSearch && (
              <button onClick={() => setTreeSearch('')} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                <X size={14} />
              </button>
            )}
          </div>
          {/* Match count */}
          {treeQ && (
            <span className="text-xs text-slate-500 shrink-0">
              {matchCount === 0 ? 'No matches' : `${matchCount} match${matchCount === 1 ? '' : 'es'}`}
            </span>
          )}
          {!treeQ && (
            <span className="text-xs text-slate-400 shrink-0">
              {items.length} requirements · {treeData.length} root{treeData.length !== 1 ? 's' : ''}
            </span>
          )}
          {!treeQ && (
            <>
              <span className="text-slate-300 text-xs">|</span>
              <button onClick={() => expandAll(treeData)} className="text-xs text-slate-500 hover:text-blue-600">Expand all</button>
              <button onClick={collapseAll}               className="text-xs text-slate-500 hover:text-blue-600">Collapse all</button>
            </>
          )}
        </div>
      )}

      {/* Split pane ──────────────────────────────────────────────────────────── */}
      <div className="flex gap-4 flex-1 min-h-0 overflow-hidden">

        {/* Main panel */}
        <div className={cn(
          'flex-1 bg-white rounded-xl border border-slate-200 shadow-sm overflow-y-auto min-w-0',
        )}>
          {items.length === 0 ? (
            <p className="px-5 py-12 text-sm text-slate-500 text-center">
              No requirements found.{' '}
              {!stats?.total && 'Sync a Jira, Linear, or GitHub Issues integration to populate this list.'}
            </p>
          ) : viewMode === 'list' ? (

            /* ── Flat list ── */
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
                        {r.externalId && <span className="text-xs font-mono text-slate-400">{r.externalId}</span>}
                      </div>
                      <p className="text-sm font-medium text-slate-900 truncate">{r.title}</p>
                      {r.changeSummary && (
                        <p className="text-xs text-amber-600 mt-0.5 truncate">⚡ {r.changeSummary}</p>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-1 shrink-0">
                      <Badge label={r.status} colorClass={statusColor(r.status)} />
                      {r.priority && <Badge label={r.priority} colorClass={priorityColor(r.priority)} />}
                      <p className="text-xs text-slate-400">{relativeTime(r.updatedAt)}</p>
                    </div>
                  </div>
                </button>
              ))}
            </div>

          ) : (

            /* ── Tree view ── */
            <div className="py-2">
              {treeData.length === 0 ? (
                <p className="px-5 py-8 text-sm text-slate-500 text-center">
                  No hierarchy found. Requirements may be missing parent links — trigger a Jira sync to refresh.
                </p>
              ) : matchCount === 0 ? (
                <p className="px-5 py-8 text-sm text-slate-500 text-center">
                  No requirements match <strong>{treeSearch}</strong>.
                </p>
              ) : treeData.map(root => (
                <TreeNodeRow
                  key={root.id}
                  node={root}
                  depth={0}
                  selected={selected}
                  onSelect={r => setSelected(selected?.id === r.id ? null : r)}
                  expanded={expanded}
                  onToggle={toggleNode}
                  visibleIds={visibleIds}
                  searchQuery={treeQ}
                />
              ))}
            </div>
          )}
        </div>

        {/* Detail panel */}
        {selected && (
          <RequirementDetail req={selected} onClose={() => setSelected(null)} />
        )}
      </div>
    </div>
  )
}
