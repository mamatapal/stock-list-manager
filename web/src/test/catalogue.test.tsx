import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { clearSession, type Me } from '../auth/session'
import { setLanguage } from '../i18n/lang'

const ME: Me = { userId: 'u1', username: 'asha', fullName: 'Asha Sharma', role: 'OWNER', businessId: 'b1', businessName: 'Sharma Store', mustChangePassword: false, permissions: [] }
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const KG = { id: 'u-kg', code: 'kg', name: 'Kilogram', builtIn: true, version: 0, deleted: false }
const PCS = { id: 'u-pc', code: 'pcs', name: 'Pieces', builtIn: true, version: 0, deleted: false }
const SUGAR = { id: 'i1', name: 'Sugar / शुगर', category: 'Grocery', defaultUnitId: 'u-kg', latestPurchasePrice: '40', latestSellingPrice: '45', lastPurchaseDate: null, preferredSupplierId: null, notes: null, active: true, version: 3, deleted: false }
const SUPPLIER = { id: 's1', name: 'Ram Traders', contactPerson: 'Ram', mobile: '9876543210', altPhone: null, address: null, city: 'Patna', gstin: null, notes: null, active: true, version: 2, deleted: false }

type Call = { method: string; url: string; body: Record<string, unknown> | null; headers: Record<string, string> }

/** A fake server. Returns the calls it received. */
function server(permissions: string[], extra: (c: Call) => Response | undefined = () => undefined) {
  const calls: Call[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
    const call: Call = { method: init.method ?? 'GET', url, body: init.body ? JSON.parse(init.body as string) : null, headers: (init.headers ?? {}) as Record<string, string> }
    calls.push(call)
    const custom = extra(call)
    if (custom) return custom
    const path = url.split('?')[0]
    if (path === '/api/v1/auth/refresh') return json(200, {
      accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z', refreshToken: 'r', refreshTokenExpiresAt: '2030-02-01T00:00:00Z',
      user: { ...ME, permissions },
    })
    if (path === '/api/v1/units') return json(200, [KG, PCS])
    if (path === '/api/v1/items/categories') return json(200, ['Grocery'])
    if (path === '/api/v1/items' && call.method === 'GET') return json(200, [SUGAR])
    if (path === '/api/v1/items/i1' && call.method === 'GET') return json(200, SUGAR)
    if (path === '/api/v1/suppliers' && call.method === 'GET') return json(200, [SUPPLIER])
    if (path === '/api/v1/stock-lists' && call.method === 'GET') return json(200, [])
    if (call.method === 'POST' || call.method === 'PUT') return json(200, { ...call.body, id: 'new', version: 1 })
    return json(404, { code: 'NOT_FOUND', detail: 'Not found' })
  }))
  return calls
}

function open(hash: string) {
  localStorage.setItem('slm.refresh', 'saved')
  window.location.hash = hash
  return render(<App />)
}

beforeEach(() => { setLanguage('en'); clearSession() })
afterEach(() => { vi.unstubAllGlobals(); clearSession() })

describe('home cards follow permissions', () => {
  it('shows only the cards the user may open, with a line of help', async () => {
    server(['ITEM_VIEW'])
    open('#/')
    const card = await screen.findByRole('link', { name: /Items/ })
    expect(card).toHaveTextContent('Catalogue, units and latest prices')
    expect(screen.queryByRole('link', { name: /Suppliers/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Out-of-stock lists/ })).not.toBeInTheDocument()
    expect(screen.getByText(/Hello, Asha Sharma/)).toBeInTheDocument()
  })

  it('shows lists to someone who can only record purchases', async () => {
    server(['PURCHASE_RECORD'])
    open('#/')
    expect(await screen.findByRole('link', { name: /Out-of-stock lists/ })).toBeInTheDocument()
  })

  it('has no bottom tabs, a way back to Home on inner screens, and a My account button', async () => {
    server(['ITEM_VIEW'])
    open('#/items')
    await screen.findByRole('heading', { name: /Items/ })
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Home/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /My account/ })).toBeInTheDocument()
  })
})

describe('items', () => {
  it('lists items in both languages with the price and unit', async () => {
    server(['ITEM_VIEW'])
    open('#/items')
    expect(await screen.findByText('Sugar')).toBeInTheDocument()
    expect(screen.getByText('शुगर')).toBeInTheDocument()
    expect(screen.getByText(/₹45 \/ kg/)).toBeInTheDocument()
  })

  it('fills the Hindi box while typing English and saves one name with both spellings', async () => {
    const calls = server(['ITEM_VIEW', 'ITEM_ADD', 'PURCHASE_PRICE_ENTER', 'SELLING_PRICE_ENTER'])
    const user = userEvent.setup()
    open('#/items/new')
    const english = await screen.findByLabelText(/English name/)
    await user.type(english, 'Basmati Rice')
    expect(screen.getByLabelText(/Hindi name/)).toHaveValue('बासमती राइस')
    await user.type(screen.getByLabelText(/Purchase price/), '90')
    await user.type(screen.getByLabelText(/Selling price/), '110')
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url === '/api/v1/items')).toBe(true))
    const post = calls.find((c) => c.method === 'POST' && c.url === '/api/v1/items')!
    expect(post.body).toMatchObject({ name: 'Basmati Rice / बासमती राइस', defaultUnitId: 'u-kg', latestPurchasePrice: '90', latestSellingPrice: '110' })
  })

  it('does not overwrite a Hindi spelling that was corrected by hand', async () => {
    server(['ITEM_VIEW', 'ITEM_ADD'])
    const user = userEvent.setup()
    open('#/items/new')
    const english = await screen.findByLabelText(/English name/)
    await user.type(english, 'Sugar')
    const hindi = screen.getByLabelText(/Hindi name/)
    await user.clear(hindi)
    await user.type(hindi, 'चीनी')
    await user.type(english, 's')
    expect(hindi).toHaveValue('चीनी')
  })

  it('fills the English box while typing Hindi', async () => {
    server(['ITEM_VIEW', 'ITEM_ADD'])
    const user = userEvent.setup()
    open('#/items/new')
    await user.type(await screen.findByLabelText(/Hindi name/), 'कमल')
    expect(screen.getByLabelText(/English name/)).toHaveValue('Kamal')
  })

  it('edits with the saved version in If-Match and leaves a price box read-only without permission', async () => {
    const calls = server(['ITEM_VIEW', 'ITEM_EDIT'])
    const user = userEvent.setup()
    open('#/items/i1')
    const purchase = await screen.findByLabelText(/Purchase price/)
    expect(purchase).toHaveAttribute('readonly')
    expect(screen.getByLabelText(/Selling price/)).toHaveAttribute('readonly')
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    const put = calls.find((c) => c.method === 'PUT')!
    expect(put.url).toBe('/api/v1/items/i1')
    expect(put.headers['If-Match']).toBe('3')
    expect(put.body).toMatchObject({ name: 'Sugar / शुगर', latestPurchasePrice: '40', latestSellingPrice: '45' })
  })

  it('shows no Delete or Save buttons to a viewer', async () => {
    server(['ITEM_VIEW'])
    open('#/items/i1')
    await screen.findByLabelText(/Purchase price/)
    expect(screen.queryByRole('button', { name: /^Save/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Delete/ })).not.toBeInTheDocument()
  })

  it('asks twice before deleting, then sends the version', async () => {
    const calls = server(['ITEM_VIEW', 'ITEM_DELETE'])
    const user = userEvent.setup()
    open('#/items/i1')
    await user.click(await screen.findByRole('button', { name: /^Delete/ }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    await user.click(screen.getByRole('button', { name: /Yes, delete/ }))
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true))
    expect(calls.find((c) => c.method === 'DELETE')!.headers['If-Match']).toBe('3')
  })
})

describe('suppliers', () => {
  it('shows the server message for a wrong phone number next to the box', async () => {
    server(['SUPPLIER_VIEW', 'SUPPLIER_ADD'], (c) => (c.method === 'POST' && c.url === '/api/v1/suppliers'
      ? json(400, { code: 'VALIDATION_FAILED', detail: '', errors: [{ field: 'mobile', message: 'Enter a valid phone number' }] }) : undefined))
    const user = userEvent.setup()
    open('#/suppliers/new')
    await user.type(await screen.findByLabelText(/English name/), 'Ram Traders')
    await user.type(screen.getByLabelText(/^Mobile/), '123')
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    expect(await screen.findByText(/Enter a valid phone number/)).toBeInTheDocument()
  })

  it('needs a name before it asks the server', async () => {
    const calls = server(['SUPPLIER_VIEW', 'SUPPLIER_ADD'])
    const user = userEvent.setup()
    open('#/suppliers/new')
    await user.click(await screen.findByRole('button', { name: /^Save/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Supplier name is required')
    expect(calls.some((c) => c.method === 'POST' && c.url !== '/api/v1/auth/refresh')).toBe(false)
  })
})

describe('out-of-stock lists', () => {
  it('saves a list with a catalogue item and a typed item, with ids and the unit of the item', async () => {
    const calls = server(['LIST_CREATE', 'ITEM_VIEW', 'SUPPLIER_VIEW'])
    const user = userEvent.setup()
    open('#/lists/new')
    await user.type(await screen.findByLabelText(/English name/), 'Monday list')
    await user.click(screen.getByRole('button', { name: /Add item to list/ }))
    await user.selectOptions(screen.getAllByLabelText(/^Item\b/)[0], 'i1')
    await user.type(screen.getAllByLabelText(/^Quantity/)[0], '25')
    await user.click(screen.getByRole('button', { name: /Add item to list/ }))
    await user.type(screen.getAllByLabelText(/English name/)[1], 'Matchbox')
    await user.type(screen.getAllByLabelText(/^Quantity/)[1], '10')
    await user.selectOptions(screen.getAllByLabelText(/^Unit/)[1], 'u-pc')
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url === '/api/v1/stock-lists')).toBe(true))
    const body = calls.find((c) => c.method === 'POST' && c.url === '/api/v1/stock-lists')!.body as { title: string; status: string; lines: Array<Record<string, unknown>> }
    expect(body.title).toBe('Monday list / मोन्डे लिस्ट')
    expect(body.status).toBe('DRAFT')
    expect(body.lines).toHaveLength(2)
    expect(body.lines[0]).toMatchObject({ itemId: 'i1', itemName: 'Sugar / शुगर', quantity: '25', unitId: 'u-kg', priceBasis: 'PER_UNIT' })
    expect(body.lines[0].id).toMatch(/^[0-9a-f-]{36}$/)
    expect(body.lines[1]).toMatchObject({ itemId: null, itemName: expect.stringMatching(/^Matchbox \/ [\u0900-\u097F ]+$/), quantity: '10', unitId: 'u-pc' })
  })

  it('does not call the server when a line has no quantity', async () => {
    const calls = server(['LIST_CREATE', 'ITEM_VIEW'])
    const user = userEvent.setup()
    open('#/lists/new')
    await user.type(await screen.findByLabelText(/English name/), 'A list')
    await user.click(screen.getByRole('button', { name: /Add item to list/ }))
    await user.selectOptions(screen.getAllByLabelText(/^Item\b/)[0], 'i1')
    await user.click(screen.getByRole('button', { name: /^Save/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Quantity is required')
    expect(calls.some((c) => c.method === 'POST' && c.url !== '/api/v1/auth/refresh')).toBe(false)
  })
})
