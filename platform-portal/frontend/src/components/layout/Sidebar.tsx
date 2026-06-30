import { useState, useEffect } from 'react'
import { NavLink, useMatch, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  LayoutDashboard,
  FolderOpen,
  Bell,
  Key,
  Bot,
  FlaskConical,
  BarChart3,
  FileText,
  GitBranch,
  Zap,
  Inbox,
  Plug,
  ShieldCheck,
  Boxes,
  Users,
  ShieldAlert,
  Gauge,
  Rocket,
  ClipboardCheck,
  Layers,
  MonitorCheck,
  ChevronLeft,
  ChevronRight,
  Building2,
  Settings,
  ArrowLeftRight,
  GitMerge,
  LogOut,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { can, type Capability } from '@/lib/auth'

// ── Types ─────────────────────────────────────────────────────────────────────

type NavItem = {
  to: string
  label: string
  icon: React.ComponentType<{ size?: number; className?: string }>
  end?: boolean
  /** Capability required to see this item; absent ⇒ visible to any signed-in user. */
  cap?: Capability
}
type NavGroup = {
  label: string
  items: NavItem[]
}

// ── Admin nav group (always visible) ─────────────────────────────────────────

const adminGroup: NavGroup = {
  label: 'Admin',
  items: [
    { to: '/settings/organization', label: 'Organization', icon: Building2, cap: 'MANAGE_ORG' },
    { to: '/settings/users', label: 'Users & Roles', icon: Users, cap: 'MANAGE_ORG' },
    { to: '/settings/integrations', label: 'Integrations', icon: Plug, cap: 'MANAGE_ORG' },
    { to: '/settings/mapping-rules', label: 'Mapping Rules', icon: Boxes, cap: 'MANAGE_ORG' },
    { to: '/settings/api-keys', label: 'API Keys', icon: Key, cap: 'MANAGE_ORG' },
    { to: '/settings/ai', label: 'AI Settings', icon: Bot, cap: 'MANAGE_AI_GATEWAY' },
    { to: '/settings/agents', label: 'Agents', icon: Bot, cap: 'OPERATE_QUALITY' },
    { to: '/settings/task-agents', label: 'Task Agents', icon: Bot, cap: 'OPERATE_QUALITY' },
  ],
}

function projectGroups(base: string): NavGroup[] {
  return [
    {
      label: 'Overview',
      items: [
        { to: base, label: 'Dashboard', icon: BarChart3, end: true },
        { to: `${base}/requirements`, label: 'Requirements', icon: FileText },
        { to: `${base}/teams`, label: 'Teams & Structure', icon: Users },
      ],
    },
    {
      label: 'Quality',
      items: [
        { to: `${base}/quality`, label: 'Quality', icon: ShieldAlert },
        { to: `${base}/productivity`, label: 'Productivity', icon: Gauge },
        { to: `${base}/coverage`, label: 'Coverage', icon: ShieldCheck },
        { to: `${base}/mapping`, label: 'Mapping', icon: Boxes },
      ],
    },
    {
      label: 'Testing',
      items: [
        { to: `${base}/test-cases`, label: 'Test Cases', icon: FlaskConical },
        { to: `${base}/test-suites`, label: 'Test Suites', icon: Layers },
        { to: `${base}/test-execution`, label: 'Test Execution', icon: ClipboardCheck },
        { to: `${base}/automated-tests`, label: 'Automated Tests', icon: MonitorCheck },
        { to: `${base}/github-workflows`, label: 'GitHub Workflows', icon: GitMerge },
        { to: `${base}/flaky-tests`, label: 'Flaky Tests', icon: Zap },
        { to: `${base}/review-queue`, label: 'Review Queue', icon: Inbox },
      ],
    },
    {
      label: 'Releases',
      items: [
        { to: `${base}/releases`, label: 'Releases', icon: Rocket },
        { to: `${base}/impact-analyses`, label: 'Impact Analyses', icon: GitBranch },
      ],
    },
    {
      label: 'Settings',
      items: [
        { to: `${base}/settings/general`, label: 'General', icon: Settings, cap: 'MANAGE_PROJECT' },
        { to: `${base}/settings/teams`, label: 'Teams', icon: Users, cap: 'MANAGE_PROJECT' },
        { to: `${base}/settings/integrations`, label: 'Integrations', icon: Plug, cap: 'MANAGE_PROJECT' },
        { to: `${base}/settings/mapping`, label: 'Mapping', icon: Boxes, cap: 'MANAGE_PROJECT' },
        { to: `${base}/settings/ai`, label: 'AI', icon: Bot, cap: 'MANAGE_AI_GATEWAY' },
        { to: `${base}/settings/github`, label: 'GitHub', icon: GitMerge, cap: 'MANAGE_PROJECT' },
      ],
    },
  ]
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const RESERVED_SEGMENTS = new Set(['settings', 'alerts', 'runs', 'projects'])

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

// ── NavItem component ─────────────────────────────────────────────────────────

function NavItemLink({ item, collapsed }: { item: NavItem; collapsed: boolean }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      title={collapsed ? item.label : undefined}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-lg text-sm font-medium transition-all duration-150',
          collapsed ? 'px-2 py-2.5 justify-center' : 'px-3 py-2',
          isActive
            ? 'bg-blue-600 text-white shadow-sm'
            : 'text-slate-400 hover:bg-slate-800 hover:text-white',
        )
      }
    >
      <item.icon size={16} className="shrink-0" />
      {!collapsed && <span className="truncate">{item.label}</span>}
    </NavLink>
  )
}

// ── NavGroup section ──────────────────────────────────────────────────────────

function NavSection({
  group,
  collapsed,
  gate,
}: {
  group: NavGroup
  collapsed: boolean
  /** Returns true if the item may be shown. Defaults to always-visible. */
  gate?: (item: NavItem) => boolean
}) {
  const items = gate ? group.items.filter(gate) : group.items
  if (items.length === 0) return null // hide the whole section when nothing is permitted
  return (
    <div className="space-y-0.5">
      {!collapsed && (
        <p className="px-3 pt-4 pb-1 text-[10px] font-semibold text-slate-500 uppercase tracking-widest select-none">
          {group.label}
        </p>
      )}
      {collapsed && <div className="my-2 border-t border-slate-700/60 mx-2" />}
      {items.map(item => (
        <NavItemLink key={item.to} item={item} collapsed={collapsed} />
      ))}
    </div>
  )
}

// ── Main Sidebar ──────────────────────────────────────────────────────────────

export default function Sidebar() {
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('sidebar-collapsed') === 'true'
    } catch {
      return false
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem('sidebar-collapsed', String(collapsed))
    } catch {}
  }, [collapsed])

  const projectMatch = useMatch('/:orgSlug/:projectSlug/*')
  const orgMatch = useMatch('/:orgSlug')

  const rawOrgSlug = projectMatch?.params.orgSlug ?? orgMatch?.params.orgSlug
  const rawProjectSlug = projectMatch?.params.projectSlug

  // Filter out reserved static-segment paths that also match /:orgSlug
  const orgSlug = rawOrgSlug && !RESERVED_SEGMENTS.has(rawOrgSlug) ? rawOrgSlug : undefined
  const projectSlug =
    rawProjectSlug && !RESERVED_SEGMENTS.has(rawProjectSlug) ? rawProjectSlug : undefined
  const projectBase = orgSlug && projectSlug ? `/${orgSlug}/${projectSlug}` : null
  const orgBase = orgSlug ? `/${orgSlug}` : null

  // Data
  const { data: orgs } = useQuery({ queryKey: ['organizations'], queryFn: api.organizations })
  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => api.projects() })

  const currentOrg = (() => {
    if (orgSlug) return orgs?.find(o => o.slug === orgSlug) ?? null
    return orgs?.[0] ?? null
  })()

  const orgName = currentOrg?.displayName ?? currentOrg?.name ?? 'Test Platform'
  const hue = orgHue(orgName)
  const orgProjects = orgSlug ? (projects ?? []).filter(p => p.orgSlug === orgSlug) : []

  // ── Role-based nav gating ────────────────────────────────────────────────
  const { user, logout } = useAuth()
  const accountName = user?.displayName || user?.username || ''
  const accountInitials =
    accountName
      .split(/[\s@._-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(w => w[0]?.toUpperCase() ?? '')
      .join('') || '?'
  const currentProject =
    orgSlug && projectSlug
      ? (projects ?? []).find(p => p.orgSlug === orgSlug && p.slug === projectSlug)
      : undefined
  // Global admin items: "can the user do this anywhere?" (no scope in the URL).
  const gateGlobal = (item: NavItem) => !item.cap || can(user, item.cap)
  // Project settings items: scoped to the project currently in context.
  const gateProject = (item: NavItem) =>
    !item.cap || can(user, item.cap, { projectId: currentProject?.id, orgId: currentOrg?.id })

  // Workspace overview link: org home if we have an org, else org selector
  const overviewLink = orgBase ?? '/'

  return (
    <aside
      className={cn(
        'bg-slate-900 flex flex-col shrink-0 transition-all duration-200 ease-in-out',
        collapsed ? 'w-[64px]' : 'w-60',
      )}
    >
      {/* ── Platform Branding Header ─────────────────────────────── */}
      <div className="border-b border-slate-700/80">
        {/* Logo + name row */}
        <div
          className={cn(
            'flex items-center gap-3',
            collapsed ? 'px-2 py-4 justify-center' : 'px-4 pt-4 pb-2',
          )}
        >
          {currentOrg?.logoUrl ? (
            <img
              src={currentOrg.logoUrl}
              className="shrink-0 w-8 h-8 rounded-lg object-cover shadow-sm"
              alt={orgName}
              title={orgName}
            />
          ) : (
            <div
              className="shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-white text-xs font-bold select-none shadow-sm"
              style={{ background: `hsl(${hue},60%,42%)` }}
              title={orgName}
            >
              {orgInitials(orgName)}
            </div>
          )}

          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-white truncate leading-tight">{orgName}</p>
              <p className="text-[10px] text-slate-400 leading-tight">Test Platform</p>
              {orgSlug && (
                <p className="text-[10px] text-slate-500 font-mono leading-tight truncate">
                  @{orgSlug}
                </p>
              )}
            </div>
          )}
        </div>

        {/* Switch org button */}
        {!collapsed && (
          <div className="px-4 pb-3">
            <button
              onClick={() => navigate('/')}
              className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] text-slate-400 border border-slate-700/80 rounded-lg hover:bg-slate-800 hover:text-white transition-colors"
            >
              <ArrowLeftRight size={11} />
              Switch Organization
            </button>
          </div>
        )}
      </div>

      {/* ── Nav ────────────────────────────────────────────────────── */}
      <nav className="flex-1 px-2 py-3 overflow-y-auto space-y-0 scrollbar-thin scrollbar-thumb-slate-700">
        {/* Workspace group (always visible) */}
        <div className="space-y-0.5">
          {!collapsed && (
            <p className="px-3 pt-2 pb-1 text-[10px] font-semibold text-slate-500 uppercase tracking-widest select-none">
              Workspace
            </p>
          )}
          <NavItemLink
            item={{ to: overviewLink, label: 'Overview', icon: LayoutDashboard, end: true }}
            collapsed={collapsed}
          />
          <NavItemLink
            item={{ to: '/alerts', label: 'Alerts', icon: Bell }}
            collapsed={collapsed}
          />
        </div>

        {/* Org-level project list (only when in org context but no specific project) */}
        {!projectBase && orgBase && orgProjects.length > 0 && (
          <div className="space-y-0.5">
            {!collapsed && (
              <p className="px-3 pt-4 pb-1 text-[10px] font-semibold text-slate-500 uppercase tracking-widest select-none">
                Projects
              </p>
            )}
            {collapsed && <div className="my-2 border-t border-slate-700/60 mx-2" />}
            {orgProjects.map(p => (
              <NavItemLink
                key={p.id}
                item={{ to: `/${orgSlug}/${p.slug}`, label: p.name, icon: FolderOpen }}
                collapsed={collapsed}
              />
            ))}
          </div>
        )}

        {/* Per-project groups (only when inside a project) */}
        {projectBase &&
          projectGroups(projectBase).map(g => (
            <NavSection key={g.label} group={g} collapsed={collapsed} gate={gateProject} />
          ))}

        {/* Admin settings group — each item gated by the matching capability */}
        <NavSection group={adminGroup} collapsed={collapsed} gate={gateGlobal} />
      </nav>

      {/* ── Signed-in user + sign out ──────────────────────────────── */}
      {user && (
        <div className="border-t border-slate-700/80">
          {collapsed ? (
            <button
              onClick={() => logout()}
              title={`Sign out (${accountName})`}
              className="w-full flex items-center justify-center py-3 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              <LogOut size={16} />
            </button>
          ) : (
            <div className="flex items-center gap-2.5 px-3 py-2.5">
              <div className="shrink-0 w-7 h-7 rounded-full bg-slate-700 text-white text-[11px] font-semibold flex items-center justify-center select-none">
                {accountInitials}
              </div>
              <div className="min-w-0 flex-1 leading-tight">
                <p className="text-xs font-medium text-white truncate" title={accountName}>
                  {accountName}
                </p>
                <p className="text-[10px] text-slate-400 truncate">
                  {user.superAdmin ? 'Super admin' : `@${user.username}`}
                </p>
              </div>
              <button
                onClick={() => logout()}
                title="Sign out"
                className="shrink-0 rounded-md p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
              >
                <LogOut size={15} />
              </button>
            </div>
          )}
        </div>
      )}

      {/* ── Collapse toggle ─────────────────────────────────────────── */}
      <div className="border-t border-slate-700/80">
        <button
          onClick={() => setCollapsed(c => !c)}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className={cn(
            'w-full flex items-center gap-2 px-4 py-3 text-xs text-slate-500',
            'hover:text-slate-300 hover:bg-slate-800 transition-colors',
            collapsed && 'justify-center px-2',
          )}
        >
          {collapsed ? (
            <ChevronRight size={15} />
          ) : (
            <>
              <ChevronLeft size={15} />
              <span>Collapse</span>
            </>
          )}
        </button>
      </div>
    </aside>
  )
}
