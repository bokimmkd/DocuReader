package com.blw.documentreader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.*

class ReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        // Called once per process, not on rotation: remove leftovers after a previous process death.
        // No filesystem enumeration before the first Activity frame. Only old leftovers:
        // a new reader may already be using a fresh snapshot when this IO job runs.
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            cacheDir.listFiles()?.filter {
                it.isFile && it.lastModified() < cutoff && (it.name.startsWith("pdf_") ||
                    it.name.startsWith("unlocked_") || it.name.startsWith("blw_export_"))
            }?.forEach { it.delete() }
        }
    }
}
