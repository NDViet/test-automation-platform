import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDuration(ms: number | null | undefined): string {
  if (!ms) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
  const mins = Math.floor(ms / 60_000)
  const secs = Math.floor((ms % 60_000) / 1000)
  return `${mins}m ${secs}s`
}

export function formatPassRate(rate: number | null | undefined): string {
  if (rate == null) return '—'
  return `${rate.toFixed(1)}%`
}

export function passRateColor(rate: number): string {
  if (rate >= 90) return 'text-green-600'
  if (rate >= 80) return 'text-yellow-600'
  return 'text-red-600'
}

export function passRateBg(rate: number): string {
  if (rate >= 90) return 'bg-green-50 border-green-200'
  if (rate >= 80) return 'bg-yellow-50 border-yellow-200'
  return 'bg-red-50 border-red-200'
}

export function flakinessColor(classification: string): string {
  switch (classification) {
    case 'CRITICAL_FLAKY': return 'text-red-700 bg-red-100'
    case 'FLAKY':           return 'text-orange-700 bg-orange-100'
    case 'WATCH':           return 'text-yellow-700 bg-yellow-100'
    default:                return 'text-green-700 bg-green-100'
  }
}

export function severityColor(severity: string): string {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL': return 'text-red-700 bg-red-100'
    case 'HIGH':     return 'text-orange-700 bg-orange-100'
    case 'MEDIUM':   return 'text-yellow-700 bg-yellow-100'
    default:         return 'text-blue-700 bg-blue-100'
  }
}

export function statusColor(status: string): string {
  switch (status) {
    case 'PASSED':  return 'text-green-700 bg-green-100'
    case 'FAILED':  return 'text-red-700 bg-red-100'
    case 'BROKEN':  return 'text-orange-700 bg-orange-100'
    case 'SKIPPED': return 'text-gray-600 bg-gray-100'
    default:        return 'text-gray-600 bg-gray-100'
  }
}

export function relativeTime(isoString: string | null | undefined): string {
  if (!isoString) return '—'
  const now = Date.now()
  const then = new Date(isoString).getTime()
  const diff = now - then
  if (diff < 60_000)  return 'just now'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`
  return `${Math.floor(diff / 86_400_000)}d ago`
}
