package app.stocklist.app.feature.pdf

import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.pdf.ReportPdf
import app.stocklist.app.data.repository.PdfException
import app.stocklist.app.data.repository.PdfFile
import app.stocklist.app.data.repository.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val pdf: PdfFile) : PdfState
    data class Failed(val message: String) : PdfState
}

@HiltViewModel
class PdfViewModel @Inject constructor(private val repo: PdfRepository, handle: SavedStateHandle) : ViewModel() {
    private val job = PdfJob.parse(handle["job"], handle["id"], handle["from"], handle["to"], handle["filter"])
    val state = MutableStateFlow<PdfState>(PdfState.Loading)
    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private val lock = Mutex()

    init { load() }

    fun load() {
        state.value = PdfState.Loading
        viewModelScope.launch {
            state.value = try {
                val j = job ?: throw PdfException(tr("This PDF link is not valid."))
                val pdf = repo.make(j)
                lock.withLock {
                    renderer?.close(); fd?.close()
                    fd = ParcelFileDescriptor.open(pdf.file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(fd!!)
                }
                PdfState.Ready(pdf)
            } catch (e: PdfException) {
                PdfState.Failed(e.message ?: tr("Could not make the PDF."))
            } catch (e: Exception) {
                PdfState.Failed(tr("Could not make the PDF: %1\$s", e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** One page as a bitmap [widthPx] wide (the preview is exactly what is printed and shared). */
    suspend fun page(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val r = renderer ?: return@withLock null
            if (index >= r.pageCount) return@withLock null
            r.openPage(index).use { p ->
                val w = widthPx.coerceIn(300, 1600)
                val h = (w * p.height.toFloat() / p.width).toInt()
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    override fun onCleared() {
        renderer?.close(); fd?.close()
    }
}

/** Preview of the PDF with Save (to a place the user picks), Print and Share at the top right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(canShare: Boolean, onBack: () -> Unit, vm: PdfViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ready = (state as? PdfState.Ready)?.pdf
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && ready != null) scope.launch {
            val msg = runCatching { withContext(Dispatchers.IO) { PdfActions.saveTo(context, ready, uri) } }
                .fold({ tr("Saved %1\$s", ready.fileName) }, { tr("Could not save: %1\$s", it.message) })
            snackbar.showSnackbar(Bilingual.twoLines(msg))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(ready?.title ?: "PDF") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } },
                actions = {
                    if (ready != null && canShare) {
                        IconButton(onClick = { save.launch(ready.fileName) }) { Icon(Icons.Filled.Save, Bilingual.oneLine(tr("Save to phone"))) }
                        IconButton(onClick = { PdfActions.print(context, ready) }) { Icon(Icons.Filled.Print, Bilingual.oneLine(tr("Print"))) }
                        IconButton(onClick = { context.startActivity(PdfActions.shareIntent(context, ready)) }) {
                            Icon(Icons.Filled.Share, Bilingual.oneLine(tr("Share")))
                        }
                    }
                })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val s = state) {
            PdfState.Loading -> Column(Modifier.padding(padding)) { LoadingState() }
            is PdfState.Failed -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyState(s.message)
                OutlinedButton(onClick = vm::load) { Text(tr("Try again")) }
            }
            is PdfState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(tr("%1\$s  ·  %2\$s page(s)", s.pdf.fileName, s.pdf.pageCount) +
                        if (!canShare) tr("  ·  you can view this PDF but not save, print or share it") else "",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(s.pdf.pageCount) { i -> PdfPage(vm, s.pdf, i) }
            }
        }
    }
}

@Composable
private fun PdfPage(vm: PdfViewModel, pdf: PdfFile, index: Int) {
    val bmp by produceState<Bitmap?>(null, pdf.file, pdf.file.lastModified(), index) { value = vm.page(index, 1240) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(ReportPdf.PAGE_W / ReportPdf.PAGE_H)
        .background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) LoadingState()
        else Image(b.asImageBitmap(), tr("Page %1\$s of %2\$s", index + 1, pdf.pageCount), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
