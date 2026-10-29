package com.blw.documentreader.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.MainViewModel
import com.blw.documentreader.data.DocumentEntry

private val LibraryBlue = Color(0xFF0869E8)
private fun formatColor(type: String) = when (type) {
    "PDF" -> Color(0xFFEF5A54)
    "Word" -> Color(0xFF448EEE)
    else -> Color(0xFF21AA65)
}

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpen: (DocumentEntry) -> Unit,
    onAdd: () -> Unit,
    initialFilter: String = "All"
) {
    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All categories") }
    var order by remember { mutableStateOf("Recent") }
    var editing by remember { mutableStateOf<DocumentEntry?>(null) }
    var newCategory by remember { mutableStateOf("") }
    var newTags by remember { mutableStateOf("") }

    val data = vm.recent.filter {
        (filter == "All" || filter == "Recent" && it.openedAt > 0L ||
            filter == "Favorites" && it.favorite || filter == it.type) &&
        (category == "All categories" || it.category == category) &&
        (search.isBlank() || listOf(it.name, it.type, it.category, it.tags)
            .any { value -> value.contains(search.trim(), ignoreCase = true) })
    }
    val entries = when (order) {
        "Name" -> data.sortedBy { it.name.lowercase() }
        "Type" -> data.sortedWith(compareBy({ it.type }, { it.name.lowercase() }))
        else -> data.sortedByDescending { it.openedAt }
    }
    val categories = listOf("All categories") + vm.recent
        .map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Library", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${vm.recent.size} documents · Stored on this device or linked",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Button(onClick = onAdd, modifier = Modifier.height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LibraryBlue, contentColor = Color.White)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Text("Add", fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(19.dp)) },
            placeholder = { Text("Search name, category or tags", fontSize = 12.sp) },
            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )
        LazyRow(contentPadding = PaddingValues(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowItems(listOf("All", "Recent", "Favorites", "PDF", "Word", "Excel")) { item ->
                FilterChip(selected = filter == item, onClick = { filter = item },
                    label = { Text(item, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            var categoryExpanded by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.height(40.dp)) {
                    Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }) {
                    categories.forEach { value ->
                        DropdownMenuItem(text = { Text(value) }, onClick = {
                            category = value
                            categoryExpanded = false
                        })
                    }
                }
            }
            var sortExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { sortExpanded = true }) {
                    Icon(Icons.Default.Sort, null, modifier = Modifier.size(17.dp))
                    Text(order, fontSize = 11.sp)
                }
                DropdownMenu(expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }) {
                    listOf("Recent", "Name", "Type").forEach { value ->
                        DropdownMenuItem(text = { Text("Sort: $value") }, onClick = {
                            order = value; sortExpanded = false
                        })
                    }
                }
            }
        }
        HorizontalDivider()
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    Text("No matching documents", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add PDFs, Word or Excel files to organize them here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp)) {
                items(entries, key = { it.uri }) { doc ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(doc) }
                        .padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(formatColor(doc.type).copy(alpha = .12f),
                            RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                            Icon(when(doc.type) {
                                "PDF" -> Icons.Default.PictureAsPdf
                                "Word" -> Icons.Default.Description
                                else -> Icons.Default.TableChart
                            }, contentDescription = doc.type, tint = formatColor(doc.type),
                                modifier = Modifier.size(21.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    append(doc.category)
                                    if (doc.tags.isNotBlank()) append(" · ${doc.tags}")
                                    append(if (doc.savedLocally) " · Offline copy" else " · Linked file")
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { vm.favorite(doc) }, modifier = Modifier.size(39.dp)) {
                            Icon(if (doc.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                "Favorite", tint = if (doc.favorite) Color(0xFFF3B900)
                                    else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            editing = doc
                            newCategory = doc.category
                            newTags = doc.tags
                        }, modifier = Modifier.size(39.dp)) {
                            Icon(Icons.Default.Edit, "Edit category and tags", modifier = Modifier.size(19.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                }
            }
        }
    }

    editing?.let { doc ->
        AlertDialog(onDismissRequest = { editing = null },
            title = { Text("Organize document", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(doc.name, fontSize = 12.sp, maxLines = 2)
                    OutlinedTextField(newCategory, { newCategory = it },
                        label = { Text("Category") }, singleLine = true,
                        placeholder = { Text("Personal, Work, Finance…") })
                    OutlinedTextField(newTags, { newTags = it },
                        label = { Text("Tags (comma separated)") }, singleLine = true)
                    if (!doc.savedLocally) {
                        Text("This is a linked document. Save an offline copy so access doesn't expire.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            vm.updateLibraryInfo(doc, newCategory, newTags)
                            vm.saveToLibrary(Uri.parse(doc.uri), doc.copy(category=newCategory, tags=newTags))
                            editing = null
                        }) {
                            Icon(Icons.Default.SaveAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Keep offline copy")
                        }
                    } else Text("Offline copy stored privately on this device.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = {
                vm.updateLibraryInfo(doc, newCategory, newTags)
                editing = null
            }) { Text("Save tags") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}
