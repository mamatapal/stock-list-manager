import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { clearSession, type Me } from '../auth/session'
import { clearStepUp } from '../api/stepUp'
import { setLanguage } from '../i18n/lang'

const ME: Me = { userId: 'u1', username: 'asha', fullName: 'Asha Sharma', role: 'OWNER', businessId: 'b1', businessName: 'Sharma Store', mustChangePassword: false, permissions: [] }
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const MEMBER = { id: 'st1', username: 'ravi', fullName: 'Ravi Kumar', mobile: '9876543210', status: 'ACTIVE', mustChangePassword: true, createdAt: '2026-11-01T10:00:00Z', lastLoginAt: null }
const BUSINESS = { id: 'b1', name: 'Sharma Store', address: '12 Main Road', city: 'Pune', state: 'MH', pinCode: '411001', phone: '9820012345', email: 'a@b.in', gstin: null, hasLogo: false, version: 2, updatedAt: '2026-11-20T10:00:00Z' }

type Call = { method: string; url: string; body: Record<string, unknown> | null; headers: Record<string, string> }

function server(permissions: string[], role: 'OWNER' | 'STAFF' = 'OWNER', opts: { stepUp?: boolean } = { stepUp: true }) {
  const calls: Call[] = []
  let stepUpOk = false
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
    const call: Call = { method: init.method ?? 'GET', url, body: init.body ? JSON.parse(init.body as string) : null, headers: (init.headers ?? {}) as Record<string, string> }
    calls.push(call)
    const path = url.split('?')[0]
    if (path === '/api/v1/auth/refresh') return json(200, { accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z', refreshToken: 'r', refreshTokenExpiresAt: '2030-02-01T00:00:00Z', user: { ...ME, role, permissions } })
    if (path === '/api/v1/auth/reauth') {
      if (call.body?.password !== 'secret') return json(403, { code: 'INVALID_CREDENTIALS', detail: 'Wrong password' })
      stepUpOk = true
      return json(200, { stepUpToken: 'step-1', expiresAt: '2030-01-01T00:00:00Z' })
    }
    if (call.method !== 'GET' && opts.stepUp && call.headers['X-Step-Up-Token'] !== 'step-1') return json(403, { code: 'STEP_UP_REQUIRED', detail: 'Please re-enter your password to continue' })
    if (path === '/api/v1/staff' && call.method === 'GET') return json(200, [MEMBER])
    if (path === '/api/v1/staff' && call.method === 'POST') return json(201, { staff: { ...MEMBER, fullName: String(call.body?.fullName), username: String(call.body?.username) }, permissions: call.body?.permissions, temporaryPassword: 'Temp-1234' })
    if (path === '/api/v1/staff/st1' && call.method === 'GET') return json(200, MEMBER)
    if (path === '/api/v1/staff/st1/permissions' && call.method === 'GET') return json(200, { userId: 'st1', permissions: ['ITEM_VIEW'] })
    if (path === '/api/v1/staff/st1/permissions' && call.method === 'PUT') return json(200, { userId: 'st1', permissions: call.body?.permissions })
    if (path === '/api/v1/staff/st1/sessions') return json(200, [])
    if (path === '/api/v1/staff/st1/deactivate') return json(200, { ...MEMBER, status: 'INACTIVE' })
    if (path === '/api/v1/business' && call.method === 'GET') return json(200, BUSINESS)
    if (path === '/api/v1/business' && call.method === 'PUT') return json(200, { ...BUSINESS, ...call.body, version: 3 })
    if (path === '/api/v1/activity') return json(200, { items: [{ id: 2, userId: 'u1', username: 'asha', action: 'STAFF_CREATED', recordType: null, recordId: null, details: null, deviceId: null, createdAt: '2026-11-22T10:00:00Z' }, { id: 1, userId: null, username: null, action: 'SOMETHING_ODD', recordType: null, recordId: null, details: null, deviceId: null, createdAt: '2026-11-21T10:00:00Z' }], page: 0, size: 50, total: 2 })
    return json(404, { code: 'NOT_FOUND', detail: 'Not found' })
  }))
  return { calls, get stepUpOk() { return stepUpOk } }
}

function open(hash: string) {
  localStorage.setItem('slm.refresh', 'saved')
  window.location.hash = hash
  return render(<App />)
}

beforeEach(() => { setLanguage('en'); clearSession(); clearStepUp() })
afterEach(() => { vi.unstubAllGlobals(); clearSession(); clearStepUp() })

describe('owner cards', () => {
  it('Staff needs STAFF_MANAGE, Activity is for the owner only, Business profile is for everyone', async () => {
    server(['ITEM_VIEW'], 'STAFF')
    open('#/')
    expect(await screen.findByRole('link', { name: /Business profile/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^.*Staff/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Activity log/ })).not.toBeInTheDocument()
  })
  it('a staff manager sees Staff but not Activity', async () => {
    server(['STAFF_MANAGE'], 'STAFF')
    open('#/')
    expect(await screen.findByRole('link', { name: /Staff/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Activity log/ })).not.toBeInTheDocument()
  })
  it('the owner sees all three', async () => {
    server(['STAFF_MANAGE', 'SETTINGS_MANAGE'])
    open('#/')
    expect(await screen.findByRole('link', { name: /Activity log/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Staff/ })).toBeInTheDocument()
  })
})

describe('staff', () => {
  it('creating an account asks for the password, retries with the step-up token and offers the sign-in details', async () => {
    const s = server(['STAFF_MANAGE', 'ITEM_VIEW', 'SUPPLIER_VIEW'])
    const user = userEvent.setup()
    open('#/staff/new')
    await user.type(await screen.findByLabelText(/Full name/), 'Sita Devi')
    await user.type(screen.getByLabelText(/^Username/), 'sita.d')
    await user.click(screen.getByRole('button', { name: /^Create/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Password/), 'secret')
    await user.click(within(dialog).getByRole('button', { name: /Continue/ }))
    expect(await screen.findByText(/Temporary password: Temp-1234/)).toBeInTheDocument()
    const posts = s.calls.filter((c) => c.method === 'POST' && c.url === '/api/v1/staff')
    expect(posts).toHaveLength(2)
    expect(posts[0].headers['X-Step-Up-Token']).toBeUndefined()
    expect(posts[1].headers['X-Step-Up-Token']).toBe('step-1')
    expect(posts[1].body).toMatchObject({ fullName: 'Sita Devi', username: 'sita.d', mobile: null })
    expect(screen.getByRole('button', { name: /Share sign-in details/ })).toBeInTheDocument()
  })
  it('a wrong password keeps the box open; cancelling creates nothing', async () => {
    const s = server(['STAFF_MANAGE'])
    const user = userEvent.setup()
    open('#/staff/new')
    await user.type(await screen.findByLabelText(/Full name/), 'Sita Devi')
    await user.type(screen.getByLabelText(/^Username/), 'sita.d')
    await user.click(screen.getByRole('button', { name: /^Create/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Password/), 'nope')
    await user.click(within(dialog).getByRole('button', { name: /Continue/ }))
    expect(await within(dialog).findByRole('alert')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /Cancel/ }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.queryByText(/Temp-1234/)).not.toBeInTheDocument()
    expect(s.calls.filter((c) => c.method === 'POST' && c.url === '/api/v1/staff').length).toBe(1)
  })
  it('validates before asking the server', async () => {
    const s = server(['STAFF_MANAGE'])
    const user = userEvent.setup()
    open('#/staff/new')
    await user.type(await screen.findByLabelText(/Full name/), 'Sita')
    await user.type(screen.getByLabelText(/^Username/), 'a')
    await user.click(screen.getByRole('button', { name: /^Create/ }))
    expect(await screen.findByText(/3-40 letters/)).toBeInTheDocument()
    expect(s.calls.filter((c) => c.method === 'POST' && c.url !== '/api/v1/auth/refresh').length).toBe(0)
  })
  it('a staff manager can only grant permissions they hold themselves', async () => {
    server(['STAFF_MANAGE', 'ITEM_VIEW'], 'STAFF')
    open('#/staff/new')
    const item = await screen.findByLabelText(/View items/)
    expect(item).toBeEnabled()
    expect(screen.getByLabelText(/Delete suppliers/)).toBeDisabled()
    expect(screen.getByLabelText(/View expected profit/)).toBeDisabled()
  })
  it('saving permissions sends the chosen set and the codes this screen knows', async () => {
    const s = server(['STAFF_MANAGE', 'ITEM_VIEW', 'ITEM_ADD'])
    const user = userEvent.setup()
    open('#/staff/st1')
    await user.click(await screen.findByLabelText(/Add items/))
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Password/), 'secret')
    await user.click(within(dialog).getByRole('button', { name: /Continue/ }))
    await waitFor(() => expect(s.calls.filter((c) => c.method === 'PUT' && c.url.endsWith('/permissions')).length).toBe(2))
    const put = s.calls.filter((c) => c.method === 'PUT' && c.url.endsWith('/permissions')).pop()!
    expect((put.body!.permissions as string[]).sort()).toEqual(['ITEM_ADD', 'ITEM_VIEW'])
    expect(put.body!.knownPermissions).toHaveLength(25)
    expect(put.headers['X-Step-Up-Token']).toBe('step-1')
  })
  it('deactivating asks to confirm first', async () => {
    const s = server(['STAFF_MANAGE', 'ITEM_VIEW'], 'OWNER', { stepUp: false })
    const user = userEvent.setup()
    open('#/staff/st1')
    await user.click(await screen.findByRole('button', { name: /Deactivate account/ }))
    expect(s.calls.some((c) => c.url.endsWith('/deactivate'))).toBe(false)
    await user.click(screen.getByRole('button', { name: /^Yes/ }))
    await waitFor(() => expect(s.calls.some((c) => c.url.endsWith('/deactivate'))).toBe(true))
    expect(await screen.findByRole('button', { name: /Activate account/ })).toBeInTheDocument()
  })
})

describe('business profile and activity', () => {
  it('editing validates, then saves with the step-up token', async () => {
    const s = server(['SETTINGS_MANAGE'])
    const user = userEvent.setup()
    open('#/business')
    await user.click(await screen.findByRole('button', { name: /^Edit/ }))
    await user.clear(screen.getByLabelText(/PIN code/))
    await user.type(screen.getByLabelText(/PIN code/), '123')
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    expect(await screen.findByText(/6-digit PIN/)).toBeInTheDocument()
    expect(s.calls.filter((c) => c.method === 'PUT').length).toBe(0)
    await user.clear(screen.getByLabelText(/PIN code/))
    await user.type(screen.getByLabelText(/PIN code/), '411002')
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Password/), 'secret')
    await user.click(within(dialog).getByRole('button', { name: /Continue/ }))
    expect(await screen.findByText(/Business details saved/)).toBeInTheDocument()
    const put = s.calls.filter((c) => c.method === 'PUT' && c.url === '/api/v1/business').pop()!
    expect(put.body).toMatchObject({ pinCode: '411002', gstin: null })
  })
  it('a user without SETTINGS_MANAGE can view but not edit', async () => {
    server(['ITEM_VIEW'], 'STAFF')
    open('#/business')
    expect(await screen.findByText('12 Main Road')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Edit/ })).not.toBeInTheDocument()
  })
  it('the activity log reads in plain words and keeps unknown actions readable', async () => {
    server([])
    open('#/activity')
    expect(await screen.findByText(/Created a staff account/)).toBeInTheDocument()
    expect(screen.getByText('Something odd')).toBeInTheDocument()
    expect(screen.getByText(/Unknown user/)).toBeInTheDocument()
  })
  it('shows Hindi when the main language is Hindi', async () => {
    server(['STAFF_MANAGE'])
    setLanguage('hi')
    open('#/staff')
    expect(await screen.findByText('Ravi Kumar')).toBeInTheDocument()
    expect(screen.getAllByText('स्टाफ').length).toBeGreaterThan(0)
  })
})
