package com.pdfliteai.ui.nav

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdfliteai.ai.AiOrchestrator
import com.pdfliteai.ai.OpenAICompatProvider
import com.pdfliteai.pdf.PdfRepository
import com.pdfliteai.ui.PdfScreen
import com.pdfliteai.ui.theme.SettingsScreen
import kotlinx.serialization.json.Json

@Composable
fun AppNav(
    app: Application,
    modifier: Modifier = Modifier
) {
    val nav = rememberNavController()

    val json = remember { Json { ignoreUnknownKeys = true } }
    val http = remember { AiOrchestrator.defaultHttp() }

    val openAICompat = remember { OpenAICompatProvider(http = http, json = json) }
    val ai = remember { AiOrchestrator(openAICompat) }

    val repo = remember { PdfRepository(app) }

    NavHost(navController = nav, startDestination = "pdf", modifier = modifier) {
        composable("pdf") {
            PdfScreen(
                repo = repo,
                ai = ai,
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onTestConnection = { provider, model, baseUrl, apiKey, temperature ->
                    val s = com.pdfliteai.settings.AiSettings(
                        provider = provider,
                        model = model,
                        baseUrl = baseUrl,
                        temperature = temperature
                    )
                    val msg = ai.chat(s, apiKey, "Say 'OK'.")
                    "OK: ${msg.take(120)}"
                }
            )
        }
    }
}
