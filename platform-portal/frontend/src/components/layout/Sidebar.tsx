import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, FolderOpen, Bell, Key, Activity, Bot,
} from 'lucide-react'
import { cn } from '@/lib/utils'

const nav = [
  { to: '/',                   label: 'Overview',    icon: LayoutDashboard },
  { to: '/alerts',             label: 'Alerts',      icon: Bell },
  { to: '/settings/api-keys',  label: 'API Keys',    icon: Key },
  { to: '/settings/ai',        label: 'AI Settings', icon: Bot },
]

export default function Sidebar() {
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
      <nav className="flex-1 px-3 py-4 space-y-0.5">
        {nav.map(({ to, label, icon: Icon }) => (
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
          className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
        >
          <FolderOpen size={16} />
          All Projects
        </NavLink>
      </nav>

      {/* Footer */}
      <div className="px-6 py-4 border-t border-slate-700">
        <p className="text-xs text-slate-500">Platform v1.0</p>
      </div>
    </aside>
  )
}
