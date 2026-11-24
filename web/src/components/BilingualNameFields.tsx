import { useId, useRef } from 'react'
import { combine, split } from '../i18n/bilingualName'
import { Tr } from '../i18n/Tr'
import { toEnglish, toHindi } from '../i18n/transliterate'
import { useLanguage } from '../i18n/lang'

/**
 * A name in English and in Hindi, in two boxes. Typing English fills the Hindi box with the same name written by
 * sound (Sugar → शुगर), and typing Hindi fills the English box. Either box can be corrected by hand; a box that was
 * changed by hand is not overwritten again. [value] is the saved form, "English / हिन्दी".
 */
export function BilingualNameFields({ value, onChange, title, required = false, error, multiline = false, englishLabel = 'English name', hindiLabel = 'Hindi name' }: {
  value: string
  onChange: (v: string) => void
  title?: string
  required?: boolean
  error?: string | null
  multiline?: boolean
  englishLabel?: string
  hindiLabel?: string
}) {
  useLanguage()
  const idEn = useId()
  const idHi = useId()
  const [english, hindi] = split(value)
  const lastTyped = useRef<0 | 1 | 2>(0)
  const Box = multiline ? 'textarea' : 'input'
  const boxProps = multiline ? { rows: 2 } : {}
  return (
    <fieldset className="field names">
      {title && <legend><Tr k={title} inline />{required ? ' *' : ''}</legend>}
      <label htmlFor={idEn}><Tr k={englishLabel} inline /></label>
      <Box id={idEn} value={english} autoCapitalize={multiline ? 'sentences' : 'words'} aria-invalid={!!error} {...boxProps}
        onChange={(e) => {
          const typed = e.target.value
          lastTyped.current = 1
          const hindiFollows = !hindi.trim() || hindi === toHindi(english)
          onChange(combine(typed, hindiFollows ? toHindi(typed) : hindi))
        }} />
      <label htmlFor={idHi}><Tr k={hindiLabel} inline /></label>
      <Box id={idHi} value={hindi} lang="hi" autoCapitalize="none" {...boxProps}
        onChange={(e) => {
          const typed = e.target.value
          const englishFollows = !english.trim() || (lastTyped.current === 2 && english === toEnglish(hindi))
          lastTyped.current = 2
          onChange(combine(englishFollows ? toEnglish(typed) : english, typed))
        }} />
      {error && <div className="error" role="alert">{error}</div>}
    </fieldset>
  )
}
