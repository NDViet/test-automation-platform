import { Outlet } from 'react-router-dom'
import { cn } from '@/lib/utils'
import Sidebar from './Sidebar'
import { PageWidthProvider, useActiveWidth, type PageWidth } from './PageWidth'

const WIDTH: Record<PageWidth, string> = {
  default: 'max-w-7xl', // forms, settings, reading-width content
  wide: 'max-w-[1600px]', // tables, dashboards — use more of a wide monitor
  full: 'max-w-none', // edge-to-edge (board / matrix views)
}

function Content() {
  const width = useActiveWidth()
  return (
    // main fills remaining width, never overflows at this level — each page/layout manages its own scroll
    <main className="flex-1 min-w-0 flex flex-col overflow-hidden">
      <div className={cn('flex-1 min-h-0 w-full mx-auto px-6 py-6 flex flex-col', WIDTH[width])}>
        <Outlet />
      </div>
    </main>
  )
}

export default function AppLayout() {
  return (
    <PageWidthProvider>
      <div className="flex h-screen bg-canvas overflow-hidden">
        <Sidebar />
        <Content />
      </div>
    </PageWidthProvider>
  )
}
