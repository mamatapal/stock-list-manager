import { Children, Fragment, isValidElement, useId, useState, type ReactElement, type ReactNode } from 'react'
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

type Opt = { value: string; label: string }
function readOptions(children: ReactNode, out: Opt[] = []): Opt[] {
  Children.forEach(children, (c) => {
    if (!isValidElement(c)) return
    const el = c as ReactElement<{ value?: string; children?: ReactNode }>
    if (el.type === Fragment) readOptions(el.props.children, out)
    else out.push({ value: String(el.props.value ?? ''), label: Children.toArray(el.props.children).join('') })
  })
  return out
}

/**
 * A drop-down that does not use the phone's own pick list (which draws radio circles): tap the field, tap a row, the
 * chosen row is highlighted. Takes <option> children like a normal select.
 */
export function SelectField({ label, value, onChange, children, required = false }: {
  label: string; value: string; onChange: (v: string) => void; children: ReactNode; required?: boolean
}) {
  const id = useId()
  useLanguage()
  const [open, setOpen] = useState(false)
  const opts = readOptions(children)
  const current = opts.find((o) => o.value === value)
  return (
    <div className="field combo">
      <label htmlFor={id}><Tr k={label} inline />{required ? ' *' : ''}</label>
      <button id={id} type="button" className="select-btn" aria-haspopup="listbox" aria-expanded={open}
        onClick={() => setOpen((o) => !o)} onBlur={() => setOpen(false)}>
        <span>{current?.label ?? ''}</span><span aria-hidden="true"> ▾</span>
      </button>
      {open && (
        <ul role="listbox" className="combo-list">
          {opts.map((o) => (
            <li key={o.value} role="presentation">
              <button type="button" role="option" aria-selected={o.value === value} className={o.value === value ? 'pick-row selected' : 'pick-row'}
                onMouseDown={(e) => e.preventDefault()} onClick={() => { onChange(o.value); setOpen(false) }}>{o.label || '—'}</button>
            </li>
          ))}
        </ul>
      )}
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
