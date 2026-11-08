package com.blw.documentreader.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.PathHandler
import com.blw.documentreader.data.DocumentTypes
import android.content.Context
import java.io.InputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real offline document-aware Office rendering:
 * DOCX -> Apache-2 docx-preview (paragraphs, Word runs, images, tables, headers).
 * XLSX -> MIT ExcelJS workbook model -> sheet-aware styled cell grid.
 * JavaScript and documents never leave the device. See OFFICE_ENGINE.md.
 * HTML does not reproduce all proprietary desktop Office pagination features.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficeReaderScreen(
    uri: Uri,
    name: String,
    type: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    autoExport: Boolean = false,
    onAutoHandled: () -> Unit = {},
    onBusy: (Boolean) -> Unit,
    onSaved: (Uri, String) -> Unit,
    onOpenSaved: (Uri) -> Unit = {}, onShareSaved: (Uri, String) -> Unit = { _, _ -> },
    onSaveLocal: (() -> Unit)? = null,
    onReady: () -> Unit,
    report: (String) -> Unit
) {
    val context = LocalContext.current
    val webView = remember(uri) { OfficeWebView.create(context, uri, type) }
    var ready by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(webView) {
        repeat(120) {
            val loaded = suspendCancellableCoroutine<Boolean> { cont ->
                webView.evaluateJavascript("window.officeReady === true") { if (cont.isActive) cont.resume(it == "true") }
            }
            if (loaded) {
                // DOM readiness can precede the AndroidView's first painted frame.
                // Enable output only after WebView has committed the document visually.
                suspendCancellableCoroutine<Unit> { cont ->
                    webView.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            webView.invalidate()
                            if (cont.isActive) cont.resume(Unit)
                        }
                    })
                }
                ready = true; onReady(); return@LaunchedEffect
            }
            delay(500)
        }
    }
    var searchText by remember(uri) { mutableStateOf("") }
    var showSearch by remember(uri) { mutableStateOf(false) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    LaunchedEffect(webView, ready) {
        if (ready) while (true) {
            webView.evaluateJavascript("window.getOfficeZoom && window.getOfficeZoom()") { value ->
                value.toFloatOrNull()?.let { scale = it }
            }
            delay(200)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                Text(if (type == DocumentTypes.WORD) "Word · Offline viewing" else "Excel · Offline viewing",
                    style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, "Search") }
            ReaderShareMenu(onShare, onSaveLocal)
            DocumentOutputActions(webView, name, ready, excel = type == DocumentTypes.EXCEL,
                snapshot = { com.blw.documentreader.printing.DirectPdf.officeSnapshot(webView) },
                prepare = { landscape, done ->
                    webView.evaluateJavascript("window.prepareOfficePrint && window.prepareOfficePrint($landscape)") { result ->
                        done(if (result == "null") null else try { org.json.JSONTokener(result).nextValue().toString() } catch (_: Exception) { "Document is not ready to print." })
                    }
                }, autoExport = autoExport, onAutoHandled = onAutoHandled,
                onBusy = onBusy, onSaved = onSaved, onOpenSaved = onOpenSaved, onShareSaved = onShareSaved, report = report)
        }
        Row(Modifier.fillMaxWidth().height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                webView.evaluateJavascript("window.changeOfficeZoom && window.changeOfficeZoom(-0.1)", null)
            }) { Icon(Icons.Default.ZoomOut, "Zoom out") }
            Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = {
                webView.evaluateJavascript("window.changeOfficeZoom && window.changeOfficeZoom(0.1)", null)
            }) { Icon(Icons.Default.ZoomIn, "Zoom in") }
            TextButton(onClick = { webView.evaluateJavascript("window.fitOfficePage && window.fitOfficePage()", null) }) {
                Icon(Icons.Default.FitScreen, "Fit to width", Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Fit")
            }
        }
        AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
    }
    if (showSearch) WReaderAlertDialog(
        onDismissRequest = { showSearch = false },
        title = { WReaderDialogTitle("Find in document") },
        text = {
            OutlinedTextField(value = searchText, onValueChange = { searchText = it },
                label = { Text("Search text or cell values") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                webView.findAllAsync(searchText)
                showSearch = false
            }) { DialogActionLabel("Find", Icons.Default.Search) }
        },
        dismissButton = {
            TextButton(onClick = { webView.clearMatches(); showSearch = false }) { DialogActionLabel("Close", Icons.Default.Close) }
        }
    )
}
