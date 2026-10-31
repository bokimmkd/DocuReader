package com.blw.documentreader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.blw.documentreader.ui.OfficeReaderScreen

class DirectExportTestActivity : ComponentActivity() {
    lateinit var vm: MainViewModel
    var saved: Uri? = null
    var savedName: String? = null
    var opened: Uri? = null
    var shared: Pair<Uri, String>? = null
    var busy = false
    var ready = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = requireNotNull(intent.data)
        vm = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            var message by remember { mutableStateOf("") }
            Column {
                if (message.isNotEmpty()) Text(message)
                OfficeReaderScreen(uri, "word.docx", "Word", onBack = {}, onShare = {},
                    onBusy = { busy = it }, onSaved = { u, n -> saved = u; savedName = n; vm.registerSavedPdf(u, n) },
                    onOpenSaved = { opened = it }, onShareSaved = { u, n -> shared = u to n },
                    onReady = { ready = true }, report = { message = it })
            }
        } } }
    }
}
