package com.blw.documentreader.data

import org.junit.Assert.*
import org.junit.Test

class DocumentNamesTest {
    @Test fun renamedAttachmentRetainsItsActualFormatAndCyrillicName() {
        assertEquals("Сметка мај.pdf", DocumentNames.renamed("  Сметка мај.PDF  ", "PDF"))
        assertEquals("Schedule.xlsx", DocumentNames.renamed("Schedule", "Excel"))
    }
    @Test fun attachmentCannotEscapeItsShareDirectory() {
        val result = DocumentNames.renamed("../../invoice\\draft\n", "Word")
        assertFalse(result.contains('/'))
        assertFalse(result.contains('\\'))
        assertFalse(result.contains('\n'))
        assertTrue(result.endsWith(".docx"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun emptyStemDoesNotBecomeAnInvisibleFile() {
        DocumentNames.renamed(".txt", "TXT")
    }
    @Test fun tagsAreTrimmedAndDeduplicatedWithoutLosingTheirReadableCase() {
        assertEquals("Work, Сметки", DocumentNames.tags(" Work, work, , Сметки,сметки "))
    }
    @Test fun longUnicodeNamesFitAnAndroidAttachmentFile() {
        val name = DocumentNames.renamed("📄Сметка".repeat(30), "TXT")
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 220)
        assertTrue(name.endsWith(".txt"))
        assertFalse(name.contains('\uFFFD'))
    }
}
