package app.stocklist.app

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.stocklist.app.core.pdf.ReportPdf
import app.stocklist.app.data.repository.PdfFile
import app.stocklist.app.data.repository.PdfRepository
import app.stocklist.app.feature.pdf.PdfActions
import app.stocklist.app.feature.pdf.PdfDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The share sheet gets the real PDF as a content:// link that the receiving app is allowed to read. */
@RunWith(AndroidJUnit4::class)
class PdfShareTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun pdf(): PdfFile {
        val doc = PdfDocuments.stockList(PdfFixtures.ctx(), PdfFixtures.list(), listOf(PdfFixtures.listLine(0, "Rice", "2", "50", "100.00")),
            null, mapOf("u-kg" to "kg"))
        val dir = File(context.cacheDir, PdfRepository.PDF_DIR).apply { mkdirs() }
        val f = File(dir, doc.fileName)
        val r = f.outputStream().use { ReportPdf.write(doc, it) }
        return PdfFile(f, doc.title, doc.fileName, r.pageCount)
    }

    @Test
    fun shareIntentCarriesAReadableContentUri() {
        val p = pdf()
        val chooser = PdfActions.shareIntent(context, p, "Sharma General Store")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".files", uri.authority)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertEquals("Out-of-stock list - Sharma General Store", send.getStringExtra(Intent.EXTRA_SUBJECT))

        // What the receiving app will see: the right name, type and bytes.
        assertEquals("application/pdf", context.contentResolver.getType(uri))
        context.contentResolver.query(uri, null, null, null, null)!!.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(p.fileName, c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)))
            assertEquals(p.file.length(), c.getLong(c.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE)))
        }
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4)))
    }

    @Test
    fun onlyThePdfFolderCanBeShared() {
        val secret = File(context.filesDir, "secret.txt").apply { writeText("x") }
        val failed = runCatching { androidx.core.content.FileProvider.getUriForFile(context, PdfActions.authority(context), secret) }
        assertTrue("files outside cache/pdfs are refused", failed.isFailure)
        assertNotNull(PdfActions.uri(context, pdf()))
    }

    @Test
    fun savingCopiesTheBytes() {
        val p = pdf()
        val target = File(context.cacheDir, "saved-copy.pdf").apply { delete(); createNewFile() }
        PdfActions.saveTo(context, p, android.net.Uri.fromFile(target))
        assertTrue(target.readBytes().contentEquals(p.file.readBytes()))
    }
}
