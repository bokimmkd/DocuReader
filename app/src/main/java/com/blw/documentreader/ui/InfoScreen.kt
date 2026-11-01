package com.blw.documentreader.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.BuildConfig
import com.blw.documentreader.R

private data class InfoSection(val heading: String?, val paragraphs: List<String>)

@Composable
fun InfoScreen(title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val privacy = title == "Privacy Policy"
    val sections = remember(title) {
        val text = context.assets.open(if (privacy) "privacy_policy.md" else "help.md")
            .bufferedReader().use { it.readText() }
        val result = mutableListOf<InfoSection>()
        var heading: String? = null
        var paragraphs = mutableListOf<String>()
        text.split("\n\n").forEach { block ->
            if (block.startsWith("## ")) {
                if (heading != null || paragraphs.isNotEmpty()) result += InfoSection(heading, paragraphs.toList())
                heading = block.removePrefix("## ").trim(); paragraphs = mutableListOf()
            } else if (!block.startsWith("# ") && block.isNotBlank()) paragraphs += block.trim()
        }
        if (heading != null || paragraphs.isNotEmpty()) result += InfoSection(heading, paragraphs.toList())
        result
    }
    fun contactSupport() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:bokimk.ap@gmail.com"))
                .putExtra(Intent.EXTRA_SUBJECT, "W Reader ${BuildConfig.VERSION_NAME} — ${if (privacy) "Privacy" else "Help"}"))
        }.onFailure { android.widget.Toast.makeText(context, "Email bokimk.ap@gmail.com", android.widget.Toast.LENGTH_LONG).show() }
    }
    BackHandler { onBack() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = ::contactSupport) { Icon(Icons.Default.Email, "Contact support", tint = MaterialTheme.colorScheme.primary) }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(painterResource(R.drawable.wreader_header_transparent), "W Reader logo", Modifier.size(48.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (privacy) "W Reader — Privacy Policy" else "W Reader Help", fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Documents · Books · Images", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                items(sections) { section ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            section.heading?.let { Text(it, fontSize = 16.sp, lineHeight = 22.sp,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    section.paragraphs.forEach { InfoParagraph(it) }
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = ::contactSupport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Contact support")
                    }
                    Text("W Reader ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 12.dp), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoParagraph(markdown: String) {
    val text = markdown.replace("**", "").replace(Regex("(?m)^- "), "• ")
    val primary = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        var start = 0
        Regex("\\[([^]]+)]\\((https://[^)]+)\\)").findAll(text).forEach { match ->
            append(text.substring(start, match.range.first))
            withLink(LinkAnnotation.Url(match.groupValues[2], TextLinkStyles(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)))) {
                append(match.groupValues[1])
            }
            start = match.range.last + 1
        }
        append(text.substring(start))
    }
    Text(annotated, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface)
}
