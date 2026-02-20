package com.pdfliteai.ai

import com.pdfliteai.data.ProviderId
import com.pdfliteai.settings.AiSettings
import okhttp3.OkHttpClient
import java.time.Duration

class AiOrchestrator(
    private val openAICompat: OpenAICompatProvider
) {
    companion object {
        fun defaultHttp(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build()
    }

    private fun resolveBaseUrl(s: AiSettings): String {
        val base = when (s.provider) {
            ProviderId.GROQ -> "https://api.groq.com/openai/v1"
            ProviderId.OPENROUTER -> "https://openrouter.ai/api/v1"
            ProviderId.NOVA -> "https://api.nova.amazon.com"
            ProviderId.LOCAL_OPENAI_COMPAT -> s.baseUrl
        }
        return base.trim().removeSuffix("/")
    }

    private fun resolveConfig(s: AiSettings, apiKey: String): AiProvider {
        val baseUrl = resolveBaseUrl(s)
        if (baseUrl.isBlank()) error("Base URL is empty for provider ${s.provider}. Set it in Settings.")
        return AiProvider(
            provider = s.provider,
            model = s.model.trim(),
            baseUrl = baseUrl,
            apiKey = apiKey.trim(),
            temperature = s.temperature
        )
    }

    suspend fun chat(s: AiSettings, apiKey: String, userText: String): String {
        val cfg = resolveConfig(s, apiKey)
        return openAICompat.chat(cfg, userText)
    }
}
