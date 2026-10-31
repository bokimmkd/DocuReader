package com.blw.documentreader.ui

import android.content.Context
import android.net.Uri
import android.print.PrintManager
import android.text.Html
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import com.blw.documentreader.data.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plain text only: escape markup, disable JavaScript and block all network loads. */
@Composable
fun TextReaderScreen(uri: Uri, name: String, type: String = "TXT", onBack: () -> Unit, onShare: () -> Unit,
                     autoExport: Boolean = false, onAutoHandled: () -> Unit = {},
                     onBusy: (Boolean) -> Unit, onSaved: (Uri, String) -> Unit,
                     onOpenSaved: (Uri) -> Unit = {}, onShareSaved: (Uri, String) -> Unit = { _, _ -> },
                     onReady: () -> Unit, report: (String) -> Unit) {
    val context = LocalContext.current
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var sourceHtml by remember(uri) { mutableStateOf("") }
    var notice by remember(uri) { mutableStateOf("") }
    var loaded by remember(uri) { mutableStateOf(false) }
    var showSearch by remember(uri) { mutableStateOf(false) }
    var query by remember(uri) { mutableStateOf("") }
    var zoom by remember(uri) { mutableIntStateOf(100) }
    val web = remember(uri) {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                    if (request?.url?.scheme == "data") null else WebResourceResponse("text/plain", "UTF-8", byteArrayOf().inputStream())
                override fun onPageFinished(view: WebView?, url: String?) { loaded = true; onReady() }
            }
        }
    }
    DisposableEffect(web) { onDispose { web.stopLoading(); web.destroy() } }
    LaunchedEffect(uri) {
        try {
            val page = withContext(Dispatchers.IO) {
                if (type in com.blw.documentreader.data.DocumentFormats.images) com.blw.documentreader.formats.ImageDocument.read(context,uri)
                else context.contentResolver.openInputStream(uri)?.use { input ->
                    if(type == "TXT") com.blw.documentreader.formats.LocalFormats.Page(com.blw.documentreader.printing.TextHtml.document(TextContent.read(input)))
                    else com.blw.documentreader.formats.LocalFormats.read(type,input)
                } ?: error("This file is no longer accessible. Locate it again.")
            }
            notice = page.notice
            val html = page.html
            sourceHtml = html
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
        } catch (ex: Exception) { error = ex.message ?: "Couldn't open this document." }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(name, maxLines = 1, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { showSearch = true }, enabled = loaded) { Icon(Icons.Default.Search, "Search text") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share file") }
            DocumentOutputActions(web, name, loaded && error == null, defaultLandscape = type in setOf("CSV","PowerPoint"),
                snapshot = { com.blw.documentreader.printing.PdfSnapshot.local(sourceHtml, type in com.blw.documentreader.data.DocumentFormats.images) },
                autoExport = autoExport, onAutoHandled = onAutoHandled, onBusy = onBusy,
                onSaved = onSaved, onOpenSaved = onOpenSaved, onShareSaved = onShareSaved, report = report)
        }
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { zoom = (zoom - 20).coerceAtLeast(60); web.settings.textZoom = zoom }) {
                Icon(Icons.Default.ZoomOut, "Smaller text")
            }
            Text("$type · $zoom%", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { zoom = (zoom + 20).coerceAtMost(240); web.settings.textZoom = zoom }) {
                Icon(Icons.Default.ZoomIn, "Larger text")
            }
        }
        if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal=12.dp,vertical=3.dp), style=MaterialTheme.typography.labelSmall)
        if (error != null) Text(error!!, Modifier.padding(16.dp))
        else {
            if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
    if (showSearch) AlertDialog(onDismissRequest = { showSearch = false }, title = { Text("Find in text") },
        text = { OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search") }) },
        confirmButton = { TextButton(onClick = { web.findAllAsync(query); showSearch = false }) { Text("Find") } },
        dismissButton = { TextButton(onClick = { web.clearMatches(); showSearch = false }) { Text("Close") } })
}
