import { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { ArrowRight, Plus } from 'lucide-react'
import CreateOrganizationModal from '@/components/CreateOrganizationModal'
import LoadingSpinner from '@/components/LoadingSpinner'

function orgInitials(name: string): string {
  return name.split(/[\s-_]+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join('')
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

  const { data: orgs = [], isLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: api.organizations,
  })

  if (isLoading) return <LoadingSpinner message="Loading organizations…" />

  // Auto-redirect when only one org exists
  if (orgs.length === 1) return <Navigate to={`/${orgs[0].slug}`} replace />

  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center py-12">
      <div className="w-full max-w-md space-y-6">
        {/* Brand header */}
        <div className="text-center space-y-1">
          <h1 className="text-3xl font-bold text-slate-900">Test Platform</h1>
          <p className="text-slate-500 text-sm">Select an organization to continue</p>
        </div>

        {/* Org list */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          {orgs.length === 0 && (
            <p className="px-6 py-10 text-sm text-slate-500 text-center">
              No organizations yet — create one to get started.
            </p>
          )}
          {orgs.map(org => (
            <button
              key={org.id}
              onClick={() => navigate(`/${org.slug}`)}
              className="w-full flex items-center justify-between px-5 py-4 hover:bg-slate-50 transition-colors group border-b border-slate-100 last:border-0"
            >
              <div className="flex items-center gap-3">
                {org.logoUrl ? (
                  <img src={org.logoUrl} className="w-9 h-9 rounded-xl object-cover shrink-0" alt={org.name} />
                ) : (
                  <div
                    className="w-9 h-9 rounded-xl flex items-center justify-center text-white text-xs font-bold shrink-0 select-none"
                    style={{ background: `hsl(${orgHue(org.name)},60%,42%)` }}
                  >
                    {orgInitials(org.name)}
                  </div>
                )}
                <div className="text-left min-w-0">
                  <p className="text-sm font-semibold text-slate-900 truncate">
                    {org.displayName ?? org.name}
                  </p>
                  <p className="text-xs text-slate-400 font-mono">@{org.slug}</p>
                </div>
              </div>
              <ArrowRight size={15} className="text-slate-300 group-hover:text-blue-500 transition-colors shrink-0" />
            </button>
          ))}
        </div>

        {/* Actions */}
        <div className="flex justify-center">
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-xl hover:bg-blue-700 transition-colors"
          >
            <Plus size={14} /> New Organization
          </button>
        </div>
      </div>

      <CreateOrganizationModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={() => void qc.invalidateQueries({ queryKey: ['organizations'] })}
      />
    </div>
  )
}
