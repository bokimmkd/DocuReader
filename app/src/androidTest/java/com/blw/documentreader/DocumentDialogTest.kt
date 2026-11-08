package com.blw.documentreader

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.blw.documentreader.data.DocumentEntry
import com.blw.documentreader.data.DocumentStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentDialogTest {
    @Test fun longCategoryListScrollsToLastItemInBothThemes() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val device = UiDevice.getInstance(inst)
        val store = DocumentStore(context)
        val original = store.load(); val originalCategories = store.categories()
        val source = File(context.filesDir, "library/long-categories.txt").also { it.parentFile!!.mkdirs(); it.writeText("Preserved source") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", source)
        val output = File(context.getExternalFilesDir(null), "dialog-qa").also { it.mkdirs() }
        try {
            store.categories((1..20).map { "Category %02d".format(it) })
            for (dark in listOf(false, true)) {
                store.save(listOf(DocumentEntry(uri.toString(), source.name, "TXT", savedLocally = true)))
                ActivityScenario.launch<DocumentDialogTestActivity>(Intent(context, DocumentDialogTestActivity::class.java).putExtra("dark", dark)).use {
                    device.wait(Until.findObject(By.desc("Document actions")), 10000)!!.click()
                    device.wait(Until.findObject(By.text("Rename")), 10000)!!.click()
                    device.wait(Until.findObject(By.text("Uncategorized")), 10000)!!.click()
                    val first = device.wait(Until.findObject(By.text("Category 01")), 10000)!!
                    var menu: UiObject2? = first
                    while (menu != null && !menu.isScrollable) menu = menu.parent
                    assertNotNull("Category menu must scroll", menu)
                    val scroller = requireNotNull(menu)
                    device.waitForIdle()
                    device.takeScreenshot(File(output, "category-long-start-$dark.png"))
                    scroller.scroll(Direction.DOWN, .7f); device.waitForIdle()
                    device.takeScreenshot(File(output, "category-long-middle-$dark.png"))
                    var attempts = 0
                    while (!device.hasObject(By.text("New category")) && attempts++ < 20) scroller.scroll(Direction.DOWN, .7f)
                    assertTrue("End of category list is unreachable", device.hasObject(By.text("New category")))
                    device.takeScreenshot(File(output, "category-long-end-$dark.png"))
                    device.findObject(By.text("Category 20")).click()
                    device.wait(Until.findObject(By.text("Save")), 10000)!!.click()
                    device.waitForIdle()
                    assertEquals("Category 20", store.load().single().category)
                    assertEquals("Preserved source", source.readText())
                }
            }
        } finally { store.save(original); store.categories(originalCategories) }
    }

    @Test fun dropdownExistingNewCategoryAndCancelPreserveDocumentInBothThemes() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val d = UiDevice.getInstance(inst)
        val store = DocumentStore(context)
        fun click(text: String) {
            val end = System.currentTimeMillis() + 10000
            do {
                val node = d.wait(Until.findObject(By.text(text)), 800)
                if (node != null) try { node.click(); d.waitForIdle(); return } catch (_: StaleObjectException) { }
            } while (System.currentTimeMillis() < end)
            fail("Missing UI: $text")
        }
        fun edit() {
            d.wait(Until.findObject(By.desc("Document actions")), 10000)!!.click()
            click("Rename")
            assertTrue(d.wait(Until.hasObject(By.text("W Reader")), 10000))
        }
        fun awaitCategory(category: String) {
            val end = System.currentTimeMillis() + 10000
            do { if (store.load().single().category == category) return; Thread.sleep(80) }
            while (System.currentTimeMillis() < end)
            fail("Category was not saved: $category")
        }
        val original = store.load(); val originalCategories = store.categories()
        store.categories(listOf("Work", "Personal"))
        try { for (dark in listOf(false, true)) {
            val source = File(context.filesDir, "library/dialog-$dark.txt").also {
                it.parentFile!!.mkdirs(); it.writeText("Original content Кирилица")
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", source)
            store.save(listOf(DocumentEntry(uri.toString(), "invoice.txt", "TXT", category = "Personal",
                tags = "2026, home", savedLocally = true)))
            val intent = Intent(context, DocumentDialogTestActivity::class.java).putExtra("dark", dark)
            ActivityScenario.launch<DocumentDialogTestActivity>(intent).use {
                edit(); click("Personal")
                val output = File(context.getExternalFilesDir(null), "dialog-qa").also { it.mkdirs() }
                d.waitForIdle(); Thread.sleep(800)
                d.takeScreenshot(File(output, if (dark) "category-menu-dark.png" else "category-menu-light.png"))
                d.dumpWindowHierarchy(File(output, if (dark) "category-menu-dark.xml" else "category-menu-light.xml"))
                assertTrue("New category menu missing",d.wait(Until.hasObject(By.text("New category")), 10000))
                click("Work")
                d.waitForIdle(); Thread.sleep(800)
                d.takeScreenshot(File(output, if (dark) "edit-dark.png" else "edit-light.png"))
                click("Save"); awaitCategory("Work")
                assertEquals("invoice.txt", store.load().single().name)
                assertEquals("2026, home", store.load().single().tags)
                edit(); click("Work"); click("New category")
                val fields = d.wait(Until.findObjects(By.clazz("android.widget.EditText")), 10000)
                assertEquals("File, new category, tags fields", 3, fields!!.size)
                fields[1].text = "Receipts"
                click("Save"); awaitCategory("Receipts")
                edit()
                d.findObjects(By.clazz("android.widget.EditText"))[0].text = "temporary-name"
                click("Cancel")
                assertEquals("invoice.txt", store.load().single().name)
                assertEquals("Receipts", store.load().single().category)
                assertEquals(uri.toString(), store.load().single().uri)
                assertEquals("Original content Кирилица", source.readText())
            }
        } } finally { store.save(original); store.categories(originalCategories) }
    }
}
