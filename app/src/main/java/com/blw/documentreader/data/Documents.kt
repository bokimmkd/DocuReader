package com.blw.documentreader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import androidx.core.content.FileProvider

/** Metadata only. Document contents are never stored here. */
data class DocumentEntry(
    val uri: String,
    val name: String,
    val type: String,
    val favorite: Boolean = false,
    val lastPage: Int = 0,
    val openedAt: Long = System.currentTimeMillis(),
    val category: String = "Uncategorized",
    val tags: String = "",
    val savedLocally: Boolean = false
)

object DocumentTypes {
    const val PDF = "PDF"
    const val WORD = "Word"
    const val EXCEL = "Excel"
    fun detect(context: Context, uri: Uri, name: String): String? {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> PDF
            ext == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> WORD
            ext == "xlsx" || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> EXCEL
            else -> null
        }
    }
    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) return it.getString(0) ?: "Document"
                }
            } catch (_: Exception) { /* revoked or inaccessible: show a useful error at open time */ }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    }
}

class DocumentStore(context: Context) {
    private val prefs = context.getSharedPreferences("metadata_only", Context.MODE_PRIVATE)
    fun load(): List<DocumentEntry> = try {
        val json = JSONArray(prefs.getString("recent", "[]"))
        (0 until json.length()).map { i ->
            val item = json.getJSONObject(i)
            DocumentEntry(item.getString("uri"), item.getString("name"), item.getString("type"),
                item.optBoolean("favorite", false), item.optInt("lastPage", 0), item.optLong("openedAt", 0),
                item.optString("category", "Uncategorized"), item.optString("tags", ""), item.optBoolean("savedLocally", false))
        }
    } catch (_: Exception) { emptyList() }
    fun save(items: List<DocumentEntry>) {
        val out = JSONArray()
        // Keep curated Library documents and favorites even when Recent exceeds 100.
        val curated = items.filter { it.savedLocally || it.favorite }
        val limit = (curated + items.filterNot { it in curated }.take(100)).distinctBy { it.uri }
        limit.forEach { item ->
            out.put(JSONObject().put("uri", item.uri).put("name", item.name).put("type", item.type)
                .put("favorite", item.favorite).put("lastPage", item.lastPage).put("openedAt", item.openedAt)
                .put("category", item.category).put("tags", item.tags).put("savedLocally", item.savedLocally))
        }
        prefs.edit().putString("recent", out.toString()).apply()
    }
    fun appearance() = prefs.getString("appearance", "System") ?: "System"
    fun setAppearance(value: String) = prefs.edit().putString("appearance", value).apply()
    fun rememberPosition() = prefs.getBoolean("remember", true)
    fun setRememberPosition(on: Boolean) = prefs.edit().putBoolean("remember", on).apply()
    fun clearHistory() = prefs.edit().remove("recent").apply()
    /** Explicit user-requested offline copy. Does not touch the original document. */
    fun keepOffline(uri: Uri, originalName: String): Uri {
        val dir = File(context.filesDir, "library").also { it.mkdirs() }
        val extension = originalName.substringAfterLast('.', "").lowercase()
            .takeIf { it in setOf("pdf", "docx", "xlsx") } ?: "pdf"
        val dest = File(dir, "${UUID.randomUUID()}.$extension")
        val temp = File(dir, "${dest.name}.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: error("This document is no longer accessible. Locate it again.")
            require(temp.length() > 0L) { "The document was empty." }
            check(temp.renameTo(dest)) { "Unable to save the local copy." }
            return FileProvider.getUriForFile(context, "${context.packageName}.files", dest)
        } catch (ex: Exception) {
            temp.delete(); dest.delete(); throw ex
        }
    }

}
