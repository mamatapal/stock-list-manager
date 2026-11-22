import { getLanguage, setLanguage, useLanguage, type Language } from '../i18n/lang'

const OPTIONS: Array<{ code: Language; name: string }> = [
  { code: 'en', name: 'English' },
  { code: 'hi', name: 'हिन्दी' },
]

/** English | हिन्दी: the language shown first. */
export function LanguageSwitch({ compact = false }: { compact?: boolean }) {
  const current = useLanguage()
  return (
    <div className={compact ? 'segmented compact' : 'segmented'} role="group" aria-label="Language / भाषा">
      {OPTIONS.map((o) => (
        <button key={o.code} type="button" className={current === o.code ? 'seg on' : 'seg'} aria-pressed={current === o.code}
          onClick={() => { if (getLanguage() !== o.code) setLanguage(o.code) }}>
          {o.name}
        </button>
      ))}
    </div>
  )
}
