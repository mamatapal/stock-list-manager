import { useId, useState } from 'react'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

/** A password box with a small eye button inside it: tap to show or hide what was typed. [label] is an English text, shown in both languages. */
export function PasswordField({ label, value, onChange, autoComplete }: {
  label: string; value: string; onChange: (v: string) => void; autoComplete: string
}) {
  const id = useId()
  const [shown, setShown] = useState(false)
  useLanguage()
  return (
    <div className="field">
      <label htmlFor={id}><Tr k={label} /></label>
      <div className="pw-wrap">
        <input id={id} type={shown ? 'text' : 'password'} value={value} autoComplete={autoComplete}
          autoCapitalize="none" autoCorrect="off" spellCheck={false} onChange={(e) => onChange(e.target.value)} />
        <button type="button" className="pw-eye" aria-pressed={shown} aria-label={trLine(shown ? 'Hide password' : 'Show password')} onClick={() => setShown(!shown)}>
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z" />
            <circle cx="12" cy="12" r="3" />
            {shown && <path d="M3 3l18 18" />}
          </svg>
        </button>
      </div>
    </div>
  )
}
