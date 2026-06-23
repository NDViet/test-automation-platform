import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import type { GitHubWorkflow, GitHubWorkflowRun } from '@/lib/types'
import {
  RefreshCw, Play, ExternalLink, ChevronDown, ChevronUp,
  GitBranch, Zap, CheckCircle, XCircle, Clock, MinusCircle,
  Settings,
} from 'lucide-react'

// ── Run status helpers ─────────────────────────────────────────────────────────


function runIcon(run: GitHubWorkflowRun) {
  if (run.status === 'in_progress') return <Clock size={13} className="text-yellow-500 animate-pulse" />
  if (run.conclusion === 'success') return <CheckCircle size={13} className="text-green-500" />
  if (run.conclusion === 'failure' || run.conclusion === 'timed_out') return <XCircle size={13} className="text-red-500" />
  return <MinusCircle size={13} className="text-slate-400" />
}

function runLabel(run: GitHubWorkflowRun): string {
  if (run.status === 'in_progress') return 'Running'
  if (run.status === 'queued') return 'Queued'
  if (run.conclusion === 'success') return 'Success'
  if (run.conclusion === 'failure') return 'Failed'
  if (run.conclusion === 'timed_out') return 'Timed out'
  if (run.conclusion === 'cancelled') return 'Cancelled'
  if (run.conclusion === 'skipped') return 'Skipped'
  return run.status
}

// ── Trigger modal ─────────────────────────────────────────────────────────────

interface TriggerModalProps {
  projectId: string
  workflow: GitHubWorkflow
  onClose: () => void
}

function TriggerModal({ projectId, workflow, onClose }: TriggerModalProps) {
  const qc = useQueryClient()
  const [ref, setRef] = useState('main')
  const [extraKey, setExtraKey] = useState('')
  const [extraVal, setExtraVal] = useState('')
  const [result, setResult] = useState<{ triggered: boolean; message: string } | null>(null)

  const mutation = useMutation({
    mutationFn: () => api.triggerWorkflow(projectId, {
      repoFullName: workflow.repoFullName,
      workflowId: workflow.id,
      ref,
      inputs: extraKey.trim() ? { [extraKey.trim()]: extraVal } : undefined,
    }),
    onSuccess: (data) => {
      setResult(data)
      if (data.triggered) {
        setTimeout(() => {
          void qc.invalidateQueries({ queryKey: ['workflow-runs', projectId] })
          onClose()
        }, 1500)
      }
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4 p-6 space-y-4">
        <div>
          <h3 className="text-base font-semibold text-slate-900">Trigger Workflow</h3>
          <p className="text-xs text-slate-500 mt-0.5 font-mono">{workflow.name}</p>
          <p className="text-xs text-slate-400 font-mono">{workflow.repoFullName}</p>
        </div>

        <div>
          <label className="block text-xs font-medium text-slate-700 mb-1">Ref (branch / tag / SHA)</label>
          <input
            type="text"
            value={ref}
            onChange={e => setRef(e.target.value)}
            placeholder="main"
            className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
          />
        </div>

        <div>
          <label className="block text-xs font-medium text-slate-700 mb-1">Input (optional)</label>
          <div className="flex gap-2">
            <input
              type="text"
              value={extraKey}
              onChange={e => setExtraKey(e.target.value)}
              placeholder="key"
              className="w-28 text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
            />
            <input
              type="text"
              value={extraVal}
              onChange={e => setExtraVal(e.target.value)}
              placeholder="value"
              className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <p className="text-[10px] text-slate-400 mt-1">
            Passed as <span className="font-mono">inputs</span> to the workflow. Add more via the GitHub UI.
          </p>
        </div>

        {result && (
          <div className={`text-sm px-4 py-3 rounded-lg ${
            result.triggered
              ? 'bg-green-50 border border-green-200 text-green-800'
              : 'bg-red-50 border border-red-200 text-red-800'
          }`}>
            {result.message}
          </div>
        )}

        {mutation.isError && !result && (
          <p className="text-sm text-red-600">Request failed — check console.</p>
        )}

        <div className="flex gap-3 pt-1">
          <button
            onClick={() => void mutation.mutate()}
            disabled={!ref.trim() || mutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
          >
            <Play size={13} />
            {mutation.isPending ? 'Triggering…' : 'Trigger'}
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Workflow runs panel ────────────────────────────────────────────────────────

interface RunsPanelProps {
  projectId: string
  workflow: GitHubWorkflow
}

function RunsPanel({ projectId, workflow }: RunsPanelProps) {
  const { data: runs = [], isLoading } = useQuery({
    queryKey: ['workflow-runs', projectId, workflow.repoFullName, workflow.id],
    queryFn: () => api.githubWorkflowRuns(projectId, workflow.repoFullName, workflow.id, 15),
    refetchInterval: 30_000,
  })

  if (isLoading) {
    return <div className="px-4 py-3"><LoadingSpinner message="Loading runs…" /></div>
  }

  if (runs.length === 0) {
    return <p className="px-4 py-3 text-xs text-slate-400">No runs found.</p>
  }

  return (
    <div className="divide-y divide-slate-50 bg-slate-50/60">
      {runs.map(run => (
        <div key={run.id} className="px-4 py-2.5 flex items-center gap-3">
          <div className="shrink-0">{runIcon(run)}</div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-xs font-medium text-slate-800 truncate">{run.displayTitle || `Run #${run.id}`}</span>
              <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${
                run.status === 'in_progress' ? 'bg-yellow-100 text-yellow-700'
                : run.conclusion === 'success' ? 'bg-green-100 text-green-700'
                : run.conclusion === 'failure' ? 'bg-red-100 text-red-700'
                : 'bg-slate-100 text-slate-600'
              }`}>
                {runLabel(run)}
              </span>
            </div>
            <div className="flex items-center gap-2 mt-0.5">
              <GitBranch size={10} className="text-slate-400 shrink-0" />
              <span className="text-[10px] text-slate-400 font-mono truncate">{run.branch}</span>
              <span className="text-[10px] text-slate-400">·</span>
              <span className="text-[10px] text-slate-400">{run.event}</span>
              {run.createdAt && (
                <>
                  <span className="text-[10px] text-slate-400">·</span>
                  <span className="text-[10px] text-slate-400">{relativeTime(run.createdAt)}</span>
                </>
              )}
            </div>
          </div>
          <a
            href={run.htmlUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="shrink-0 text-slate-400 hover:text-blue-600 transition-colors"
          >
            <ExternalLink size={13} />
          </a>
        </div>
      ))}
    </div>
  )
}

// ── Workflow card ──────────────────────────────────────────────────────────────

interface WorkflowCardProps {
  projectId: string
  workflow: GitHubWorkflow
  onTrigger: (wf: GitHubWorkflow) => void
}

function WorkflowCard({ projectId, workflow, onTrigger }: WorkflowCardProps) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      {/* Header row */}
      <div
        className="px-4 py-3 flex items-center gap-3 cursor-pointer hover:bg-slate-50 transition-colors"
        onClick={() => setExpanded(e => !e)}
      >
        {/* State indicator */}
        <div className={`w-2 h-2 rounded-full shrink-0 ${
          workflow.state === 'active' ? 'bg-green-500' : 'bg-slate-300'
        }`} />

        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-slate-900 truncate">{workflow.name}</p>
          <p className="text-[11px] text-slate-400 font-mono truncate">{workflow.path}</p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full border ${
            workflow.state === 'active'
              ? 'bg-green-50 text-green-700 border-green-200'
              : 'bg-slate-100 text-slate-500 border-slate-200'
          }`}>
            {workflow.state === 'active' ? 'active' : workflow.state.replace(/_/g, ' ')}
          </span>

          <button
            onClick={e => { e.stopPropagation(); onTrigger(workflow) }}
            title="Trigger workflow"
            className="flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-green-700 border border-green-200 rounded-lg hover:bg-green-50 transition-colors"
          >
            <Play size={11} /> Run
          </button>

          <a
            href={workflow.htmlUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={e => e.stopPropagation()}
            className="text-slate-400 hover:text-blue-600 transition-colors"
            title="View in GitHub"
          >
            <ExternalLink size={14} />
          </a>

          {expanded ? <ChevronUp size={14} className="text-slate-400" /> : <ChevronDown size={14} className="text-slate-400" />}
        </div>
      </div>

      {/* Expandable runs list */}
      {expanded && <RunsPanel projectId={projectId} workflow={workflow} />}
    </div>
  )
}

// ── Main page ──────────────────────────────────────────────────────────────────

export default function GitHubWorkflowsPage() {
  const { projectId, base } = useProject()
  const qc = useQueryClient()
  const [triggerTarget, setTriggerTarget] = useState<GitHubWorkflow | null>(null)

  const {
    data: workflows = [],
    isLoading,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['github-workflows', projectId],
    queryFn: () => api.githubWorkflows(projectId!),
    enabled: !!projectId,
    refetchInterval: 60_000,
  })

  // Group workflows by repo
  const byRepo = workflows.reduce<Record<string, GitHubWorkflow[]>>((acc, wf) => {
    ;(acc[wf.repoFullName] ??= []).push(wf)
    return acc
  }, {})

  const repos = Object.keys(byRepo).sort()

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
            <Zap size={22} className="text-purple-600" />
            GitHub Workflows
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            GitHub Actions workflows from <span className="font-medium">Test Automation</span> repos assigned to this project.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to={`${base}/settings`}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
          >
            <Settings size={13} /> Manage Repos
          </Link>
          <button
            onClick={() => { void refetch(); void qc.invalidateQueries({ queryKey: ['workflow-runs', projectId] }) }}
            disabled={isFetching}
            className="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50 transition-colors"
          >
            <RefreshCw size={13} className={isFetching ? 'animate-spin' : ''} />
            Refresh
          </button>
        </div>
      </div>

      {/* Content */}
      {isLoading ? (
        <LoadingSpinner message="Loading workflows…" />
      ) : error ? (
        <div className="bg-red-50 border border-red-200 rounded-xl px-5 py-6 text-center">
          <p className="text-sm text-red-700">Failed to load workflows — check your GitHub credential.</p>
        </div>
      ) : repos.length === 0 ? (
        <div className="bg-slate-50 border border-slate-200 rounded-xl px-5 py-12 text-center space-y-3">
          <Zap size={32} className="text-slate-300 mx-auto" />
          <p className="text-sm font-medium text-slate-700">No Test Automation repos configured</p>
          <p className="text-xs text-slate-500">
            Assign repos with the <span className="font-medium">Test Auto</span> role in{' '}
            <Link to={`${base}/settings`} className="text-blue-600 hover:underline">Project Settings → GitHub</Link>.
          </p>
        </div>
      ) : (
        <div className="space-y-8">
          {repos.map(repo => (
            <div key={repo} className="space-y-3">
              {/* Repo header */}
              <div className="flex items-center gap-3">
                <GitBranch size={15} className="text-slate-500 shrink-0" />
                <span className="text-sm font-semibold text-slate-800 font-mono">{repo}</span>
                <span className="text-xs text-slate-400">
                  {byRepo[repo].length} workflow{byRepo[repo].length !== 1 ? 's' : ''}
                </span>
                <div className="flex-1 border-t border-slate-200" />
              </div>

              {/* Workflow cards */}
              <div className="space-y-2">
                {byRepo[repo].map(wf => (
                  <WorkflowCard
                    key={`${repo}/${wf.id}`}
                    projectId={projectId!}
                    workflow={wf}
                    onTrigger={setTriggerTarget}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Trigger modal */}
      {triggerTarget && (
        <TriggerModal
          projectId={projectId!}
          workflow={triggerTarget}
          onClose={() => setTriggerTarget(null)}
        />
      )}
    </div>
  )
}
