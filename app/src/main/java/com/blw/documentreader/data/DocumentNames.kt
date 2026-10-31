package com.blw.documentreader.data

/** A Library title is also the exact, safe attachment name. */
object DocumentNames {
    fun extension(type: String) = DocumentFormats.extension(type)

    fun renamed(input: String, type: String): String {
        val extension = extension(type)
        val clean = input.trim().replace(Regex("[\\p{Cc}/\\\\:*?\"<>|]"), "_")
        var stem = (if (clean.endsWith(".$extension", ignoreCase = true))
            clean.dropLast(extension.length + 1) else clean).trim().trimEnd('.', ' ').take(150).trimEnd { it.isHighSurrogate() }
        require(stem.isNotBlank() && stem.any { it != '.' }) { "Enter a file name." }
        // Android file systems limit UTF-8 bytes, not the number of displayed characters.
        while ("$stem.$extension".toByteArray(Charsets.UTF_8).size > 220) {
            val count = if (stem.length >= 2 && stem.last().isLowSurrogate() &&
                stem[stem.lastIndex - 1].isHighSurrogate()) 2 else 1
            stem = stem.dropLast(count)
        }
        return "$stem.$extension"
    }

    fun tags(input: String): String = input.split(',').map { it.trim().take(35) }
        .filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(12).joinToString(", ")
}
