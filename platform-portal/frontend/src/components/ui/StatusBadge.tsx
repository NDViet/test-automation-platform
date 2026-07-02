import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badge = cva(
  'inline-flex items-center gap-1.5 font-medium rounded-full border whitespace-nowrap',
  {
    variants: {
      variant: {
        success: 'text-success bg-success-bg border-success-border',
        warning: 'text-warning bg-warning-bg border-warning-border',
        danger: 'text-danger bg-danger-bg border-danger-border',
        info: 'text-info bg-info-bg border-info-border',
        neutral: 'text-neutral bg-neutral-bg border-neutral-border',
        primary: 'text-primary-subtle-fg bg-primary-subtle border-primary-subtle',
      },
      size: {
        sm: 'px-2 py-0.5 text-[11px]',
        md: 'px-2.5 py-1 text-xs',
      },
    },
    defaultVariants: { variant: 'neutral', size: 'sm' },
  },
)

export type StatusVariant = NonNullable<VariantProps<typeof badge>['variant']>

export interface StatusBadgeProps extends VariantProps<typeof badge> {
  children: React.ReactNode
  /** Leading status dot, inherits the text color. */
  dot?: boolean
  className?: string
}

/**
 * Semantic status pill. The single home for status coloring — pages map their
 * domain value to a {@link StatusVariant} (see lib/status.ts) instead of
 * hand-writing `text-x bg-y` class pairs.
 */
export function StatusBadge({ variant, size, dot, children, className }: StatusBadgeProps) {
  return (
    <span className={cn(badge({ variant, size }), className)}>
      {dot && <span className="w-1.5 h-1.5 rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  )
}
