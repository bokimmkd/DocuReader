package com.blw.documentreader.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.data.DocumentNames
import com.blw.documentreader.data.DocumentTypes
import com.blw.documentreader.pdf.PdfPrint
import com.blw.documentreader.printing.DirectPdf
import com.blw.documentreader.printing.PdfSnapshot
import com.blw.documentreader.printing.WebPdf
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import java.io.File

private val PdfBlue = Color(0xFF0869E8)

/** Saves a real PDF first. Only explicit Print opens Android printing. */
@Composable
fun DocumentOutputActions(web: WebView, name: String, ready: Boolean, excel: Boolean = false,
    snapshot: suspend () -> PdfSnapshot,
    prepare: (Boolean, (String?) -> Unit) -> Unit = { _, done -> done(null) },
    autoExport: Boolean = false, onAutoHandled: () -> Unit = {},
    onBusy: (Boolean) -> Unit, onSaved: (Uri, String) -> Unit,
    onOpenSaved: (Uri) -> Unit, onShareSaved: (Uri, String) -> Unit, report: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember(web) { mutableStateOf(false) }
    var progress by remember(web) { mutableStateOf<String?>(null) }
    var job by remember(web) { mutableStateOf<Job?>(null) }
    var dialog by remember(web) { mutableStateOf(false) }
    var outputName by rememberSaveable(name) { mutableStateOf(name.substringBeforeLast('.', name) + ".pdf") }
    var landscape by rememberSaveable(name) { mutableStateOf(excel) }
    var inputError by remember(web) { mutableStateOf<String?>(null) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var stagedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var savedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var savedName by rememberSaveable { mutableStateOf("") }
    fun busy(value: Boolean) { working = value; onBusy(value) }
    fun staged(): File {
        val root = File(context.cacheDir, "pdf-exports").canonicalFile
        val file = File(requireNotNull(stagedPath)).canonicalFile
        require(file.parentFile == root && file.isFile) { "PDF preparation was interrupted. Please save again." }
        return file
    }
    fun clearStage() { runCatching { staged().delete() }; stagedPath = null }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) { clearStage(); progress = null; busy(false); report("PDF save canceled.") }
        else {
            busy(true); progress = "Saving PDF…"
            job = scope.launch {
                try {
                    val file = staged()
                    runCatching { context.contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            file.inputStream().use { input ->
                                val buf = ByteArray(64*1024)
                                while (true) { ensureActive(); val n = input.read(buf); if (n < 0) break; output.write(buf, 0, n) }
                            }
                        } ?: error("The chosen folder could not be written.")
                    }
                    savedName = withContext(Dispatchers.IO) { DocumentTypes.displayName(context, uri) }
                    savedUri = uri.toString(); onSaved(uri, savedName)
                } catch (cancel: CancellationException) { throw cancel }
                catch (ex: Exception) { report("PDF was not saved: ${ex.message ?: "Choose another folder."}") }
                finally { clearStage(); progress = null; busy(false) }
            }
        }
    }
    DisposableEffect(web) { onDispose { job?.cancel(); onBusy(false) } }
    LaunchedEffect(stagedPath) { if (stagedPath != null) busy(true) }
    BackHandler(working) { job?.cancel() }
    LaunchedEffect(autoExport, ready) { if (autoExport && ready) { dialog = true; onAutoHandled() } }
    IconButton(enabled = ready && !working, onClick = { inputError = null; dialog = true }) {
        Icon(Icons.Default.PictureAsPdf, "Convert to PDF")
    }
    IconButton(enabled = ready && !working, onClick = {
        busy(true)
        prepare(landscape) { reason ->
            if (reason != null) { busy(false); report(reason) }
            else try { WebPdf.print(web, name, landscape) { busy(false) } }
            catch (ex: Exception) { busy(false); report("Printing unavailable: ${ex.message}") }
        }
    }) { Icon(Icons.Default.Print, "Print") }
    if (dialog) AlertDialog(onDismissRequest = { dialog = false },
        title = { WReaderDialogTitle("Save as PDF") }, shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp,
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(outputName, { outputName = it; inputError = null }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("File name") }, shape = RoundedCornerShape(10.dp), isError = inputError != null,
                supportingText = { Text(inputError ?: "Choose where to save your PDF.") })
            if (excel) Text("Converts the selected worksheet.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !landscape, onClick = { landscape = false }, label = { Text("Portrait") })
                FilterChip(selected = landscape, onClick = { landscape = true }, label = { Text("Landscape") })
            }
        } },
        confirmButton = { Button(onClick = {
            try { pendingName = DocumentNames.renamed(outputName, "PDF") }
            catch (ex: IllegalArgumentException) { inputError = ex.message; return@Button }
            dialog = false; busy(true); progress = "Creating PDF…"
            job = scope.launch {
                try {
                    val reason = suspendCancellableCoroutine<String?> { c -> prepare(landscape) { if (c.isActive) c.resume(it) } }
                    require(reason == null) { reason.orEmpty() }
                    val file = DirectPdf.create(context, snapshot(), landscape)
                    stagedPath = file.absolutePath; progress = null; destination.launch(pendingName)
                } catch (timeout: TimeoutCancellationException) { clearStage(); report("PDF creation timed out. Please try again.") }
                catch (cancel: CancellationException) { clearStage(); report("PDF creation canceled."); throw cancel }
                catch (ex: Exception) { clearStage(); report("Could not create PDF: ${ex.message}") }
                finally { progress = null; if (stagedPath == null) busy(false) }
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = PdfBlue, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp)) { Text("Save PDF") } },
        dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } })
    if (progress != null) AlertDialog(onDismissRequest = { job?.cancel() },
        title = { WReaderDialogTitle(progress!!) }, containerColor = MaterialTheme.colorScheme.background,
        text = { LinearProgressIndicator(Modifier.fillMaxWidth(), color = PdfBlue) },
        confirmButton = { TextButton(onClick = { job?.cancel() }) { Text("Cancel") } })
    savedUri?.let { uriString ->
        val uri = Uri.parse(uriString)
        AlertDialog(onDismissRequest = { savedUri = null }, title = { WReaderDialogTitle("PDF saved") },
            shape = RoundedCornerShape(22.dp), containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp,
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(savedName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { savedUri = null; onOpenSaved(uri) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Open", fontSize = 12.sp) }
                    OutlinedButton(onClick = { savedUri = null; onShareSaved(uri, savedName) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Share", fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        savedUri = null; busy(true)
                        try { PdfPrint.print(context, uri, savedName) { busy(false) } }
                        catch (ex: Exception) { busy(false); report("Printing unavailable: ${ex.message}") }
                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Print", fontSize = 12.sp) }
                }
            } }, confirmButton = { TextButton(onClick = { savedUri = null }) { Text("Done") } })
    }
}
