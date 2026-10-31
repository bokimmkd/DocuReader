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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
            if (loaded) { ready = true; onReady(); return@LaunchedEffect }
            delay(500)
        }
    }
    var searchText by remember(uri) { mutableStateOf("") }
    var showSearch by remember(uri) { mutableStateOf(false) }
    var scale by remember(uri) { mutableFloatStateOf(0.5f) }
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
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share original") }
            DocumentOutputActions(webView, name, ready, excel = type == DocumentTypes.EXCEL,
                prepare = { landscape, done ->
                    webView.evaluateJavascript("window.prepareOfficePrint && window.prepareOfficePrint($landscape)") { result ->
                        done(if (result == "null") null else try { org.json.JSONTokener(result).nextValue().toString() } catch (_: Exception) { "Document is not ready to print." })
                    }
                }, autoExport = autoExport, onAutoHandled = onAutoHandled,
                onBusy = onBusy, onSaved = onSaved, report = report)
        }
        Row(Modifier.fillMaxWidth().height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                scale = (scale - .10f).coerceIn(.25f, 2.5f)
                webView.evaluateJavascript("window.setOfficeZoom && window.setOfficeZoom($scale)", null)
            }) { Icon(Icons.Default.ZoomOut, "Zoom out") }
            Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = {
                scale = (scale + .10f).coerceIn(.25f, 2.5f)
                webView.evaluateJavascript("window.setOfficeZoom && window.setOfficeZoom($scale)", null)
            }) { Icon(Icons.Default.ZoomIn, "Zoom in") }
            Text("Layout may differ from Microsoft Office", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 10.dp))
        }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
    if (showSearch) AlertDialog(
        onDismissRequest = { showSearch = false },
        title = { Text("Find in document") },
        text = {
            OutlinedTextField(value = searchText, onValueChange = { searchText = it },
                label = { Text("Search text or cell values") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                webView.findAllAsync(searchText)
                showSearch = false
            }) { Text("Find") }
        },
        dismissButton = {
            TextButton(onClick = { webView.clearMatches(); showSearch = false }) { Text("Close") }
        }
    )
}
