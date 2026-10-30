package com.blw.documentreader.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object TextContent {
    const val MAX_BYTES = 8 * 1024 * 1024

    fun read(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= MAX_BYTES) { "TXT files up to 8 MB are supported." }
            out.write(buffer, 0, count)
        }
        return decode(out.toByteArray())
    }

    fun decode(bytes: ByteArray): String {
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
                    .toString().removePrefix("\uFEFF")
            } catch (_: java.nio.charset.CharacterCodingException) {
                // Common legacy Cyrillic TXT documents from Windows.
                String(bytes, charset("windows-1251"))
            }
        }
        require('\u0000' !in text) { "This file does not look like a text document." }
        return text
    }
}
