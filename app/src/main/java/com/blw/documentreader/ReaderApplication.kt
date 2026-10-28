package com.blw.documentreader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        // Called once per process, not on rotation: remove leftovers after a previous process death.
        cacheDir.listFiles()?.filter {
            it.isFile && (it.name.startsWith("pdf_") ||
                it.name.startsWith("unlocked_") || it.name.startsWith("blw_export_"))
        }?.forEach { it.delete() }
    }
}
