import { useState } from 'react'
import { KeyRound } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { Button, Input } from '@/components/ui'

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
    <div className="min-h-screen flex items-center justify-center bg-canvas p-4">
      <form
        onSubmit={submit}
        className="w-full max-w-sm bg-surface rounded-xl border border-border shadow-sm p-6 space-y-4"
      >
        <div className="text-center">
          <h1 className="text-xl font-bold text-fg flex items-center justify-center gap-2">
            <KeyRound size={20} className="text-primary" /> Change password
          </h1>
          {forced && (
            <p className="text-sm text-warning mt-1">
              You must set a new password before continuing.
            </p>
          )}
        </div>
        {error && (
          <p
            role="alert"
            className="text-sm text-danger bg-danger-bg border border-danger-border rounded-lg px-3 py-2"
          >
            {error}
          </p>
        )}
        <Input
          type="password"
          placeholder="Current password"
          aria-label="Current password"
          value={current}
          onChange={e => setCurrent(e.target.value)}
          autoComplete="current-password"
        />
        <Input
          type="password"
          placeholder="New password (min 8 chars)"
          aria-label="New password"
          value={next}
          onChange={e => setNext(e.target.value)}
          autoComplete="new-password"
        />
        <Input
          type="password"
          placeholder="Confirm new password"
          aria-label="Confirm new password"
          value={confirm}
          onChange={e => setConfirm(e.target.value)}
          autoComplete="new-password"
        />
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={!current || !next}
          loading={busy}
        >
          {busy ? 'Saving…' : 'Change password'}
        </Button>
        <button
          type="button"
          onClick={() => void logout()}
          className="w-full text-xs text-fg-muted hover:text-fg"
        >
          Sign out
        </button>
      </form>
    </div>
  )
}
