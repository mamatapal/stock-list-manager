/**
 * The signed-in session. The short-lived access token and the user live in memory only. The refresh token is kept in
 * localStorage so the app stays signed in when it is reopened from the home screen (the server rotates it on every use,
 * and the app's Content-Security-Policy blocks outside scripts).
 */
export interface Me {
  userId: string
  username: string
  fullName: string
  role: 'OWNER' | 'STAFF'
  businessId: string
  businessName: string
  mustChangePassword: boolean
  permissions: string[]
}

export interface SessionResponse {
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
  user: Me
}

const REFRESH = 'slm.refresh'
const DEVICE = 'slm.device'

let accessToken: string | null = null
let user: Me | null = null
let endedMessage: string | null = null
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((f) => f())

function read(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}
function write(key: string, value: string | null): void {
  try { if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value) } catch { /* ignore */ }
}

export const subscribe = (f: () => void) => { listeners.add(f); return () => { listeners.delete(f) } }
export const getAccessToken = () => accessToken
export const getRefreshToken = () => read(REFRESH)
export const getUser = () => user
/** Why the user was signed out (shown once on the sign-in page). */
export const takeEndedMessage = () => { const m = endedMessage; endedMessage = null; return m }

/** A stable id for this browser; the server records each sign-in as a device. */
export function deviceId(): string {
  let id = read(DEVICE)
  if (!id || !/^[A-Za-z0-9-]{8,64}$/.test(id)) {
    id = 'web-' + crypto.randomUUID()
    write(DEVICE, id)
  }
  return id
}

export function saveSession(s: SessionResponse): void {
  accessToken = s.accessToken
  user = s.user
  endedMessage = null
  write(REFRESH, s.refreshToken)
  emit()
}

export function clearSession(message: string | null = null): void {
  accessToken = null
  user = null
  endedMessage = message
  write(REFRESH, null)
  emit()
}

export const can = (permission: string): boolean => !!user && user.permissions.includes(permission)
