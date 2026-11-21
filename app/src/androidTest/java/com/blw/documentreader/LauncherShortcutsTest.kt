package com.blw.documentreader

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.blw.documentreader.data.LibraryState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class LauncherShortcutsTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val device get() = UiDevice.getInstance(inst)
    private fun vm(activity: MainActivity) = ViewModelProvider(activity)[MainViewModel::class.java]
    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = System.currentTimeMillis() + 15000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            Thread.sleep(50)
        } while (System.currentTimeMillis() < deadline)
        fail("Shortcut did not reach its destination")
    }
    private class PickerMonitor(private val result: Intent? = null) : Instrumentation.ActivityMonitor() {
        val requests = CopyOnWriteArrayList<Intent>()
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
            requests += Intent(intent)
            return Instrumentation.ActivityResult(if (result == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, result)
        }
    }
    private fun launch(destination: LauncherShortcuts.Destination) =
        context.startActivity(LauncherShortcuts.intent(context, destination))

    @Test fun publishesFourDistinctShortcutsWithExplicitReusableActivityIntents() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val manager = context.getSystemService(ShortcutManager::class.java)
            await(scenario) { manager.dynamicShortcuts.size == 4 }
            val shortcuts = manager.dynamicShortcuts.sortedBy { it.rank }
            assertEquals(listOf("Open document", "Recent", "Favorites", "Sign PDF"), shortcuts.map { it.shortLabel.toString() })
            assertEquals(4, shortcuts.map { it.id }.distinct().size)
            shortcuts.forEach {
                val intent = requireNotNull(it.intent)
                assertEquals(context.packageName, intent.component?.packageName)
                assertEquals(MainActivity::class.java.name, intent.component?.className)
                assertNotNull(LauncherShortcuts.destination(intent))
                assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
                assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val dir = File(context.getExternalFilesDir(null), "shortcut-qa").also { it.mkdirs() }
            device.pressHome()
            // The launcher may place the icon only in its app drawer; publication is
            // asserted through Android's ShortcutManager, independent of launcher layout.
            device.takeScreenshot(File(dir, "launcher.png"))
        }
    }

    @Test fun coldRecentAndWarmFavoritesResetFiltersAndDoNotReplayOnRecreation() {
        ActivityScenario.launch<MainActivity>(LauncherShortcuts.intent(context, LauncherShortcuts.Destination.Recent)).use { scenario ->
            await(scenario) { vm(it).libraryState.collection == "Recent" }
            assertTrue(device.wait(Until.hasObject(By.text("Library")), 10000))
            var original: MainActivity? = null
            scenario.onActivity {
                original = it
                vm(it).libraryState(LibraryState(type="Excel", search="old query", category="Work", tag="old", index=9, offset=20))
            }
            launch(LauncherShortcuts.Destination.Favorites)
            await(scenario) { vm(it).libraryState.type == "Favorites" && vm(it).libraryState.search.isEmpty() }
            scenario.onActivity {
                assertSame("Warm shortcut must reuse the reader activity", original, it)
                assertEquals(LibraryState(type="Favorites"), vm(it).libraryState)
                // A user's later search must survive rotation; the launch action is consumed.
                vm(it).libraryState(vm(it).libraryState.copy(search="keep after rotation"))
            }
            scenario.recreate()
            await(scenario) { it.hasWindowFocus() }
            scenario.onActivity { assertEquals("keep after rotation", vm(it).libraryState.search) }
            val dir = File(context.getExternalFilesDir(null), "shortcut-qa").also { it.mkdirs() }
            device.takeScreenshot(File(dir, "favorites.png"))
        }
    }

    @Test fun coldSignAndWarmOpenUseCorrectPickersAndCancellationDoesNotReplay() {
        val monitor = PickerMonitor()
        inst.addMonitor(monitor)
        try {
            ActivityScenario.launch<MainActivity>(LauncherShortcuts.intent(context, LauncherShortcuts.Destination.Sign)).use { scenario ->
                await(scenario) { monitor.requests.size == 1 && it.hasWindowFocus() }
                assertEquals("application/pdf", monitor.requests[0].getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.single())
                scenario.recreate()
                await(scenario) { it.hasWindowFocus() }
                inst.waitForIdleSync()
                assertEquals("Rotation reopened a cancelled picker", 1, monitor.requests.size)
                launch(LauncherShortcuts.Destination.Open)
                await(scenario) { monitor.requests.size == 2 && it.hasWindowFocus() }
                val formats = monitor.requests[1].getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.toList()
                assertTrue(formats.contains("application/pdf"))
                assertTrue(formats.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                assertTrue(formats.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                scenario.recreate()
                await(scenario) { it.hasWindowFocus() }
                inst.waitForIdleSync()
                assertEquals(2, monitor.requests.size)
            }
        } finally { inst.removeMonitor(monitor) }
    }

    @Test fun signatureInProgressSurvivesAnotherLauncherShortcut() {
        val file = File(context.filesDir, "library/shortcut-sign.pdf").also { it.parentFile!!.mkdirs() }
        PdfDocument().use { pdf ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
            pdf.finishPage(page)
            file.outputStream().use { pdf.writeTo(it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val monitor = PickerMonitor(Intent().setData(uri))
        inst.addMonitor(monitor)
        try {
            ActivityScenario.launch<MainActivity>(LauncherShortcuts.intent(context, LauncherShortcuts.Destination.Sign)).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.desc("Draw signature here")), 15000))
                val canvas = device.findObject(By.desc("Draw signature here")).visibleBounds
                device.swipe(canvas.left+20, canvas.centerY(), canvas.right-20, canvas.centerY()-15, 15)
                launch(LauncherShortcuts.Destination.Recent)
                await(scenario) { vm(it).message == "Finish the current action before using a shortcut." }
                assertTrue(device.hasObject(By.desc("Draw signature here")))
                scenario.onActivity { assertEquals("Saved", vm(it).libraryState.collection) }
                val dir = File(context.getExternalFilesDir(null), "shortcut-qa").also { it.mkdirs() }
                device.takeScreenshot(File(dir, "signature-protected.png"))
            }
        } finally { inst.removeMonitor(monitor); file.delete() }
    }
}

