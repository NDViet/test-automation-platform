import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Button, Input, PageHeader } from '@/components/ui'
import { Plus, Trash2, Copy, Check, KeyRound } from 'lucide-react'

export default function ApiKeysPage() {
  const qc = useQueryClient()
  const [newKeyName, setNewKeyName] = useState('')
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const {
    data: keys,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['api-keys'],
    queryFn: () => api.apiKeys(),
  })

  const createMutation = useMutation({
    mutationFn: () => api.createApiKey({ name: newKeyName }),
    onSuccess: data => {
      setCreatedKey(data.rawKey)
      setNewKeyName('')
      void qc.invalidateQueries({ queryKey: ['api-keys'] })
    },
  })

  const revokeMutation = useMutation({
    mutationFn: (id: string) => api.revokeApiKey(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['api-keys'] }),
  })

  const copyKey = async (key: string) => {
    await navigator.clipboard.writeText(key)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="API Keys"
        icon={<KeyRound size={20} />}
        description="Manage authentication keys for CI/CD pipelines. Keys are not restricted to any project or team."
      />

      {/* New key created banner */}
      {createdKey && (
        <div className="bg-success-bg border border-success-border rounded-lg p-4">
          <p className="text-sm font-semibold text-success mb-2">
            New API key created — copy it now, it won&apos;t be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-surface border border-success-border rounded px-3 py-2 text-sm font-mono text-success break-all">
              {createdKey}
            </code>
            <button
              onClick={() => void copyKey(createdKey)}
              className="p-2 rounded-md bg-success text-white hover:opacity-90 transition-opacity"
              aria-label="Copy API key"
            >
              {copied ? <Check size={16} /> : <Copy size={16} />}
            </button>
          </div>
        </div>
      )}

      {/* Create new key form */}
      <div className="bg-surface rounded-lg border border-border shadow-xs p-5">
        <h2 className="font-semibold text-fg mb-4">Create New Key</h2>
        <div className="flex items-center gap-3">
          <Input
            type="text"
            placeholder="Key name (e.g. github-actions-prod)"
            aria-label="Key name"
            value={newKeyName}
            onChange={e => setNewKeyName(e.target.value)}
          />
          <Button
            onClick={() => void createMutation.mutate()}
            disabled={!newKeyName.trim()}
            loading={createMutation.isPending}
          >
            <Plus size={16} />
            {createMutation.isPending ? 'Creating…' : 'Create Key'}
          </Button>
        </div>
        {createMutation.isError && (
          <p className="text-xs text-danger mt-2">Failed to create key. Please try again.</p>
        )}
      </div>

      {/* Keys list */}
      {isLoading && <LoadingSpinner message="Loading API keys…" />}
      {error && <ErrorMessage message="Failed to load API keys." onRetry={() => void refetch()} />}
      {!isLoading && !error && (
        <div className="bg-surface rounded-lg border border-border shadow-xs">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="font-semibold text-fg">Active Keys</h2>
          </div>
          <div className="divide-y divide-border">
            {(!keys || keys.length === 0) && (
              <p className="px-5 py-8 text-center text-sm text-fg-muted">No API keys yet.</p>
            )}
            {(keys ?? []).map(k => (
              <div key={k.id} className="px-5 py-4 flex items-center justify-between gap-4">
                <div>
                  <p className="text-sm font-medium text-fg">{k.name}</p>
                  <div className="flex items-center gap-3 mt-0.5">
                    <code className="text-xs text-fg-muted font-mono">{k.prefix}…</code>
                    <span className="text-xs text-fg-subtle">Created {relativeTime(k.createdAt)}</span>
                    {k.lastUsedAt && (
                      <span className="text-xs text-fg-subtle">
                        Last used {relativeTime(k.lastUsedAt)}
                      </span>
                    )}
                    {k.expiresAt && (
                      <span className="text-xs text-warning">Expires {relativeTime(k.expiresAt)}</span>
                    )}
                  </div>
                </div>
                <button
                  onClick={() => void revokeMutation.mutate(k.id)}
                  disabled={revokeMutation.isPending}
                  className="p-2 text-fg-subtle hover:text-danger hover:bg-danger-bg rounded-md transition-colors"
                  title="Revoke key"
                  aria-label={`Revoke ${k.name}`}
                >
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
