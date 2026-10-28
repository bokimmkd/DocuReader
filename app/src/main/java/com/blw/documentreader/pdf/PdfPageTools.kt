package com.blw.documentreader.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException

/** A page references a snapshot of its original PDF; actual bytes are only written at Save. */
data class ToolPage(val file: File, val originalIndex: Int, val rotation: Int = 0)
data class SourcePdf(val file: File, val name: String, val count: Int)

class PdfPageTools(private val context: Context) : Closeable {
    private val sources = mutableListOf<SourcePdf>()

    suspend fun importSource(uri: Uri, name: String): SourcePdf = withContext(Dispatchers.IO) {
        val file = copyUriToCache(context, uri)
        try {
            val count = PDDocument.load(file).use { it.numberOfPages }
            require(count > 0) { "The selected PDF has no pages" }
            SourcePdf(file, name, count).also { synchronized(sources) { sources += it } }
        } catch (e: Exception) {
            file.delete(); throw e
        }
    }

    /** PDFBox page cloning may not preserve interactive forms, outlines, tags or signatures.
     *  UI must obtain explicit informed confirmation before calling save(). */
    suspend fun save(pages: List<ToolPage>, destination: Uri) = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "Cannot save an empty PDF" }
        val outputFile = File.createTempFile("blw_export_", ".pdf", context.cacheDir)
        try {
            PDDocument().use { output ->
                val openSources = mutableMapOf<File, PDDocument>()
                try {
                    for (item in pages) {
                        val original = openSources.getOrPut(item.file) { PDDocument.load(item.file) }
                        val sourcePage = original.getPage(item.originalIndex)
                        val copy = output.importPage(sourcePage)
                        copy.rotation = ((sourcePage.rotation + item.rotation) % 360 + 360) % 360
                    }
                    output.save(outputFile)
                } finally {
                    openSources.values.forEach { it.close() }
                }
            }
            context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                outputFile.inputStream().use { it.copyTo(output, 64 * 1024) }
            } ?: throw IOException("Couldn't create the selected output file")
        } finally {
            outputFile.delete()
        }
    }

    override fun close() {
        synchronized(sources) { sources.forEach { it.file.delete() }; sources.clear() }
    }
}
