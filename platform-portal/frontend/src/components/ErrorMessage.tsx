import { AlertCircle, RotateCw } from 'lucide-react'

interface ErrorMessageProps {
  message: string
  /** Optional bold heading shown above the message. */
  title?: string
  /** When provided, renders a "Try again" button that calls this. */
  onRetry?: () => void
}

export default function ErrorMessage({ message, title, onRetry }: ErrorMessageProps) {
  return (
    <div
      role="alert"
      className="flex items-start gap-3 rounded-lg border border-danger-border bg-danger-bg p-4 text-danger"
    >
      <AlertCircle size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        {title && <p className="text-sm font-semibold">{title}</p>}
        <p className="text-sm">{message}</p>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 inline-flex items-center gap-1.5 rounded-md border border-danger-border bg-surface px-2.5 py-1 text-xs font-medium text-danger transition-colors hover:bg-danger-bg focus:outline-none focus:ring-2 focus:ring-danger/30"
          >
            <RotateCw size={13} aria-hidden="true" /> Try again
          </button>
        )}
      </div>
    </div>
  )
}
