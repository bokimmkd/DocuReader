package com.blw.documentreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class PdfLibraryAction { ShareWithPassword, AddPages, DeletePages, ExtractPages, OrganizePages, Stamp, Sign }

/** Compact actions grow naturally for larger fonts and wrapping labels. */
@Composable
internal fun DocumentActionItem(label: String, icon: ImageVector, onClick: () -> Unit,
    enabled: Boolean = true, destructive: Boolean = false, submenu: Boolean = false) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(Modifier.widthIn(min = 208.dp, max = 320.dp).fillMaxWidth().heightIn(min = 44.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint.copy(alpha = if (enabled) 1f else .38f))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                .copy(alpha = if (enabled) 1f else .38f))
        if (submenu) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp)) }
    }
}
