package com.blw.documentreader.ui

import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blw.documentreader.data.DocumentNames
import com.blw.documentreader.data.DocumentTypes
import com.blw.documentreader.pdf.PdfCopies
import com.blw.documentreader.pdf.PdfStamp
import com.blw.documentreader.pdf.PdfPrint
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StampDropdown(label: String, value: String, choices: List<String>, icon: ImageVector,
    enabled: Boolean, onSelect: (String) -> Unit, colors: Map<String, Color> = emptyMap()) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value, {}, Modifier.fillMaxWidth().menuAnchor(), readOnly = true,
            enabled = enabled, singleLine = true, label = { Text(label) },
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = {
                colors[value]?.let { Box(Modifier.size(18.dp).background(it, CircleShape)) }
                    ?: Icon(icon, null, Modifier.size(19.dp))
            }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) })
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice, style = MaterialTheme.typography.bodyMedium) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    leadingIcon = {
                        colors[choice]?.let { Box(Modifier.size(18.dp).background(it, CircleShape)) }
                            ?: Icon(icon, null, Modifier.size(18.dp))
                    }, trailingIcon = {
                        if (choice == value) Icon(Icons.Default.Check, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }, onClick = { expanded = false; onSelect(choice) })
            }
        }
    }
}

@Composable
fun PdfPasswordDialog(uri: Uri, name: String, onDismiss: () -> Unit, onBusy: (Boolean) -> Unit,
    onShare: (File, String) -> Unit, report: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { password = ""; confirmation = ""; onBusy(false) } }
    WReaderAlertDialog(onDismissRequest = { if (!working) onDismiss() }, title = { WReaderDialogTitle("Share with password") },
        containerColor = MaterialTheme.colorScheme.background,
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Creates a protected PDF copy. The original stays unchanged.")
            OutlinedTextField(password, { password = it; error = null }, label = { Text("Password (6–64 characters)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !working)
            OutlinedTextField(confirmation, { confirmation = it; error = null }, label = { Text("Confirm password") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
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
        }) { DialogActionLabel("Protect & share", Icons.Default.Lock) } }, dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { DialogActionLabel("Cancel", Icons.Default.Close) } })
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
    val presets = listOf("CONFIDENTIAL", "COPY", "PAID", "APPROVED", "DRAFT", "VOID", "RECEIVED")
    val stampColors = linkedMapOf("Red" to 0xffa21c27.toInt(), "Blue" to 0xff1c57be.toInt(),
        "Teal" to 0xff00899e.toInt(), "Green" to 0xff1f8454.toInt(), "Purple" to 0xff76439f.toInt(),
        "Orange" to 0xffc66515.toInt(), "Charcoal" to 0xff313e4d.toInt())
    if (saved == null) WReaderCompactDialog(onDismissRequest = { if (!working) { clearPreview(); onDismiss() } },
        title = "Stamp PDF", content = {
            Text("Saves a new copy. Original unchanged.", style = MaterialTheme.typography.bodySmall)
            StampDropdown("Stamp type", presets.firstOrNull { it == text } ?: "Custom text", presets + "Custom text",
                Icons.Default.Edit, !working, { value ->
                    clearPreview()
                    if (value != "Custom text") text = value else if (text in presets) text = ""
                })
            OutlinedTextField(text, { clearPreview(); text = it.take(64) }, label = { Text("Stamp text") },
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(19.dp)) },
                singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth())
            StampDropdown("Color", stampColors.entries.firstOrNull { it.value == color }?.key ?: "Red",
                stampColors.keys.toList(), Icons.Default.Palette, !working,
                { value -> clearPreview(); color = stampColors.getValue(value) }, stampColors.mapValues { Color(it.value) })
            StampDropdown("Position", position, listOf("Center", "Top", "Bottom", "Left", "Right", "Diagonal"),
                Icons.Default.OpenWith, !working, { value -> clearPreview(); position = value })
            Row { FilterChip(!all, { clearPreview(); all = false }, label = { Text("Page ${page+1}") }, enabled = !working); Spacer(Modifier.width(6.dp)); FilterChip(all, { clearPreview(); all = true }, label = { Text("All pages") }, enabled = !working) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(date, { clearPreview(); date = it }, enabled = !working)
                Text("Include date", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(filename, { filename = it }, label = { Text("New PDF name") },
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, Modifier.size(19.dp)) },
                singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth())
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
                    catch (_: Exception) { clearPreview(); error = "Could not stamp this PDF. Use supported text and an unprotected document." }
                    finally { working = false; onBusy(false) }
                }
            }) { Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Preview") }
        }, buttons = {
            TextButton(enabled = !working, onClick = { clearPreview(); onDismiss() }) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(enabled = staged != null && !working, onClick = {
                try { filename = DocumentNames.renamed(filename,"PDF"); working = true; onBusy(true); destination.launch(filename) }
                catch (ex: Exception) { working = false; onBusy(false); error = ex.message }
            }) { Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save copy") }
        })
    saved?.let { value -> WReaderAlertDialog(onDismissRequest = onDismiss, title = { WReaderDialogTitle("PDF saved") }, text = { Column {
        Text(savedName)
        Row { TextButton(onClick = { onDismiss(); onOpen(Uri.parse(value)) }) { DialogActionLabel("Open", Icons.Default.OpenInNew) }; TextButton(onClick = { onDismiss(); onShare(Uri.parse(value),savedName) }) { DialogActionLabel("Share", Icons.Default.Share) }; TextButton(onClick = { onDismiss(); PdfPrint.print(context,Uri.parse(value),savedName) }) { DialogActionLabel("Print", Icons.Default.Print) } }
    } }, confirmButton = { TextButton(onClick = onDismiss) { DialogActionLabel("Done", Icons.Default.Check) } }) }
}
