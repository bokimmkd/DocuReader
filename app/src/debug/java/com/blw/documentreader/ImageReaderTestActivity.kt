package com.blw.documentreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.blw.documentreader.ui.TextReaderScreen

/** Diagnostic host only: unchanged production reader, no ads/billing and no checkout. */
class ImageReaderTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = requireNotNull(intent.data)
        val type = requireNotNull(intent.getStringExtra("type"))
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                    TextReaderScreen(uri, "image-check.$type", type,
                        onBack = { finish() }, onShare = {}, onBusy = {}, onSaved = { _, _ -> },
                        onReady = {}, report = {})
                }
            }
        }
    }
}
