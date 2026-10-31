package com.blw.documentreader

import android.app.Application
import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blw.documentreader.data.DocumentEntry
import com.blw.documentreader.data.DocumentStore
import com.blw.documentreader.data.DocumentTypes
import com.blw.documentreader.data.DocumentNames
import com.blw.documentreader.pdf.PdfPageTools
import com.blw.documentreader.pdf.PdfSession
import com.blw.documentreader.pdf.SourcePdf
import com.blw.documentreader.pdf.ToolPage
import com.blw.documentreader.pdf.parsePageSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()
    private val store = DocumentStore(appContext)
    var recent by mutableStateOf(store.load()); private set
    var libraryReveal by mutableStateOf(0); private set
    var reviewMoment by mutableStateOf(0); private set
    private var successfullyOpened = false
    var current by mutableStateOf<DocumentEntry?>(null); private set
    var pdf by mutableStateOf<PdfSession?>(null); private set
    var officeUri by mutableStateOf<Uri?>(null); private set
    var officeName by mutableStateOf(""); private set
    var officeType by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var passwordNeeded by mutableStateOf(false); private set
    var pdfMatches by mutableStateOf<List<Int>>(emptyList()); private set
    var appearance by mutableStateOf(store.appearance()); private set
    var remember by mutableStateOf(store.rememberPosition()); private set

    private val toolEngine = PdfPageTools(appContext)
    val toolPages = mutableStateListOf<ToolPage>()
    var toolTitle by mutableStateOf("PDF tools"); private set
    var toolReady by mutableStateOf(false); private set
    var toolSaveResult by mutableStateOf<Uri?>(null); private set
    var sourceForSelection by mutableStateOf<SourcePdf?>(null); private set
    var addSource by mutableStateOf(false); private set

    fun consumeLibraryReveal() { libraryReveal = 0 }
    fun dismissMessage() { message = null }
    fun report(text: String) { message = text }
    fun updateAppearance(theme: String) { appearance = theme; store.setAppearance(theme) }
    fun updateRemember(on: Boolean) { remember = on; store.setRememberPosition(on) }
    fun clearRecent() {
        // Clearing history must not discard separately starred documents.
        recent = recent.filter { it.favorite || it.savedLocally || it.addedAt > 0L }.map { it.copy(openedAt = 0L, lastPage = 0) }
        store.save(recent)
    }
    fun favorite(doc: DocumentEntry) {
        recent = recent.map { if (it.uri == doc.uri) it.copy(favorite = !it.favorite) else it }
        current = current?.let { if (it.uri == doc.uri) it.copy(favorite = !it.favorite) else it }
        store.save(recent)
    }
    fun updateLibraryInfo(doc: DocumentEntry, category: String, tags: String) {
        updateDocumentInfo(doc, doc.name, category, tags)
    }
    fun updateDocumentInfo(doc: DocumentEntry, name: String, category: String, tags: String): Boolean {
        val fileName = try { DocumentNames.renamed(name, doc.type) } catch (ex: IllegalArgumentException) {
            message = ex.message; return false
        }
        val label = category.trim().take(45).ifBlank { "Uncategorized" }
        val keywords = DocumentNames.tags(tags)
        recent = recent.map { if (it.uri == doc.uri) it.copy(name = fileName, category = label, tags = keywords) else it }
        current = current?.let { if (it.uri == doc.uri) it.copy(name = fileName, category = label, tags = keywords) else it }
        if (current?.uri == doc.uri) officeName = fileName
        store.save(recent)
        message = "Document saved."
        return true
    }
    /** Explicit user opt-in. Keep copies in app-private storage without changing source. */
    fun saveToLibrary(uri: Uri, document: DocumentEntry? = null) {
        busy = true
        viewModelScope.launch {
            try {
                val original = document ?: recent.find { it.uri == uri.toString() }
                val name = original?.name ?: DocumentTypes.displayName(appContext, uri)
                val type = document?.type ?: DocumentTypes.detect(appContext, uri, name)
                require(type != null) { "Supported: ${com.blw.documentreader.data.DocumentFormats.supported}." }
                val savedUri = withContext(Dispatchers.IO) { store.keepOffline(uri, name) }
                val newEntry = DocumentEntry(
                    savedUri.toString(), name, type, original?.favorite ?: false,
                    original?.lastPage ?: 0, original?.openedAt ?: 0L,
                    original?.category ?: "Uncategorized", original?.tags ?: "", true, System.currentTimeMillis()
                )
                recent = listOf(newEntry) + recent.filter {
                    it.uri != newEntry.uri && it.uri != uri.toString()
                }
                store.save(recent)
                libraryReveal++
                message = "Saved to Library. Original document unchanged."
            } catch (ex: Exception) {
                message = "Could not save Library copy: ${ex.message ?: "Check access and storage."}"
            } finally { busy = false }
        }
    }
    /** Register the successfully written destination without making a duplicate private copy. */
    fun registerSavedPdf(uri: Uri, name: String) {
        val existing = recent.find { it.uri == uri.toString() }
        val source = current
        val record = DocumentEntry(uri.toString(), DocumentNames.renamed(name, DocumentTypes.PDF), DocumentTypes.PDF,
            existing?.favorite ?: false, existing?.lastPage ?: 0, existing?.openedAt ?: 0L,
            existing?.category ?: source?.category ?: "Uncategorized", existing?.tags ?: source?.tags ?: "",
            existing?.savedLocally ?: false, existing?.addedAt?.takeIf { it > 0L } ?: System.currentTimeMillis())
        recent = listOf(record) + recent.filterNot { it.uri == record.uri }
        store.save(recent); libraryReveal++; message = "PDF saved to Library."
    }
    fun removeLibraryEntry(doc: DocumentEntry) {
        recent = recent.filterNot { it.uri == doc.uri }
        store.save(recent)
        // Never delete files from external providers or shared storage.
        // An app-private copy, if any, stays untouched until explicit file cleanup.
        message = "Removed from Library list. Original file unchanged."
    }
    fun pageChanged(page: Int) {
        if (!remember) return
        current?.let { doc ->
            if (doc.lastPage != page) {
                val changed = doc.copy(lastPage = page)
                current = changed
                recent = recent.map { if (it.uri == doc.uri) changed else it }
                store.save(recent)
            }
        }
    }
    fun openDocument(uri: Uri, password: String? = null) {
        successfullyOpened = false
        val existingEntry = recent.find { it.uri == uri.toString() }
        val name = existingEntry?.name ?: DocumentTypes.displayName(appContext, uri)
        val type = existingEntry?.type ?: DocumentTypes.detect(appContext, uri, name)
        if (type == null) { message = "Unsupported format. Choose ${com.blw.documentreader.data.DocumentFormats.supported}."; return }
        if (type != DocumentTypes.PDF) {
            pdf?.close(); pdf = null
            officeUri = uri
            officeName = name
            officeType = type
            passwordNeeded = false
            message = null
            val existing = recent.find { it.uri == uri.toString() }
            val record = DocumentEntry(
                uri.toString(), name, type, existing?.favorite ?: false,
                0, System.currentTimeMillis(), existing?.category ?: "Uncategorized",
                existing?.tags ?: "", existing?.savedLocally ?: false, existing?.addedAt ?: 0L
            )
            current = record
            recent = listOf(record) + recent.filter { it.uri != record.uri }
            store.save(recent)
            return
        }
        officeUri = null
        busy = true; passwordNeeded = false; message = null
        viewModelScope.launch {
            try {
                val session = PdfSession.open(appContext, uri, password)
                pdf?.close()
                pdf = session
                val existing = recent.find { it.uri == uri.toString() }
                val record = DocumentEntry(uri.toString(), name, type, existing?.favorite ?: false,
                    if (remember) existing?.lastPage ?: 0 else 0,
                    System.currentTimeMillis(), existing?.category ?: "Uncategorized",
                    existing?.tags ?: "", existing?.savedLocally ?: false, existing?.addedAt ?: 0L)
                current = record
                recent = listOf(record) + recent.filter { it.uri != record.uri }
                store.save(recent)
                pdfMatches = emptyList()
                successfullyOpened = true
            } catch (ex: SecurityException) {
                passwordNeeded = true
                message = "This PDF is protected. Enter its password to open it."
            } catch (ex: Exception) {
                message = if (password != null) "Wrong password or unsupported PDF protection." else
                    "Could not open this file. It may be damaged or access may have expired. Choose Locate again."
            } finally { busy = false }
        }
    }
    fun markReaderReady() { successfullyOpened = true }
    fun closeDocument() {
        if (successfullyOpened) reviewMoment++
        successfullyOpened = false
        pdf?.close(); pdf = null; officeUri = null
        current = null; passwordNeeded = false
    }
    fun searchPdf(query: String) {
        val session = pdf ?: return
        busy = true
        viewModelScope.launch {
            try {
                pdfMatches = session.search(query)
                message = if (pdfMatches.isEmpty())
                    "No selectable text matches. Scanned image PDFs need OCR (not in V1)." else
                    "${pdfMatches.size} matching page(s). Tap a result to navigate."
            } catch (_: Exception) { message = "Text search couldn't finish for this PDF." }
            finally { busy = false }
        }
    }
    fun beginTool(uri: Uri) { toolEngine.close(); sourceForSelection = null; addSource = false; toolPages.clear(); toolSaveResult = null; toolTitle = "PDF tools"; toolReady = false; importTool(uri, false) }
    fun importTool(uri: Uri, adding: Boolean) {
        busy = true
        viewModelScope.launch {
            try {
                val name = DocumentTypes.displayName(appContext, uri)
                require(DocumentTypes.detect(appContext, uri, name) == DocumentTypes.PDF) { "Choose a PDF file" }
                val source = toolEngine.importSource(uri, name)
                sourceForSelection = source
                addSource = adding
                if (!adding) {
                    toolPages.clear(); toolTitle = name; toolReady = true
                    toolPages.addAll((0 until source.count).map { ToolPage(source.file, it) })
                }
            } catch (ex: Exception) {
                message = "Can't import PDF. Check its file permission and password. ${ex.message.orEmpty()}"
            } finally { busy = false }
        }
    }
    fun addSelected(text: String) {
        val source = sourceForSelection ?: return
        try { addSelectedPages(parsePageSelection(text, source.count)) }
        catch (ex: Exception) { message = ex.message }
    }
    fun addSelectedPages(indices: List<Int>) {
        val source = sourceForSelection ?: return
        if (indices.isEmpty() || indices.any { it !in 0 until source.count }) {
            message = "Select at least one valid page."; return
        }
        val imported = indices.distinct().map { ToolPage(source.file, it) }
        if (addSource) toolPages.addAll(imported)
        else { toolPages.clear(); toolPages.addAll(imported) }
        sourceForSelection = null
        toolReady = true
    }
    fun clearSelection() { sourceForSelection = null }
    fun swap(index: Int, direction: Int) {
        val next = index + direction
        if (index in toolPages.indices && next in toolPages.indices) {
            val tmp = toolPages[index]; toolPages[index] = toolPages[next]; toolPages[next] = tmp
        }
    }
    fun rotate(index: Int) { toolPages[index] = toolPages[index].copy(rotation = (toolPages[index].rotation + 90) % 360) }
    fun remove(index: Int) {
        if (toolPages.size <= 1) message = "A document must contain at least one page."
        else toolPages.removeAt(index)
    }
    fun saveTool(uri: Uri) {
        if (toolPages.isEmpty()) { message = "No pages to export"; return }
        busy = true
        val snapshot = toolPages.toList()
        viewModelScope.launch {
            try { toolEngine.save(snapshot, uri); toolSaveResult = uri; reviewMoment++; message = "New PDF saved. The original documents were not changed." }
            catch (_: Exception) { message = "Could not save PDF. Check the destination and free space." }
            finally { busy = false }
        }
    }
    override fun onCleared() { pdf?.close(); toolEngine.close(); super.onCleared() }
}
