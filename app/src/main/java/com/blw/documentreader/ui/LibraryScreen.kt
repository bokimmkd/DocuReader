package com.blw.documentreader.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.MainViewModel
import com.blw.documentreader.data.DocumentEntry
import com.blw.documentreader.data.DocumentNames
import com.blw.documentreader.data.LibraryActions
import com.blw.documentreader.monetization.InlineBanner

private val LibraryBlue = Color(0xFF0869E8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySelectionAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { icon() }
    }
}

@Composable
private fun SelectionTagIcon(add: Boolean) {
    Box(Modifier.size(28.dp)) {
        Icon(Icons.Default.Label, if (add) "Add tags" else "Remove tags", Modifier.size(24.dp))
        Surface(Modifier.size(14.dp).align(Alignment.BottomEnd),
            shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary) {
            Icon(if (add) Icons.Default.Add else Icons.Default.Remove, null, Modifier.padding(1.dp))
        }
    }
}

private fun libraryField(vm: MainViewModel, get: (com.blw.documentreader.data.LibraryState) -> String,
    set: (com.blw.documentreader.data.LibraryState, String) -> com.blw.documentreader.data.LibraryState) =
    object : kotlin.properties.ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = get(vm.libraryState)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) { vm.libraryState(set(vm.libraryState, value).copy(index = 0, offset = 0)) }
    }
private fun formatColor(type: String) = when (type) {
    "PDF" -> Color(0xFFEF5A54)
    "Word" -> Color(0xFF448EEE)
    "Excel" -> Color(0xFF21AA65)
    else -> Color(0xFF7896B7)
}

@Composable
private fun LibraryFilter(label: String, values: List<String>, onSelect: (String) -> Unit,
                          modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.FolderOpen) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(40.dp),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
            shape = RoundedCornerShape(8.dp)) {
            Icon(icon, null, Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(17.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { value ->
                DocumentActionItem(value, if (value == label) Icons.Default.Check else icon,
                    { onSelect(value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (DocumentEntry) -> Unit, onAdd: () -> Unit,
                  onShare: (DocumentEntry) -> Unit, onConvert: (DocumentEntry) -> Unit,
                  onPdfAction: (DocumentEntry, PdfLibraryAction) -> Unit, onBookMode: (DocumentEntry) -> Unit, initialFilter: String = "All",
                  onOpenFiltered: (DocumentEntry, List<DocumentEntry>) -> Unit = { doc, _ -> onOpen(doc) },
                  onLocate: (DocumentEntry) -> Unit = {}, showAds: Boolean = false) {
    val state = vm.libraryState
    var collection by libraryField(vm, { it.collection }) { s, v -> s.copy(collection = v) }
    var filter by libraryField(vm, { it.type }) { s, v -> s.copy(type = v) }
    var search by libraryField(vm, { it.search }) { s, v -> s.copy(search = v) }
    var category by libraryField(vm, { it.category }) { s, v -> s.copy(category = v) }
    var tag by libraryField(vm, { it.tag }) { s, v -> s.copy(tag = v) }
    var order by libraryField(vm, { it.order }) { s, v -> s.copy(order = v) }
    val listState = rememberLazyListState(state.index, state.offset)
    val filters = listOf(collection, filter, search, category, tag, order)
    var priorFilters by remember { mutableStateOf(filters) }
    LaunchedEffect(filters) { if (filters != priorFilters) { listState.scrollToItem(0); priorFilters = filters } }
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        .collect { (i, offset) -> vm.libraryState(vm.libraryState.copy(index = i, offset = offset)) } }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var remove by remember { mutableStateOf<Set<String>?>(null) }
    var bulkAction by remember { mutableStateOf<String?>(null) }
    var bulkValue by remember { mutableStateOf("") }
    var manageCategories by remember { mutableStateOf(false) }
    var categoryDraft by rememberSaveable { mutableStateOf("") }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var deleteCategory by remember { mutableStateOf<String?>(null) }
    var unavailableDoc by remember { mutableStateOf<DocumentEntry?>(null) }
    BackHandler(selected.isNotEmpty()) { selected = arrayListOf() }
    fun toggle(uri: String) { selected = ArrayList(if (uri in selected) selected - uri else selected + uri) }
    LaunchedEffect(vm.libraryReveal) {
        if (vm.libraryReveal > 0) {
            collection = "Saved"; filter = "All"; search = ""; category = "All categories"; tag = "All tags"; order = "Recent"
            listState.scrollToItem(0)
            vm.consumeLibraryReveal()
        }
    }
    var editing by remember { mutableStateOf<DocumentEntry?>(null) }
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var newTags by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    fun edit(doc: DocumentEntry) {
        editing = doc
        newName = doc.name.substringBeforeLast('.', doc.name)
        newCategory = doc.category
        newTags = doc.tags
        editError = null
    }
    val data = vm.recent.filter {
        (if (collection == "Recent") it.isRecent() else it.isSaved) &&
        (filter == "All" || filter == "Recent" && it.isRecent() ||
            filter == "Favorites" && it.favorite || com.blw.documentreader.data.DocumentFormats.matches(filter,it.type)) &&
        (category == "All categories" || it.category == category) &&
        (tag == "All tags" || it.tags.split(',').any { value -> value.trim().equals(tag, true) }) &&
        (search.isBlank() || listOf(it.name, it.type, it.category, it.tags)
            .any { value -> value.contains(search.trim(), ignoreCase = true) })
    }
    val entries = when (order) {
        "Name" -> data.sortedBy { it.name.lowercase() }
        "Type" -> data.sortedWith(compareBy({ it.type }, { it.name.lowercase() }))
        else -> data.sortedByDescending { if (collection == "Recent") it.openedAt else maxOf(it.openedAt, it.addedAt) }
    }
    val categories = listOf("All categories", "Uncategorized") + (vm.categories + vm.recent.map { it.category })
        .filter { it.isNotBlank() }.distinct().sorted()
    val tags = listOf("All tags") + vm.recent.flatMap { it.tags.split(',') }
        .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted()

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                Text("${entries.size} documents · " + if (collection == "Recent") "Last 30 days" else "Saved in Library", fontSize = 10.sp, lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { manageCategories = true }) { Icon(Icons.Default.FolderOpen, "Manage categories", Modifier.size(20.dp)) }
            Button(onClick = onAdd, modifier = Modifier.height(38.dp), shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LibraryBlue, contentColor = Color.White)) {
                Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                Text("Add", fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        TabRow(selectedTabIndex = if (collection == "Saved") 0 else 1) {
            listOf("Saved", "Recent").forEach { label ->
                Tab(selected = collection == label, onClick = { collection = label; selected = arrayListOf() }, text = { Text(label) })
            }
        }
        // BasicTextField avoids Material's large built-in vertical content padding.
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            BasicTextField(search, { search = it }, modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                singleLine = true, textStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (search.isEmpty()) Text("Search name, category or tags", fontSize = 12.sp, lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                })
            if (search.isNotEmpty()) IconButton(onClick = { search = "" }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Clear search", Modifier.size(17.dp))
            }
        }
        LazyRow(contentPadding = PaddingValues(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            rowItems(listOf("All", "Favorites", "PDF", "Word", "Excel", "TXT", "CSV", "PowerPoint", "EPUB", "RTF", "ODT", "Images")) { item ->
                FilterChip(selected = filter == item, onClick = { filter = item },
                    label = { Text(item, fontSize = 11.sp, lineHeight = 14.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LibraryFilter(category, categories, { category = it }, Modifier.weight(1f))
            LibraryFilter(tag, tags, { tag = it }, Modifier.weight(1f), Icons.Default.Label)
            LibraryFilter(order, listOf("Recent", "Name", "Type"), { order = it }, Modifier.width(92.dp), Icons.Default.Sort)
        }
        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { selected = ArrayList(entries.map { it.uri }) }) { Text("All shown (${entries.size})", fontSize = 11.sp) }
                IconButton(onClick = { selected = arrayListOf() }) { Icon(Icons.Default.Close, "Cancel selection") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LibrarySelectionAction("Assign category", { bulkValue = ""; bulkAction = "Category" }) {
                    Icon(Icons.Default.FolderOpen, "Assign category")
                }
                LibrarySelectionAction("Add tags", { bulkValue = ""; bulkAction = "Add tags" }) {
                    SelectionTagIcon(add = true)
                }
                LibrarySelectionAction("Remove tags", { bulkValue = ""; bulkAction = "Remove tags" }) {
                    SelectionTagIcon(add = false)
                }
                LibrarySelectionAction("Remove selected from Library", { remove = selected.toSet() }) {
                    Icon(Icons.Default.DeleteOutline, "Remove selected from Library", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider()
        if (entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (vm.recent.isEmpty()) "No documents yet" else "No matching documents",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
                Text("Add documents, books or images.", fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
            entries.forEachIndexed { index, doc ->
              item(key = doc.uri) {
                LaunchedEffect(doc.uri, vm.availabilityEpoch) { vm.checkAvailability(doc) }
                val missing = vm.unavailable[doc.uri] == true
                var menu by remember(doc.uri) { mutableStateOf(false) }
                var pdfMenu by remember(doc.uri) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().background(if (missing) MaterialTheme.colorScheme.errorContainer.copy(alpha = .30f) else Color.Transparent)
                    .combinedClickable(onClick = {
                        if (selected.isNotEmpty()) toggle(doc.uri)
                        else if (missing) unavailableDoc = doc
                        else onOpenFiltered(doc, entries)
                    }, onLongClick = { toggle(doc.uri) }).padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(formatColor(doc.type).copy(alpha = .12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        if (selected.isNotEmpty()) Checkbox(doc.uri in selected, { toggle(doc.uri) })
                        else Icon(if (missing) Icons.Default.WarningAmber else when (doc.type) {
                            "PDF" -> Icons.Default.PictureAsPdf
                            "Word" -> Icons.Default.Description
                            "Excel" -> Icons.Default.TableChart
                            else -> Icons.Default.TextSnippet
                        }, if (missing) "File unavailable" else doc.type, Modifier.size(19.dp), tint = if (missing) MaterialTheme.colorScheme.error else formatColor(doc.type))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(doc.name, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
                            color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(buildString {
                            if (missing) append("File unavailable · ")
                            append(doc.category)
                            if (doc.tags.isNotBlank()) append(" · ${doc.tags}")
                            append(if (doc.savedLocally) " · Saved locally" else if (doc.temporaryCopy) " · Temporary copy" else " · Linked")
                        }, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.favorite(doc) }, modifier = Modifier.size(40.dp)) {
                        Icon(if (doc.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite",
                            Modifier.size(19.dp), tint = if (doc.favorite) Color(0xFFF3B900) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.MoreVert, "Document actions", Modifier.size(19.dp))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DocumentActionItem("Select", Icons.Default.CheckBox, { menu = false; toggle(doc.uri) })
                            DocumentActionItem("Share file", Icons.Default.Share, { menu = false; onShare(doc) })
                            if (doc.type == "PDF") {
                                DocumentActionItem("Share with password", Icons.Default.Lock,
                                    { menu = false; onPdfAction(doc, PdfLibraryAction.ShareWithPassword) }, enabled = !missing)
                                DocumentActionItem("PDF tools", Icons.Default.Build,
                                    { menu = false; pdfMenu = true }, enabled = !missing, submenu = true)
                            }
                            if (doc.type != "PDF" && doc.type in com.blw.documentreader.data.DocumentFormats.mimeByType) {
                                DocumentActionItem("Convert to PDF", Icons.Default.PictureAsPdf,
                                    { menu = false; onConvert(doc) }, enabled = !missing)
                            }
                            if (doc.type in setOf("PDF", "EPUB", "TXT")) DocumentActionItem("Book mode", Icons.Default.MenuBook,
                                { menu = false; onBookMode(doc) }, enabled = !missing)
                            DocumentActionItem("Rename", Icons.Default.DriveFileRenameOutline, { menu = false; edit(doc) })
                            DocumentActionItem("Category & tags", Icons.Default.Label, { menu = false; edit(doc) })
                            if (!doc.savedLocally) DocumentActionItem("Save locally", Icons.Default.Download,
                                { menu = false; vm.saveToLibrary(Uri.parse(doc.uri), doc) })
                            DocumentActionItem("Locate file", Icons.Default.FolderOpen, { menu = false; onLocate(doc) })
                            HorizontalDivider()
                            DocumentActionItem("Remove from Library", Icons.Default.DeleteOutline,
                                { menu = false; remove = setOf(doc.uri) }, destructive = true)
                        }
                        DropdownMenu(pdfMenu, { pdfMenu = false }) {
                            DocumentActionItem("Add pages", Icons.Default.AddBox,
                                { pdfMenu = false; onPdfAction(doc, PdfLibraryAction.AddPages) })
                            DocumentActionItem("Delete pages", Icons.Default.DeleteOutline,
                                { pdfMenu = false; onPdfAction(doc, PdfLibraryAction.DeletePages) })
                            DocumentActionItem("Extract pages", Icons.Default.ContentCut,
                                { pdfMenu = false; onPdfAction(doc, PdfLibraryAction.ExtractPages) })
                            DocumentActionItem("Organize pages", Icons.Default.Dashboard,
                                { pdfMenu = false; onPdfAction(doc, PdfLibraryAction.OrganizePages) })
                            DocumentActionItem("Stamp PDF", Icons.Default.Approval,
                                { pdfMenu = false; onPdfAction(doc, PdfLibraryAction.Stamp) })
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
              }
              if (showAds && selected.isEmpty() && LibraryActions.adAfter(index + 1)) item(key = "inline-ad-$index") { InlineBanner() }
            }
        }
    }
    editing?.let { doc ->
        DocumentEditDialog(
            name = newName, extension = DocumentNames.extension(doc.type),
            category = newCategory, tags = newTags,
            categories = vm.categories, error = editError,
            onNameChange = { newName = it; editError = null },
            onCategoryChange = { newCategory = it }, onTagsChange = { newTags = it },
            onSave = {
                try { DocumentNames.renamed(newName, doc.type) } catch (ex: IllegalArgumentException) {
                    editError = ex.message; return@DocumentEditDialog
                }
                if (vm.updateDocumentInfo(doc, newName, newCategory, newTags)) editing = null
            }, onDismiss = { editing = null }
        )
    }
    remove?.let { uris -> WReaderAlertDialog(onDismissRequest = { remove = null }, title = { WReaderDialogTitle("Remove ${uris.size} document(s)?") },
        text = { Text("Remove from Library only. Original files and offline copies stay unchanged. You can undo this action.") },
        confirmButton = { Button(onClick = { vm.removeEntries(uris); selected = arrayListOf(); remove = null }) {
            Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Remove")
        } },
        dismissButton = { TextButton(onClick = { remove = null }) {
            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cancel")
        } }) }
    unavailableDoc?.let { doc -> WReaderAlertDialog(onDismissRequest = { unavailableDoc = null }, title = { WReaderDialogTitle("File unavailable") },
        text = { Column { Text(doc.name); Text("The file may be offline, moved or permission may have expired.")
            TextButton(onClick = { vm.checkAvailability(doc); unavailableDoc = null; onOpenFiltered(doc, entries) }) { DialogActionLabel("Retry", Icons.Default.Refresh) }
            TextButton(onClick = { unavailableDoc = null; onLocate(doc) }) { DialogActionLabel("Locate file", Icons.Default.FolderOpen) }
            TextButton(onClick = { unavailableDoc = null; remove = setOf(doc.uri) }) { DialogActionLabel("Remove from Library", Icons.Default.DeleteOutline) } } },
        confirmButton = { TextButton(onClick = { unavailableDoc = null }) { DialogActionLabel("Close", Icons.Default.Close) } }) }
    bulkAction?.let { action -> WReaderAlertDialog(onDismissRequest = { bulkAction = null }, title = { WReaderDialogTitle(action) },
        text = { Column {
            if (action == "Category") {
                LibraryFilter(bulkValue.ifBlank { "Choose category" }, listOf("Uncategorized") + vm.categories, { bulkValue = it })
                Text("Choose an existing category or enter a new one.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(bulkValue, { bulkValue = it }, label = { Text(if (action == "Category") "Category" else "Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
            Text("Apply to ${selected.size} selected document(s).", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { Button(enabled = bulkValue.isNotBlank(), onClick = {
            if (action == "Category") vm.bulk(selected.toSet(), category = bulkValue)
            else vm.bulk(selected.toSet(), tags = bulkValue, removeTags = action == "Remove tags")
            bulkAction = null; selected = arrayListOf()
        }) { DialogActionLabel("Apply", Icons.Default.Check) } }, dismissButton = { TextButton(onClick = { bulkAction = null }) { DialogActionLabel("Cancel", Icons.Default.Close) } }) }
    if (manageCategories) WReaderCompactDialog(title = "Categories", onDismissRequest = { manageCategories = false },
        content = {
            OutlinedTextField(categoryDraft, { categoryDraft = it.take(45); categoryError = null },
                modifier = Modifier.fillMaxWidth(), label = { Text("New category") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(10.dp),
                leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(19.dp)) },
                trailingIcon = {
                    IconButton(enabled = categoryDraft.isNotBlank(), onClick = {
                        val value = categoryDraft.trim()
                        if (value.equals("Uncategorized", true) || vm.categories.any { it.equals(value, true) }) {
                            categoryError = "This category already exists."
                        } else {
                            vm.addCategory(value); categoryDraft = ""; categoryError = null
                        }
                    }) { Icon(Icons.Default.Add, "Add category", tint = if (categoryDraft.isBlank())
                        MaterialTheme.colorScheme.onSurface.copy(alpha = .38f) else MaterialTheme.colorScheme.primary) }
                }, isError = categoryError != null)
            categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (vm.categories.isEmpty()) Text("No custom categories yet.", style = MaterialTheme.typography.bodySmall)
            Column {
                vm.categories.forEach { value -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { deleteCategory = value }) {
                        Icon(Icons.Default.DeleteOutline, "Delete $value", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    }
                } }
            }
        }, buttons = { TextButton(onClick = { manageCategories = false }) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Done")
        } })
    deleteCategory?.let { value -> WReaderAlertDialog(onDismissRequest = { deleteCategory = null }, title = { WReaderDialogTitle("Delete category?") },
        text = { Text("Remove “$value”? Its documents become Uncategorized. No files are deleted.") },
        confirmButton = { Button(onClick = { vm.deleteCategory(value); deleteCategory = null }) { DialogActionLabel("Delete category", Icons.Default.DeleteOutline) } },
        dismissButton = { TextButton(onClick = { deleteCategory = null }) { DialogActionLabel("Cancel", Icons.Default.Close) } }) }
}
