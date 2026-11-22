import { useSyncExternalStore } from 'react'
import hi from './hi.generated.json'
import extra from './hi.extra'

/**
 * English + Hindi, always both on screen. The "main" language (chosen by the user) comes first; the other is shown
 * smaller underneath. Texts are written in English and looked up by their exact English wording, like the Android app.
 */
export type Language = 'en' | 'hi'

const catalogue: Record<string, string> = { ...(hi as Record<string, string>), ...extra }
const KEY = 'slm.language'
const listeners = new Set<() => void>()

function initial(): Language {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'en' || saved === 'hi') return saved
  } catch { /* storage can be unavailable; fall through */ }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('hi') ? 'hi' : 'en'
}

let current: Language = initial()
if (typeof document !== 'undefined') document.documentElement.lang = current

export const getLanguage = (): Language => current
export const otherLanguage = (l: Language = current): Language => (l === 'hi' ? 'en' : 'hi')

export function setLanguage(l: Language): void {
  current = l
  try { localStorage.setItem(KEY, l) } catch { /* ignore */ }
  if (typeof document !== 'undefined') document.documentElement.lang = l
  listeners.forEach((f) => f())
}

/** Re-renders the component when the main language changes. */
export function useLanguage(): Language {
  return useSyncExternalStore((cb) => { listeners.add(cb); return () => { listeners.delete(cb) } }, getLanguage, getLanguage)
}

export const hasHindi = (english: string): boolean => english in catalogue

/** Replaces %1$s, %2$s … with the arguments. */
export function format(template: string, args: ReadonlyArray<string | number | null | undefined> = []): string {
  return template.replace(/%(\d+)\$s/g, (_, n: string) => String(args[Number(n) - 1] ?? ''))
}

export function translate(english: string, to: Language, args: ReadonlyArray<string | number | null | undefined> = []): string {
  return format(to === 'en' ? english : (catalogue[english] ?? english), args)
}

/** The text in the main language only (for attributes and anywhere two lines do not fit). */
export const tr = (english: string, ...args: Array<string | number | null | undefined>): string =>
  translate(english, current, args)

/** [main, other]; the same text twice is collapsed into one (for example "English" or a brand name). */
export function trBoth(english: string, ...args: Array<string | number | null | undefined>): [string, string | null] {
  const main = translate(english, current, args)
  const other = translate(english, otherLanguage(), args)
  return [main, other === main ? null : other]
}

/** "main / other" on one line, for placeholders, aria-labels and buttons. */
export function trLine(english: string, ...args: Array<string | number | null | undefined>): string {
  const [main, other] = trBoth(english, ...args)
  return other ? `${main} / ${other}` : main
}

/**
 * Text that came from the server (always English). Fixed server messages have a Hindi wording in the catalogue;
 * anything else is shown as it is.
 */
export function trMessage(english: string): [string, string | null] {
  if (!hasHindi(english)) return [english, null]
  return trBoth(english)
}

/** Marks English text that is stored or sent and translated only when it is shown (a no-op, like Android's trKey). */
export const trKey = (english: string): string => english
