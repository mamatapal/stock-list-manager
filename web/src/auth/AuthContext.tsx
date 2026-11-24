import { createContext, useCallback, useContext, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react'
import { login as apiLogin, logout as apiLogout, refreshSession } from '../api/client'
import { can as canDo, getRefreshToken, getUser, subscribe, type Me } from './session'

type Status = 'loading' | 'out' | 'in'

interface AuthValue {
  status: Status
  user: Me | null
  can: (permission: string) => boolean
  signIn: (username: string, password: string, role: 'OWNER' | 'STAFF') => Promise<void>
  signOut: () => Promise<void>
}

const Ctx = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const user = useSyncExternalStore(subscribe, getUser, getUser)
  // When the app is reopened, a saved refresh token is swapped for a fresh session before anything is shown.
  const [restoring, setRestoring] = useState<boolean>(() => !getUser() && !!getRefreshToken())

  useEffect(() => {
    if (!restoring) return
    let alive = true
    refreshSession().catch(() => { /* offline: stay signed out for now, the token is kept */ }).finally(() => { if (alive) setRestoring(false) })
    return () => { alive = false }
  }, [restoring])

  const signIn = useCallback(async (u: string, p: string, r: 'OWNER' | 'STAFF') => { await apiLogin(u, p, r) }, [])
  const signOut = useCallback(async () => { await apiLogout() }, [])

  const value = useMemo<AuthValue>(() => ({
    status: user ? 'in' : restoring ? 'loading' : 'out',
    user,
    can: (permission) => canDo(permission),
    signIn,
    signOut,
  }), [user, restoring, signIn, signOut])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth(): AuthValue {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth must be used inside AuthProvider')
  return v
}
