import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { items, purchases, stockLists, suppliers, units } from '../api/catalogue'
import type { Item, ListLine, Purchase, PurchaseLine, StockList, Supplier, Unit } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BilingualNameFields } from '../components/BilingualNameFields'
import { ItemCombo } from '../components/ItemCombo'
import { DeleteButton } from '../components/DeleteButton'
import { ErrorText } from '../components/ErrorText'
import { Name, oneLine } from '../components/Name'
import { AddLink, CheckField, Empty, Loading, SearchBox, SelectField, TextField } from '../components/ui'
import { useDebounced, useLoad } from '../components/useLoad'
import { clean } from '../i18n/bilingualName'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const newId = () => crypto.randomUUID()

const STATUS_TEXT: Record<string, string> = {
  DRAFT: 'Draft', PENDING: 'Waiting for goods', PARTIAL: 'Partly received', FULL: 'Fully received', CANCELLED: 'Cancelled',
}
const StatusTag = ({ status }: { status: string }) => <span className={status === 'CANCELLED' ? 'tag bad' : 'tag'}><Tr k={STATUS_TEXT[status] ?? status} inline /></span>

export function PurchaseList() {
  const { can } = useAuth()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const dq = useDebounced(q)
  const { data, error, loading } = useLoad(() => purchases.list(dq, status), [dq, status])
  useLanguage()
  return (
    <>
      <h1><Tr k="Purchases" /></h1>
      {can('PURCHASE_RECORD') && <AddLink to="/purchases/new" label="New purchase" />}
      <SearchBox value={q} onChange={setQ} />
      <SelectField label="Status" value={status} onChange={setStatus}>
        <option value="">{trLine('All')}</option>
        {Object.entries(STATUS_TEXT).map(([k, v]) => <option key={k} value={k}>{trLine(v)}</option>)}
      </SelectField>
      <ErrorText error={error} />
      {loading && !data ? <Loading /> : data && data.length === 0 ? <Empty text="No purchases yet" /> : (
        <ul className="rows">
          {data?.map((p) => (
            <li key={p.id}>
              <Link to={`/purchases/${p.id}`} className="row">
                <span className="row-main"><Name value={p.supplierName} /></span>
                <span className="row-sub">
                  {p.purchaseDate}{p.invoiceNo ? ` · #${p.invoiceNo}` : ''}{p.totalCost ? ` · ₹${p.totalCost}` : ''} · {p.itemCount} <Tr k="items" inline />
                </span>
                <StatusTag status={p.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

/** One purchase: draft → form; otherwise the details with receiving and cancel. */
export function PurchaseScreen({ mode }: { mode: 'view' | 'edit' }) {
  const { id } = useParams()
  // keyed by the purchase, so opening another one (or the one just saved) starts from a clean load
  return <PurchaseLoader key={`${id ?? 'new'}-${mode}`} id={id} mode={mode} />
}

function PurchaseLoader({ id, mode }: { id: string | undefined; mode: 'view' | 'edit' }) {
  const p = useLoad<Purchase | null>(() => (id ? purchases.get(id) : Promise.resolve(null)), [id])
  if (id && (p.loading || (!p.data && !p.error))) return <Loading />
  if (id && p.error) return <ErrorText error={p.error} />
  if (!id || mode === 'edit' || p.data?.status === 'DRAFT') return <PurchaseForm existing={p.data} />
  return <PurchaseView purchase={p.data!} reload={p.reload} />
}

interface LineForm { id: string; itemId: string; quantity: string; unitId: string; priceBasis: 'PER_UNIT' | 'TOTAL'; purchasePrice: string; sellingPrice: string; stockListItemId: string | null; updateCatalog: boolean }

const fromPurchaseLine = (l: PurchaseLine): LineForm => ({
  id: l.id, itemId: l.itemId, quantity: l.quantity, unitId: l.unitId, priceBasis: l.priceBasis, purchasePrice: l.purchasePrice ?? '',
  sellingPrice: l.sellingPrice ?? '', stockListItemId: l.stockListItemId, updateCatalog: l.updateCatalog,
})

function PurchaseForm({ existing }: { existing: Purchase | null }) {
  const { can } = useAuth()
  const navigate = useNavigate()
  useLanguage()
  const supplierList = useLoad<Supplier[]>(() => suppliers.list(), [])
  const itemList = useLoad<Item[]>(() => items.list(), [])
  const unitList = useLoad<Unit[]>(() => units.list(), [])
  const lists = useLoad<StockList[]>(() => stockLists.list().catch(() => []), [])
  if (supplierList.loading || itemList.loading || unitList.loading || lists.loading) return <Loading />
  const err = supplierList.error ?? itemList.error ?? unitList.error
  if (err) return <ErrorText error={err} />
  return <PurchaseFormBody existing={existing} suppliers={supplierList.data ?? []} items={itemList.data ?? []} units={(unitList.data ?? []).filter((u) => !u.deleted)} lists={(lists.data ?? []).filter((l) => !l.deleted)} navigate={navigate} can={can} />
}

const remainingOf = (l: ListLine) => (l.remainingQuantity && l.remainingQuantity !== '0' ? l.remainingQuantity : l.quantity)
/** A line can be bought when it has a name with something still to come; a name not yet in the catalogue is added on pick. */
const buyable = (l: ListLine) => !!(l.itemId || l.itemName?.trim()) && !(l.remainingQuantity !== null && Number(l.remainingQuantity) <= 0)

/**
 * Every item still to buy from the out-of-stock lists, grouped by list, each with its own tick box, so items this supplier
 * cannot give can simply be left unticked. Lists of the chosen supplier (or of no supplier) come first.
 */
function ListPicker({ lists, supplierId, added, defaultOpen, onAdd }: { lists: StockList[]; supplierId: string; added: Set<string>; defaultOpen: boolean; onAdd: (picked: Array<{ list: StockList; line: ListLine }>) => void }) {
  useLanguage()
  const [open, setOpen] = useState(defaultOpen)
  const [ticked, setTicked] = useState<Set<string>>(new Set())
  const groups = lists
    .map((list) => ({ list, rows: list.status === 'SAVED' ? list.lines.filter(buyable) : [] }))
    .filter((g) => g.rows.length > 0)
    .sort((a, b) => Number(!!supplierId && !(a.list.supplierId === supplierId || !a.list.supplierId)) - Number(!!supplierId && !(b.list.supplierId === supplierId || !b.list.supplierId)))
  const toggle = (id: string, on: boolean) => setTicked((t) => { const n = new Set(t); if (on) n.add(id); else n.delete(id); return n })
  if (groups.length === 0) {
    const why = lists.length === 0 ? 'There are no out-of-stock lists yet.'
      : lists.every((l) => l.status !== 'SAVED') ? 'Your out-of-stock lists are drafts. Save a list first, then pick from it here.'
      : 'No out-of-stock list has an item left to buy. Items typed without a catalogue item, or already received, are not offered.'
    return <p className="muted small"><Tr k="Pick from out-of-stock list" />: <Tr k={why} /></p>
  }
  const count = groups.reduce((n, g) => n + g.rows.filter((l) => !added.has(l.id)).length, 0)
  if (!open) return <button type="button" className="dropdown-btn" aria-expanded="false" onClick={() => setOpen(true)}>{trLine('Pick from out-of-stock list')} ({count}) ▾</button>
  const picked = groups.flatMap((g) => g.rows.filter((l) => ticked.has(l.id) && !added.has(l.id)).map((line) => ({ list: g.list, line })))
  return (
    <section className="card dropdown-panel" aria-label={trLine('Pick from out-of-stock list')}>
      <button type="button" className="dropdown-btn" aria-expanded="true" onClick={() => setOpen(false)}>{trLine('Pick from out-of-stock list')} ({picked.length}) ▴</button>
      <p className="muted small"><Tr k="Tap the items you are buying now. Leave out items this supplier cannot give." /></p>
      <div className="dropdown-scroll">
      {groups.map(({ list, rows }) => {
        const free = rows.filter((l) => !added.has(l.id))
        const allOn = free.length > 0 && free.every((l) => ticked.has(l.id))
        return (
          <fieldset key={list.id} className="perm-group">
            <legend>{oneLine(list.title)} — {list.listDate}</legend>
            {free.length > 1 && <button type="button" onClick={() => setTicked((t) => { const n = new Set(t); free.forEach((l) => (allOn ? n.delete(l.id) : n.add(l.id))); return n })}>{trLine('Select all')}</button>}
            <ul role="listbox" aria-multiselectable="true" className="pick-list">
              {rows.map((l) => {
                const on = added.has(l.id) || ticked.has(l.id)
                return (
                  <li key={l.id} role="presentation">
                    <button type="button" role="option" aria-selected={on} className={on ? 'pick-row selected' : 'pick-row'} disabled={added.has(l.id)} onClick={() => toggle(l.id, !ticked.has(l.id))}>
                      <Name value={l.itemName} inline /> — <Tr k="Remaining %1$s" args={[remainingOf(l)]} inline />
                      {added.has(l.id) && <> · <Tr k="Already added" inline /></>}
                    </button>
                  </li>
                )
              })}
            </ul>
          </fieldset>
        )
      })}
      </div>
      <button type="button" className="primary" disabled={picked.length === 0} onClick={() => { onAdd(picked); setTicked(new Set()); setOpen(false) }}>
        {trLine('Add %1$s', picked.length)}
      </button>
    </section>
  )
}

function PurchaseFormBody({ existing, suppliers: supplierList, items: loadedItems, units: unitList, lists, navigate, can }: {
  existing: Purchase | null; suppliers: Supplier[]; items: Item[]; units: Unit[]; lists: StockList[]
  navigate: ReturnType<typeof useNavigate>; can: (p: string) => boolean
}) {
  const [addedItems, setAddedItems] = useState<Item[]>([])
  const itemList = [...loadedItems, ...addedItems]
  const [supplierId, setSupplierId] = useState(existing?.supplierId ?? '')
  const [invoiceNo, setInvoiceNo] = useState(existing?.invoiceNo ?? '')
  const [purchaseDate, setPurchaseDate] = useState(existing?.purchaseDate ?? today())
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [stockListId, setStockListId] = useState(existing?.stockListId ?? '')
  const [lines, setLines] = useState<LineForm[]>(() => (existing ? existing.lines.map(fromPurchaseLine) : []))
  const [arrived, setArrived] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const canPrice = can('PURCHASE_PRICE_ENTER')
  const canSell = can('SELLING_PRICE_ENTER')
  const wasCompleted = !!existing && existing.status !== 'DRAFT'

  const patch = (id: string, p: Partial<LineForm>) => setLines((ls) => ls.map((l) => (l.id === id ? { ...l, ...p } : l)))
  const addLine = () => setLines((ls) => [...ls, { id: newId(), itemId: '', quantity: '', unitId: unitList[0]?.id ?? '', priceBasis: 'PER_UNIT', purchasePrice: '', sellingPrice: '', stockListItemId: null, updateCatalog: false }])
  const pickItem = (lineId: string, itemId: string) => {
    const item = itemList.find((i) => i.id === itemId)
    if (!item) { patch(lineId, { itemId: '' }); return }
    patch(lineId, {
      itemId, unitId: item.defaultUnitId,
      purchasePrice: canPrice ? item.latestPurchasePrice ?? '' : '', sellingPrice: canSell ? item.latestSellingPrice ?? '' : '',
      priceBasis: 'PER_UNIT',
    })
  }

  async function createItem(lineId: string, unitId: string, name: string) {
    try {
      const item = await items.create({ name, category: null, defaultUnitId: unitId || unitList[0]?.id, latestPurchasePrice: null, latestSellingPrice: null, preferredSupplierId: null, notes: null, active: true })
      setAddedItems((a) => [...a, item])
      patch(lineId, { itemId: item.id, unitId: item.defaultUnitId, priceBasis: 'PER_UNIT' })
    } catch (err) { setError(err) }
  }

  /** Adds the ticked lines at their remaining quantity. The supplier and the list link come from the first list when still empty. */
  async function addPicked(picked: Array<{ list: StockList; line: ListLine }>) {
    if (picked.length === 0) return
    const first = picked[0].list
    if (!stockListId) setStockListId(first.id)
    if (!supplierId) { const withSupplier = picked.find((p) => p.list.supplierId); if (withSupplier) setSupplierId(withSupplier.list.supplierId!) }
    // A list line typed without a catalogue item is matched by name, or added to the catalogue now.
    const known = [...itemList]
    const resolved: Array<{ line: ListLine; item: Item | undefined }> = []
    try {
      for (const { line: l } of picked) {
        let item = known.find((i) => (l.itemId ? i.id === l.itemId : i.name.trim().toLowerCase() === (l.itemName ?? '').trim().toLowerCase()))
        if (!item && !l.itemId) {
          if (!can('ITEM_ADD')) { setError('You do not have permission to add items to the catalogue'); continue }
          item = await items.create({ name: (l.itemName ?? '').trim(), category: null, defaultUnitId: l.unitId, latestPurchasePrice: null, latestSellingPrice: null, preferredSupplierId: null, notes: null, active: true })
          known.push(item); setAddedItems((a) => [...a, item!])
        }
        if (item) resolved.push({ line: l, item })
      }
    } catch (err) { setError(err) }
    setLines((ls) => [...ls, ...resolved.map(({ line: l, item }) => ({
      id: newId(), itemId: item!.id, quantity: remainingOf(l), unitId: l.unitId, priceBasis: 'PER_UNIT' as const,
      purchasePrice: canPrice ? l.effectiveUnitPrice ?? item?.latestPurchasePrice ?? '' : '',
      sellingPrice: canSell ? item?.latestSellingPrice ?? '' : '', stockListItemId: l.id, updateCatalog: false,
    }))])
  }

  async function save(complete: boolean) {
    if (!supplierId) { setError('Choose a supplier'); return }
    if (lines.length === 0) { setError('Add at least one item'); return }
    if (lines.some((l) => !l.itemId)) { setError('Choose an item'); return }
    if (lines.some((l) => !l.quantity.trim())) { setError('Quantity is required'); return }
    setBusy(true); setError(null)
    const body = {
      supplierId, invoiceNo: invoiceNo.trim() || null, purchaseDate, stockListId: stockListId || null, notes: clean(notes), complete,
      lines: lines.map((l) => ({
        id: l.id, itemId: l.itemId, quantity: l.quantity.trim(), unitId: l.unitId, priceBasis: l.priceBasis,
        purchasePrice: l.purchasePrice.trim() || null, sellingPrice: l.sellingPrice.trim() || null,
        stockListItemId: l.stockListItemId, updateCatalog: l.updateCatalog,
      })),
      receive: complete && arrived && !wasCompleted ? {
        id: newId(), receivedDate: purchaseDate, notes: null,
        lines: lines.map((l) => ({ id: newId(), purchaseLineId: l.id, kind: 'RECEIPT', quantity: l.quantity.trim(), correctsId: null, reason: null, batchNo: null, expiryDate: null })),
      } : null,
    }
    try {
      const saved = existing ? await purchases.update(existing.id, existing.version, body) : await purchases.create(body)
      navigate(`/purchases/${saved.id}`, { replace: true })
    } catch (e) { setError(e); setBusy(false) }
  }

  async function removeDraft() {
    if (!existing) return
    setBusy(true)
    try { await purchases.removeDraft(existing.id, existing.version); navigate('/purchases', { replace: true }) } catch (e) { setError(e); setBusy(false) }
  }

  const submit = (e: FormEvent) => { e.preventDefault(); void save(true) }

  return (
    <form onSubmit={submit} noValidate>
      <h1><Tr k={existing ? 'Edit purchase' : 'New purchase'} /></h1>
      <SelectField label="Supplier" required value={supplierId} onChange={setSupplierId}>
        <option value="">{trLine('Choose a supplier')}</option>
        {supplierList.filter((s) => s.active || s.id === supplierId).map((s) => <option key={s.id} value={s.id}>{oneLine(s.name)}</option>)}
      </SelectField>
      <TextField label="Invoice number" value={invoiceNo} onChange={setInvoiceNo} />
      <TextField label="Purchase date" type="date" value={purchaseDate} onChange={setPurchaseDate} required />
      <BilingualNameFields value={notes} onChange={setNotes} title="Notes" multiline englishLabel="Notes (English)" hindiLabel="Notes (Hindi)" />

      {!wasCompleted && <ListPicker lists={lists} supplierId={supplierId} added={new Set(lines.map((l) => l.stockListItemId).filter((x): x is string => !!x))} defaultOpen={false} onAdd={addPicked} />}

      <h2><Tr k="Items bought" /></h2>
      {lines.length === 0 && <Empty text="No items yet" />}
      {lines.map((l) => (
        <section key={l.id} className="card line">
          <ItemCombo label="Item" required value={l.itemId} items={itemList.filter((i) => i.active || i.id === l.itemId)}
            onSelect={(v) => pickItem(l.id, v)} onCreate={can('ITEM_ADD') ? (name) => createItem(l.id, l.unitId, name) : undefined} />
          <div className="grid2">
            <TextField label="Quantity" inputMode="decimal" value={l.quantity} onChange={(v) => patch(l.id, { quantity: v })} required />
            <SelectField label="Unit" value={l.unitId} onChange={(v) => patch(l.id, { unitId: v })}>
              {unitList.map((u) => <option key={u.id} value={u.id}>{u.code}</option>)}
            </SelectField>
          </div>
          <div className="grid2">
            <SelectField label="Price is" value={l.priceBasis} onChange={(v) => patch(l.id, { priceBasis: v as 'PER_UNIT' | 'TOTAL' })}>
              <option value="PER_UNIT">{trLine('Per unit')}</option>
              <option value="TOTAL">{trLine('For the whole line')}</option>
            </SelectField>
            <TextField label="Purchase price" inputMode="decimal" value={l.purchasePrice} onChange={(v) => patch(l.id, { purchasePrice: v })} readOnly={!canPrice} />
          </div>
          <TextField label="Selling price (per unit)" inputMode="decimal" value={l.sellingPrice} onChange={(v) => patch(l.id, { sellingPrice: v })} readOnly={!canSell} />
          {(canPrice || canSell) && <CheckField label="Update the item's latest prices" checked={l.updateCatalog} onChange={(v) => patch(l.id, { updateCatalog: v })} />}
          <button type="button" className="danger" onClick={() => setLines((ls) => ls.filter((x) => x.id !== l.id))}>{trLine('Remove this line')}</button>
        </section>
      ))}
      <button type="button" onClick={addLine}>{trLine('Add item')}</button>

      {!wasCompleted && <CheckField label="The goods arrived with the bill" checked={arrived} onChange={setArrived} />}
      <ErrorText error={error} />
      {!wasCompleted && <button type="button" disabled={busy} onClick={() => void save(false)}>{busy ? trLine('Saving…') : trLine('Save as draft')}</button>}
      <button type="submit" className="primary" disabled={busy}>{busy ? trLine('Saving…') : trLine(wasCompleted ? 'Save changes' : 'Complete purchase')}</button>
      <Link className="button" to={existing ? `/purchases/${existing.id}` : '/purchases'}>{trLine('Cancel')}</Link>
      {existing && !wasCompleted && <DeleteButton onConfirm={removeDraft} busy={busy} />}
    </form>
  )
}

function PurchaseView({ purchase: p, reload }: { purchase: Purchase; reload: () => void }) {
  const { can } = useAuth()
  const navigate = useNavigate()
  useLanguage()
  const [receiving, setReceiving] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const cancelled = p.status === 'CANCELLED'

  async function cancel() {
    if (!reason.trim()) { setError('Give a reason'); return }
    setBusy(true); setError(null)
    try { await purchases.cancel(p.id, p.version, reason.trim()); setCancelling(false); reload() } catch (e) { setError(e) } finally { setBusy(false) }
  }

  return (
    <>
      <h1><Name value={p.supplierName} /></h1>
      {p.status !== 'DRAFT' && (
        <div className="pdf-actions">
          <Link className="button" to={`/pdf/purchase/${p.id}`}><Tr k="Cost price and selling price" inline /></Link>
          <Link className="button" to={`/pdf/purchase/${p.id}?prices=selling`}><Tr k="Selling price only" inline /></Link>
        </div>
      )}
      <section className="card">
        <p><StatusTag status={p.status} /></p>
        <dl>
          <dt><Tr k="Purchase date" /></dt><dd>{p.purchaseDate}</dd>
          {p.invoiceNo && <><dt><Tr k="Invoice number" /></dt><dd>{p.invoiceNo}</dd></>}
          {p.totalCost && <><dt><Tr k="Total" /></dt><dd>₹{p.totalCost}</dd></>}
          {p.totalExpectedProfit && <><dt><Tr k="Expected profit" /></dt><dd>₹{p.totalExpectedProfit}{p.marginPercent ? ` (${p.marginPercent}%)` : ''}</dd></>}
          {p.receivedDate && <><dt><Tr k="Received on" /></dt><dd>{p.receivedDate}</dd></>}
        </dl>
        {p.notes && <p className="muted"><Name value={p.notes} /></p>}
        {p.duplicateInvoice && <p className="error"><Tr k="Another purchase from this supplier has the same invoice number." /></p>}
        {p.overReceived && <p className="error"><Tr k="More goods were received than were billed." /></p>}
        {cancelled && p.cancelReason && <p className="error"><Tr k="Cancelled" inline />: {p.cancelReason}</p>}
      </section>

      <h2><Tr k="Items bought" /></h2>
      {p.lines.map((l) => (
        <section key={l.id} className="card">
          <p className="row-main"><Name value={l.itemName} /></p>
          <dl>
            <dt><Tr k="Quantity" /></dt><dd>{l.quantity} {l.unitCode}</dd>
            <dt><Tr k="Received" /></dt><dd>{l.receivedQuantity ?? '0'} {l.unitCode}</dd>
            <dt><Tr k="Remaining" /></dt><dd>{l.remainingQuantity ?? l.quantity} {l.unitCode}</dd>
            {l.purchaseUnitPrice && <><dt><Tr k="Purchase price" /></dt><dd>₹{l.purchaseUnitPrice} / {l.unitCode}</dd></>}
            {l.sellingPrice && <><dt><Tr k="Selling price" /></dt><dd>₹{l.sellingPrice} / {l.unitCode}</dd></>}
            {l.totalCost && <><dt><Tr k="Total" /></dt><dd>₹{l.totalCost}</dd></>}
            {l.expectedProfit && <><dt><Tr k="Expected profit" /></dt><dd>₹{l.expectedProfit}</dd></>}
          </dl>
          {l.sellingBelowCost && <p className="error"><Tr k="Selling price is below the cost price." /></p>}
        </section>
      ))}

      {p.deliveries.length > 0 && (
        <>
          <h2><Tr k="Goods received" /></h2>
          {p.deliveries.map((d) => (
            <section key={d.id} className="card">
              <p className="row-main">{d.receivedDate}</p>
              {d.notes && <p className="muted small">{d.notes}</p>}
              <ul className="plain">
                {d.lines.map((dl) => {
                  const line = p.lines.find((x) => x.id === dl.purchaseLineId)
                  return (
                    <li key={dl.id}>
                      <Name value={line?.itemName} inline /> — {dl.quantity} {line?.unitCode}
                      {dl.batchNo ? ` · ${dl.batchNo}` : ''}{dl.expiryDate ? ` · ${dl.expiryDate}` : ''}
                    </li>
                  )
                })}
              </ul>
            </section>
          ))}
        </>
      )}

      <ErrorText error={error} />
      {!cancelled && can('PURCHASE_RECORD') && !receiving && <button type="button" className="primary" onClick={() => setReceiving(true)}>{trLine('Receive goods')}</button>}
      {receiving && <ReceiveForm purchase={p} onDone={() => { setReceiving(false); reload() }} onCancel={() => setReceiving(false)} />}
      {!cancelled && can('PURCHASE_EDIT') && <Link className="button" to={`/purchases/${p.id}/edit`}>{trLine('Edit')}</Link>}
      {!cancelled && (can('PURCHASE_DELETE') || can('PURCHASE_RECORD')) && !cancelling && <button type="button" className="danger" onClick={() => setCancelling(true)}>{trLine('Cancel this purchase')}</button>}
      {cancelling && (
        <section className="card confirm">
          <TextField label="Reason for cancelling" value={reason} onChange={setReason} required />
          <button type="button" className="danger" disabled={busy} onClick={cancel}>{trLine('Cancel this purchase')}</button>
          <button type="button" onClick={() => setCancelling(false)}>{trLine('Keep it')}</button>
        </section>
      )}
      <button type="button" onClick={() => navigate('/purchases')}>{trLine('Back to purchases')}</button>
    </>
  )
}

function ReceiveForm({ purchase: p, onDone, onCancel }: { purchase: Purchase; onDone: () => void; onCancel: () => void }) {
  useLanguage()
  const [date, setDate] = useState(today())
  const [notes, setNotes] = useState('')
  const rows = useMemo(() => p.lines.filter((l) => !(l.remainingQuantity === '0')), [p.lines])
  const [qty, setQty] = useState<Record<string, string>>(() => Object.fromEntries(rows.map((l) => [l.id, l.remainingQuantity ?? l.quantity])))
  const [batch, setBatch] = useState<Record<string, string>>({})
  const [expiry, setExpiry] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function save(e: FormEvent) {
    e.preventDefault()
    const lines = rows.filter((l) => (qty[l.id] ?? '').trim() && qty[l.id] !== '0').map((l) => ({
      id: newId(), purchaseLineId: l.id, kind: 'RECEIPT', quantity: qty[l.id].trim(), correctsId: null, reason: null,
      batchNo: (batch[l.id] ?? '').trim() || null, expiryDate: expiry[l.id] || null,
    }))
    if (lines.length === 0) { setError('Enter at least one received quantity'); return }
    setBusy(true); setError(null)
    try { await purchases.receive(p.id, { id: newId(), receivedDate: date, notes: clean(notes) || null, lines }); onDone() } catch (err) { setError(err); setBusy(false) }
  }

  return (
    <form onSubmit={save} noValidate className="card">
      <h2><Tr k="Receive goods" /></h2>
      <TextField label="Received date" type="date" value={date} onChange={setDate} required />
      {rows.map((l) => (
        <div key={l.id} className="line">
          <p className="row-main"><Name value={l.itemName} /></p>
          <TextField label="Quantity received" inputMode="decimal" value={qty[l.id] ?? ''} onChange={(v) => setQty({ ...qty, [l.id]: v })} />
          <div className="grid2">
            <TextField label="Batch number" value={batch[l.id] ?? ''} onChange={(v) => setBatch({ ...batch, [l.id]: v })} />
            <TextField label="Expiry date" type="date" value={expiry[l.id] ?? ''} onChange={(v) => setExpiry({ ...expiry, [l.id]: v })} />
          </div>
        </div>
      ))}
      <TextField label="Notes" value={notes} onChange={setNotes} />
      <ErrorText error={error} />
      <button type="submit" className="primary" disabled={busy}>{busy ? trLine('Saving…') : trLine('Save')}</button>
      <button type="button" onClick={onCancel}>{trLine('Cancel')}</button>
    </form>
  )
}
