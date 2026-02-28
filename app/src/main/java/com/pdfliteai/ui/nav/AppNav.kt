package com.pdfliteai.ui.nav

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdfliteai.MainActivity
import com.pdfliteai.ai.AiOrchestrator
import com.pdfliteai.ai.OpenAICompatProvider
import com.pdfliteai.pdf.PdfRepository
import com.pdfliteai.settings.SettingsViewModel
import com.pdfliteai.ui.OnboardingScreen
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

    // Observe pending "Open with" URI (but only used after onboarding)
    val pendingUri by MainActivity.pendingOpenUriState
    val pendingMime by MainActivity.pendingOpenMimeState

    // Gate: onboarding must be completed once
    val vm: SettingsViewModel = viewModel()
    val onboardingDone: Boolean by vm.onboardingDone.collectAsState()

    NavHost(
        navController = nav,
        startDestination = if (onboardingDone) "pdf" else "onboarding",
        modifier = modifier
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onDone = {
                    MainActivity.clearPendingOpen()
                    nav.navigate("pdf") {
                        popUpTo("onboarding") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("pdf") {
            if (!onboardingDone) {
                nav.navigate("onboarding") {
                    popUpTo("pdf") { inclusive = true }
                    launchSingleTop = true
                }
                return@composable
            }

            PdfScreen(
                repo = repo,
                ai = ai,
                onOpenSettings = { nav.navigate("settings") },
                initialOpenUri = pendingUri,
                initialOpenMime = pendingMime
            )
        }

        composable("settings") {
            if (!onboardingDone) {
                nav.navigate("onboarding") {
                    popUpTo("settings") { inclusive = true }
                    launchSingleTop = true
                }
                return@composable
            }

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