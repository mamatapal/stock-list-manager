import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'
import { format, hasHindi, setLanguage, tr, trBoth, trLine, trMessage, translate } from '../i18n/lang'

beforeEach(() => setLanguage('en'))

describe('languages', () => {
  it('shows the main language first and the other one second', () => {
    expect(trBoth('Sign in')).toEqual(['Sign in', 'साइन इन'])
    setLanguage('hi')
    expect(trBoth('Sign in')).toEqual(['साइन इन', 'Sign in'])
    expect(trLine('Sign in')).toBe('साइन इन / Sign in')
    expect(tr('Sign in')).toBe('साइन इन')
  })

  it('fills in placeholders in both languages', () => {
    expect(translate('Signed in as %1$s', 'en', ['Asha'])).toBe('Signed in as Asha')
    expect(translate('Signed in as %1$s', 'hi', ['Asha'])).toBe('Asha के रूप में साइन इन हैं')
    expect(format('%2$s then %1$s', ['a', 'b'])).toBe('b then a')
  })

  it('collapses a text that is the same in both languages', () => {
    expect(trBoth('English')).toEqual(['English', null])
    expect(trBoth('Stock List Manager')[1]).toBeNull()
  })

  it('shows English for a text that has no Hindi yet instead of failing', () => {
    expect(trBoth('A text nobody translated')).toEqual(['A text nobody translated', null])
  })

  it('translates the fixed messages of the server and leaves other server text alone', () => {
    expect(hasHindi('You do not have permission for this action')).toBe(true)
    expect(trMessage('You do not have permission for this action')[1]).toBeTruthy()
    expect(trMessage('Some made-up server text')).toEqual(['Some made-up server text', null])
  })
})

/** Every text in the web code that goes through the translation helpers must have a Hindi wording. */
describe('Hindi coverage', () => {
  const root = join(__dirname, '..')
  const files: string[] = []
  const walk = (dir: string) => {
    for (const name of readdirSync(dir)) {
      const p = join(dir, name)
      if (statSync(p).isDirectory()) { if (name !== 'test') walk(p) } else if (/\.(ts|tsx)$/.test(name) && !name.startsWith('hi.')) files.push(p)
    }
  }
  walk(root)

  const literals = new Set<string>()
  const quoted = /(['"])((?:(?!\1)[^\\\n]|\\.)*)\1/g
  for (const f of files) {
    const src = readFileSync(f, 'utf8')
    for (const m of src.matchAll(/\b(?:tr|trLine|trBoth|trKey|trMessage)\(\s*(['"`])((?:(?!\1)[^\\\n]|\\.)*)\1/g)) literals.add(m[2])
    for (const m of src.matchAll(/\bsetError\(\s*(['"])((?:(?!\1)[^\\\n]|\\.)*)\1\)/g)) literals.add(m[2])
    for (const m of src.matchAll(/(?<![-\w])(?:k|label)="([^"]+)"/g)) literals.add(m[1])
    // k={ cond ? 'text' : 'other text' }
    for (const m of src.matchAll(/\bk=\{([^}]*)\}/g)) for (const q of m[1].slice(m[1].indexOf('?') + 1).matchAll(quoted)) literals.add(q[2])
  }
  // texts stored by key (menus and similar)
  for (const f of files.filter((x) => x.endsWith('nav.ts'))) {
    for (const m of readFileSync(f, 'utf8').matchAll(/label: trKey\('([^']+)'\)/g)) literals.add(m[1])
  }

  it('found the texts to check', () => { expect(literals.size).toBeGreaterThan(25) })

  it('has a Hindi wording for every text', () => {
    const missing = [...literals].filter((t) => !hasHindi(t) && !['English', 'Stock List Manager'].includes(t))
    expect(missing).toEqual([])
  })

  it('uses the same placeholders in English and Hindi', () => {
    const ph = (s: string) => [...s.matchAll(/%\d+\$s/g)].map((m) => m[0]).sort().join(',')
    const bad = [...literals].filter((t) => hasHindi(t) && ph(t) !== ph(translate(t, 'hi')))
    expect(bad).toEqual([])
  })
})
