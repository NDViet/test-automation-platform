import { AlertCircle } from 'lucide-react'

export default function ErrorMessage({ message }: { message: string }) {
  return (
    <div className="flex items-center gap-3 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700">
      <AlertCircle size={18} className="shrink-0" />
      <p className="text-sm">{message}</p>
    </div>
  )
}
