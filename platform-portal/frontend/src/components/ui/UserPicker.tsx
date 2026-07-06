import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { Check, ChevronDown, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { AssignableUser } from '@/lib/types'

/**
 * Searchable member picker. Filters a list of project members by name / username / email as you type
 * and yields only a listed member — there is no free-text value, since every field this backs
 * (Triggered By, Default Assignee, run Assignee) must resolve to a real user.
 *
 * `bind` selects which member field the value maps to:
 *  - `id`       → value is the member's user id (assignee fields; FK to users).
 *  - `username` → value is the member's username (Triggered By).
 * `value` is the currently-bound value ('' = none); `onChange` emits the new bound value ('' when
 * cleared). The list is the already-fetched `AssignableUser[]` (client-side filter is enough at
 * current member counts).
 */
export function UserPicker({
  users,
  value,
  onChange,
  bind,
  placeholder = 'Search by name or email…',
  allowClear = true,
  disabled = false,
  id,
}: {
  users: AssignableUser[]
  value: string
  onChange: (value: string) => void
  bind: 'id' | 'username'
  placeholder?: string
  allowClear?: boolean
  disabled?: boolean
  id?: string
}) {
  const bound = (u: AssignableUser) => (bind === 'id' ? u.id : u.username)
  const selected = useMemo(
    () => users.find(u => bound(u) === value) ?? null,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [users, value, bind],
  )
  const label = (u: AssignableUser) => u.displayName || u.username
  const secondary = (u: AssignableUser) => u.email || u.username

  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [highlight, setHighlight] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const listboxId = useId()

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return users
    return users.filter(u =>
      [u.displayName, u.username, u.email].some(f => (f ?? '').toLowerCase().includes(q)),
    )
  }, [users, query])

  // Close on outside click.
  useEffect(() => {
    if (!open) return
    function onDocClick(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [open])

  // Keep the highlighted row in range as the filtered list changes.
  useEffect(() => {
    setHighlight(h => Math.min(Math.max(h, 0), Math.max(filtered.length - 1, 0)))
  }, [filtered.length])

  function commit(u: AssignableUser) {
    onChange(bound(u))
    setQuery('')
    setOpen(false)
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (disabled) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setHighlight(h => Math.min(h + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight(h => Math.max(h - 1, 0))
    } else if (e.key === 'Enter') {
      if (open && filtered[highlight]) {
        e.preventDefault()
        commit(filtered[highlight])
      }
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div ref={rootRef} className="relative">
      <div
        className={cn(
          'flex items-center gap-1.5 h-9 rounded-md border border-border-strong bg-surface px-2.5',
          'focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/30',
          disabled && 'opacity-60 bg-surface-muted',
        )}
      >
        <input
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={listboxId}
          aria-autocomplete="list"
          autoComplete="off"
          disabled={disabled}
          className="flex-1 min-w-0 bg-transparent text-sm text-fg outline-none placeholder:text-fg-subtle"
          placeholder={selected ? '' : placeholder}
          value={open ? query : selected ? label(selected) : ''}
          onFocus={() => setOpen(true)}
          onChange={e => {
            setQuery(e.target.value)
            setOpen(true)
          }}
          onKeyDown={onKeyDown}
        />
        {allowClear && selected && !disabled && (
          <button
            type="button"
            aria-label="Clear"
            className="text-fg-subtle hover:text-fg shrink-0"
            onClick={() => {
              onChange('')
              setQuery('')
            }}
          >
            <X size={14} />
          </button>
        )}
        <ChevronDown size={14} className="text-fg-subtle shrink-0" />
      </div>

      {open && !disabled && (
        <ul
          id={listboxId}
          role="listbox"
          className="absolute z-50 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-border bg-surface shadow-md py-1"
        >
          {filtered.length === 0 ? (
            <li className="px-3 py-2 text-xs text-fg-subtle">No matching members.</li>
          ) : (
            filtered.map((u, i) => {
              const isSel = selected != null && bound(u) === value
              return (
                <li
                  key={u.id}
                  role="option"
                  aria-selected={isSel}
                  onMouseEnter={() => setHighlight(i)}
                  onMouseDown={e => {
                    // mousedown (not click) so it fires before the input blur closes the list.
                    e.preventDefault()
                    commit(u)
                  }}
                  className={cn(
                    'flex items-center gap-2 px-3 py-1.5 cursor-pointer text-sm',
                    i === highlight ? 'bg-surface-muted' : '',
                  )}
                >
                  <span className="min-w-0 flex-1">
                    <span className="text-fg">{label(u)}</span>
                    <span className="block text-xs text-fg-subtle truncate">{secondary(u)}</span>
                  </span>
                  {isSel && <Check size={14} className="text-primary shrink-0" />}
                </li>
              )
            })
          )}
        </ul>
      )}
    </div>
  )
}
