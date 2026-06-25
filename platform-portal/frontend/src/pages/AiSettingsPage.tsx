import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Save, FlaskConical, Eye, EyeOff, CheckCircle, XCircle, Play, Info } from 'lucide-react'
import type { AiSettingsUpdate } from '@/lib/types'

function KeyField({
  label,
  hint,
  isSet,
  value,
  onChange,
  show,
  onToggleShow,
}: {
  label: string
  hint: string
  isSet: boolean
  value: string
  onChange: (v: string) => void
  show: boolean
  onToggleShow: () => void
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-slate-700 mb-1">
        {label}
        {isSet && (
          <span className="ml-2 text-xs font-normal text-green-600">
            (configured — enter new value to replace)
          </span>
        )}
        {!isSet && <span className="ml-2 text-xs font-normal text-slate-400">(not set)</span>}
      </label>
      <div className="relative">
        <input
          type={show ? 'text' : 'password'}
          value={value}
          onChange={e => onChange(e.target.value)}
          placeholder={isSet ? '••••••••  (leave blank to keep current)' : hint}
          className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 pr-10 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
        />
        <button
          type="button"
          onClick={onToggleShow}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
        >
          {show ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
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
  const [provider, setProvider] = useState<'anthropic' | 'openai'>('anthropic')
  const [model, setModel] = useState('')
  const [anthropicKey, setAnthropicKey] = useState('')
  const [openaiKey, setOpenaiKey] = useState('')
  const [showAnthropicKey, setShowAnthr] = useState(false)
  const [showOpenaiKey, setShowOpenai] = useState(false)
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [analyseResult, setAnalyseResult] = useState<{ queued: number } | null>(null)

  useEffect(() => {
    if (settings) {
      setEnabled(settings.enabled)
      setRealtime(settings.realtimeEnabled)
      setProvider(settings.provider)
      setModel(settings.model)
    }
  }, [settings])

  const defaultModel = provider === 'openai' ? 'gpt-4o' : 'claude-sonnet-4-6'

  const saveMutation = useMutation({
    mutationFn: () => {
      const body: AiSettingsUpdate = {
        enabled,
        realtimeEnabled,
        provider,
        model: model || defaultModel,
      }
      if (anthropicKey.trim()) body.anthropicApiKey = anthropicKey.trim()
      if (openaiKey.trim()) body.openaiApiKey = openaiKey.trim()
      return api.updateAiSettings(body)
    },
    onSuccess: () => {
      setAnthropicKey('')
      setOpenaiKey('')
      void qc.invalidateQueries({ queryKey: ['ai-settings'] })
    },
  })

  const analyseNowMutation = useMutation({
    mutationFn: () => api.analyseNow(24),
    onSuccess: result => setAnalyseResult({ queued: result.queued }),
    onError: () => setAnalyseResult({ queued: -1 }),
  })

  const testMutation = useMutation({
    mutationFn: () => {
      const testKey =
        provider === 'anthropic' ? anthropicKey.trim() || undefined : openaiKey.trim() || undefined
      return api.testAiConnection({
        provider,
        model: model || defaultModel,
        ...(testKey ? { apiKey: testKey } : {}),
      })
    },
    onSuccess: result => setTestResult(result),
    onError: () =>
      setTestResult({ success: false, message: 'Request failed — check console for details' }),
  })

  if (isLoading) return <LoadingSpinner message="Loading AI settings…" />
  if (error)
    return <ErrorMessage message="Failed to load AI settings." onRetry={() => void refetch()} />

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">AI Settings</h1>
        <p className="text-sm text-slate-500 mt-1">
          Configure AI providers for failure analysis and agentic workflows
        </p>
      </div>

      {/* Callout: Anthropic is required for agent features */}
      <div className="flex gap-3 bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-sm text-blue-800">
        <Info size={16} className="shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold">Anthropic (Claude) key is required</span> for all agent
          features: test case generation, automation code generation, and PR analysis. OpenAI is
          only used for failure classification when selected as the active provider.
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm divide-y divide-slate-100">
        {/* Enable / Disable */}
        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">AI Analysis</p>
            <p className="text-xs text-slate-500 mt-0.5">
              When enabled, failures are automatically analysed and classified
            </p>
          </div>
          <button
            role="switch"
            aria-checked={enabled}
            onClick={() => setEnabled(v => !v)}
            className={`relative inline-flex h-6 w-11 shrink-0 rounded-full border-2 border-transparent transition-colors focus:outline-none ${
              enabled ? 'bg-blue-600' : 'bg-slate-200'
            }`}
          >
            <span
              className={`inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition-transform ${
                enabled ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Real-time analysis */}
        <div className="px-5 py-4 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-900">Real-time Analysis</p>
            <p className="text-xs text-slate-500 mt-0.5">
              Classify failures immediately on ingestion. When off, failures are analysed by the
              nightly batch job (02:00 UTC).
            </p>
          </div>
          <button
            role="switch"
            aria-checked={realtimeEnabled}
            onClick={() => setRealtime(v => !v)}
            disabled={!enabled}
            className={`relative inline-flex h-6 w-11 shrink-0 rounded-full border-2 border-transparent transition-colors focus:outline-none disabled:opacity-40 ${
              realtimeEnabled && enabled ? 'bg-blue-600' : 'bg-slate-200'
            }`}
          >
            <span
              className={`inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition-transform ${
                realtimeEnabled && enabled ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Active provider for failure classification */}
        <div className="px-5 py-4">
          <label className="block text-sm font-medium text-slate-700 mb-2">
            Failure Classification Provider
          </label>
          <p className="text-xs text-slate-500 mb-3">
            Which AI provider to use for automated failure classification
          </p>
          <div className="flex gap-3">
            {(['anthropic', 'openai'] as const).map(p => (
              <button
                key={p}
                onClick={() => {
                  setProvider(p)
                  setModel('')
                }}
                className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  provider === p
                    ? 'bg-blue-600 text-white border-blue-600'
                    : 'bg-white text-slate-700 border-slate-200 hover:border-blue-300'
                }`}
              >
                {p === 'anthropic' ? 'Anthropic (Claude)' : 'OpenAI'}
              </button>
            ))}
          </div>
        </div>

        {/* Model */}
        <div className="px-5 py-4">
          <label className="block text-sm font-medium text-slate-700 mb-2">Model</label>
          <input
            type="text"
            value={model}
            onChange={e => setModel(e.target.value)}
            placeholder={defaultModel}
            className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <p className="text-xs text-slate-400 mt-1">
            {provider === 'anthropic'
              ? 'e.g. claude-sonnet-4-6, claude-opus-4-6'
              : 'e.g. gpt-4o, gpt-4o-mini'}
          </p>
        </div>

        {/* API Keys — separate fields */}
        <div className="px-5 py-4 space-y-4">
          <p className="text-sm font-medium text-slate-700">API Keys</p>

          <KeyField
            label="Anthropic API Key"
            hint="sk-ant-..."
            isSet={settings?.anthropicKeySet ?? false}
            value={anthropicKey}
            onChange={setAnthropicKey}
            show={showAnthropicKey}
            onToggleShow={() => setShowAnthr(v => !v)}
          />

          <KeyField
            label="OpenAI API Key"
            hint="sk-..."
            isSet={settings?.openaiKeySet ?? false}
            value={openaiKey}
            onChange={setOpenaiKey}
            show={showOpenaiKey}
            onToggleShow={() => setShowOpenai(v => !v)}
          />
        </div>

        {/* Test connection result */}
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

        {/* Actions */}
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
