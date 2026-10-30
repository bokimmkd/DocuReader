package com.blw.documentreader.data

import org.junit.Assert.*
import org.junit.Test

class TextContentTest {
    @Test fun opensUtf8AndBothUtf16ByteOrders() {
        val text = "Здраво Боки\nTXT document"
        assertEquals(text, TextContent.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, TextContent.decode(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()) + text.toByteArray()))
        assertEquals(text, TextContent.decode(byteArrayOf(0xff.toByte(),0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, TextContent.decode(byteArrayOf(0xfe.toByte(),0xff.toByte()) + text.toByteArray(Charsets.UTF_16BE)))
    }
    @Test fun opensLegacyCyrillicTextAndEmptyFiles() {
        assertEquals("Текст", TextContent.decode("Текст".toByteArray(charset("windows-1251"))))
        assertEquals("", TextContent.read(byteArrayOf().inputStream()))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsBinaryNulContent() { TextContent.decode(byteArrayOf(1,0,3)) }
    @Test(expected = IllegalArgumentException::class)
    fun refusesOversizedTextBeforeRendering() {
        TextContent.read(ByteArray(TextContent.MAX_BYTES + 1) { 65 }.inputStream())
    }
}
