import type { ReactNode } from 'react'
import { Inbox, type LucideIcon } from 'lucide-react'

interface EmptyStateProps {
  /** Icon shown in the muted circle. Defaults to an inbox. */
  icon?: LucideIcon
  title: string
  description?: string
  /** Optional call-to-action (e.g. a "Create" button). */
  action?: ReactNode
  className?: string
}

/**
 * Friendly placeholder for "there is genuinely nothing here yet" — distinct from
 * an error. Use when a request succeeds but returns no rows.
 */
export default function EmptyState({
  icon: Icon = Inbox,
  title,
  description,
  action,
  className = '',
}: EmptyStateProps) {
  return (
    <div
      role="status"
      className={`flex flex-col items-center justify-center px-6 py-12 text-center ${className}`}
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100">
        <Icon size={22} className="text-slate-400" aria-hidden="true" />
      </div>
      <h3 className="mt-4 text-sm font-semibold text-slate-800">{title}</h3>
      {description && (
        <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>
      )}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
