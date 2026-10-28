package com.blw.documentreader

import android.content.Intent
import android.content.ClipData
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
import androidx.compose.material.icons.outlined.ContentCopy
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

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistIfAvailable(uri); incomingUri = uri; vm.openDocument(uri) }
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
        consent.start()
        billing.start()
        handleIncomingIntent(intent)
        setContent { ApplicationScreen() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }
    private fun handleIncomingIntent(intent: Intent?) {
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
        openDocument.launch(arrayOf("application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream"))
    }
    private fun share(uri: Uri, mime: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, "document", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share document"))
    }

    @Composable
    private fun ApplicationScreen() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        BackHandler(enabled = vm.current != null || tab == 1) {
            if (vm.current != null) vm.closeDocument() else tab = 0
        }
        val dark = when (vm.appearance) {
            "Dark" -> true
            "Light" -> false
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        var password by remember { mutableStateOf("") }
        var showPolicy by remember { mutableStateOf(false) }
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
        )) {
            Scaffold(
                topBar = {
                    if (vm.current == null && !(tab == 1 && vm.toolReady)) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Row(
                                Modifier.fillMaxWidth().statusBarsPadding()
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                                    .height(78.dp)
                                    .background(
                                        if (dark) Color(0xFF252525) else Color(0xFFE9EFF6),
                                        RoundedCornerShape(17.dp)
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(
                                        com.blw.documentreader.R.drawable.ic_wreader_symbol
                                    ),
                                    contentDescription = "W Reader",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(43.dp)
                                )
                                Spacer(Modifier.width(11.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("W Reader", fontSize = 19.sp, lineHeight = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("PDF · Word · Excel", fontSize = 11.sp, lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Read · Organize · Share", fontSize = 10.sp, lineHeight = 14.sp,
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
                        if (!billing.adFree && permittedAds) BottomBanner()
                        if (vm.current == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .height(60.dp)
                                    .background(navBackground, RoundedCornerShape(17.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val labels = listOf("Home", "Tools", "Settings")
                                val icons: List<ImageVector> = listOf(
                                    Icons.Outlined.Home,
                                    Icons.Outlined.ContentCopy,
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
                    if (vm.current != null && vm.pdf != null) {
                        ReaderScreen(vm, onBack = { vm.closeDocument() },
                            onShare = { vm.current?.let { share(Uri.parse(it.uri), "application/pdf") } },
                            onPrint = { vm.current?.let { PdfPrint.print(this@MainActivity, Uri.parse(it.uri), it.name) } },
                            onOrganize = { vm.current?.let { vm.beginTool(Uri.parse(it.uri)); tab = 1; vm.closeDocument() } })
                    } else when (tab) {
                        0 -> HomeScreen(vm, openPicker = ::openPicker, reopen = { incomingUri = Uri.parse(it.uri); vm.openDocument(incomingUri!!) })
                        1 -> if (vm.toolReady) ToolEditor(vm,
                            addPdf = { addTool.launch(arrayOf("application/pdf")) },
                            onSave = { saveTool.launch("WReader_edited.pdf") },
                            onBack = { tab = 0 },
                            openOutput = { incomingUri = it; vm.openDocument(it) },
                            shareOutput = { share(it, "application/pdf") })
                        else ToolsStartScreen { pickTool.launch(arrayOf("application/pdf")) }
                        else -> SettingsScreen(vm, removeAds = { billing.buy() }, restore = { billing.restore() },
                            price = billing.localizedPrice, adFree = billing.adFree,
                            privacy = { showPolicy = true }, privacyOptions = { consent.showPrivacyOptions() }, feedback = {
                                vm.report("Help: Open a document, use PDF Tools to save a new copy. For support, contact the publisher through the Play Store listing.")
                            })
                    }
                    if (vm.busy) {
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
            if (showPolicy) AlertDialog(onDismissRequest = { showPolicy = false },
                title = { Text("Privacy overview") },
                text = { Text("Documents are opened and processed locally. No document contents or filenames are sent to ads, billing or diagnostic services. AdMob may process device and advertising data subject to your consent. Before publishing, replace this overview with your publisher's complete public privacy policy URL and Play Data safety declarations.") },
                confirmButton = { TextButton(onClick = { showPolicy = false }) { Text("Close") } })
        }
    }
    override fun onDestroy() {
        billing.close()
        super.onDestroy()
    }
}
