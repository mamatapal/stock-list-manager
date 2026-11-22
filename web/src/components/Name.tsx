import { Pair } from '../i18n/Tr'
import { show, oneLine } from '../i18n/bilingualName'
import { useLanguage } from '../i18n/lang'

/** A saved name (or note, or address) in both languages, main language first. */
export function Name({ value, inline = false }: { value: string | null | undefined; inline?: boolean }) {
  useLanguage()
  const [main, other] = show(value)
  if (!main) return null
  return <Pair main={main} other={other} inline={inline} />
}

export { oneLine }
