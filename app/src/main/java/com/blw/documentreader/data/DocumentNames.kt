package com.blw.documentreader.data

/** A Library title is also the exact, safe attachment name. */
object DocumentNames {
    fun extension(type: String) = when (type) {
        "Word" -> "docx"
        "Excel" -> "xlsx"
        "TXT" -> "txt"
        else -> "pdf"
    }

    fun renamed(input: String, type: String): String {
        val extension = extension(type)
        val clean = input.trim().replace(Regex("[\\p{Cc}/\\\\:*?\"<>|]"), "_")
        val stem = (if (clean.endsWith(".$extension", ignoreCase = true))
            clean.dropLast(extension.length + 1) else clean).trim().trimEnd('.', ' ').take(150)
        require(stem.isNotBlank() && stem.any { it != '.' }) { "Enter a file name." }
        return "$stem.$extension"
    }

    fun tags(input: String): String = input.split(',').map { it.trim().take(35) }
        .filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(12).joinToString(", ")
}
