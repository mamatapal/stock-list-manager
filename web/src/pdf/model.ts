/** A text in two languages: the main one first, the other (if any) on the line below. */
export interface Bi { main: string; other: string | null }
/** One cell, heading or sentence: lines, each plain or bilingual. */
export type Txt = string | Bi | Array<string | Bi>

export type Align = 'left' | 'center' | 'right'
export interface Column { title: Txt; weight: number; align?: Align }
export interface Row { cells: Txt[]; muted?: boolean; bold?: boolean }

export type Block =
  | { kind: 'heading'; text: Txt }
  | { kind: 'facts'; items: Array<[Txt, Txt | null | undefined]> }
  | { kind: 'note'; text: Txt; warning?: boolean }
  | { kind: 'table'; columns: Column[]; rows: Row[]; totals?: Row[]; emptyText: Txt }

export interface ReportDoc {
  business: { name: Txt; lines: Txt[] }
  title: Txt
  subtitle: Txt[]
  blocks: Block[]
  footerLeft: string
  pageLabel: (n: number, total: number) => string
  continuedLabel: (business: string, title: string) => string
  fileName: string
}

export const isBi = (x: unknown): x is Bi => typeof x === 'object' && x !== null && !Array.isArray(x) && 'main' in x
export const lineList = (t: Txt): Array<string | Bi> => (Array.isArray(t) ? t : [t])
export const plainText = (t: Txt): string => lineList(t).map((l) => (isBi(l) ? l.main : l)).join(' ')
