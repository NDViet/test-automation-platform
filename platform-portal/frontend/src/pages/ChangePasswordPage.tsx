import { useState } from 'react'
import { KeyRound } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'

/** Forced when the user must change their (bootstrap) password before continuing. */
export default function ChangePasswordPage({ forced = false }: { forced?: boolean }) {
  const { setUser, logout } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (next.length < 8) return setError('New password must be at least 8 characters')
    if (next !== confirm) return setError('Passwords do not match')
    setBusy(true)
    try {
      const me = await api.authChangePassword(current, next)
      setUser(me)
    } catch (err) {
      setError((err as Error).message || 'Could not change password')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <form
        onSubmit={submit}
        className="w-full max-w-sm bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-4"
      >
        <div className="text-center">
          <h1 className="text-xl font-bold text-slate-900 flex items-center justify-center gap-2">
            <KeyRound size={20} /> Change password
          </h1>
          {forced && (
            <p className="text-sm text-amber-700 mt-1">
              You must set a new password before continuing.
            </p>
          )}
        </div>
        {error && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
            {error}
          </p>
        )}
        <input
          type="password"
          placeholder="Current password"
          value={current}
          onChange={e => setCurrent(e.target.value)}
          autoComplete="current-password"
          className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg"
        />
        <input
          type="password"
          placeholder="New password (min 8 chars)"
          value={next}
          onChange={e => setNext(e.target.value)}
          autoComplete="new-password"
          className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg"
        />
        <input
          type="password"
          placeholder="Confirm new password"
          value={confirm}
          onChange={e => setConfirm(e.target.value)}
          autoComplete="new-password"
          className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg"
        />
        <button
          type="submit"
          disabled={busy || !current || !next}
          className="w-full px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50"
        >
          {busy ? 'Saving…' : 'Change password'}
        </button>
        <button
          type="button"
          onClick={() => void logout()}
          className="w-full text-xs text-slate-500 hover:text-slate-700"
        >
          Sign out
        </button>
      </form>
    </div>
  )
}
