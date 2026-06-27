import { useState } from 'react'
import { Copy, Check } from 'lucide-react'
import type { LiteLlmModel } from '@/lib/types'

/**
 * Generates ready-to-paste LiteLLM client config for the tools developers already use, so the same
 * gateway + models power the platform and a developer's local tooling. The API key is never embedded
 * — every snippet references a `LITELLM_API_KEY` placeholder the user supplies in their own env.
 *
 * Snippets are built from JS objects via JSON.stringify, so they are always valid JSON.
 */
function buildConfigs(baseUrl: string, models: LiteLlmModel[]) {
  const url = (baseUrl || 'http://localhost:4000/v1').replace(/\/+$/, '')
  const named = models.filter(m => m.id.trim() !== '')
  const ids = named.map(m => m.id)
  const first = ids[0] ?? 'claude-sonnet-4-6'

  const opencode = {
    provider: {
      litellm: {
        npm: '@ai-sdk/openai-compatible',
        name: 'LiteLLM',
        options: { baseURL: url, apiKey: '{env:LITELLM_API_KEY}' },
        models: Object.fromEntries(named.map(m => [m.id, { name: m.label || m.id }])),
      },
    },
  }

  const claudeRouter = {
    Providers: [
      {
        name: 'litellm',
        api_base_url: `${url}/chat/completions`,
        api_key: '$LITELLM_API_KEY',
        models: ids,
      },
    ],
    Router: { default: `litellm,${first}` },
  }

  // VS Code chat "OpenAI-compatible" model provider (settings.json). Field names vary by
  // extension/version — adjust to your installed chat provider if needed.
  const vscode = {
    'chat.modelProviders': {
      litellm: {
        type: 'openai-compatible',
        baseURL: url,
        apiKeyEnv: 'LITELLM_API_KEY',
        models: ids,
      },
    },
  }

  return [
    { key: 'opencode', label: 'OpenCode', file: 'opencode.json', json: opencode },
    {
      key: 'claude-router',
      label: 'Claude Code Router',
      file: '~/.claude-code-router/config.json',
      json: claudeRouter,
    },
    { key: 'vscode', label: 'VS Code chat', file: 'settings.json', json: vscode },
  ]
}

function Snippet({ file, json }: { file: string; json: unknown }) {
  const [copied, setCopied] = useState(false)
  const text = JSON.stringify(json, null, 2)
  const copy = () => {
    void navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }
  return (
    <div className="rounded-lg border border-slate-200 overflow-hidden">
      <div className="flex items-center justify-between bg-slate-50 px-3 py-1.5 border-b border-slate-200">
        <span className="font-mono text-xs text-slate-500">{file}</span>
        <button
          onClick={copy}
          className="flex items-center gap-1.5 text-xs font-medium text-slate-600 hover:text-blue-600"
        >
          {copied ? <Check size={13} className="text-green-600" /> : <Copy size={13} />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <pre className="overflow-x-auto px-3 py-2 text-xs text-slate-700 bg-white">{text}</pre>
    </div>
  )
}

export default function LiteLlmExport({
  baseUrl,
  models,
}: {
  baseUrl: string
  models: LiteLlmModel[]
}) {
  const [open, setOpen] = useState<string>('opencode')
  const configs = buildConfigs(baseUrl, models)

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
      <div className="px-5 py-4 border-b border-slate-100">
        <p className="text-sm font-medium text-slate-900">Use this gateway in your tools</p>
        <p className="text-xs text-slate-500 mt-0.5">
          Point OpenCode, Claude Code Router, or VS Code chat at the same LiteLLM endpoint and
          models. Keys are never included — set <span className="font-mono">LITELLM_API_KEY</span>{' '}
          in your env.
        </p>
      </div>

      <div className="flex gap-1 px-5 pt-3">
        {configs.map(c => (
          <button
            key={c.key}
            onClick={() => setOpen(c.key)}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
              open === c.key
                ? 'bg-blue-600 text-white'
                : 'bg-white text-slate-600 hover:bg-slate-50 border border-slate-200'
            }`}
          >
            {c.label}
          </button>
        ))}
      </div>

      <div className="px-5 py-3">
        {configs
          .filter(c => c.key === open)
          .map(c => (
            <Snippet key={c.key} file={c.file} json={c.json} />
          ))}
      </div>
    </div>
  )
}
