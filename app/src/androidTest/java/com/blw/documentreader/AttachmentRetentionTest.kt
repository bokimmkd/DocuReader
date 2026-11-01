package com.blw.documentreader

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blw.documentreader.data.DocumentEntry
import com.blw.documentreader.data.DocumentStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AttachmentRetentionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun temporaryProviderBytesSurviveOriginalDeletionAndCategoryPersists() {
        val library = File(context.filesDir, "library").also { it.mkdirs() }
        val original = File(library, "viber-attachment-original.pdf")
        val expected = "%PDF-1.4 temporary provider test bytes".toByteArray()
        original.writeBytes(expected)
        try {
            val source = FileProvider.getUriForFile(context, "${context.packageName}.files", original)
            val store = DocumentStore(context)
            val aliases = store.sourceKeys(source)
            val kept = store.keepOffline(source, "Преглед на записник.pdf", maxBytes = 1024)
            val entry = DocumentEntry(kept.toString(), "Преглед на записник.pdf", "PDF",
                category = "Work", tags = "viber", savedLocally = true, sourceUris = aliases)
            store.save(listOf(entry))
            original.delete() // Simulates messaging provider's expired backing file.
            val reopened = context.contentResolver.openInputStream(kept)!!.use { it.readBytes() }
            assertArrayEquals("Offline copy changed", expected, reopened)
            val persisted = store.load().single { it.uri == kept.toString() }
            assertEquals("Преглед на записник.pdf", persisted.name)
            assertEquals("Work", persisted.category)
            assertEquals("viber", persisted.tags)
            assertTrue(persisted.sourceUris.contains(source.toString()))
        } finally {
            original.delete()
        }
    }

    @Test fun oversizedTemporaryAttachmentLeavesNoHalfWrittenFile() {
        val library = File(context.filesDir, "library").also { it.mkdirs() }
        val original = File(library, "viber-large.pdf")
        original.writeBytes(ByteArray(4096) { 7 })
        val before = library.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", original)
            try {
                DocumentStore(context).keepOffline(uri, "viber-large.pdf", maxBytes = 1024)
                fail("Oversized temporary attachment must be rejected.")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("safety limit"))
            }
            assertEquals(before, library.listFiles()?.map { it.name }?.toSet().orEmpty())
        } finally { original.delete() }
    }
}
