package com.blw.documentreader.printing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Base64
import android.view.ViewGroup
import android.webkit.*
import androidx.webkit.WebViewAssetLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

data class PdfSnapshot(val html: String, val css: String = "", val image: Boolean = false) {
    companion object {
        fun local(html: String, image: Boolean = false): PdfSnapshot {
            val doc = Jsoup.parse(html)
            return PdfSnapshot(doc.body().html(), doc.select("style").joinToString("\n") { it.data() }, image)
        }
    }
}

/** Public Android APIs plus a packaged local JS PDF engine. Never opens PrintManager. */
object DirectPdf {
    private suspend fun js(web: WebView, script: String): String = suspendCancellableCoroutine { c ->
        web.evaluateJavascript(script) { if (c.isActive) c.resume(it ?: "null") }
    }
    suspend fun officeSnapshot(web: WebView): PdfSnapshot = withContext(Dispatchers.Main.immediate) {
        js(web, "window.captureWReaderPdfSnapshot()")
        withTimeout(30000) {
            while (true) {
                val state = JSONObject(js(web, "JSON.stringify({ready:!!window.wReaderSnapshotReady,error:window.wReaderSnapshotError||'',size:(window.wReaderSnapshotHtml||'').length})").let { JSONTokener(it).nextValue().toString() })
                require(state.optString("error").isEmpty()) { state.optString("error") }
                if (state.optBoolean("ready")) {
                    val size = state.getInt("size"); require(size <= 24 * 1024 * 1024) { "Document is too large to export." }
                    val html = StringBuilder()
                    var offset = 0
                    while (offset < size) {
                        html.append(JSONTokener(js(web, "window.wReaderSnapshotHtml.slice($offset,${minOf(size, offset + 32768)})")).nextValue().toString())
                        offset += 32768
                    }
                    val css = web.context.assets.open("office/reader.css").bufferedReader().use { it.readText() }
                    return@withTimeout PdfSnapshot(html.toString(), css)
                }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE") error("No snapshot")
        }
    }
    private fun activity(context: Context): Activity {
        var c = context
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        error("The reader is no longer open.")
    }
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun create(context: Context, snapshot: PdfSnapshot, landscape: Boolean): File {
        val root = File(context.cacheDir, "pdf-exports").also { it.mkdirs() }
        val file = File(root, UUID.randomUUID().toString() + ".pdf")
        try {
            if (snapshot.image) withContext(Dispatchers.IO) { image(snapshot, landscape, file) }
            else html(context, snapshot, landscape, file)
            withContext(Dispatchers.IO) {
                require(file.length() in 8L..64L * 1024 * 1024) { "The PDF could not be created." }
                PDDocument.load(file).use { pdf ->
                    require(pdf.numberOfPages > 0) { "The PDF has no pages." }
                    if (!snapshot.image) {
                        val source = Jsoup.parse(snapshot.html).apply {
                            select("style,script,[hidden],.sheet thead,.sheet .rowhead").remove()
                        }.text()
                        fun compact(text: String) = text.filterNot {
                            it.isWhitespace() || Character.isSpaceChar(it) || Character.getType(it) == Character.FORMAT.toInt()
                        }
                        val expected = compact(source)
                        val actual = compact(PDFTextStripper().getText(pdf))
                        // PDF layout may add list/page labels; every source character must still occur in order.
                        var matched = 0
                        for (ch in actual) if (matched < expected.length && ch == expected[matched]) matched++
                        require(matched == expected.length) {
                            "PDF content could not be preserved completely. Use Print and choose Save as PDF for this document."
                        }
                    }
                }
            }
            return file
        } catch (e: Throwable) { file.delete(); throw e }
    }
    private fun image(snapshot: PdfSnapshot, landscape: Boolean, file: File) {
        val source = Jsoup.parse(snapshot.html).selectFirst("img")?.attr("src").orEmpty()
        require(source.startsWith("data:image/jpeg;base64,")) { "The image is not ready." }
        val bytes = Base64.decode(source.substringAfter(','), Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("The image could not be decoded.")
        val pdf = PdfDocument()
        try {
            val width = if (landscape) 842 else 595; val height = if (landscape) 595 else 842
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            val scale = minOf((width - 58f) / bitmap.width, (height - 58f) / bitmap.height)
            val w = bitmap.width * scale; val h = bitmap.height * scale
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page); file.outputStream().use { pdf.writeTo(it) }
        } finally { bitmap.recycle(); pdf.close() }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun html(context: Context, snapshot: PdfSnapshot, landscape: Boolean, file: File) = withContext(Dispatchers.Main.immediate) {
        val payload = JSONObject().put("html", snapshot.html).put("css", snapshot.css).put("landscape", landscape)
            .put("width", if (landscape) 1047 else 718).toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= 32 * 1024 * 1024) { "Document is too large to export." }
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/export/", WebViewAssetLoader.PathHandler { path ->
                if (path == "current") WebResourceResponse("application/json", "UTF-8", payload.inputStream()) else null
            }).build()
        val parent = activity(context).findViewById<ViewGroup>(android.R.id.content)
        val web = WebView(context).apply {
            alpha = 0f; isFocusable = false
            settings.javaScriptEnabled = true; settings.domStorageEnabled = false
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) = true
                override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse {
                    if (r?.url?.host == "appassets.androidplatform.net") loader.shouldInterceptRequest(r.url)?.let { return it }
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), byteArrayOf().inputStream())
                }
            }
        }
        parent.addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        try {
            web.loadUrl("https://appassets.androidplatform.net/assets/pdf/export.html")
            withTimeout(90000) {
                while (true) {
                    val raw = js(web, "window.wReaderPdf?JSON.stringify({state:window.wReaderPdf.state,error:window.wReaderPdf.error||'',size:window.wReaderPdf.size||0}):null")
                    if (raw != "null") {
                        val state = JSONObject(JSONTokener(raw).nextValue().toString())
                        require(state.optString("state") != "error") { state.optString("error") }
                        if (state.optString("state") == "done") {
                            val size = state.getInt("size"); require(size in 8..64*1024*1024)
                            file.outputStream().use { output ->
                                var offset = 0
                                while (offset < size) {
                                    ensureActive()
                                    val count = minOf(49152, size-offset)
                                    val chunk = JSONTokener(js(web, "window.wReaderPdfChunk($offset,$count)")).nextValue().toString()
                                    val bytes = Base64.decode(chunk, Base64.DEFAULT)
                                    require(bytes.size == count) { "Incomplete PDF transfer." }
                                    withContext(Dispatchers.IO) { output.write(bytes) }; offset += count
                                }
                            }
                            return@withTimeout
                        }
                    }
                    delay(100)
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { web.stopLoading(); parent.removeView(web); web.destroy() }
        }
    }
}
