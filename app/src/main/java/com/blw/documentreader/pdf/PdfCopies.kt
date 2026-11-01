package com.blw.documentreader.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfStamp(val text: String, val color: Int = 0xffa21c27.toInt(), val position: String = "Diagonal",
    val allPages: Boolean = true, val page: Int = 0, val includeDate: Boolean = false)

/** Make independent copies, preserving source bytes and original vector/text page contents. */
object PdfCopies {
    private fun stage(context: Context): File = File(context.cacheDir, "pdf-exports").also { it.mkdirs() }
        .let { File(it, "copy-${UUID.randomUUID()}.pdf") }
    private fun source(context: Context, uri: Uri): File {
        val file = stage(context)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buf = ByteArray(65536); var total = 0L
                while (true) { val n = input.read(buf); if (n < 0) break; total += n
                    require(total <= 64L*1024*1024) { "PDF exceeds the 64 MB editing limit." }; output.write(buf, 0, n) }
            } } ?: error("PDF access expired. Locate the file again.")
            return file
        } catch (ex: Exception) { file.delete(); throw ex }
    }
    suspend fun protect(context: Context, uri: Uri, password: String): File = withContext(Dispatchers.IO) {
        require(password.length in 6..64) { "Use a password between 6 and 64 characters." }
        val input = source(context, uri); val out = stage(context)
        try {
            PDDocument.load(input).use { doc ->
                require(!doc.isEncrypted) { "This PDF is already protected. Share it normally or choose an unprotected copy." }
                val policy = StandardProtectionPolicy(UUID.randomUUID().toString(), password, AccessPermission())
                policy.encryptionKeyLength = 256
                policy.setPreferAES(true)
                doc.protect(policy); doc.save(out)
            }
            out
        } catch (ex: Exception) { out.delete(); throw ex } finally { input.delete() }
    }
    suspend fun stamp(context: Context, uri: Uri, options: PdfStamp): File = withContext(Dispatchers.IO) {
        val text = options.text.trim().replace(Regex("[\\p{Cntrl}]"), " ").take(64) +
            if (options.includeDate) " · " + java.time.LocalDate.now().toString() else ""
        require(text.isNotBlank()) { "Enter stamp text." }
        val input = source(context, uri); val out = stage(context)
        try {
            PDDocument.load(input).use { doc ->
                require(!doc.isEncrypted) { "Stamp an unprotected PDF copy. The protected original is unchanged." }
                require(options.page in 0 until doc.numberOfPages) { "The selected page is no longer available." }
                val font = context.assets.open("pdf/fonts/NotoSans-Bold.ttf").use { PDType0Font.load(doc, it) }
                val measured = font.getStringWidth(text) / 1000f // Validate every glyph before writing anything.
                for (i in 0 until doc.numberOfPages) {
                    if (!options.allPages && i != options.page) continue
                    val page = doc.getPage(i); val box = page.cropBox
                    val w = box.width; val h = box.height
                    val rotation = ((page.rotation % 360) + 360) % 360
                    val dw = if (rotation % 180 == 0) w else h
                    val dh = if (rotation % 180 == 0) h else w
                    val base = when (rotation) {
                        90 -> Matrix(0f, 1f, -1f, 0f, w + box.lowerLeftX, box.lowerLeftY)
                        180 -> Matrix(-1f, 0f, 0f, -1f, w + box.lowerLeftX, h + box.lowerLeftY)
                        270 -> Matrix(0f, -1f, 1f, 0f, box.lowerLeftX, h + box.lowerLeftY)
                        else -> Matrix(1f, 0f, 0f, 1f, box.lowerLeftX, box.lowerLeftY)
                    }
                    val size = minOf(42f, dw * .58f / measured.coerceAtLeast(.1f))
                    val x = when(options.position) { "Left" -> dw*.27f; "Right" -> dw*.73f; else -> dw*.5f }
                    val y = when(options.position) { "Top" -> dh*.82f; "Bottom" -> dh*.16f; else -> dh*.5f }
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        stream.saveGraphicsState(); stream.transform(base)
                        stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = .55f })
                        stream.setNonStrokingColor(android.graphics.Color.red(options.color), android.graphics.Color.green(options.color), android.graphics.Color.blue(options.color))
                        stream.beginText(); stream.setFont(font, size)
                        stream.setTextMatrix(Matrix.getRotateInstance(if(options.position=="Diagonal") Math.toRadians(35.0) else 0.0, x, y))
                        stream.newLineAtOffset(-measured*size/2f, -size/3f); stream.showText(text); stream.endText(); stream.restoreGraphicsState()
                    }
                }
                doc.save(out)
            }
            out
        } catch (ex: Exception) { out.delete(); throw ex } finally { input.delete() }
    }
}
