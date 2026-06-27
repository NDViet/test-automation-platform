import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ArrowRight, Check, Loader2, X } from 'lucide-react'
import { api } from '@/lib/api'
import type { AdoOnboardResult, AzureOrg } from '@/lib/types'

interface Props {
  open: boolean
  onClose: () => void
  /** Called after a successful bootstrap with the new org's slug, so the caller can navigate in. */
  onDone: (orgSlug: string) => void
}

type Step = 'pat' | 'select' | 'done'

/**
 * First-run "Connect Azure DevOps" wizard for a blank platform (no Organization yet): enter a PAT →
 * discover the accounts it can access → pick one → bootstrap (org + projects + structure + members)
 * → land in the seeded org. The PAT is only sent to the backend; it is never rendered back.
 */
export default function AdoOnboardWizard({ open, onClose, onDone }: Props) {
  const [step, setStep] = useState<Step>('pat')
  const [pat, setPat] = useState('')
  const [accounts, setAccounts] = useState<AzureOrg[]>([])
  const [account, setAccount] = useState<string>('')
  const [displayName, setDisplayName] = useState('')
  const [result, setResult] = useState<AdoOnboardResult | null>(null)

  // Discovery needs the PAT's Profile (read) scope. Many tokens are scoped only to Projects/Work
  // Items, so a failure here is non-fatal: we still advance and let the user type the org name.
  const discover = useMutation({
    mutationFn: () => api.adoOnboardDiscover(pat.trim()),
    onSuccess: orgs => {
      setAccounts(orgs)
      if (orgs.length === 1) setAccount(orgs[0].accountName)
      setStep('select')
    },
    onError: () => {
      setAccounts([])
      setStep('select')
    },
  })

  const onboard = useMutation({
    mutationFn: () =>
      api.adoOnboardOrg({
        pat: pat.trim(),
        adoAccount: account.trim(),
        displayName: displayName.trim() || undefined,
      }),
    onSuccess: res => {
      setResult(res)
      setStep('done')
    },
  })

  if (!open) return null

  function reset() {
    setStep('pat')
    setPat('')
    setAccounts([])
    setAccount('')
    setDisplayName('')
    setResult(null)
    discover.reset()
    onboard.reset()
  }

  function close() {
    reset()
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={close} />
      <div className="relative z-10 bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-semibold text-slate-900">Connect Azure DevOps</h2>
          <button onClick={close} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>

        {/* Step 1 — PAT */}
        {step === 'pat' && (
          <div className="space-y-4">
            <p className="text-sm text-slate-500">
              Paste a personal access token. We&apos;ll try to list the organizations it can access
              — if your token isn&apos;t allowed to, you can type the organization name on the next
              step.
            </p>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Personal access token
              </label>
              <input
                type="password"
                value={pat}
                onChange={e => setPat(e.target.value)}
                placeholder="••••••••••••••••"
                autoComplete="off"
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                autoFocus
              />
              <p className="text-xs text-slate-400 mt-1">
                Needs read access to Projects, Teams and Work Items (and Profile to auto-list orgs).
                Stored encrypted; never shown again.
              </p>
            </div>

            <div className="flex justify-end gap-3 pt-1">
              <button
                onClick={close}
                className="px-4 py-2 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => void discover.mutate()}
                disabled={discover.isPending || !pat.trim()}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                {discover.isPending ? (
                  <>
                    <Loader2 size={14} className="animate-spin" /> Checking…
                  </>
                ) : (
                  <>
                    Continue <ArrowRight size={14} />
                  </>
                )}
              </button>
            </div>
          </div>
        )}

        {/* Step 2 — choose / enter org */}
        {step === 'select' && (
          <div className="space-y-4">
            {accounts.length > 0 ? (
              // Token can enumerate its orgs → pick from a dropdown.
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Organization
                </label>
                <select
                  value={account}
                  onChange={e => setAccount(e.target.value)}
                  className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                  autoFocus
                >
                  <option value="" disabled>
                    Select an organization…
                  </option>
                  {accounts.map(a => (
                    <option key={a.accountName} value={a.accountName}>
                      {a.accountName}
                    </option>
                  ))}
                </select>
                <p className="text-xs text-slate-400 mt-1">
                  {accounts.length} organization{accounts.length === 1 ? '' : 's'} accessible to
                  this token. Its projects, teams and members will be imported.
                </p>
              </div>
            ) : (
              // Token can't enumerate orgs (org-scoped PAT / no Profile scope) → enter manually.
              <div>
                <p className="text-sm text-slate-500 mb-2">
                  {discover.isError
                    ? "We couldn't auto-list your organizations (this token is scoped to one org, or lacks the Profile scope). Enter the organization name — importing only needs the Projects/Work Items scope."
                    : 'Enter the Azure DevOps organization to import.'}
                </p>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Organization name
                </label>
                <input
                  type="text"
                  value={account}
                  onChange={e => setAccount(e.target.value)}
                  placeholder="my-org"
                  className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                  autoFocus
                />
                <p className="text-xs text-slate-400 mt-1">
                  The name in your ADO URL: dev.azure.com/
                  <span className="font-mono">&lt;name&gt;</span>.
                </p>
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Display name <span className="font-normal text-slate-400">(optional)</span>
              </label>
              <input
                type="text"
                value={displayName}
                onChange={e => setDisplayName(e.target.value)}
                placeholder={account || 'Organization name'}
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {onboard.isError && (
              <p className="text-sm text-red-600">{(onboard.error as Error).message}</p>
            )}

            <div className="flex justify-between gap-3 pt-1">
              <button
                onClick={() => {
                  onboard.reset()
                  setStep('pat')
                }}
                className="px-4 py-2 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
              >
                Back
              </button>
              <button
                onClick={() => void onboard.mutate()}
                disabled={onboard.isPending || !account.trim()}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                {onboard.isPending ? (
                  <>
                    <Loader2 size={14} className="animate-spin" /> Importing…
                  </>
                ) : (
                  <>
                    Import organization <ArrowRight size={14} />
                  </>
                )}
              </button>
            </div>
          </div>
        )}

        {/* Step 3 — done */}
        {step === 'done' && result && (
          <div className="space-y-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-green-100 flex items-center justify-center">
                <Check size={20} className="text-green-600" />
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-900">
                  {result.org.orgCreated ? 'Organization created' : 'Organization updated'}
                </p>
                <p className="text-xs text-slate-400 font-mono">@{result.org.slug}</p>
              </div>
            </div>

            <dl className="grid grid-cols-3 gap-3 text-center">
              <Stat label="Projects" value={result.projects.total} />
              <Stat label="Teams" value={result.structure.teamsCreated} />
              <Stat label="Members" value={result.members.membersSeen} />
            </dl>

            {result.structure.failures.length > 0 && (
              <div className="text-xs text-amber-600 bg-amber-50 border border-amber-100 rounded-lg p-3">
                <p className="font-medium mb-1">
                  {result.structure.failures.length} project(s) couldn&apos;t be fully synced:
                </p>
                <ul className="list-disc list-inside space-y-0.5">
                  {result.structure.failures.map((f, i) => (
                    <li key={i}>{f}</li>
                  ))}
                </ul>
              </div>
            )}

            <div className="flex justify-end pt-1">
              <button
                onClick={() => onDone(result.org.slug)}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                Go to organization <ArrowRight size={14} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="bg-slate-50 rounded-lg py-3">
      <dd className="text-xl font-bold text-slate-900">{value}</dd>
      <dt className="text-xs text-slate-500 mt-0.5">{label}</dt>
    </div>
  )
}
