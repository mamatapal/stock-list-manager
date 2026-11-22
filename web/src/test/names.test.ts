import { beforeEach, describe, expect, it } from 'vitest'
import { both, clean, combine, key, show, split, withBothSpellings } from '../i18n/bilingualName'
import { setLanguage } from '../i18n/lang'
import { toEnglish, toHindi } from '../i18n/transliterate'

beforeEach(() => setLanguage('en'))

/** These are the same cases as the Android NameFillTest, so both apps write names the same way. */
describe('writing a name by sound', () => {
  it('uses the usual Hindi spelling of common shop words', () => {
    expect(toHindi('Sugar')).toBe('शुगर')
    expect(toHindi('Basmati Rice')).toBe('बासमती राइस')
    expect(toHindi('5 kg sugar')).toBe('5 केजी शुगर')
    expect(toHindi('Cups')).toBe('कप्स')
  })

  it('spells other words by sound and never leaves Latin letters', () => {
    expect(toHindi('kamal')).toBe('कमल')
    expect(toHindi('silk')).toBe('सिल्क')
    for (const w of ['Parle-G Biscuit', 'Zebra', 'Quick Fix', 'Xerox', 'Lux Soap 100g', 'Tata Salt', 'Aashirvaad Atta']) {
      const hi = toHindi(w)
      expect(hi, w).not.toMatch(/[A-Za-z]/)
      expect(hi.trim(), w).not.toBe('')
    }
  })

  it('keeps spaces, digits and punctuation as typed', () => {
    expect(toHindi('5 kg ')).toBe('5 केजी ')
    expect(toHindi('')).toBe('')
    expect(toHindi('100 – 200')).toBe('100 – 200')
  })

  it('writes Hindi in English letters', () => {
    expect(toEnglish('शुगर')).toBe('Sugar')
    expect(toEnglish('कमल')).toBe('Kamal')
    expect(toEnglish('आलू प्याज़')).not.toMatch(/[ऀ-ॿ]/)
    expect(toEnglish('चिप्स 5')).toBe('Chips 5')
  })

  it('writes sentences and people names the same as Android', () => {
    expect(toHindi('Add expiry date. Items should fresh')).toBe('ऐड एक्सपायरी डेट. आइटम्स शुड फ्रेश')
    expect(toHindi('Mamata Pal')).toBe('ममता पाल')
  })
})

describe('names with two spellings', () => {
  it('combines, splits and tidies', () => {
    expect(combine('Sugar', 'शुगर')).toBe('Sugar / शुगर')
    expect(combine('Sugar', '')).toBe('Sugar')
    expect(combine('', 'शुगर')).toBe('शुगर')
    expect(split('Sugar / शुगर')).toEqual(['Sugar', 'शुगर'])
    expect(split('Salt / Pepper')).toEqual(['Salt / Pepper', ''])
    expect(split('चीनी')).toEqual(['', 'चीनी'])
    expect(split(combine('Basmati ', 'बासमती '))).toEqual(['Basmati ', 'बासमती '])
    expect(clean('  Sugar   /  शुगर ')).toBe('Sugar / शुगर')
    expect(clean('Basmati   Rice  /  बासमती   राइस')).toBe('Basmati Rice / बासमती राइस')
    expect(key('Sugar / शुगर')).toBe('Sugar')
    expect(key('चीनी')).toBe('चीनी')
    expect(both('Sugar')).toBeNull()
  })

  it('shows the main language first, and the other one under it', () => {
    expect(show('Sugar / शुगर')).toEqual(['Sugar', 'शुगर'])
    setLanguage('hi')
    expect(show('Sugar / शुगर')).toEqual(['शुगर', 'Sugar'])
  })

  it('shows both languages for a name saved with one spelling only', () => {
    expect(show('Sugar')).toEqual(['Sugar', 'शुगर'])
    expect(show('चीनी')).toEqual(['Cheeni', 'चीनी'])
    setLanguage('hi')
    expect(show('Sugar')).toEqual(['शुगर', 'Sugar'])
    expect(show('')).toEqual(['', null])
  })

  it('adds the other spelling when an old name is opened for editing', () => {
    expect(withBothSpellings('Sugar')).toBe('Sugar / शुगर')
    expect(withBothSpellings('Sugar / शुगर')).toBe('Sugar / शुगर')
  })
})
