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
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.doOnPreDraw
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
    private var bookModeOnOpen by mutableStateOf(false)
    private var resumeTick by mutableIntStateOf(0)
    private var optionalServicesReady = false
    private var preparingShare by mutableStateOf(false)
    private var protectedShare by mutableStateOf<Pair<Uri, String>?>(null)
    private var stampRequest by mutableStateOf<Triple<Uri, String, Int>?>(null)
    private var locating: DocumentEntry? = null
    private val locateDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val doc = locating; locating = null
        if (uri != null && doc != null) { persistIfAvailable(uri); vm.relink(doc, uri) }
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistIfAvailable(uri); incomingUri = uri; bookModeOnOpen = false; vm.openDocument(uri) }
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
        setContent { ApplicationScreen() }
        // The reader shell must be drawn before optional SDK initialization or provider IO.
        window.decorView.doOnPreDraw {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    optionalServicesReady = true
                    if (BuildConfig.MONETIZATION_ENABLED) consent.start()
                    if (BuildConfig.BILLING_ENABLED) billing.start()
                    handleIncomingIntent(intent)
                }
            }
        }
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
        if (uri != null) {
            val durableGrant = persistIfAvailable(uri)
            incomingUri = uri
            bookModeOnOpen = false
            // Messaging providers (Viber, WhatsApp, email) often grant access only
            // for this intent. Copy while permission is live, not when Library
            // later attempts to reopen an expired external URI.
            vm.openDocument(uri, retainTemporary = !durableGrant)
        }
    }
    private fun persistIfAvailable(uri: Uri): Boolean {
        if (uri.scheme != "content" || uri.authority == "${packageName}.files") return true
        return try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (_: SecurityException) {
            false // Provider offers a temporary read grant only.
        } catch (_: IllegalArgumentException) {
            false
        }
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
        vm.registerSavedPdf(uri, name)
    }
    private fun shareGenerated(file: java.io.File, name: String) {
        lifecycleScope.launch {
            try {
                val shared = withContext(Dispatchers.IO) {
                    val dir = java.io.File(cacheDir, "shared-documents/${java.util.UUID.randomUUID()}").also { check(it.mkdirs()) }
                    val copy = java.io.File(dir, com.blw.documentreader.data.DocumentNames.renamed(name,"PDF"))
                    try { file.copyTo(copy); androidx.core.content.FileProvider.getUriForFile(this@MainActivity,"$packageName.files",copy) }
                    finally { file.delete() }
                }
                share(shared, "application/pdf", name)
            } catch (_: Exception) { vm.report("Could not share the protected copy. Original unchanged.") }
        }
    }
    private fun shareDocument(doc: com.blw.documentreader.data.DocumentEntry) {
        val mime = DocumentFormats.mimeByType[doc.type] ?: "application/octet-stream"
        share(Uri.parse(doc.uri), mime, doc.name)
    }
    override fun onResume() {
        super.onResume()
        resumeTick++
        vm.refreshAvailability()
        if (::reviews.isInitialized) reviews.observeUse()
        if (::billing.isInitialized && BuildConfig.BILLING_ENABLED && optionalServicesReady) billing.restore(silent = true)
    }

    @Composable
    private fun ApplicationScreen() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var libraryFilter by rememberSaveable { mutableStateOf("All") }
        var pendingPdfAction by rememberSaveable { mutableStateOf<PdfLibraryAction?>(null) }
        var toolFromLibrary by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = vm.current != null || tab == 2) {
            if (outputBusy) return@BackHandler
            convertOnReady = false
            pendingPdfAction = null
            if (vm.current != null) vm.closeDocument() else tab = if (toolFromLibrary && vm.toolReady) 1 else 0
        }
        val dark = when (vm.appearance) {
            "Dark" -> true
            "Light" -> false
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        // Scoped to the current document; a book may have its own theme while the app stays Light.
        var bookTheme by remember(vm.current?.uri) { mutableStateOf<String?>(null) }
        val readingTheme = if (vm.current != null) bookTheme else null
        val readingColors = readingTheme?.let { bookColors(it, dark) }
        val systemDark = bookIsDark(readingTheme, dark)
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
                val undo = msg.startsWith("Removed ") && vm.canUndoRemoval
                val result = snackbar.showSnackbar(msg, actionLabel = if (undo) "Undo" else if (locate) "Locate" else null, withDismissAction = true)
                vm.dismissMessage()
                if (result == SnackbarResult.ActionPerformed) { if (undo) vm.undoRemoval() else if (locate) openPicker() }
            }
        }
        // Keep status and gesture navigation icons legible in both theme modes.
        SideEffect {
            val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            bars.isAppearanceLightStatusBars = !systemDark
            bars.isAppearanceLightNavigationBars = !systemDark
            // API 35+ draws system bars over our Scaffold. Older releases use these colors.
            @Suppress("DEPRECATION")
            window.statusBarColor = (readingColors?.background ?: if (dark) Color(0xFF101723) else Color(0xFFF9FBFF)).toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = (readingColors?.background ?: if (dark) Color(0xFF101723) else Color(0xFFF9FBFF)).toArgb()
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = readingTheme == null
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
                containerColor = readingColors?.background ?: MaterialTheme.colorScheme.background,
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
                            .background(readingColors?.background ?: MaterialTheme.colorScheme.background)
                            .navigationBarsPadding()
                    ) {
                        if ((!BuildConfig.MONETIZATION_ENABLED || consent.settled) && !consent.privacyOpen && !outputBusy) {
                            UpdateBanner(updates.prompt, updates.transfer, updates.installFailed,
                                enabled = !preparingShare && !vm.busy,
                                restartEnabled = !preparingShare && !vm.busy,
                                onUpdate = updates::download, onLater = updates::later,
                                onRestart = updates::restart)
                        }
                        if (BuildConfig.MONETIZATION_ENABLED && !billing.adFree && permittedAds && !(vm.current == null && tab == 1)) BottomBanner()
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
                            onOpenSaved = { bookModeOnOpen = false; incomingUri = it; convertOnReady = false; vm.openDocument(it) },
                            onShareSaved = { uri, fileName -> share(uri, "application/pdf", fileName) },
                            onReady = vm::markReaderReady, report = vm::report, initialBookMode = bookModeOnOpen,
                            onBookTheme = { bookTheme = it },
                            imagePosition = if(vm.imageQueue.isNotEmpty()) "${vm.imageQueue.indexOf(vm.current?.uri)+1} / ${vm.imageQueue.size}" else null,
                            onPrevious = if(vm.imageQueue.indexOf(vm.current?.uri)>0) ({vm.adjacentImage(-1)}) else null,
                            onNext = if(vm.imageQueue.indexOf(vm.current?.uri) in 0 until vm.imageQueue.lastIndex) ({vm.adjacentImage(1)}) else null
                        ) else OfficeReaderScreen(
                            uri = vm.officeUri!!,
                            name = vm.officeName,
                            type = vm.officeType,
                            onBack = { if (!outputBusy) { convertOnReady = false; vm.closeDocument() } },
                            onShare = { vm.current?.let(::shareDocument) },
                            autoExport = convertOnReady, onAutoHandled = { convertOnReady = false },
                            onBusy = { outputBusy = it }, onSaved = ::keepExport,
                            onOpenSaved = { bookModeOnOpen = false; incomingUri = it; convertOnReady = false; vm.openDocument(it) },
                            onShareSaved = { uri, fileName -> share(uri, "application/pdf", fileName) },
                            onReady = vm::markReaderReady, report = vm::report
                        )
                    } else if (vm.current != null && vm.pdf != null) {
                        ReaderScreen(vm, onBack = { if (!outputBusy) { convertOnReady = false; vm.closeDocument() } },
                            readingMode = bookModeOnOpen, onDocumentView = { bookModeOnOpen = false },
                            onShare = { vm.current?.let(::shareDocument) },
                            onPrint = { vm.current?.let { PdfPrint.print(this@MainActivity, Uri.parse(it.uri), it.name) } },
                            onOrganize = { vm.current?.let { toolFromLibrary = false; vm.beginTool(Uri.parse(it.uri)); tab = 2; vm.closeDocument() } },
                            onStamp = { page -> vm.current?.let { stampRequest = Triple(Uri.parse(it.uri), it.name, page) } },
                            onProtect = { vm.current?.let { protectedShare = Uri.parse(it.uri) to it.name } })
                    } else when (tab) {
                        0 -> HomeScreen(vm, openPicker = ::openPicker,
                            reopen = { bookModeOnOpen = false; incomingUri = Uri.parse(it.uri); vm.openDocument(incomingUri!!) },
                            onLibrary = { vm.showLibrary(it); libraryFilter = it; tab = 1 })
                        1 -> LibraryScreen(vm,
                            onOpen = { bookModeOnOpen = false; incomingUri = Uri.parse(it.uri); vm.openDocument(incomingUri!!) },
                            onShare = ::shareDocument,
                            onConvert = { doc ->
                                incomingUri = Uri.parse(doc.uri)
                                bookModeOnOpen = false; convertOnReady = true
                                vm.openDocument(incomingUri!!)
                            },
                            onBookMode = { doc -> bookModeOnOpen = true; incomingUri = Uri.parse(doc.uri); vm.openDocument(incomingUri!!) },
                            onPdfAction = { doc, action ->
                                val uri = Uri.parse(doc.uri)
                                when (action) {
                                    PdfLibraryAction.ShareWithPassword -> protectedShare = uri to doc.name
                                    PdfLibraryAction.Stamp -> stampRequest = Triple(uri, doc.name, 0)
                                    else -> {
                                        pendingPdfAction = action; toolFromLibrary = true
                                        vm.beginTool(uri, selectStartingPages = false); tab = 2
                                    }
                                }
                            },
                            onAdd = { pickForLibrary.launch(DocumentFormats.pickerTypes) },
                            initialFilter = libraryFilter,
                            onOpenFiltered = { doc, entries -> bookModeOnOpen = false; incomingUri = Uri.parse(doc.uri); vm.openFromLibrary(doc, entries) },
                            onLocate = { doc -> locating = doc; locateDocument.launch(DocumentFormats.pickerTypes) },
                            showAds = BuildConfig.MONETIZATION_ENABLED && !billing.adFree && permittedAds)
                        2 -> if (vm.toolReady) ToolEditor(vm,
                            addPdf = { addTool.launch(arrayOf("application/pdf")) },
                            onSave = { saveTool.launch("WReader_edited.pdf") },
                            onBack = { pendingPdfAction = null; tab = if (toolFromLibrary) 1 else 0 },
                            openOutput = { bookModeOnOpen = false; incomingUri = it; vm.openDocument(it) },
                            shareOutput = { share(it, "application/pdf") },
                            initialAction = pendingPdfAction, onInitialActionHandled = { pendingPdfAction = null })
                        else ToolsStartScreen(onChoosePdf = { pendingPdfAction = null; toolFromLibrary = false; pickTool.launch(arrayOf("application/pdf")) },
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
            if (vm.passwordNeeded && incomingUri != null) WReaderAlertDialog(
                onDismissRequest = { vm.dismissMessage() }, title = { WReaderDialogTitle("PDF password") },
                text = { OutlinedTextField(password, { password = it }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    label = { Text("Password") }) },
                confirmButton = { TextButton(onClick = {
                    incomingUri?.let { vm.openDocument(it, password) }; password = ""
                }) { DialogActionLabel("Unlock", Icons.Default.LockOpen) } },
                dismissButton = { TextButton(onClick = { vm.closeDocument() }) { DialogActionLabel("Cancel", Icons.Default.Close) } })
            if (infoPage != null) InfoScreen(infoPage!!, onBack = { infoPage = null })
            protectedShare?.let { (uri, name) -> PdfPasswordDialog(uri, name, onDismiss = { protectedShare = null },
                onBusy = { outputBusy = it }, onShare = ::shareGenerated, report = vm::report) }
            stampRequest?.let { (uri, name, page) ->
                PdfStampDialog(uri, name, page, onDismiss = { stampRequest = null },
                    onBusy = { outputBusy = it }, onSaved = ::keepExport,
                    onOpen = { stampRequest = null; incomingUri = it; vm.openDocument(it) },
                    onShare = { uri, name -> share(uri, "application/pdf", name) }, report = vm::report)
            }

        }
    }
    override fun onDestroy() {
        billing.close()
        super.onDestroy()
    }
}
