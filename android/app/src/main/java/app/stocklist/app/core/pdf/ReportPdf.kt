package app.stocklist.app.core.pdf

import app.stocklist.app.core.i18n.tr

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [ReportDoc] as an A4 PDF (595 x 842 points) with Android's own PdfDocument, so nothing leaves the phone.
 *
 * Layout runs first and produces every page as a list of drawing steps; the footer ("Page 3 of 7") is added once
 * the page count is known. Tables repeat their heading on each page; wrapped cells never overlap; a row taller than a
 * page is split at line boundaries and continues under a repeated heading.
 */
object ReportPdf {
    const val PAGE_W = 595f
    const val PAGE_H = 842f
    private const val MARGIN = 36f
    private const val FOOTER_H = 22f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private const val BODY_BOTTOM = PAGE_H - MARGIN - FOOTER_H
    private const val PAD_X = 3.5f
    private const val PAD_Y = 3f

    private val INK = Color.rgb(0x1F, 0x23, 0x28)
    private val MUTED = Color.rgb(0x6B, 0x72, 0x80)
    private val RULE = Color.rgb(0xC9, 0xCE, 0xD6)
    private val HEAD_BG = Color.rgb(0xE8, 0xEC, 0xF1)
    private val WARN_BG = Color.rgb(0xFF, 0xF4, 0xE0)
    private val WARN_LINE = Color.rgb(0xE0, 0x9A, 0x2B)

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private val businessName = paint(15f, bold = true)
    private val businessLine = paint(8.5f, color = MUTED)
    private val titlePaint = paint(13f, bold = true)
    private val subtitlePaint = paint(9f, color = MUTED)
    private val headingPaint = paint(10.5f, bold = true)
    private val labelPaint = paint(8.5f, color = MUTED)
    private val valuePaint = paint(8.5f)
    private val notePaint = paint(8.5f)
    private val cellPaint = paint(8.5f)
    private val cellBold = paint(8.5f, bold = true)
    private val cellMuted = paint(8.5f, color = MUTED)
    private val headPaint = paint(8f, bold = true)
    private val footerPaint = paint(7.5f, color = MUTED)
    private val rulePaint = Paint().apply { color = RULE; strokeWidth = 0.6f }
    private val strongRule = Paint().apply { color = INK; strokeWidth = 0.9f }
    private val fill = Paint()

    /** What ended up on a page; used by tests. */
    data class PageInfo(val tableHeadings: Int, val rows: List<String>)

    data class Result(val pageCount: Int, val pages: List<PageInfo>)

    private class Page {
        val steps = mutableListOf<(Canvas) -> Unit>()
        var headings = 0
        val rows = mutableListOf<String>()
    }

    fun write(doc: ReportDoc, out: OutputStream): Result {
        val pages = layout(doc)
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { i, p ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), i + 1).create())
                val c = page.canvas
                p.steps.forEach { it(c) }
                footer(c, doc, i + 1, pages.size)
                pdf.finishPage(page)
            }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
        return Result(pages.size, pages.map { PageInfo(it.headings, it.rows.toList()) })
    }

    // ---------------------------------------------------------------------------------------------- layout

    private class Cursor(val doc: ReportDoc) {
        val pages = mutableListOf<Page>()
        var y = 0f
        val page get() = pages.last()

        fun newPage() {
            pages += Page()
            y = MARGIN
            if (pages.size == 1) firstHeader() else continuedHeader()
        }

        fun room() = BODY_BOTTOM - y

        fun draw(step: (Canvas) -> Unit) { page.steps += step }

        private fun firstHeader() {
            val name = text(doc.business.name, businessName, CONTENT_W)
            place(name, MARGIN, y); y += name.height + 2
            doc.business.lines.filter { it.isNotBlank() }.forEach { line ->
                val l = text(line, businessLine, CONTENT_W)
                place(l, MARGIN, y); y += l.height
            }
            y += 6
            val ry = y
            draw { it.drawLine(MARGIN, ry, PAGE_W - MARGIN, ry, strongRule) }
            y += 9
            val t = text(doc.title, titlePaint, CONTENT_W)
            place(t, MARGIN, y); y += t.height + 2
            doc.subtitle.filter { it.isNotBlank() }.forEach { s ->
                val l = text(s, subtitlePaint, CONTENT_W)
                place(l, MARGIN, y); y += l.height
            }
            y += 10
        }

        private fun continuedHeader() {
            val l = text(tr("%1\$s  ·  %2\$s (continued)", doc.business.name, doc.title), subtitlePaint, CONTENT_W)
            place(l, MARGIN, y); y += l.height + 4
            val ry = y
            draw { it.drawLine(MARGIN, ry, PAGE_W - MARGIN, ry, rulePaint) }
            y += 8
        }

        fun place(layout: StaticLayout, x: Float, top: Float) {
            draw { c -> c.save(); c.translate(x, top); layout.draw(c); c.restore() }
        }
    }

    private fun layout(doc: ReportDoc): List<Page> {
        val cur = Cursor(doc)
        cur.newPage()
        doc.blocks.forEach { b ->
            when (b) {
                is Heading -> heading(cur, b)
                is Facts -> facts(cur, b)
                is Note -> note(cur, b)
                is Table -> table(cur, b)
            }
        }
        return cur.pages
    }

    private fun text(s: String, p: TextPaint, width: Float, align: Align = Align.LEFT): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, p, max(1, width.toInt()))
            .setAlignment(when (align) {
                Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
                Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            })
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .build()

    private fun heading(cur: Cursor, h: Heading) {
        val l = text(h.text, headingPaint, CONTENT_W)
        // Keep a heading with at least a few lines of what follows.
        if (cur.room() < l.height + 40) cur.newPage()
        cur.y += 4
        cur.place(l, MARGIN, cur.y)
        cur.y += l.height + 5
    }

    private fun facts(cur: Cursor, f: Facts) {
        val items = f.items.filter { !it.second.isNullOrBlank() }
        if (items.isEmpty()) return
        val colW = (CONTENT_W - 16) / 2
        val labelW = 78f
        items.chunked(2).forEach { pair ->
            val cells = pair.map { (label, value) ->
                text(label, labelPaint, labelW) to text(value!!, valuePaint, colW - labelW - 4)
            }
            val h = cells.maxOf { (a, b) -> max(a.height, b.height) }.toFloat() + 3
            if (cur.room() < h) cur.newPage()
            cells.forEachIndexed { i, (a, b) ->
                val x = MARGIN + i * (colW + 16)
                cur.place(a, x, cur.y)
                cur.place(b, x + labelW + 4, cur.y)
            }
            cur.y += h
        }
        cur.y += 6
    }

    private fun note(cur: Cursor, n: Note) {
        val pad = if (n.warning) 6f else 0f
        val l = text(n.text, notePaint, CONTENT_W - 2 * pad)
        val h = l.height + 2 * pad
        if (cur.room() < h) cur.newPage()
        val top = cur.y
        if (n.warning) cur.draw { c ->
            fill.color = WARN_BG; c.drawRect(MARGIN, top, PAGE_W - MARGIN, top + h, fill)
            fill.color = WARN_LINE; c.drawRect(MARGIN, top, MARGIN + 2.5f, top + h, fill)
        }
        cur.place(l, MARGIN + pad, top + pad)
        cur.y += h + 8
    }

    private class Cells(val layouts: List<StaticLayout>, val height: Float)

    private fun table(cur: Cursor, t: Table) {
        val total = t.columns.sumOf { it.weight.toDouble() }.toFloat()
        val widths = t.columns.map { CONTENT_W * it.weight / total }
        val xs = widths.runningFold(MARGIN) { acc, w -> acc + w }

        fun cells(row: List<String>, p: TextPaint): Cells {
            val ls = row.mapIndexed { i, s -> text(s, p, widths[i] - 2 * PAD_X, t.columns[i].align) }
            return Cells(ls, ls.maxOf { it.height }.toFloat() + 2 * PAD_Y)
        }

        val head = cells(t.columns.map { it.title }, headPaint)

        fun drawHeading() {
            val top = cur.y
            cur.draw { c ->
                fill.color = HEAD_BG
                c.drawRect(MARGIN, top, PAGE_W - MARGIN, top + head.height, fill)
            }
            head.layouts.forEachIndexed { i, l -> cur.place(l, xs[i] + PAD_X, top + PAD_Y) }
            cur.y += head.height
            cur.page.headings++
        }

        val body = t.rows.map { r -> r to cells(r.cells, if (r.muted) cellMuted else if (r.bold) cellBold else cellPaint) }
        val firstH = body.firstOrNull()?.second?.height ?: 18f
        if (cur.room() < head.height + min(firstH, 60f)) cur.newPage()
        drawHeading()

        if (body.isEmpty()) {
            val l = text(t.emptyText, cellMuted, CONTENT_W - 2 * PAD_X, Align.CENTER)
            cur.place(l, MARGIN + PAD_X, cur.y + 6)
            cur.y += l.height + 12
            val ry = cur.y
            cur.draw { it.drawLine(MARGIN, ry, PAGE_W - MARGIN, ry, rulePaint) }
        }

        body.forEachIndexed { index, (_, c) -> drawRow(cur, c, xs, "row $index", ::drawHeading, head.height) }

        if (t.totals.isNotEmpty()) {
            val totals = t.totals.map { cells(it.cells, cellBold) }
            if (cur.room() < totals.sumOf { it.height.toDouble() }.toFloat() + 2) { cur.newPage(); drawHeading() }
            val ry = cur.y
            cur.draw { it.drawLine(MARGIN, ry, PAGE_W - MARGIN, ry, strongRule) }
            cur.y += 1
            totals.forEachIndexed { i, c -> drawRow(cur, c, xs, "total $i", ::drawHeading, head.height) }
        }
        cur.y += 10
    }

    private fun drawRow(cur: Cursor, c: Cells, xs: List<Float>, name: String, heading: () -> Unit, headH: Float) {
        if (c.height <= cur.room()) {
            val top = cur.y
            c.layouts.forEachIndexed { i, l -> cur.place(l, xs[i] + PAD_X, top + PAD_Y) }
            cur.y += c.height
            val ry = cur.y
            cur.draw { it.drawLine(MARGIN, ry, PAGE_W - MARGIN, ry, rulePaint) }
            cur.page.rows += name
            return
        }
        val fullPage = BODY_BOTTOM - MARGIN - 40f - headH
        if (c.height <= fullPage) {
            cur.newPage(); heading()
            drawRow(cur, c, xs, name, heading, headH)
            return
        }
        // Taller than a page: draw it in slices that end on whole lines.
        val tallest = c.layouts.maxBy { it.height }
        val lineH = (tallest.getLineBottom(0) - tallest.getLineTop(0)).toFloat().coerceAtLeast(1f)
        var offset = 0f
        var part = 1
        val contentH = c.height - 2 * PAD_Y
        while (offset < contentH) {
            var avail = cur.room() - 2 * PAD_Y
            if (avail < lineH * 3) { cur.newPage(); heading(); avail = cur.room() - 2 * PAD_Y }
            val slice = min(floor(avail / lineH) * lineH, contentH - offset)
            val top = cur.y
            val from = offset
            c.layouts.forEachIndexed { i, l ->
                cur.draw { canvas ->
                    canvas.save()
                    canvas.translate(xs[i] + PAD_X, top + PAD_Y - from)
                    canvas.clipRect(0f, from, xs[i + 1] - xs[i], from + slice)
                    l.draw(canvas)
                    canvas.restore()
                }
            }
            cur.y += slice + 2 * PAD_Y
            val ry = cur.y
            cur.draw { it.drawLine(MARGIN, ry, PAGE_W - MARGIN, ry, rulePaint) }
            cur.page.rows += "$name part $part"
            offset += slice
            part++
            if (offset < contentH) { cur.newPage(); heading() }
        }
    }

    private fun footer(c: Canvas, doc: ReportDoc, n: Int, total: Int) {
        val y = PAGE_H - MARGIN - FOOTER_H + 8
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rulePaint)
        val base = y + 12
        val left = doc.generatedBy?.let { tr("Generated %1\$s by %2\$s", doc.generatedAt, it) } ?: tr("Generated %1\$s", doc.generatedAt)
        c.drawText(left, MARGIN, base, footerPaint)
        val right = tr("Page %1\$s of %2\$s", n, total)
        c.drawText(right, PAGE_W - MARGIN - footerPaint.measureText(right), base, footerPaint)
    }
}
