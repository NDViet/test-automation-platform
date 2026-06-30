import { forwardRef } from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'

export const Select = forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, children, ...props }, ref) => (
    <div className="relative inline-flex w-full">
      <select
        ref={ref}
        className={cn(
          'h-9 w-full appearance-none rounded-md border border-border-strong bg-surface pl-3 pr-9 text-sm text-fg',
          'focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/30',
          'disabled:opacity-60 disabled:bg-surface-muted',
          className,
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown
        size={15}
        className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-fg-subtle"
        aria-hidden
      />
    </div>
  ),
)
Select.displayName = 'Select'
