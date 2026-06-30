import type { StatusVariant } from '@/components/ui/StatusBadge'

/**
 * Domain value → semantic {@link StatusVariant}. One place to decide how a
 * status reads, replacing the per-page `statusColor`/`priorityColor`/… helpers
 * that each returned ad-hoc `text-x bg-y` class pairs.
 */

export function testCaseStatusVariant(status: string): StatusVariant {
  switch (status?.toUpperCase()) {
    case 'APPROVED':
      return 'success'
    case 'UNDER_REVIEW':
      return 'warning'
    case 'DEPRECATED':
      return 'danger'
    case 'DRAFT':
    default:
      return 'neutral'
  }
}

export function runStatusVariant(status: string): StatusVariant {
  switch (status?.toUpperCase()) {
    case 'PASSED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'BROKEN':
      return 'warning'
    case 'SKIPPED':
    default:
      return 'neutral'
  }
}

export function priorityVariant(priority: string): StatusVariant {
  switch (priority?.toUpperCase()) {
    case 'CRITICAL':
      return 'danger'
    case 'HIGH':
      return 'warning'
    case 'MEDIUM':
      return 'info'
    case 'LOW':
    default:
      return 'neutral'
  }
}

export function severityVariant(severity: string): StatusVariant {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
      return 'danger'
    case 'HIGH':
      return 'warning'
    case 'MEDIUM':
      return 'info'
    default:
      return 'neutral'
  }
}

export function automationVariant(status: string): StatusVariant {
  switch (status?.toUpperCase()) {
    case 'PR_MERGED':
      return 'success'
    case 'PR_CREATED':
      return 'primary'
    case 'GENERATING':
      return 'info'
    case 'FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

export function coverageVariant(status: string): StatusVariant {
  switch (status?.toUpperCase()) {
    case 'COVERED':
      return 'success'
    case 'PARTIAL':
      return 'warning'
    case 'NOT_COVERED':
      return 'danger'
    default:
      return 'neutral'
  }
}

export function flakinessVariant(classification: string): StatusVariant {
  switch (classification?.toUpperCase()) {
    case 'CRITICAL_FLAKY':
      return 'danger'
    case 'FLAKY':
      return 'warning'
    case 'WATCH':
      return 'info'
    default:
      return 'success'
  }
}

/** Pass-rate → variant for thresholded coloring (≥90 good, ≥80 watch, else bad). */
export function passRateVariant(rate: number): StatusVariant {
  if (rate >= 90) return 'success'
  if (rate >= 80) return 'warning'
  return 'danger'
}
