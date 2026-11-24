import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { visibleNav } from '../components/nav'
import { clearSession, type Me, type SessionResponse } from '../auth/session'
import { setLanguage } from '../i18n/lang'

const me = (over: Partial<Me> = {}): Me => ({
  userId: 'u1', username: 'asha', fullName: 'Asha Sharma', role: 'OWNER', businessId: 'b1', businessName: 'Sharma Store',
  mustChangePassword: false, permissions: ['ITEM_VIEW'], ...over,
})
const session = (over: Partial<Me> = {}): SessionResponse => ({
  accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z', refreshToken: 'r', refreshTokenExpiresAt: '2030-02-01T00:00:00Z', user: me(over),
})
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

beforeEach(() => { setLanguage('en'); clearSession(); window.location.hash = '' })
afterEach(() => { vi.unstubAllGlobals(); clearSession() })

describe('the app', () => {
  it('asks for sign-in when nobody is signed in, in both languages', () => {
    render(<App />)
    expect(screen.getByRole('button', { name: /^Sign in/ })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /साइन इन/ }).length).toBeGreaterThan(0)
    expect(screen.getByLabelText(/Username/)).toBeInTheDocument()
  })

  it('signs in an owner and shows the home page', async () => {
    const fetchMock = vi.fn(async () => json(200, session()))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText(/Username/), 'asha')
    await user.type(screen.getAllByLabelText(/Password/)[0], 'secret-pass')
    await user.click(screen.getByRole('button', { name: /^Sign in/ }))
    expect(await screen.findByText(/Hello, Asha Sharma/)).toBeInTheDocument()
    expect(screen.getByText('Sharma Store', { selector: '.appbar-title' })).toBeInTheDocument()
    expect(JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string).expectedRole).toBe('OWNER')
  })

  it('signs in staff when Staff sign-in is chosen', async () => {
    const fetchMock = vi.fn(async () => json(200, session({ role: 'STAFF' })))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByRole('button', { name: /Staff sign-in/ }))
    await user.type(screen.getByLabelText(/Username/), 'ravi')
    await user.type(screen.getAllByLabelText(/Password/)[0], 'secret-pass')
    await user.click(screen.getByRole('button', { name: /^Sign in/ }))
    await screen.findByText(/Hello, Asha Sharma/)
    expect(JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string).expectedRole).toBe('STAFF')
  })

  it('shows a wrong password in both languages and stays on the sign-in page', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(401, { code: 'INVALID_CREDENTIALS', detail: 'Wrong username or password' })))
    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText(/Username/), 'asha')
    await user.type(screen.getAllByLabelText(/Password/)[0], 'nope-nope')
    await user.click(screen.getByRole('button', { name: /^Sign in/ }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Wrong username or password')
    expect(screen.getByRole('button', { name: /^Sign in/ })).toBeInTheDocument()
  })

  it('asks for both boxes before calling the server', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByRole('button', { name: /^Sign in/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter your username and password')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('stays signed in after a reload by using the saved refresh token', async () => {
    localStorage.setItem('slm.refresh', 'saved')
    vi.stubGlobal('fetch', vi.fn(async () => json(200, session())))
    render(<App />)
    expect(await screen.findByText(/Hello, Asha Sharma/)).toBeInTheDocument()
  })

  it('makes a user with a temporary password set a new one first', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(200, session({ role: 'STAFF', mustChangePassword: true }))))
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByRole('button', { name: /Staff sign-in/ }))
    await user.type(screen.getByLabelText(/Username/), 'ravi')
    await user.type(screen.getAllByLabelText(/Password/)[0], 'temp-pass-1')
    await user.click(screen.getByRole('button', { name: /^Sign in/ }))
    expect(await screen.findByText('You must set a new password before you continue.')).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('changes the language order from the account page and signs out', async () => {
    const fetchMock = vi.fn(async (url: string) => (url.endsWith('/logout') ? new Response(null, { status: 204 }) : json(200, session())))
    vi.stubGlobal('fetch', fetchMock)
    localStorage.setItem('slm.refresh', 'saved')
    const user = userEvent.setup()
    render(<App />)
    await screen.findByText(/Hello, Asha Sharma/)
    await user.click(screen.getByRole('link', { name: /My account/ }))
    expect(await screen.findByRole('heading', { name: /Account/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'हिन्दी' }))
    await waitFor(() => expect(document.documentElement.lang).toBe('hi'))
    const heading = screen.getByRole('heading', { name: /Account/ })
    const lines = heading.querySelectorAll('.bi-main, .bi-other')
    expect(lines[0]).toHaveTextContent('अकाउंट')
    expect(lines[1]).toHaveTextContent('Account')
    await user.click(screen.getByRole('button', { name: /Sign out/ }))
    expect(await screen.findByRole('button', { name: /^साइन इन/ })).toBeInTheDocument()
    expect(localStorage.getItem('slm.refresh')).toBeNull()
  })
})

describe('menu by permission', () => {
  const items = [{ to: '/', label: 'Home', subtitle: '', icon: '' }, { to: '/x', label: 'Secret', subtitle: '', icon: '', permission: 'STAFF_MANAGE' }]
  it('hides what the user may not open', () => {
    expect(visibleNav(items, () => false).map((i) => i.to)).toEqual(['/'])
    expect(visibleNav(items, (p) => p === 'STAFF_MANAGE').map((i) => i.to)).toEqual(['/', '/x'])
  })
})
