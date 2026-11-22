import { useId, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Tr } from '../i18n/Tr'
import { useLanguage } from '../i18n/lang'

export function Loading() { return <p className="muted"><Tr k="Loading…" /></p> }

export function Empty({ text }: { text: string }) { return <p className="muted center-text"><Tr k={text} /></p> }

export function SearchBox({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const id = useId()
  useLanguage()
  return (
    <div className="field">
      <label htmlFor={id} className="sr-only"><Tr k="Search" /></label>
      <input id={id} type="search" value={value} placeholder="Search / खोजें" autoCapitalize="none" onChange={(e) => onChange(e.target.value)} />
    </div>
  )
}

/** A labelled text box. [label] is an English text, shown in both languages. */
export function TextField({ label, value, onChange, error, type = 'text', inputMode, multiline = false, required = false, readOnly = false }: {
  label: string; value: string; onChange: (v: string) => void; error?: string | null
  type?: string; inputMode?: 'decimal' | 'numeric' | 'tel' | 'text'; multiline?: boolean; required?: boolean; readOnly?: boolean
}) {
  const id = useId()
  useLanguage()
  return (
    <div className="field">
      <label htmlFor={id}><Tr k={label} inline />{required ? ' *' : ''}</label>
      {multiline
        ? <textarea id={id} rows={3} value={value} readOnly={readOnly} aria-invalid={!!error} onChange={(e) => onChange(e.target.value)} />
        : <input id={id} type={type} inputMode={inputMode} value={value} readOnly={readOnly} aria-invalid={!!error} onChange={(e) => onChange(e.target.value)} />}
      {error && <div className="error" role="alert">{error}</div>}
    </div>
  )
}

export function SelectField({ label, value, onChange, children, required = false }: {
  label: string; value: string; onChange: (v: string) => void; children: ReactNode; required?: boolean
}) {
  const id = useId()
  useLanguage()
  return (
    <div className="field">
      <label htmlFor={id}><Tr k={label} inline />{required ? ' *' : ''}</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>{children}</select>
    </div>
  )
}

export function CheckField({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  const id = useId()
  useLanguage()
  return (
    <div className="field check">
      <input id={id} type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <label htmlFor={id}><Tr k={label} inline /></label>
    </div>
  )
}

export function AddLink({ to, label }: { to: string; label: string }) {
  return <Link className="button primary" to={to}><Tr k={label} /></Link>
}
