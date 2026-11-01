package com.blw.documentreader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.R

private val EditorBlue = Color(0xFF0869E8)

/** Edits Library metadata; saving never renames or deletes the external source. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditDialog(
    name: String, extension: String, category: String, tags: String,
    categories: List<String>, error: String?,
    onNameChange: (String) -> Unit, onCategoryChange: (String) -> Unit,
    onTagsChange: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var creatingCategory by remember { mutableStateOf(false) }
    val choices = (listOf("Uncategorized") + categories + category)
        .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    val accent = MaterialTheme.colorScheme.primary
    val fields = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent, focusedLabelColor = accent,
        cursorColor = accent, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )
    WReaderCompactDialog(
        onDismissRequest = onDismiss,
        title = "Edit document",
        content = {
            OutlinedTextField(name, onNameChange, Modifier.fillMaxWidth(),
                label = { Text("File name") }, singleLine = true, colors = fields,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(19.dp)) },
                shape = RoundedCornerShape(10.dp), isError = error != null,
                supportingText = { Text(error ?: ".$extension is kept automatically", fontSize = 11.sp, lineHeight = 14.sp) })
            if (creatingCategory) {
                OutlinedTextField(category, onCategoryChange, Modifier.fillMaxWidth(),
                    label = { Text("New category") }, singleLine = true, colors = fields,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(19.dp)) },
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        IconButton(onClick = { creatingCategory = false }) {
                            Icon(Icons.Default.ArrowDropDown, "Choose existing category")
                        }
                    })
            } else ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(category.ifBlank { "Uncategorized" }, {},
                    Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Category") }, readOnly = true, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(19.dp)) },
                    colors = fields, shape = RoundedCornerShape(10.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 260.dp)) {
                    choices.forEach { value ->
                        DropdownMenuItem(text = { Text(value, style = MaterialTheme.typography.bodyMedium) },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)) }, onClick = {
                            onCategoryChange(value); expanded = false
                        }, trailingIcon = {
                            if (value.equals(category, true)) Icon(Icons.Default.Check, null, tint = accent)
                        })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("New category", color = accent, style = MaterialTheme.typography.bodyMedium) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        leadingIcon = { Icon(Icons.Default.Add, null, tint = accent) },
                        onClick = { expanded = false; creatingCategory = true; onCategoryChange("") })
                }
            }
            OutlinedTextField(tags, onTagsChange, Modifier.fillMaxWidth(),
                label = { Text("Tags (comma separated)") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.LabelOutline, null, Modifier.size(19.dp)) },
                colors = fields, shape = RoundedCornerShape(10.dp),
                placeholder = { Text("invoice, apartment, 2026") })
            Text("This name is used when sharing. The original file stays unchanged.",
                fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EditorBlue, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                Icon(Icons.Default.Check, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/** Compact spacing while keeping fields scrollable above the keyboard and actions reachable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WReaderCompactDialog(title: String, onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit, buttons: @Composable RowScope.() -> Unit) {
    WReaderDialogFrame({ WReaderDialogTitle(title) }, onDismissRequest, content, buttons)
}

@Composable
internal fun WReaderAlertDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null, shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color = MaterialTheme.colorScheme.background, tonalElevation: Dp = 0.dp) {
    WReaderDialogFrame(title, onDismissRequest, { text() }, {
        dismissButton?.let { it(); Spacer(Modifier.width(8.dp)) }
        confirmButton()
    }, shape, containerColor, tonalElevation)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WReaderDialogFrame(title: @Composable () -> Unit, onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit, buttons: @Composable RowScope.() -> Unit,
    shape: Shape = RoundedCornerShape(22.dp), containerColor: Color = MaterialTheme.colorScheme.background,
    tonalElevation: Dp = 0.dp) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = Modifier.widthIn(max = 400.dp)) {
        Surface(shape = shape, color = containerColor, tonalElevation = tonalElevation) {
            Column(Modifier.padding(20.dp)) {
                title()
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End, content = buttons)
            }
        }
    }
}

@Composable
internal fun DialogActionLabel(label: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label)
    }
}

@Composable
internal fun WReaderDialogTitle(title: String) {
    val accent = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).background(EditorBlue.copy(alpha = .08f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.wreader_header_transparent), null, Modifier.size(34.dp))
        }
        Column {
            Text("W Reader", color = accent, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
