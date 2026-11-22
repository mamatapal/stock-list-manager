import { useId, useState } from 'react'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

/** A password box with a Show / Hide button. [label] is an English text, shown in both languages. */
export function PasswordField({ label, value, onChange, autoComplete }: {
  label: string; value: string; onChange: (v: string) => void; autoComplete: string
}) {
  const id = useId()
  const [shown, setShown] = useState(false)
  useLanguage()
  return (
    <div className="field">
      <label htmlFor={id}><Tr k={label} /></label>
      <div className="field-row">
        <input id={id} type={shown ? 'text' : 'password'} value={value} autoComplete={autoComplete}
          autoCapitalize="none" autoCorrect="off" spellCheck={false} onChange={(e) => onChange(e.target.value)} />
        <button type="button" className="ghost" aria-pressed={shown} onClick={() => setShown(!shown)}>
          {trLine(shown ? 'Hide password' : 'Show password')}
        </button>
      </div>
    </div>
  )
}
