import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useProjectId } from '@/components/layout/ProjectLayout'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import MappingRulesEditor from '@/components/MappingRulesEditor'
import {
  Boxes,
  Bug,
  FileText,
  Ban,
  Copy,
  Check,
  Wand2,
  SlidersHorizontal,
  ChevronDown,
  ChevronRight,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
} from 'lucide-react'
import { relativeTime } from '@/lib/utils'
import type { AdoTypeSummary, SchemaDriftReport } from '@/lib/types'

// ── minimal object → YAML (display/export only) ─────────────────────────────────
function toYaml(value: unknown, indent = 0): string {
  const pad = '  '.repeat(indent)
  if (value === null || value === undefined) return 'null'
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]'
    return value
      .map(v => {
        const inner = toYaml(v, indent + 1)
        return typeof v === 'object' && v !== null ? `${pad}-\n${inner}` : `${pad}- ${inner}`
      })
      .join('\n')
  }
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
    if (entries.length === 0) return '{}'
    return entries
      .map(([k, v]) => {
        if (typeof v === 'object' && v !== null && Object.keys(v).length > 0) {
          return `${pad}${k}:\n${toYaml(v, indent + 1)}`
        }
        return `${pad}${k}: ${toYaml(v, indent + 1).trimStart()}`
      })
      .join('\n')
  }
  if (typeof value === 'string') {
    return /[:#\-?{}[\],&*!|>'"%@`]|^\s|\s$|^\d|^(true|false|null|yes|no)$/i.test(value)
      ? JSON.stringify(value)
      : value
  }
  return String(value)
}

function laneBadge(lane: string) {
  if (lane === 'DEFECT') return { cls: 'text-red-700 bg-red-100', icon: <Bug size={12} /> }
  if (lane === 'REQUIREMENT')
    return { cls: 'text-blue-700 bg-blue-100', icon: <FileText size={12} /> }
  return { cls: 'text-slate-500 bg-slate-100', icon: <Ban size={12} /> }
}
function catColor(cat: string) {
  switch (cat) {
    case 'Proposed':
      return 'text-slate-600 bg-slate-100'
    case 'InProgress':
      return 'text-blue-700 bg-blue-100'
    case 'Completed':
      return 'text-green-700 bg-green-100'
    case 'Removed':
      return 'text-red-700 bg-red-100'
    default:
      return 'text-slate-500 bg-slate-50'
  }
}

export default function AdoMappingPage() {
  const projectId = useProjectId()
  const qc = useQueryClient()
  const [adoProject, setAdoProject] = useState('')
  const [selectedType, setSelectedType] = useState('')
  const [fmt, setFmt] = useState<'yaml' | 'json'>('yaml')
  const [copied, setCopied] = useState(false)
  const [showRules, setShowRules] = useState(false)

  const projectsQ = useQuery({
    queryKey: ['adoProjects', projectId],
    queryFn: () => api.adoProjects(projectId),
  })
  const effProject = adoProject || projectsQ.data?.[0]?.name || ''

  const typesQ = useQuery({
    queryKey: ['adoTypes', projectId, effProject],
    queryFn: () => api.adoTypes(projectId, effProject),
    enabled: !!effProject,
  })
  const schemaQ = useQuery({
    queryKey: ['adoSchema', projectId, effProject, selectedType],
    queryFn: () => api.adoTypeSchema(projectId, effProject, selectedType),
    enabled: !!effProject && !!selectedType,
  })
  const driftQ = useQuery({
    queryKey: ['adoDrift', projectId, effProject, selectedType],
    queryFn: () => api.schemaDrift(projectId, effProject, selectedType),
    enabled: !!effProject && !!selectedType,
  })
  const baselineMutation = useMutation({
    mutationFn: () => api.captureSchemaBaseline(projectId, effProject, selectedType),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ['adoDrift', projectId, effProject, selectedType] }),
  })

  const suggested = schemaQ.data?.suggestedProfile
  const profileText = useMemo(
    () =>
      suggested ? (fmt === 'yaml' ? toYaml(suggested) : JSON.stringify(suggested, null, 2)) : '',
    [suggested, fmt],
  )

  const copy = async () => {
    await navigator.clipboard.writeText(profileText)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  const grouped = (typesQ.data ?? []).reduce<Record<string, AdoTypeSummary[]>>((acc, t) => {
    ;(acc[t.suggestedLane] ??= []).push(t)
    return acc
  }, {})
  const laneOrder = ['REQUIREMENT', 'DEFECT', 'IGNORE']

  if (projectsQ.isLoading) return <LoadingSpinner message="Connecting to Azure DevOps…" />
  if (projectsQ.error)
    return (
      <ErrorMessage
        message="Could not reach Azure DevOps. Ensure an AZURE_DEVOPS_BOARDS credential is configured for this project (Settings → Integrations)."
        onRetry={() => void projectsQ.refetch()}
      />
    )

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
          <Boxes size={22} /> Mapping Discovery
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Explore the Azure DevOps work-item structure and get a suggested mapping profile to start
          from.
        </p>
      </div>

      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-slate-700">ADO project:</label>
        <select
          value={effProject}
          onChange={e => {
            setAdoProject(e.target.value)
            setSelectedType('')
          }}
          className="text-sm border border-slate-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {(projectsQ.data ?? []).map(p => (
            <option key={p.id} value={p.name}>
              {p.name}
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-3 gap-5">
        {/* Types */}
        <div className="col-span-1 space-y-3">
          {typesQ.isLoading && <LoadingSpinner message="Loading work-item types…" />}
          {laneOrder
            .filter(l => grouped[l]?.length)
            .map(lane => (
              <div key={lane} className="bg-white rounded-xl border border-slate-200 shadow-sm">
                <div className="px-4 py-2 border-b border-slate-100 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  {lane === 'IGNORE' ? 'Not tracked' : lane}
                </div>
                <div className="divide-y divide-slate-50">
                  {grouped[lane].map(t => {
                    const b = laneBadge(t.suggestedLane)
                    return (
                      <button
                        key={t.name}
                        onClick={() => setSelectedType(t.name)}
                        className={`w-full text-left px-4 py-2 flex items-center gap-2 text-sm hover:bg-slate-50 ${selectedType === t.name ? 'bg-blue-50' : ''}`}
                      >
                        <span
                          className={`inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded ${b.cls}`}
                        >
                          {b.icon}
                        </span>
                        <span className="flex-1 truncate text-slate-800">{t.name}</span>
                        {t.custom && (
                          <span className="text-[10px] px-1 py-0.5 rounded bg-amber-100 text-amber-700">
                            custom
                          </span>
                        )}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
        </div>

        {/* Schema + suggestion */}
        <div className="col-span-2 space-y-4">
          {!selectedType && (
            <div className="text-sm text-slate-400 py-10 text-center bg-white rounded-xl border border-slate-200">
              Select a work-item type to view its fields, states, and a suggested mapping.
            </div>
          )}
          {selectedType && schemaQ.isLoading && (
            <LoadingSpinner message={`Inspecting ${selectedType}…`} />
          )}
          {selectedType && schemaQ.data && (
            <>
              {/* Schema drift vs baseline */}
              {driftQ.data && (
                <DriftBanner
                  report={driftQ.data}
                  onUpdateBaseline={() => baselineMutation.mutate()}
                  updating={baselineMutation.isPending}
                />
              )}

              {/* Fields */}
              <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
                <div className="px-4 py-2 border-b border-slate-100 text-sm font-semibold text-slate-900">
                  {schemaQ.data.workItemType} — fields ({schemaQ.data.fields.length})
                </div>
                <div className="max-h-60 overflow-y-auto divide-y divide-slate-50">
                  {schemaQ.data.fields
                    .filter(
                      f =>
                        f.custom ||
                        [
                          'System.Title',
                          'System.Description',
                          'System.Parent',
                          'Microsoft.VSTS.Common.Severity',
                          'Microsoft.VSTS.Common.Priority',
                          'Microsoft.VSTS.Common.BusinessValue',
                        ].includes(f.referenceName),
                    )
                    .map(f => (
                      <div
                        key={f.referenceName}
                        className="px-4 py-1.5 flex items-center gap-2 text-xs"
                      >
                        <code className="font-mono text-slate-700">{f.referenceName}</code>
                        <span className="text-slate-400">{f.name}</span>
                        {f.custom && (
                          <span className="px-1 rounded bg-amber-100 text-amber-700">custom</span>
                        )}
                        {f.required && (
                          <span className="px-1 rounded bg-slate-100 text-slate-500">required</span>
                        )}
                      </div>
                    ))}
                  <p className="px-4 py-1.5 text-[11px] text-slate-400">
                    Showing custom + mappable fields. {schemaQ.data.fields.length} total.
                  </p>
                </div>
              </div>

              {/* States */}
              <div className="bg-white rounded-xl border border-slate-200 shadow-sm px-4 py-3">
                <p className="text-sm font-semibold text-slate-900 mb-2">
                  States → category (drives status normalization)
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {schemaQ.data.states.map(s => (
                    <span
                      key={s.name}
                      className={`text-xs px-2 py-0.5 rounded ${catColor(s.category)}`}
                    >
                      {s.name} <span className="opacity-60">· {s.category}</span>
                    </span>
                  ))}
                </div>
              </div>

              {/* Suggested profile */}
              <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
                <div className="px-4 py-2 border-b border-slate-100 flex items-center gap-2">
                  <Wand2 size={15} className="text-purple-600" />
                  <span className="text-sm font-semibold text-slate-900">
                    Suggested mapping profile
                  </span>
                  <div className="ml-auto flex items-center gap-1">
                    {(['yaml', 'json'] as const).map(f => (
                      <button
                        key={f}
                        onClick={() => setFmt(f)}
                        className={`px-2 py-0.5 text-xs rounded ${fmt === f ? 'bg-blue-600 text-white' : 'text-slate-500 hover:bg-slate-100'}`}
                      >
                        {f.toUpperCase()}
                      </button>
                    ))}
                    <button
                      onClick={copy}
                      className="ml-1 inline-flex items-center gap-1 text-xs px-2 py-0.5 border border-slate-200 rounded hover:bg-slate-50"
                    >
                      {copied ? <Check size={12} /> : <Copy size={12} />}{' '}
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                </div>
                <pre className="text-xs font-mono text-slate-800 bg-slate-50 p-3 overflow-x-auto max-h-[28rem] leading-relaxed">
                  {profileText}
                </pre>
              </div>
            </>
          )}
          {selectedType && schemaQ.error && (
            <ErrorMessage
              message="Failed to load the work-item schema."
              onRetry={() => void schemaQ.refetch()}
            />
          )}
        </div>
      </div>

      {/* Project mapping-rule overrides (advanced) */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        <button
          onClick={() => setShowRules(v => !v)}
          className="w-full px-5 py-3 flex items-center gap-2 text-left"
        >
          {showRules ? (
            <ChevronDown size={16} className="text-slate-400" />
          ) : (
            <ChevronRight size={16} className="text-slate-400" />
          )}
          <SlidersHorizontal size={15} className="text-slate-500" />
          <span className="text-sm font-semibold text-slate-900">
            Mapping rules for this project
          </span>
          <span className="text-xs text-slate-400">
            override the org default · controls these suggestions
          </span>
        </button>
        {showRules && (
          <div className="px-5 pb-5 border-t border-slate-100 pt-4">
            <MappingRulesEditor scope="PROJECT" id={projectId} />
          </div>
        )}
      </div>
    </div>
  )
}

function DriftBanner({
  report,
  onUpdateBaseline,
  updating,
}: {
  report: SchemaDriftReport
  onUpdateBaseline: () => void
  updating: boolean
}) {
  if (!report.hasBaseline) {
    return (
      <div className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-xs text-slate-500">
        Baseline captured for <span className="font-mono">{report.workItemType}</span>. Future
        schema changes (removed / renamed / added fields) will be flagged here.
      </div>
    )
  }
  if (!report.hasDrift) {
    return (
      <div className="bg-green-50 border border-green-200 rounded-xl px-4 py-3 flex items-center gap-2 text-sm text-green-800">
        <CheckCircle2 size={15} /> Schema matches baseline
        {report.baselineCapturedAt && (
          <span className="text-xs text-green-600">
            · captured {relativeTime(report.baselineCapturedAt)}
          </span>
        )}
      </div>
    )
  }
  const mappedRemoved = report.removed.filter(f => f.mapped)
  return (
    <div className="bg-amber-50 border border-amber-300 rounded-xl p-4 space-y-2">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-amber-900 flex items-center gap-2">
          <AlertTriangle size={15} /> Schema drift since baseline
          {report.baselineCapturedAt && (
            <span className="text-xs font-normal text-amber-700">
              (baseline {relativeTime(report.baselineCapturedAt)})
            </span>
          )}
        </p>
        <button
          onClick={onUpdateBaseline}
          disabled={updating}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-amber-400 text-amber-800 rounded-lg hover:bg-amber-100 disabled:opacity-50"
        >
          <RefreshCw size={12} className={updating ? 'animate-spin' : ''} />{' '}
          {updating ? 'Updating…' : 'Update baseline'}
        </button>
      </div>

      {mappedRemoved.length > 0 && (
        <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1">
          ⚠ {mappedRemoved.length} <strong>mapped</strong> field(s) removed upstream — data for
          these will go stale: {mappedRemoved.map(f => f.referenceName).join(', ')}
        </p>
      )}

      <div className="grid grid-cols-3 gap-3 text-xs">
        <DriftCol title={`Removed (${report.removed.length})`} color="text-red-700">
          {report.removed.map(f => (
            <li key={f.referenceName} className="truncate">
              <span className="font-mono">{f.referenceName}</span>
              {f.mapped && (
                <span className="ml-1 text-[10px] px-1 rounded bg-red-100 text-red-700">
                  mapped
                </span>
              )}
            </li>
          ))}
        </DriftCol>
        <DriftCol title={`Added (${report.added.length})`} color="text-blue-700">
          {report.added.map(f => (
            <li key={f.referenceName} className="truncate">
              <span className="font-mono">{f.referenceName}</span>
              {f.mapped && (
                <span className="ml-1 text-[10px] px-1 rounded bg-blue-100 text-blue-700">
                  auto-mappable
                </span>
              )}
            </li>
          ))}
        </DriftCol>
        <DriftCol title={`Type changed (${report.typeChanged.length})`} color="text-amber-700">
          {report.typeChanged.map(f => (
            <li key={f.referenceName} className="truncate">
              <span className="font-mono">{f.referenceName}</span>: {f.fromType} → {f.toType}
            </li>
          ))}
        </DriftCol>
      </div>

      {(report.removedStateCategories.length > 0 || report.addedStateCategories.length > 0) && (
        <p className="text-xs text-amber-800">
          State categories changed
          {report.removedStateCategories.length > 0 && (
            <> · removed: {report.removedStateCategories.join(', ')}</>
          )}
          {report.addedStateCategories.length > 0 && (
            <> · added: {report.addedStateCategories.join(', ')}</>
          )}{' '}
          — affects status normalization.
        </p>
      )}
    </div>
  )
}

function DriftCol({
  title,
  color,
  children,
}: {
  title: string
  color: string
  children: React.ReactNode
}) {
  return (
    <div>
      <p className={`font-semibold mb-1 ${color}`}>{title}</p>
      <ul className="space-y-0.5 text-slate-600">{children}</ul>
    </div>
  )
}
