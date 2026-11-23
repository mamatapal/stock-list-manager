import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { batches, items, purchases, stockLists, suppliers, units } from '../api/catalogue'
import { useAuth } from '../auth/AuthContext'
import { ErrorText } from '../components/ErrorText'
import { Loading } from '../components/ui'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'
import { renderPages } from '../pdf/canvas'
import { expectedProfitDoc, expiryDoc, itemDoc, itemPricesDoc, purchaseDoc, purchaseReportDoc, sellingPricesDoc, stockListDoc, supplierDoc, supplierTotalsDoc, type PdfContext } from '../pdf/documents'
import { buildPdf } from '../pdf/file'
import type { ReportDoc } from '../pdf/model'
import type { BusinessDto, ExpectedProfitDto, ItemHistoryDto, ItemPricesDto, PurchaseReportDto, SupplierHistoryDto, SupplierTotalsDto } from '../pdf/serverTypes'
import { PAGE_H, PAGE_W } from '../pdf/layout'

const SCALE = 2.5
const qs = (o: Record<string, string | null | undefined>) => {
  const p = new URLSearchParams()
  Object.entries(o).forEach(([k, v]) => { if (v) p.set(k, v) })
  const s = p.toString()
  return s ? `?${s}` : ''
}
const todayIso = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }

export interface Built { doc: ReportDoc }

/** Fetches what the document needs and builds it. Routes: /pdf/list/:id, /pdf/purchase/:id, /pdf/supplier/:id, /pdf/item/:id, /pdf/expiry, /pdf/report/:kind. */
export async function buildDocument(kind: string, id: string | undefined, q: URLSearchParams, ctx: PdfContext): Promise<ReportDoc> {
  const from = q.get('from'); const to = q.get('to')
  switch (kind) {
    case 'list': {
      const [list, unitList] = await Promise.all([stockLists.get(id!), units.list()])
      const sup = list.supplierId ? await suppliers.get(list.supplierId).catch(() => null) : null
      return stockListDoc(ctx, list, sup, Object.fromEntries(unitList.map((u) => [u.id, u.code])))
    }
    case 'purchase': {
      const p = await purchases.get(id!)
      if (q.get('prices') === 'selling') return sellingPricesDoc(ctx, p)
      const sup = await suppliers.get(p.supplierId).catch(() => null)
      return purchaseDoc(ctx, p, sup)
    }
    case 'supplier': {
      const [sup, h] = await Promise.all([suppliers.get(id!), api.get<SupplierHistoryDto>(`/api/v1/purchases/by-supplier/${id}${qs({ from, to })}`)])
      return supplierDoc(ctx, sup, h, from, to)
    }
    case 'item': {
      const [item, h] = await Promise.all([items.get(id!), api.get<ItemHistoryDto>(`/api/v1/purchases/by-item/${id}${qs({ from, to })}`)])
      return itemDoc(ctx, item, h, from, to)
    }
    case 'expiry': {
      const filter = q.get('filter') ?? 'WITHIN_30'
      const [list, alert] = await Promise.all([batches.list(filter), batches.alert()])
      return expiryDoc(ctx, list, filter, alert.today ?? todayIso())
    }
    case 'report': {
      const r = id
      const range = qs({ from, to })
      if (r === 'purchases') return purchaseReportDoc(ctx, await api.get<PurchaseReportDto>(`/api/v1/reports/purchases${range}`))
      if (r === 'supplier-totals') return supplierTotalsDoc(ctx, await api.get<SupplierTotalsDto>(`/api/v1/reports/supplier-totals${range}`))
      if (r === 'item-prices') return itemPricesDoc(ctx, await api.get<ItemPricesDto>(`/api/v1/reports/item-prices${range}`))
      if (r === 'expected-profit') return expectedProfitDoc(ctx, await api.get<ExpectedProfitDto>(`/api/v1/reports/expected-profit${range}`))
      throw new Error('Unknown report')
    }
    default: throw new Error('Unknown document')
  }
}

export async function loadContext(generatedBy: string | null, showProfit: boolean): Promise<PdfContext> {
  const [business, people] = await Promise.all([
    api.get<BusinessDto>('/api/v1/business'),
    api.get<Array<{ id: string; fullName: string }>>('/api/v1/people').catch(() => []),
  ])
  return { business, generatedBy, generatedAt: new Date(), people: Object.fromEntries(people.map((p) => [p.id, p.fullName])), showProfit }
}

function toDataUrl(jpeg: Uint8Array): string {
  let s = ''
  for (let i = 0; i < jpeg.length; i += 0x8000) s += String.fromCharCode(...jpeg.subarray(i, i + 0x8000))
  return 'data:image/jpeg;base64,' + btoa(s)
}

interface Ready { name: string; pdf: Uint8Array; previews: string[] }

export function PdfPage() {
  useLanguage()
  const { kind = '', id } = useParams()
  const [q] = useSearchParams()
  const { user, can } = useAuth()
  const [ready, setReady] = useState<Ready | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [note, setNote] = useState<string | null>(null)
  const qKey = q.toString()

  useEffect(() => {
    let alive = true
    setReady(null); setError(null)
    ;(async () => {
      const ctx = await loadContext(user?.fullName ?? null, can('EXPECTED_PROFIT_VIEW'))
      const doc = await buildDocument(kind, id, new URLSearchParams(qKey), ctx)
      const { jpegs } = await renderPages(doc)
      const pdf = buildPdf(jpegs, Math.round(PAGE_W * SCALE), Math.round(PAGE_H * SCALE))
      if (alive) setReady({ name: doc.fileName, pdf, previews: jpegs.map(toDataUrl) })
    })().catch((e) => { if (alive) setError(e) })
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind, id, qKey])

  const blob = () => new Blob([ready!.pdf as BlobPart], { type: 'application/pdf' })
  function download() {
    const url = URL.createObjectURL(blob())
    const a = document.createElement('a')
    a.href = url; a.download = ready!.name
    document.body.appendChild(a); a.click(); a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }
  async function share() {
    const file = new File([blob()], ready!.name, { type: 'application/pdf' })
    try {
      if (navigator.canShare?.({ files: [file] })) await navigator.share({ files: [file], title: ready!.name })
      else { download(); setNote('Sharing is not available in this browser, so the PDF was saved instead.') }
    } catch (e) { if ((e as { name?: string }).name !== 'AbortError') setError(e) }
  }

  if (error) return <ErrorText error={error} />
  if (!ready) return <><h1><Tr k="PDF" /></h1><Loading /><p className="muted"><Tr k="Preparing the PDF…" /></p></>
  return (
    <>
      <h1><Tr k="PDF" /></h1>
      <div className="pdf-actions">
        <button type="button" className="primary" onClick={download}><Tr k="Download PDF" inline /></button>
        <button type="button" onClick={() => { void share() }}><Tr k="Share PDF" inline /></button>
      </div>
      {note && <p className="muted"><Tr k={note} /></p>}
      <div className="pdf-pages">
        {ready.previews.map((src, i) => <img key={i} src={src} alt={`${trLine('Page %1$s of %2$s', i + 1, ready.previews.length)}`} className="pdf-page" />)}
      </div>
    </>
  )
}
