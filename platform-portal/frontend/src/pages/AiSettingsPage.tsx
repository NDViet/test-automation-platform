import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import {
  Save,
  FlaskConical,
  Eye,
  EyeOff,
  CheckCircle,
  XCircle,
  Play,
  Info,
  Plus,
  Trash2,
  RefreshCw,
} from 'lucide-react'
import LiteLlmExport from '@/components/LiteLlmExport'
import type { AiSettingsUpdate, LiteLlmModel } from '@/lib/types'

function Toggle({
  checked,
  disabled,
  onChange,
}: {
  checked: boolean
  disabled?: boolean
  onChange: () => void
}) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={onChange}
      className={`relative inline-flex h-6 w-11 shrink-0 rounded-full border-2 border-transparent transition-colors focus:outline-none disabled:opacity-40 ${
        checked ? 'bg-blue-600' : 'bg-slate-200'
      }`}
    >
      <span
        className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </button>
  )
}

/** A model picker backed by the configured model list, falling back to free text when empty. */
function ModelSelect({
  label,
  hint,
  value,
  models,
  onChange,
}: {
  label: string
  hint: string
  value: string
  models: LiteLlmModel[]
  onChange: (v: string) => void
}) {
  const ids = models.map(m => m.id)
  const known = value === '' || ids.includes(value)
  return (
    <div>
      <label className="block text-sm font-medium text-slate-700 mb-1">{label}</label>
      {models.length > 0 && known ? (
        <select
          value={value}
          onChange={e => onChange(e.target.value)}
          className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {models.map(m => (
            <option key={m.id} value={m.id}>
              {m.label ? `${m.label} (${m.id})` : m.id}
            </option>
          ))}
        </select>
      ) : (
        <input
          type="text"
          value={value}
          onChange={e => onChange(e.target.value)}
          placeholder={hint}
          className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
        />
      )}
      <p className="text-xs text-slate-400 mt-1">{hint}</p>
    </div>
  )
}

export default function AiSettingsPage() {
  const qc = useQueryClient()
  const {
    data: settings,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['ai-settings'],
    queryFn: api.aiSettings,
  })

  const [enabled, setEnabled] = useState(false)
  const [realtimeEnabled, setRealtime] = useState(false)
  const [baseUrl, setBaseUrl] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [showKey, setShowKey] = useState(false)
  const [models, setModels] = useState<LiteLlmModel[]>([])
  const [modelAnalysis, setModelAnalysis] = useState('')
  const [modelStandard, setModelStandard] = useState('')
  const [modelComplex, setModelComplex] = useState('')
  const [modelSummarizer, setModelSummarizer] = useState('')
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [analyseResult, setAnalyseResult] = useState<{ queued: number } | null>(null)

  useEffect(() => {
    if (settings) {
      setEnabled(settings.enabled)
      setRealtime(settings.realtimeEnabled)
      setBaseUrl(settings.liteLlmBaseUrl)
      setModels(settings.models ?? [])
      setModelAnalysis(settings.modelAnalysis)
      setModelStandard(settings.modelStandard)
      setModelComplex(settings.modelComplex)
      setModelSummarizer(settings.modelSummarizer)
    }
  }, [settings])

  const saveMutation = useMutation({
    mutationFn: () => {
      const body: AiSettingsUpdate = {
        enabled,
        realtimeEnabled,
        liteLlmBaseUrl: baseUrl.trim(),
        models: models.filter(m => m.id.trim() !== ''),
        modelAnalysis,
        modelStandard,
        modelComplex,
        modelSummarizer,
      }
      if (apiKey.trim()) body.liteLlmApiKey = apiKey.trim()
      return api.updateAiSettings(body)
    },
    onSuccess: () => {
      setApiKey('')
      void qc.invalidateQueries({ queryKey: ['ai-settings'] })
    },
  })

  const testMutation = useMutation({
    mutationFn: () =>
      api.testAiConnection({
        liteLlmBaseUrl: baseUrl.trim() || undefined,
        ...(apiKey.trim() ? { liteLlmApiKey: apiKey.trim() } : {}),
      }),
    onSuccess: result => {
      setTestResult(result)
      // Replace the model list with exactly what the gateway allows for this key/team, so the
      // per-role pickers below select from real models instead of free-text or stale defaults.
      if (result.success && result.models && result.models.length > 0) {
        setModels(result.models)
      }
    },
    onError: () =>
      setTestResult({ success: false, message: 'Request failed — check console for details' }),
  })

  const fetchModelsMutation = useMutation({
    mutationFn: () =>
      api.fetchAiModels({
        liteLlmBaseUrl: baseUrl.trim() || undefined,
        ...(apiKey.trim() ? { liteLlmApiKey: apiKey.trim() } : {}),
      }),
    onSuccess: result => {
      setTestResult(result)
      if (result.success && result.models && result.models.length > 0) {
        setModels(result.models)
      }
    },
    onError: () =>
      setTestResult({ success: false, message: 'Request failed — check console for details' }),
  })

  const analyseNowMutation = useMutation({
    mutationFn: () => api.analyseNow(24),
    onSuccess: result => setAnalyseResult({ queued: result.queued }),
    onError: () => setAnalyseResult({ queued: -1 }),
  })

  if (isLoading) return <LoadingSpinner message="Loading AI settings…" />
  if (error)
    return <ErrorMessage message="Failed to load AI settings." onRetry={() => void refetch()} />

  const updateModel = (i: number, patch: Partial<LiteLlmModel>) =>
    setModels(ms => ms.map((m, idx) => (idx === i ? { ...m, ...patch } : m)))

  return (
    <div className="space-y-6 max-w-2xl h-full min-h-0 overflow-y-auto pr-1">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">AI Settings</h1>
        <p className="text-sm text-slate-500 mt-1">
          The platform reaches every model through a single LiteLLM gateway. Point it at your
          LiteLLM endpoint and map the models used for analysis and agent workflows.
        </p>
      </div>

      <div className="flex gap-3 bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-sm text-blue-800">
        <Info size={16} className="shrink-0 mt-0.5" />
        <div>
          LiteLLM is OpenAI-compatible — the same base URL + key + model list you use in OpenCode,
          Claude Code Router, or VS Code chat. Models route to Claude / GPT / Gemini / etc. inside
          LiteLLM by their id.
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-100">
        {/* Enable / Realtime */}
        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">AI Analysis</p>
            <p className="text-xs text-slate-500 mt-0.5">
              When enabled, failures are automatically analysed and classified
            </p>
          </div>
          <Toggle checked={enabled} onChange={() => setEnabled(v => !v)} />
        </div>
        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">Real-time Analysis</p>
            <p className="text-xs text-slate-500 mt-0.5">
              Classify failures immediately on ingestion. When off, the nightly batch (02:00 UTC)
              handles them.
            </p>
          </div>
          <Toggle
            checked={realtimeEnabled && enabled}
            disabled={!enabled}
            onChange={() => setRealtime(v => !v)}
          />
        </div>

        {/* Gateway connection */}
        <div className="px-5 py-4 space-y-4">
          <p className="text-sm font-medium text-slate-700">LiteLLM Gateway</p>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Base URL</label>
            <input
              type="text"
              value={baseUrl}
              onChange={e => setBaseUrl(e.target.value)}
              placeholder="http://litellm:4000/v1"
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
            />
            <p className="text-xs text-slate-400 mt-1">
              OpenAI-compatible endpoint of your LiteLLM proxy
            </p>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">
              API Key
              {settings?.liteLlmKeySet ? (
                <span className="ml-2 text-xs font-normal text-green-600">
                  (configured — enter new value to replace)
                </span>
              ) : (
                <span className="ml-2 text-xs font-normal text-slate-400">(not set)</span>
              )}
            </label>
            <div className="relative">
              <input
                type={showKey ? 'text' : 'password'}
                value={apiKey}
                onChange={e => setApiKey(e.target.value)}
                placeholder={
                  settings?.liteLlmKeySet ? '••••••••  (leave blank to keep current)' : 'sk-…'
                }
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 pr-10 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
              />
              <button
                type="button"
                onClick={() => setShowKey(v => !v)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showKey ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>
        </div>

        {/* Model list */}
        <div className="px-5 py-4 space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium text-slate-700">Models</p>
            <div className="flex items-center gap-3">
              <button
                onClick={() => {
                  setTestResult(null)
                  void fetchModelsMutation.mutate()
                }}
                disabled={fetchModelsMutation.isPending}
                className="flex items-center gap-1.5 text-xs font-medium text-blue-600 hover:text-blue-700 disabled:opacity-50"
              >
                <RefreshCw
                  size={14}
                  className={fetchModelsMutation.isPending ? 'animate-spin' : ''}
                />
                {fetchModelsMutation.isPending ? 'Fetching…' : 'Fetch from gateway'}
              </button>
              <button
                onClick={() => setModels(ms => [...ms, { id: '', label: '' }])}
                className="flex items-center gap-1.5 text-xs font-medium text-blue-600 hover:text-blue-700"
              >
                <Plus size={14} /> Add model
              </button>
            </div>
          </div>
          {models.length === 0 && (
            <p className="text-xs text-slate-400">
              No models yet — click <span className="font-medium">Test Connection</span> to load the
              models your key is allowed to use, or add their ids manually (e.g. claude-sonnet-4-6,
              gpt-4o). The per-role pickers below select from this list.
            </p>
          )}
          {models.map((m, i) => (
            <div key={i} className="flex items-center gap-2">
              <input
                type="text"
                value={m.id}
                onChange={e => updateModel(i, { id: e.target.value })}
                placeholder="model id (e.g. gpt-4o)"
                className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
              />
              <input
                type="text"
                value={m.label ?? ''}
                onChange={e => updateModel(i, { label: e.target.value })}
                placeholder="label (optional)"
                className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button
                onClick={() => setModels(ms => ms.filter((_, idx) => idx !== i))}
                className="text-slate-400 hover:text-red-600"
                aria-label="Remove model"
              >
                <Trash2 size={16} />
              </button>
            </div>
          ))}
        </div>

        {/* Role → model mapping */}
        <div className="px-5 py-4 grid grid-cols-1 sm:grid-cols-2 gap-4">
          <ModelSelect
            label="Failure analysis"
            hint="Classifies test failures"
            value={modelAnalysis}
            models={models}
            onChange={setModelAnalysis}
          />
          <ModelSelect
            label="Agent — standard"
            hint="Fast tier for structured agent steps"
            value={modelStandard}
            models={models}
            onChange={setModelStandard}
          />
          <ModelSelect
            label="Agent — complex"
            hint="Deep reasoning: test/code generation, healing"
            value={modelComplex}
            models={models}
            onChange={setModelComplex}
          />
          <ModelSelect
            label="Agent — summarizer"
            hint="Cheap tier for step summaries"
            value={modelSummarizer}
            models={models}
            onChange={setModelSummarizer}
          />
        </div>

        {testResult && (
          <div
            className={`px-5 py-3 flex items-center gap-2 text-sm ${
              testResult.success ? 'text-green-700 bg-green-50' : 'text-red-700 bg-red-50'
            }`}
          >
            {testResult.success ? (
              <CheckCircle size={16} className="shrink-0" />
            ) : (
              <XCircle size={16} className="shrink-0" />
            )}
            {testResult.message}
          </div>
        )}

        <div className="px-5 py-4 flex items-center gap-3">
          <button
            onClick={() => {
              setTestResult(null)
              void testMutation.mutate()
            }}
            disabled={testMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 text-sm font-medium border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50 transition-colors"
          >
            <FlaskConical size={15} />
            {testMutation.isPending ? 'Testing…' : 'Test Connection'}
          </button>
          <button
            onClick={() => void saveMutation.mutate()}
            disabled={saveMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            <Save size={15} />
            {saveMutation.isPending ? 'Saving…' : 'Save Settings'}
          </button>
          {saveMutation.isSuccess && (
            <span className="text-xs text-green-600 flex items-center gap-1">
              <CheckCircle size={13} /> Saved
            </span>
          )}
          {saveMutation.isError && (
            <span className="text-xs text-red-600">Failed to save — please try again.</span>
          )}
        </div>
      </div>

      {/* Export gateway config for external tools */}
      <LiteLlmExport baseUrl={baseUrl} models={models} />

      {/* On-Demand Analysis */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-100">
        <div className="px-5 py-4">
          <p className="text-sm font-medium text-slate-900">On-Demand Analysis</p>
          <p className="text-xs text-slate-500 mt-0.5">
            Immediately classify all unanalysed failures from the last 24 hours without waiting for
            the nightly batch.
          </p>
        </div>
        <div className="px-5 py-4 flex items-center gap-4">
          <button
            onClick={() => {
              setAnalyseResult(null)
              void analyseNowMutation.mutate()
            }}
            disabled={!enabled || analyseNowMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-slate-800 text-white text-sm font-medium rounded-lg hover:bg-slate-700 disabled:opacity-50 transition-colors"
          >
            <Play size={14} />
            {analyseNowMutation.isPending ? 'Queuing…' : 'Analyze Now'}
          </button>
          {analyseResult !== null &&
            (analyseResult.queued >= 0 ? (
              <span className="text-sm text-green-700 flex items-center gap-1.5">
                <CheckCircle size={15} className="shrink-0" />
                {analyseResult.queued === 0
                  ? 'No unanalysed failures found in the last 24 hours'
                  : `${analyseResult.queued} failure${analyseResult.queued !== 1 ? 's' : ''} queued for analysis`}
              </span>
            ) : (
              <span className="text-sm text-red-600 flex items-center gap-1.5">
                <XCircle size={15} className="shrink-0" />
                Failed to trigger analysis — check platform-ai logs
              </span>
            ))}
        </div>
      </div>
    </div>
  )
}
