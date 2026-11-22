import { clearSession, deviceId, getAccessToken, getRefreshToken, saveSession, type SessionResponse } from '../auth/session'
import { tr } from '../i18n/lang'

/** An error answer from the server (RFC 7807 problem+json with a stable "code"). */
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fieldErrors: Array<{ field: string; message: string }> = []) {
    super(message)
  }
}

/** The server could not be reached (offline, DNS, timeout). */
export class NetworkError extends Error {
  constructor() { super('network') }
}

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? ''

async function problem(res: Response): Promise<ApiError> {
  let body: { code?: string; detail?: string; errors?: Array<{ field: string; message: string }> } = {}
  try { body = await res.json() } catch { /* not JSON */ }
  return new ApiError(res.status, body.code ?? 'ERROR', body.detail ?? '', body.errors ?? [])
}

async function send(method: string, path: string, body: unknown, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    return await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new NetworkError()
  }
}

let refreshing: Promise<string | null> | null = null

/**
 * Swaps the refresh token for a new pair (the server rotates it). Calls that fail at the same moment share one refresh.
 * Resolves to the new access token, or null when the session is over. Going offline keeps the session.
 */
export function refreshSession(): Promise<string | null> {
  refreshing ??= (async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) return null
    const res = await send('POST', '/api/v1/auth/refresh', { refreshToken }, null)
    if (!res.ok) {
      const e = await problem(res)
      clearSession(e.code === 'ACCOUNT_INACTIVE' && e.message ? e.message : tr('Your session has ended. Please sign in again.'))
      return null
    }
    const s = (await res.json()) as SessionResponse
    saveSession(s)
    return s.accessToken
  })().finally(() => { refreshing = null })
  return refreshing
}

export interface RequestOptions { auth?: boolean }

export async function request<T>(method: string, path: string, body?: unknown, { auth = true }: RequestOptions = {}): Promise<T> {
  let token = auth ? getAccessToken() : null
  if (auth && !token && getRefreshToken()) token = await refreshSession()
  let res = await send(method, path, body, token)
  if (res.status === 401 && auth) {
    const fresh = await refreshSession()
    if (!fresh) throw new ApiError(401, 'SESSION_ENDED', tr('Your session has ended. Please sign in again.'))
    res = await send(method, path, body, fresh)
  }
  if (!res.ok) throw await problem(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
  delete: <T>(path: string) => request<T>('DELETE', path),
}

export async function login(username: string, password: string, role: 'OWNER' | 'STAFF'): Promise<SessionResponse> {
  const s = await request<SessionResponse>('POST', '/api/v1/auth/login', {
    username: username.trim(),
    password,
    deviceId: deviceId(),
    deviceName: 'Web browser',
    appVersion: 'web',
    expectedRole: role,
  }, { auth: false })
  saveSession(s)
  return s
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken()
  try {
    if (refreshToken) await request<void>('POST', '/api/v1/auth/logout', { refreshToken })
  } catch { /* signing out always works locally */ }
  clearSession()
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const s = await request<SessionResponse>('POST', '/api/v1/auth/password/change', { currentPassword, newPassword })
  saveSession(s)
}
