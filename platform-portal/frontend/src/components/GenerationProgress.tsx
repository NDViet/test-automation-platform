import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2, Sparkles, MessageCircleQuestion, CheckCircle2, AlertTriangle } from 'lucide-react'
import { api } from '@/lib/api'
import type { ClarificationAnswer, ClarificationRound } from '@/lib/types'
import ProposalsReview from './ProposalsReview'

const TERMINAL = new Set(['COMPLETED', 'FAILED'])

/**
 * Live view of a generation run launched from the modal: polls status, renders the agent's
 * clarifying questions for the user to answer (pause → answer → resume), shows the Q&A transcript,
 * and the final outcome.
 */
export default function GenerationProgress({
  projectId,
  workflowId,
  onClose,
}: {
  projectId: string
  workflowId: string
  onClose: () => void
}) {
  const qc = useQueryClient()

  const { data: status } = useQuery({
    queryKey: ['generation', projectId, workflowId],
    queryFn: () => api.getGeneration(projectId, workflowId),
    refetchInterval: q => {
      const s = q.state.data?.status
      return s && TERMINAL.has(s) ? false : 2500
    },
  })

  const state = status?.status ?? 'RUNNING'
  const answered = (status?.rounds ?? []).filter(r => r.status === 'ANSWERED')

  // Live token stream over WebSocket — the agent relays throttled previews via Redis. This is the
  // heartbeat: while previews keep arriving the run is provably still transferring content. The
  // poll above remains the source of truth for the terminal status.
  const [live, setLive] = useState<{ chars: number; preview: string } | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const terminal = TERMINAL.has(state)

  useEffect(() => {
    if (terminal) {
      wsRef.current?.close()
      return
    }
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${proto}//${window.location.host}/ws/generations/${workflowId}`
    let ws: WebSocket
    try {
      ws = new WebSocket(url)
    } catch {
      return // fall back to polling-only
    }
    wsRef.current = ws
    ws.onmessage = ev => {
      try {
        const msg = JSON.parse(ev.data as string)
        if (msg.type === 'token') setLive({ chars: msg.chars ?? 0, preview: msg.preview ?? '' })
        else if (msg.type === 'started') setLive({ chars: 0, preview: '' })
      } catch {
        /* ignore malformed frames */
      }
    }
    return () => {
      try {
        ws.close()
      } catch {
        /* ignore */
      }
    }
  }, [workflowId, terminal])

  return (
    <div className="px-5 py-5 space-y-4 overflow-y-auto">
      {/* State banner */}
      {state === 'AWAITING_INPUT' && status?.pending ? (
        <QuestionForm
          projectId={projectId}
          workflowId={workflowId}
          round={status.pending}
          onAnswered={() =>
            qc.invalidateQueries({ queryKey: ['generation', projectId, workflowId] })
          }
        />
      ) : state === 'COMPLETED' ? (
        <Banner
          icon={<CheckCircle2 className="text-green-600" size={22} />}
          tone="green"
          title="Generation complete"
          body="New DRAFT test cases were created. Refresh the list to see them."
        />
      ) : state === 'FAILED' ? (
        <Banner
          icon={<AlertTriangle className="text-red-600" size={22} />}
          tone="red"
          title="Generation failed"
          body="The run did not finish. Check AI settings (LiteLLM) and try again."
        />
      ) : state === 'AWAITING_REVIEW' ? (
        <ProposalsReview projectId={projectId} workflowId={workflowId} />
      ) : (
        <>
          <Banner
            icon={<Loader2 className="animate-spin text-purple-600" size={22} />}
            tone="purple"
            title={live && live.chars > 0 ? `Generating… (${live.chars} chars)` : 'Generating…'}
            body={
              live
                ? 'Streaming live from the model — content is still transferring.'
                : 'Claude is working. If it needs clarification it will pause and ask you here.'
            }
          />
          {live && live.preview && (
            <pre className="max-h-48 overflow-y-auto whitespace-pre-wrap break-words rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs font-mono text-slate-600">
              {live.preview}
              <span className="animate-pulse">▌</span>
            </pre>
          )}
        </>
      )}

      {/* Transcript of answered rounds */}
      {answered.length > 0 && (
        <div className="space-y-3">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">
            Q&amp;A history
          </p>
          {answered.map(r => (
            <div key={r.round} className="rounded-lg border border-slate-200 p-3 space-y-2">
              {(r.questions ?? []).map(q => {
                const a = (r.answers ?? []).find(x => x.id === q.id)
                return (
                  <div key={q.id} className="text-sm">
                    <p className="text-slate-700 font-medium">{q.question}</p>
                    <p className="text-slate-500">↳ {a?.answer ?? '—'}</p>
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      )}

      <div className="flex justify-end">
        <button
          onClick={onClose}
          className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
        >
          Close
        </button>
      </div>
    </div>
  )
}

function Banner({
  icon,
  title,
  body,
  tone,
}: {
  icon: React.ReactNode
  title: string
  body: string
  tone: 'green' | 'red' | 'purple' | 'amber'
}) {
  const ring =
    tone === 'green'
      ? 'bg-green-50 border-green-200'
      : tone === 'red'
        ? 'bg-red-50 border-red-200'
        : tone === 'amber'
          ? 'bg-amber-50 border-amber-200'
          : 'bg-purple-50 border-purple-200'
  return (
    <div className={`flex items-start gap-3 rounded-lg border px-4 py-3 ${ring}`}>
      {icon}
      <div>
        <p className="text-sm font-medium text-slate-800">{title}</p>
        <p className="text-xs text-slate-600">{body}</p>
      </div>
    </div>
  )
}

function QuestionForm({
  projectId,
  workflowId,
  round,
  onAnswered,
}: {
  projectId: string
  workflowId: string
  round: ClarificationRound
  onAnswered: () => void
}) {
  const questions = round.questions ?? []
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [err, setErr] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => {
      const payload: ClarificationAnswer[] = questions.map(q => ({
        id: q.id,
        answer: answers[q.id] ?? '',
      }))
      return api.submitGenerationAnswers(projectId, workflowId, payload)
    },
    onSuccess: onAnswered,
    onError: e => setErr((e as Error).message),
  })

  const allAnswered = questions.every(q => (answers[q.id] ?? '').trim().length > 0)

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <MessageCircleQuestion className="text-amber-600" size={18} />
        <p className="text-sm font-medium text-slate-800">
          The agent needs a few answers to continue
        </p>
      </div>
      {err && <p className="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{err}</p>}
      {questions.map(q => (
        <div key={q.id}>
          <label className="block text-sm text-slate-700 mb-1">{q.question}</label>
          {q.kind === 'CHOICE' && q.options && q.options.length > 0 ? (
            <select
              value={answers[q.id] ?? ''}
              onChange={e => setAnswers(a => ({ ...a, [q.id]: e.target.value }))}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            >
              <option value="">Select…</option>
              {q.options.map(opt => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          ) : (
            <textarea
              value={answers[q.id] ?? ''}
              onChange={e => setAnswers(a => ({ ...a, [q.id]: e.target.value }))}
              rows={2}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
          )}
        </div>
      ))}
      <button
        onClick={() => {
          setErr(null)
          mutation.mutate()
        }}
        disabled={!allAnswered || mutation.isPending}
        className="px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-lg hover:bg-purple-700 disabled:opacity-50 flex items-center gap-2"
      >
        {mutation.isPending ? (
          <Loader2 size={14} className="animate-spin" />
        ) : (
          <Sparkles size={14} />
        )}
        Submit answers &amp; continue
      </button>
    </div>
  )
}
