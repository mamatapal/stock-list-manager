import { add, cmp, div, mulInt, parse, setScale, sign, stripTrailingZeros, sum as decSum, toPlain, type Dec } from './dec'

/**
 * Text formatting for PDFs, the same rules as the Android app: ₹ with Indian digit grouping (₹1,23,456.50), dates as
 * DD-MM-YYYY, quantities without trailing zeros.
 */
export const RUPEE = '₹'
export const NONE = '-'

/** "1234567.50" → "12,34,567.50" (last 3 digits, then pairs). */
export function grouped(plain: string): string {
  const dot = plain.indexOf('.')
  const int = dot < 0 ? plain : plain.slice(0, dot)
  const frac = dot < 0 ? '' : plain.slice(dot)
  if (int.length <= 3) return int + frac
  const last3 = int.slice(-3)
  const rest = int.slice(0, -3)
  const parts: string[] = []
  const first = rest.length % 2
  if (first > 0) parts.push(rest.slice(0, first))
  for (let i = first; i < rest.length; i += 2) parts.push(rest.slice(i, i + 2))
  return parts.join(',') + ',' + last3 + frac
}

const signed = (d: Dec, body: string) => (sign(d) < 0 ? '-' : '') + body
const abs = (d: Dec): Dec => (sign(d) < 0 ? { n: -d.n, s: d.s } : d)

export function moneyDec(d: Dec): string {
  const s = setScale(d, 2)
  return signed(s, RUPEE + grouped(toPlain(abs(s))))
}
export const money = (v: string | null | undefined): string => { const d = parse(v); return d ? moneyDec(d) : NONE }

/** ₹ unit price: at least 2 and at most 4 decimals (₹12.50, ₹0.3333). */
export function unitPrice(v: string | null | undefined): string {
  const d = parse(v)
  if (!d) return NONE
  const scale = Math.min(4, Math.max(2, stripTrailingZeros(d).s))
  const s = setScale(d, scale)
  return signed(s, RUPEE + grouped(toPlain(abs(s))))
}

export function qty(v: string | null | undefined, unit?: string | null): string {
  const d = parse(v)
  if (!d) return NONE
  const stripped = stripTrailingZeros(d)
  const text = signed(stripped, grouped(toPlain(abs(stripped))))
  return !unit || !unit.trim() ? text : `${text} ${unit}`
}

export const percent = (v: string | null | undefined): string => { const d = parse(v); return d ? toPlain(setScale(d, 2)) + '%' : NONE }

/** yyyy-MM-dd (or a full ISO time) as DD-MM-YYYY. */
export function date(v: string | null | undefined): string {
  if (!v) return NONE
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(v)
  return m ? `${m[3]}-${m[2]}-${m[1]}` : NONE
}

export function dateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getDate())}-${p(d.getMonth() + 1)}-${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export const sumOf = (values: Array<string | null | undefined>): Dec => decSum(values)
export const share = (part: Dec, whole: Dec): string =>
  sign(whole) === 0 ? NONE : percent(toPlain(div(mulInt(part, 100), whole, 2)))
export { add, cmp, div, parse, sign, toPlain }

/** A file name safe for phones, WhatsApp and e-mail: letters (Hindi included), digits, '-' and '_'. */
export function fileName(...parts: Array<string | null | undefined>): string {
  const clean = parts.filter((p): p is string => !!p && p.trim() !== '').map((p) =>
    p.trim().replace(/[^\p{L}\p{M}\p{N}-]+/gu, '-').replace(/^-+|-+$/g, '').slice(0, 40).replace(/-+$/, ''))
  return (clean.filter(Boolean).join('_') || 'document') + '.pdf'
}
