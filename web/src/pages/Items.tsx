import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { items, suppliers, units } from '../api/catalogue'
import type { Item, Supplier, Unit } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BilingualNameFields } from '../components/BilingualNameFields'
import { DeleteButton } from '../components/DeleteButton'
import { ErrorText, fieldMessage, hasFieldErrors } from '../components/ErrorText'
import { Name, oneLine } from '../components/Name'
import { AddLink, CheckField, Empty, Loading, SearchBox, SelectField, TextField } from '../components/ui'
import { useDebounced, useLoad } from '../components/useLoad'
import { clean } from '../i18n/bilingualName'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

export const unitLabel = (u: Unit | undefined) => (u ? u.code : '')

export function ItemList() {
  const { can } = useAuth()
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const dq = useDebounced(q)
  const cats = useLoad(() => items.categories(), [])
  const { data, error, loading } = useLoad(() => items.list(dq, category), [dq, category])
  const unitList = useLoad(() => units.list(), [])
  const unitById = useMemo(() => new Map((unitList.data ?? []).map((u) => [u.id, u])), [unitList.data])
  return (
    <>
      <h1><Tr k="Items" /></h1>
      {can('ITEM_ADD') && <AddLink to="/items/new" label="Add item" />}
      <Link className="button" to="/units"><Tr k="Units" /></Link>
      <SearchBox value={q} onChange={setQ} />
      {cats.data && cats.data.length > 0 && (
        <SelectField label="Category" value={category} onChange={setCategory}>
          <option value="">{trLine('All categories')}</option>
          {cats.data.map((c) => <option key={c} value={c}>{oneLine(c)}</option>)}
        </SelectField>
      )}
      <ErrorText error={error} />
      {loading && !data ? <Loading /> : data && data.length === 0 ? <Empty text="No items yet" /> : (
        <ul className="rows">
          {data?.map((i) => (
            <li key={i.id}>
              <Link to={`/items/${i.id}`} className="row">
                <span className="row-main"><Name value={i.name} /></span>
                <span className="row-sub">
                  {i.category ? <Name value={i.category} inline /> : null}
                  {i.latestSellingPrice ? ` · ₹${i.latestSellingPrice} / ${unitLabel(unitById.get(i.defaultUnitId))}` : ''}
                </span>
                {!i.active && <span className="tag"><Tr k="Inactive" inline /></span>}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

const blank = { name: '', category: '', defaultUnitId: '', purchase: '', selling: '', preferredSupplierId: '', notes: '', active: true }

export function ItemForm() {
  const { id } = useParams()
  const item = useLoad<Item | null>(() => (id ? items.get(id) : Promise.resolve(null)), [id])
  const unitList = useLoad(() => units.list(), [])
  const supplierList = useLoad<Supplier[]>(() => suppliers.list().catch(() => []), [])
  if (item.loading || unitList.loading) return <Loading />
  if (item.error || unitList.error) return <ErrorText error={item.error ?? unitList.error} />
  return <ItemFormBody key={id ?? 'new'} existing={item.data} units={unitList.data ?? []} suppliers={supplierList.data ?? []} />
}

function ItemFormBody({ existing, units: unitList, suppliers: supplierList }: { existing: Item | null; units: Unit[]; suppliers: Supplier[] }) {
  const { can } = useAuth()
  const navigate = useNavigate()
  useLanguage()
  const usable = unitList.filter((u) => !u.deleted)
  const [f, setF] = useState(() => existing ? {
    name: existing.name, category: existing.category ?? '', defaultUnitId: existing.defaultUnitId,
    purchase: existing.latestPurchasePrice ?? '', selling: existing.latestSellingPrice ?? '',
    preferredSupplierId: existing.preferredSupplierId ?? '', notes: existing.notes ?? '', active: existing.active,
  } : { ...blank, defaultUnitId: usable[0]?.id ?? '' })
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const set = <K extends keyof typeof blank>(k: K, v: (typeof blank)[K]) => setF((p) => ({ ...p, [k]: v }))
  const canSave = existing ? can('ITEM_EDIT') : can('ITEM_ADD')
  // the server refuses a price the user may not enter, so those boxes are shown read-only
  const canPurchase = can('PURCHASE_PRICE_ENTER')
  const canSelling = existing ? can('CATALOG_SELLING_PRICE_CHANGE') : can('SELLING_PRICE_ENTER')

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!clean(f.name)) { setError('Item name is required'); return }
    if (!f.defaultUnitId) { setError('Default unit is required'); return }
    setBusy(true); setError(null)
    const body = {
      name: clean(f.name), category: clean(f.category) || null, defaultUnitId: f.defaultUnitId,
      latestPurchasePrice: f.purchase.trim() || null, latestSellingPrice: f.selling.trim() || null,
      preferredSupplierId: f.preferredSupplierId || null, notes: clean(f.notes), active: f.active,
    }
    try {
      if (existing) await items.update(existing.id, existing.version, body); else await items.create(body)
      navigate('/items', { replace: true })
    } catch (err) { setError(err); setBusy(false) }
  }

  async function remove() {
    if (!existing) return
    setBusy(true)
    try { await items.remove(existing.id, existing.version); navigate('/items', { replace: true }) } catch (err) { setError(err); setBusy(false) }
  }

  return (
    <form onSubmit={save} noValidate>
      <h1><Tr k={existing ? 'Edit item' : 'Add item'} /></h1>
      <BilingualNameFields value={f.name} onChange={(v) => set('name', v)} title="Item name" required error={fieldMessage(error, 'name')} />
      <BilingualNameFields value={f.category} onChange={(v) => set('category', v)} title="Category" englishLabel="Category (English)" hindiLabel="Category (Hindi)" />
      <SelectField label="Default unit" required value={f.defaultUnitId} onChange={(v) => set('defaultUnitId', v)}>
        {usable.map((u) => <option key={u.id} value={u.id}>{u.code} — {oneLine(u.name)}</option>)}
      </SelectField>
      <TextField label="Purchase price" inputMode="decimal" value={f.purchase} onChange={(v) => set('purchase', v)} readOnly={!canPurchase} error={fieldMessage(error, 'latestPurchasePrice')} />
      <TextField label="Selling price" inputMode="decimal" value={f.selling} onChange={(v) => set('selling', v)} readOnly={!canSelling} error={fieldMessage(error, 'latestSellingPrice')} />
      <SelectField label="Preferred supplier" value={f.preferredSupplierId} onChange={(v) => set('preferredSupplierId', v)}>
        <option value="">{trLine('None')}</option>
        {supplierList.filter((s) => s.active || s.id === f.preferredSupplierId).map((s) => <option key={s.id} value={s.id}>{oneLine(s.name)}</option>)}
      </SelectField>
      <BilingualNameFields value={f.notes} onChange={(v) => set('notes', v)} title="Notes" multiline englishLabel="Notes (English)" hindiLabel="Notes (Hindi)" />
      <CheckField label="Active" checked={f.active} onChange={(v) => set('active', v)} />
      <ErrorText error={hasFieldErrors(error) ? null : error} />
      {canSave && <button type="submit" className="primary" disabled={busy}>{busy ? trLine('Saving…') : trLine('Save')}</button>}
      <Link className="button" to="/items">{trLine('Cancel')}</Link>
      {existing && can('ITEM_DELETE') && <DeleteButton onConfirm={remove} busy={busy} />}
    </form>
  )
}
