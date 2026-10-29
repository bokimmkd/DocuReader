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
    onPrintOriginal: () -> Unit
) {
    val context = LocalContext.current
    val webView = remember(uri) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }
    }
    var searchText by remember(uri) { mutableStateOf("") }
    var showSearch by remember(uri) { mutableStateOf(false) }
    var scale by remember(uri) { mutableFloatStateOf(0.5f) }
    DisposableEffect(webView) {
        val loader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/document/", PathHandler { relative ->
                if (relative != "current") null else {
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                        if (input == null) null
                        else WebResourceResponse("application/octet-stream", null, input)
                    } catch (_: Exception) {
                        // Missing URI grant is displayed by the document renderer.
                        null
                    }
                }
            })
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val requestUri = request?.url ?: return null
                if (requestUri.host != "appassets.androidplatform.net") {
                    // Do not make network requests from document content.
                    return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return loader.shouldInterceptRequest(requestUri)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return request?.url?.host != "appassets.androidplatform.net"
            }
        }
        webView.loadUrl(
            "https://appassets.androidplatform.net/assets/office/reader.html?type=" +
                if (type == DocumentTypes.WORD) "word" else "excel"
        )
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
            IconButton(onClick = {
                val adapter = webView.createPrintDocumentAdapter(name)
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                printManager.print(name, adapter, null)
            }) { Icon(Icons.Default.Print, "Print viewed document") }
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
