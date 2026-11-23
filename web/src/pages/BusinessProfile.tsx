import { useState, type FormEvent } from 'react'
import { business, type Business } from '../api/owner'
import { useAuth } from '../auth/AuthContext'
import { ErrorText } from '../components/ErrorText'
import { StepUpCancelled, useStepUp } from '../components/StepUp'
import { Loading, TextField } from '../components/ui'
import { useLoad } from '../components/useLoad'
import { show } from '../i18n/bilingualName'
import { dateTime } from '../pdf/format'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

const PIN = /^[1-9]\d{5}$/
const MOBILE = /^[6-9]\d{9}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const GSTIN = /^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/

const text = (b: Business, f: keyof Business) => String(b[f] ?? '')

export function BusinessProfilePage() {
  useLanguage()
  const { user, can } = useAuth()
  const run = useStepUp()
  const { data, error, loading, reload } = useLoad(() => business.get(), [])
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<Record<string, string>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saveError, setSaveError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const canEdit = user?.role === 'OWNER' || can('SETTINGS_MANAGE')
  const set = (k: string) => (v: string) => setForm((f) => ({ ...f, [k]: v }))

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorText error={error} />

  function start() {
    setForm({ name: data!.name, address: data!.address, city: data!.city, state: data!.state, pinCode: data!.pinCode, phone: data!.phone, email: data!.email, gstin: data!.gstin ?? '' })
    setErrors({}); setSaveError(null); setSaved(false); setEditing(true)
  }
  async function save(e: FormEvent) {
    e.preventDefault()
    const bad: Record<string, string> = {}
    const req = (k: string, label: string) => { if (!form[k]?.trim()) bad[k] = trLine('%1$s is required', trLine(label)) }
    req('name', 'Business name'); req('address', 'Address'); req('city', 'City'); req('state', 'State')
    if (!form.pinCode?.trim()) bad.pinCode = trLine('PIN code is required'); else if (!PIN.test(form.pinCode.trim())) bad.pinCode = trLine('Enter a 6-digit PIN code')
    if (!form.phone?.trim()) bad.phone = trLine('Mobile number is required'); else if (!MOBILE.test(form.phone.trim())) bad.phone = trLine('Enter a 10-digit mobile number')
    if (!form.email?.trim()) bad.email = trLine('Email is required'); else if (!EMAIL.test(form.email.trim())) bad.email = trLine('Enter a valid email address')
    if (form.gstin?.trim() && !GSTIN.test(form.gstin.trim().toUpperCase())) bad.gstin = trLine('Enter a valid 15-character GSTIN')
    setErrors(bad)
    if (Object.keys(bad).length) return
    setBusy(true); setSaveError(null)
    try {
      await run(() => business.update({ name: form.name.trim(), address: form.address.trim(), city: form.city.trim(), state: form.state.trim(), pinCode: form.pinCode.trim(),
        phone: form.phone.trim(), email: form.email.trim(), gstin: form.gstin.trim() ? form.gstin.trim().toUpperCase() : null }))
      setEditing(false); setSaved(true); reload()
    } catch (err) {
      if (err instanceof StepUpCancelled) return
      setSaveError(err)
      const fields = (err as { fieldErrors?: Array<{ field: string; message: string }> }).fieldErrors ?? []
      if (fields.length) setErrors(Object.fromEntries(fields.map((f) => [f.field, f.message])))
    } finally { setBusy(false) }
  }

  if (editing) {
    return (
      <form onSubmit={(e) => { void save(e) }}>
        <h1><Tr k="Edit business" /></h1>
        <ErrorText error={saveError} />
        <TextField label="Business name" value={form.name} onChange={set('name')} error={errors.name} required />
        <TextField label="Address" value={form.address} onChange={set('address')} error={errors.address} multiline required />
        <TextField label="City" value={form.city} onChange={set('city')} error={errors.city} required />
        <TextField label="State" value={form.state} onChange={set('state')} error={errors.state} required />
        <TextField label="PIN code" inputMode="numeric" value={form.pinCode} onChange={(v) => set('pinCode')(v.replace(/\D/g, '').slice(0, 6))} error={errors.pinCode} required />
        <TextField label="Phone" type="tel" inputMode="tel" value={form.phone} onChange={(v) => set('phone')(v.replace(/\D/g, '').slice(0, 10))} error={errors.phone} required />
        <TextField label="Email" type="email" value={form.email} onChange={(v) => set('email')(v.trim())} error={errors.email} required />
        <TextField label="GST number (optional)" value={form.gstin} onChange={(v) => set('gstin')(v.toUpperCase().slice(0, 15))} error={errors.gstin} />
        <button type="submit" className="primary" disabled={busy}>{trLine(busy ? 'Saving...' : 'Save')}</button>
        <button type="button" disabled={busy} onClick={() => setEditing(false)}>{trLine('Cancel')}</button>
      </form>
    )
  }
  const [name, other] = show(data.name)
  return (
    <>
      <h1><Tr k="Business profile" /></h1>
      {saved && <p className="ok" role="status"><Tr k="Business details saved" /></p>}
      <section className="card">
        <h2>{name}{other ? <span className="other"> / {other}</span> : null}</h2>
        <dl>
          <dt><Tr k="Address" /></dt><dd>{text(data, 'address')}</dd>
          <dt><Tr k="City" /></dt><dd>{text(data, 'city')}</dd>
          <dt><Tr k="State" /></dt><dd>{text(data, 'state')}</dd>
          <dt><Tr k="PIN code" /></dt><dd>{text(data, 'pinCode')}</dd>
          <dt><Tr k="Phone" /></dt><dd>{text(data, 'phone')}</dd>
          <dt><Tr k="Email" /></dt><dd>{text(data, 'email')}</dd>
          <dt><Tr k="GST number" /></dt><dd>{text(data, 'gstin') || '-'}</dd>
          <dt><Tr k="Last updated" /></dt><dd>{data.updatedAt ? dateTime(new Date(data.updatedAt)) : '-'}</dd>
        </dl>
      </section>
      <p className="muted small"><Tr k="These details are printed on your PDFs." /></p>
      {canEdit && <button type="button" className="primary" onClick={start}>{trLine('Edit')}</button>}
    </>
  )
}
