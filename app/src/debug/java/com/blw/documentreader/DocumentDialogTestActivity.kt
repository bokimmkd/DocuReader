package com.blw.documentreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import com.blw.documentreader.ui.LibraryScreen

/** Uses the actual Library edit flow and metadata store; no checkout or ads. */
class DocumentDialogTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[MainViewModel::class.java]
        setContent {
            val dark = intent.getBooleanExtra("dark", false)
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF67B6FF))
                else lightColorScheme(primary = Color(0xFF0869E8))) {
                Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                    LibraryScreen(vm, onOpen = {}, onAdd = {}, onShare = {})
                }
            }
        }
    }
}
