import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Check,
  CheckCheck,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  Sparkles,
  X,
} from 'lucide-react'
import { api } from '@/lib/api'
import type { GeneratedProposal } from '@/lib/types'

const PRIORITY_TONE: Record<string, string> = {
  CRITICAL: 'bg-red-100 text-red-700',
  HIGH: 'bg-orange-100 text-orange-700',
  MEDIUM: 'bg-amber-100 text-amber-700',
  LOW: 'bg-slate-100 text-slate-600',
}
const STATUS_TONE: Record<string, string> = {
  PROPOSED: 'bg-blue-100 text-blue-700',
  ACCEPTED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-slate-200 text-slate-500',
}

/**
 * Review panel for AI-generated test-case proposals. The generation parked at AWAITING_REVIEW; these
 * are staged (not yet in the catalog). Per-case accept / reject / refine controls are added in later
 * slices — this is the read-only list.
 */
export default function ProposalsReview({
  projectId,
  workflowId,
}: {
  projectId: string
  workflowId: string
}) {
  const qc = useQueryClient()
  const { data: proposals, isLoading } = useQuery({
    queryKey: ['proposals', projectId, workflowId],
    queryFn: () => api.listProposals(projectId, workflowId),
  })
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['proposals', projectId, workflowId] })
    qc.invalidateQueries({ queryKey: ['testCases', projectId] })
  }

  const acceptMut = useMutation({
    mutationFn: (proposalId: string) => api.acceptProposal(projectId, workflowId, proposalId),
    onSuccess: invalidate,
  })
  const acceptAllMut = useMutation({
    mutationFn: () => api.acceptAllProposals(projectId, workflowId),
    onSuccess: invalidate,
  })
  const rejectMut = useMutation({
    mutationFn: (proposalId: string) => api.rejectProposal(projectId, workflowId, proposalId),
    onSuccess: invalidate,
  })
  const repollStatus = () =>
    qc.invalidateQueries({ queryKey: ['generation', projectId, workflowId] })
  const refineMut = useMutation({
    mutationFn: (v: { proposalId: string; instruction: string }) =>
      api.refineProposal(projectId, workflowId, v.proposalId, v.instruction),
    // Refine is async; the run flips to RUNNING then back to AWAITING_REVIEW. Re-poll the status so
    // the parent shows progress and this panel remounts with the refined proposal.
    onSuccess: repollStatus,
  })
  const refineAllMut = useMutation({
    mutationFn: (instruction: string) => api.refineAllProposals(projectId, workflowId, instruction),
    onSuccess: repollStatus,
  })
  const [refineAllOpen, setRefineAllOpen] = useState(false)
  const [refineAllText, setRefineAllText] = useState('')

  const pending = (proposals ?? []).filter(p => p.status === 'PROPOSED').length

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <ClipboardCheck className="text-amber-600" size={18} />
          <p className="text-sm font-semibold text-slate-800">
            Review proposed test cases
            {proposals ? ` · ${pending} pending of ${proposals.length}` : ''}
          </p>
        </div>
        {pending > 0 && (
          <div className="flex items-center gap-1.5">
            <button
              className="inline-flex items-center gap-1 rounded-md border border-blue-300 px-2.5 py-1.5 text-xs font-medium text-blue-600 hover:bg-blue-50 disabled:opacity-50"
              disabled={refineAllMut.isPending}
              onClick={() => setRefineAllOpen(o => !o)}
            >
              <Sparkles size={14} /> Refine all
            </button>
            <button
              className="inline-flex items-center gap-1 rounded-md bg-green-600 px-2.5 py-1.5 text-xs font-medium text-white disabled:opacity-50"
              disabled={acceptAllMut.isPending}
              onClick={() => acceptAllMut.mutate()}
            >
              <CheckCheck size={14} /> Accept all
            </button>
          </div>
        )}
      </div>
      {refineAllOpen && pending > 0 && (
        <div className="flex items-start gap-2 rounded-md border border-blue-200 bg-blue-50/50 p-2">
          <textarea
            className="min-h-[2.25rem] flex-1 rounded border border-slate-300 px-2 py-1 text-xs"
            rows={2}
            placeholder="One instruction applied to every proposed case (e.g. 'add a negative test variant to each')…"
            value={refineAllText}
            onChange={e => setRefineAllText(e.target.value)}
          />
          <button
            className="shrink-0 rounded bg-blue-600 px-2.5 py-1.5 text-xs font-medium text-white disabled:opacity-50"
            disabled={refineAllMut.isPending || !refineAllText.trim()}
            onClick={() => {
              refineAllMut.mutate(refineAllText.trim())
              setRefineAllText('')
              setRefineAllOpen(false)
            }}
          >
            Refine all
          </button>
        </div>
      )}
      <p className="text-xs text-slate-500">
        These were generated for review and are not in the catalog yet. Accept a case to add it as a
        Draft, reject to discard, or refine to have the AI revise it.
      </p>

      {isLoading && <p className="text-sm text-slate-500">Loading proposals…</p>}
      {!isLoading && (proposals ?? []).length === 0 && (
        <p className="py-4 text-sm text-slate-400">No proposals for this run.</p>
      )}
      {(proposals ?? []).map(p => (
        <ProposalCard
          key={p.id}
          proposal={p}
          onAccept={() => acceptMut.mutate(p.id)}
          onReject={() => rejectMut.mutate(p.id)}
          onRefine={instruction => refineMut.mutate({ proposalId: p.id, instruction })}
          busy={
            acceptMut.isPending ||
            acceptAllMut.isPending ||
            rejectMut.isPending ||
            refineMut.isPending
          }
        />
      ))}
    </div>
  )
}

function ProposalCard({
  proposal: p,
  onAccept,
  onReject,
  onRefine,
  busy,
}: {
  proposal: GeneratedProposal
  onAccept: () => void
  onReject: () => void
  onRefine: (instruction: string) => void
  busy: boolean
}) {
  const [open, setOpen] = useState(false)
  const [refineOpen, setRefineOpen] = useState(false)
  const [instruction, setInstruction] = useState('')
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex items-start gap-2">
        <button
          className="mt-0.5 text-slate-400 hover:text-slate-600"
          onClick={() => setOpen(o => !o)}
          title={open ? 'Collapse' : 'Expand steps'}
        >
          {open ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
        </button>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-slate-800">{p.title}</span>
            {p.priority && (
              <span
                className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${
                  PRIORITY_TONE[p.priority] ?? 'bg-slate-100 text-slate-600'
                }`}
              >
                {p.priority}
              </span>
            )}
            <span
              className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${
                STATUS_TONE[p.status] ?? 'bg-slate-100 text-slate-600'
              }`}
            >
              {p.status.toLowerCase()}
            </span>
            {p.status === 'PROPOSED' && (
              <div className="ml-auto flex items-center gap-1.5">
                <button
                  className="inline-flex items-center gap-1 rounded border border-green-600 px-2 py-0.5 text-[11px] font-medium text-green-700 hover:bg-green-50 disabled:opacity-50"
                  disabled={busy}
                  onClick={onAccept}
                >
                  <Check size={12} /> Accept
                </button>
                <button
                  className="inline-flex items-center gap-1 rounded border border-blue-300 px-2 py-0.5 text-[11px] font-medium text-blue-600 hover:bg-blue-50 disabled:opacity-50"
                  disabled={busy}
                  onClick={() => setRefineOpen(o => !o)}
                >
                  <Sparkles size={12} /> Refine
                </button>
                <button
                  className="inline-flex items-center gap-1 rounded border border-slate-300 px-2 py-0.5 text-[11px] font-medium text-slate-500 hover:bg-slate-50 disabled:opacity-50"
                  disabled={busy}
                  onClick={onReject}
                >
                  <X size={12} /> Reject
                </button>
              </div>
            )}
          </div>
          {refineOpen && p.status === 'PROPOSED' && (
            <div className="mt-2 flex items-start gap-2">
              <textarea
                className="min-h-[2.25rem] flex-1 rounded border border-slate-300 px-2 py-1 text-xs"
                rows={2}
                placeholder="Tell the AI how to revise this case (e.g. 'add negative cases', 'split into smaller steps')…"
                value={instruction}
                onChange={e => setInstruction(e.target.value)}
              />
              <button
                className="shrink-0 rounded bg-blue-600 px-2.5 py-1.5 text-xs font-medium text-white disabled:opacity-50"
                disabled={busy || !instruction.trim()}
                onClick={() => {
                  onRefine(instruction.trim())
                  setInstruction('')
                  setRefineOpen(false)
                }}
              >
                Send
              </button>
            </div>
          )}
          {p.description && <p className="mt-1 text-xs text-slate-500">{p.description}</p>}
          {open && (
            <div className="mt-2 space-y-2 border-t border-slate-100 pt-2">
              {p.preconditions && (
                <p className="text-xs text-slate-500">
                  <span className="font-medium text-slate-600">Preconditions: </span>
                  {p.preconditions}
                </p>
              )}
              <ol className="space-y-1 text-xs text-slate-600">
                {p.steps.map((s, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="text-slate-400">{i + 1}.</span>
                    <span>
                      {s.action}
                      {s.expectedResult && (
                        <span className="text-slate-400"> → {s.expectedResult}</span>
                      )}
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
