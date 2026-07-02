import { cn } from '@/lib/utils'
import { LucideIcon } from 'lucide-react'

interface StatCardProps {
  title: string
  value: string | number
  subtitle?: string
  icon?: LucideIcon
  /** Token text color for the value + icon, e.g. 'text-primary', 'text-danger'. */
  colorClass?: string
  trend?: { value: string; positive: boolean }
}

export default function StatCard({
  title,
  value,
  subtitle,
  icon: Icon,
  colorClass = 'text-primary',
  trend,
}: StatCardProps) {
  return (
    <div className="bg-surface rounded-lg border border-border p-4 shadow-xs">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-medium text-fg-muted uppercase tracking-wide">{title}</p>
          <p className={cn('text-3xl font-bold mt-1', colorClass)}>{value}</p>
          {subtitle && <p className="text-xs text-fg-muted mt-1">{subtitle}</p>}
          {trend && (
            <span
              className={cn(
                'text-xs font-medium mt-1 inline-block',
                trend.positive ? 'text-success' : 'text-danger',
              )}
            >
              {trend.positive ? '↑' : '↓'} {trend.value}
            </span>
          )}
        </div>
        {Icon && (
          <div className={cn('p-2 rounded-lg bg-surface-muted', colorClass)}>
            <Icon size={20} />
          </div>
        )}
      </div>
    </div>
  )
}
