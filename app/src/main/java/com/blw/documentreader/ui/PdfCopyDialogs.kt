package com.blw.documentreader.ui

import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.blw.documentreader.data.DocumentNames
import com.blw.documentreader.data.DocumentTypes
import com.blw.documentreader.pdf.PdfCopies
import com.blw.documentreader.pdf.PdfStamp
import com.blw.documentreader.pdf.PdfPrint
import kotlinx.coroutines.*
import java.io.File

@Composable
fun PdfPasswordDialog(uri: Uri, name: String, onDismiss: () -> Unit, onBusy: (Boolean) -> Unit,
    onShare: (File, String) -> Unit, report: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { password = ""; confirmation = ""; onBusy(false) } }
    AlertDialog(onDismissRequest = { if (!working) onDismiss() }, title = { WReaderDialogTitle("Share with password") },
        containerColor = MaterialTheme.colorScheme.background,
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Creates a protected PDF copy. The original stays unchanged.")
            OutlinedTextField(password, { password = it; error = null }, label = { Text("Password (6–64 characters)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !working)
            OutlinedTextField(confirmation, { confirmation = it; error = null }, label = { Text("Confirm password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !working)
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        } }, confirmButton = { Button(enabled = !working, onClick = {
            if (password.length !in 6..64 || password != confirmation) { error = "Use 6–64 characters and matching passwords."; return@Button }
            working = true; onBusy(true)
            scope.launch {
                try {
                    val file = PdfCopies.protect(context, uri, password)
                    val filename = DocumentNames.renamed(name.substringBeforeLast('.') + "_protected", "PDF")
                    password = ""; confirmation = ""; working = false; onBusy(false); onShare(file, filename); onDismiss()
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { error = "Could not protect this PDF. It may already be encrypted or unsupported." }
                finally { working = false; onBusy(false) }
            }
        }) { Text("Protect & share") } }, dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun PdfStampDialog(uri: Uri, name: String, page: Int, onDismiss: () -> Unit, onBusy: (Boolean) -> Unit,
    onSaved: (Uri, String) -> Unit, onOpen: (Uri) -> Unit, onShare: (Uri, String) -> Unit, report: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var text by rememberSaveable(uri.toString()) { mutableStateOf("COPY") }
    var position by rememberSaveable { mutableStateOf("Diagonal") }; var all by rememberSaveable { mutableStateOf(true) }
    var date by rememberSaveable { mutableStateOf(false) }; var color by rememberSaveable { mutableIntStateOf(0xffa21c27.toInt()) }
    var filename by rememberSaveable { mutableStateOf(DocumentNames.renamed(name.substringBeforeLast('.') + "_stamped", "PDF")) }
    var staged by rememberSaveable { mutableStateOf<String?>(null) }; var saved by rememberSaveable { mutableStateOf<String?>(null) }
    var savedName by remember { mutableStateOf("") }; var working by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    fun clearPreview() { staged?.let { File(it).delete() }; staged = null; preview = null; error = null }
    DisposableEffect(Unit) { onDispose { onBusy(false) } }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { output ->
        if (output == null) { working = false; onBusy(false) }
        else scope.launch {
            working = true; onBusy(true)
            try {
                val source = File(requireNotNull(staged)).canonicalFile
                require(source.parentFile == File(context.cacheDir, "pdf-exports").canonicalFile && source.isFile)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(output,"wt")?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("Folder unavailable") }
                runCatching { context.contentResolver.takePersistableUriPermission(output, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                savedName = DocumentTypes.displayName(context, output); onSaved(output, savedName); saved = output.toString(); clearPreview()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "PDF was not saved. Choose another folder." }
            finally { working = false; onBusy(false) }
        }
    }
    if (saved == null) AlertDialog(onDismissRequest = { if (!working) { clearPreview(); onDismiss() } },
        title = { WReaderDialogTitle("Stamp PDF") }, containerColor = MaterialTheme.colorScheme.background,
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Saves a new copy. Original unchanged.", style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("CONFIDENTIAL","COPY","PAID","APPROVED","DRAFT","VOID","RECEIVED").forEach { value -> item { FilterChip(text == value, { clearPreview(); text = value }, label = { Text(value) }, enabled = !working) } }
            }
            OutlinedTextField(text, { clearPreview(); text = it.take(64) }, label = { Text("Stamp text") }, singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Red" to 0xffa21c27, "Blue" to 0xff1c57be, "Teal" to 0xff00899e, "Green" to 0xff1f8454, "Purple" to 0xff76439f, "Orange" to 0xffc66515, "Charcoal" to 0xff313e4d).forEach { (label, value) -> item { FilterChip(color == value.toInt(), { clearPreview(); color = value.toInt() }, label = { Text(label) }, enabled = !working) } }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Center","Top","Bottom","Left","Right","Diagonal").forEach { value -> item { FilterChip(position == value, { clearPreview(); position = value }, label = { Text(value) }, enabled = !working) } } }
            Row { FilterChip(!all, { clearPreview(); all = false }, label = { Text("Page ${page+1}") }, enabled = !working); Spacer(Modifier.width(6.dp)); FilterChip(all, { clearPreview(); all = true }, label = { Text("All pages") }, enabled = !working) }
            Row { Checkbox(date, { clearPreview(); date = it }, enabled = !working); Text("Include date", Modifier.padding(top=12.dp)) }
            OutlinedTextField(filename, { filename = it }, label = { Text("New PDF name") }, singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            preview?.let { Image(it.asImageBitmap(), "Stamp preview for page ${page+1}", Modifier.fillMaxWidth().heightIn(max=260.dp)) }
            OutlinedButton(enabled = !working && text.isNotBlank(), onClick = {
                working = true; onBusy(true); error = null
                scope.launch {
                    try {
                        clearPreview(); val file = PdfCopies.stamp(context,uri,PdfStamp(text,color,position,all,page,date)); staged = file.absolutePath
                        preview = withContext(Dispatchers.IO) {
                            PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> renderer.openPage(page).use { p ->
                                Bitmap.createBitmap(480, (480f*p.height/p.width).toInt().coerceIn(1,1600),Bitmap.Config.ARGB_8888).also { bitmap -> bitmap.eraseColor(android.graphics.Color.WHITE); p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                            } }
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { error = "Could not stamp this PDF. Use supported text and an unprotected document." }
                    finally { working = false; onBusy(false) }
                }
            }) { Text("Preview") }
        } }, confirmButton = { Button(enabled = staged != null && !working, onClick = {
            try { filename = DocumentNames.renamed(filename,"PDF"); working = true; onBusy(true); destination.launch(filename) }
            catch (ex: Exception) { working = false; onBusy(false); error = ex.message }
        }) { Text("Save copy") } }, dismissButton = { TextButton(enabled = !working, onClick = { clearPreview(); onDismiss() }) { Text("Cancel") } })
    saved?.let { value -> AlertDialog(onDismissRequest = onDismiss, title = { WReaderDialogTitle("PDF saved") }, text = { Column {
        Text(savedName)
        Row { TextButton(onClick = { onDismiss(); onOpen(Uri.parse(value)) }) { Text("Open") }; TextButton(onClick = { onDismiss(); onShare(Uri.parse(value),savedName) }) { Text("Share") }; TextButton(onClick = { onDismiss(); PdfPrint.print(context,Uri.parse(value),savedName) }) { Text("Print") } }
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }) }
}
