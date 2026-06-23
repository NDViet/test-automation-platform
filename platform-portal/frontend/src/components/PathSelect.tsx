import { useState, useRef, useEffect } from 'react'
import { pathLeaf, cn } from '@/lib/utils'
import { ChevronDown } from 'lucide-react'

interface Opt { value: string; label: string }

/**
 * Dropdown for long backslash paths (Area / Iteration): the open list shows the FULL
 * path, but the collapsed trigger shows only the trimmed leaf ("…\Leaf"). Native
 * <select> can't do this (collapsed text === option text), hence a small custom control.
 */
export default function PathSelect({
  value, onChange, options, placeholder, leftIcon, className,
}: {
  value: string
  onChange: (v: string) => void
  options: Opt[]
  placeholder: string          // shown for the empty ('') value
  leftIcon?: React.ReactNode
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const selectedRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  // On open, bring the current selection into view so the user doesn't scroll to find it.
  useEffect(() => {
    if (open) selectedRef.current?.scrollIntoView({ block: 'nearest' })
  }, [open])

  const selected = options.find(o => o.value === value)
  const display = value ? pathLeaf(selected?.label ?? value) : placeholder

  return (
    <div ref={ref} className={cn('relative', className)}>
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        title={value ? (selected?.label ?? value) : placeholder}
        className="w-full flex items-center gap-1.5 border border-slate-200 rounded-lg px-2 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        {leftIcon}
        <span className="truncate flex-1 text-left">{display}</span>
        <ChevronDown size={13} className="text-slate-400 shrink-0" />
      </button>
      {open && (
        <ul className="absolute z-50 mt-1 max-h-72 w-max min-w-full max-w-[28rem] overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg py-1 text-sm">
          <li>
            <button type="button" ref={!value ? selectedRef : undefined}
              onClick={() => { onChange(''); setOpen(false) }}
              className={cn('block w-full text-left px-3 py-1.5 hover:bg-slate-50',
                !value ? 'bg-blue-50 text-blue-700' : 'text-slate-700')}>
              {placeholder}
            </button>
          </li>
          {options.map(o => (
            <li key={o.value}>
              <button type="button" ref={o.value === value ? selectedRef : undefined}
                onClick={() => { onChange(o.value); setOpen(false) }}
                className={cn('block w-full text-left px-3 py-1.5 hover:bg-slate-50 whitespace-nowrap',
                  o.value === value ? 'bg-blue-50 text-blue-700' : 'text-slate-700')}>
                {o.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
