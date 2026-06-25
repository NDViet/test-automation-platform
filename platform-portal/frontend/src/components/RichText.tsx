import { useMemo } from 'react'

/**
 * Renders upstream rich text (e.g. Azure DevOps work-item descriptions, which are HTML)
 * safely and decoded. Sanitizes via the browser DOM (DOMParser) — far safer than regex:
 * drops disallowed elements (script/style/iframe/…), strips event handlers and
 * javascript:/data: URLs, and keeps a readable subset. Plain text (no tags) is rendered
 * with preserved line breaks.
 */

const ALLOWED_TAGS = new Set([
  'A',
  'B',
  'STRONG',
  'I',
  'EM',
  'U',
  'S',
  'P',
  'BR',
  'HR',
  'SPAN',
  'DIV',
  'UL',
  'OL',
  'LI',
  'BLOCKQUOTE',
  'CODE',
  'PRE',
  'H1',
  'H2',
  'H3',
  'H4',
  'H5',
  'H6',
  'TABLE',
  'THEAD',
  'TBODY',
  'TR',
  'TH',
  'TD',
])
const ALLOWED_ATTRS = new Set(['href', 'title', 'colspan', 'rowspan'])

function looksLikeHtml(s: string): boolean {
  return /<[a-z!/][\s\S]*>/i.test(s)
}

function sanitize(html: string): string {
  const doc = new DOMParser().parseFromString(html, 'text/html')

  const walk = (node: Element) => {
    // iterate over a static copy — we mutate during traversal
    Array.from(node.children).forEach(child => {
      if (!ALLOWED_TAGS.has(child.tagName)) {
        // unwrap unknown element: replace it with its text content (keeps readable text)
        child.replaceWith(document.createTextNode(child.textContent ?? ''))
        return
      }
      // strip disallowed / dangerous attributes
      Array.from(child.attributes).forEach(attr => {
        const name = attr.name.toLowerCase()
        if (!ALLOWED_ATTRS.has(name)) {
          child.removeAttribute(attr.name)
          return
        }
        if (name === 'href') {
          const v = attr.value.trim().toLowerCase()
          if (v.startsWith('javascript:') || v.startsWith('data:') || v.startsWith('vbscript:')) {
            child.removeAttribute(attr.name)
          }
        }
      })
      if (child.tagName === 'A') {
        child.setAttribute('target', '_blank')
        child.setAttribute('rel', 'noopener noreferrer')
      }
      walk(child)
    })
  }

  // remove script/style/etc. entirely (textContent of these is not wanted)
  doc.body
    .querySelectorAll('script,style,iframe,object,embed,link,meta,noscript')
    .forEach(el => el.remove())
  walk(doc.body)
  return doc.body.innerHTML
}

export default function RichText({
  children,
  className,
}: {
  children: string | null | undefined
  className?: string
}) {
  const rendered = useMemo(() => {
    if (!children) return null
    return looksLikeHtml(children) ? { html: sanitize(children) } : { text: children }
  }, [children])

  if (!rendered) return null
  if ('text' in rendered) {
    return (
      <div className={`text-sm text-slate-700 whitespace-pre-wrap ${className ?? ''}`}>
        {rendered.text}
      </div>
    )
  }
  return (
    <div
      className={`text-sm text-slate-700 rich-text ${className ?? ''}`}
      dangerouslySetInnerHTML={{ __html: rendered.html }}
    />
  )
}
