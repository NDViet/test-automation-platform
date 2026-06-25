import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

const selectCls =
  'text-sm border border-slate-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500'

/** Organization picker — value/onChange carry the organization UUID. */
export function OrganizationSelect({
  value,
  onChange,
  className,
}: {
  value: string
  onChange: (id: string) => void
  className?: string
}) {
  const { data: orgs } = useQuery({ queryKey: ['organizations'], queryFn: api.organizations })

  useEffect(() => {
    if (!value && orgs && orgs.length > 0) onChange(orgs[0].id)
  }, [orgs, value, onChange])

  return (
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className={className ?? selectCls}
    >
      {(orgs ?? []).length === 0 && <option value="">No organizations</option>}
      {(orgs ?? []).map(o => (
        <option key={o.id} value={o.id}>
          {o.name}
        </option>
      ))}
    </select>
  )
}

/** Project picker — value/onChange carry the project UUID. */
export function ProjectSelect({
  value,
  onChange,
  className,
}: {
  value: string
  onChange: (id: string) => void
  className?: string
}) {
  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => api.projects() })

  useEffect(() => {
    if (!value && projects && projects.length > 0) onChange(projects[0].id)
  }, [projects, value, onChange])

  return (
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className={className ?? selectCls}
    >
      {(projects ?? []).length === 0 && <option value="">No projects</option>}
      {(projects ?? []).map(p => (
        <option key={p.id} value={p.id}>
          {p.orgSlug} / {p.name}
        </option>
      ))}
    </select>
  )
}

/** Team picker scoped to a project — value/onChange carry the team UUID. */
export function TeamSelect({
  projectId,
  value,
  onChange,
  className,
}: {
  projectId: string
  value: string
  onChange: (id: string) => void
  className?: string
}) {
  const { data: teams } = useQuery({
    queryKey: ['teams', projectId],
    queryFn: () => api.teams(projectId),
    enabled: !!projectId,
  })

  useEffect(() => {
    if (teams && !teams.some(t => t.id === value)) onChange(teams[0]?.id ?? '')
  }, [teams, value, onChange])

  return (
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className={className ?? selectCls}
    >
      {(teams ?? []).length === 0 && <option value="">No teams in project</option>}
      {(teams ?? []).map(t => (
        <option key={t.id} value={t.id}>
          {t.name}
        </option>
      ))}
    </select>
  )
}
