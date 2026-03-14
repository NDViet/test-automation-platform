import { cn } from '@/lib/utils'

interface BadgeProps {
  label: string
  colorClass: string
  size?: 'sm' | 'md'
}

export default function Badge({ label, colorClass, size = 'sm' }: BadgeProps) {
  return (
    <span className={cn(
      'inline-flex items-center font-medium rounded-full',
      size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-sm',
      colorClass,
    )}>
      {label}
    </span>
  )
}
