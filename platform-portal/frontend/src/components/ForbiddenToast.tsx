import { useEffect, useState } from 'react'
import { ShieldAlert } from 'lucide-react'

/** Event the query/mutation error handler fires on an HTTP 403. */
export const FORBIDDEN_EVENT = 'platform:forbidden'

/**
 * Transient bottom-right banner shown when an API call returns 403 — the friendly
 * counterpart to nav/route gating for actions the UI couldn't pre-hide. Self-dismisses.
 */
export default function ForbiddenToast() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>
    const onForbidden = () => {
      setVisible(true)
      clearTimeout(timer)
      timer = setTimeout(() => setVisible(false), 4500)
    }
    window.addEventListener(FORBIDDEN_EVENT, onForbidden)
    return () => {
      window.removeEventListener(FORBIDDEN_EVENT, onForbidden)
      clearTimeout(timer)
    }
  }, [])

  if (!visible) return null
  return (
    <div className="fixed bottom-4 right-4 z-50 flex max-w-sm items-start gap-3 rounded-lg border border-amber-200 bg-white px-4 py-3 shadow-lg">
      <ShieldAlert size={18} className="mt-0.5 shrink-0 text-amber-600" />
      <div className="text-sm">
        <p className="font-semibold text-slate-800">Not permitted</p>
        <p className="text-slate-500">You don't have permission for that action.</p>
      </div>
    </div>
  )
}
