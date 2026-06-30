import { cn } from '@/lib/utils'

interface PageHeaderProps {
  title: string
  description?: React.ReactNode
  /** Right-aligned actions (buttons, filters). */
  actions?: React.ReactNode
  /** Optional leading icon, sized ~20px. */
  icon?: React.ReactNode
  className?: string
}

/**
 * Consistent page title block used across every page: an h1 with optional
 * description, a leading icon, and a right-aligned actions slot. Replaces the
 * per-page hand-rolled header markup so spacing and type scale stay uniform.
 */
export function PageHeader({ title, description, actions, icon, className }: PageHeaderProps) {
  return (
    <div className={cn('flex items-start justify-between gap-4 mb-5', className)}>
      <div className="flex items-start gap-3 min-w-0">
        {icon && <div className="shrink-0 mt-0.5 text-fg-muted">{icon}</div>}
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight text-fg truncate">{title}</h1>
          {description && <p className="text-sm text-fg-muted mt-1">{description}</p>}
        </div>
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  )
}
