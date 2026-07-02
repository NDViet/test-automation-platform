import { createContext, useContext, useEffect } from 'react'
import { useState } from 'react'

export type PageWidth = 'default' | 'wide' | 'full'

const WidthCtx = createContext<{ width: PageWidth; setWidth: (w: PageWidth) => void } | null>(null)

export function PageWidthProvider({ children }: { children: React.ReactNode }) {
  const [width, setWidth] = useState<PageWidth>('default')
  return <WidthCtx.Provider value={{ width, setWidth }}>{children}</WidthCtx.Provider>
}

/** The active content-container width (consumed by AppLayout). */
export function useActiveWidth(): PageWidth {
  return useContext(WidthCtx)?.width ?? 'default'
}

/**
 * Opt a page into a wider content container. Data-dense pages (tables,
 * dashboards) call `usePageWidth('wide')` (or `'full'`); forms/settings keep the
 * comfortable default. Resets when the page unmounts.
 */
export function usePageWidth(w: PageWidth) {
  const ctx = useContext(WidthCtx)
  const setWidth = ctx?.setWidth
  useEffect(() => {
    setWidth?.(w)
    return () => setWidth?.('default')
  }, [setWidth, w])
}
