package com.pdfliteai.data

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PrefKeys {
    val AI_PROVIDER = stringPreferencesKey("ai_provider")
    val AI_MODEL = stringPreferencesKey("ai_model")
    val AI_BASE_URL = stringPreferencesKey("ai_base_url") // only for LOCAL_OPENAI_COMPAT
    val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
}
