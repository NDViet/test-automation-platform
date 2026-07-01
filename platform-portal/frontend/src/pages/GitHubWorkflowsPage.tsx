import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useProject } from '@/components/layout/ProjectLayout'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import { Button, Input, PageHeader } from '@/components/ui'
import type { GitHubWorkflow, GitHubWorkflowRun } from '@/lib/types'
import {
  RefreshCw,
  Play,
  ExternalLink,
  ChevronDown,
  ChevronUp,
  GitBranch,
  Zap,
  CheckCircle,
  XCircle,
  Clock,
  MinusCircle,
  Settings,
} from 'lucide-react'

// ── Run status helpers ─────────────────────────────────────────────────────────

function runIcon(run: GitHubWorkflowRun) {
  if (run.status === 'in_progress')
    return <Clock size={13} className="text-warning animate-pulse" />
  if (run.conclusion === 'success') return <CheckCircle size={13} className="text-success" />
  if (run.conclusion === 'failure' || run.conclusion === 'timed_out')
    return <XCircle size={13} className="text-danger" />
  return <MinusCircle size={13} className="text-fg-subtle" />
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

function runPillCls(run: GitHubWorkflowRun): string {
  if (run.status === 'in_progress') return 'bg-warning-bg text-warning'
  if (run.conclusion === 'success') return 'bg-success-bg text-success'
  if (run.conclusion === 'failure') return 'bg-danger-bg text-danger'
  return 'bg-neutral-bg text-neutral'
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
    mutationFn: () =>
      api.triggerWorkflow(projectId, {
        repoFullName: workflow.repoFullName,
        workflowId: workflow.id,
        ref,
        inputs: extraKey.trim() ? { [extraKey.trim()]: extraVal } : undefined,
      }),
    onSuccess: data => {
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
      <div className="bg-surface rounded-xl shadow-md w-full max-w-md mx-4 p-6 space-y-4">
        <div>
          <h3 className="text-base font-semibold text-fg">Trigger Workflow</h3>
          <p className="text-xs text-fg-muted mt-0.5 font-mono">{workflow.name}</p>
          <p className="text-xs text-fg-subtle font-mono">{workflow.repoFullName}</p>
        </div>

        <div>
          <label className="block text-xs font-medium text-fg mb-1">Ref (branch / tag / SHA)</label>
          <Input
            value={ref}
            onChange={e => setRef(e.target.value)}
            placeholder="main"
            className="font-mono"
          />
        </div>

        <div>
          <label className="block text-xs font-medium text-fg mb-1">Input (optional)</label>
          <div className="flex gap-2">
            <Input
              value={extraKey}
              onChange={e => setExtraKey(e.target.value)}
              placeholder="key"
              className="w-28 font-mono"
            />
            <Input
              value={extraVal}
              onChange={e => setExtraVal(e.target.value)}
              placeholder="value"
              className="flex-1"
            />
          </div>
          <p className="text-[10px] text-fg-subtle mt-1">
            Passed as <span className="font-mono">inputs</span> to the workflow. Add more via the
            GitHub UI.
          </p>
        </div>

        {result && (
          <div
            className={`text-sm px-4 py-3 rounded-lg border ${
              result.triggered
                ? 'bg-success-bg border-success-border text-success'
                : 'bg-danger-bg border-danger-border text-danger'
            }`}
          >
            {result.message}
          </div>
        )}

        {mutation.isError && !result && (
          <p className="text-sm text-danger">Request failed — check console.</p>
        )}

        <div className="flex gap-3 pt-1">
          <Button onClick={() => void mutation.mutate()} disabled={!ref.trim()} loading={mutation.isPending}>
            <Play size={13} />
            {mutation.isPending ? 'Triggering…' : 'Trigger'}
          </Button>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
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
    return (
      <div className="px-4 py-3">
        <LoadingSpinner message="Loading runs…" />
      </div>
    )
  }

  if (runs.length === 0) {
    return <p className="px-4 py-3 text-xs text-fg-subtle">No runs found.</p>
  }

  return (
    <div className="divide-y divide-border bg-surface-muted/50">
      {runs.map(run => (
        <div key={run.id} className="px-4 py-2.5 flex items-center gap-3">
          <div className="shrink-0">{runIcon(run)}</div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-xs font-medium text-fg truncate">
                {run.displayTitle || `Run #${run.id}`}
              </span>
              <span
                className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${runPillCls(run)}`}
              >
                {runLabel(run)}
              </span>
            </div>
            <div className="flex items-center gap-2 mt-0.5">
              <GitBranch size={10} className="text-fg-subtle shrink-0" />
              <span className="text-[10px] text-fg-subtle font-mono truncate">{run.branch}</span>
              <span className="text-[10px] text-fg-subtle">·</span>
              <span className="text-[10px] text-fg-subtle">{run.event}</span>
              {run.createdAt && (
                <>
                  <span className="text-[10px] text-fg-subtle">·</span>
                  <span className="text-[10px] text-fg-subtle">{relativeTime(run.createdAt)}</span>
                </>
              )}
            </div>
          </div>
          <a
            href={run.htmlUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="shrink-0 text-fg-subtle hover:text-primary transition-colors"
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
    <div className="bg-surface rounded-lg border border-border shadow-xs overflow-hidden">
      {/* Header row */}
      <div
        className="px-4 py-3 flex items-center gap-3 cursor-pointer hover:bg-surface-muted transition-colors"
        onClick={() => setExpanded(e => !e)}
      >
        {/* State indicator */}
        <div
          className={`w-2 h-2 rounded-full shrink-0 ${
            workflow.state === 'active' ? 'bg-success' : 'bg-border-strong'
          }`}
        />

        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-fg truncate">{workflow.name}</p>
          <p className="text-[11px] text-fg-subtle font-mono truncate">{workflow.path}</p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <span
            className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full border ${
              workflow.state === 'active'
                ? 'bg-success-bg text-success border-success-border'
                : 'bg-neutral-bg text-neutral border-neutral-border'
            }`}
          >
            {workflow.state === 'active' ? 'active' : workflow.state.replace(/_/g, ' ')}
          </span>

          <Button
            size="sm"
            variant="subtle"
            onClick={e => {
              e.stopPropagation()
              onTrigger(workflow)
            }}
            title="Trigger workflow"
          >
            <Play size={11} /> Run
          </Button>

          <a
            href={workflow.htmlUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={e => e.stopPropagation()}
            className="text-fg-subtle hover:text-primary transition-colors"
            title="View in GitHub"
          >
            <ExternalLink size={14} />
          </a>

          {expanded ? (
            <ChevronUp size={14} className="text-fg-subtle" />
          ) : (
            <ChevronDown size={14} className="text-fg-subtle" />
          )}
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
      <PageHeader
        title="GitHub Workflows"
        icon={<Zap size={20} className="text-primary" />}
        description={
          <>
            GitHub Actions workflows from <span className="font-medium">Test Automation</span> repos
            assigned to this project.
          </>
        }
        actions={
          <>
            <Link
              to={`${base}/settings`}
              className="inline-flex items-center gap-1.5 h-9 px-3.5 text-sm font-medium text-fg bg-surface border border-border-strong rounded-md hover:bg-surface-muted transition-colors"
            >
              <Settings size={13} /> Manage Repos
            </Link>
            <Button
              variant="secondary"
              onClick={() => {
                void refetch()
                void qc.invalidateQueries({ queryKey: ['workflow-runs', projectId] })
              }}
              disabled={isFetching}
            >
              <RefreshCw size={13} className={isFetching ? 'animate-spin' : ''} />
              Refresh
            </Button>
          </>
        }
      />

      {/* Content */}
      {isLoading ? (
        <LoadingSpinner message="Loading workflows…" />
      ) : error ? (
        <div className="bg-danger-bg border border-danger-border rounded-lg px-5 py-6 text-center">
          <p className="text-sm text-danger">
            Failed to load workflows — check your GitHub credential.
          </p>
        </div>
      ) : repos.length === 0 ? (
        <div className="bg-surface-muted border border-border rounded-lg px-5 py-12 text-center space-y-3">
          <Zap size={32} className="text-fg-subtle mx-auto" />
          <p className="text-sm font-medium text-fg">No Test Automation repos configured</p>
          <p className="text-xs text-fg-muted">
            Assign repos with the <span className="font-medium">Test Auto</span> role in{' '}
            <Link to={`${base}/settings`} className="text-primary hover:underline">
              Project Settings → GitHub
            </Link>
            .
          </p>
        </div>
      ) : (
        <div className="space-y-8">
          {repos.map(repo => (
            <div key={repo} className="space-y-3">
              {/* Repo header */}
              <div className="flex items-center gap-3">
                <GitBranch size={15} className="text-fg-muted shrink-0" />
                <span className="text-sm font-semibold text-fg font-mono">{repo}</span>
                <span className="text-xs text-fg-subtle">
                  {byRepo[repo].length} workflow{byRepo[repo].length !== 1 ? 's' : ''}
                </span>
                <div className="flex-1 border-t border-border" />
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
