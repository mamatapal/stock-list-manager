package app.stocklist.app.core.pdf

/** A document to print, independent of how it is drawn. Built by [PdfDocuments], drawn by [ReportPdf]. */
data class ReportDoc(
    val business: BusinessInfo,
    val title: String,
    val subtitle: List<String> = emptyList(),
    val blocks: List<Block>,
    val generatedBy: String?,
    val generatedAt: String,
    val fileName: String,
)

data class BusinessInfo(val name: String, val lines: List<String> = emptyList())

enum class Align { LEFT, CENTER, RIGHT }

data class Column(val title: String, val weight: Float, val align: Align = Align.LEFT)

data class Row(val cells: List<String>, val muted: Boolean = false, val bold: Boolean = false)

sealed interface Block

/** Section heading. */
data class Heading(val text: String) : Block

/** Label/value pairs in two columns (blank values are left out). */
data class Facts(val items: List<Pair<String, String?>>) : Block

/** A paragraph; [warning] draws it boxed, used for "expected, not realised" and "cancelled" notices. */
data class Note(val text: String, val warning: Boolean = false) : Block

/**
 * A table whose heading is repeated on every page it continues to. Long cell text wraps; a row too tall for one
 * page continues on the next. [totals] rows are drawn bold after a rule.
 */
data class Table(
    val columns: List<Column>,
    val rows: List<Row>,
    val totals: List<Row> = emptyList(),
    val emptyText: String = "No records",
) : Block {
    init {
        (rows + totals).forEach { require(it.cells.size == columns.size) { "Row has ${it.cells.size} cells for ${columns.size} columns" } }
    }
}
