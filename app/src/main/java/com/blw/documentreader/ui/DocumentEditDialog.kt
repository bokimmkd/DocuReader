package com.blw.documentreader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val choices = (listOf("Uncategorized", "Work", "Personal", "Finance") + categories + category)
        .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    val fields = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = EditorBlue, focusedLabelColor = EditorBlue,
        cursorColor = EditorBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(42.dp).background(EditorBlue.copy(alpha = .08f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.wreader_header_transparent), null, Modifier.size(34.dp))
                }
                Column {
                    Text("W Reader", color = EditorBlue, fontSize = 11.sp, lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Edit document", fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, onNameChange, Modifier.fillMaxWidth(),
                    label = { Text("File name") }, singleLine = true, colors = fields,
                    shape = RoundedCornerShape(10.dp), isError = error != null,
                    supportingText = { Text(error ?: ".$extension is kept automatically", fontSize = 11.sp) })
                if (creatingCategory) {
                    OutlinedTextField(category, onCategoryChange, Modifier.fillMaxWidth(),
                        label = { Text("New category") }, singleLine = true, colors = fields,
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
                        colors = fields, shape = RoundedCornerShape(10.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 260.dp)) {
                        choices.forEach { value ->
                            DropdownMenuItem(text = { Text(value) }, onClick = {
                                onCategoryChange(value); expanded = false
                            }, trailingIcon = {
                                if (value.equals(category, true)) Icon(Icons.Default.Check, null, tint = EditorBlue)
                            })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("New category", color = EditorBlue) },
                            leadingIcon = { Icon(Icons.Default.Add, null, tint = EditorBlue) },
                            onClick = { expanded = false; creatingCategory = true; onCategoryChange("") })
                    }
                }
                OutlinedTextField(tags, onTagsChange, Modifier.fillMaxWidth(),
                    label = { Text("Tags (comma separated)") }, singleLine = true,
                    colors = fields, shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("invoice, apartment, 2026") })
                Text("This name is used when sharing. The original file stays unchanged.",
                    fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onSave, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EditorBlue, contentColor = Color.White),
                modifier = Modifier.height(42.dp), contentPadding = PaddingValues(horizontal = 18.dp)) {
                Icon(Icons.Default.Check, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.primary) }
        }
    )
}
