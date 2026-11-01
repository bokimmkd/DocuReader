package com.blw.documentreader.data

/** Kept by MainViewModel while the Library is temporarily replaced by a reader. */
data class LibraryState(
    val type: String = "All", val search: String = "", val category: String = "All categories",
    val tag: String = "All tags", val order: String = "Recent", val index: Int = 0, val offset: Int = 0
)

object LibraryActions {
    fun tags(existing: String, incoming: String, remove: Boolean): String {
        val old = existing.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val changes = incoming.split(',').map { it.trim().take(40) }.filter { it.isNotBlank() }
        return (if (remove) old.filterNot { a -> changes.any { it.equals(a, true) } } else old + changes)
            .distinctBy { it.lowercase() }.take(30).joinToString(", ")
    }
    /** File identities, rather than adapter positions, define every bulk action. */
    fun change(items: List<DocumentEntry>, uris: Set<String>, category: String? = null,
               tags: String? = null, removeTags: Boolean = false): List<DocumentEntry> = items.map {
        if (it.uri !in uris) it else it.copy(category = category?.trim()?.take(45)?.ifBlank { "Uncategorized" } ?: it.category,
            tags = tags?.let { incoming -> LibraryActions.tags(it.tags, incoming, removeTags) } ?: it.tags)
    }
    fun adAfter(documentCount: Int) = documentCount >= 5 && (documentCount - 5) % 10 == 0
}
