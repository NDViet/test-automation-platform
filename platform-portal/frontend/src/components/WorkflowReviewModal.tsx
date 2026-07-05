import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle, GitPullRequest, Loader2, Search, Sparkles, X } from 'lucide-react'
import { api } from '@/lib/api'
import type { GenerationStatus, PrContextItem, RegenerateForm, Requirement } from '@/lib/types'
import ProposalsReview from '@/components/ProposalsReview'

/**
 * Opens a parked AI test-case generation workflow from the Review Queue. It loads the run status and
 * mounts the existing {@link ProposalsReview} panel so the proposals can be curated (accept / accept
 * all / reject / refine) outside the original generation session, plus:
 *  - "Generate more": a fresh round with an augmented requirement set, selected PR context (stored
 *    Impact Analyses), and/or custom input — the fresh round replaces the un-accepted proposals.
 *  - "Finish review": auto-reject any remaining proposals and complete the workflow (leaves queue).
 */
export default function WorkflowReviewModal({
  projectId,
  workflowId,
  title,
  onClose,
}: {
  projectId: string
  workflowId: string
  title: string
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [showGenerateMore, setShowGenerateMore] = useState(false)
  const [selectedReqIds, setSelectedReqIds] = useState<Set<string>>(new Set())
  const [selectedAnalysisIds, setSelectedAnalysisIds] = useState<Set<string>>(new Set())
  const [customInput, setCustomInput] = useState('')
  const [reqSearch, setReqSearch] = useState('')

  // Poll the run status so the panel reflects RUNNING (during a refine/regenerate round) and returns
  // to review when the model finishes.
  const { data: status } = useQuery({
    queryKey: ['generation', projectId, workflowId],
    queryFn: () => api.getGeneration(projectId, workflowId),
    refetchInterval: query => {
      const s = (query.state.data as GenerationStatus | undefined)?.status
      return s === 'RUNNING' ? 3000 : false
    },
  })
  const running = status?.status === 'RUNNING'

  // Lazy-load the pickers only when the panel is open.
  const { data: prContext } = useQuery({
    queryKey: ['generation-pr-context', projectId, workflowId],
    queryFn: () => api.generationPrContext(projectId, workflowId),
    enabled: showGenerateMore,
  })
  const { data: requirements } = useQuery({
    queryKey: ['requirements', projectId],
    queryFn: () => api.requirements(projectId),
    enabled: showGenerateMore,
  })

  const filteredReqs = useMemo(() => {
    const list = requirements ?? []
    const q = reqSearch.trim().toLowerCase()
    const matched = q
      ? list.filter(
          r => r.title.toLowerCase().includes(q) || (r.externalId ?? '').toLowerCase().includes(q),
        )
      : list
    return matched.slice(0, 50)
  }, [requirements, reqSearch])

  const regenerateMut = useMutation({
    mutationFn: () => {
      const body: RegenerateForm = {}
      if (selectedReqIds.size > 0) body.requirementIds = [...selectedReqIds]
      if (selectedAnalysisIds.size > 0) body.impactAnalysisIds = [...selectedAnalysisIds]
      if (customInput.trim()) body.freeText = customInput.trim()
      return api.regenerateGeneration(projectId, workflowId, body)
    },
    onSuccess: () => {
      // Fresh round runs async; re-poll status (flips to RUNNING) and clear the pending proposals.
      qc.invalidateQueries({ queryKey: ['generation', projectId, workflowId] })
      qc.invalidateQueries({ queryKey: ['proposals', projectId, workflowId] })
      setShowGenerateMore(false)
      setCustomInput('')
    },
  })

  const finishMut = useMutation({
    mutationFn: () => api.finishGeneration(projectId, workflowId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['work-items', projectId] })
      onClose()
    },
  })

  function toggle(set: Set<string>, id: string): Set<string> {
    const next = new Set(set)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-surface rounded-xl shadow-md w-full max-w-3xl max-h-[85vh] flex flex-col">
        <div className="px-6 py-5 border-b border-border flex items-center justify-between shrink-0">
          <div className="min-w-0">
            <h2 className="font-semibold text-fg truncate">Resume generation review</h2>
            <p className="text-xs text-fg-muted mt-0.5 truncate">{title}</p>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            {status && (
              <span className="inline-flex items-center gap-1.5 text-xs text-fg-muted">
                {running && <Loader2 size={13} className="animate-spin text-primary" />}
                {status.status.replace('_', ' ')}
              </span>
            )}
            <button onClick={onClose} className="text-fg-subtle hover:text-fg" aria-label="Close">
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="px-6 py-5 overflow-y-auto space-y-4">
          {running && (
            <div className="flex items-center gap-2 rounded-md border border-border bg-surface-muted px-3 py-2 text-xs text-fg-muted">
              <Loader2 size={13} className="animate-spin text-primary" />
              The AI is working — proposals will refresh when it returns to review.
            </div>
          )}

          {/* Generate more panel */}
          <div className="rounded-lg border border-border">
            <button
              onClick={() => setShowGenerateMore(o => !o)}
              disabled={running}
              className="w-full flex items-center gap-2 px-3 py-2.5 text-sm font-medium text-fg hover:bg-surface-muted disabled:opacity-50 rounded-lg"
            >
              <Sparkles size={15} className="text-primary" />
              Generate more
              <span className="text-xs font-normal text-fg-subtle ml-1">
                add requirements, PR context, or custom input — replaces un-accepted proposals
              </span>
            </button>

            {showGenerateMore && (
              <div className="border-t border-border px-3 py-3 space-y-3">
                {/* PR context */}
                <div>
                  <p className="text-xs font-medium text-fg-muted mb-1.5 flex items-center gap-1.5">
                    <GitPullRequest size={13} /> PR context
                  </p>
                  {(prContext ?? []).length === 0 ? (
                    <p className="text-xs text-fg-subtle">
                      No analysed pull requests linked to this workflow's requirements.
                    </p>
                  ) : (
                    <div className="space-y-1">
                      {(prContext ?? []).map((a: PrContextItem) => (
                        <label
                          key={a.analysisId}
                          className="flex items-start gap-2 text-xs text-fg cursor-pointer"
                        >
                          <input
                            type="checkbox"
                            className="mt-0.5"
                            checked={selectedAnalysisIds.has(a.analysisId)}
                            onChange={() => setSelectedAnalysisIds(s => toggle(s, a.analysisId))}
                          />
                          <span>
                            <span className="font-medium">{a.name}</span>
                            {a.prs.length > 0 && (
                              <span className="text-fg-subtle"> · {a.prs.length} PR(s)</span>
                            )}
                            {a.summary && (
                              <span className="block text-fg-subtle leading-snug">{a.summary}</span>
                            )}
                          </span>
                        </label>
                      ))}
                    </div>
                  )}
                </div>

                {/* Requirement picker */}
                <div>
                  <p className="text-xs font-medium text-fg-muted mb-1.5">
                    Requirements{' '}
                    <span className="font-normal text-fg-subtle">
                      (optional — leave empty to keep the current set)
                    </span>
                  </p>
                  <div className="flex items-center gap-1.5 border border-border-strong rounded-md px-2 py-1 mb-1.5">
                    <Search size={13} className="text-fg-subtle" />
                    <input
                      value={reqSearch}
                      onChange={e => setReqSearch(e.target.value)}
                      placeholder="Search requirements…"
                      className="flex-1 text-xs outline-none bg-transparent text-fg"
                    />
                  </div>
                  <div className="max-h-40 overflow-y-auto space-y-1 pr-1">
                    {filteredReqs.map((r: Requirement) => (
                      <label
                        key={r.id}
                        className="flex items-start gap-2 text-xs text-fg cursor-pointer"
                      >
                        <input
                          type="checkbox"
                          className="mt-0.5"
                          checked={selectedReqIds.has(r.id)}
                          onChange={() => setSelectedReqIds(s => toggle(s, r.id))}
                        />
                        <span>
                          {r.externalId && (
                            <span className="text-fg-subtle">{r.externalId} · </span>
                          )}
                          {r.title}
                        </span>
                      </label>
                    ))}
                    {filteredReqs.length === 0 && (
                      <p className="text-xs text-fg-subtle">No matching requirements.</p>
                    )}
                  </div>
                  {selectedReqIds.size > 0 && (
                    <p className="text-xs text-fg-subtle mt-1">
                      {selectedReqIds.size} selected — this replaces the requirement set for the
                      next round.
                    </p>
                  )}
                </div>

                {/* Custom input */}
                <div>
                  <p className="text-xs font-medium text-fg-muted mb-1.5">Custom input</p>
                  <textarea
                    value={customInput}
                    onChange={e => setCustomInput(e.target.value)}
                    rows={3}
                    placeholder="Extra guidance for the AI this round (e.g. 'focus on error handling and boundary values')…"
                    className="w-full text-xs rounded-md border border-border-strong px-2 py-1.5 bg-transparent text-fg outline-none"
                  />
                </div>

                {regenerateMut.isError && (
                  <p className="text-xs text-danger">Failed to start regeneration. Try again.</p>
                )}
                <div className="flex justify-end">
                  <button
                    onClick={() => regenerateMut.mutate()}
                    disabled={
                      regenerateMut.isPending ||
                      (selectedReqIds.size === 0 &&
                        selectedAnalysisIds.size === 0 &&
                        !customInput.trim())
                    }
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-primary-fg bg-primary rounded-md hover:bg-primary-hover disabled:opacity-50"
                  >
                    {regenerateMut.isPending ? (
                      <Loader2 size={13} className="animate-spin" />
                    ) : (
                      <Sparkles size={13} />
                    )}
                    Generate fresh round
                  </button>
                </div>
              </div>
            )}
          </div>

          <ProposalsReview projectId={projectId} workflowId={workflowId} />
        </div>

        <div className="px-6 py-4 border-t border-border flex items-center justify-between shrink-0">
          {finishMut.isError ? (
            <p className="text-xs text-danger">Failed to finish review. Try again.</p>
          ) : (
            <span className="text-xs text-fg-subtle">
              Finishing discards any remaining un-accepted proposals.
            </span>
          )}
          <div className="flex gap-3">
            <button
              onClick={() => {
                qc.invalidateQueries({ queryKey: ['work-items', projectId] })
                onClose()
              }}
              className="px-3 py-1.5 text-sm font-medium text-fg border border-border-strong rounded-md hover:bg-surface-muted"
            >
              Close
            </button>
            <button
              onClick={() => finishMut.mutate()}
              disabled={finishMut.isPending || running}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-success border border-success-border rounded-md hover:bg-success-bg disabled:opacity-50"
            >
              {finishMut.isPending ? (
                <Loader2 size={13} className="animate-spin" />
              ) : (
                <CheckCircle size={13} />
              )}
              Finish review
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
