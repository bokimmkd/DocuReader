package com.blw.documentreader.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.MainViewModel
import com.blw.documentreader.data.DocumentEntry
import com.blw.documentreader.data.DocumentNames

private val LibraryBlue = Color(0xFF0869E8)
private fun formatColor(type: String) = when (type) {
    "PDF" -> Color(0xFFEF5A54)
    "Word" -> Color(0xFF448EEE)
    "Excel" -> Color(0xFF21AA65)
    else -> Color(0xFF7896B7)
}

@Composable
private fun LibraryFilter(label: String, values: List<String>, onSelect: (String) -> Unit,
                          modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(40.dp),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
            shape = RoundedCornerShape(8.dp)) {
            Text(label, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(17.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (DocumentEntry) -> Unit, onAdd: () -> Unit,
                  onShare: (DocumentEntry) -> Unit, initialFilter: String = "All") {
    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All categories") }
    var tag by remember { mutableStateOf("All tags") }
    var order by remember { mutableStateOf("Recent") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.libraryReveal) {
        if (vm.libraryReveal > 0) {
            filter = "All"; search = ""; category = "All categories"; tag = "All tags"; order = "Recent"
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
        (filter == "All" || filter == "Recent" && it.openedAt > 0L ||
            filter == "Favorites" && it.favorite || com.blw.documentreader.data.DocumentFormats.matches(filter,it.type)) &&
        (category == "All categories" || it.category == category) &&
        (tag == "All tags" || it.tags.split(',').any { value -> value.trim().equals(tag, true) }) &&
        (search.isBlank() || listOf(it.name, it.type, it.category, it.tags)
            .any { value -> value.contains(search.trim(), ignoreCase = true) })
    }
    val entries = when (order) {
        "Name" -> data.sortedBy { it.name.lowercase() }
        "Type" -> data.sortedWith(compareBy({ it.type }, { it.name.lowercase() }))
        else -> data.sortedByDescending { if (filter == "Recent") it.openedAt else maxOf(it.openedAt, it.addedAt) }
    }
    val categories = listOf("All categories") + vm.recent.map { it.category }
        .filter { it.isNotBlank() }.distinct().sorted()
    val tags = listOf("All tags") + vm.recent.flatMap { it.tags.split(',') }
        .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted()

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                Text("${vm.recent.size} documents · Local or linked", fontSize = 10.sp, lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd, modifier = Modifier.height(38.dp), shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LibraryBlue, contentColor = Color.White)) {
                Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                Text("Add", fontSize = 12.sp, lineHeight = 16.sp)
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
            rowItems(listOf("All", "Recent", "Favorites", "PDF", "Word", "Excel", "TXT", "CSV", "PowerPoint", "EPUB", "RTF", "ODT", "Images")) { item ->
                FilterChip(selected = filter == item, onClick = { filter = item },
                    label = { Text(item, fontSize = 11.sp, lineHeight = 14.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LibraryFilter(category, categories, { category = it }, Modifier.weight(1f))
            LibraryFilter(tag, tags, { tag = it }, Modifier.weight(1f))
            LibraryFilter(order, listOf("Recent", "Name", "Type"), { order = it }, Modifier.width(82.dp))
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
            items(entries, key = { it.uri }) { doc ->
                var menu by remember(doc.uri) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { onOpen(doc) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(formatColor(doc.type).copy(alpha = .12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(when (doc.type) {
                            "PDF" -> Icons.Default.PictureAsPdf
                            "Word" -> Icons.Default.Description
                            "Excel" -> Icons.Default.TableChart
                            else -> Icons.Default.TextSnippet
                        }, doc.type, Modifier.size(19.dp), tint = formatColor(doc.type))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(doc.name, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(buildString {
                            append(doc.category)
                            if (doc.tags.isNotBlank()) append(" · ${doc.tags}")
                            append(if (doc.savedLocally) " · Offline" else " · Linked")
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
                            DropdownMenuItem(text = { Text("Share file") }, onClick = { menu = false; onShare(doc) })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; edit(doc) })
                            DropdownMenuItem(text = { Text("Category & tags") }, onClick = { menu = false; edit(doc) })
                            if (!doc.savedLocally) DropdownMenuItem(text = { Text("Keep offline copy") },
                                onClick = { menu = false; vm.saveToLibrary(Uri.parse(doc.uri), doc) })
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            }
        }
    }
    editing?.let { doc ->
        DocumentEditDialog(
            name = newName, extension = DocumentNames.extension(doc.type),
            category = newCategory, tags = newTags,
            categories = vm.recent.map { it.category }, error = editError,
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
}
