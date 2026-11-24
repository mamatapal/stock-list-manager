import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { clearSession, type Me } from '../auth/session'
import { setLanguage } from '../i18n/lang'

const ME: Me = { userId: 'u1', username: 'asha', fullName: 'Asha Sharma', role: 'OWNER', businessId: 'b1', businessName: 'Sharma Store', mustChangePassword: false, permissions: [] }
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const KG = { id: 'u-kg', code: 'kg', name: 'Kilogram', builtIn: true, version: 0, deleted: false }
const SUGAR = { id: 'i1', name: 'Sugar / शुगर', category: null, defaultUnitId: 'u-kg', latestPurchasePrice: '40', latestSellingPrice: '45', lastPurchaseDate: null, preferredSupplierId: null, notes: null, active: true, version: 3, deleted: false }
const SUPPLIER = { id: 's1', name: 'Ram Traders', contactPerson: null, mobile: null, altPhone: null, address: null, city: null, gstin: null, notes: null, active: true, version: 2, deleted: false }
const LIST = {
  id: 'l1', title: 'Monday list', listDate: '2026-11-20', supplierId: 's1', notes: null, status: 'SAVED', receivingStatus: 'PENDING', totalAmount: null, version: 1, deleted: false,
  lines: [
    { id: 'll1', itemId: 'i1', itemName: 'Sugar / शुगर', description: null, quantity: '25', unitId: 'u-kg', priceBasis: 'PER_UNIT', enteredPrice: '41', lineTotal: '1025.00', effectiveUnitPrice: '41', sortOrder: 0, receivedQuantity: '5', remainingQuantity: '20', overBy: null, receivingStatus: 'PARTIAL', unitMismatch: false },
    { id: 'll2', itemId: null, itemName: 'Matchbox', description: null, quantity: '10', unitId: 'u-kg', priceBasis: 'PER_UNIT', enteredPrice: null, lineTotal: null, effectiveUnitPrice: null, sortOrder: 1, receivedQuantity: '0', remainingQuantity: '10', overBy: null, receivingStatus: 'PENDING', unitMismatch: false },
  ],
}
const line = (over = {}) => ({
  id: 'pl1', itemId: 'i1', itemName: 'Sugar / शुगर', unitId: 'u-kg', unitCode: 'kg', quantity: '20', priceBasis: 'PER_UNIT', purchasePrice: '40', purchaseUnitPrice: '40',
  sellingPrice: '45', totalCost: '800.00', expectedSales: null, expectedProfit: null, marginPercent: null, markupPercent: null, sellingBelowCost: false, stockListItemId: 'll1',
  updateCatalog: false, sortOrder: 0, receivedQuantity: '5', remainingQuantity: '15', overBy: null, receivingStatus: 'PARTIAL', ...over,
})
const purchase = (over = {}) => ({
  id: 'p1', supplierId: 's1', supplierName: 'Ram Traders', invoiceNo: 'INV-7', purchaseDate: '2026-11-21', receivedDate: '2026-11-21', stockListId: 'l1', notes: null, status: 'PARTIAL',
  overReceived: false, duplicateInvoice: false, itemCount: 1, totalCost: '800.00', totalExpectedSales: null, totalExpectedProfit: null, marginPercent: null, cancelReason: null,
  lines: [line()], deliveries: [{ id: 'd1', receivedDate: '2026-11-21', notes: null, lines: [{ id: 'dl1', purchaseLineId: 'pl1', kind: 'RECEIPT', quantity: '5', correctsId: null, reason: null, batchNo: 'B1', expiryDate: '2027-01-01' }] }],
  version: 4, ...over,
})

type Call = { method: string; url: string; body: Record<string, unknown> | null; headers: Record<string, string> }

function server(permissions: string[], p = purchase()) {
  const calls: Call[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
    const call: Call = { method: init.method ?? 'GET', url, body: init.body ? JSON.parse(init.body as string) : null, headers: (init.headers ?? {}) as Record<string, string> }
    calls.push(call)
    const path = url.split('?')[0]
    if (path === '/api/v1/auth/refresh') return json(200, { accessToken: 'a', accessTokenExpiresAt: '2030-01-01T00:00:00Z', refreshToken: 'r', refreshTokenExpiresAt: '2030-02-01T00:00:00Z', user: { ...ME, permissions } })
    if (path === '/api/v1/units') return json(200, [KG])
    if (path === '/api/v1/items') return json(200, [SUGAR])
    if (path === '/api/v1/suppliers') return json(200, [SUPPLIER])
    if (path === '/api/v1/stock-lists') return json(200, [LIST])
    if (path === '/api/v1/purchases' && call.method === 'GET') return json(200, [p])
    if (path === '/api/v1/purchases/p1' && call.method === 'GET') return json(200, p)
    if (path === '/api/v1/batches/alert') return json(200, { today: '2026-11-22', expired: 2, within7: 1, within30: 4 })
    if (path === '/api/v1/batches') return json(200, [
      { id: 'b1', itemId: 'i1', itemName: 'Sugar / शुगर', purchaseId: 'p1', supplierName: 'Ram Traders', invoiceNo: null, batchNo: 'B1', quantity: '5', unitCode: 'kg', expiryDate: '2026-11-20', daysToExpiry: -2, expiryLabel: 'Expired 2 days ago', cancelled: false },
      { id: 'b2', itemId: 'i1', itemName: 'Sugar / शुगर', purchaseId: 'p1', supplierName: null, invoiceNo: null, batchNo: null, quantity: '3', unitCode: 'kg', expiryDate: '2026-11-23', daysToExpiry: 1, expiryLabel: 'Expires in 1 day', cancelled: false },
    ])
    if (call.method === 'POST' || call.method === 'PUT') return json(200, { ...p, id: 'p1', version: 5 })
    return json(404, { code: 'NOT_FOUND', detail: 'Not found' })
  }))
  return calls
}

function open(hash: string) {
  localStorage.setItem('slm.refresh', 'saved')
  window.location.hash = hash
  return render(<App />)
}
const writes = (calls: Call[], url: string) => calls.filter((c) => c.method !== 'GET' && c.url === url)

beforeEach(() => { setLanguage('en'); clearSession() })
afterEach(() => { vi.unstubAllGlobals(); clearSession() })

describe('home cards', () => {
  it('shows a card for every screen the user may open', async () => {
    server(['PURCHASE_HISTORY_VIEW', 'EXPIRY_VIEW', 'SUPPLIER_VIEW'])
    open('#/')
    expect(await screen.findByRole('link', { name: /Expiry/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Suppliers/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Purchases/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Out-of-stock lists/ })).not.toBeInTheDocument()
  })
})

describe('purchase list and details', () => {
  it('lists purchases with status, invoice and total', async () => {
    server(['PURCHASE_HISTORY_VIEW'])
    open('#/purchases')
    expect(await screen.findByText('Ram Traders')).toBeInTheDocument()
    expect(screen.getByText(/#INV-7 · ₹800.00/)).toBeInTheDocument()
    expect(within(screen.getByRole('link', { name: /Ram Traders/ })).getByText(/Partly received/)).toBeInTheDocument()
  })

  it('shows lines, received and remaining, and the delivery batch', async () => {
    server(['PURCHASE_HISTORY_VIEW'])
    open('#/purchases/p1')
    expect(await screen.findByText('Sugar', { selector: '.bi-main' })).toBeInTheDocument()
    expect(screen.getByText('15 kg')).toBeInTheDocument()
    expect(screen.getByText(/B1 · 2027-01-01/)).toBeInTheDocument()
    expect(screen.queryByText(/Expected profit/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Receive goods/ })).not.toBeInTheDocument()
  })

  it('shows expected profit only when the server sent it', async () => {
    server(['PURCHASE_HISTORY_VIEW', 'EXPECTED_PROFIT_VIEW'], purchase({ totalExpectedProfit: '100.00', marginPercent: '11.1' }))
    open('#/purchases/p1')
    expect(await screen.findByText(/₹100.00 \(11.1%\)/)).toBeInTheDocument()
  })
})

describe('recording a purchase', () => {
  it('starts from an out-of-stock list: supplier, remaining quantity, list links and price', async () => {
    const calls = server(['PURCHASE_RECORD', 'PURCHASE_PRICE_ENTER', 'SELLING_PRICE_ENTER'])
    const user = userEvent.setup()
    open('#/purchases/new')
    await user.click(await screen.findByRole('button', { name: /Pick from out-of-stock list/ }))
    await user.selectOptions(screen.getByLabelText(/Out-of-stock lists/), 'l1')
    expect(screen.getByRole('checkbox', { name: /Sugar/ })).toBeChecked()
    expect(screen.queryByText(/Matchbox/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^Add 1/ }))
    expect(screen.getByLabelText(/^Supplier/)).toHaveValue('s1')
    expect(screen.getByLabelText(/^Quantity/)).toHaveValue('20')
    await user.type(screen.getByLabelText(/Invoice number/), 'INV-9')
    await user.click(screen.getByRole('checkbox', { name: /goods arrived/ }))
    await user.click(screen.getByRole('button', { name: /Complete purchase/ }))
    await waitFor(() => expect(writes(calls, '/api/v1/purchases')).toHaveLength(1))
    const b = writes(calls, '/api/v1/purchases')[0].body as { lines: Array<Record<string, unknown>>; receive: { lines: Array<Record<string, unknown>> } } & Record<string, unknown>
    expect(b).toMatchObject({ supplierId: 's1', invoiceNo: 'INV-9', stockListId: 'l1', complete: true })
    expect(b.lines).toHaveLength(1)
    expect(b.lines[0]).toMatchObject({ itemId: 'i1', quantity: '20', unitId: 'u-kg', stockListItemId: 'll1', purchasePrice: '41', sellingPrice: '45', priceBasis: 'PER_UNIT' })
    expect(b.receive.lines[0]).toMatchObject({ purchaseLineId: b.lines[0].id, kind: 'RECEIPT', quantity: '20' })
  })

  it('lets you tick only some lines, and shows lines already added as added', async () => {
    server(['PURCHASE_RECORD'])
    const user = userEvent.setup()
    open('#/purchases/new')
    await user.click(await screen.findByRole('button', { name: /Pick from out-of-stock list/ }))
    await user.selectOptions(screen.getByLabelText(/Out-of-stock lists/), 'l1')
    await user.click(screen.getByRole('checkbox', { name: /Sugar/ }))
    expect(screen.getByRole('button', { name: /^Add 0/ })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /Select all/ }))
    await user.click(screen.getByRole('button', { name: /^Add 1/ }))
    await user.click(screen.getByRole('button', { name: /Pick from out-of-stock list/ }))
    await user.selectOptions(screen.getByLabelText(/Out-of-stock lists/), 'l1')
    expect(screen.getByRole('checkbox', { name: /Sugar/ })).toBeDisabled()
    expect(screen.getByText(/Already added/)).toBeInTheDocument()
    expect(screen.getAllByLabelText(/^Quantity/)).toHaveLength(1)
  })

  it('leaves price boxes read-only and empty without price permissions', async () => {
    const calls = server(['PURCHASE_RECORD'])
    const user = userEvent.setup()
    open('#/purchases/new')
    await user.selectOptions(await screen.findByLabelText(/^Supplier/), 's1')
    await user.click(screen.getByRole('button', { name: /^Add item/ }))
    await user.selectOptions(screen.getByLabelText(/^Item\b/), 'i1')
    expect(screen.getByLabelText(/^Purchase price/)).toHaveAttribute('readonly')
    expect(screen.getByLabelText(/^Purchase price/)).toHaveValue('')
    await user.type(screen.getByLabelText(/^Quantity/), '5')
    await user.click(screen.getByRole('button', { name: /Save as draft/ }))
    await waitFor(() => expect(writes(calls, '/api/v1/purchases')).toHaveLength(1))
    const sent = writes(calls, '/api/v1/purchases')[0].body as { complete: boolean; receive: unknown; lines: Array<Record<string, unknown>> }
    expect(sent.complete).toBe(false)
    expect(sent.receive).toBeNull()
    expect(sent.lines[0]).toMatchObject({ purchasePrice: null, sellingPrice: null })
  })

  it('needs a supplier, an item and a quantity before it asks the server', async () => {
    const calls = server(['PURCHASE_RECORD'])
    const user = userEvent.setup()
    open('#/purchases/new')
    await user.click(await screen.findByRole('button', { name: /Complete purchase/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a supplier')
    await user.selectOptions(screen.getByLabelText(/^Supplier/), 's1')
    await user.click(screen.getByRole('button', { name: /Complete purchase/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Add at least one item')
    expect(writes(calls, '/api/v1/purchases')).toHaveLength(0)
  })
})

describe('receiving goods', () => {
  it('offers what is still to come and sends batch and expiry', async () => {
    const calls = server(['PURCHASE_HISTORY_VIEW', 'PURCHASE_RECORD'])
    const user = userEvent.setup()
    open('#/purchases/p1')
    await user.click(await screen.findByRole('button', { name: /Receive goods/ }))
    const qty = screen.getByLabelText(/Quantity received/)
    expect(qty).toHaveValue('15')
    await user.clear(qty)
    await user.type(qty, '10')
    await user.type(screen.getByLabelText(/Batch number/), 'B2')
    await user.type(screen.getByLabelText(/Expiry date/), '2027-03-01')
    await user.click(within(screen.getByRole('heading', { name: /Receive goods/ }).closest('form')!).getByRole('button', { name: /^Save/ }))
    await waitFor(() => expect(writes(calls, '/api/v1/purchases/p1/deliveries')).toHaveLength(1))
    const b = writes(calls, '/api/v1/purchases/p1/deliveries')[0].body as { lines: Array<Record<string, unknown>> }
    expect(b.lines).toHaveLength(1)
    expect(b.lines[0]).toMatchObject({ purchaseLineId: 'pl1', kind: 'RECEIPT', quantity: '10', batchNo: 'B2', expiryDate: '2027-03-01' })
  })

  it('does not send an empty delivery', async () => {
    const calls = server(['PURCHASE_HISTORY_VIEW', 'PURCHASE_RECORD'])
    const user = userEvent.setup()
    open('#/purchases/p1')
    await user.click(await screen.findByRole('button', { name: /Receive goods/ }))
    await user.clear(screen.getByLabelText(/Quantity received/))
    await user.click(within(screen.getByRole('heading', { name: /Receive goods/ }).closest('form')!).getByRole('button', { name: /^Save/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter at least one received quantity')
    expect(writes(calls, '/api/v1/purchases/p1/deliveries')).toHaveLength(0)
  })
})

describe('cancelling', () => {
  it('needs a reason and sends the saved version', async () => {
    const calls = server(['PURCHASE_HISTORY_VIEW', 'PURCHASE_DELETE'])
    const user = userEvent.setup()
    open('#/purchases/p1')
    await user.click(await screen.findByRole('button', { name: /Cancel this purchase/ }))
    await user.click(screen.getByRole('button', { name: /Cancel this purchase/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Give a reason')
    expect(writes(calls, '/api/v1/purchases/p1/cancel')).toHaveLength(0)
    await user.type(screen.getByLabelText(/Reason for cancelling/), 'Wrong supplier')
    await user.click(screen.getByRole('button', { name: /Cancel this purchase/ }))
    await waitFor(() => expect(writes(calls, '/api/v1/purchases/p1/cancel')).toHaveLength(1))
    const c = writes(calls, '/api/v1/purchases/p1/cancel')[0]
    expect(c.body).toEqual({ reason: 'Wrong supplier' })
    expect(c.headers['If-Match']).toBe('4')
  })
})

describe('expiry', () => {
  it('shows counts and days left in both languages', async () => {
    server(['EXPIRY_VIEW'])
    open('#/expiry')
    expect(await screen.findByText(/Expired 2 days ago/)).toBeInTheDocument()
    expect(screen.getByText(/Expires in 1 day/)).toBeInTheDocument()
    expect(screen.getByText(/2 दिन पहले एक्सपायर हुआ/)).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
  })
})

describe('PDF entry points', () => {
  it('a purchase offers the two PDFs; the selling-price one is a separate link', async () => {
    server(['PURCHASE_HISTORY_VIEW'])
    open('#/purchases/p1')
    const full = await screen.findByRole('link', { name: /Cost price and selling price/ })
    expect(full).toHaveAttribute('href', '#/pdf/purchase/p1')
    expect(screen.getByRole('link', { name: /Selling price only/ })).toHaveAttribute('href', '#/pdf/purchase/p1?prices=selling')
  })
  it('the Reports card and screen list the reports; expected profit needs both profit permissions', async () => {
    server(['PURCHASE_HISTORY_VIEW', 'EXPECTED_PROFIT_VIEW'])
    open('#/reports')
    expect(await screen.findByRole('link', { name: /Purchase report/ })).toHaveAttribute('href', expect.stringMatching(/^#\/pdf\/report\/purchases\?from=\d{4}-\d{2}-01&to=\d{4}-\d{2}-\d{2}$/))
    expect(screen.getByRole('link', { name: /Supplier totals/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Item purchase prices/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Expected profit report/ })).not.toBeInTheDocument()
  })
  it('shows expected profit report with both permissions', async () => {
    server(['PURCHASE_HISTORY_VIEW', 'EXPECTED_PROFIT_VIEW', 'PROFIT_REPORT_VIEW'])
    open('#/reports')
    expect(await screen.findByRole('link', { name: /Expected profit report/ })).toBeInTheDocument()
  })
  it('Home has a Reports card for users who can see purchase history', async () => {
    server(['PURCHASE_HISTORY_VIEW'])
    open('#/')
    expect(await screen.findByRole('link', { name: /Reports/ })).toHaveAttribute('href', '#/reports')
  })
})
