package com.pdfliteai.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pdfliteai.data.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pdflite_settings")

class SettingsRepository(private val context: Context) {

    private val KEY_PROVIDER = stringPreferencesKey("ai_provider")
    private val KEY_MODEL = stringPreferencesKey("ai_model")
    private val KEY_BASEURL = stringPreferencesKey("ai_baseurl")
    private val KEY_TEMP = floatPreferencesKey("ai_temperature")

    private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val KEY_RECENTS = stringPreferencesKey("recent_uris")
    private val KEY_RECENTS_LIMIT = intPreferencesKey("recents_limit")

    private val KEY_AUTO_OPEN_AI = booleanPreferencesKey("auto_open_ai")
    private val KEY_DEFAULT_ENTIRE_DOC = booleanPreferencesKey("default_entire_doc")
    private val KEY_BG_DIM = floatPreferencesKey("bg_dim")

    // ✅ NEW
    private val KEY_CHAT_HISTORY_LIMIT = intPreferencesKey("chat_history_limit")

    val aiSettingsFlow: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val provider = runCatching {
            ProviderId.valueOf(prefs[KEY_PROVIDER] ?: ProviderId.GROQ.name)
        }.getOrDefault(ProviderId.GROQ)

        AiSettings(
            provider = provider,
            model = prefs[KEY_MODEL] ?: defaultModel(provider),
            baseUrl = prefs[KEY_BASEURL] ?: "",
            temperature = prefs[KEY_TEMP] ?: 0.2f
        )
    }

    val readerSettingsFlow: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
            recentsLimit = (prefs[KEY_RECENTS_LIMIT] ?: 10).coerceIn(3, 10),
            autoOpenAi = prefs[KEY_AUTO_OPEN_AI] ?: true,
            defaultEntireDoc = prefs[KEY_DEFAULT_ENTIRE_DOC] ?: true,
            bgDim = (prefs[KEY_BG_DIM] ?: 0.22f).coerceIn(0f, 0.45f),
            chatHistoryLimit = (prefs[KEY_CHAT_HISTORY_LIMIT] ?: 3).coerceIn(1, 10)
        )
    }

    val recentDocsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val limit = (prefs[KEY_RECENTS_LIMIT] ?: 10).coerceIn(3, 10)
        decodeList(prefs[KEY_RECENTS].orEmpty()).take(limit)
    }

    suspend fun ensureInitialized() {
        val prefs = context.dataStore.data.first()
        context.dataStore.edit { p ->
            if (!prefs.contains(KEY_PROVIDER)) {
                p[KEY_PROVIDER] = ProviderId.GROQ.name
                p[KEY_MODEL] = defaultModel(ProviderId.GROQ)
                p[KEY_TEMP] = 0.2f
            }
            if (!prefs.contains(KEY_RECENTS_LIMIT)) p[KEY_RECENTS_LIMIT] = 10
            if (!prefs.contains(KEY_KEEP_SCREEN_ON)) p[KEY_KEEP_SCREEN_ON] = false
            if (!prefs.contains(KEY_AUTO_OPEN_AI)) p[KEY_AUTO_OPEN_AI] = true
            if (!prefs.contains(KEY_DEFAULT_ENTIRE_DOC)) p[KEY_DEFAULT_ENTIRE_DOC] = true
            if (!prefs.contains(KEY_BG_DIM)) p[KEY_BG_DIM] = 0.22f

            // ✅ NEW default
            if (!prefs.contains(KEY_CHAT_HISTORY_LIMIT)) p[KEY_CHAT_HISTORY_LIMIT] = 3
        }
    }

    private fun defaultModel(p: ProviderId): String = when (p) {
        ProviderId.GROQ -> "llama-3.1-8b-instant"
        ProviderId.OPENROUTER -> "openai/gpt-4o-mini"
        ProviderId.NOVA -> "nova-lite-v1"
        ProviderId.LOCAL_OPENAI_COMPAT -> ""
    }

    suspend fun setProvider(p: ProviderId) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER] = p.name
            prefs[KEY_MODEL] = defaultModel(p)
        }
    }

    suspend fun setModel(m: String) {
        context.dataStore.edit { prefs -> prefs[KEY_MODEL] = m.trim() }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[KEY_BASEURL] = url.trim() }
    }

    suspend fun setTemperature(t: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_TEMP] = t.coerceIn(0f, 1f) }
    }

    suspend fun setKeepScreenOn(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_KEEP_SCREEN_ON] = on }
    }

    suspend fun setRecentsLimit(limit: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECENTS_LIMIT] = limit.coerceIn(3, 10) }
    }

    suspend fun setAutoOpenAi(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_OPEN_AI] = on }
    }

    suspend fun setDefaultEntireDoc(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DEFAULT_ENTIRE_DOC] = on }
    }

    suspend fun setBgDim(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_BG_DIM] = v.coerceIn(0f, 0.45f) }
    }

    // ✅ NEW
    suspend fun setChatHistoryLimit(v: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CHAT_HISTORY_LIMIT] = v.coerceIn(1, 10) }
    }

    suspend fun addRecent(uri: String) {
        val u = uri.trim()
        if (u.isBlank()) return

        context.dataStore.edit { prefs ->
            val limit = (prefs[KEY_RECENTS_LIMIT] ?: 10).coerceIn(3, 10)
            val cur = decodeList(prefs[KEY_RECENTS].orEmpty())
            val next = buildList {
                add(u)
                cur.filterNot { it == u }.forEach { add(it) }
            }.take(limit)
            prefs[KEY_RECENTS] = encodeList(next)
        }
    }

    suspend fun clearRecents() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_RECENTS) }
    }

    private fun encodeList(items: List<String>): String =
        items.joinToString("\n") { it.replace("\n", "").trim() }.trim()

    private fun decodeList(raw: String): List<String> =
        raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
}