package com.blw.documentreader.ui

import android.content.Context
import android.net.Uri
import android.print.PrintManager
import android.text.Html
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.view.MotionEvent
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
import kotlin.math.abs

private class GalleryWebView(context: Context) : WebView(context) {
    var previous: (() -> Unit)? = null; var next: (() -> Unit)? = null
    var fitScale = 1f; var gallery = false
    private var x = 0f; private var y = 0f; private var multi = false
    @Suppress("DEPRECATION")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x=event.x; y=event.y; multi=false }
            MotionEvent.ACTION_POINTER_DOWN -> multi=true
            MotionEvent.ACTION_UP -> {
                val dx=event.x-x;val dy=event.y-y
                if(gallery && !multi && scale<=fitScale*1.02f && abs(dx)>64*resources.displayMetrics.density && abs(dx)>abs(dy)*1.5) {
                    val action=if(dx<0)next else previous
                    if(action!=null){post{action()};val cancel=MotionEvent.obtain(event);cancel.action=MotionEvent.ACTION_CANCEL;super.onTouchEvent(cancel);cancel.recycle();return true}
                }
            }
        }
        return super.onTouchEvent(event)
    }
}

/** Plain text only: escape markup, disable JavaScript and block all network loads. */
@Composable
fun TextReaderScreen(uri: Uri, name: String, type: String = "TXT", onBack: () -> Unit, onShare: () -> Unit,
                     autoExport: Boolean = false, onAutoHandled: () -> Unit = {},
                     onBusy: (Boolean) -> Unit, onSaved: (Uri, String) -> Unit,
                     onOpenSaved: (Uri) -> Unit = {}, onShareSaved: (Uri, String) -> Unit = { _, _ -> },
                     onReady: () -> Unit, report: (String) -> Unit,
                     initialBookMode: Boolean = false, onBookTheme: (String?) -> Unit = {}, imagePosition: String? = null, onPrevious: (() -> Unit)? = null, onNext: (() -> Unit)? = null) {
    val context = LocalContext.current
    var bookMode by androidx.compose.runtime.saveable.rememberSaveable(uri.toString()) { mutableStateOf(initialBookMode && !autoExport) }
    if (bookMode && type in setOf("EPUB","TXT")) {
        BookReaderScreen(uri,name,type,context.getSharedPreferences("metadata_only",Context.MODE_PRIVATE).getBoolean("remember",true),
            onBack,onShare,onReady,onDocumentView={bookMode=false},report=report,onBookTheme=onBookTheme)
        return
    }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var sourceHtml by remember(uri) { mutableStateOf("") }
    var notice by remember(uri) { mutableStateOf("") }
    var loaded by remember(uri) { mutableStateOf(false) }
    var showSearch by remember(uri) { mutableStateOf(false) }
    var query by remember(uri) { mutableStateOf("") }
    var zoom by remember(uri) { mutableIntStateOf(100) }
    val previous by rememberUpdatedState(onPrevious); val next by rememberUpdatedState(onNext)
    val web = remember(uri) {
        GalleryWebView(context).apply {
            gallery = type in com.blw.documentreader.data.DocumentFormats.images
            this.previous = { previous?.invoke() }; this.next = { next?.invoke() }
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
                @Suppress("DEPRECATION")
                override fun onPageFinished(view: WebView?, url: String?) { fitScale = scale; loaded = true; onReady() }
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
            if(type in setOf("EPUB","TXT")) IconButton(onClick={bookMode=true}){Icon(Icons.Default.MenuBook,"Book mode")}
            IconButton(onClick = { showSearch = true }, enabled = loaded) { Icon(Icons.Default.Search, "Search text") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share file") }
            DocumentOutputActions(web, name, loaded && error == null, defaultLandscape = type in setOf("CSV","PowerPoint"),
                snapshot = { com.blw.documentreader.printing.PdfSnapshot.local(sourceHtml, type in com.blw.documentreader.data.DocumentFormats.images) },
                autoExport = autoExport, onAutoHandled = onAutoHandled, onBusy = onBusy,
                onSaved = onSaved, onOpenSaved = onOpenSaved, onShareSaved = onShareSaved, report = report)
        }
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { zoom = (zoom - 20).coerceAtLeast(60); if(web.gallery)web.zoomOut() else web.settings.textZoom = zoom }) {
                Icon(Icons.Default.ZoomOut, "Smaller text")
            }
            Text(if(web.gallery) "$type · Pinch to zoom" else "$type · $zoom%", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { zoom = (zoom + 20).coerceAtMost(240); if(web.gallery)web.zoomIn() else web.settings.textZoom = zoom }) {
                Icon(Icons.Default.ZoomIn, "Larger text")
            }
        }
        if(imagePosition!=null)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
            IconButton(enabled=onPrevious!=null,onClick={onPrevious?.invoke()}){Icon(Icons.Default.ChevronLeft,"Previous image")}
            Text(imagePosition,style=MaterialTheme.typography.labelSmall)
            IconButton(enabled=onNext!=null,onClick={onNext?.invoke()}){Icon(Icons.Default.ChevronRight,"Next image")}
        }
        if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal=12.dp,vertical=3.dp), style=MaterialTheme.typography.labelSmall)
        if (error != null) Text(error!!, Modifier.padding(16.dp))
        else {
            if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            key(web) { AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth()) }
        }
    }
    if (showSearch) WReaderAlertDialog(onDismissRequest = { showSearch = false }, title = { WReaderDialogTitle("Find in text") },
        text = { OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search") }) },
        confirmButton = { TextButton(onClick = { web.findAllAsync(query); showSearch = false }) { DialogActionLabel("Find", Icons.Default.Search) } },
        dismissButton = { TextButton(onClick = { web.clearMatches(); showSearch = false }) { DialogActionLabel("Close", Icons.Default.Close) } })
}
