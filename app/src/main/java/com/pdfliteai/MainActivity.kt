package com.pdfliteai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pdfliteai.ui.nav.AppNav
import com.pdfliteai.ui.theme.PdfLiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PdfLiteTheme {
                AppNav(app = application)
            }
        }
    }
}