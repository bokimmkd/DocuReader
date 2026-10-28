package com.blw.documentreader.pdf

/** Returns distinct, ordered 0-based page indices. Throws for invalid or out-of-range input. */
fun parsePageSelection(text: String, count: Int): List<Int> {
    require(count > 0) { "PDF contains no pages" }
    require(text.isNotBlank()) { "Enter page numbers, such as 1, 3-5" }
    val result = linkedSetOf<Int>()
    text.replace('–', '-').split(',').forEach { token ->
        val value = token.trim()
        val match = Regex("^(\\d+)\\s*(?:-\\s*(\\d+))?$").matchEntire(value)
            ?: throw IllegalArgumentException("Invalid page range: $value")
        val from = match.groupValues[1].toIntOrNull() ?: throw IllegalArgumentException("Page number too large")
        val to = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: from
        require(from in 1..count && to in 1..count && to >= from) {
            "Pages must be between 1 and $count (ascending ranges only)"
        }
        (from..to).forEach { result.add(it - 1) }
    }
    return result.toList()
}
