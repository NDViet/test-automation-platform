/**
 * Client-side capability model — mirrors the backend `Tier`/`Capability` rules
 * (platform-security) so the UI can hide/disable what the user can't do. This is
 * advisory only: the backend is the real enforcement point. Keep these tables in
 * sync with `com.platform.security.authz.{Tier,Capability,RoleResolver}`.
 */
import type { AuthUser } from './types'

/** Ordered low→high; a higher tier satisfies every lower requirement. */
export const TIERS = ['NONE', 'VIEW', 'OPERATE', 'ADMIN_PROJECT', 'ADMIN_ORG', 'SUPER'] as const
export type Tier = (typeof TIERS)[number]

const TIER_RANK: Record<Tier, number> = TIERS.reduce(
  (acc, t, i) => ({ ...acc, [t]: i }),
  {} as Record<Tier, number>,
)

export type Capability =
  | 'VIEW_RESULTS'
  | 'OPERATE_QUALITY'
  | 'MANAGE_PROJECT'
  | 'MANAGE_ORG'
  | 'MANAGE_PLATFORM'
  | 'MANAGE_AI_GATEWAY'
  | 'IMPORT_ADO_STRUCTURE'

/** Minimum tier each capability needs — mirrors Capability.minTier(). */
const CAP_MIN_TIER: Record<Capability, Tier> = {
  VIEW_RESULTS: 'VIEW',
  OPERATE_QUALITY: 'OPERATE',
  MANAGE_PROJECT: 'ADMIN_PROJECT',
  MANAGE_ORG: 'ADMIN_ORG',
  MANAGE_PLATFORM: 'SUPER',
  MANAGE_AI_GATEWAY: 'SUPER',
  IMPORT_ADO_STRUCTURE: 'SUPER',
}

/** Capabilities whose natural scope is the organization (not a project). */
const ORG_SCOPED: ReadonlySet<Capability> = new Set<Capability>(['MANAGE_ORG'])

const ROLE_TIER: Record<string, Tier> = {
  VIEWER: 'VIEW',
  TESTER: 'OPERATE',
  PROJECT_ADMIN: 'ADMIN_PROJECT',
  ORG_ADMIN: 'ADMIN_ORG',
}

function roleTier(role: string): Tier {
  return ROLE_TIER[role] ?? 'NONE'
}

function maxTier(a: Tier, b: Tier): Tier {
  return TIER_RANK[a] >= TIER_RANK[b] ? a : b
}

/** Effective tier on a specific project (org-admin of the project's org cascades). */
export function tierForProject(user: AuthUser, projectId?: string, orgId?: string): Tier {
  if (user.superAdmin) return 'SUPER'
  let t: Tier = 'NONE'
  for (const g of user.roles) {
    if (g.scope === 'PROJECT' && projectId && g.scopeId === projectId) {
      t = maxTier(t, roleTier(g.role))
    }
    // Org-scoped grants cascade to every project in the org: ORG_ADMIN manages, and an org-wide
    // TESTER/VIEWER operates/reads across all of them. When we know the project's org, match it;
    // otherwise any org grant counts (UI is advisory — the backend is authoritative).
    if (g.scope === 'ORG' && (!orgId || g.scopeId === orgId)) {
      if (g.role === 'ORG_ADMIN') t = maxTier(t, 'ADMIN_ORG')
      else if (g.role === 'TESTER') t = maxTier(t, 'OPERATE')
      else if (g.role === 'VIEWER') t = maxTier(t, 'VIEW')
    }
  }
  return t
}

/** Effective tier on a specific organization. */
export function tierForOrg(user: AuthUser, orgId?: string): Tier {
  if (user.superAdmin) return 'SUPER'
  let t: Tier = 'NONE'
  for (const g of user.roles) {
    if (g.scope === 'ORG' && (!orgId || g.scopeId === orgId)) {
      t = maxTier(t, roleTier(g.role))
    }
  }
  return t
}

/** Highest tier the user holds anywhere — used to gate global nav (no scope in URL). */
export function tierAnywhere(user: AuthUser): Tier {
  if (user.superAdmin) return 'SUPER'
  let t: Tier = 'NONE'
  for (const g of user.roles) t = maxTier(t, roleTier(g.role))
  return t
}

export interface ScopeOpts {
  projectId?: string
  orgId?: string
}

/**
 * Whether the user may perform a capability. With a scope (projectId/orgId) the
 * check is scoped; without one it asks "anywhere" — right for global nav items.
 */
export function can(
  user: AuthUser | null | undefined,
  capability: Capability,
  opts: ScopeOpts = {},
): boolean {
  if (!user) return false
  if (user.superAdmin) return true
  const required = CAP_MIN_TIER[capability]
  if (required === 'SUPER') return false // super-only, and we already handled super

  let userTier: Tier
  if (ORG_SCOPED.has(capability)) {
    userTier = opts.orgId ? tierForOrg(user, opts.orgId) : orgTierAnywhere(user)
  } else if (opts.projectId) {
    userTier = tierForProject(user, opts.projectId, opts.orgId)
  } else {
    userTier = tierAnywhere(user)
  }
  return TIER_RANK[userTier] >= TIER_RANK[required]
}

/** Highest org-tier held in any org (for global org-scoped nav). */
function orgTierAnywhere(user: AuthUser): Tier {
  let t: Tier = 'NONE'
  for (const g of user.roles) {
    if (g.scope === 'ORG') t = maxTier(t, roleTier(g.role))
  }
  return t
}

export function isSuperAdmin(user: AuthUser | null | undefined): boolean {
  return !!user?.superAdmin
}
