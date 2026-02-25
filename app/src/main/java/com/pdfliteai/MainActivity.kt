package com.pdfliteai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.pdfliteai.ui.nav.AppNav
import com.pdfliteai.ui.theme.PdfLiteTheme

class MainActivity : ComponentActivity() {

    companion object {
        // ✅ Compose-observable state (so UI reacts immediately)
        val pendingOpenUriState = mutableStateOf<Uri?>(null)
        val pendingOpenMimeState = mutableStateOf<String?>(null)

        fun clearPendingOpen() {
            pendingOpenUriState.value = null
            pendingOpenMimeState.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            PdfLiteTheme {
                AppNav(app = application)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action

        val uri = when (action) {
            Intent.ACTION_VIEW -> intent.data

            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            }

            else -> null
        } ?: return

        pendingOpenUriState.value = uri
        pendingOpenMimeState.value = intent.type ?: contentResolver.getType(uri)

        // ✅ Persist read permission when possible (SAF URIs); ignore failures safely
        if ((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Not persistable for many providers; still readable for this session
            }
        }
    }
}