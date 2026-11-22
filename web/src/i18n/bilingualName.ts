import { toEnglish, toHindi } from './transliterate'
import { getLanguage } from './lang'

/**
 * Names (and notes, addresses) are saved with both an English and a Hindi spelling in the one field: "Sugar / शुगर".
 * English always comes first, so lists stay in A–Z order and searches find either spelling. The same format as the
 * Android app, so both apps read each other's data.
 */
export const SEPARATOR = ' / '

export const hasDevanagari = (t: string) => /[ऀ-ॿ]/.test(t)
const hasLatin = (t: string) => /[A-Za-z]/.test(t)

/** [English, Hindi] as typed (not trimmed, so a space typed at the end of a word is kept). */
export function split(name: string): [string, string] {
  const at = name.indexOf(SEPARATOR)
  if (at >= 0) {
    const left = name.substring(0, at)
    const right = name.substring(at + SEPARATOR.length)
    if (right.trim() && left.trim() && !hasDevanagari(left) && hasDevanagari(right) && !hasLatin(right)) return [left, right]
  }
  return hasDevanagari(name) && !hasLatin(name) ? ['', name] : [name, '']
}

export function combine(english: string, hindi: string): string {
  if (!hindi.trim()) return english
  if (!english.trim()) return hindi
  return english + SEPARATOR + hindi
}

/** The name ready to save: spaces tidied in each spelling. */
export function clean(name: string): string {
  const [en, hi] = split(name)
  const tidy = (s: string) => s.trim().replace(/\s+/g, ' ')
  return combine(tidy(en), tidy(hi))
}

/** The English spelling, used to find duplicates; a Hindi-only name is its own key. */
export function key(name: string): string {
  const [en, hi] = split(name)
  return en.trim() || hi.trim()
}

export function both(name: string): [string, string] | null {
  const [en, hi] = split(name)
  return en.trim() && hi.trim() ? [en.trim(), hi.trim()] : null
}

/**
 * A name as it is shown: [main, other], the main language first. A name saved with one spelling only gets the other
 * one written by sound, so every screen shows both.
 */
export function show(name: string | null | undefined): [string, string | null] {
  if (!name || !name.trim()) return ['', null]
  let en: string, hi: string
  const b = both(name)
  if (b) [en, hi] = b
  else if (hasDevanagari(name) && !hasLatin(name)) { hi = name.trim(); en = toEnglish(hi) }
  else if (hasLatin(name) && !hasDevanagari(name)) { en = name.trim(); hi = toHindi(en) }
  else return [name, null]
  const main: [string, string] = getLanguage() === 'hi' ? [hi, en] : [en, hi]
  return main[0] === main[1] ? [main[0], null] : main
}

/** The name in the main language only (for sorting, select boxes and search results). */
export const mainOnly = (name: string | null | undefined): string => show(name)[0]

/** One line with both spellings, for places that cannot show two lines (select boxes). */
export const oneLine = (name: string | null | undefined): string => {
  const [a, b] = show(name)
  return b ? `${a} / ${b}` : a
}

/** An existing name with its Hindi (or English) spelling added, ready to edit. */
export function withBothSpellings(name: string): string {
  if (!name.trim() || both(name)) return name
  const [en, hi] = split(name)
  if (en.trim() && !hi.trim()) return combine(en.trim(), toHindi(en.trim()))
  if (hi.trim() && !en.trim()) return combine(toEnglish(hi.trim()), hi.trim())
  return name
}
