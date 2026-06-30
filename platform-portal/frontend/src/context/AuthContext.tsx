import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '@/lib/api'
import type { AuthUser } from '@/lib/types'

interface AuthState {
  /** undefined = loading, null = unauthenticated, otherwise the current user. */
  user: AuthUser | null | undefined
  login: (username: string, password: string) => Promise<AuthUser>
  logout: () => Promise<void>
  refresh: () => Promise<void>
  setUser: (u: AuthUser | null) => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null | undefined>(undefined)

  useEffect(() => {
    api
      .authMe()
      .then(setUser)
      .catch(() => setUser(null))
  }, [])

  const login = async (username: string, password: string) => {
    const me = await api.authLogin(username, password)
    setUser(me)
    return me
  }
  const logout = async () => {
    try {
      await api.authLogout()
    } finally {
      setUser(null)
    }
  }
  const refresh = async () => setUser(await api.authMe())

  return (
    <AuthContext.Provider value={{ user, login, logout, refresh, setUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
