import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { items, stockLists, suppliers, units } from '../api/catalogue'
import type { Item, ListLine, StockList, Supplier, Unit } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BilingualNameFields } from '../components/BilingualNameFields'
import { DeleteButton } from '../components/DeleteButton'
import { ErrorText } from '../components/ErrorText'
import { Name, oneLine } from '../components/Name'
import { AddLink, Empty, Loading, SearchBox, SelectField, TextField } from '../components/ui'
import { useDebounced, useLoad } from '../components/useLoad'
import { clean } from '../i18n/bilingualName'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const newId = () => crypto.randomUUID()

export function ListList() {
  const { can } = useAuth()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const dq = useDebounced(q)
  const { data, error, loading } = useLoad(() => stockLists.list(dq, status), [dq, status])
  const supplierList = useLoad<Supplier[]>(() => suppliers.list().catch(() => []), [])
  const supplierById = useMemo(() => new Map((supplierList.data ?? []).map((s) => [s.id, s])), [supplierList.data])
  useLanguage()
  return (
    <>
      <h1><Tr k="Out-of-stock lists" /></h1>
      {can('LIST_CREATE') && <AddLink to="/lists/new" label="New list" />}
      <SearchBox value={q} onChange={setQ} />
      <SelectField label="Status" value={status} onChange={setStatus}>
        <option value="">{trLine('All')}</option>
        <option value="DRAFT">{trLine('Draft')}</option>
        <option value="SAVED">{trLine('Saved')}</option>
      </SelectField>
      <ErrorText error={error} />
      {loading && !data ? <Loading /> : data && data.length === 0 ? <Empty text="No lists yet" /> : (
        <ul className="rows">
          {data?.map((l) => (
            <li key={l.id}>
              <Link to={`/lists/${l.id}`} className="row">
                <span className="row-main"><Name value={l.title} /></span>
                <span className="row-sub">
                  {l.listDate}{l.supplierId && supplierById.get(l.supplierId) ? ` · ${oneLine(supplierById.get(l.supplierId)!.name)}` : ''}
                  {l.totalAmount ? ` · ₹${l.totalAmount}` : ''} · {l.lines.length} <Tr k="items" inline />
                </span>
                <span className="tag"><Tr k={l.status === 'DRAFT' ? 'Draft' : 'Saved'} inline /></span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

interface LineForm { id: string; itemId: string; itemName: string; description: string; quantity: string; unitId: string; priceBasis: 'PER_UNIT' | 'TOTAL'; enteredPrice: string; progress?: ListLine }

const fromLine = (l: ListLine): LineForm => ({
  id: l.id, itemId: l.itemId ?? '', itemName: l.itemName ?? '', description: l.description ?? '', quantity: l.quantity, unitId: l.unitId,
  priceBasis: l.priceBasis, enteredPrice: l.enteredPrice ?? '', progress: l,
})

export function ListForm() {
  const { id } = useParams()
  const list = useLoad<StockList | null>(() => (id ? stockLists.get(id) : Promise.resolve(null)), [id])
  const itemList = useLoad<Item[]>(() => items.list().catch(() => []), [])
  const unitList = useLoad<Unit[]>(() => units.list(), [])
  const supplierList = useLoad<Supplier[]>(() => suppliers.list().catch(() => []), [])
  if (list.loading || unitList.loading || itemList.loading || supplierList.loading) return <Loading />
  if (list.error || unitList.error) return <ErrorText error={list.error ?? unitList.error} />
  return <ListFormBody key={id ?? 'new'} existing={list.data} items={itemList.data ?? []} units={unitList.data ?? []} suppliers={supplierList.data ?? []} />
}

function ListFormBody({ existing, items: itemList, units: unitList, suppliers: supplierList }: { existing: StockList | null; items: Item[]; units: Unit[]; suppliers: Supplier[] }) {
  const { can } = useAuth()
  const navigate = useNavigate()
  useLanguage()
  const usableUnits = unitList.filter((u) => !u.deleted)
  const [title, setTitle] = useState(existing?.title ?? '')
  const [listDate, setListDate] = useState(existing?.listDate ?? today())
  const [supplierId, setSupplierId] = useState(existing?.supplierId ?? '')
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [status, setStatus] = useState<'DRAFT' | 'SAVED'>(existing?.status ?? 'DRAFT')
  const [lines, setLines] = useState<LineForm[]>(() => existing ? existing.lines.map(fromLine) : [])
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const canSave = existing ? can('LIST_EDIT') : can('LIST_CREATE')
  const unitById = new Map(unitList.map((u) => [u.id, u]))

  const patch = (id: string, p: Partial<LineForm>) => setLines((ls) => ls.map((l) => (l.id === id ? { ...l, ...p } : l)))
  const addLine = () => setLines((ls) => [...ls, { id: newId(), itemId: '', itemName: '', description: '', quantity: '', unitId: usableUnits[0]?.id ?? '', priceBasis: 'PER_UNIT', enteredPrice: '' }])
  const pickItem = (lineId: string, itemId: string) => {
    const item = itemList.find((i) => i.id === itemId)
    patch(lineId, item ? { itemId, itemName: item.name, unitId: item.defaultUnitId } : { itemId: '' })
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!clean(title)) { setError('List title is required'); return }
    if (lines.some((l) => !l.quantity.trim())) { setError('Quantity is required'); return }
    if (lines.some((l) => !l.itemId && !clean(l.itemName))) { setError('Choose an item or type a name for every line'); return }
    setBusy(true); setError(null)
    const body = {
      title: clean(title), listDate, supplierId: supplierId || null, notes: clean(notes), status,
      lines: lines.map((l) => ({
        id: l.id, itemId: l.itemId || null, itemName: clean(l.itemName) || null, description: l.description.trim() || null,
        quantity: l.quantity.trim(), unitId: l.unitId, priceBasis: l.priceBasis, enteredPrice: l.enteredPrice.trim() || null,
      })),
    }
    try {
      if (existing) await stockLists.update(existing.id, existing.version, body); else await stockLists.create(body)
      navigate('/lists', { replace: true })
    } catch (err) { setError(err); setBusy(false) }
  }

  async function remove() {
    if (!existing) return
    setBusy(true)
    try { await stockLists.remove(existing.id, existing.version); navigate('/lists', { replace: true }) } catch (err) { setError(err); setBusy(false) }
  }

  async function duplicate() {
    if (!existing) return
    setBusy(true)
    try { const copy = await stockLists.duplicate(existing.id); navigate(`/lists/${copy.id}`, { replace: true }) } catch (err) { setError(err); setBusy(false) }
  }

  return (
    <form onSubmit={save} noValidate>
      <h1><Tr k={existing ? 'Edit list' : 'New list'} /></h1>
      {existing && <p><Link className="button" to={`/pdf/list/${existing.id}`}><Tr k="Share PDF" inline /></Link></p>}
      <BilingualNameFields value={title} onChange={setTitle} title="List title" required />
      <TextField label="Date" type="date" value={listDate} onChange={setListDate} required />
      <SelectField label="Supplier" value={supplierId} onChange={setSupplierId}>
        <option value="">{trLine('None')}</option>
        {supplierList.filter((s) => s.active || s.id === supplierId).map((s) => <option key={s.id} value={s.id}>{oneLine(s.name)}</option>)}
      </SelectField>
      <BilingualNameFields value={notes} onChange={setNotes} title="Notes" multiline englishLabel="Notes (English)" hindiLabel="Notes (Hindi)" />
      <SelectField label="Status" value={status} onChange={(v) => setStatus(v as 'DRAFT' | 'SAVED')}>
        <option value="DRAFT">{trLine('Draft')}</option>
        <option value="SAVED">{trLine('Saved')}</option>
      </SelectField>

      <h2><Tr k="Items in this list" /></h2>
      {lines.length === 0 && <Empty text="No items in this list yet" />}
      {lines.map((l, n) => (
        <section key={l.id} className="card line" aria-label={`${n + 1}`}>
          <SelectField label="Item" value={l.itemId} onChange={(v) => pickItem(l.id, v)}>
            <option value="">{trLine('Other (type a name)')}</option>
            {itemList.filter((i) => i.active || i.id === l.itemId).map((i) => <option key={i.id} value={i.id}>{oneLine(i.name)}</option>)}
          </SelectField>
          {!l.itemId && <BilingualNameFields value={l.itemName} onChange={(v) => patch(l.id, { itemName: v })} title="Item name" />}
          <div className="grid2">
            <TextField label="Quantity" inputMode="decimal" value={l.quantity} onChange={(v) => patch(l.id, { quantity: v })} required />
            <SelectField label="Unit" value={l.unitId} onChange={(v) => patch(l.id, { unitId: v })}>
              {usableUnits.map((u) => <option key={u.id} value={u.id}>{u.code}</option>)}
            </SelectField>
          </div>
          <div className="grid2">
            <SelectField label="Price is" value={l.priceBasis} onChange={(v) => patch(l.id, { priceBasis: v as 'PER_UNIT' | 'TOTAL' })}>
              <option value="PER_UNIT">{trLine('Per unit')}</option>
              <option value="TOTAL">{trLine('For the whole line')}</option>
            </SelectField>
            <TextField label="Price" inputMode="decimal" value={l.enteredPrice} onChange={(v) => patch(l.id, { enteredPrice: v })} />
          </div>
          <TextField label="Note" value={l.description} onChange={(v) => patch(l.id, { description: v })} />
          {l.progress && (
            <p className="muted small">
              <Tr k="Received" inline />: {l.progress.receivedQuantity ?? '0'} {unitById.get(l.unitId)?.code} · <Tr k="Remaining" inline />: {l.progress.remainingQuantity ?? l.quantity}
            </p>
          )}
          <button type="button" className="danger" onClick={() => setLines((ls) => ls.filter((x) => x.id !== l.id))}>{trLine('Remove this line')}</button>
        </section>
      ))}
      <button type="button" onClick={addLine}>{trLine('Add item to list')}</button>
      {existing?.totalAmount && <p className="big"><Tr k="Total" inline />: ₹{existing.totalAmount}</p>}

      <ErrorText error={error} />
      {canSave && <button type="submit" className="primary" disabled={busy}>{busy ? trLine('Saving…') : trLine('Save')}</button>}
      <Link className="button" to="/lists">{trLine('Cancel')}</Link>
      {existing && can('LIST_CREATE') && <button type="button" disabled={busy} onClick={duplicate}>{trLine('Make a copy')}</button>}
      {existing && can('LIST_DELETE') && <DeleteButton onConfirm={remove} busy={busy} />}
    </form>
  )
}
