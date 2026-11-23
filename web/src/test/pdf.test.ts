import { readFileSync } from 'node:fs'
import { describe, expect, it, beforeEach } from 'vitest'
import { hasHindi, setLanguage } from '../i18n/lang'
import { div, parse, setScale, toPlain } from '../pdf/dec'
import { dateTime, date, fileName, grouped, money, percent, qty, unitPrice } from '../pdf/format'
import { layout, PAGE_H, type Paint, type Page } from '../pdf/layout'
import { buildPdf } from '../pdf/file'
import { expectedProfitDoc, expiryDoc, itemDoc, itemPricesDoc, purchaseDoc, purchaseReportDoc, sellingPricesDoc, stockListDoc, supplierDoc, supplierTotalsDoc, type PdfContext } from '../pdf/documents'
import { plainText, type ReportDoc, type Txt } from '../pdf/model'
import type { Purchase, StockList, Supplier, Item, Batch } from '../api/types'

const measure = (text: string, p: Paint) => text.length * p.size * 0.5
const ctx = (showProfit = false): PdfContext => ({
  business: { name: 'Sharma Store', address: '12 Main Road', city: 'Pune', state: 'MH', pinCode: '411001', phone: '98200', email: null, gstin: '27ABCDE1234F1Z5' },
  generatedBy: 'Asha Sharma', generatedAt: new Date(2026, 10, 23, 9, 5), people: { u1: 'Asha Sharma' }, showProfit,
})
const KG = 'u-kg'
const supplier: Supplier = { id: 's1', name: 'Ram Traders', contactPerson: null, mobile: '99', altPhone: null, address: null, city: 'Pune', gstin: null, notes: null, active: true, version: 0, deleted: false }
const line = (o = {}) => ({ id: 'pl1', itemId: 'i1', itemName: 'Sugar / शुगर', unitId: KG, unitCode: 'kg', quantity: '20', priceBasis: 'PER_UNIT', purchasePrice: '40', purchaseUnitPrice: '40', sellingPrice: '45', totalCost: '800.00', expectedSales: '900.00', expectedProfit: '100.00', marginPercent: null, markupPercent: null, sellingBelowCost: false, stockListItemId: null, updateCatalog: false, sortOrder: 0, receivedQuantity: '5', remainingQuantity: '15', overBy: null, receivingStatus: 'PARTIAL', ...o })
const purchase = (o = {}): Purchase => ({ id: 'p1', supplierId: 's1', supplierName: 'Ram Traders', invoiceNo: 'INV-7', purchaseDate: '2026-11-21', receivedDate: null, stockListId: null, notes: null, status: 'PARTIAL', overReceived: false, duplicateInvoice: false, itemCount: 1, totalCost: '800.00', totalExpectedSales: '900.00', totalExpectedProfit: '100.00', marginPercent: null, cancelReason: null, lines: [line() as never], deliveries: [{ id: 'd1', receivedDate: '2026-11-22', notes: null, lines: [{ id: 'dl1', purchaseLineId: 'pl1', kind: 'RECEIPT', quantity: '5', correctsId: null, reason: null, batchNo: 'B1', expiryDate: '2027-01-31' } as never] }], version: 1, ...o } as Purchase)

const cellsOf = (d: ReportDoc) => d.blocks.flatMap((b) => (b.kind === 'table' ? [b.columns.map((c) => plainText(c.title)), ...b.rows.map((r) => r.cells.map(plainText)), ...(b.totals ?? []).map((r) => r.cells.map(plainText))] : []))
const allText = (d: ReportDoc) => JSON.stringify([d.title, d.subtitle, d.blocks])

beforeEach(() => setLanguage('en'))

describe('decimal and formatting (same results as the Android app)', () => {
  it('rounds half up and keeps exact decimals', () => {
    expect(toPlain(setScale(parse('2.345')!, 2))).toBe('2.35')
    expect(toPlain(setScale(parse('-2.345')!, 2))).toBe('-2.35')
    expect(toPlain(div(parse('800')!, parse('3')!, 4))).toBe('266.6667')
    expect(toPlain(parse('0.1')! && { n: parse('0.1')!.n + parse('0.2')!.n, s: 1 })).toBe('0.3')
  })
  it('groups rupees the Indian way', () => {
    expect(grouped('1234567.50')).toBe('12,34,567.50')
    expect(money('123456.5')).toBe('₹1,23,456.50')
    expect(money('-1000')).toBe('-₹1,000.00')
    expect(money(null)).toBe('-')
    expect(unitPrice('12.5')).toBe('₹12.50')
    expect(unitPrice('0.33333')).toBe('₹0.3333')
    expect(qty('20.500', 'kg')).toBe('20.5 kg')
    expect(qty('20.000')).toBe('20')
    expect(percent('12.5')).toBe('12.50%')
    expect(date('2026-11-21')).toBe('21-11-2026')
    expect(dateTime(new Date(2026, 10, 3, 9, 5))).toBe('03-11-2026 09:05')
  })
  it('makes safe file names', () => {
    expect(fileName('Purchase', 'INV/7 *', '2026-11-21')).toBe('Purchase_INV-7_2026-11-21.pdf')
    expect(fileName()).toBe('document.pdf')
  })
})

describe('document builders', () => {
  it('purchase shows cost and profit only to those allowed', () => {
    const plain = purchaseDoc(ctx(false), purchase(), supplier)
    expect(cellsOf(plain)[0]).not.toContain('Expected profit')
    expect(allText(plain)).not.toContain('Expected profit is an estimate')
    const rich = purchaseDoc(ctx(true), purchase(), supplier)
    expect(cellsOf(rich)[0]).toContain('Expected profit')
    const rows = cellsOf(rich)
    expect(rows[1][2]).toBe('20 kg')
    expect(rows[1][3]).toBe('5')
    expect(rows[1][7]).toBe('₹100.00')
    expect(rows[2][5]).toBe('₹800.00')
    expect(allText(rich)).toContain('Goods received')
  })
  it('selling price list has no cost, profit, supplier or invoice', () => {
    const d = sellingPricesDoc(ctx(true), purchase())
    const text = allText(d)
    for (const bad of ['Ram Traders', 'INV-7', '800.00', '100.00', 'Unit cost', 'Expected profit', 'Supplier']) expect(text).not.toContain(bad)
    expect(cellsOf(d)[1]).toContain('₹45.00 / kg')
  })
  it('cancelled purchases are marked', () => {
    expect(allText(purchaseDoc(ctx(), purchase({ status: 'CANCELLED', cancelReason: 'Wrong bill' }), supplier))).toContain('CANCELLED')
  })
  it('stock list, supplier and item history, reports and expiry build with the right totals', () => {
    const list = { id: 'l1', title: 'Monday list', listDate: '2026-11-20', supplierId: 's1', notes: null, status: 'SAVED', receivingStatus: 'PENDING', totalAmount: '500.00', version: 1, deleted: false,
      lines: [{ id: 'a', itemId: 'i1', itemName: 'Sugar / शुगर', description: null, quantity: '10', unitId: KG, priceBasis: 'PER_UNIT', enteredPrice: '50', lineTotal: '500.00', effectiveUnitPrice: '50', sortOrder: 0, receivedQuantity: null, remainingQuantity: null, overBy: null, receivingStatus: 'PENDING', unitMismatch: false }] } as StockList
    const sl = cellsOf(stockListDoc(ctx(), list, supplier, { [KG]: 'kg' }))
    expect(sl[1].slice(2)).toEqual(['10 kg', '₹50.00 / kg', '₹500.00'])
    const sh = supplierDoc(ctx(), supplier, { supplierId: 's1', supplierName: 'Ram Traders', purchaseCount: 2, totalCost: '1000', firstPurchaseDate: null, lastPurchaseDate: null,
      purchases: [purchase({ totalCost: '600.00' }), purchase({ id: 'p2', totalCost: '400.00', purchaseDate: '2026-11-22' }), purchase({ id: 'p3', status: 'CANCELLED', totalCost: '999.00' })] }, null, null)
    const sup = cellsOf(sh)
    expect(sup[sup.length - 1][4]).toBe('₹1,000.00')
    expect(allText(sh)).toContain('Average purchase')
    const item = { id: 'i1', name: 'Sugar / शुगर', category: null } as Item
    const ih = itemDoc(ctx(), item, { itemId: 'i1', purchaseCount: 2, totalQuantity: '30', totalCost: '1200', lowestUnitPrice: '40', highestUnitPrice: '42', rows: [
      { purchaseId: 'p1', purchaseLineId: 'a', purchaseDate: '2026-11-01', status: 'FULL', supplierId: 's1', supplierName: 'Ram', invoiceNo: null, itemName: 'Sugar', quantity: '10', unitCode: 'kg', priceBasis: 'PER_UNIT', purchasePrice: '40', purchaseUnitPrice: '40', sellingPrice: null, totalCost: '400', receivedQuantity: null },
      { purchaseId: 'p2', purchaseLineId: 'b', purchaseDate: '2026-11-05', status: 'FULL', supplierId: 's1', supplierName: 'Ram', invoiceNo: null, itemName: 'Sugar', quantity: '20', unitCode: 'kg', priceBasis: 'PER_UNIT', purchasePrice: '42', purchaseUnitPrice: '42', sellingPrice: null, totalCost: '840', receivedQuantity: null }] }, null, null)
    expect(allText(ih)).toContain('₹41.3333')
    const pr = purchaseReportDoc(ctx(), { period: { from: '2026-11-01', to: '2026-11-30' }, purchaseCount: 1, cancelledCount: 0, totalCost: '800', expectedSales: null, expectedProfit: null, profitIncluded: false, profitNote: null,
      rows: [{ purchaseId: 'p1', purchaseDate: '2026-11-21', invoiceNo: 'X', supplierId: 's1', supplierName: 'Ram', status: 'FULL', itemCount: 1, totalCost: '800', expectedSales: null, expectedProfit: null }] })
    expect(cellsOf(pr)[0]).not.toContain('Expected profit')
    expect(allText(pr)).toContain('01-11-2026 to 30-11-2026')
    expect(supplierTotalsDoc(ctx(), { period: { from: null, to: null }, purchaseCount: 0, totalCost: '0', profitIncluded: false, profitNote: null, rows: [] }).fileName).toBe('Supplier-totals.pdf')
    expect(itemPricesDoc(ctx(), { period: { from: null, to: null }, profitIncluded: true, profitNote: null, rows: [] }).title).toBeTruthy()
    expect(allText(expectedProfitDoc(ctx(), { period: { from: null, to: null }, purchaseCount: 0, totalCost: '0', pricedCost: '0', unpricedCost: '0', expectedSales: '0', expectedProfit: '0', marginPercent: null, note: null, bySupplier: [], byItem: [] }))).toContain('Estimate only')
    const batch = { id: 'b1', itemId: 'i1', itemName: 'Milk', purchaseId: 'p1', supplierName: null, invoiceNo: null, batchNo: 'B9', quantity: '4', unitCode: 'l', expiryDate: '2026-11-20', daysToExpiry: -3, expiryLabel: null, cancelled: false } as Batch
    expect(allText(expiryDoc(ctx(), [batch], 'EXPIRED', '2026-11-23'))).toContain('Expired 3 days ago')
  })
  it('every fixed wording in the builders has a Hindi translation', () => {
    const src = readFileSync('src/pdf/documents.ts', 'utf8')
    const keys = new Set<string>([...src.matchAll(/\b(?:t|col)\('((?:[^'\\]|\\.)*)'/g)].map((m) => m[1]))
    const filters = src.match(/EXPIRY_FILTERS[^\n]*\n?/)![0]
    for (const m of filters.matchAll(/'([A-Z][^']+)'/g)) keys.add(m[1])
    const missing = [...keys].filter((k) => !hasHindi(k) && !['#', 'GSTIN'].includes(k))
    expect(missing).toEqual([])
  })
  it('shows both languages when the main language changes', () => {
    setLanguage('hi')
    const d = sellingPricesDoc(ctx(), purchase())
    const head = d.blocks.find((b) => b.kind === 'table')! as { columns: Array<{ title: Txt }> }
    expect(head.columns[1].title).toEqual({ main: 'आइटम', other: 'Item' })
  })
})

describe('page layout', () => {
  const manyRows = (n: number): ReportDoc => ({
    business: { name: 'Shop', lines: [] }, title: 'Big', subtitle: [], fileName: 'x.pdf', footerLeft: 'Generated', pageLabel: (a, b) => `Page ${a} of ${b}`, continuedLabel: (b, t) => `${b} · ${t} (continued)`,
    blocks: [{ kind: 'table', columns: [{ title: 'A', weight: 1 }, { title: 'B', weight: 3, align: 'right' }], rows: Array.from({ length: n }, (_, i) => ({ cells: [`row ${i}`, 'x'.repeat(30)] })), totals: [{ cells: ['Total', '1'] }], emptyText: 'none' }],
  })
  const rowsOn = (pages: Page[]) => pages.flatMap((p) => p.rows)
  it('flows long tables over pages with a repeated heading, footer and every row once', () => {
    const pages = layout(manyRows(120), measure)
    expect(pages.length).toBeGreaterThan(2)
    expect(pages.every((p) => p.headings >= 1)).toBe(true)
    expect(rowsOn(pages).filter((r) => r.startsWith('row ')).length).toBe(120)
    pages.forEach((p, i) => {
      const labels = p.ops.filter((o) => o.t === 'text').map((o) => (o as { text: string }).text)
      expect(labels).toContain(`Page ${i + 1} of ${pages.length}`)
      expect(p.ops.every((o) => o.t !== 'text' || o.y < PAGE_H)).toBe(true)
    })
  })
  it('splits a row that is taller than a page and never loses a line', () => {
    const d = manyRows(1)
    ;(d.blocks[0] as { rows: Array<{ cells: string[] }> }).rows[0].cells[1] = Array.from({ length: 400 }, (_, i) => `line${i}`).join('\n')
    const pages = layout(d, measure)
    expect(pages.length).toBeGreaterThan(1)
    const text = pages.flatMap((p) => p.ops.filter((o) => o.t === 'text').map((o) => (o as { text: string }).text))
    for (let i = 0; i < 400; i++) expect(text).toContain(`line${i}`)
  })
  it('shows the empty text for a table without rows', () => {
    const pages = layout(manyRows(0), measure)
    expect(pages[0].ops.some((o) => o.t === 'text' && o.text === 'none')).toBe(true)
  })
})

describe('PDF file', () => {
  it('has the right structure and page count', () => {
    const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xd9])
    const bytes = buildPdf([jpeg, jpeg, jpeg], 1488, 2105)
    const text = new TextDecoder('latin1').decode(bytes)
    expect(text.startsWith('%PDF-1.4')).toBe(true)
    expect(text.trimEnd().endsWith('%%EOF')).toBe(true)
    expect(text).toContain('/Count 3')
    expect((text.match(/\/Type \/Page /g) ?? []).length).toBe(3)
    const start = Number(/startxref\n(\d+)/.exec(text)![1])
    expect(text.slice(start, start + 4)).toBe('xref')
    const offsets = [...text.matchAll(/(\d{10}) 00000 n /g)].map((m) => Number(m[1]))
    offsets.forEach((o, i) => expect(text.slice(o, o + 8)).toBe(`${i + 1} 0 obj\n`.slice(0, 8)))
  })
})
