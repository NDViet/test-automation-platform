import { createContext, useContext } from 'react'
import { useParams, Outlet, Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Project } from '@/lib/types'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'

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

/**
 * Resolves the human-readable {@code /:orgSlug/:projectSlug} URL to a project and
 * provides its UUID (for API calls) + slug base path (for links) via context.
 */
export default function ProjectLayout() {
  const { orgSlug, projectSlug } = useParams<{ orgSlug: string; projectSlug: string }>()

  const { data: projects, isLoading, error } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.projects(),
  })

  if (isLoading) return <LoadingSpinner message="Loading project…" />
  if (error) return <ErrorMessage message="Failed to load project." />

  const project = projects?.find(p => p.orgSlug === orgSlug && p.slug === projectSlug)
  if (!project) {
    return (
      <ErrorMessage message={`Project "${orgSlug}/${projectSlug}" not found.`} />
    )
  }

  const value: ProjectCtx = {
    project,
    projectId: project.id,
    base: `/${orgSlug}/${projectSlug}`,
  }
  return <Ctx.Provider value={value}><Outlet /></Ctx.Provider>
}

/**
 * Back-compat: redirects legacy UUID URLs ({@code /projects/:id/...}) to the
 * human-readable slug URL ({@code /:orgSlug/:projectSlug/...}). Keeps existing
 * in-app links and bookmarks working.
 */
export function LegacyProjectRedirect() {
  const params = useParams<{ projectId: string; '*': string }>()
  const { data: projects, isLoading } = useQuery({ queryKey: ['projects'], queryFn: () => api.projects() })

  if (isLoading) return <LoadingSpinner message="Resolving project…" />
  const project = projects?.find(p => p.id === params.projectId)
  if (!project) return <Navigate to="/" replace />

  const rest = params['*'] ? `/${params['*']}` : ''
  return <Navigate to={`/${project.orgSlug}/${project.slug}${rest}`} replace />
}
