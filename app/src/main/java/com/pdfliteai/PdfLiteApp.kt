package com.pdfliteai

import android.app.Application
import android.util.Log
import androidx.work.*
import com.pdfliteai.settings.SettingsRepository
import com.pdfliteai.telemetry.TelemetryConfig
import com.pdfliteai.telemetry.TelemetryManager
import com.pdfliteai.telemetry.TelemetryUploadWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PdfLiteApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // ✅ Avoid ANR: PDFBox init can be slow on emulators/preview builds
        appScope.launch {
            runCatching { PDFBoxResourceLoader.init(this@PdfLiteApp) }
                .onFailure { Log.w("PDFBox", "PDFBox init failed: ${it.message}", it) }
        }

        val repo = SettingsRepository(this)

        // ✅ Ensure DataStore is initialized early, but NOT on main thread
        appScope.launch(Dispatchers.IO) {
            runCatching { repo.ensureInitialized() }
                .onFailure { Log.w("SettingsRepo", "ensureInitialized failed: ${it.message}", it) }
        }

        val cfg = TelemetryConfig(
            baseUrl = BuildConfig.TELEMETRY_BASE_URL,
            authToken = null,
            appVersion = BuildConfig.VERSION_NAME
        )

        Log.d(
            "Telemetry",
            "PdfLiteApp.onCreate baseUrl=${cfg.baseUrl} tokenPresent=${!cfg.authToken.isNullOrBlank()}"
        )

        TelemetryManager.init(
            context = this,
            config = cfg,
            userIdProvider = { repo.getTelemetryPrefsOnce().userId },
            profileProvider = { repo.getUserProfileOnce() },
            userIdSetter = { canonicalId -> repo.setUserId(canonicalId) } // assumes suspend-safe
        )

        val req = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("telemetry_flush", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}