@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.blw.documentreader.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.MainViewModel
import com.blw.documentreader.data.DocumentEntry
import com.blw.documentreader.pdf.PdfSession
import com.blw.documentreader.pdf.ToolPage
import com.blw.documentreader.pdf.thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val BrandBlue = Color(0xFF0869E8)
private val PdfRed = Color(0xFFE74742)
private val WordBlue = Color(0xFF176CE1)
private val ExcelGreen = Color(0xFF14895B)

@Composable
private fun DocumentBadge(type: String) {
    val (icon, tint) = when (type) {
        "PDF" -> Icons.Default.PictureAsPdf to PdfRed
        "Word" -> Icons.Default.Description to WordBlue
        else -> Icons.Default.TableChart to ExcelGreen
    }
    Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
        .background(tint.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = type, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun DocumentRow(doc: DocumentEntry, onFavorite: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onOpen).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DocumentBadge(doc.type)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(doc.type + if (doc.lastPage > 0) " · Page ${doc.lastPage + 1}" else " · Local file",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (doc.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (doc.favorite) "Remove favorite" else "Add favorite",
                tint = if (doc.favorite) Color(0xFFF3B900) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, openPicker: () -> Unit, reopen: (DocumentEntry) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val recent = vm.recent.filter { it.openedAt > 0L && (filter == "All" || it.type == filter) }
    val favorites = vm.recent.filter { it.favorite }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = openPicker, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = MaterialTheme.shapes.large) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(10.dp))
                Text("Open document", style = MaterialTheme.typography.titleSmall)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (recent.size > 5) TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show less" else "See all")
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "PDF", "Word", "Excel")) { type ->
                    FilterChip(selected = filter == type, onClick = { filter = type },
                        label = { Text(type) })
                }
            }
        }
        if (recent.isEmpty()) item {
            Text("Open a PDF, Word or Excel file to get started.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp))
        }
        items(if (showAll) recent else recent.take(5), key = { "recent:" + it.uri }) { doc ->
            DocumentRow(doc, onFavorite = { vm.favorite(doc) }, onOpen = { reopen(doc) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        item {
            Text("Favorites", modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (favorites.isEmpty()) item {
            Text("Tap the star beside a document to keep it here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 14.dp))
        }
        items(favorites, key = { "favorite:" + it.uri }) { doc ->
            DocumentRow(doc, onFavorite = { vm.favorite(doc) }, onOpen = { reopen(doc) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun ReaderScreen(vm: MainViewModel, onBack: () -> Unit,
                 onShare: () -> Unit, onPrint: () -> Unit, onOrganize: () -> Unit) {
    val doc = vm.current ?: return
    val session = vm.pdf ?: return
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = doc.lastPage.coerceIn(0, session.pageCount - 1))
    val scope = rememberCoroutineScope()
    var goDialog by remember(doc.uri) { mutableStateOf(false) }
    var thumbDialog by remember(doc.uri) { mutableStateOf(false) }
    var searchDialog by remember(doc.uri) { mutableStateOf(false) }
    var pageText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var zoom by remember { mutableFloatStateOf(1f) }
    var currentPage by remember(doc.uri) { mutableIntStateOf(doc.lastPage.coerceIn(0, session.pageCount - 1)) }
    LaunchedEffect(session) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            currentPage = index
            vm.pageChanged(index)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${currentPage + 1} / ${session.pageCount}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { vm.favorite(doc) }) { Icon(if (doc.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share original") }
            IconButton(onClick = onPrint) { Icon(Icons.Default.Print, "Print") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { thumbDialog = true }) { Icon(Icons.Default.GridView, null); Text("Pages") }
            TextButton(onClick = { searchDialog = true }) { Icon(Icons.Default.Search, null); Text("Find") }
            TextButton(onClick = { pageText = "${currentPage + 1}"; goDialog = true }) { Text("Go to") }
            TextButton(onClick = onOrganize) { Icon(Icons.Default.Build, null); Text("Tools") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom", style = MaterialTheme.typography.labelSmall)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..2.5f,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(session.pageCount, key = { it }) { index ->
                ReaderPage(session, index, zoom)
            }
        }
    }
    if (goDialog) AlertDialog(onDismissRequest = { goDialog = false }, title = { Text("Go to page") },
        text = { OutlinedTextField(pageText, { pageText = it }, label = { Text("1–${session.pageCount}") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            pageText.toIntOrNull()?.let { if (it in 1..session.pageCount) scope.launch { listState.animateScrollToItem(it - 1) } }
            goDialog = false
        }) { Text("Go") } }, dismissButton = { TextButton(onClick = { goDialog = false }) { Text("Cancel") } })
    if (searchDialog) AlertDialog(onDismissRequest = { searchDialog = false }, title = { Text("Search selectable text") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search PDF") })
                Spacer(Modifier.height(8.dp))
                Text("Image-only scans cannot be searched without OCR.", style = MaterialTheme.typography.bodySmall)
                vm.pdfMatches.take(30).forEach { index ->
                    TextButton(onClick = { scope.launch { listState.animateScrollToItem(index) }; searchDialog = false }) {
                        Text("Match on page ${index + 1}")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { vm.searchPdf(query) }) { Text("Find") } },
        dismissButton = { TextButton(onClick = { searchDialog = false }) { Text("Close") } })
    if (thumbDialog) AlertDialog(onDismissRequest = { thumbDialog = false }, title = { Text("Page thumbnails") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(session.pageCount) { index ->
                    Row(Modifier.fillMaxWidth().clickable {
                        scope.launch { listState.animateScrollToItem(index) }; thumbDialog = false
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PdfThumb(session, index)
                        Spacer(Modifier.width(12.dp)); Text("Page ${index + 1}")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { thumbDialog = false }) { Text("Close") } })
}

@Composable
private fun ReaderPage(session: PdfSession, index: Int, zoom: Float) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val targetPixels = (screenWidth * 2.4).toInt()
    val image by produceState<Bitmap?>(initialValue = null, session, index, targetPixels) {
        value = try { session.render(index, targetPixels) } catch (_: Exception) { null }
    }
    var translateX by remember(session, index) { mutableFloatStateOf(0f) }
    var translateY by remember(session, index) { mutableFloatStateOf(0f) }
    var gestureZoom by remember(session, index) { mutableFloatStateOf(1f) }
    val scale = (zoom * gestureZoom).coerceIn(1f, 4f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .pointerInput(session, index, zoom) {
                detectTransformGestures { _, pan, pinch, _ ->
                    gestureZoom = (gestureZoom * pinch).coerceIn(1f, 4f / zoom)
                    translateX += pan.x; translateY += pan.y
                    if (gestureZoom * zoom <= 1.01f) { translateX = 0f; translateY = 0f }
                }
            }) {
            if (image == null) Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else Image(image!!.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = translateX; translationY = translateY
            }, contentScale = ContentScale.FillWidth)
        }
        Spacer(Modifier.height(3.dp))
        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PdfThumb(session: PdfSession, index: Int) {
    val bitmap by produceState<Bitmap?>(null, session, index) {
        value = try { session.render(index, 136) } catch (_: Exception) { null }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Page ${index + 1} preview", Modifier.width(70.dp).height(95.dp), contentScale = ContentScale.Fit)
    else Box(Modifier.width(70.dp).height(95.dp), contentAlignment = Alignment.Center) { Text("…") }
}

@Composable
private fun ToolChoice(icon: androidx.compose.ui.graphics.vector.ImageVector,
                       title: String, detail: String, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium)
                .background(color.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(28.dp), tint = color)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ToolsStartScreen(onChoosePdf: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PDF Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Quick changes without altering your original PDF.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        ToolChoice(Icons.Default.PostAdd, "Add Pages", "Import selected pages from another PDF", PdfRed, onChoosePdf)
        ToolChoice(Icons.Default.ContentCut, "Extract Pages", "Create a new PDF from selected pages", BrandBlue, onChoosePdf)
        ToolChoice(Icons.Default.SwapVert, "Organize Pages", "Reorder, rotate or remove pages", Color(0xFF7253DE), onChoosePdf)
        Spacer(Modifier.height(5.dp))
        Text("Choose a PDF to start. Review all changes before saving a new file.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToolEditor(vm: MainViewModel, addPdf: () -> Unit, onSave: () -> Unit, onBack: () -> Unit,
               openOutput: (Uri) -> Unit, shareOutput: (Uri) -> Unit) {
    var showRange by rememberSaveable { mutableStateOf(false) }
    var rangeText by rememberSaveable { mutableStateOf("") }
    var warning by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }
    val pages = vm.toolPages
    val singleIndex = selected.singleOrNull()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Organize Pages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${pages.size} pages · New document", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(vm.toolTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (selected.size == pages.size) selected.clear()
                else { selected.clear(); selected.addAll(pages.indices.toList()) } }) {
                Text(if (selected.size == pages.size && pages.isNotEmpty()) "Clear selection" else "Select all")
            }
        }
        HorizontalDivider()
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            gridItemsIndexed(pages, key = { index, page -> "${page.file.path}_${page.originalIndex}_${index}" }) { index, page ->
                PdfPageTile(page, index, index in selected, onClick = {
                    if (index in selected) selected.remove(index) else selected.add(index)
                })
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            ToolAction(Icons.Default.AddBox, "Add", addPdf)
            ToolAction(Icons.Default.ContentCut, "Extract", {
                rangeText = ""; showRange = true
            })
            ToolAction(Icons.Default.KeyboardArrowLeft, "Move left", {
                singleIndex?.let { vm.swap(it, -1); selected.clear(); selected.add(it - 1) }
            }, enabled = singleIndex != null && singleIndex > 0)
            ToolAction(Icons.Default.KeyboardArrowRight, "Move right", {
                singleIndex?.let { vm.swap(it, 1); selected.clear(); selected.add(it + 1) }
            }, enabled = singleIndex != null && singleIndex < pages.size - 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            ToolAction(Icons.Default.DeleteOutline, "Delete", {
                if (selected.size >= pages.size) vm.report("A PDF must contain at least one page.")
                else { selected.sortedDescending().forEach { vm.remove(it) }; selected.clear() }
            }, enabled = selected.isNotEmpty())
            ToolAction(Icons.Default.RotateRight, "Rotate 90°", {
                selected.forEach { if (it in pages.indices) vm.rotate(it) }
            }, enabled = selected.isNotEmpty())
            Text("${selected.size} selected", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { warning = true }, enabled = pages.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)) {
            Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Save as New PDF")
        }
        vm.toolSaveResult?.let { uri ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { openOutput(uri) }) { Icon(Icons.Default.OpenInNew, null); Text("Open result") }
                TextButton(onClick = { shareOutput(uri) }) { Icon(Icons.Default.Share, null); Text("Share result") }
            }
        }
    }
    if (vm.sourceForSelection != null) {
        val source = vm.sourceForSelection!!
        val picked = remember(source.file.path) {
            mutableStateListOf<Int>().also { it.addAll(0 until source.count) }
        }
        var typedRange by remember(source.file.path) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { vm.clearSelection() },
            title = { Text(if (vm.addSource) "Add pages from PDF" else "Choose starting pages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${source.name} · ${source.count} pages", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { picked.clear(); picked.addAll(0 until source.count) }) { Text("All") }
                        TextButton(onClick = { picked.clear() }) { Text("Clear") }
                        Text("${picked.size} selected", style = MaterialTheme.typography.labelSmall)
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 310.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        gridItemsIndexed((0 until source.count).toList()) { _, index ->
                            PdfPageTile(ToolPage(source.file, index), index, index in picked) {
                                if (index in picked) picked.remove(index) else picked.add(index)
                                typedRange = ""
                            }
                        }
                    }
                    OutlinedTextField(typedRange, { typedRange = it }, singleLine = true,
                        label = { Text("Or enter pages: 2, 5, 10-12") },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                if (typedRange.isBlank()) vm.addSelectedPages(picked.sorted()) else vm.addSelected(typedRange)
            }) { Text(if (vm.addSource) "Add pages" else "Continue") } },
            dismissButton = { TextButton(onClick = { vm.clearSelection() }) { Text("Cancel") } })
    }
    if (showRange) AlertDialog(onDismissRequest = { showRange = false }, title = { Text("Extract selected pages") },
        text = { Column {
            Text("Keep only these positions from the current page order.")
            OutlinedTextField(rangeText, { rangeText = it }, label = { Text("e.g. 2, 5, 10-12") })
        } }, confirmButton = { TextButton(onClick = {
            try {
                val indexes = com.blw.documentreader.pdf.parsePageSelection(rangeText, pages.size)
                val copies = indexes.map { pages[it] }
                pages.clear(); pages.addAll(copies)
                selected.clear(); showRange = false
            } catch (e: Exception) { vm.report(e.message ?: "Invalid page selection") }
        }) { Text("Keep pages") } }, dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } })
    if (warning) AlertDialog(onDismissRequest = { warning = false }, title = { Text("Save new PDF?") },
        text = { Text("The original files stay unchanged. Page changes can invalidate digital signatures and may discard form fields, bookmarks, annotations, tags, layers or attachments. This engine cannot guarantee their preservation. Continue only if that is acceptable.") },
        confirmButton = { TextButton(onClick = { warning = false; onSave() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { warning = false }) { Text("Cancel") } })
}

@Composable
private fun ToolAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                       onClick: () -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PdfPageTile(page: ToolPage, index: Int, selected: Boolean, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, page.file.path, page.originalIndex) {
        value = try { thumbnail(page.file, page.originalIndex) } catch (_: Exception) { null }
    }
    Card(onClick = onClick, border = androidx.compose.foundation.BorderStroke(
        if (selected) 2.dp else 1.dp,
        if (selected) BrandBlue else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(126.dp), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp!!.asImageBitmap(), "Page ${index + 1}",
                Modifier.fillMaxSize().padding(6.dp).graphicsLayer { rotationZ = page.rotation.toFloat() },
                contentScale = ContentScale.Fit)
            else Icon(Icons.Default.PictureAsPdf, null, tint = PdfRed)
            if (selected) Icon(Icons.Default.CheckCircle, "Selected", tint = BrandBlue,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        Text("${index + 1}", modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, removeAds: () -> Unit, restore: () -> Unit,
                   price: String, adFree: Boolean, privacy: () -> Unit, privacyOptions: () -> Unit, feedback: () -> Unit) {
    var clearDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("System", "Light", "Dark").forEachIndexed { index, text ->
                SegmentedButton(selected = vm.appearance == text, onClick = { vm.updateAppearance(text) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(text) }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Remember last reading position"); Text("Resume PDFs where you left off", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = vm.remember, onCheckedChange = vm::updateRemember)
        }
        HorizontalDivider()
        Text("Ads & purchase", style = MaterialTheme.typography.titleMedium)
        Text(if (adFree) "Ad-free is active" else "Free includes one small banner. No interruptions.")
        Button(onClick = removeAds, enabled = !adFree) { Text(if (adFree) "Ads removed" else "Remove Ads · $price") }
        OutlinedButton(onClick = restore) { Text("Restore Purchase") }
        HorizontalDivider()
        Text("Data & help", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { clearDialog = true }) { Text("Clear Recent History") }
        TextButton(onClick = feedback) { Text("Help & Feedback") }
        TextButton(onClick = privacy) { Text("Privacy Policy") }
        TextButton(onClick = privacyOptions) { Text("Ad privacy options") }
        Text("App version 0.1.0 · English", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false },
        title = { Text("Clear recent history?") },
        text = { Text("This removes only the history list. Your document files will not be deleted.") },
        confirmButton = { TextButton(onClick = { vm.clearRecent(); clearDialog = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
}
