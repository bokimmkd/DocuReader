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
import androidx.compose.ui.graphics.luminance
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (icon, tint) = when (type) {
        "PDF" -> Icons.Default.PictureAsPdf to (if (isDark) Color(0xFFF05C55) else PdfRed)
        "Word" -> Icons.Default.Description to (if (isDark) Color(0xFF66B6FF) else WordBlue)
        else -> Icons.Default.TableChart to (if (isDark) Color(0xFF36C77B) else ExcelGreen)
    }
    Box(Modifier.size(38.dp).clip(MaterialTheme.shapes.small)
        .background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = type, tint = tint, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun DocumentRow(doc: DocumentEntry, onFavorite: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onOpen).padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DocumentBadge(doc.type)
        Spacer(Modifier.width(10.dp))
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
private fun FormatCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (isDark) when (title) {
        "PDF" -> Color(0xFFF05C55)
        "Word" -> Color(0xFF66B6FF)
        else -> Color(0xFF36C77B)
    } else tint
    val bg = if (isDark) Color(0xFF252525) else Color(0xFFEDF2F8)
    val labelColor = if (isDark) Color(0xFFF5F6FA) else Color(0xFF152438)
    Card(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accent else if (isDark) Color(0xFF252525) else Color(0xFFE1E7EF)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            Text(title, color = labelColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeScreen(
    vm: MainViewModel,
    openPicker: () -> Unit,
    reopen: (DocumentEntry) -> Unit,
    onLibrary: (String) -> Unit
) {
    val recents = vm.recent.filter { it.openedAt > 0L }
        .sortedByDescending { it.openedAt }.take(3)
    val favorites = vm.recent.filter { it.favorite }.take(2)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 9.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        item {
            Button(onClick = openPicker, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text("Open document", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FormatCard("PDF", Icons.Default.PictureAsPdf, PdfRed, false,
                    { onLibrary("PDF") }, modifier = Modifier.weight(1f))
                FormatCard("Word", Icons.Default.Description, WordBlue, false,
                    { onLibrary("Word") }, modifier = Modifier.weight(1f))
                FormatCard("Excel", Icons.Default.TableChart, ExcelGreen, false,
                    { onLibrary("Excel") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onLibrary("Recent") }) {
                    Text("See all", fontSize = 12.sp)
                }
            }
        }
        if (recents.isEmpty()) item {
            Text("Your opened documents will appear here",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, modifier = Modifier.padding(vertical = 9.dp))
        }
        items(recents, key = { "recent:" + it.uri }) { doc ->
            DocumentRow(doc, onFavorite = { vm.favorite(doc) }, onOpen = { reopen(doc) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Favorites", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onLibrary("Favorites") }) {
                    Text("See all", fontSize = 12.sp)
                }
            }
        }
        if (favorites.isEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.StarBorder, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Star documents for quick access",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp)
            }
        }
        items(favorites, key = { "favorite:" + it.uri }) { doc ->
            DocumentRow(doc, onFavorite = { vm.favorite(doc) }, onOpen = { reopen(doc) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(MaterialTheme.shapes.medium)
                .background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(23.dp), tint = color)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ToolsStartScreen(onChoosePdf: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PDF Tools", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Edit pages. Keep your original unchanged.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolChoice(Icons.Default.PostAdd, "Add Pages", "Import pages from another PDF", PdfRed, onChoosePdf)
        ToolChoice(Icons.Default.ContentCut, "Extract Pages", "Save selected pages to a new PDF", BrandBlue, onChoosePdf)
        ToolChoice(Icons.Default.SwapVert, "Organize Pages", "Reorder, rotate or delete pages", Color(0xFF6B59D9), onChoosePdf)
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
private fun SettingsAction(icon: androidx.compose.ui.graphics.vector.ImageVector,
                           title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
        .clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, removeAds: () -> Unit, restore: () -> Unit,
                   price: String, adFree: Boolean, privacy: () -> Unit,
                   privacyOptions: () -> Unit, feedback: () -> Unit) {
    var clearDialog by remember { mutableStateOf(false) }
    val priceAvailable = price.isNotBlank() && !price.contains("unavailable", ignoreCase = true)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Palette, null, modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("Appearance", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("System", "Light", "Dark").forEachIndexed { index, label ->
                SegmentedButton(selected = vm.appearance == label,
                    onClick = { vm.updateAppearance(label) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3)) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Remember reading position", fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
                Text("Continue where you stopped", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = vm.remember, onCheckedChange = vm::updateRemember)
        }
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Block, null, modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("Ads & purchase", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (adFree) "Ad-free is active" else "One small banner · No interruptions",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Remove Ads", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(if (adFree) "Purchased" else if (priceAvailable) "One-time · $price"
                    else "Available through Google Play",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = removeAds, enabled = !adFree && priceAvailable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandBlue, contentColor = Color.White
                ), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (adFree) "Active" else "Buy", fontSize = 13.sp)
            }
        }
        SettingsAction(Icons.Default.Restore, "Restore Purchase", restore)
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Tune, null, modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("Data & help", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        SettingsAction(Icons.Default.History, "Clear Recent History", { clearDialog = true })
        SettingsAction(Icons.Default.HelpOutline, "Help & Feedback", feedback)
        SettingsAction(Icons.Default.Shield, "Privacy Policy", privacy)
        SettingsAction(Icons.Default.Security, "Ad privacy options", privacyOptions)
        Text("W Reader ${com.blw.documentreader.BuildConfig.VERSION_NAME} · English", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp))
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false },
        title = { Text("Clear recent history?") },
        text = { Text("This removes only the history list. Your document files will not be deleted.") },
        confirmButton = { TextButton(onClick = { vm.clearRecent(); clearDialog = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
}
