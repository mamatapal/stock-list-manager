import { useState } from 'react'
import { batches } from '../api/catalogue'
import { ErrorText } from '../components/ErrorText'
import { Name } from '../components/Name'
import { Empty, Loading, SearchBox, SelectField } from '../components/ui'
import { useDebounced, useLoad } from '../components/useLoad'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

/** The same wording as the Android app, in both languages. */
function ExpiryLabel({ days }: { days: number | null }) {
  if (days === null) return <Tr k="No expiry date" inline />
  if (days < 0) return days === -1 ? <Tr k="Expired 1 day ago" inline /> : <Tr k="Expired %1$s days ago" args={[-days]} inline />
  if (days === 0) return <Tr k="Expires today" inline />
  return days === 1 ? <Tr k="Expires in 1 day" inline /> : <Tr k="Expires in %1$s days" args={[days]} inline />
}

const FILTERS: Array<[string, string]> = [
  ['ALL', 'All with an expiry date'], ['EXPIRED', 'Expired'], ['WITHIN_7', 'Within 7 days'], ['WITHIN_30', 'Within 30 days'], ['WITHIN_90', 'Within 90 days'],
]

export function ExpiryPage() {
  useLanguage()
  const [filter, setFilter] = useState('WITHIN_30')
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const alert = useLoad(() => batches.alert(), [])
  const { data, error, loading } = useLoad(() => batches.list(filter, dq), [filter, dq])
  return (
    <>
      <h1><Tr k="Expiry" /></h1>
      {alert.data && (
        <section className="card grid3">
          <div><p className="big">{alert.data.expired}</p><p className="muted small"><Tr k="Expired" /></p></div>
          <div><p className="big">{alert.data.within7}</p><p className="muted small"><Tr k="Within 7 days" /></p></div>
          <div><p className="big">{alert.data.within30}</p><p className="muted small"><Tr k="Within 30 days" /></p></div>
        </section>
      )}
      <SearchBox value={q} onChange={setQ} />
      <SelectField label="Show" value={filter} onChange={setFilter}>
        {FILTERS.map(([k, v]) => <option key={k} value={k}>{trLine(v)}</option>)}
      </SelectField>
      <ErrorText error={error} />
      {loading && !data ? <Loading /> : data && data.length === 0 ? <Empty text="Nothing to show" /> : (
        <ul className="rows">
          {data?.map((b) => (
            <li key={b.id} className="row static">
              <span className="row-main"><Name value={b.itemName} /></span>
              <span className="row-sub">{b.quantity} {b.unitCode}{b.batchNo ? ` · ${b.batchNo}` : ''}{b.supplierName ? ` · ${b.supplierName}` : ''}</span>
              <span className={b.daysToExpiry !== null && b.daysToExpiry < 0 ? 'tag bad' : 'tag'}>{b.expiryDate} · <ExpiryLabel days={b.daysToExpiry} /></span>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
