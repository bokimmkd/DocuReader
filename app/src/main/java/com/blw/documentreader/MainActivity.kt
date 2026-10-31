package com.blw.documentreader

import android.content.Intent
import android.content.ClipData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.blw.documentreader.data.DocumentStore
import com.blw.documentreader.data.DocumentTypes
import com.blw.documentreader.data.DocumentFormats
import com.blw.documentreader.updates.PlayUpdates
import com.blw.documentreader.reviews.PlayReview
import com.blw.documentreader.data.DocumentEntry
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.monetization.AdConsent
import com.blw.documentreader.monetization.BottomBanner
import com.blw.documentreader.monetization.RemoveAdsBilling
import com.blw.documentreader.pdf.PdfPrint
import com.blw.documentreader.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var incomingUri by mutableStateOf<Uri?>(null)
    private var permittedAds by mutableStateOf(false)
    private lateinit var consent: AdConsent
    private lateinit var billing: RemoveAdsBilling
    private lateinit var updates: PlayUpdates
    private lateinit var reviews: PlayReview
    private var outputBusy by mutableStateOf(false)
    private var convertOnReady by mutableStateOf(false)
    private var resumeTick by mutableIntStateOf(0)
    private var preparingShare by mutableStateOf(false)

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistIfAvailable(uri); incomingUri = uri; vm.openDocument(uri) }
    }
    private val pickForLibrary = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistIfAvailable(uri); vm.saveToLibrary(uri) }
    }
    private val convertDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistIfAvailable(uri); incomingUri = uri; convertOnReady = true; vm.openDocument(uri) }
    }
    private val pickTool = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistIfAvailable(uri); vm.beginTool(uri) }
    }
    private val addTool = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistIfAvailable(uri); vm.importTool(uri, true) }
    }
    private val saveTool = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveTool(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consent = AdConsent(this) { permittedAds = true }
        billing = RemoveAdsBilling(this) { vm.report(it) }
        updates = PlayUpdates(this, vm::report)
        reviews = PlayReview(this)
        reviews.observeUse()
        updates.allowedToPrompt = { !outputBusy && !preparingShare && !vm.busy &&
            (!BuildConfig.MONETIZATION_ENABLED || consent.settled) && !consent.privacyOpen }
        if (BuildConfig.MONETIZATION_ENABLED) {
            consent.start()
        }
        if (BuildConfig.BILLING_ENABLED) billing.start()
        handleIncomingIntent(intent)
        setContent { ApplicationScreen() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }
    private fun handleIncomingIntent(intent: Intent?) {
        if (outputBusy) { vm.report("Finish printing or saving before opening another document."); return }
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        } ?: intent?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) { persistIfAvailable(uri); incomingUri = uri; vm.openDocument(uri) }
    }
    private fun persistIfAvailable(uri: Uri) {
        // Persist only grants actually offered by SAF; attachment grants may be temporary.
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* temporary attachment permission; report expiration on reopen */ }
        catch (_: IllegalArgumentException) { /* non-persistable provider */ }
    }
    private fun openPicker() {
        openDocument.launch(DocumentFormats.pickerTypes)
    }
    private fun share(uri: Uri, mime: String, displayName: String? = null) {
        if (preparingShare || outputBusy) return
        val entry = vm.recent.find { it.uri == uri.toString() }
        val name = displayName ?: entry?.name ?: DocumentTypes.displayName(this, uri)
        val format = entry?.type ?: DocumentFormats.detect(name,mime) ?: DocumentTypes.PDF
        preparingShare = true
        lifecycleScope.launch {
            try {
                val shared = withContext(Dispatchers.IO) { DocumentStore(this@MainActivity).shareCopy(uri, name, format) }
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, shared)
                    putExtra(Intent.EXTRA_TITLE, name)
                    putExtra(Intent.EXTRA_SUBJECT, name)
                    clipData = ClipData.newUri(contentResolver, name, shared)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share $name"))
            } catch (ex: Exception) {
                vm.report("Could not share file: ${ex.message ?: "Check access and storage."}")
            } finally { preparingShare = false }
        }
    }
    private fun keepExport(uri: Uri, name: String) {
        val source = vm.current
        vm.saveToLibrary(uri, DocumentEntry(uri.toString(), name, DocumentTypes.PDF,
            openedAt = 0L, category = source?.category ?: "Uncategorized", tags = source?.tags ?: ""))
    }
    private fun shareDocument(doc: com.blw.documentreader.data.DocumentEntry) {
        val mime = DocumentFormats.mimeByType[doc.type] ?: "application/octet-stream"
        share(Uri.parse(doc.uri), mime, doc.name)
    }
    override fun onResume() {
        super.onResume()
        resumeTick++
        if (::reviews.isInitialized) reviews.observeUse()
        if (::billing.isInitialized && BuildConfig.BILLING_ENABLED) billing.restore(silent = true)
    }

    @Composable
    private fun ApplicationScreen() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var libraryFilter by rememberSaveable { mutableStateOf("All") }
        BackHandler(enabled = vm.current != null || tab == 2) {
            if (outputBusy) return@BackHandler
            convertOnReady = false
            if (vm.current != null) vm.closeDocument() else tab = 0
        }
        val dark = when (vm.appearance) {
            "Dark" -> true
            "Light" -> false
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        var password by remember { mutableStateOf("") }
        var infoPage by rememberSaveable { mutableStateOf<String?>(null) }
        BackHandler(infoPage != null) { infoPage = null }
        LaunchedEffect(vm.current?.uri) { if(vm.current != null) infoPage = null }
        LaunchedEffect(vm.reviewMoment, vm.current?.uri, vm.busy, outputBusy, tab, resumeTick) {
            if (vm.reviewMoment > 0 && tab == 0 && vm.current == null && !vm.busy && !outputBusy) {
                delay(4000)
                reviews.maybeAsk { tab == 0 && vm.current == null && !vm.busy && !outputBusy &&
                    !preparingShare && !updates.isBusy && !updates.hasNotice && consent.settled && !consent.privacyOpen }
            }
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.message) {
            vm.message?.let { msg ->
                val locate = msg.contains("Locate again", true) || msg.contains("expired", true)
                val result = snackbar.showSnackbar(msg, actionLabel = if (locate) "Locate" else null, withDismissAction = true)
                vm.dismissMessage()
                if (locate && result == SnackbarResult.ActionPerformed) openPicker()
            }
        }
        // Keep status and gesture navigation icons legible in both theme modes.
        SideEffect {
            val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
        }
        val blue = Color(0xFF0869E8)
        MaterialTheme(colorScheme = if (dark) darkColorScheme(
            primary = Color(0xFF3286EC), onPrimary = Color.White,
            primaryContainer = Color(0xFF1B3C63), onPrimaryContainer = Color(0xFFE5F2FF),
            secondary = Color(0xFF9FCBFF), onSecondary = Color(0xFF071725),
            secondaryContainer = Color(0xFF233F62), onSecondaryContainer = Color(0xFFE9F2FF),
            surface = Color(0xFF151C29), background = Color(0xFF101723),
            surfaceContainer = Color(0xFF1B2736), surfaceContainerLow = Color(0xFF172231),
            onSurface = Color(0xFFF0F4F9), onSurfaceVariant = Color(0xFFB3C2D3),
            outline = Color(0xFF8293A7)
        ) else lightColorScheme(
            primary = blue, onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBFF), onPrimaryContainer = Color(0xFF0B3975),
            secondary = Color(0xFF3C7CDC), onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDEBFC), onSecondaryContainer = Color(0xFF123B70),
            surface = Color.White, background = Color(0xFFF9FBFF),
            surfaceContainer = Color(0xFFF1F5FC), surfaceContainerLow = Color(0xFFF7F9FC),
            onSurface = Color(0xFF172338), onSurfaceVariant = Color(0xFF596C82)
        ), typography = Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
            bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 15.sp)
        )) {
            Scaffold(
                topBar = {
                    if (vm.current == null && !(tab == 2 && vm.toolReady)) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Row(
                                Modifier.fillMaxWidth().statusBarsPadding()
                                    .padding(horizontal = 14.dp, vertical = 3.dp)
                                    .height(60.dp)
                                    .background(
                                        if (dark) Color(0xFF252525) else Color(0xFFE9EFF6),
                                        RoundedCornerShape(17.dp)
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.wreader_header_transparent),
                                    contentDescription = "W Reader",
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.width(11.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("W Reader", fontSize = 19.sp, lineHeight = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("Documents · Books · Images", fontSize = 11.sp, lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    // Keep the ad and the 60dp rounded navigation card ABOVE system gestures.
                    val navBackground = if (dark) Color(0xFF252525) else Color(0xFFE9EFF6)
                    val inactiveTint = if (dark) Color(0xFFEAEDF3) else Color(0xFF43556B)
                    val activeTint = if (dark) Color(0xFF67B6FF) else Color(0xFF0869E8)
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .navigationBarsPadding()
                    ) {
                        if ((!BuildConfig.MONETIZATION_ENABLED || consent.settled) && !consent.privacyOpen && !outputBusy) {
                            UpdateBanner(updates.prompt, updates.transfer, updates.installFailed,
                                enabled = !preparingShare && !vm.busy,
                                restartEnabled = !preparingShare && !vm.busy,
                                onUpdate = updates::download, onLater = updates::later,
                                onRestart = updates::restart)
                        }
                        if (BuildConfig.MONETIZATION_ENABLED && !billing.adFree && permittedAds) BottomBanner()
                        if (vm.current == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                                    .height(56.dp)
                                    .background(navBackground, RoundedCornerShape(17.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val labels = listOf("Home", "Library", "Tools", "Settings")
                                val icons: List<ImageVector> = listOf(
                                    Icons.Outlined.Home,
                                    Icons.Outlined.FolderOpen,
                                    Icons.Outlined.Build,
                                    Icons.Outlined.Settings
                                )
                                labels.forEachIndexed { index, label ->
                                    val selected = tab == index
                                    val tint = if (selected) activeTint else inactiveTint
                                    Column(
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                            .clickable(onClickLabel = "Open $label") { tab = index },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            icons[index], contentDescription = label,
                                            tint = tint, modifier = Modifier.size(21.dp)
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            label, fontSize = 11.sp, maxLines = 1,
                                            color = tint,
                                            fontWeight = if (selected) FontWeight.SemiBold
                                                else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { insets ->
                Box(Modifier.fillMaxSize().padding(insets)) {
                    if (vm.current != null && vm.officeUri != null) {
                        if (vm.officeType !in setOf(DocumentTypes.WORD,DocumentTypes.EXCEL)) TextReaderScreen(
                            uri = vm.officeUri!!, name = vm.officeName, type = vm.officeType,
                            onBack = { if (!outputBusy) { convertOnReady = false; vm.closeDocument() } },
                            onShare = { vm.current?.let(::shareDocument) },
                            autoExport = convertOnReady, onAutoHandled = { convertOnReady = false },
                            onBusy = { outputBusy = it }, onSaved = ::keepExport,
                            onReady = vm::markReaderReady, report = vm::report
                        ) else OfficeReaderScreen(
                            uri = vm.officeUri!!,
                            name = vm.officeName,
                            type = vm.officeType,
                            onBack = { if (!outputBusy) { convertOnReady = false; vm.closeDocument() } },
                            onShare = { vm.current?.let(::shareDocument) },
                            autoExport = convertOnReady, onAutoHandled = { convertOnReady = false },
                            onBusy = { outputBusy = it }, onSaved = ::keepExport,
                            onReady = vm::markReaderReady, report = vm::report
                        )
                    } else if (vm.current != null && vm.pdf != null) {
                        ReaderScreen(vm, onBack = { if (!outputBusy) { convertOnReady = false; vm.closeDocument() } },
                            onShare = { vm.current?.let { share(Uri.parse(it.uri), "application/pdf") } },
                            onPrint = { vm.current?.let { PdfPrint.print(this@MainActivity, Uri.parse(it.uri), it.name) } },
                            onOrganize = { vm.current?.let { vm.beginTool(Uri.parse(it.uri)); tab = 2; vm.closeDocument() } })
                    } else when (tab) {
                        0 -> HomeScreen(vm, openPicker = ::openPicker,
                            reopen = { incomingUri = Uri.parse(it.uri); vm.openDocument(incomingUri!!) },
                            onLibrary = { libraryFilter = it; tab = 1 })
                        1 -> LibraryScreen(vm,
                            onOpen = { incomingUri = Uri.parse(it.uri); vm.openDocument(incomingUri!!) },
                            onShare = ::shareDocument,
                            onAdd = { pickForLibrary.launch(DocumentFormats.pickerTypes) },
                            initialFilter = libraryFilter)
                        2 -> if (vm.toolReady) ToolEditor(vm,
                            addPdf = { addTool.launch(arrayOf("application/pdf")) },
                            onSave = { saveTool.launch("WReader_edited.pdf") },
                            onBack = { tab = 0 },
                            openOutput = { incomingUri = it; vm.openDocument(it) },
                            shareOutput = { share(it, "application/pdf") })
                        else ToolsStartScreen(onChoosePdf = { pickTool.launch(arrayOf("application/pdf")) },
                            onConvert = { convertDocument.launch(DocumentFormats.pickerTypes.filterNot { it == "application/pdf" }.toTypedArray()) })
                        else -> SettingsScreen(vm, removeAds = { billing.buy() }, restore = { billing.restore() },
                            price = if (BuildConfig.BILLING_ENABLED) billing.localizedPrice else "Closed test",
                            adFree = billing.adFree,
                            checkUpdates = { updates.check(manual = true) }, rateApp = { reviews.openStore() },
                            privacy = { infoPage = "Privacy Policy" }, privacyOptions = { consent.showPrivacyOptions() },
                            feedback = { infoPage = "Help & Feedback" })
                    }
                    if (vm.busy || preparingShare || outputBusy) {
                        Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.medium,
                            shadowElevation = 6.dp, tonalElevation = 5.dp) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); Text("Processing locally…")
                            }
                        }
                    }
                }
            }
            if (vm.passwordNeeded && incomingUri != null) AlertDialog(
                onDismissRequest = { vm.dismissMessage() }, title = { Text("PDF password") },
                text = { OutlinedTextField(password, { password = it }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    label = { Text("Password") }) },
                confirmButton = { TextButton(onClick = {
                    incomingUri?.let { vm.openDocument(it, password) }; password = ""
                }) { Text("Unlock") } },
                dismissButton = { TextButton(onClick = { vm.closeDocument() }) { Text("Cancel") } })
            if (infoPage != null) InfoScreen(infoPage!!, onBack = { infoPage = null })

        }
    }
    override fun onDestroy() {
        billing.close()
        super.onDestroy()
    }
}
