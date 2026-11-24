import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { suppliers } from '../api/catalogue'
import type { Supplier } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BilingualNameFields } from '../components/BilingualNameFields'
import { DeleteButton } from '../components/DeleteButton'
import { ErrorText, fieldMessage, hasFieldErrors } from '../components/ErrorText'
import { Name } from '../components/Name'
import { AddLink, CheckField, Empty, Loading, SearchBox, TextField } from '../components/ui'
import { useDebounced, useLoad } from '../components/useLoad'
import { clean } from '../i18n/bilingualName'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

export function SupplierList() {
  const { can } = useAuth()
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const { data, error, loading } = useLoad(() => suppliers.list(dq), [dq])
  return (
    <>
      <h1><Tr k="Suppliers" /></h1>
      {can('SUPPLIER_ADD') && <AddLink to="/suppliers/new" label="Add supplier" />}
      <SearchBox value={q} onChange={setQ} />
      <ErrorText error={error} />
      {loading && !data ? <Loading /> : data && data.length === 0 ? <Empty text="No suppliers yet" /> : (
        <ul className="rows">
          {data?.map((s) => (
            <li key={s.id}>
              <Link to={`/suppliers/${s.id}`} className="row">
                <span className="row-main"><Name value={s.name} /></span>
                <span className="row-sub">{[s.contactPerson, s.mobile, s.city].filter(Boolean).join(' · ')}</span>
                {!s.active && <span className="tag"><Tr k="Inactive" inline /></span>}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

const blank = { name: '', contactPerson: '', mobile: '', altPhone: '', address: '', city: '', gstin: '', notes: '', active: true }

export function SupplierForm() {
  const { id } = useParams()
  const isNew = !id
  const { data, error: loadError, loading } = useLoad<Supplier | null>(() => (id ? suppliers.get(id) : Promise.resolve(null)), [id])
  if (!isNew && loading) return <Loading />
  if (!isNew && loadError) return <ErrorText error={loadError} />
  return <SupplierFormBody key={id ?? 'new'} existing={data} />
}

function SupplierFormBody({ existing }: { existing: Supplier | null }) {
  const { can } = useAuth()
  const navigate = useNavigate()
  useLanguage()
  const [f, setF] = useState(() => existing ? {
    name: existing.name, contactPerson: existing.contactPerson ?? '', mobile: existing.mobile ?? '', altPhone: existing.altPhone ?? '',
    address: existing.address ?? '', city: existing.city ?? '', gstin: existing.gstin ?? '', notes: existing.notes ?? '', active: existing.active,
  } : blank)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const set = <K extends keyof typeof blank>(k: K, v: (typeof blank)[K]) => setF((p) => ({ ...p, [k]: v }))
  const canSave = existing ? can('SUPPLIER_EDIT') : can('SUPPLIER_ADD')

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!clean(f.name)) { setError('Supplier name is required'); return }
    setBusy(true); setError(null)
    const body = { ...f, name: clean(f.name), address: clean(f.address), notes: clean(f.notes), gstin: f.gstin.trim().toUpperCase() }
    try {
      if (existing) await suppliers.update(existing.id, existing.version, body); else await suppliers.create(body)
      navigate('/suppliers', { replace: true })
    } catch (err) { setError(err) } finally { setBusy(false) }
  }

  async function remove() {
    if (!existing) return
    setBusy(true)
    try { await suppliers.remove(existing.id, existing.version); navigate('/suppliers', { replace: true }) } catch (err) { setError(err); setBusy(false) }
  }

  return (
    <form onSubmit={save} noValidate>
      <h1><Tr k={existing ? 'Edit supplier' : 'Add supplier'} /></h1>
      <BilingualNameFields value={f.name} onChange={(v) => set('name', v)} title="Supplier name" required error={fieldMessage(error, 'name')} />
      <TextField label="Contact person" value={f.contactPerson} onChange={(v) => set('contactPerson', v)} />
      <TextField label="Mobile" type="tel" inputMode="tel" value={f.mobile} onChange={(v) => set('mobile', v)} error={fieldMessage(error, 'mobile')} />
      <TextField label="Alternate phone" type="tel" inputMode="tel" value={f.altPhone} onChange={(v) => set('altPhone', v)} error={fieldMessage(error, 'altPhone')} />
      <BilingualNameFields value={f.address} onChange={(v) => set('address', v)} title="Address" multiline englishLabel="Address (English)" hindiLabel="Address (Hindi)" />
      <TextField label="City" value={f.city} onChange={(v) => set('city', v)} />
      <TextField label="GSTIN" value={f.gstin} onChange={(v) => set('gstin', v)} error={fieldMessage(error, 'gstin')} />
      <BilingualNameFields value={f.notes} onChange={(v) => set('notes', v)} title="Notes" multiline englishLabel="Notes (English)" hindiLabel="Notes (Hindi)" />
      <CheckField label="Active" checked={f.active} onChange={(v) => set('active', v)} />
      <ErrorText error={hasFieldErrors(error) ? null : error} />
      {canSave && <button type="submit" className="primary" disabled={busy}>{busy ? trLine('Saving…') : trLine('Save')}</button>}
      <Link className="button" to="/suppliers">{trLine('Cancel')}</Link>
      {existing && can('SUPPLIER_DELETE') && <DeleteButton onConfirm={remove} busy={busy} />}
    </form>
  )
}
