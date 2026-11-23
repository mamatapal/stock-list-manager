/** Exact decimal numbers for money and quantities (no floating-point drift): value = n / 10^s. */
export interface Dec { n: bigint; s: number }

export const ZERO: Dec = { n: 0n, s: 0 }

export function parse(v: string | null | undefined): Dec | null {
  if (v === null || v === undefined) return null
  const t = v.trim()
  const m = /^([+-]?)(\d+)(?:\.(\d+))?$/.exec(t)
  if (!m) return null
  const frac = m[3] ?? ''
  const n = BigInt(m[2] + frac)
  return { n: m[1] === '-' ? -n : n, s: frac.length }
}

const pow10 = (k: number) => 10n ** BigInt(k)
const align = (a: Dec, b: Dec): [bigint, bigint, number] => {
  const s = Math.max(a.s, b.s)
  return [a.n * pow10(s - a.s), b.n * pow10(s - b.s), s]
}

export const add = (a: Dec, b: Dec): Dec => { const [x, y, s] = align(a, b); return { n: x + y, s } }
export const cmp = (a: Dec, b: Dec): number => { const [x, y] = align(a, b); return x < y ? -1 : x > y ? 1 : 0 }
export const sign = (a: Dec): number => (a.n < 0n ? -1 : a.n > 0n ? 1 : 0)
export const sum = (values: Array<string | null | undefined>): Dec => values.reduce<Dec>((acc, v) => { const d = parse(v); return d ? add(acc, d) : acc }, ZERO)

/** Rounds half away from zero (like BigDecimal HALF_UP) to [scale] decimals. */
export function setScale(a: Dec, scale: number): Dec {
  if (scale >= a.s) return { n: a.n * pow10(scale - a.s), s: scale }
  const div = pow10(a.s - scale)
  const abs = a.n < 0n ? -a.n : a.n
  let q = abs / div
  if ((abs % div) * 2n >= div) q += 1n
  return { n: a.n < 0n ? -q : q, s: scale }
}

export function stripTrailingZeros(a: Dec): Dec {
  let { n, s } = a
  if (n === 0n) return { n: 0n, s: 0 }
  while (s > 0 && n % 10n === 0n) { n /= 10n; s-- }
  return { n, s }
}

/** a / b rounded half-up to [scale] decimals. */
export function div(a: Dec, b: Dec, scale: number): Dec {
  const num = a.n * pow10(scale + b.s)
  const den = b.n * pow10(a.s)
  const neg = (num < 0n) !== (den < 0n)
  const an = num < 0n ? -num : num
  const ad = den < 0n ? -den : den
  let q = an / ad
  if ((an % ad) * 2n >= ad) q += 1n
  return { n: neg ? -q : q, s: scale }
}

export const mulInt = (a: Dec, k: number): Dec => ({ n: a.n * BigInt(k), s: a.s })

export function toPlain(a: Dec): string {
  const neg = a.n < 0n
  const digits = (neg ? -a.n : a.n).toString().padStart(a.s + 1, '0')
  const int = digits.slice(0, digits.length - a.s)
  const frac = a.s > 0 ? '.' + digits.slice(digits.length - a.s) : ''
  return (neg ? '-' : '') + int + frac
}

export const toNumber = (a: Dec): number => Number(toPlain(a))
