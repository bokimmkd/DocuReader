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
        for (dark in listOf(false, true)) {
            val source = File(context.filesDir, "library/dialog-$dark.txt").also {
                it.parentFile!!.mkdirs(); it.writeText("Original content Кирилица")
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", source)
            store.save(listOf(DocumentEntry(uri.toString(), "invoice.txt", "TXT", category = "Personal",
                tags = "2026, home", savedLocally = true)))
            val intent = Intent(context, DocumentDialogTestActivity::class.java).putExtra("dark", dark)
            ActivityScenario.launch<DocumentDialogTestActivity>(intent).use {
                edit(); click("Personal")
                assertTrue(d.wait(Until.hasObject(By.text("New category")), 10000))
                val output = File(context.getExternalFilesDir(null), "dialog-qa").also { it.mkdirs() }
                d.waitForIdle(); Thread.sleep(800)
                d.takeScreenshot(File(output, if (dark) "category-menu-dark.png" else "category-menu-light.png"))
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
        }
    }
}
