package com.pdfliteai.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pdfliteai.data.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pdflite_settings")

class SettingsRepository(private val context: Context) {

    private val KEY_PROVIDER = stringPreferencesKey("ai_provider")
    private val KEY_MODEL = stringPreferencesKey("ai_model")
    private val KEY_BASEURL = stringPreferencesKey("ai_baseurl")
    private val KEY_TEMP = floatPreferencesKey("ai_temperature")

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

    private fun defaultModel(p: ProviderId): String = when (p) {
        ProviderId.GROQ -> "llama-3.1-8b-instant"
        ProviderId.OPENROUTER -> "openai/gpt-4o-mini"
        ProviderId.NOVA -> "nova-lite-v1"
        ProviderId.LOCAL_OPENAI_COMPAT -> "gpt-4o-mini"
    }

    suspend fun setProvider(p: ProviderId) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER] = p.name
            prefs[KEY_MODEL] = defaultModel(p)
        }
    }

    suspend fun setModel(m: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MODEL] = m.trim()
        }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASEURL] = url.trim()
        }
    }

    suspend fun setTemperature(t: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEMP] = t
        }
    }
}
