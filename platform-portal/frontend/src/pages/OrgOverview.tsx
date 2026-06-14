import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import { formatPassRate, passRateColor, relativeTime, cn } from '@/lib/utils'
import StatCard from '@/components/StatCard'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import Badge from '@/components/Badge'
import CreateProjectModal from '@/components/CreateProjectModal'
import CreateOrganizationModal from '@/components/CreateOrganizationModal'
import { Activity, AlertTriangle, FolderOpen, TrendingUp, Plus, Pencil, Trash2, Building2 } from 'lucide-react'
import type { Project, Organization } from '@/lib/types'

export default function OrgOverview() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [showCreateProject, setShowCreateProject] = useState(false)
  const [showCreateOrg,     setShowCreateOrg]     = useState(false)
  const [editingOrg,        setEditingOrg]        = useState<Organization | null>(null)
  const [editName,          setEditName]          = useState('')

  const { data, isLoading, error } = useQuery({
    queryKey: ['overview'],
    queryFn: () => api.overview(7),
    refetchInterval: 60_000,
  })

  const { data: organizations, isLoading: orgsLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: api.organizations,
  })

  const { data: projects, isLoading: projectsLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.projects(),
  })

  const updateOrgMutation = useMutation({
    mutationFn: () => api.updateOrganization(editingOrg!.id, { name: editName }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['organizations'] })
      setEditingOrg(null)
    },
  })

  const deleteOrgMutation = useMutation({
    mutationFn: (id: string) => api.deleteOrganization(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['organizations'] }),
  })

  function startEditOrg(org: Organization) {
    setEditingOrg(org)
    setEditName(org.name)
  }

  function handleDeleteOrg(org: Organization) {
    const projectCount = (projects ?? []).filter(p => p.orgId === org.id).length
    if (projectCount > 0) {
      alert(`Organization "${org.name}" has ${projectCount} project(s). Delete them first.`)
      return
    }
    if (confirm(`Delete organization "${org.name}"? This cannot be undone.`)) {
      void deleteOrgMutation.mutate(org.id)
    }
  }

  function handleProjectCreated(project: Project) {
    void qc.invalidateQueries({ queryKey: ['projects'] })
    navigate(`/${project.orgSlug}/${project.slug}`)
  }

  function handleOrgCreated() {
    void qc.invalidateQueries({ queryKey: ['organizations'] })
  }

  if (isLoading) return <LoadingSpinner message="Loading overview…" />
  if (error) return <ErrorMessage message="Failed to load overview data." />

  const summary = data?.summary
  const alerts  = data?.recentAlerts ?? []

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Organization Overview</h1>
        <p className="text-sm text-slate-500 mt-1">Quality health across all organizations and projects</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Overall Pass Rate"
          value={formatPassRate(summary?.overallPassRate)}
          icon={TrendingUp}
          colorClass={passRateColor(summary?.overallPassRate ?? 0)}
        />
        <StatCard
          title="Total Projects"
          value={summary?.totalProjects ?? 0}
          icon={FolderOpen}
          colorClass="text-blue-600"
        />
        <StatCard
          title="Total Runs (7d)"
          value={summary?.totalRuns ?? 0}
          icon={Activity}
          colorClass="text-slate-600"
        />
        <StatCard
          title="Critical Flaky Tests"
          value={summary?.criticalFlakyTests ?? 0}
          icon={AlertTriangle}
          colorClass={(summary?.criticalFlakyTests ?? 0) > 0 ? 'text-red-600' : 'text-green-600'}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Organizations table */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
            <h2 className="font-semibold text-slate-900 flex items-center gap-2">
              <Building2 size={16} className="text-slate-400" /> Organizations
            </h2>
            <button
              onClick={() => setShowCreateOrg(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Plus size={13} /> New Organization
            </button>
          </div>
          {orgsLoading ? (
            <LoadingSpinner message="Loading organizations…" />
          ) : (
            <div className="divide-y divide-slate-50">
              {(organizations ?? []).length === 0 && (
                <p className="px-5 py-8 text-sm text-slate-500 text-center">No organizations yet.</p>
              )}
              {(organizations ?? []).map(org => {
                const projectCount = (projects ?? []).filter(p => p.orgId === org.id).length
                const isEditing = editingOrg?.id === org.id
                return (
                  <div key={org.id} className="px-5 py-3.5">
                    {isEditing ? (
                      <div className="flex items-center gap-2">
                        <input
                          type="text"
                          value={editName}
                          onChange={e => setEditName(e.target.value)}
                          onKeyDown={e => {
                            if (e.key === 'Enter') void updateOrgMutation.mutate()
                            if (e.key === 'Escape') setEditingOrg(null)
                          }}
                          autoFocus
                          className="flex-1 text-sm border border-blue-300 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        <button
                          onClick={() => void updateOrgMutation.mutate()}
                          disabled={updateOrgMutation.isPending || !editName.trim()}
                          className="text-xs px-3 py-1.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                        >
                          {updateOrgMutation.isPending ? 'Saving…' : 'Save'}
                        </button>
                        <button
                          onClick={() => setEditingOrg(null)}
                          className="text-xs px-3 py-1.5 border border-slate-200 rounded-lg text-slate-600 hover:bg-slate-50 transition-colors"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium text-slate-900">{org.name}</p>
                          <p className="text-xs text-slate-400 font-mono">{org.slug} · {projectCount} project{projectCount !== 1 ? 's' : ''}</p>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            title="Edit organization name"
                            onClick={() => startEditOrg(org)}
                            className="text-slate-400 hover:text-blue-600 transition-colors"
                          >
                            <Pencil size={14} />
                          </button>
                          <button
                            title="Delete organization"
                            onClick={() => handleDeleteOrg(org)}
                            className="text-slate-400 hover:text-red-600 transition-colors"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Projects table */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
            <h2 className="font-semibold text-slate-900">Projects</h2>
            <button
              onClick={() => setShowCreateProject(true)}
              disabled={(organizations ?? []).length === 0}
              title={(organizations ?? []).length === 0 ? 'Create an organization first' : undefined}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              <Plus size={13} /> New Project
            </button>
          </div>
          {projectsLoading ? (
            <LoadingSpinner message="Loading projects…" />
          ) : (
            <div className="divide-y divide-slate-50">
              {(projects ?? []).length === 0 && (
                <p className="px-5 py-8 text-sm text-slate-500 text-center">No projects yet.</p>
              )}
              {(projects ?? []).map(p => {
                const ps = summary?.projects?.find(s => s.projectId === p.id)
                return (
                  <button
                    key={p.id}
                    onClick={() => navigate(`/${p.orgSlug}/${p.slug}`)}
                    className="w-full text-left px-5 py-3.5 hover:bg-slate-50 transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium text-slate-900">{p.name}</p>
                        <p className="text-xs text-slate-400">{p.orgName} · {p.slug}</p>
                      </div>
                      <div className="text-right">
                        <p className={cn('text-sm font-semibold', passRateColor(ps?.passRate ?? 0))}>
                          {formatPassRate(ps?.passRate)}
                        </p>
                        <p className="text-xs text-slate-400">{ps?.totalRuns ?? 0} runs</p>
                      </div>
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Recent alerts */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm lg:col-span-2">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="font-semibold text-slate-900">Recent Alerts</h2>
          </div>
          <div className="divide-y divide-slate-50">
            {alerts.length === 0 && (
              <p className="px-5 py-8 text-sm text-slate-500 text-center">No recent alerts.</p>
            )}
            {alerts.slice(0, 8).map((a: import('@/lib/types').Alert) => (
              <div key={a.id} className="px-5 py-3.5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-900 truncate">{a.ruleName}</p>
                    <p className="text-xs text-slate-500 truncate mt-0.5">{a.message}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <Badge
                      label={a.severity}
                      colorClass={
                        a.severity === 'CRITICAL' ? 'text-red-700 bg-red-100' :
                        a.severity === 'HIGH'     ? 'text-orange-700 bg-orange-100' :
                                                    'text-yellow-700 bg-yellow-100'
                      }
                    />
                    <p className="text-xs text-slate-400">{relativeTime(a.firedAt)}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <CreateOrganizationModal
        open={showCreateOrg}
        onClose={() => setShowCreateOrg(false)}
        onCreated={handleOrgCreated}
      />
      <CreateProjectModal
        open={showCreateProject}
        organizations={organizations ?? []}
        onClose={() => setShowCreateProject(false)}
        onCreated={handleProjectCreated}
      />
    </div>
  )
}
