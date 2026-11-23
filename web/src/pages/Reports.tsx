import { useState } from 'react'
import { Link } from 'react-router-dom'
import { items, suppliers } from '../api/catalogue'
import { useAuth } from '../auth/AuthContext'
import { ErrorText } from '../components/ErrorText'
import { Loading, SelectField, TextField } from '../components/ui'
import { useLoad } from '../components/useLoad'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

const pad = (n: number) => String(n).padStart(2, '0')
const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const monthStart = () => { const d = new Date(); return iso(new Date(d.getFullYear(), d.getMonth(), 1)) }

export function ReportsPage() {
  useLanguage()
  const { can } = useAuth()
  const [from, setFrom] = useState(monthStart())
  const [to, setTo] = useState(iso(new Date()))
  const [supplierId, setSupplierId] = useState('')
  const [itemId, setItemId] = useState('')
  const supplierList = useLoad(() => suppliers.list(), [])
  const itemList = useLoad(() => items.list(), [])
  if (supplierList.loading || itemList.loading) return <Loading />
  const err = supplierList.error ?? itemList.error
  if (err) return <ErrorText error={err} />
  const bad = !from || !to || from > to
  const range = `from=${from}&to=${to}`
  const link = (path: string, label: string) => (
    bad ? <span className="button disabled" aria-disabled="true"><Tr k={label} inline /></span>
      : <Link className="button" to={path}><Tr k={label} inline /></Link>)
  return (
    <>
      <h1><Tr k="Reports" /></h1>
      <p className="muted small"><Tr k="Reports need an internet connection. Connect and try again." /></p>
      <section className="card">
        <div className="grid2">
          <TextField label="From" type="date" value={from} onChange={setFrom} />
          <TextField label="To" type="date" value={to} onChange={setTo} />
        </div>
        {bad && <p className="error" role="alert"><Tr k="Choose a start date that is not after the end date" /></p>}
        <div className="stack">
          {link(`/pdf/report/purchases?${range}`, 'Purchase report')}
          {link(`/pdf/report/supplier-totals?${range}`, 'Supplier totals')}
          {link(`/pdf/report/item-prices?${range}`, 'Item purchase prices')}
          {can('EXPECTED_PROFIT_VIEW') && can('PROFIT_REPORT_VIEW') && link(`/pdf/report/expected-profit?${range}`, 'Expected profit report')}
        </div>
      </section>
      <section className="card">
        <SelectField label="Supplier" value={supplierId} onChange={setSupplierId}>
          <option value="">{trLine('Choose a supplier')}</option>
          {(supplierList.data ?? []).filter((s) => !s.deleted).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </SelectField>
        {supplierId && link(`/pdf/supplier/${supplierId}?${range}`, 'Supplier purchase report')}
      </section>
      <section className="card">
        <SelectField label="Item" value={itemId} onChange={setItemId}>
          <option value="">{trLine('Choose an item')}</option>
          {(itemList.data ?? []).filter((i) => !i.deleted).map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
        </SelectField>
        {itemId && link(`/pdf/item/${itemId}?${range}`, 'Item purchase report')}
      </section>
    </>
  )
}
