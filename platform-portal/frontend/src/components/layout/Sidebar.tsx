import { NavLink, useMatch } from 'react-router-dom'
import {
  LayoutDashboard, FolderOpen, Bell, Key, Activity, Bot,
  FlaskConical, PlayCircle, BarChart3, FileText, GitBranch, Zap, Inbox, Plug, ShieldCheck, Boxes, Users, ShieldAlert, Gauge,
} from 'lucide-react'
import { cn } from '@/lib/utils'

const globalNav = [
  { to: '/',                       label: 'Overview',     icon: LayoutDashboard },
  { to: '/alerts',                 label: 'Alerts',       icon: Bell },
  { to: '/settings/integrations',  label: 'Integrations', icon: Plug },
  { to: '/settings/mapping-rules', label: 'Mapping Rules', icon: Boxes },
  { to: '/settings/roles',         label: 'Roles',        icon: ShieldCheck },
  { to: '/settings/api-keys',      label: 'API Keys',     icon: Key },
  { to: '/settings/ai',            label: 'AI Settings',  icon: Bot },
]

function ProjectNav({ base }: { base: string }) {
  const projectNav = [
    { to: base,                       label: 'Overview',     icon: BarChart3,    end: true },
    { to: `${base}/requirements`,     label: 'Requirements', icon: FileText,     end: false },
    { to: `${base}/teams`,            label: 'Teams & Structure', icon: Users,   end: false },
    { to: `${base}/quality`,          label: 'Quality',      icon: ShieldAlert,  end: false },
    { to: `${base}/productivity`,     label: 'Productivity', icon: Gauge,        end: false },
    { to: `${base}/coverage`,         label: 'Coverage',     icon: ShieldCheck,  end: false },
    { to: `${base}/mapping`,          label: 'Mapping',      icon: Boxes,        end: false },
    { to: `${base}/test-cases`,       label: 'Test Cases',   icon: FlaskConical, end: false },
    { to: `${base}/test-runs`,        label: 'Test Runs',    icon: PlayCircle,   end: false },
    { to: `${base}/impact-analyses`,  label: 'Impact Analyses', icon: GitBranch, end: false },
    { to: `${base}/flaky-tests`,      label: 'Flaky Tests',    icon: Zap,    end: false },
    { to: `${base}/review-queue`,     label: 'Review Queue',   icon: Inbox,  end: false },
  ]

  return (
    <>
      <div className="pt-4 pb-1 px-3">
        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
          Project
        </span>
      </div>
      {projectNav.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
              isActive
                ? 'bg-blue-600 text-white'
                : 'text-slate-400 hover:bg-slate-800 hover:text-white',
            )
          }
        >
          <Icon size={16} />
          {label}
        </NavLink>
      ))}
    </>
  )
}

// First URL segments that are NOT projects (they are global/static routes).
const RESERVED_SEGMENTS = new Set(['settings', 'alerts', 'runs', 'projects'])

export default function Sidebar() {
  const projectMatch = useMatch('/:orgSlug/:projectSlug/*')
  const orgSlug = projectMatch?.params.orgSlug
  const projectSlug = projectMatch?.params.projectSlug
  const projectBase = (orgSlug && projectSlug && !RESERVED_SEGMENTS.has(orgSlug))
    ? `/${orgSlug}/${projectSlug}`
    : null

  return (
    <aside className="w-60 bg-slate-900 flex flex-col shrink-0">
      {/* Logo */}
      <div className="px-6 py-5 border-b border-slate-700">
        <div className="flex items-center gap-2">
          <Activity className="text-blue-400" size={22} />
          <span className="font-semibold text-white text-sm leading-tight">
            Test Platform
          </span>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
        {globalNav.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-blue-600 text-white'
                  : 'text-slate-400 hover:bg-slate-800 hover:text-white',
              )
            }
          >
            <Icon size={16} />
            {label}
          </NavLink>
        ))}

        <div className="pt-4 pb-1 px-3">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Projects
          </span>
        </div>
        <NavLink
          to="/"
          end
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
              isActive
                ? 'bg-blue-600 text-white'
                : 'text-slate-400 hover:bg-slate-800 hover:text-white',
            )
          }
        >
          <FolderOpen size={16} />
          All Projects
        </NavLink>

        {projectBase && <ProjectNav base={projectBase} />}
      </nav>

      {/* Footer */}
      <div className="px-6 py-4 border-t border-slate-700">
        <p className="text-xs text-slate-500">Platform v1.0</p>
      </div>
    </aside>
  )
}
