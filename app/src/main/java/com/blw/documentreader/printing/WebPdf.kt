package com.blw.documentreader.printing

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses Chromium's print layout, never a screenshot or the visible zoom level. */
object WebPdf {
    fun attributes(landscape: Boolean) = PrintAttributes.Builder()
        .setMediaSize(if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
        .setMinMargins(PrintAttributes.Margins(394, 394, 394, 394))
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build()

    fun print(web: WebView, name: String, landscape: Boolean, finished: () -> Unit) {
        val zoom = web.settings.textZoom
        web.settings.textZoom = 100
        val delegate = web.createPrintDocumentAdapter(name)
        val wrapper = object : PrintDocumentAdapter() {
            override fun onStart() { delegate.onStart() }
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, signal: CancellationSignal?, result: LayoutResultCallback?, extras: Bundle?) {
                delegate.onLayout(old, new, signal, result, extras)
            }
            override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor?, signal: CancellationSignal?, result: WriteResultCallback?) {
                delegate.onWrite(pages, destination, signal, result)
            }
            override fun onFinish() {
                delegate.onFinish(); web.settings.textZoom = zoom; finished()
            }
        }
        try {
            (web.context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager).print(name, wrapper, attributes(landscape))
        } catch (error: Exception) { web.settings.textZoom = zoom; finished(); throw error }
    }
}
