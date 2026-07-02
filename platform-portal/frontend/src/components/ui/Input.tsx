import { forwardRef } from 'react'
import { cn } from '@/lib/utils'

export const Input = forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      className={cn(
        'h-9 w-full rounded-md border border-border-strong bg-surface px-3 text-sm text-fg',
        'placeholder:text-fg-subtle transition-colors',
        'focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/30',
        'disabled:opacity-60 disabled:bg-surface-muted',
        className,
      )}
      {...props}
    />
  ),
)
Input.displayName = 'Input'
