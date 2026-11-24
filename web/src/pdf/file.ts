import { PAGE_H, PAGE_W } from './layout'

/**
 * A PDF file with one full-page JPEG per page. Small and dependency-free; opens in every viewer, prints and shares like
 * any PDF. (The text is part of the picture, so it cannot be selected or searched.)
 */
export function buildPdf(jpegs: Uint8Array[], widthPx: number, heightPx: number): Uint8Array {
  const enc = new TextEncoder()
  const chunks: Uint8Array[] = []
  const offsets: number[] = []
  let length = 0
  const push = (b: Uint8Array | string) => { const u = typeof b === 'string' ? enc.encode(b) : b; chunks.push(u); length += u.length }
  const object = (n: number, body: Array<Uint8Array | string>) => {
    offsets[n] = length
    push(`${n} 0 obj\n`); body.forEach(push); push('\nendobj\n')
  }

  push('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n')
  const count = jpegs.length
  // objects: 1 catalog, 2 pages, then per page: image, contents, page
  object(1, ['<< /Type /Catalog /Pages 2 0 R >>'])
  const kids = jpegs.map((_, i) => `${5 + i * 3} 0 R`).join(' ')
  object(2, [`<< /Type /Pages /Count ${count} /Kids [${kids}] >>`])
  jpegs.forEach((jpg, i) => {
    const img = 3 + i * 3
    const content = 4 + i * 3
    const page = 5 + i * 3
    object(img, [`<< /Type /XObject /Subtype /Image /Width ${widthPx} /Height ${heightPx} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpg.length} >>\nstream\n`, jpg, '\nendstream'])
    const draw = `q ${PAGE_W} 0 0 ${PAGE_H} 0 0 cm /Im0 Do Q`
    object(content, [`<< /Length ${draw.length} >>\nstream\n${draw}\nendstream`])
    object(page, [`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${PAGE_W} ${PAGE_H}] /Resources << /XObject << /Im0 ${img} 0 R >> >> /Contents ${content} 0 R >>`])
  })
  const objects = 2 + count * 3
  const xref = length
  push(`xref\n0 ${objects + 1}\n0000000000 65535 f \n`)
  for (let n = 1; n <= objects; n++) push(`${String(offsets[n]).padStart(10, '0')} 00000 n \n`)
  push(`trailer\n<< /Size ${objects + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`)

  const out = new Uint8Array(length)
  let at = 0
  for (const c of chunks) { out.set(c, at); at += c.length }
  return out
}
