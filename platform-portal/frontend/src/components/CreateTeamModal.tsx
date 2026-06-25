import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { api } from '@/lib/api'
import type { Team } from '@/lib/types'

interface Props {
  open: boolean
  /** Project the team belongs to (ADO-first: Org → Project → Team). */
  projectId: string
  onClose: () => void
  onCreated: (team: Team) => void
}

function toSlug(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-') // any run of non-alphanumerics -> a single hyphen
    .replace(/^-+|-+$/g, '') // trim leading/trailing hyphens
}

export default function CreateTeamModal({ open, projectId, onClose, onCreated }: Props) {
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [slugEdited, setSlugEdited] = useState(false)

  const mutation = useMutation({
    mutationFn: () => api.createTeam(projectId, { name, slug }),
    onSuccess: team => {
      onCreated(team)
      onClose()
      setName('')
      setSlug('')
      setSlugEdited(false)
    },
  })

  if (!open) return null

  function handleNameChange(value: string) {
    setName(value)
    if (!slugEdited) setSlug(toSlug(value))
  }

  function handleSlugChange(value: string) {
    setSlug(value)
    setSlugEdited(true)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative z-10 bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-semibold text-slate-900">New Team</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={18} />
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Name</label>
            <input
              type="text"
              value={name}
              onChange={e => handleNameChange(e.target.value)}
              placeholder="Platform Team"
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              autoFocus
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Slug</label>
            <input
              type="text"
              value={slug}
              onChange={e => handleSlugChange(e.target.value)}
              placeholder="platform-team"
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
            />
            <p className="text-xs text-slate-400 mt-1">
              Lowercase letters, numbers and dashes only. Cannot be changed later.
            </p>
          </div>
        </div>

        {mutation.isError && (
          <p className="mt-3 text-sm text-red-600">
            Failed to create team — {(mutation.error as Error).message}
          </p>
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
            disabled={mutation.isPending || !name.trim() || !slug.trim()}
            className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {mutation.isPending ? 'Creating…' : 'Create Team'}
          </button>
        </div>
      </div>
    </div>
  )
}
