import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Plus, Trash2, Copy, Check } from 'lucide-react'
import type { Team } from '@/lib/types'

export default function ApiKeysPage() {
  const qc = useQueryClient()
  const [selectedTeamId, setSelectedTeamId] = useState<string>('')
  const [newKeyName, setNewKeyName] = useState('')
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const { data: teams } = useQuery({
    queryKey: ['teams'],
    queryFn: api.teams,
  })

  const teamId = selectedTeamId || teams?.[0]?.id || ''

  const { data: keys, isLoading, error } = useQuery({
    queryKey: ['api-keys', teamId],
    queryFn: () => api.apiKeys(teamId),
    enabled: !!teamId,
  })

  const createMutation = useMutation({
    mutationFn: () => api.createApiKey({ name: newKeyName, teamId }),
    onSuccess: (data) => {
      setCreatedKey(data.key)
      setNewKeyName('')
      void qc.invalidateQueries({ queryKey: ['api-keys', teamId] })
    },
  })

  const revokeMutation = useMutation({
    mutationFn: (id: string) => api.revokeApiKey(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['api-keys', teamId] }),
  })

  const copyKey = async (key: string) => {
    await navigator.clipboard.writeText(key)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">API Keys</h1>
        <p className="text-sm text-slate-500 mt-1">Manage authentication keys for CI/CD pipelines</p>
      </div>

      {/* Team selector */}
      {teams && teams.length > 0 && (
        <div className="flex items-center gap-3">
          <label className="text-sm font-medium text-slate-700">Team:</label>
          <select
            value={teamId}
            onChange={e => setSelectedTeamId(e.target.value)}
            className="text-sm border border-slate-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {teams.map((t: Team) => (
              <option key={t.id} value={t.id}>{t.name}</option>
            ))}
          </select>
        </div>
      )}

      {/* New key created banner */}
      {createdKey && (
        <div className="bg-green-50 border border-green-200 rounded-xl p-4">
          <p className="text-sm font-semibold text-green-800 mb-2">
            New API key created — copy it now, it won&apos;t be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-white border border-green-200 rounded px-3 py-2 text-sm font-mono text-green-900 break-all">
              {createdKey}
            </code>
            <button
              onClick={() => void copyKey(createdKey)}
              className="p-2 rounded-lg bg-green-600 text-white hover:bg-green-700 transition-colors"
            >
              {copied ? <Check size={16} /> : <Copy size={16} />}
            </button>
          </div>
        </div>
      )}

      {/* Create new key form */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
        <h2 className="font-semibold text-slate-900 mb-4">Create New Key</h2>
        <div className="flex items-center gap-3">
          <input
            type="text"
            placeholder="Key name (e.g. github-actions-prod)"
            value={newKeyName}
            onChange={e => setNewKeyName(e.target.value)}
            className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            onClick={() => void createMutation.mutate()}
            disabled={!newKeyName.trim() || !teamId || createMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <Plus size={16} />
            {createMutation.isPending ? 'Creating…' : 'Create Key'}
          </button>
        </div>
        {createMutation.isError && (
          <p className="text-xs text-red-600 mt-2">Failed to create key. Please try again.</p>
        )}
      </div>

      {/* Keys list */}
      {isLoading && <LoadingSpinner message="Loading API keys…" />}
      {error && <ErrorMessage message="Failed to load API keys." />}
      {!isLoading && !error && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="font-semibold text-slate-900">Active Keys</h2>
          </div>
          <div className="divide-y divide-slate-50">
            {(!keys || keys.length === 0) && (
              <p className="px-5 py-8 text-center text-sm text-slate-500">
                No API keys for this team.
              </p>
            )}
            {(keys ?? []).map(k => (
              <div key={k.id} className="px-5 py-4 flex items-center justify-between gap-4">
                <div>
                  <p className="text-sm font-medium text-slate-900">{k.name}</p>
                  <div className="flex items-center gap-3 mt-0.5">
                    <code className="text-xs text-slate-500 font-mono">{k.prefix}…</code>
                    <span className="text-xs text-slate-400">
                      Created {relativeTime(k.createdAt)}
                    </span>
                    {k.lastUsedAt && (
                      <span className="text-xs text-slate-400">
                        Last used {relativeTime(k.lastUsedAt)}
                      </span>
                    )}
                    {k.expiresAt && (
                      <span className="text-xs text-orange-600">
                        Expires {relativeTime(k.expiresAt)}
                      </span>
                    )}
                  </div>
                </div>
                <button
                  onClick={() => void revokeMutation.mutate(k.id)}
                  disabled={revokeMutation.isPending}
                  className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  title="Revoke key"
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
