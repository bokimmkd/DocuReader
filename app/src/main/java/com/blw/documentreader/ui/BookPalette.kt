package com.blw.documentreader.ui

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

internal fun bookIsDark(theme: String?, appDark: Boolean) = when (theme) {
    "Dark" -> true
    "Light", "Sepia" -> false
    else -> appDark
}

internal fun bookColors(theme: String, appDark: Boolean): ColorScheme {
    val dark = bookIsDark(theme, appDark)
    val background = when { theme == "Sepia" -> Color(0xFFF5EAD4); dark -> Color(0xFF131B27); else -> Color(0xFFFFFDF8) }
    val foreground = when { theme == "Sepia" -> Color(0xFF443727); dark -> Color(0xFFE8EDF4); else -> Color(0xFF273044) }
    return if (dark) darkColorScheme(
        primary = Color(0xFF93C5FF), onPrimary = Color(0xFF10345C),
        primaryContainer = Color(0xFF24466B), onPrimaryContainer = foreground,
        secondaryContainer = Color(0xFF24466B), onSecondaryContainer = foreground,
        background = background, onBackground = foreground, surface = background, onSurface = foreground,
        surfaceContainer = Color(0xFF1C293A), surfaceContainerHigh = Color(0xFF233246),
        onSurfaceVariant = Color(0xFFBAC7D8), outline = Color(0xFF7F91A8)
    ) else lightColorScheme(
        primary = if (theme == "Sepia") Color(0xFF755324) else Color(0xFF0869E8),
        onPrimary = Color.White,
        primaryContainer = if (theme == "Sepia") Color(0xFFE6D5B6) else Color(0xFFDCEBFF),
        onPrimaryContainer = foreground,
        secondaryContainer = if (theme == "Sepia") Color(0xFFE6D5B6) else Color(0xFFDCEBFF),
        onSecondaryContainer = foreground,
        background = background, onBackground = foreground, surface = background, onSurface = foreground,
        surfaceContainer = background, surfaceContainerHigh = background,
        onSurfaceVariant = if (theme == "Sepia") Color(0xFF6B5940) else Color(0xFF596C82)
    )
}
