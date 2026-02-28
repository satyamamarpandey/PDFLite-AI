package com.pdfliteai.telemetry

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.telemetryDataStore by preferencesDataStore(name = "pdflite_telemetry")

class TelemetryStore(private val context: Context) {

    private val KEY_QUEUE = stringPreferencesKey("event_queue_jsonl") // JSONL (one per line)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun appendEvent(e: TelemetryEvent) {
        val line = json.encodeToString(e)
        context.telemetryDataStore.edit { prefs ->
            val cur = prefs[KEY_QUEUE].orEmpty()
            val next = if (cur.isBlank()) line else (cur + "\n" + line)
            prefs[KEY_QUEUE] = trimToMax(next, 300_000) // ~300KB cap
        }
    }

    suspend fun peekBatch(maxLines: Int = 80): Batch? {
        val raw = context.telemetryDataStore.data.first()[KEY_QUEUE].orEmpty().trim()
        if (raw.isBlank()) return null

        val lines = raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val take = lines.take(maxLines)
        val remaining = lines.drop(maxLines)

        return Batch(
            batchId = "b_" + UUID.randomUUID().toString().replace("-", "").take(12),
            jsonl = take.joinToString("\n"),
            count = take.size,
            remainingJsonl = remaining.joinToString("\n")
        )
    }

    suspend fun commitBatch(remainingJsonl: String) {
        context.telemetryDataStore.edit { prefs ->
            prefs[KEY_QUEUE] = remainingJsonl.trim()
        }
    }

    suspend fun clear() {
        context.telemetryDataStore.edit { prefs -> prefs.remove(KEY_QUEUE) }
    }

    data class Batch(
        val batchId: String,
        val jsonl: String,
        val count: Int,
        val remainingJsonl: String
    )

    private fun trimToMax(s: String, maxChars: Int): String {
        if (s.length <= maxChars) return s
        return s.takeLast(maxChars) // keep newest
    }
}