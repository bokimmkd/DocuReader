package com.blw.documentreader.ui

import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blw.documentreader.data.DocumentNames
import com.blw.documentreader.printing.WebPdf
import kotlinx.coroutines.*
import java.io.File

/** Same explicit Print and Save PDF actions in Office/TXT readers. */
@Composable
fun DocumentOutputActions(web: WebView, name: String, ready: Boolean, excel: Boolean = false,
                          prepare: (Boolean, (String?) -> Unit) -> Unit = { _, done -> done(null) },
                          autoExport: Boolean = false, onAutoHandled: () -> Unit = {},
                          onBusy: (Boolean) -> Unit, onSaved: (Uri, String) -> Unit,
                          report: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember(web) { mutableStateOf(false) }
    var dialog by remember(web) { mutableStateOf(false) }
    var outputName by remember(name) { mutableStateOf(name.substringBeforeLast('.', name) + ".pdf") }
    var landscape by remember(web) { mutableStateOf(excel) }
    var pendingName by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { onBusy(false) } }
    BackHandler(working) { }
    fun busy(value: Boolean) { working = value; onBusy(value) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) { busy(false) } else {
            scope.launch {
                val temp = File.createTempFile("wreader-pdf-", ".pdf", context.cacheDir)
                try {
                    WebPdf.export(web, pendingName, temp, landscape)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                            ?: error("Cannot write to the selected folder.")
                    }
                    onSaved(uri, pendingName)
                    report("PDF saved.")
                } catch (error: Exception) {
                    if (error !is CancellationException) report("Could not save PDF: ${error.message ?: "Try again."}")
                } finally { temp.delete(); busy(false) }
            }
        }
    }
    LaunchedEffect(autoExport, ready) { if (autoExport && ready) { dialog = true; onAutoHandled() } }
    IconButton(enabled = ready && !working, onClick = { dialog = true }) {
        Icon(Icons.Default.PictureAsPdf, "Convert to PDF")
    }
    IconButton(enabled = ready && !working, onClick = {
        busy(true)
        prepare(landscape) { error ->
            if (error != null) { busy(false); report(error) }
            else try { WebPdf.print(web, name, landscape) { busy(false) } }
            catch (ex: Exception) { busy(false); report("Printing unavailable: ${ex.message}") }
        }
    }) { Icon(Icons.Default.Print, "Print") }
    if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text("Save as PDF") },
        text = { Column {
            OutlinedTextField(outputName, { outputName = it }, singleLine = true, label = { Text("File name") })
            if (excel) Text("Exports the worksheet currently selected in the reader.", style = MaterialTheme.typography.bodySmall)
            Row {
                FilterChip(selected = !landscape, onClick = { landscape = false }, label = { Text("Portrait") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = landscape, onClick = { landscape = true }, label = { Text("Landscape") })
            }
        } },
        confirmButton = { TextButton(onClick = {
            try { pendingName = DocumentNames.renamed(outputName, "PDF") }
            catch (ex: IllegalArgumentException) { report(ex.message ?: "Choose a file name."); return@TextButton }
            dialog = false; busy(true)
            prepare(landscape) { error ->
                if (error != null) { busy(false); report(error) }
                else save.launch(pendingName)
            }
        }) { Text("Save PDF") } },
        dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } })
}
