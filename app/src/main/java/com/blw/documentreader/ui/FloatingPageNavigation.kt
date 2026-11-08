package com.blw.documentreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FloatingPageNavigation(page: Int, count: Int, previous: () -> Unit, next: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), shadowElevation = 5.dp,
        color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = previous, enabled = page > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
            Text("${page + 1} / $count", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = next, enabled = page < count - 1) { Icon(Icons.Default.ChevronRight, "Next page") }
        }
    }
}

@Composable
internal fun ReaderShareMenu(onShare: () -> Unit, onSaveLocal: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.Share, "Share options") }
        DropdownMenu(expanded, { expanded = false }) {
            DocumentActionItem("Share file", Icons.Default.Share, { expanded = false; onShare() })
            if (onSaveLocal != null) DocumentActionItem("Save to Library", Icons.Default.Download, { expanded = false; onSaveLocal() })
        }
    }
}
