import type { ReactNode } from 'react'
import { ShieldAlert } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { can, type Capability, type ScopeOpts } from '@/lib/auth'

/**
 * Renders {@code children} only when the current user holds {@code cap} at the given
 * scope; otherwise renders {@code fallback} (nothing by default). The backend still
 * enforces — this just hides/disables UI the user can't act on.
 */
export function Can({
  cap,
  projectId,
  orgId,
  fallback = null,
  children,
}: {
  cap: Capability
  fallback?: ReactNode
  children: ReactNode
} & ScopeOpts) {
  const { user } = useAuth()
  return <>{can(user, cap, { projectId, orgId }) ? children : fallback}</>
}

/** Imperative form for non-JSX gating (e.g. building a filtered list). */
export function useCan(cap: Capability, opts: ScopeOpts = {}): boolean {
  const { user } = useAuth()
  return can(user, cap, opts)
}

/** Friendly full-page notice shown when a route is opened without permission. */
export function Forbidden({
  message = "You don't have permission to view this page.",
}: {
  message?: string
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-100 text-amber-600">
        <ShieldAlert size={24} />
      </div>
      <h2 className="text-lg font-semibold text-slate-800">Access restricted</h2>
      <p className="max-w-sm text-sm text-slate-500">{message}</p>
    </div>
  )
}

/** Route-level guard: shows {@link Forbidden} instead of the page when not permitted. */
export function RequireCap({
  cap,
  projectId,
  orgId,
  children,
}: {
  cap: Capability
  children: ReactNode
} & ScopeOpts) {
  const { user } = useAuth()
  if (!can(user, cap, { projectId, orgId })) return <Forbidden />
  return <>{children}</>
}
