package com.blw.documentreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.min

/** Seekable local snapshot, needed because many mail/messaging ContentProviders supply streams. */
fun copyUriToCache(context: Context, uri: Uri): File {
    val file = File(context.cacheDir, "pdf_${UUID.randomUUID()}.pdf")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        } ?: throw IOException("This file is no longer accessible. Locate it again.")
        return file
    } catch (ex: Exception) {
        file.delete()
        throw ex
    }
}

class PdfSession private constructor(private val snapshot: File, private val renderer: PdfRenderer,
                                   private val tempEncrypted: File?) : Closeable {
    private val lock = Mutex()
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(pageIndex: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            require(pageIndex in 0 until renderer.pageCount)
            renderer.openPage(pageIndex).use { page ->
                val width = targetWidth.coerceIn(200, 1800)
                val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                // Prevent an enormous page from exhausting memory.
                val factor = min(1f, kotlin.math.sqrt(3_000_000f / (width.toFloat() * height)))
                val bitmap = Bitmap.createBitmap((width * factor).toInt().coerceAtLeast(1),
                    (height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    suspend fun search(query: String): List<Int> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        lock.withLock {
            val matches = ArrayList<Int>()
            PDDocument.load(snapshot).use { doc ->
                val extractor = PDFTextStripper()
                for (i in 1..doc.numberOfPages) {
                    extractor.startPage = i
                    extractor.endPage = i
                    if (extractor.getText(doc).contains(query, ignoreCase = true)) matches.add(i - 1)
                }
            }
            matches
        }
    }

    override fun close() {
        renderer.close()
        snapshot.delete()
        tempEncrypted?.delete()
    }

    companion object {
        suspend fun open(context: Context, uri: Uri, password: String? = null): PdfSession = withContext(Dispatchers.IO) {
            val original = copyUriToCache(context, uri)
            var decrypted: File? = null
            try {
                val renderingFile = if (password != null) {
                    // Old Android versions cannot pass passwords to PdfRenderer.
                    // The unencrypted copy exists ONLY in app-private cache and is deleted on close.
                    val clearFile = File(context.cacheDir, "unlocked_${UUID.randomUUID()}.pdf")
                    decrypted = clearFile
                    PDDocument.load(original, password).use { doc ->
                        doc.setAllSecurityToBeRemoved(true)
                        doc.save(clearFile)
                    }
                    clearFile
                } else original
                val fd = ParcelFileDescriptor.open(renderingFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
                // Search the same decrypted bytes that are visible to the reader.
                val searchable = if (decrypted != null) decrypted!! else original
                return@withContext PdfSession(searchable, renderer,
                    if (decrypted != null) original else null)
            } catch (ex: Exception) {
                original.delete(); decrypted?.delete(); throw ex
            }
        }
    }
}

/** Small preview with its own short-lived renderer and no long-lived file descriptors. */
suspend fun thumbnail(file: File, index: Int): Bitmap = withContext(Dispatchers.IO) {
    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(fd).use { renderer ->
        renderer.openPage(index).use { page ->
            val width = 136
            val height = (width * page.height.toFloat() / page.width).toInt().coerceIn(60, 230)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }
}
