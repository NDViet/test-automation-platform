import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ListChecks } from 'lucide-react'
import { api } from '@/lib/api'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { PageHeader, Select } from '@/components/ui'
import type { Agent, AgentScope, TaskAgentAssignment } from '@/lib/types'

/** Task types exposed for assignment, grouped by flow, with their sub-types (seed-aligned). */
const TASK_CATALOG: {
  group: string
  tasks: { key: string; label: string; subTypes: string[] }[]
}[] = [
  {
    group: 'Requirements → tests',
    tasks: [
      {
        key: 'GENERATE_TEST_CASES',
        label: 'Generate test cases',
        subTypes: ['FUNCTIONAL', 'NON_FUNCTIONAL'],
      },
      { key: 'GENERATE_AUTOMATION_CODE', label: 'Generate automation code', subTypes: ['DEFAULT'] },
    ],
  },
  {
    group: 'Failure handling',
    tasks: [
      { key: 'CLASSIFY_FAILURE', label: 'Failure analysis', subTypes: ['DEFAULT'] },
      { key: 'PROPOSE_HEAL_FIX', label: 'Failure fixing', subTypes: ['DEFAULT'] },
    ],
  },
  {
    group: 'PR review',
    tasks: [
      { key: 'ANALYZE_PR_DIFF', label: 'Analyze PR diff', subTypes: ['DEFAULT'] },
      { key: 'DETECT_COVERAGE_GAPS', label: 'Detect coverage gaps', subTypes: ['DEFAULT'] },
    ],
  },
]

export default function TaskAgentsPage() {
  const qc = useQueryClient()
  const actor = localStorage.getItem('platform.actor') ?? ''

  const [scopeKind, setScopeKind] = useState<AgentScope>('projects')
  const [scopeId, setScopeId] = useState('')

  const orgs = useQuery({ queryKey: ['organizations'], queryFn: () => api.organizations() })
  const projects = useQuery({ queryKey: ['projects'], queryFn: () => api.projects() })

  const agentsQuery = useQuery({
    queryKey: ['agents', scopeKind, scopeId],
    queryFn: () =>
      scopeKind === 'projects' ? api.effectiveAgents(scopeId) : api.agents('orgs', scopeId),
    enabled: !!scopeId,
  })
  const assignmentsQuery = useQuery({
    queryKey: ['task-agents', scopeKind, scopeId],
    queryFn: () => api.taskAgents(scopeKind, scopeId),
    enabled: !!scopeId,
  })

  const byCell = useMemo(() => {
    const m = new Map<string, TaskAgentAssignment>()
    for (const a of assignmentsQuery.data ?? []) m.set(`${a.taskType}|${a.subType}`, a)
    return m
  }, [assignmentsQuery.data])

  const save = useMutation({
    mutationFn: (v: { taskType: string; subType: string; agentId: string }) =>
      api.upsertTaskAgent(scopeKind, scopeId, v, actor),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['task-agents', scopeKind, scopeId] }),
  })
  const clear = useMutation({
    mutationFn: (id: string) => api.deleteTaskAgent(scopeKind, scopeId, id, actor),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['task-agents', scopeKind, scopeId] }),
  })

  const ownerOptions = useMemo(() => {
    if (scopeKind === 'orgs') return (orgs.data ?? []).map(o => ({ id: o.id, label: o.name }))
    return (projects.data ?? []).map(p => ({ id: p.id, label: `${p.orgName} / ${p.name}` }))
  }, [scopeKind, orgs.data, projects.data])

  const agents: Agent[] = agentsQuery.data ?? []

  function onSelect(taskType: string, subType: string, agentId: string) {
    const existing = byCell.get(`${taskType}|${subType}`)
    if (!agentId) {
      if (existing) clear.mutate(existing.id)
      return
    }
    save.mutate({ taskType, subType, agentId })
  }

  return (
    <div className="space-y-6 max-w-4xl h-full min-h-0 overflow-y-auto scrollbar-thin pr-1">
      <PageHeader
        title="Task → Agent assignments"
        icon={<ListChecks size={20} />}
        description="Choose the default agent for each task (and sub-type). Leave blank to inherit the org default, or the built-in seed. Users can still override per run."
      />

      <div className="flex flex-wrap items-end gap-3 bg-surface rounded-lg border border-border p-4">
        <label className="text-sm">
          <span className="block text-fg-muted mb-1">Scope</span>
          <Select
            containerClassName="w-auto min-w-[10rem]"
            value={scopeKind}
            onChange={e => {
              setScopeKind(e.target.value as AgentScope)
              setScopeId('')
            }}
          >
            <option value="projects">Project</option>
            <option value="orgs">Organization</option>
          </Select>
        </label>
        <label className="text-sm flex-1 min-w-56">
          <span className="block text-fg-muted mb-1">
            {scopeKind === 'orgs' ? 'Organization' : 'Project'}
          </span>
          <Select value={scopeId} onChange={e => setScopeId(e.target.value)}>
            <option value="">Select…</option>
            {ownerOptions.map(o => (
              <option key={o.id} value={o.id}>
                {o.label}
              </option>
            ))}
          </Select>
        </label>
      </div>

      {!scopeId && <p className="text-sm text-fg-subtle">Pick a scope and owner to assign agents.</p>}

      {scopeId && (agentsQuery.isLoading || assignmentsQuery.isLoading) && (
        <LoadingSpinner message="Loading…" />
      )}
      {scopeId && assignmentsQuery.error && (
        <ErrorMessage
          message="Failed to load assignments."
          onRetry={() => void assignmentsQuery.refetch()}
        />
      )}

      {scopeId && agentsQuery.data && assignmentsQuery.data && (
        <div className="space-y-5">
          {agents.length === 0 && (
            <p className="text-sm text-warning bg-warning-bg border border-warning-border rounded-lg px-3 py-2">
              No agents available in this scope yet — create one on the Agents page first.
            </p>
          )}
          {TASK_CATALOG.map(group => (
            <div key={group.group} className="bg-surface rounded-lg border border-border">
              <div className="px-5 py-3 border-b border-border text-sm font-semibold text-fg">
                {group.group}
              </div>
              <div className="divide-y divide-border">
                {group.tasks.flatMap(task =>
                  task.subTypes.map(sub => {
                    const cell = byCell.get(`${task.key}|${sub}`)
                    return (
                      <div key={`${task.key}|${sub}`} className="px-5 py-3 flex items-center gap-3">
                        <div className="flex-1 min-w-0">
                          <p className="text-sm text-fg">{task.label}</p>
                          {sub !== 'DEFAULT' && (
                            <p className="text-xs text-fg-muted">{sub.toLowerCase()}</p>
                          )}
                        </div>
                        <Select
                          containerClassName="w-64"
                          value={cell?.agentId ?? ''}
                          onChange={e => onSelect(task.key, sub, e.target.value)}
                        >
                          <option value="">(inherit / seed)</option>
                          {agents.map(a => (
                            <option key={a.id} value={a.id}>
                              {a.name}
                              {a.inherited ? ' (org)' : ''}
                            </option>
                          ))}
                        </Select>
                      </div>
                    )
                  }),
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
