import { cn } from '@/lib/utils'

interface TableProps extends React.TableHTMLAttributes<HTMLTableElement> {
  /** Classes for the scroll container that wraps the table. */
  containerClassName?: string
}

/**
 * Data table. Always wraps the table in an `overflow-x-auto` container so wide
 * tables scroll inside themselves instead of pushing the page horizontally.
 */
export function Table({ className, containerClassName, ...props }: TableProps) {
  return (
    <div className={cn('w-full overflow-x-auto', containerClassName)}>
      <table className={cn('w-full border-collapse text-sm', className)} {...props} />
    </div>
  )
}

export function THead({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn('bg-surface-muted', className)} {...props} />
}

export function TBody({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn('divide-y divide-border', className)} {...props} />
}

export function TR({ className, ...props }: React.HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn('transition-colors hover:bg-surface-muted/60', className)} {...props} />
}

export function TH({ className, ...props }: React.ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn(
        'px-3 py-2 text-left text-[11px] font-semibold uppercase tracking-wide text-fg-muted whitespace-nowrap',
        className,
      )}
      {...props}
    />
  )
}

export function TD({ className, ...props }: React.TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-3 py-2 align-middle text-fg', className)} {...props} />
}
