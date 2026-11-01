package com.blw.documentreader.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class PdfLibraryAction { ShareWithPassword, AddPages, DeletePages, ExtractPages, OrganizePages, Stamp }

/** Shared file-menu styling; retain the standard 48dp minimum touch target. */
@Composable
internal fun DocumentActionItem(label: String, icon: ImageVector, onClick: () -> Unit,
    enabled: Boolean = true, destructive: Boolean = false, submenu: Boolean = false) {
    DropdownMenuItem(text = {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) MaterialTheme.colorScheme.error
                else if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
    }, leadingIcon = {
        Icon(icon, null, Modifier.size(18.dp), tint = if (destructive) MaterialTheme.colorScheme.error
            else if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
    }, trailingIcon = if (submenu) ({ Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp)) }) else null,
        contentPadding = PaddingValues(horizontal = 12.dp), enabled = enabled, onClick = onClick)
}
