package com.pdfliteai

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfLiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // ✅ REQUIRED for pdfbox-android
        PDFBoxResourceLoader.init(this)
    }
}
