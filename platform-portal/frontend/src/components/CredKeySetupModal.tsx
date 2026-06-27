import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Loader2, ShieldCheck, X } from 'lucide-react'
import { api } from '@/lib/api'
import type { CredKeyStatus } from '@/lib/types'

interface Props {
  open: boolean
  /** Current status; decides whether this is first-run setup (init) or post-restart unlock. */
  status: CredKeyStatus | undefined
  onClose: () => void
  onReady: () => void
}

const MIN = 12

/**
 * Sets up (first run) or unlocks (after a restart) the credential encryption key from a passphrase,
 * for platforms that don't supply PLATFORM_CRED_KEY via the environment. The passphrase is only sent
 * to the backend, which derives the AES-256 key (PBKDF2) — it is never stored or echoed back.
 */
export default function CredKeySetupModal({ open, status, onClose, onReady }: Props) {
  const qc = useQueryClient()
  const isUnlock = !!status?.initialized
  const [passphrase, setPassphrase] = useState('')
  const [confirm, setConfirm] = useState('')

  const mutation = useMutation({
    mutationFn: () => (isUnlock ? api.credKeyUnlock(passphrase) : api.credKeyInit(passphrase)),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cred-key-status'] })
      setPassphrase('')
      setConfirm('')
      onReady()
    },
  })

  if (!open) return null

  const tooShort = passphrase.length < MIN
  const mismatch = !isUnlock && confirm.length > 0 && passphrase !== confirm
  const canSubmit =
    !mutation.isPending && !tooShort && (isUnlock || (passphrase === confirm && confirm.length > 0))

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative z-10 bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            {isUnlock ? (
              <KeyRound size={18} className="text-blue-600" />
            ) : (
              <ShieldCheck size={18} className="text-blue-600" />
            )}
            <h2 className="text-lg font-semibold text-slate-900">
              {isUnlock ? 'Unlock credential encryption' : 'Set up credential encryption'}
            </h2>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>

        <p className="text-sm text-slate-500 mb-4">
          {isUnlock
            ? 'Enter the passphrase you chose to re-open credential storage after the restart.'
            : 'Choose a passphrase to protect stored integration secrets (PATs, tokens). The encryption key is derived from it — keep it safe; it can’t be recovered if lost.'}
        </p>

        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Passphrase</label>
            <input
              type="password"
              value={passphrase}
              onChange={e => setPassphrase(e.target.value)}
              autoComplete={isUnlock ? 'current-password' : 'new-password'}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              autoFocus
            />
            {!isUnlock && <p className="text-xs text-slate-400 mt-1">At least {MIN} characters.</p>}
          </div>

          {!isUnlock && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Confirm passphrase
              </label>
              <input
                type="password"
                value={confirm}
                onChange={e => setConfirm(e.target.value)}
                autoComplete="new-password"
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {mismatch && (
                <p className="text-xs text-red-600 mt-1">Passphrases don&apos;t match.</p>
              )}
            </div>
          )}
        </div>

        {mutation.isError && (
          <p className="mt-3 text-sm text-red-600">{(mutation.error as Error).message}</p>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={() => void mutation.mutate()}
            disabled={!canSubmit}
            className="flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {mutation.isPending ? (
              <>
                <Loader2 size={14} className="animate-spin" />
                {isUnlock ? 'Unlocking…' : 'Setting up…'}
              </>
            ) : isUnlock ? (
              'Unlock'
            ) : (
              'Set up key'
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
