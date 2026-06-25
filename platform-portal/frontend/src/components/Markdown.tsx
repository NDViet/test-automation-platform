import { useMemo } from 'react'

/**
 * Minimal, dependency-free, XSS-safe Markdown renderer. Escapes HTML first, then
 * applies a safe subset: headings, bold/italic, inline code, fenced code blocks,
 * links, unordered/ordered lists, blockquotes, and line breaks. Good enough to
 * mirror Kiwi's markdown-rendered test text without pulling in a parser.
 */
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function inline(s: string): string {
  return (
    s
      // inline code
      .replace(
        /`([^`]+)`/g,
        '<code class="px-1 py-0.5 rounded bg-slate-100 text-[0.85em] font-mono">$1</code>',
      )
      // bold
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      // italic (single * or _)
      .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
      .replace(/(^|[^_])_([^_\n]+)_/g, '$1<em>$2</em>')
      // links [text](url) — only http(s) urls
      .replace(
        /\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g,
        '<a href="$2" target="_blank" rel="noopener noreferrer" class="text-blue-600 hover:underline">$1</a>',
      )
  )
}

function toHtml(md: string): string {
  const lines = md.replace(/\r\n/g, '\n').split('\n')
  const out: string[] = []
  let i = 0
  let listType: 'ul' | 'ol' | null = null

  const closeList = () => {
    if (listType) {
      out.push(`</${listType}>`)
      listType = null
    }
  }

  while (i < lines.length) {
    const raw = lines[i]

    // fenced code block
    if (/^```/.test(raw)) {
      closeList()
      const body: string[] = []
      i++
      while (i < lines.length && !/^```/.test(lines[i])) {
        body.push(escapeHtml(lines[i]))
        i++
      }
      i++ // skip closing fence
      out.push(
        `<pre class="bg-slate-900 text-slate-100 rounded-lg p-3 text-xs overflow-x-auto my-2"><code>${body.join('\n')}</code></pre>`,
      )
      continue
    }

    const line = escapeHtml(raw)

    // headings
    const h = line.match(/^(#{1,4})\s+(.*)$/)
    if (h) {
      closeList()
      const level = h[1].length
      const sizes = ['text-lg', 'text-base', 'text-sm', 'text-sm']
      out.push(
        `<h${level} class="font-semibold ${sizes[level - 1]} text-slate-900 mt-3 mb-1">${inline(h[2])}</h${level}>`,
      )
      i++
      continue
    }

    // blockquote
    if (/^>\s?/.test(line)) {
      closeList()
      out.push(
        `<blockquote class="border-l-2 border-slate-300 pl-3 text-slate-500 my-1">${inline(line.replace(/^>\s?/, ''))}</blockquote>`,
      )
      i++
      continue
    }

    // unordered list
    if (/^[-*]\s+/.test(line)) {
      if (listType !== 'ul') {
        closeList()
        out.push('<ul class="list-disc pl-5 my-1 space-y-0.5">')
        listType = 'ul'
      }
      out.push(`<li>${inline(line.replace(/^[-*]\s+/, ''))}</li>`)
      i++
      continue
    }
    // ordered list
    if (/^\d+\.\s+/.test(line)) {
      if (listType !== 'ol') {
        closeList()
        out.push('<ol class="list-decimal pl-5 my-1 space-y-0.5">')
        listType = 'ol'
      }
      out.push(`<li>${inline(line.replace(/^\d+\.\s+/, ''))}</li>`)
      i++
      continue
    }

    // blank line
    if (line.trim() === '') {
      closeList()
      i++
      continue
    }

    // paragraph
    closeList()
    out.push(`<p class="my-1 leading-relaxed">${inline(line)}</p>`)
    i++
  }
  closeList()
  return out.join('')
}

export default function Markdown({
  children,
  className,
}: {
  children: string | null | undefined
  className?: string
}) {
  const html = useMemo(() => (children ? toHtml(children) : ''), [children])
  if (!children) return null
  return (
    <div
      className={`text-sm text-slate-700 markdown-body ${className ?? ''}`}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  )
}
