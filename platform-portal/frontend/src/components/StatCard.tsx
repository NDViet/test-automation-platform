import { cn } from '@/lib/utils'
import { LucideIcon } from 'lucide-react'

interface StatCardProps {
  title: string
  value: string | number
  subtitle?: string
  icon?: LucideIcon
  colorClass?: string
  trend?: { value: string; positive: boolean }
}

export default function StatCard({
  title, value, subtitle, icon: Icon, colorClass = 'text-blue-600', trend,
}: StatCardProps) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">{title}</p>
          <p className={cn('text-3xl font-bold mt-1', colorClass)}>{value}</p>
          {subtitle && <p className="text-xs text-slate-500 mt-1">{subtitle}</p>}
          {trend && (
            <span className={cn('text-xs font-medium mt-1 inline-block',
              trend.positive ? 'text-green-600' : 'text-red-600')}>
              {trend.positive ? '↑' : '↓'} {trend.value}
            </span>
          )}
        </div>
        {Icon && (
          <div className={cn('p-2 rounded-lg bg-slate-50', colorClass)}>
            <Icon size={20} />
          </div>
        )}
      </div>
    </div>
  )
}
