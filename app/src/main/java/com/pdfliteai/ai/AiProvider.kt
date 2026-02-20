package com.pdfliteai.ai

import com.pdfliteai.data.ProviderId

data class AiProvider(
    val provider: ProviderId,
    val model: String,
    val baseUrl: String,
    val apiKey: String,
    val temperature: Float = 0.2f
)

object ProviderDefaults {
    fun fixedBaseUrl(provider: ProviderId): String = when (provider) {
        ProviderId.OPENROUTER -> "https://openrouter.ai/api/v1"
        ProviderId.NOVA -> "https://api.nova.amazon.com"
        ProviderId.GROQ -> "https://api.groq.com/openai/v1"
        ProviderId.LOCAL_OPENAI_COMPAT -> ""
    }
}

