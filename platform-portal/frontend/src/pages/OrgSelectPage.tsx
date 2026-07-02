import { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { ArrowRight, Lock, Plus, ShieldAlert } from 'lucide-react'
import CreateOrganizationModal from '@/components/CreateOrganizationModal'
import AdoOnboardWizard from '@/components/AdoOnboardWizard'
import CredKeySetupModal from '@/components/CredKeySetupModal'
import LoadingSpinner from '@/components/LoadingSpinner'
import { Button } from '@/components/ui'

function orgInitials(name: string): string {
  return name
    .split(/[\s-_]+/)
    .slice(0, 2)
    .map(w => w[0]?.toUpperCase() ?? '')
    .join('')
}
function orgHue(name: string): number {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) & 0xffffff
  return h % 360
}

export default function OrgSelectPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [showAdo, setShowAdo] = useState(false)
  const [showKey, setShowKey] = useState(false)

  const { data: orgs = [], isLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: api.organizations,
  })

  const { data: keyStatus } = useQuery({
    queryKey: ['cred-key-status'],
    queryFn: api.credKeyStatus,
    retry: false,
  })

  // Credentials can only be stored when a key is loaded. Optimistic when status is unknown so a
  // status-endpoint hiccup never blocks org selection.
  const keyReady = keyStatus ? keyStatus.unlocked : true
  const keyLocked = !!keyStatus && !keyStatus.unlocked

  // Onboarding needs credential encryption — route through key setup first when it isn't ready.
  const startOnboard = () => (keyReady ? setShowAdo(true) : setShowKey(true))

  if (isLoading) return <LoadingSpinner message="Loading organizations…" />

  // Auto-redirect when only one org exists
  if (orgs.length === 1) return <Navigate to={`/${orgs[0].slug}`} replace />

  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center py-12">
      <div className="w-full max-w-md space-y-6">
        {/* Brand header */}
        <div className="text-center space-y-1">
          <h1 className="text-3xl font-bold text-fg">Test Platform</h1>
          <p className="text-fg-muted text-sm">Select an organization to continue</p>
        </div>

        {/* Credential encryption key gate (only when PLATFORM_CRED_KEY env is unset) */}
        {keyLocked && (
          <button
            onClick={() => setShowKey(true)}
            className="w-full flex items-center gap-3 px-4 py-3 rounded-lg border border-warning-border bg-warning-bg text-left hover:brightness-[0.98] transition-all"
          >
            {keyStatus?.initialized ? (
              <Lock size={18} className="text-warning shrink-0" />
            ) : (
              <ShieldAlert size={18} className="text-warning shrink-0" />
            )}
            <div className="min-w-0">
              <p className="text-sm font-semibold text-warning">
                {keyStatus?.initialized
                  ? 'Credential encryption is locked'
                  : 'Set up credential encryption'}
              </p>
              <p className="text-xs text-warning/80">
                {keyStatus?.initialized
                  ? 'Unlock with your passphrase to use integration credentials.'
                  : 'Choose a passphrase before connecting integrations.'}
              </p>
            </div>
            <ArrowRight size={15} className="text-warning/60 ml-auto shrink-0" />
          </button>
        )}

        {/* Org list */}
        <div className="bg-surface rounded-xl border border-border shadow-xs overflow-hidden">
          {orgs.length === 0 && (
            <div className="px-6 py-10 text-center space-y-4">
              <p className="text-sm text-fg-muted">
                No organizations yet. Connect Azure DevOps to import your projects, teams and
                members — or create an empty organization.
              </p>
              <Button onClick={startOnboard}>
                Connect Azure DevOps <ArrowRight size={14} />
              </Button>
            </div>
          )}
          {orgs.map(org => (
            <button
              key={org.id}
              onClick={() => navigate(`/${org.slug}`)}
              className="w-full flex items-center justify-between px-5 py-4 hover:bg-surface-muted transition-colors group border-b border-border last:border-0"
            >
              <div className="flex items-center gap-3">
                {org.logoUrl ? (
                  <img
                    src={org.logoUrl}
                    className="w-9 h-9 rounded-xl object-cover shrink-0"
                    alt={org.name}
                  />
                ) : (
                  <div
                    className="w-9 h-9 rounded-xl flex items-center justify-center text-white text-xs font-bold shrink-0 select-none"
                    style={{ background: `hsl(${orgHue(org.name)},60%,42%)` }}
                  >
                    {orgInitials(org.name)}
                  </div>
                )}
                <div className="text-left min-w-0">
                  <p className="text-sm font-semibold text-fg truncate">
                    {org.displayName ?? org.name}
                  </p>
                  <p className="text-xs text-fg-subtle font-mono">@{org.slug}</p>
                </div>
              </div>
              <ArrowRight
                size={15}
                className="text-fg-subtle group-hover:text-primary transition-colors shrink-0"
              />
            </button>
          ))}
        </div>

        {/* Actions */}
        <div className="flex justify-center gap-3">
          <Button variant="secondary" onClick={startOnboard}>
            Connect Azure DevOps
          </Button>
          <Button onClick={() => setShowCreate(true)}>
            <Plus size={14} /> New Organization
          </Button>
        </div>
      </div>

      <CreateOrganizationModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={() => void qc.invalidateQueries({ queryKey: ['organizations'] })}
      />

      <AdoOnboardWizard
        open={showAdo}
        onClose={() => setShowAdo(false)}
        onDone={slug => {
          setShowAdo(false)
          void qc.invalidateQueries({ queryKey: ['organizations'] })
          navigate(`/${slug}`)
        }}
      />

      <CredKeySetupModal
        open={showKey}
        status={keyStatus}
        onClose={() => setShowKey(false)}
        onReady={() => {
          setShowKey(false)
          void qc.invalidateQueries({ queryKey: ['cred-key-status'] })
        }}
      />
    </div>
  )
}
