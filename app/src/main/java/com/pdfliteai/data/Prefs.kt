package com.pdfliteai.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("pdflite_prefs")

enum class Scope { SelectedText, EntireDocument }
enum class ProviderId { OpenAI, OpenRouter, Groq, OllamaLan }

object PrefKeys {
    val provider = stringPreferencesKey("provider")
    val modelName = stringPreferencesKey("model_name")

    val openaiKey = stringPreferencesKey("openai_key")
    val openaiBaseUrl = stringPreferencesKey("openai_base_url")

    val openrouterKey = stringPreferencesKey("openrouter_key")
    val openrouterBaseUrl = stringPreferencesKey("openrouter_base_url")

    val groqKey = stringPreferencesKey("groq_key")
    val groqBaseUrl = stringPreferencesKey("groq_base_url")

    val ollamaBaseUrl = stringPreferencesKey("ollama_base_url")
}

class Prefs(private val ctx: Context) {

    val providerFlow: Flow<ProviderId> = ctx.ds.data.map {
        ProviderId.valueOf(it[PrefKeys.provider] ?: ProviderId.OpenAI.name)
    }

    val modelFlow: Flow<String> = ctx.ds.data.map {
        it[PrefKeys.modelName] ?: "gpt-4o-mini"
    }

    fun stringFlow(key: Preferences.Key<String>, default: String = ""): Flow<String> =
        ctx.ds.data.map { it[key] ?: default }

    suspend fun setProvider(id: ProviderId) {
        ctx.ds.edit { it[PrefKeys.provider] = id.name }
    }

    suspend fun setModel(m: String) {
        ctx.ds.edit { it[PrefKeys.modelName] = m }
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        ctx.ds.edit { it[key] = value }
    }
}
