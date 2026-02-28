package com.pdfliteai.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TelemetryUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ok = TelemetryManager.flushNow()
            if (ok) Result.success() else Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}