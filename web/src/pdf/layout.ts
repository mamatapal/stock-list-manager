import { isBi, lineList, plainText, type Align, type Block, type ReportDoc, type Row, type Txt } from './model'

/**
 * Lays a [ReportDoc] out as A4 pages (595 x 842 points). Same rules as the Android app: the page footer says
 * "Page 3 of 7", tables repeat their heading on every page they continue to, wrapped cells never overlap, and a row
 * taller than a page is split at line boundaries. The result is plain drawing data, so tests need no canvas.
 */
export const PAGE_W = 595
export const PAGE_H = 842
const MARGIN = 36
const FOOTER_H = 22
const CONTENT_W = PAGE_W - 2 * MARGIN
const BODY_BOTTOM = PAGE_H - MARGIN - FOOTER_H
const PAD_X = 3.5
const PAD_Y = 3

export const INK = '#1F2328'
export const MUTED = '#6B7280'
export const RULE = '#C9CED6'
export const HEAD_BG = '#E8ECF1'
export const WARN_BG = '#FFF4E0'
export const WARN_LINE = '#E09A2B'

export interface Paint { size: number; bold: boolean; color: string }
const paint = (size: number, bold = false, color = INK): Paint => ({ size, bold, color })
const P = {
  businessName: paint(15, true), businessLine: paint(8.5, false, MUTED), title: paint(13, true), subtitle: paint(9, false, MUTED),
  heading: paint(10.5, true), label: paint(8.5, false, MUTED), value: paint(8.5), note: paint(8.5), cell: paint(8.5),
  cellBold: paint(8.5, true), cellMuted: paint(8.5, false, MUTED), head: paint(8, true), footer: paint(7.5, false, MUTED),
}

export type Op =
  | { t: 'text'; x: number; y: number; w: number; align: Align; text: string; paint: Paint }
  | { t: 'line'; y: number; color: string; width: number }
  | { t: 'rect'; x: number; y: number; w: number; h: number; color: string }

export interface Page { ops: Op[]; headings: number; rows: string[] }
export type Measure = (text: string, paint: Paint) => number

const lineHeight = (p: Paint) => p.size * 1.4

interface TLine { text: string; align: Align }
interface TBox { lines: TLine[]; paint: Paint; height: number }

/** Splits [text] into pieces that each fit [width], at spaces; a word wider than the line is cut between letters. */
export function wrap(text: string, width: number, p: Paint, measure: Measure): string[] {
  const out: string[] = []
  for (const para of text.split('\n')) {
    const words = para.split(/\s+/).filter(Boolean)
    if (words.length === 0) { out.push(''); continue }
    let line = ''
    for (const word of words) {
      const tryLine = line ? `${line} ${word}` : word
      if (measure(tryLine, p) <= width) { line = tryLine; continue }
      if (line) { out.push(line); line = '' }
      if (measure(word, p) <= width) { line = word; continue }
      let chunk = ''
      for (const ch of graphemes(word)) {
        if (chunk && measure(chunk + ch, p) > width) { out.push(chunk); chunk = ch } else chunk += ch
      }
      line = chunk
    }
    out.push(line)
  }
  return out
}

function graphemes(s: string): string[] {
  const Seg = (Intl as unknown as { Segmenter?: new (l: string, o: { granularity: string }) => { segment(s: string): Iterable<{ segment: string }> } }).Segmenter
  return Seg ? Array.from(new Seg('hi', { granularity: 'grapheme' }).segment(s), (x) => x.segment) : Array.from(s)
}

function textBox(t: Txt, p: Paint, width: number, align: Align, measure: Measure): TBox {
  const lines: TLine[] = []
  for (const l of lineList(t)) {
    const parts = isBi(l) ? [l.main, l.other].filter((x): x is string => !!x) : [l]
    for (const part of parts) for (const w of wrap(part, Math.max(1, width), p, measure)) lines.push({ text: w, align })
  }
  if (lines.length === 0) lines.push({ text: '', align })
  return { lines, paint: p, height: lines.length * lineHeight(p) }
}

class Cursor {
  pages: Page[] = []
  y = 0
  constructor(readonly doc: ReportDoc, readonly measure: Measure) {}
  get page() { return this.pages[this.pages.length - 1] }
  room() { return BODY_BOTTOM - this.y }
  op(o: Op) { this.page.ops.push(o) }

  newPage() {
    this.pages.push({ ops: [], headings: 0, rows: [] })
    this.y = MARGIN
    if (this.pages.length === 1) this.firstHeader(); else this.continuedHeader()
  }

  box(b: TBox, x: number, top: number, width: number) {
    b.lines.forEach((l, i) => this.op({ t: 'text', x, y: top + i * lineHeight(b.paint), w: width, align: l.align, text: l.text, paint: b.paint }))
  }

  private firstHeader() {
    const d = this.doc
    const name = textBox(d.business.name, P.businessName, CONTENT_W, 'left', this.measure)
    this.box(name, MARGIN, this.y, CONTENT_W); this.y += name.height + 2
    for (const line of d.business.lines) {
      const l = textBox(line, P.businessLine, CONTENT_W, 'left', this.measure)
      this.box(l, MARGIN, this.y, CONTENT_W); this.y += l.height
    }
    this.y += 6
    this.op({ t: 'line', y: this.y, color: INK, width: 0.9 }); this.y += 9
    const t = textBox(d.title, P.title, CONTENT_W, 'left', this.measure)
    this.box(t, MARGIN, this.y, CONTENT_W); this.y += t.height + 2
    for (const s of d.subtitle) {
      const l = textBox(s, P.subtitle, CONTENT_W, 'left', this.measure)
      this.box(l, MARGIN, this.y, CONTENT_W); this.y += l.height
    }
    this.y += 10
  }

  private continuedHeader() {
    const d = this.doc
    const l = textBox(d.continuedLabel(plainText(d.business.name), plainText(d.title)), P.subtitle, CONTENT_W, 'left', this.measure)
    this.box(l, MARGIN, this.y, CONTENT_W); this.y += l.height + 4
    this.op({ t: 'line', y: this.y, color: RULE, width: 0.6 }); this.y += 8
  }
}

export function layout(doc: ReportDoc, measure: Measure): Page[] {
  const cur = new Cursor(doc, measure)
  cur.newPage()
  for (const b of doc.blocks) block(cur, b)
  const total = cur.pages.length
  cur.pages.forEach((pg, i) => {
    const y = PAGE_H - MARGIN - FOOTER_H + 8
    pg.ops.push({ t: 'line', y, color: RULE, width: 0.6 })
    pg.ops.push({ t: 'text', x: MARGIN, y: y + 4, w: CONTENT_W - 90, align: 'left', text: doc.footerLeft, paint: P.footer })
    pg.ops.push({ t: 'text', x: PAGE_W - MARGIN - 90, y: y + 4, w: 90, align: 'right', text: doc.pageLabel(i + 1, total), paint: P.footer })
  })
  return cur.pages
}

function block(cur: Cursor, b: Block) {
  if (b.kind === 'heading') {
    const l = textBox(b.text, P.heading, CONTENT_W, 'left', cur.measure)
    if (cur.room() < l.height + 40) cur.newPage()
    cur.y += 4
    cur.box(l, MARGIN, cur.y, CONTENT_W)
    cur.y += l.height + 5
  } else if (b.kind === 'facts') {
    const items = b.items.filter(([, v]) => v !== null && v !== undefined && plainText(v).trim() !== '')
    if (items.length === 0) return
    const colW = (CONTENT_W - 16) / 2
    const labelW = 78
    for (let i = 0; i < items.length; i += 2) {
      const pair = items.slice(i, i + 2).map(([label, value]) => [
        textBox(label, P.label, labelW, 'left', cur.measure), textBox(value!, P.value, colW - labelW - 4, 'left', cur.measure)] as const)
      const h = Math.max(...pair.map(([a, v]) => Math.max(a.height, v.height))) + 3
      if (cur.room() < h) cur.newPage()
      pair.forEach(([a, v], k) => {
        const x = MARGIN + k * (colW + 16)
        cur.box(a, x, cur.y, labelW)
        cur.box(v, x + labelW + 4, cur.y, colW - labelW - 4)
      })
      cur.y += h
    }
    cur.y += 6
  } else if (b.kind === 'note') {
    const pad = b.warning ? 6 : 0
    const l = textBox(b.text, P.note, CONTENT_W - 2 * pad, 'left', cur.measure)
    const h = l.height + 2 * pad
    if (cur.room() < h) cur.newPage()
    const top = cur.y
    if (b.warning) {
      cur.op({ t: 'rect', x: MARGIN, y: top, w: CONTENT_W, h, color: WARN_BG })
      cur.op({ t: 'rect', x: MARGIN, y: top, w: 2.5, h, color: WARN_LINE })
    }
    cur.box(l, MARGIN + pad, top + pad, CONTENT_W - 2 * pad)
    cur.y += h + 8
  } else {
    table(cur, b)
  }
}

interface Cells { boxes: TBox[]; height: number }

function table(cur: Cursor, t: Extract<Block, { kind: 'table' }>) {
  const total = t.columns.reduce((a, c) => a + c.weight, 0)
  const widths = t.columns.map((c) => (CONTENT_W * c.weight) / total)
  const xs = widths.reduce<number[]>((acc, w) => [...acc, acc[acc.length - 1] + w], [MARGIN])

  const cells = (row: Txt[], p: Paint): Cells => {
    const boxes = row.map((s, i) => textBox(s, p, widths[i] - 2 * PAD_X, t.columns[i].align ?? 'left', cur.measure))
    return { boxes, height: Math.max(...boxes.map((b) => b.height)) + 2 * PAD_Y }
  }
  const paintOf = (r: Row) => (r.muted ? P.cellMuted : r.bold ? P.cellBold : P.cell)
  const head = cells(t.columns.map((c) => c.title), P.head)

  const drawHeading = () => {
    const top = cur.y
    cur.op({ t: 'rect', x: MARGIN, y: top, w: CONTENT_W, h: head.height, color: HEAD_BG })
    head.boxes.forEach((b, i) => cur.box(b, xs[i] + PAD_X, top + PAD_Y, widths[i] - 2 * PAD_X))
    cur.y += head.height
    cur.page.headings++
  }

  const body = t.rows.map((r) => cells(r.cells, paintOf(r)))
  const firstH = body[0]?.height ?? 18
  if (cur.room() < head.height + Math.min(firstH, 60)) cur.newPage()
  drawHeading()

  if (body.length === 0) {
    const l = textBox(t.emptyText, P.cellMuted, CONTENT_W - 2 * PAD_X, 'center', cur.measure)
    cur.box(l, MARGIN + PAD_X, cur.y + 6, CONTENT_W - 2 * PAD_X)
    cur.y += l.height + 12
    cur.op({ t: 'line', y: cur.y, color: RULE, width: 0.6 })
  }
  body.forEach((c, i) => drawRow(cur, c, xs, widths, `row ${i}`, drawHeading, head.height))

  const totals = t.totals ?? []
  if (totals.length > 0) {
    const tc = totals.map((r) => cells(r.cells, P.cellBold))
    if (cur.room() < tc.reduce((a, c) => a + c.height, 0) + 2) { cur.newPage(); drawHeading() }
    cur.op({ t: 'line', y: cur.y, color: INK, width: 0.9 })
    cur.y += 1
    tc.forEach((c, i) => drawRow(cur, c, xs, widths, `total ${i}`, drawHeading, head.height))
  }
  cur.y += 10
}

function drawRow(cur: Cursor, c: Cells, xs: number[], widths: number[], name: string, heading: () => void, headH: number) {
  if (c.height <= cur.room()) {
    const top = cur.y
    c.boxes.forEach((b, i) => cur.box(b, xs[i] + PAD_X, top + PAD_Y, widths[i] - 2 * PAD_X))
    cur.y += c.height
    cur.op({ t: 'line', y: cur.y, color: RULE, width: 0.6 })
    cur.page.rows.push(name)
    return
  }
  const fullPage = BODY_BOTTOM - MARGIN - 40 - headH
  if (c.height <= fullPage) {
    cur.newPage(); heading()
    drawRow(cur, c, xs, widths, name, heading, headH)
    return
  }
  // Taller than a page: continue it in slices that end on whole lines.
  const lineH = lineHeight(c.boxes[0].paint)
  const most = Math.max(...c.boxes.map((b) => b.lines.length))
  let done = 0
  let part = 1
  while (done < most) {
    let avail = cur.room() - 2 * PAD_Y
    if (avail < lineH * 3) { cur.newPage(); heading(); avail = cur.room() - 2 * PAD_Y }
    const n = Math.max(1, Math.min(Math.floor(avail / lineH), most - done))
    const top = cur.y
    c.boxes.forEach((b, i) => {
      const slice: TBox = { ...b, lines: b.lines.slice(done, done + n), height: 0 }
      cur.box(slice, xs[i] + PAD_X, top + PAD_Y, widths[i] - 2 * PAD_X)
    })
    cur.y += n * lineH + 2 * PAD_Y
    cur.op({ t: 'line', y: cur.y, color: RULE, width: 0.6 })
    cur.page.rows.push(`${name} part ${part}`)
    done += n
    part++
    if (done < most) { cur.newPage(); heading() }
  }
}
