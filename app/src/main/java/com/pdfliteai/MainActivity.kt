package com.pdfliteai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pdfliteai.ui.nav.AppNav
import com.pdfliteai.ui.theme.PDFLiteAITheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Required for PDFBox on Android
        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            PDFLiteAITheme {
                AppNav()
            }
        }
    }
}
