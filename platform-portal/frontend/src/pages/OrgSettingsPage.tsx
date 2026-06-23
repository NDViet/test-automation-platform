import { useState, useRef, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Organization } from '@/lib/types'

// ── Helpers (mirrors Sidebar) ─────────────────────────────────────────────────

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

// ── Sub-component: Display Name card ─────────────────────────────────────────

function DisplayNameCard({ org }: { org: Organization }) {
  const qc = useQueryClient()
  const [displayName, setDisplayName] = useState(org.displayName ?? org.name)
  const [feedback, setFeedback] = useState<{ ok: boolean; msg: string } | null>(null)

  const mutation = useMutation({
    mutationFn: (value: string) =>
      api.updateOrganization(org.id, { name: org.name, displayName: value }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['organizations'] })
      setFeedback({ ok: true, msg: 'Display name saved.' })
      setTimeout(() => setFeedback(null), 3000)
    },
    onError: (err: Error) => {
      setFeedback({ ok: false, msg: err.message ?? 'Save failed.' })
    },
  })

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex flex-col gap-4">
      <div>
        <h2 className="text-base font-semibold text-slate-800">Display Name</h2>
        <p className="text-sm text-slate-500 mt-0.5">
          The friendly name shown in the sidebar and across the platform.
        </p>
      </div>

      <div className="flex flex-col gap-2">
        <label htmlFor="displayName" className="text-sm font-medium text-slate-700">
          Organization display name
        </label>
        <input
          id="displayName"
          type="text"
          value={displayName}
          onChange={e => setDisplayName(e.target.value)}
          placeholder={org.name}
          className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
        />
        <p className="text-xs text-slate-400">
          Slug (read-only): <span className="font-mono">{org.slug}</span>
        </p>
      </div>

      {feedback && (
        <p className={`text-sm font-medium ${feedback.ok ? 'text-green-600' : 'text-red-600'}`}>
          {feedback.msg}
        </p>
      )}

      <div className="flex justify-end">
        <button
          onClick={() => mutation.mutate(displayName)}
          disabled={mutation.isPending || displayName.trim() === ''}
          className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {mutation.isPending ? 'Saving…' : 'Save'}
        </button>
      </div>
    </div>
  )
}

// ── Sub-component: Logo upload card ──────────────────────────────────────────

function LogoCard({ org }: { org: Organization }) {
  const qc = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const [feedback, setFeedback] = useState<{ ok: boolean; msg: string } | null>(null)

  const hue = orgHue(org.name)

  const mutation = useMutation({
    mutationFn: (file: File) => api.uploadOrgLogo(org.id, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['organizations'] })
      setFeedback({ ok: true, msg: 'Logo uploaded successfully.' })
      setTimeout(() => setFeedback(null), 3000)
    },
    onError: (err: Error) => {
      setFeedback({ ok: false, msg: err.message ?? 'Upload failed.' })
    },
  })

  const handleFile = useCallback(
    (file: File | null | undefined) => {
      if (!file) return
      if (!file.type.startsWith('image/')) {
        setFeedback({ ok: false, msg: 'Please select an image file.' })
        return
      }
      if (file.size > 2 * 1024 * 1024) {
        setFeedback({ ok: false, msg: 'File is too large (max 2 MB).' })
        return
      }
      setFeedback(null)
      mutation.mutate(file)
    },
    [mutation],
  )

  const onDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault()
      setDragOver(false)
      handleFile(e.dataTransfer.files[0])
    },
    [handleFile],
  )

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex flex-col gap-4">
      <div>
        <h2 className="text-base font-semibold text-slate-800">Organization Logo</h2>
        <p className="text-sm text-slate-500 mt-0.5">
          Upload a logo (PNG, JPG, SVG — max 2 MB). Drag and drop or click to choose.
        </p>
      </div>

      {/* Preview / drop zone */}
      <div
        onClick={() => fileRef.current?.click()}
        onDragOver={e => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        className={`flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed p-8 cursor-pointer transition-colors select-none
          ${dragOver
            ? 'border-blue-500 bg-blue-50'
            : 'border-slate-200 hover:border-blue-400 hover:bg-slate-50'
          }`}
      >
        {org.logoUrl ? (
          <img
            src={org.logoUrl}
            alt={org.displayName ?? org.name}
            className="w-16 h-16 rounded-xl object-cover shadow"
          />
        ) : (
          <div
            className="w-16 h-16 rounded-xl flex items-center justify-center text-white text-xl font-bold shadow"
            style={{ background: `hsl(${hue},60%,42%)` }}
          >
            {orgInitials(org.displayName ?? org.name)}
          </div>
        )}

        <p className="text-sm text-slate-500 text-center">
          {mutation.isPending
            ? 'Uploading…'
            : dragOver
              ? 'Drop to upload'
              : 'Click or drag an image here'}
        </p>
      </div>

      {/* Hidden file input */}
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={e => handleFile(e.target.files?.[0])}
      />

      {feedback && (
        <p className={`text-sm font-medium ${feedback.ok ? 'text-green-600' : 'text-red-600'}`}>
          {feedback.msg}
        </p>
      )}

      <div className="flex justify-end">
        <button
          onClick={() => fileRef.current?.click()}
          disabled={mutation.isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {mutation.isPending ? 'Uploading…' : 'Choose file'}
        </button>
      </div>
    </div>
  )
}

// ── Org selector (when multiple orgs exist) ───────────────────────────────────

function OrgSelect({
  orgs,
  selected,
  onSelect,
}: {
  orgs: Organization[]
  selected: Organization
  onSelect: (org: Organization) => void
}) {
  if (orgs.length <= 1) return null
  return (
    <div className="flex items-center gap-3">
      <label className="text-sm font-medium text-slate-700">Organization:</label>
      <select
        value={selected.id}
        onChange={e => {
          const found = orgs.find(o => o.id === e.target.value)
          if (found) onSelect(found)
        }}
        className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
      >
        {orgs.map(o => (
          <option key={o.id} value={o.id}>
            {o.displayName ?? o.name}
          </option>
        ))}
      </select>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function OrgSettingsPage() {
  const { data: orgs, isLoading, isError } = useQuery({
    queryKey: ['organizations'],
    queryFn: api.organizations,
  })

  const [selectedId, setSelectedId] = useState<string | null>(null)

  if (isLoading) {
    return (
      <div className="p-8 text-sm text-slate-500">Loading organization…</div>
    )
  }

  if (isError || !orgs || orgs.length === 0) {
    return (
      <div className="p-8 text-sm text-red-500">Failed to load organization data.</div>
    )
  }

  const org = (selectedId ? orgs.find(o => o.id === selectedId) : null) ?? orgs[0]

  return (
    <div className="max-w-4xl mx-auto px-6 py-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold text-slate-900">Organization Branding</h1>
        <p className="text-sm text-slate-500">
          Customize how your organization appears across the platform.
        </p>
      </div>

      {/* Org selector (only shown when multiple orgs) */}
      <OrgSelect
        orgs={orgs}
        selected={org}
        onSelect={o => setSelectedId(o.id)}
      />

      {/* Cards grid: side-by-side on md+, stacked on mobile */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <DisplayNameCard key={`name-${org.id}`} org={org} />
        <LogoCard key={`logo-${org.id}`} org={org} />
      </div>
    </div>
  )
}
