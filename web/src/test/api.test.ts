import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError, api, changePassword, login, logout, refreshSession } from '../api/client'
import { can, clearSession, getAccessToken, getRefreshToken, getUser, takeEndedMessage, type Me, type SessionResponse } from '../auth/session'

const me = (over: Partial<Me> = {}): Me => ({
  userId: 'u1', username: 'asha', fullName: 'Asha Sharma', role: 'OWNER', businessId: 'b1', businessName: 'Sharma Store',
  mustChangePassword: false, permissions: ['ITEM_VIEW', 'PDF_GENERATE'], ...over,
})
const session = (n: number, over: Partial<Me> = {}): SessionResponse => ({
  accessToken: `access-${n}`, accessTokenExpiresAt: '2030-01-01T00:00:00Z', refreshToken: `refresh-${n}`,
  refreshTokenExpiresAt: '2030-02-01T00:00:00Z', user: me(over),
})
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>
function mockFetch(handler: Handler) {
  const calls: Array<{ url: string; init: RequestInit }> = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => { calls.push({ url, init }); return handler(url, init) }))
  return calls
}
const auth = (c: { init: RequestInit }) => (c.init.headers as Record<string, string>).Authorization

beforeEach(() => clearSession())
afterEach(() => { vi.unstubAllGlobals(); clearSession(); takeEndedMessage() })

describe('sign in', () => {
  it('sends the role and a device id, keeps the tokens and the permissions', async () => {
    const calls = mockFetch(() => json(200, session(1)))
    await login(' asha ', 'secret-pass', 'OWNER')
    const body = JSON.parse(calls[0].init.body as string)
    expect(calls[0].url).toBe('/api/v1/auth/login')
    expect(body).toMatchObject({ username: 'asha', password: 'secret-pass', expectedRole: 'OWNER' })
    expect(body.deviceId).toMatch(/^[A-Za-z0-9-]{8,64}$/)
    expect(getAccessToken()).toBe('access-1')
    expect(getRefreshToken()).toBe('refresh-1')
    expect(can('ITEM_VIEW')).toBe(true)
    expect(can('STAFF_MANAGE')).toBe(false)
    expect(auth(calls[0])).toBeUndefined()
  })

  it('uses the same device id every time', async () => {
    const calls = mockFetch(() => json(200, session(1)))
    await login('a', 'b', 'STAFF')
    await login('a', 'b', 'STAFF')
    expect(JSON.parse(calls[0].init.body as string).deviceId).toBe(JSON.parse(calls[1].init.body as string).deviceId)
  })

  it('reports a wrong password with the server code and message', async () => {
    mockFetch(() => json(401, { code: 'INVALID_CREDENTIALS', detail: 'Wrong username or password' }))
    const e = await login('a', 'b', 'OWNER').catch((x) => x)
    expect(e).toBeInstanceOf(ApiError)
    expect(e).toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS', message: 'Wrong username or password' })
    expect(getUser()).toBeNull()
  })

  it('reports an unreachable server', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))
    await expect(login('a', 'b', 'OWNER')).rejects.toBeInstanceOf(NetworkError)
  })
})

describe('keeping the session alive', () => {
  it('refreshes once on 401 and repeats the call with the new token', async () => {
    mockFetch(() => json(200, session(1)))
    await login('a', 'b', 'OWNER')
    const calls = mockFetch((url, init) => {
      if (url === '/api/v1/auth/refresh') return json(200, session(2))
      return (init.headers as Record<string, string>).Authorization === 'Bearer access-2' ? json(200, { ok: true }) : json(401, { code: 'UNAUTHORIZED' })
    })
    await expect(api.get('/api/v1/items')).resolves.toEqual({ ok: true })
    expect(calls.map((c) => c.url)).toEqual(['/api/v1/items', '/api/v1/auth/refresh', '/api/v1/items'])
    expect(JSON.parse(calls[1].init.body as string)).toEqual({ refreshToken: 'refresh-1' })
    expect(getRefreshToken()).toBe('refresh-2')
  })

  it('shares one refresh between calls that fail at the same moment', async () => {
    mockFetch(() => json(200, session(1)))
    await login('a', 'b', 'OWNER')
    const calls = mockFetch(async (url, init) => {
      if (url === '/api/v1/auth/refresh') { await new Promise((r) => setTimeout(r, 20)); return json(200, session(2)) }
      return (init.headers as Record<string, string>).Authorization === 'Bearer access-2' ? json(200, {}) : json(401, {})
    })
    await Promise.all([api.get('/api/v1/items'), api.get('/api/v1/suppliers'), api.get('/api/v1/units')])
    expect(calls.filter((c) => c.url === '/api/v1/auth/refresh')).toHaveLength(1)
  })

  it('signs out with a message when the refresh is refused', async () => {
    mockFetch(() => json(200, session(1)))
    await login('a', 'b', 'OWNER')
    mockFetch((url) => (url === '/api/v1/auth/refresh' ? json(401, { code: 'INVALID_REFRESH_TOKEN', detail: 'x' }) : json(401, {})))
    await expect(api.get('/api/v1/items')).rejects.toMatchObject({ code: 'SESSION_ENDED' })
    expect(getUser()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(takeEndedMessage()).toBe('Your session has ended. Please sign in again.')
    expect(takeEndedMessage()).toBeNull()
  })

  it('keeps the session when the refresh fails only because there is no internet', async () => {
    mockFetch(() => json(200, session(1)))
    await login('a', 'b', 'OWNER')
    mockFetch((url) => { if (url === '/api/v1/auth/refresh') throw new TypeError('offline'); return json(401, {}) })
    await expect(api.get('/api/v1/items')).rejects.toBeInstanceOf(NetworkError)
    expect(getRefreshToken()).toBe('refresh-1')
    await expect(refreshSession().catch(() => 'offline')).resolves.toBe('offline')
  })

  it('restores a saved session by refreshing before the first call', async () => {
    localStorage.setItem('slm.refresh', 'refresh-9')
    const calls = mockFetch((url) => (url === '/api/v1/auth/refresh' ? json(200, session(10)) : json(200, { items: [] })))
    await api.get('/api/v1/items')
    expect(calls[0].url).toBe('/api/v1/auth/refresh')
    expect(auth(calls[1])).toBe('Bearer access-10')
  })
})

describe('sign out and password', () => {
  it('tells the server and always clears the phone', async () => {
    mockFetch(() => json(200, session(1)))
    await login('a', 'b', 'OWNER')
    const calls = mockFetch(() => json(204, null))
    await logout()
    expect(calls[0].url).toBe('/api/v1/auth/logout')
    expect(getUser()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('signs out locally even when the server cannot be reached', async () => {
    mockFetch(() => json(200, session(1)))
    await login('a', 'b', 'OWNER')
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
    await logout()
    expect(getUser()).toBeNull()
  })

  it('takes the new session after a password change', async () => {
    mockFetch(() => json(200, session(1, { mustChangePassword: true })))
    await login('a', 'b', 'STAFF')
    expect(getUser()?.mustChangePassword).toBe(true)
    const calls = mockFetch(() => json(200, session(2, { mustChangePassword: false })))
    await changePassword('old-pass', 'new-pass-1')
    expect(JSON.parse(calls[0].init.body as string)).toEqual({ currentPassword: 'old-pass', newPassword: 'new-pass-1' })
    expect(getUser()?.mustChangePassword).toBe(false)
  })
})
