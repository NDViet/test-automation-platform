import { useState } from 'react'
import { LogIn } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { Button, Input } from '@/components/ui'

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(username.trim(), password)
    } catch (err) {
      setError((err as Error).message || 'Login failed')
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
            <LogIn size={20} className="text-primary" /> Sign in
          </h1>
          <p className="text-sm text-fg-muted mt-1">Test Automation Platform</p>
        </div>
        {error && (
          <p
            role="alert"
            className="text-sm text-danger bg-danger-bg border border-danger-border rounded-lg px-3 py-2"
          >
            {error}
          </p>
        )}
        <div>
          <label htmlFor="login-username" className="block text-sm font-medium text-fg mb-1">
            Username
          </label>
          <Input
            id="login-username"
            value={username}
            onChange={e => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
          />
        </div>
        <div>
          <label htmlFor="login-password" className="block text-sm font-medium text-fg mb-1">
            Password
          </label>
          <Input
            id="login-password"
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </div>
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={!username.trim() || !password}
          loading={busy}
        >
          {busy ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </div>
  )
}
