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
import com.blw.documentreader.data.DocumentFormats
import com.blw.documentreader.data.LibraryState
import com.blw.documentreader.data.LibraryActions
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.blw.documentreader.pdf.PdfPageTools
import com.blw.documentreader.pdf.PdfSession
import com.blw.documentreader.pdf.SourcePdf
import com.blw.documentreader.pdf.ToolPage
import com.blw.documentreader.pdf.parsePageSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application, private val savedState: SavedStateHandle = SavedStateHandle()) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()
    private val store = DocumentStore(appContext)
    var recent by mutableStateOf(store.load()); private set
    init { store.save(recent) }
    var libraryState by mutableStateOf(LibraryState(
        savedState["library.type"] ?: "All", savedState["library.search"] ?: "",
        savedState["library.category"] ?: "All categories", savedState["library.tag"] ?: "All tags",
        savedState["library.order"] ?: "Recent", savedState["library.index"] ?: 0, savedState["library.offset"] ?: 0, savedState["library.collection"] ?: "Saved"
    )); private set
    var categories by mutableStateOf((store.categories() + recent.map { it.category }).filter { it != "Uncategorized" }.distinctBy { it.lowercase() }); private set
    val unavailable = mutableStateMapOf<String, Boolean>()
    var availabilityEpoch by mutableStateOf(0); private set
    private val accessSlots = Semaphore(3)
    private val checking = mutableSetOf<String>()
    private var removed = emptyList<Pair<Int, DocumentEntry>>()
    val canUndoRemoval get() = removed.isNotEmpty()
    var imageQueue by mutableStateOf<List<String>>(emptyList()); private set
    fun libraryState(value: LibraryState) {
        libraryState = value
        savedState["library.type"] = value.type; savedState["library.search"] = value.search
        savedState["library.category"] = value.category; savedState["library.tag"] = value.tag
        savedState["library.collection"] = value.collection
        savedState["library.order"] = value.order; savedState["library.index"] = value.index; savedState["library.offset"] = value.offset
    }
    fun showLibrary(type: String) { libraryState(libraryState.copy(type = if (type == "Recent") "All" else type,
        collection = if (type == "Recent") "Recent" else "Saved", index = 0, offset = 0)) }
    fun checkAvailability(doc: DocumentEntry) {
        if (!checking.add(doc.uri)) return
        viewModelScope.launch {
            try {
                val accessible = withContext(Dispatchers.IO) { accessSlots.withPermit {
                    runCatching { appContext.contentResolver.openInputStream(Uri.parse(doc.uri))?.use { true } ?: false }.getOrDefault(false)
                } }
                if (recent.any { it.uri == doc.uri }) unavailable[doc.uri] = !accessible
            } finally { checking.remove(doc.uri) }
        }
    }
    fun refreshAvailability() {
        val kept = recent.filter { it.isSaved || it.isRecent() || it.uri == current?.uri }
        if (kept != recent) { recent = kept; store.save(recent) }
        availabilityEpoch++
    }
    fun addCategory(value: String) {
        val name = value.trim().take(45)
        if (name.isNotEmpty() && name != "Uncategorized" && categories.none { it.equals(name, true) }) {
            categories = categories + name; store.categories(categories)
        }
    }
    fun deleteCategory(value: String) {
        recent = recent.map { if (it.category == value) it.copy(category = "Uncategorized") else it }
        current = current?.let { c -> recent.find { it.uri == c.uri } ?: c }
        categories = categories.filterNot { it == value }; store.categories(categories); store.save(recent)
        if (libraryState.category == value) libraryState(libraryState.copy(category = "All categories"))
        report("Category removed. Files are unchanged.")
    }
    fun bulk(uris: Set<String>, category: String? = null, tags: String? = null, removeTags: Boolean = false) {
        category?.let(::addCategory)
        recent = LibraryActions.change(recent, uris, category, tags, removeTags)
        current = current?.let { c -> recent.find { it.uri == c.uri } ?: c }; store.save(recent)
        report("Updated ${uris.size} document(s). Active Library filters are kept.")
    }
    fun removeEntries(uris: Set<String>) {
        removed = recent.mapIndexedNotNull { i, d -> if (d.uri in uris) i to d else null }
        recent = recent.filterNot { it.uri in uris }; store.save(recent)
        report("Removed ${removed.size} document(s) from Library. Original files unchanged.")
    }
    fun undoRemoval() {
        val list = recent.toMutableList()
        removed.forEach { (i, d) -> if (list.none { it.uri == d.uri }) list.add(i.coerceAtMost(list.size), d) }
        removed = emptyList(); recent = list; store.save(recent); report("Library removal undone.")
    }
    fun relink(doc: DocumentEntry, uri: Uri) {
        viewModelScope.launch {
            try {
                val type = withContext(Dispatchers.IO) { DocumentTypes.detect(appContext, uri, DocumentTypes.displayName(appContext, uri)) }
                require(type == doc.type) { "Choose another ${doc.type} file." }
                require(recent.none { it.uri == uri.toString() && it.uri != doc.uri }) { "That file is already in Library." }
                recent = recent.map { if (it.uri == doc.uri) it.copy(uri = uri.toString(), savedLocally = false, temporaryCopy = false, inLibrary = true, addedAt = maxOf(1L, it.addedAt)) else it }
                unavailable.remove(doc.uri); unavailable.remove(uri.toString()); store.save(recent)
                report("File located. Name, category, tags and favorite are kept.")
            } catch (cancel: CancellationException) { throw cancel }
            catch (ex: Exception) { report(ex.message ?: "Could not locate file.") }
        }
    }
    fun openFromLibrary(doc: DocumentEntry, entries: List<DocumentEntry>) {
        imageQueue = if (doc.type in DocumentFormats.images) entries.filter { it.type in DocumentFormats.images }.map { it.uri } else emptyList()
        openDocument(Uri.parse(doc.uri), fromQueue = true)
    }
    fun adjacentImage(direction: Int) {
        val index = imageQueue.indexOf(current?.uri)
        val next = imageQueue.getOrNull(index + direction) ?: return
        recent.find { it.uri == next }?.let { openDocument(Uri.parse(it.uri), fromQueue = true) }
    }
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
        recent = recent.filter { it.isSaved }.map { it.copy(openedAt = 0L, lastPage = 0) }
        store.save(recent)
    }
    fun favorite(doc: DocumentEntry) {
        if (!doc.favorite && !doc.savedLocally) {
            saveToLibrary(Uri.parse(doc.uri), doc, makeFavorite = true, reveal = false)
            return
        }
        recent = recent.map { if (it.uri == doc.uri) it.copy(favorite = !it.favorite, inLibrary = true) else it }
        current = current?.let { c -> recent.find { it.uri == c.uri } ?: c }
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
        addCategory(label)
        val keywords = DocumentNames.tags(tags)
        recent = recent.map { if (it.uri == doc.uri) it.copy(name = fileName, category = label, tags = keywords) else it }
        current = current?.let { if (it.uri == doc.uri) it.copy(name = fileName, category = label, tags = keywords) else it }
        if (current?.uri == doc.uri) officeName = fileName
        store.save(recent)
        message = "Document saved."
        return true
    }
    /** Explicit user opt-in. Keep copies in app-private storage without changing source. */
    fun saveToLibrary(uri: Uri, document: DocumentEntry? = null, makeFavorite: Boolean = false, reveal: Boolean = true) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val original = document ?: recent.find { it.uri == uri.toString() || uri.toString() in it.sourceUris }
                if (original?.savedLocally == true) {
                    libraryReveal++; message = "Already saved in Library."; return@launch
                }
                val name = original?.name ?: DocumentTypes.displayName(appContext, uri)
                val type = original?.type ?: DocumentTypes.detect(appContext, uri, name)
                require(type != null) { "Supported: ${com.blw.documentreader.data.DocumentFormats.supported}." }
                val sourceUri = original?.let { Uri.parse(it.uri) } ?: uri
                val savedUri = withContext(Dispatchers.IO) {
                    if (original?.temporaryCopy == true) {
                        appContext.contentResolver.openInputStream(sourceUri)?.use { } ?: error("Reopen this document from its source.")
                        sourceUri
                    } else store.keepOffline(sourceUri, name, maxBytes = 128L * 1024 * 1024)
                }
                val newEntry = DocumentEntry(
                    savedUri.toString(), name, type, makeFavorite || original?.favorite == true,
                    original?.lastPage ?: 0, original?.openedAt ?: 0L,
                    original?.category ?: "Uncategorized", original?.tags ?: "", true, System.currentTimeMillis(),
                    (original?.sourceUris.orEmpty() + withContext(Dispatchers.IO) { store.sourceKeys(uri) }).distinct(), inLibrary = true
                )
                recent = listOf(newEntry) + recent.filter {
                    it.uri != newEntry.uri && it.uri != uri.toString() && it.uri != original?.uri
                }
                current = current?.let { c -> if (c.uri == original?.uri || c.uri == uri.toString()) newEntry else c }
                unavailable.remove(newEntry.uri)
                store.save(recent)
                if (reveal) libraryReveal++
                message = "Saved locally. Available offline in Library."
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
        removeEntries(setOf(doc.uri))
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
    private var openRequest = 0L
    fun openDocument(requestedUri: Uri, password: String? = null, fromQueue: Boolean = false, retainTemporary: Boolean = false) {
        val request = ++openRequest
        busy = true
        viewModelScope.launch {
            try {
                val keys = withContext(Dispatchers.IO) { store.sourceKeys(requestedUri) }
                if (request != openRequest) return@launch
                val known = recent.find { it.uri in keys || it.sourceUris.any { key -> key in keys } }
                val migrated = known ?: withContext(Dispatchers.IO) { store.legacyCopy(requestedUri, recent) }
                if (request != openRequest) return@launch
                if (migrated != null) {
                    recent = recent.map { if (it.uri == migrated.uri) it.copy(sourceUris = (it.sourceUris + keys).distinct()) else it }
                    store.save(recent)
                }
                val existing = migrated ?: known
                if (retainTemporary && existing?.savedLocally != true && existing?.temporaryCopy != true &&
                    requestedUri.authority != "${appContext.packageName}.files") {
                    // Copy only supported attachments, while the messaging app's
                    // temporary URI permission is still valid. Keep source aliases
                    // so reopening the SAME Viber message does not add another row.
                    val name = withContext(Dispatchers.IO) { DocumentTypes.displayName(appContext, requestedUri) }
                    val type = withContext(Dispatchers.IO) { DocumentTypes.detect(appContext, requestedUri, name) }
                    if (type != null) {
                        val saved = withContext(Dispatchers.IO) {
                            store.keepOffline(requestedUri, name, maxBytes = 128L * 1024 * 1024)
                        }
                        if (request != openRequest) return@launch
                        val entry = DocumentEntry(saved.toString(), name, type,
                            existing?.favorite ?: false, existing?.lastPage ?: 0,
                            System.currentTimeMillis(), existing?.category ?: "Uncategorized",
                            existing?.tags ?: "", false,
                            existing?.addedAt ?: 0L,
                            (existing?.sourceUris.orEmpty() + keys).distinct(),
                            inLibrary = existing?.isSaved ?: false, temporaryCopy = true)
                        recent = listOf(entry) + recent.filterNot {
                            it.uri == entry.uri || it.uri == requestedUri.toString() ||
                                (existing != null && it.uri == existing.uri)
                        }
                        store.save(recent)
                    }
                }
                busy = false
                openResolved(requestedUri, password, fromQueue, request, keys)
            } catch (cancel: CancellationException) { throw cancel }
            catch (ex: Exception) { if (request == openRequest) { busy = false; message = ex.message ?: "Could not open this file." } }
        }
    }
    private fun openResolved(requestedUri: Uri, password: String?, fromQueue: Boolean, request: Long, keys: List<String>) {
        val requested = requestedUri.toString()
        val linked = recent.find { it.uri == requested || requested in it.sourceUris }
        val uri = linked?.let { Uri.parse(it.uri) } ?: requestedUri
        if (!fromQueue) imageQueue = emptyList()
        successfullyOpened = false
        val existingEntry = linked ?: recent.find { it.uri == uri.toString() }
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
                existing?.tags ?: "", existing?.savedLocally ?: false, existing?.addedAt ?: 0L, (existing?.sourceUris.orEmpty() + keys).distinct(), existing?.inLibrary ?: false, existing?.temporaryCopy ?: false
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
                if (request != openRequest) { session.close(); return@launch }
                pdf?.close()
                pdf = session
                val existing = recent.find { it.uri == uri.toString() }
                val record = DocumentEntry(uri.toString(), name, type, existing?.favorite ?: false,
                    if (remember) existing?.lastPage ?: 0 else 0,
                    System.currentTimeMillis(), existing?.category ?: "Uncategorized",
                    existing?.tags ?: "", existing?.savedLocally ?: false, existing?.addedAt ?: 0L, (existing?.sourceUris.orEmpty() + keys).distinct(), existing?.inLibrary ?: false, existing?.temporaryCopy ?: false)
                current = record
                recent = listOf(record) + recent.filter { it.uri != record.uri }
                store.save(recent)
                pdfMatches = emptyList()
                successfullyOpened = true
            } catch (ex: SecurityException) {
                if (request != openRequest) return@launch
                passwordNeeded = true
                message = "This PDF is protected. Enter its password to open it."
            } catch (ex: Exception) {
                if (request != openRequest) return@launch
                message = if (password != null) "Wrong password or unsupported PDF protection." else
                    "Could not open this file. It may be damaged or access may have expired. Choose Locate again."
            } finally { if (request == openRequest) busy = false }
        }
    }
    fun markReaderReady() { successfullyOpened = true }
    fun closeDocument() {
        openRequest++; busy = false
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
    fun beginTool(uri: Uri, selectStartingPages: Boolean = true) { toolEngine.close(); sourceForSelection = null; addSource = false; toolPages.clear(); toolSaveResult = null; toolTitle = "PDF tools"; toolReady = false; importTool(uri, false, selectStartingPages) }
    fun importTool(uri: Uri, adding: Boolean, selectStartingPages: Boolean = true) {
        busy = true
        viewModelScope.launch {
            try {
                val name = DocumentTypes.displayName(appContext, uri)
                require(DocumentTypes.detect(appContext, uri, name) == DocumentTypes.PDF) { "Choose a PDF file" }
                val source = toolEngine.importSource(uri, name)
                sourceForSelection = if (adding || selectStartingPages) source else null
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
