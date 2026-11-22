import wordList from './words.generated.json'

/**
 * Writes a name in the other script by SOUND, not by meaning: "Sugar" becomes "शुगर" and "शुगर" becomes "Sugar".
 * Same rules and the same shop-word list as the Android app. Other words are spelled out by letter sounds, which is
 * only approximate, so the screens always let the user correct the result.
 */
const words = new Map<string, string>()
for (const [en, hi] of wordList as Array<[string, string]>) words.set(en, hi)
const reverseWords = new Map<string, string>()
for (const [en, hi] of wordList as Array<[string, string]>) if (!reverseWords.has(hi)) reverseWords.set(hi, en)

const consonants: Array<[string, string]> = [
  ['ksh', 'क्ष'], ['chh', 'छ'], ['tch', 'च'], ['sh', 'श'], ['ch', 'च'], ['th', 'थ'], ['ph', 'फ'], ['kh', 'ख'],
  ['gh', 'घ'], ['bh', 'भ'], ['dh', 'ध'], ['ck', 'क'], ['qu', 'क्व'],
  ['b', 'ब'], ['c', 'क'], ['d', 'ड'], ['f', 'फ'], ['g', 'ग'], ['h', 'ह'], ['j', 'ज'], ['k', 'क'], ['l', 'ल'],
  ['m', 'म'], ['n', 'न'], ['p', 'प'], ['q', 'क'], ['r', 'र'], ['s', 'स'], ['t', 'ट'], ['v', 'व'], ['w', 'व'],
  ['x', 'क्स'], ['y', 'य'], ['z', 'ज़'],
]

type Pair = [string, string]
const vowels: Array<[string, Pair]> = [
  ['aa', ['ा', 'आ']], ['ee', ['ी', 'ई']], ['ea', ['ी', 'ई']], ['oo', ['ू', 'ऊ']],
  ['ai', ['ै', 'ऐ']], ['ay', ['े', 'ए']], ['au', ['ौ', 'औ']], ['aw', ['ौ', 'औ']],
  ['ou', ['ाउ', 'आउ']], ['ow', ['ाउ', 'आउ']], ['oy', ['ॉय', 'ऑय']], ['oi', ['ॉय', 'ऑय']],
  ['ie', ['ी', 'ई']], ['ei', ['े', 'ए']], ['oa', ['ो', 'ओ']], ['ue', ['ू', 'ऊ']],
  ['ui', ['ू', 'ऊ']],
  ['a', ['', 'अ']], ['e', ['े', 'ए']], ['i', ['ि', 'इ']], ['o', ['ो', 'ओ']], ['u', ['ु', 'उ']],
]
const longAtEnd: Record<string, Pair> = { a: ['ा', 'आ'], i: ['ी', 'ई'], u: ['ू', 'ऊ'] }

type Tok = { kind: 'C'; hi: string; latin: string } | { kind: 'V'; sign: string; full: string }

const isLatin = (c: string) => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
const isDevanagariLetter = (c: string) => c >= 'ऀ' && c <= 'ॿ' && c !== '।' && c !== '॥' && !(c >= '०' && c <= '९')
const upperFirst = (s: string) => (s ? s[0].toUpperCase() + s.slice(1) : s)

function tokens(w: string): Tok[] {
  const toks: Tok[] = []
  let end = w.length
  // a silent final "e" after a consonant ("rice", "cake") is not spoken but lengthens the vowel before it
  const silentE = end > 2 && w.endsWith('e') && !'aeiouy'.includes(w[end - 2]) && 'aeiou'.includes(w[end - 3])
  if (silentE) end -= 1
  let i = 0
  while (i < end) {
    const rest = w.substring(i, end)
    const v = vowels.find(([k]) => rest.startsWith(k))
    if (v) {
      let pair: Pair = v[1]
      const atEnd = i + v[0].length === end && !silentE
      if (atEnd && v[0] in longAtEnd && toks.length > 0) pair = longAtEnd[v[0]]
      if (atEnd && v[0] === 'e' && toks.length > 1) pair = ['', '']
      if (silentE && i + v[0].length === end - 1) {
        switch (v[0]) {
          case 'a': pair = ['े', 'ए']; break
          case 'i': pair = ['ाइ', 'आइ']; break
          case 'o': pair = ['ो', 'ओ']; break
          case 'u': pair = ['ू', 'यू']; break
        }
      }
      toks.push({ kind: 'V', sign: pair[0], full: pair[1] })
      i += v[0].length
      continue
    }
    if (rest.startsWith('y')) {
      const atEnd = i + 1 === end
      if (atEnd && toks.length > 0 && toks[toks.length - 1].kind === 'C') { toks.push({ kind: 'V', sign: 'ी', full: 'ई' }); i++; continue }
    }
    if (rest.startsWith('c') && rest.length > 1 && 'eiy'.includes(rest[1])) { toks.push({ kind: 'C', hi: 'स', latin: 's' }); i++; continue }
    if (silentE && rest.startsWith('c') && i === end - 1) { toks.push({ kind: 'C', hi: 'स', latin: 's' }); i++; continue }
    const c = consonants.find(([k]) => rest.startsWith(k))
    if (!c) { i++; continue }
    // a doubled letter ("tt", "ll") is one sound
    const doubled = c[0].length === 1 && rest.length > 1 && rest[1] === rest[0]
    toks.push({ kind: 'C', hi: c[1], latin: c[0] })
    i += c[0].length + (doubled ? 1 : 0)
  }
  if (silentE && toks.length > 0) {
    const last = toks[toks.length - 1]
    if (last.kind === 'C' && w[w.length - 1] === 'e' && w[w.length - 2] === 'c') toks[toks.length - 1] = { kind: 'C', hi: 'स', latin: 's' }
  }
  return toks
}

function wordToHindi(word: string): string {
  const w = word.toLowerCase()
  const known = words.get(w)
  if (known !== undefined) return known
  if (w.length > 1 && w.endsWith('s')) { const k = words.get(w.slice(0, -1)); if (k !== undefined) return k + '्स' }
  const toks = tokens(w)
  let sb = ''
  toks.forEach((t, idx) => {
    const next = toks[idx + 1]
    const prev = idx > 0 ? toks[idx - 1] : undefined
    if (t.kind === 'C') {
      if (t.latin === 'n' && prev?.kind === 'V' && next?.kind === 'C' && (next.latin === 'g' || next.latin === 'k')) {
        sb += 'ं'
      } else {
        sb += t.hi
        if (next?.kind === 'C' && !(next.latin === 'g' && t.latin === 'n')) sb += '्'
      }
    } else {
      sb += prev?.kind === 'C' ? t.sign : t.full
    }
  })
  return sb
}

/** Latin letters → Devanagari. Anything that is not a letter (spaces, digits, punctuation) stays as typed. */
export function toHindi(text: string): string {
  let out = ''
  let i = 0
  while (i < text.length) {
    if (!isLatin(text[i])) { out += text[i]; i++; continue }
    let j = i
    while (j < text.length && isLatin(text[j])) j++
    out += wordToHindi(text.substring(i, j))
    i = j
  }
  return out
}

const consonantsHi: Record<string, string> = {
  'क': 'k', 'ख': 'kh', 'ग': 'g', 'घ': 'gh', 'ङ': 'ng', 'च': 'ch', 'छ': 'chh', 'ज': 'j', 'झ': 'jh',
  'ञ': 'n', 'ट': 't', 'ठ': 'th', 'ड': 'd', 'ढ': 'dh', 'ण': 'n', 'त': 't', 'थ': 'th', 'द': 'd',
  'ध': 'dh', 'न': 'n', 'प': 'p', 'फ': 'f', 'ब': 'b', 'भ': 'bh', 'म': 'm', 'य': 'y', 'र': 'r',
  'ल': 'l', '\u095C': 'd', '\u095D': 'dh', '\u095B': 'z', '\u095E': 'f', 'व': 'v', 'श': 'sh', 'ष': 'sh', 'स': 's', 'ह': 'h', 'ळ': 'l',
}
const vowelSigns: Record<string, string> = {
  'ा': 'a', 'ि': 'i', 'ी': 'i', 'ु': 'u', 'ू': 'u', 'े': 'e', 'ै': 'ai', 'ो': 'o', 'ौ': 'au', 'ृ': 'ri', 'ॉ': 'o', 'ॅ': 'e',
}
const vowelLetters: Record<string, string> = {
  'अ': 'a', 'आ': 'a', 'इ': 'i', 'ई': 'i', 'उ': 'u', 'ऊ': 'u', 'ए': 'e', 'ऐ': 'ai', 'ओ': 'o', 'औ': 'au', 'ऋ': 'ri', 'ऑ': 'o',
}

function wordToEnglish(word: string): string {
  const known = reverseWords.get(word)
  if (known !== undefined) return upperFirst(known)
  let sb = ''
  let i = 0
  while (i < word.length) {
    const ch = word[i]
    const hasNukta = i + 1 < word.length && word[i + 1] === '\u093C'
    let cons: string | undefined
    if (hasNukta) {
      cons = ch === '\u0921' ? 'd' : ch === '\u0922' ? 'dh' : ch === '\u091C' ? 'z' : ch === '\u092B' ? 'f' : consonantsHi[ch]
    } else cons = consonantsHi[ch]
    if (cons !== undefined) {
      sb += cons
      i += hasNukta ? 2 : 1
      const sign = word[i]
      if (sign === '्') i++
      else if (sign !== undefined && sign in vowelSigns) { sb += vowelSigns[sign]; i++ }
      else if (i >= word.length) { /* nothing */ }
      else sb += 'a'
      continue
    }
    const v = vowelLetters[ch]
    if (v !== undefined) sb += v
    else if (ch === 'ं' || ch === 'ँ') sb += 'n'
    else if (ch === 'ः') sb += 'h'
    i++
  }
  return upperFirst(sb)
}

/** Devanagari → Latin letters, each word starting with a capital. Anything else stays as typed. */
export function toEnglish(text: string): string {
  let out = ''
  let i = 0
  while (i < text.length) {
    if (!isDevanagariLetter(text[i])) { out += text[i]; i++; continue }
    let j = i
    while (j < text.length && isDevanagariLetter(text[j])) j++
    out += wordToEnglish(text.substring(i, j))
    i = j
  }
  return out
}
