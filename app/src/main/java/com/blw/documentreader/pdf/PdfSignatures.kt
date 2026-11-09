package com.blw.documentreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Coordinates are fractions of the displayed, cropped, rotated page, from its top-left. */
data class SignaturePlacement(val left: Float = .55f, val top: Float = .75f, val width: Float = .3f) {
    fun bounded(aspect: Float, pageAspect: Float): SignaturePlacement {
        val maxWidth = minOf(.8f, aspect / pageAspect * .8f)
        val w = width.coerceIn(minOf(.08f, maxWidth), maxWidth)
        val h = w * pageAspect / aspect
        return copy(left = left.coerceIn(0f, 1f-w), top = top.coerceIn(0f, 1f-h), width = w)
    }
}

/** Private and excluded from backups and FileProvider roots. No upload path. */
object SignatureStore {
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "my-signature.png"))
    fun load(context: Context): Bitmap? = runCatching { file(context).openRead().use { BitmapFactory.decodeStream(it) } }.getOrNull()
    fun save(context: Context, bitmap: Bitmap) {
        val file = file(context); val out = file.startWrite()
        try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun delete(context: Context) = file(context).delete()
}

/** Decode a user-selected PNG/JPEG without tinting, cropping or removing its background. */
object SignatureImages {
    fun load(context: Context, uri: Uri): Bitmap {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            var total = 0
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                total += count
                require(total <= 16 * 1024 * 1024) { "Choose a PNG or JPG smaller than 16 MB." }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: error("Image unavailable. Choose it again.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType in listOf("image/png", "image/jpeg")) { "Choose a PNG or JPG image." }
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 8_000_000) { "Choose an image up to 4096 pixels per side and 8 megapixels." }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false })
            ?: error("This image could not be opened.")
    }
    fun export(context: Context, uri: Uri, bitmap: Bitmap) {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not save signature PNG." }
        } ?: error("Destination unavailable")
    }
}

data class SignaturePage(val bitmap: Bitmap, val count: Int)

object PdfSignatures {
    suspend fun preview(context: Context, uri: Uri, page: Int): SignaturePage = withContext(Dispatchers.IO) {
        val input = copySource(context, uri)
        try {
            PdfRenderer(ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                require(page in 0 until renderer.pageCount) { "Choose an available page." }
                renderer.openPage(page).use { p ->
                    val scale = minOf(1200f/p.width, 1600f/p.height)
                    val bitmap = Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1), (p.height*scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    SignaturePage(bitmap, renderer.pageCount)
                }
            }
        } finally { input.delete() }
    }
    private fun stage(context: Context) = File(File(context.cacheDir, "pdf-exports").also { it.mkdirs() }, "signature-${UUID.randomUUID()}.pdf")
    private fun copySource(context: Context, uri: Uri): File {
        val input = stage(context)
        try {
            context.contentResolver.openInputStream(uri)?.use { source -> input.outputStream().use { out ->
                val buf = ByteArray(65536); var total = 0L
                while (true) { val n = source.read(buf); if (n < 0) break; total += n
                    require(total <= 64L*1024*1024) { "PDF exceeds the 64 MB editing limit." }; out.write(buf,0,n) }
            } } ?: error("PDF access expired. Locate the file again.")
            return input
        } catch(e: Exception) { input.delete(); throw e }
    }
    suspend fun sign(context: Context, uri: Uri, pageIndex: Int, signature: Bitmap, placement: SignaturePlacement): File = withContext(Dispatchers.IO) {
        val input = copySource(context, uri); val output = stage(context)
        try {
            PDDocument.load(input).use { doc ->
                require(!doc.isEncrypted) { "Choose an unprotected PDF copy." }
                require(pageIndex in 0 until doc.numberOfPages) { "Choose an available page." }
                val page = doc.getPage(pageIndex); val box = page.cropBox
                val w = box.width; val h = box.height
                val rotation = ((page.rotation % 360)+360)%360
                require(rotation in listOf(0,90,180,270)) { "Unsupported page rotation." }
                val dw = if(rotation%180==0) w else h; val dh = if(rotation%180==0) h else w
                val base = when(rotation) {
                    90 -> Matrix(0f,1f,-1f,0f,w+box.lowerLeftX,box.lowerLeftY)
                    180 -> Matrix(-1f,0f,0f,-1f,w+box.lowerLeftX,h+box.lowerLeftY)
                    270 -> Matrix(0f,-1f,1f,0f,box.lowerLeftX,h+box.lowerLeftY)
                    else -> Matrix(1f,0f,0f,1f,box.lowerLeftX,box.lowerLeftY)
                }
                val aspect = signature.width.toFloat()/signature.height
                val p = placement.bounded(aspect,dw/dh); val sw = p.width*dw; val sh = sw/aspect
                val image = LosslessFactory.createFromImage(doc,signature)
                PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { stream ->
                    stream.saveGraphicsState(); stream.transform(base)
                    stream.drawImage(image,p.left*dw,dh-p.top*dh-sh,sw,sh)
                    stream.restoreGraphicsState()
                }
                doc.save(output)
            }
            output
        } catch(e: Exception) { output.delete(); throw e } finally { input.delete() }
    }
}
