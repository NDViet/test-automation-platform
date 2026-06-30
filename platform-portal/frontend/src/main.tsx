import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider, QueryCache, MutationCache } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'
import { FORBIDDEN_EVENT } from './components/ForbiddenToast'

/** The api layer throws Errors ending in "→ <status>"; pull the HTTP status back out. */
function statusOf(error: unknown): number | undefined {
  const m = error instanceof Error ? error.message.match(/→\s*(\d{3})\b/) : null
  return m ? Number(m[1]) : undefined
}

/** Global auth-error handling: 401 ⇒ re-authenticate, 403 ⇒ friendly notice. */
function handleAuthError(error: unknown) {
  const status = statusOf(error)
  if (status === 401) {
    // Session expired/invalid — drop cached data and let AuthGate show the login screen.
    queryClient.clear()
    if (window.location.pathname !== '/') window.location.assign('/')
  } else if (status === 403) {
    window.dispatchEvent(new CustomEvent(FORBIDDEN_EVENT))
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: handleAuthError }),
  mutationCache: new MutationCache({ onError: handleAuthError }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
