import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Leaf segment of a backslash-delimited ADO path (Area/Iteration), prefixed with "…\"
 * when the rest is trimmed. e.g. "Product House\Search and Book\Search" → "…\Search".
 */
export function pathLeaf(path: string | null | undefined): string {
  if (!path) return ''
  const parts = path.split('\\')
  const leaf = parts[parts.length - 1]
  return parts.length > 1 ? `…\\${leaf}` : leaf
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

// Color helpers return design-token class pairs (see lib/status.ts for the typed
// StatusVariant equivalents used with the <StatusBadge> primitive).
export function passRateColor(rate: number): string {
  if (rate >= 90) return 'text-success'
  if (rate >= 80) return 'text-warning'
  return 'text-danger'
}

export function passRateBg(rate: number): string {
  if (rate >= 90) return 'bg-success-bg border-success-border'
  if (rate >= 80) return 'bg-warning-bg border-warning-border'
  return 'bg-danger-bg border-danger-border'
}

export function flakinessColor(classification: string): string {
  switch (classification) {
    case 'CRITICAL_FLAKY':
      return 'text-danger bg-danger-bg'
    case 'FLAKY':
      return 'text-warning bg-warning-bg'
    case 'WATCH':
      return 'text-info bg-info-bg'
    default:
      return 'text-success bg-success-bg'
  }
}

export function severityColor(severity: string): string {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
      return 'text-danger bg-danger-bg'
    case 'HIGH':
      return 'text-warning bg-warning-bg'
    case 'MEDIUM':
      return 'text-info bg-info-bg'
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

export function statusColor(status: string): string {
  switch (status) {
    case 'PASSED':
      return 'text-success bg-success-bg'
    case 'FAILED':
      return 'text-danger bg-danger-bg'
    case 'BROKEN':
      return 'text-warning bg-warning-bg'
    case 'SKIPPED':
    default:
      return 'text-neutral bg-neutral-bg'
  }
}

export function relativeTime(isoString: string | null | undefined): string {
  if (!isoString) return '—'
  const now = Date.now()
  const then = new Date(isoString).getTime()
  const diff = now - then
  if (diff < 60_000) return 'just now'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`
  return `${Math.floor(diff / 86_400_000)}d ago`
}
