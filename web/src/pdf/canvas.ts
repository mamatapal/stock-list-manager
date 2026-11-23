import { layout, PAGE_H, PAGE_W, type Op, type Paint, type Page } from './layout'
import type { ReportDoc } from './model'

const SCALE = 2.5
const FONT = '"Noto Sans Devanagari", "Kohinoor Devanagari", "Devanagari Sangam MN", "Mangal", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'
const font = (p: Paint) => `${p.bold ? '700 ' : '400 '}${p.size}px ${FONT}`

/** Draws laid-out pages onto canvases; the browser does the Hindi letter shaping, so conjuncts and vowel signs are right. */
export async function renderPages(doc: ReportDoc): Promise<{ pages: Page[]; jpegs: Uint8Array[] }> {
  const measureCanvas = document.createElement('canvas')
  const mctx = measureCanvas.getContext('2d')!
  const measure = (text: string, p: Paint) => { mctx.font = font(p); return mctx.measureText(text).width }
  const pages = layout(doc, measure)
  const jpegs: Uint8Array[] = []
  for (const page of pages) {
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(PAGE_W * SCALE)
    canvas.height = Math.round(PAGE_H * SCALE)
    const ctx = canvas.getContext('2d')!
    ctx.scale(SCALE, SCALE)
    ctx.fillStyle = '#FFFFFF'
    ctx.fillRect(0, 0, PAGE_W, PAGE_H)
    for (const op of page.ops) draw(ctx, op)
    const blob: Blob = await new Promise((res, rej) => canvas.toBlob((b) => (b ? res(b) : rej(new Error('PDF page could not be drawn'))), 'image/jpeg', 0.9))
    jpegs.push(new Uint8Array(await blob.arrayBuffer()))
  }
  return { pages, jpegs }
}

function draw(ctx: CanvasRenderingContext2D, op: Op) {
  if (op.t === 'rect') { ctx.fillStyle = op.color; ctx.fillRect(op.x, op.y, op.w, op.h); return }
  if (op.t === 'line') {
    ctx.strokeStyle = op.color; ctx.lineWidth = op.width
    ctx.beginPath(); ctx.moveTo(36, op.y); ctx.lineTo(PAGE_W - 36, op.y); ctx.stroke()
    return
  }
  if (!op.text) return
  ctx.font = font(op.paint)
  ctx.fillStyle = op.paint.color
  ctx.textBaseline = 'middle'
  const mid = op.y + (op.paint.size * 1.4) / 2
  if (op.align === 'right') { ctx.textAlign = 'right'; ctx.fillText(op.text, op.x + op.w, mid) }
  else if (op.align === 'center') { ctx.textAlign = 'center'; ctx.fillText(op.text, op.x + op.w / 2, mid) }
  else { ctx.textAlign = 'left'; ctx.fillText(op.text, op.x, mid) }
}
