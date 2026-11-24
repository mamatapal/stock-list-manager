import type { Batch, Item, Purchase, StockList, Supplier } from '../api/types'
import { show } from '../i18n/bilingualName'
import { trBoth } from '../i18n/lang'
import { date, fileName, money, percent, qty, share, sumOf, unitPrice, dateTime, NONE, parse, sign, div, toPlain, cmp } from './format'
import type { Bi, Block, Column, ReportDoc, Row, Txt } from './model'
import type { BusinessDto, ExpectedProfitDto, ItemHistoryDto, ItemPricesDto, ProfitRowDto, PurchaseReportDto, SupplierHistoryDto, SupplierTotalsDto } from './serverTypes'

export const PROFIT_NOTE = 'Expected profit is an estimate: selling price minus purchase cost, for lines that have a selling price. It is not realised profit; actual sales are not recorded in this app.'

/** Who is printing, for whom, and what they may see. */
export interface PdfContext {
  business: BusinessDto
  generatedBy: string | null
  generatedAt: Date
  people: Record<string, string>
  showProfit: boolean
}

/** A fixed wording in both languages (main first). */
export const t = (english: string, ...args: Array<string | number | null | undefined>): Bi => {
  const [main, other] = trBoth(english, ...args)
  return { main, other }
}
/** A name typed by the user: "English / हिन्दी" is shown on two lines, a single-language name on one. */
export const nm = (name: string | null | undefined): Txt => {
  if (!name) return NONE
  const [main, other] = show(name)
  return other ? { main, other } : main
}
const plainEn = (english: string, ...args: Array<string | number>) => english.replace(/%(\d+)\$s/g, (_, n: string) => String(args[Number(n) - 1] ?? ''))
const both = (...lines: Txt[]): Txt => lines.flatMap((l) => (Array.isArray(l) ? l : [l]))
const col = (title: string, weight: number, align?: Column['align']): Column => ({ title: t(title), weight, align })

export function businessHeader(b: BusinessDto): ReportDoc['business'] {
  const place = [b.city, [b.state, b.pinCode].filter(Boolean).join(' ')].filter((x) => x && x.trim()).join(', ')
  const lines: Txt[] = []
  if (b.address) lines.push(b.address)
  if (place) lines.push(place)
  const phone = b.phone ? t('Phone %1$s', b.phone) : null
  if (phone && b.email) lines.push({ main: `${phone.main} · ${b.email}`, other: phone.other ? `${phone.other} · ${b.email}` : null })
  else if (phone) lines.push(phone)
  else if (b.email) lines.push(b.email)
  if (b.gstin) lines.push(t('GSTIN %1$s', b.gstin))
  return { name: nm(b.name), lines }
}

export const period = (from: string | null | undefined, to: string | null | undefined): Bi =>
  !from && !to ? t('All dates') : !from ? t('Up to %1$s', date(to)) : !to ? t('From %1$s', date(from)) : t('%1$s to %2$s', date(from), date(to))

const purchaseStatus = (s: string): Txt => ({ DRAFT: 'Draft', PENDING: 'Awaiting goods', PARTIAL: 'Partly received', FULL: 'Received', RECEIVED: 'Received', CANCELLED: 'Cancelled' } as Record<string, string>)[s]
  ? t(({ DRAFT: 'Draft', PENDING: 'Awaiting goods', PARTIAL: 'Partly received', FULL: 'Received', RECEIVED: 'Received', CANCELLED: 'Cancelled' } as Record<string, string>)[s]) : s
const label = (s: string) => { const x = s.toLowerCase().replace(/_/g, ' '); return x.charAt(0).toUpperCase() + x.slice(1) }
const hasText = (s: string | null | undefined): s is string => !!s && s.trim() !== ''
const note = (text: Txt, warning = false): Block => ({ kind: 'note', text, warning })
const present = (...b: Array<Block | null | false>): Block[] => b.filter((x): x is Block => !!x)

function doc(ctx: PdfContext, title: string, subtitle: Txt[], file: string, blocks: Block[]): ReportDoc {
  return {
    business: businessHeader(ctx.business), title: t(title), subtitle, blocks, fileName: file,
    footerLeft: ctx.generatedBy ? plainFooter('Generated %1$s by %2$s', dateTime(ctx.generatedAt), ctx.generatedBy) : plainFooter('Generated %1$s', dateTime(ctx.generatedAt)),
    pageLabel: (n, total) => t('Page %1$s of %2$s', n, total).main,
    continuedLabel: (business, tt) => t('%1$s  ·  %2$s (continued)', business, tt).main,
  }
}
const plainFooter = (key: string, ...args: string[]) => t(key, ...args).main
const counted = (p: { status: string }) => p.status !== 'CANCELLED' && p.status !== 'DRAFT'

// ----------------------------------------------------------------------------------------- 1. out-of-stock list
export function stockListDoc(ctx: PdfContext, list: StockList, supplier: Supplier | null, units: Record<string, string>, createdBy?: string | null): ReportDoc {
  const lines = [...list.lines].sort((a, b) => a.sortOrder - b.sortOrder)
  const rows: Row[] = lines.map((l, i) => {
    const unit = units[l.unitId]
    const price = !hasText(l.enteredPrice) ? NONE : l.priceBasis === 'TOTAL' ? t('%1$s total', unitPrice(l.enteredPrice)).main : unitPrice(l.enteredPrice) + (unit ? ` / ${unit}` : '')
    const item = nm(l.itemName)
    return { cells: [String(i + 1), hasText(l.description) ? both(item, l.description) : item, qty(l.quantity, unit), price, money(l.lineTotal)] }
  })
  const total = list.totalAmount ?? toPlain(sumOf(lines.map((l) => l.lineTotal)))
  const sup = supplier ? [show(supplier.name)[0], supplier.mobile, supplier.city ? show(supplier.city)[0] : null].filter(Boolean).join(', ') : null
  return doc(ctx, 'Out-of-stock list', [nm(list.title)], fileName(plainEn('List'), list.title, list.listDate), present(
    { kind: 'facts', items: [[t('List date'), date(list.listDate)], [t('Supplier'), sup], [t('Status'), label(list.status)], [t('Items'), String(lines.length)], [t('Prepared by'), createdBy ?? null]] },
    hasText(list.notes) && note(t('Notes: %1$s', list.notes)),
    { kind: 'table', columns: [col('#', 0.5, 'right'), col('Item', 4.6), col('Quantity', 1.5, 'right'), col('Price', 1.7, 'right'), col('Amount', 1.6, 'right')], rows,
      totals: [{ cells: ['', lines.length === 1 ? t('Total (1 item)') : t('Total (%1$s items)', lines.length), '', '', money(total)] }], emptyText: t('This list has no items') }))
}

// ----------------------------------------------------------------------------------------- 2. purchase
export function purchaseDoc(ctx: PdfContext, p: Purchase, supplier: Supplier | null): ReportDoc {
  const profit = ctx.showProfit
  const lines = [...p.lines].sort((a, b) => a.sortOrder - b.sortOrder)
  const received = (lineId: string) => toPlain(sumOf(p.deliveries.flatMap((d) => d.lines).filter((l) => l.purchaseLineId === lineId).map((l) => l.quantity)))
  const columns: Column[] = [col('#', 0.45, 'right'), col('Item', 3.4), col('Quantity', 1.35, 'right'), col('Received', 1.2, 'right'), col('Unit cost', 1.3, 'right'), col('Amount', 1.45, 'right'), col('Selling price', 1.3, 'right'), ...(profit ? [col('Expected profit', 1.4, 'right')] : [])]
  const rows: Row[] = lines.map((l, i) => ({ cells: [String(i + 1), nm(l.itemName), qty(l.quantity, l.unitCode), qty(received(l.id)), unitPrice(l.purchaseUnitPrice), money(l.totalCost), unitPrice(l.sellingPrice), ...(profit ? [money(l.expectedProfit)] : [])] }))
  const totals: Row[] = [{ cells: ['', t('Total'), '', '', '', money(p.totalCost), '', ...(profit ? [money(p.totalExpectedProfit)] : [])] }]
  const lineName = new Map(lines.map((l) => [l.id, { name: nm(l.itemName), unit: l.unitCode }]))
  const deliveries = [...p.deliveries].sort((a, b) => a.receivedDate.localeCompare(b.receivedDate))
  const deliveryRows: Row[] = deliveries.flatMap((d) => d.lines.map((dl) => {
    const ln = lineName.get(dl.purchaseLineId)
    const correction = dl.kind === 'CORRECTION'
    const item = ln?.name ?? t('Item')
    return { cells: [date(d.receivedDate), ctx.people[(d as { receivedBy?: string }).receivedBy ?? ''] ?? NONE,
      correction ? both(item, t('Correction').main + (hasText((dl as { reason?: string }).reason) ? `: ${(dl as { reason?: string }).reason}` : '')) : item,
      qty(dl.quantity, ln?.unit), (dl as { batchNo?: string | null }).batchNo ?? NONE, date((dl as { expiryDate?: string | null }).expiryDate)] }
  }))
  const cancelled = p.status === 'CANCELLED'
  const sup = supplier ? [supplier.mobile, supplier.city ? show(supplier.city)[0] : null].filter(Boolean).join(', ') : ''
  return doc(ctx, 'Purchase', [[show(p.supplierName)[0], p.invoiceNo ? t('Invoice %1$s', p.invoiceNo).main : null].filter(Boolean).join('  ·  ')],
    fileName(plainEn('Purchase'), p.invoiceNo ?? p.supplierName, p.purchaseDate), present(
      cancelled && note(both(t('CANCELLED. This purchase is not counted in any total.'), ...(hasText(p.cancelReason) ? [t(' Reason: %1$s', p.cancelReason)] : [])), true),
      { kind: 'facts', items: [[t('Supplier'), nm(p.supplierName)], [t('Supplier contact'), sup || null], [t('Invoice no.'), p.invoiceNo], [t('Supplier GSTIN'), supplier?.gstin ?? null],
        [t('Purchase date'), date(p.purchaseDate)], [t('Status'), purchaseStatus(p.status)], [t('Goods received'), p.receivedDate ? date(p.receivedDate) : null], [t('Items'), String(lines.length)]] },
      { kind: 'table', columns, rows, totals, emptyText: t('This purchase has no items') },
      profit && p.totalExpectedProfit !== null && note(t(PROFIT_NOTE), true),
      hasText(p.notes) && note(t('Notes: %1$s', p.notes)),
      deliveryRows.length > 0 && { kind: 'heading', text: t('Goods received') },
      deliveryRows.length > 0 && { kind: 'table', columns: [col('Date', 1.2), col('Received by', 1.6), col('Item', 3.2), col('Quantity', 1.3, 'right'), col('Batch', 1.3), col('Expiry', 1.2)], rows: deliveryRows, emptyText: '' }))
}

/** Items, quantities and selling prices only: no cost, profit, supplier or invoice, so it can be shown to anyone. */
export function sellingPricesDoc(ctx: PdfContext, p: Purchase): ReportDoc {
  const lines = [...p.lines].sort((a, b) => a.sortOrder - b.sortOrder)
  const rows: Row[] = lines.map((l, i) => {
    const price = unitPrice(l.sellingPrice)
    return { cells: [String(i + 1), nm(l.itemName), qty(l.quantity, l.unitCode), price === NONE || !l.unitCode.trim() ? price : `${price} / ${l.unitCode}`] }
  })
  const missing = lines.some((l) => unitPrice(l.sellingPrice) === NONE)
  return doc(ctx, 'Selling price list', [date(p.purchaseDate)], fileName(plainEn('Selling prices'), p.purchaseDate), present(
    p.status === 'CANCELLED' && note(t('CANCELLED. This purchase is not counted in any total.'), true),
    { kind: 'facts', items: [[t('Purchase date'), date(p.purchaseDate)], [t('Items'), String(lines.length)]] },
    { kind: 'table', columns: [col('#', 0.45, 'right'), col('Item', 4), col('Quantity', 1.5, 'right'), col('Selling price', 1.8, 'right')], rows, emptyText: t('This purchase has no items') },
    missing && note(t('Items marked - have no selling price yet.'))))
}

// ----------------------------------------------------------------------------------------- 3. supplier history
export function supplierDoc(ctx: PdfContext, supplier: Supplier, h: SupplierHistoryDto, from: string | null, to: string | null): ReportDoc {
  const profit = ctx.showProfit
  const listed = h.purchases.filter((p) => p.status !== 'DRAFT').sort((a, b) => a.purchaseDate.localeCompare(b.purchaseDate))
  const ok = listed.filter(counted)
  const columns = [col('Date', 1.2), col('Invoice', 1.6), col('Status', 1.5), col('Items', 0.8, 'right'), col('Total cost', 1.6, 'right'), ...(profit ? [col('Expected profit', 1.6, 'right')] : [])]
  const rows: Row[] = listed.map((p) => ({ cells: [date(p.purchaseDate), p.invoiceNo ?? NONE, purchaseStatus(p.status), String(p.itemCount), money(p.totalCost), ...(profit ? [money(p.totalExpectedProfit)] : [])], muted: p.status === 'CANCELLED' }))
  const cost = sumOf(ok.map((p) => p.totalCost))
  const dates = ok.map((p) => p.purchaseDate).sort()
  return doc(ctx, 'Supplier purchase report', [nm(supplier.name), period(from, to)], fileName(plainEn('Supplier'), supplier.name, from, to), present(
    { kind: 'facts', items: [[t('Supplier'), nm(supplier.name)], [t('Contact'), [supplier.contactPerson, supplier.mobile].filter(Boolean).join(', ') || null],
      [t('Address'), [supplier.address ? show(supplier.address)[0] : null, supplier.city ? show(supplier.city)[0] : null].filter(Boolean).join(', ') || null], ['GSTIN', supplier.gstin],
      [t('Period'), period(from, to)], [t('Purchases'), String(ok.length)], [t('Total cost'), money(toPlain(cost))],
      [t('Average purchase'), ok.length ? money(toPlain(div(cost, { n: BigInt(ok.length), s: 0 }, 2))) : null],
      [t('First purchase'), dates.length ? date(dates[0]) : null], [t('Last purchase'), dates.length ? date(dates[dates.length - 1]) : null]] },
    { kind: 'table', columns, rows, totals: [{ cells: [t('Total'), ok.length === 1 ? t('1 purchase') : t('%1$s purchases', ok.length), '', '', money(toPlain(cost)), ...(profit ? [money(toPlain(sumOf(ok.map((p) => p.totalExpectedProfit))))] : [])] }], emptyText: t('No purchases from this supplier in this period') },
    listed.some((p) => p.status === 'CANCELLED') && note(t('Cancelled purchases are listed in grey and not counted in the totals.')),
    profit && note(t(PROFIT_NOTE), true)))
}

// ----------------------------------------------------------------------------------------- 4. item history
export function itemDoc(ctx: PdfContext, item: Item, h: ItemHistoryDto, from: string | null, to: string | null): ReportDoc {
  const sorted = [...h.rows].sort((a, b) => a.purchaseDate.localeCompare(b.purchaseDate))
  const ok = sorted.filter((r) => r.status !== 'CANCELLED')
  const units = new Set(ok.map((r) => r.unitCode))
  const oneUnit = units.size <= 1
  const unit = ok[0]?.unitCode
  const prices = ok.map((r) => parse(r.purchaseUnitPrice)).filter((x): x is NonNullable<typeof x> => !!x)
  const priced = ok.filter((r) => r.totalCost !== null)
  const pricedQty = sumOf(priced.map((r) => r.quantity)); const pricedCost = sumOf(priced.map((r) => r.totalCost))
  const average = oneUnit && sign(pricedQty) > 0 ? div(pricedCost, pricedQty, 4) : null
  const sortedPrices = [...prices].sort(cmp)
  const nPurchases = new Set(ok.map((r) => r.purchaseId)).size
  const latest = [...ok].reverse().find((r) => r.purchaseUnitPrice !== null)
  const rows: Row[] = sorted.map((r) => ({
    cells: [date(r.purchaseDate), r.status === 'CANCELLED' ? both(nm(r.supplierName), t('Cancelled')) : nm(r.supplierName), r.invoiceNo ?? NONE, qty(r.quantity, r.unitCode), unitPrice(r.purchaseUnitPrice), unitPrice(r.sellingPrice), money(r.totalCost)],
    muted: r.status === 'CANCELLED' }))
  return doc(ctx, 'Item purchase report', [nm(item.name), period(from, to)], fileName(plainEn('Item'), item.name, from, to), present(
    { kind: 'facts', items: [[t('Item'), nm(item.name)], [t('Category'), item.category], [t('Period'), period(from, to)], [t('Purchases'), String(nPurchases)],
      [t('Lowest unit cost'), sortedPrices.length ? unitPrice(toPlain(sortedPrices[0])) : null], [t('Highest unit cost'), sortedPrices.length ? unitPrice(toPlain(sortedPrices[sortedPrices.length - 1])) : null],
      [t('Average unit cost'), average ? t('%1$s (weighted)', unitPrice(toPlain(average))) : null], [t('Latest unit cost'), latest ? unitPrice(latest.purchaseUnitPrice) : null]] },
    { kind: 'table', columns: [col('Date', 1.15), col('Supplier', 2.3), col('Invoice', 1.3), col('Quantity', 1.3, 'right'), col('Unit cost', 1.25, 'right'), col('Selling price', 1.25, 'right'), col('Amount', 1.4, 'right')], rows,
      totals: [{ cells: [t('Total'), `${nPurchases} ${plainEn('purchase(s)')}`, '', oneUnit ? qty(toPlain(sumOf(ok.map((r) => r.quantity))), unit) : t('Mixed units'), '', '', money(toPlain(sumOf(ok.map((r) => r.totalCost))))] }],
      emptyText: t('No purchases of this item in this period') },
    !oneUnit && note(t('This item was bought in more than one unit, so quantities are not added up.')),
    sorted.some((r) => r.status === 'CANCELLED') && note(t('Cancelled purchases are listed in grey and not counted in the totals.'))))
}

// ----------------------------------------------------------------------------------------- 5. server reports
const purchaseColumns = (profit: boolean): Column[] => [col('Date', 1.15), col('Supplier', 2.4), col('Invoice', 1.35), col('Status', 1.35), col('Items', 0.7, 'right'), col('Total cost', 1.5, 'right'), ...(profit ? [col('Expected profit', 1.5, 'right')] : [])]

export function purchaseReportDoc(ctx: PdfContext, r: PurchaseReportDto): ReportDoc {
  const profit = r.profitIncluded
  const per = period(r.period.from, r.period.to)
  return doc(ctx, 'Purchase report', [per], fileName(plainEn('Purchases'), r.period.from, r.period.to), present(
    { kind: 'facts', items: [[t('Period'), per], [t('Purchases'), String(r.purchaseCount)], [t('Total cost'), money(r.totalCost)], [t('Cancelled'), r.cancelledCount > 0 ? String(r.cancelledCount) : null], [t('Expected profit'), profit ? money(r.expectedProfit) : null]] },
    { kind: 'table', columns: purchaseColumns(profit), rows: r.rows.map((p) => ({ cells: [date(p.purchaseDate), nm(p.supplierName), p.invoiceNo ?? NONE, purchaseStatus(p.status), String(p.itemCount), money(p.totalCost), ...(profit ? [money(p.expectedProfit)] : [])], muted: p.status === 'CANCELLED' })),
      totals: [{ cells: [t('Total'), `${r.purchaseCount} ${plainEn('purchase(s)')}`, '', '', '', money(r.totalCost), ...(profit ? [money(r.expectedProfit)] : [])] }], emptyText: t('No purchases in this period') },
    r.cancelledCount > 0 && note(t('Cancelled purchases are listed in grey and not counted in the totals.')),
    profit && note(t(PROFIT_NOTE), true)))
}

export function supplierTotalsDoc(ctx: PdfContext, r: SupplierTotalsDto): ReportDoc {
  const profit = r.profitIncluded
  const per = period(r.period.from, r.period.to)
  return doc(ctx, 'Supplier totals', [per], fileName(plainEn('Supplier-totals'), r.period.from, r.period.to), present(
    { kind: 'facts', items: [[t('Period'), per], [t('Suppliers'), String(r.rows.length)], [t('Purchases'), String(r.purchaseCount)], [t('Total cost'), money(r.totalCost)]] },
    { kind: 'table', columns: [col('Supplier', 3), col('Purchases', 1, 'right'), col('Total cost', 1.6, 'right'), col('Share', 1, 'right'), col('Last purchase', 1.3), ...(profit ? [col('Expected profit', 1.5, 'right')] : [])],
      rows: r.rows.map((s) => ({ cells: [nm(s.supplierName), String(s.purchaseCount), money(s.totalCost), percent(s.sharePercent), date(s.lastPurchaseDate), ...(profit ? [money(s.expectedProfit)] : [])] })),
      totals: [{ cells: [t('Total'), String(r.purchaseCount), money(r.totalCost), r.rows.length ? '100.00%' : NONE, '', ...(profit ? [money(toPlain(sumOf(r.rows.map((s) => s.expectedProfit))))] : [])] }], emptyText: t('No purchases in this period') },
    profit && note(t(PROFIT_NOTE), true)))
}

export function itemPricesDoc(ctx: PdfContext, r: ItemPricesDto): ReportDoc {
  const per = period(r.period.from, r.period.to)
  const profit = r.profitIncluded
  return doc(ctx, 'Item purchase prices', [per], fileName(plainEn('Item-prices'), r.period.from, r.period.to), present(
    { kind: 'facts', items: [[t('Period'), per], [t('Items'), String(r.rows.length)]] },
    { kind: 'table', columns: [col('Item', 2.6), col('Bought', 1.3, 'right'), col('Lowest', 1.1, 'right'), col('Highest', 1.1, 'right'), col('Average', 1.1, 'right'), col('Latest', 1.9, 'right'), col('Selling price', 1.1, 'right'), ...(profit ? [col('Expected profit', 1.3, 'right')] : [])],
      rows: r.rows.map((i) => ({ cells: [nm(i.itemName), `${qty(i.totalQuantity, i.unitCode)}\n${i.purchaseCount} ${plainEn('purchase(s)')}`, unitPrice(i.lowestUnitPrice), unitPrice(i.highestUnitPrice), unitPrice(i.averageUnitPrice),
        `${unitPrice(i.latestUnitPrice)}\n${[date(i.latestDate), i.latestSupplierName ? show(i.latestSupplierName)[0] : null].filter((x) => x && x !== NONE).join(', ')}`, unitPrice(i.latestSellingPrice), ...(profit ? [money(i.expectedProfit)] : [])] })), emptyText: t('No purchases in this period') },
    note(t('Prices are per unit. The average is weighted by quantity. An item bought in two units appears once per unit.')),
    profit && note(t(PROFIT_NOTE), true)))
}

export function expectedProfitDoc(ctx: PdfContext, r: ExpectedProfitDto): ReportDoc {
  const per = period(r.period.from, r.period.to)
  const cols = (first: string): Column[] => [col(first, 3), col('Cost', 1.4, 'right'), col('Expected sales', 1.5, 'right'), col('Expected profit', 1.5, 'right'), col('Expected margin', 1.2, 'right')]
  const rows = (list: ProfitRowDto[]): Row[] => list.map((x) => ({ cells: [nm(x.name), money(x.cost), money(x.expectedSales), money(x.expectedProfit), percent(x.marginPercent)] }))
  return doc(ctx, 'Expected profit report', [per, t('Estimate only, not realised profit')], fileName(plainEn('Expected-profit'), r.period.from, r.period.to), present(
    note(hasText(r.note) ? r.note : t(PROFIT_NOTE), true),
    { kind: 'facts', items: [[t('Period'), per], [t('Purchases'), String(r.purchaseCount)], [t('Total cost'), money(r.totalCost)], [t('Cost with selling price'), money(r.pricedCost)], [t('Cost without selling price'), money(r.unpricedCost)],
      [t('Expected sales'), money(r.expectedSales)], [t('Expected profit'), money(r.expectedProfit)], [t('Expected margin'), percent(r.marginPercent)]] },
    { kind: 'heading', text: t('By supplier') }, { kind: 'table', columns: cols('Supplier'), rows: rows(r.bySupplier), emptyText: t('No purchases in this period') },
    { kind: 'heading', text: t('By item') }, { kind: 'table', columns: cols('Item'), rows: rows(r.byItem), emptyText: t('No purchases in this period') }))
}

// ----------------------------------------------------------------------------------------- 6. expiry
const EXPIRY_FILTERS: Record<string, string> = { ALL: 'All with an expiry date', EXPIRED: 'Expired', WITHIN_7: 'Within 7 days', WITHIN_30: 'Within 30 days', WITHIN_90: 'Within 90 days' }
export function expiryLabel(days: number | null): Bi {
  if (days === null) return t('No expiry date')
  if (days < 0) return days === -1 ? t('Expired 1 day ago') : t('Expired %1$s days ago', -days)
  if (days === 0) return t('Expires today')
  return days === 1 ? t('Expires in 1 day') : t('Expires in %1$s days', days)
}
export function expiryDoc(ctx: PdfContext, batches: Batch[], filter: string, today: string): ReportDoc {
  const list = t(EXPIRY_FILTERS[filter] ?? 'All with an expiry date')
  const rows: Row[] = batches.map((b) => ({ cells: [nm(b.itemName), b.batchNo ?? NONE, date(b.expiryDate), expiryLabel(b.daysToExpiry), qty(b.quantity, b.unitCode), nm(b.supplierName), b.invoiceNo ?? NONE], bold: b.daysToExpiry !== null && b.daysToExpiry < 0 }))
  return doc(ctx, 'Expiry report', [list, t('As on %1$s', date(today))], fileName(plainEn('Expiry'), filter.toLowerCase(), today), present(
    { kind: 'facts', items: [[t('List'), list], [t('As on'), date(today)], [t('Batches'), String(batches.length)], [t('Already expired'), String(batches.filter((b) => b.daysToExpiry !== null && b.daysToExpiry < 0).length)]] },
    { kind: 'table', columns: [col('Item', 2.8), col('Batch', 1.2), col('Expiry', 1.1), col('Status', 1.5), col('Stock', 1.2, 'right'), col('Supplier', 1.7), col('Invoice', 1.4)], rows, emptyText: t('No batches in this list') },
    note(t('Only stock still on hand from completed purchases is listed. Expired batches are in bold.'))))
}

export const shareOf = share
