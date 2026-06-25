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
      className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-red-700"
    >
      <AlertCircle size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        {title && <p className="text-sm font-semibold">{title}</p>}
        <p className="text-sm">{message}</p>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 inline-flex items-center gap-1.5 rounded-md border border-red-200 bg-white px-2.5 py-1 text-xs font-medium text-red-700 transition-colors hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-500/30"
          >
            <RotateCw size={13} aria-hidden="true" /> Try again
          </button>
        )}
      </div>
    </div>
  )
}
