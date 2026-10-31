package com.blw.documentreader.pdf

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.PageRange

object PdfPrint {
    fun print(context: Context, uri: Uri, fileName: String, finished: () -> Unit = {}) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("W Reader - $fileName", object : PrintDocumentAdapter() {
            override fun onFinish() { finished() }
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(PrintDocumentInfo.Builder(fileName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error("File permission expired")
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (_: Exception) { callback.onWriteFailed("File unavailable. Locate it again.") }
            }
        }, null)
    }
}
