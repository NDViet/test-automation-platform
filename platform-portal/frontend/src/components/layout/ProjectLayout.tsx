import { createContext, useContext, useEffect, useState } from 'react'
import { useParams, Outlet, Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Project } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import PathSelect from '@/components/PathSelect'
import { LayoutGrid, Users, CalendarRange, X } from 'lucide-react'

interface ProjectCtx {
  project: Project
  /** Project UUID — used for all data/API calls. */
  projectId: string
  /** Slug base path, e.g. "/payments/checkout" — used to build links. */
  base: string
}

const Ctx = createContext<ProjectCtx | null>(null)

export function useProject(): ProjectCtx {
  const c = useContext(Ctx)
  if (!c) throw new Error('useProject must be used within a ProjectLayout route')
  return c
}

/** Convenience hook returning the project UUID. */
export function useProjectId(): string {
  return useProject().projectId
}

// ── Project-level scope filter (Area + Team mandatory dimensions, Iteration optional) ──
// Persists across menu navigation within a project so every object list/dashboard can
// honor the same scope. Area/Team apply to all objects; Iteration to those that carry it.
export interface ProjectFilter {
  area: string // area_path ('' = all)
  teamId: string // ado_teams id ('' = all)
  iteration: string // iteration_path ('' = all)
}

interface FilterCtx {
  filter: ProjectFilter
  setFilter: (f: Partial<ProjectFilter>) => void
  clear: () => void
  active: boolean
}

const FCtx = createContext<FilterCtx | null>(null)

/** Project scope filter shared across all menu pages. */
export function useProjectFilter(): FilterCtx {
  const c = useContext(FCtx)
  if (!c) throw new Error('useProjectFilter must be used within a ProjectLayout route')
  return c
}

const EMPTY: ProjectFilter = { area: '', teamId: '', iteration: '' }

function FilterBar({ projectId }: { projectId: string }) {
  const { filter, setFilter, clear, active } = useProjectFilter()
  const { data: areas = [] } = useQuery({
    queryKey: ['adoAreas', projectId],
    queryFn: () => api.adoAreas(projectId),
  })
  const { data: teams = [] } = useQuery({
    queryKey: ['adoTeams', projectId],
    queryFn: () => api.adoTeams(projectId),
  })
  const { data: iterations = [] } = useQuery({
    queryKey: ['adoIterations', projectId],
    queryFn: () => api.adoIterations(projectId),
  })

  const sel =
    'border border-border-strong rounded-md pl-7 pr-2 py-1.5 text-sm bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary max-w-[15rem]'
  return (
    <div className="sticky top-0 z-20 -mx-6 px-6 py-2.5 mb-4 bg-surface/95 backdrop-blur border-b border-border flex items-center gap-3 flex-wrap">
      <span className="text-xs font-semibold text-fg-muted uppercase tracking-wide">Scope</span>
      <div className="relative">
        <Users size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-fg-subtle" />
        <select
          className={sel}
          value={filter.teamId}
          onChange={e => setFilter({ teamId: e.target.value })}
          title="Team"
        >
          <option value="">All teams</option>
          {teams.map(t => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <PathSelect
        className="w-[15rem]"
        leftIcon={<LayoutGrid size={13} className="text-fg-subtle shrink-0" />}
        value={filter.area}
        onChange={v => setFilter({ area: v })}
        placeholder="All areas"
        options={areas.map(a => ({ value: a.path, label: a.path }))}
      />
      <PathSelect
        className="w-[15rem]"
        leftIcon={<CalendarRange size={13} className="text-fg-subtle shrink-0" />}
        value={filter.iteration}
        onChange={v => setFilter({ iteration: v })}
        placeholder="All sprints"
        options={iterations.map(it => ({ value: it.path, label: it.path }))}
      />
      {active && (
        <button
          onClick={clear}
          className="flex items-center gap-1 text-xs text-fg-muted hover:text-fg"
        >
          <X size={13} /> Clear
        </button>
      )}
    </div>
  )
}

/**
 * Resolves the human-readable {@code /:orgSlug/:projectSlug} URL to a project and
 * provides its UUID (for API calls) + slug base path (for links) + the shared
 * project-scope filter via context. Renders the persistent filter bar above each page.
 */
export default function ProjectLayout() {
  const { orgSlug, projectSlug } = useParams<{ orgSlug: string; projectSlug: string }>()

  const {
    data: projects,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.projects(),
  })

  const project = projects?.find(p => p.orgSlug === orgSlug && p.slug === projectSlug)
  const projectId = project?.id ?? ''

  // Filter state, persisted per project across navigation.
  const storeKey = `pf:${projectId}`
  const [filter, setFilterState] = useState<ProjectFilter>(EMPTY)
  useEffect(() => {
    if (!projectId) return
    try {
      const raw = sessionStorage.getItem(storeKey)
      setFilterState(raw ? { ...EMPTY, ...JSON.parse(raw) } : EMPTY)
    } catch {
      setFilterState(EMPTY)
    }
  }, [projectId, storeKey])

  function setFilter(patch: Partial<ProjectFilter>) {
    setFilterState(prev => {
      const next = { ...prev, ...patch }
      try {
        sessionStorage.setItem(storeKey, JSON.stringify(next))
      } catch {
        /* ignore */
      }
      return next
    })
  }
  function clear() {
    setFilterState(EMPTY)
    try {
      sessionStorage.removeItem(storeKey)
    } catch {
      /* ignore */
    }
  }
  const active = !!(filter.area || filter.teamId || filter.iteration)

  if (isLoading) return <LoadingSpinner message="Loading project…" />
  if (error) return <ErrorMessage message="Failed to load project." />
  if (!project) return <ErrorMessage message={`Project "${orgSlug}/${projectSlug}" not found.`} />

  const value: ProjectCtx = {
    project,
    projectId: project.id,
    base: `/${orgSlug}/${projectSlug}`,
  }
  return (
    <Ctx.Provider value={value}>
      <FCtx.Provider value={{ filter, setFilter, clear, active }}>
        {/* Flex column so FilterBar stays fixed-height and Outlet fills remaining space */}
        <div className="flex-1 min-h-0 flex flex-col">
          <FilterBar projectId={project.id} />
          {/* overflow-y-auto here so normal (non-height-managed) pages scroll naturally.
              Pages that control their own height (e.g. TestCasesPage) use h-full + overflow-hidden
              to cap themselves within this bounded container instead of letting it scroll. */}
          <div className="flex-1 min-h-0 overflow-y-auto">
            <Outlet />
          </div>
        </div>
      </FCtx.Provider>
    </Ctx.Provider>
  )
}

/**
 * Back-compat: redirects legacy UUID URLs ({@code /projects/:id/...}) to the
 * human-readable slug URL ({@code /:orgSlug/:projectSlug/...}). Keeps existing
 * in-app links and bookmarks working.
 */
export function LegacyProjectRedirect() {
  const params = useParams<{ projectId: string; '*': string }>()
  const { data: projects, isLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.projects(),
  })

  if (isLoading) return <LoadingSpinner message="Resolving project…" />
  const project = projects?.find(p => p.id === params.projectId)
  if (!project) return <Navigate to="/" replace />

  const rest = params['*'] ? `/${params['*']}` : ''
  return <Navigate to={`/${project.orgSlug}/${project.slug}${rest}`} replace />
}
