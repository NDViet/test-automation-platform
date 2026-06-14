import { useState } from 'react'
import Markdown from './Markdown'

/**
 * Textarea with a Write/Preview toggle — a lightweight stand-in for Kiwi's
 * SimpleMDE editor. Markdown preview uses the shared {@link Markdown} renderer.
 */
export default function MarkdownEditor({
  value,
  onChange,
  rows = 3,
  placeholder,
}: {
  value: string
  onChange: (v: string) => void
  rows?: number
  placeholder?: string
}) {
  const [tab, setTab] = useState<'write' | 'preview'>('write')

  return (
    <div className="border border-slate-200 rounded-lg overflow-hidden focus-within:ring-2 focus-within:ring-blue-500">
      <div className="flex items-center gap-1 border-b border-slate-100 bg-slate-50 px-2 py-1">
        {(['write', 'preview'] as const).map(t => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`px-2 py-0.5 text-xs font-medium rounded ${
              tab === t ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            {t === 'write' ? 'Write' : 'Preview'}
          </button>
        ))}
        <span className="ml-auto text-[10px] text-slate-400 pr-1">Markdown</span>
      </div>
      {tab === 'write' ? (
        <textarea
          value={value}
          onChange={e => onChange(e.target.value)}
          rows={rows}
          placeholder={placeholder}
          className="w-full px-3 py-2 text-sm focus:outline-none resize-y font-mono"
        />
      ) : (
        <div className="px-3 py-2 min-h-[5rem]">
          {value.trim()
            ? <Markdown>{value}</Markdown>
            : <p className="text-xs text-slate-400">Nothing to preview.</p>}
        </div>
      )}
    </div>
  )
}
