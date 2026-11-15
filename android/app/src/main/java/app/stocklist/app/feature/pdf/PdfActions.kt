package app.stocklist.app.feature.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import app.stocklist.app.data.repository.PdfFile
import java.io.FileInputStream
import java.io.FileOutputStream

/** Sharing, printing and saving a PDF that is already written to cache/pdfs. */
object PdfActions {
    fun authority(context: Context) = context.packageName + ".files"

    fun uri(context: Context, pdf: PdfFile): Uri = FileProvider.getUriForFile(context, authority(context), pdf.file, pdf.fileName)

    /**
     * The share sheet with the real PDF attached. The receiving app (WhatsApp, Gmail, Drive...) gets read access to
     * this one file only, through the ClipData grant; EXTRA_SUBJECT becomes the email subject.
     */
    fun shareIntent(context: Context, pdf: PdfFile, businessName: String? = null): Intent {
        val uri = uri(context, pdf)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, listOfNotNull(pdf.title, businessName).joinToString(" - "))
            putExtra(Intent.EXTRA_TITLE, pdf.fileName)
            clipData = ClipData.newRawUri(pdf.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share PDF").apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** Android's own print dialog (printers, or "Save as PDF"), A4 by default. */
    fun print(context: Context, pdf: PdfFile) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(pdf.fileName.removeSuffix(".pdf"), FileAdapter(pdf),
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    /** Copies the PDF to where the user chose in the system "Save to" picker (Storage Access Framework). */
    fun saveTo(context: Context, pdf: PdfFile, target: Uri) {
        val out = context.contentResolver.openOutputStream(target, "wt") ?: error("Could not open the chosen file")
        out.use { o -> pdf.file.inputStream().use { it.copyTo(o) } }
    }

    private class FileAdapter(private val pdf: PdfFile) : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?,
                              callback: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder(pdf.fileName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pdf.pageCount).build(), old != new)
        }

        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal?,
                             callback: WriteResultCallback) {
            try {
                FileInputStream(pdf.file).use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                if (cancel?.isCanceled == true) callback.onWriteCancelled() else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
