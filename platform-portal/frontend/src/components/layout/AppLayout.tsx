import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'

export default function AppLayout() {
  return (
    <div className="flex h-screen bg-slate-50 overflow-hidden">
      <Sidebar />
      {/* main fills remaining width, never overflows at this level — each page/layout manages its own scroll */}
      <main className="flex-1 min-w-0 flex flex-col overflow-hidden">
        <div className="flex-1 min-h-0 max-w-7xl w-full mx-auto px-6 py-8 flex flex-col">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
