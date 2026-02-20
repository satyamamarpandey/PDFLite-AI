package com.pdfliteai.ai

import com.pdfliteai.data.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAICompatProvider(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(cfg: AiProvider, userText: String): String = withContext(Dispatchers.IO) {
        val base = cfg.baseUrl.trim().removeSuffix("/")
        val url = buildChatUrl(base, cfg.provider)

        val body = ChatBody(
            model = cfg.model,
            messages = listOf(ChatMsg(role = "user", content = userText)),
            temperature = cfg.temperature
        )

        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .apply {
                if (cfg.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${cfg.apiKey.trim()}")

                // OpenRouter recommended headers (won’t break others)
                if (cfg.provider == ProviderId.OPENROUTER) {
                    addHeader("HTTP-Referer", "https://pdfliteai.local")
                    addHeader("X-Title", "PDFLite AI")
                }
            }
            .post(json.encodeToString(body).toRequestBody(mediaJson))
            .build()

        val resp = http.newCall(req).execute()
        val raw = resp.body?.string().orEmpty()

        if (!resp.isSuccessful) {
            throw IllegalStateException("AI error ${resp.code} (${cfg.provider}) @ $url: $raw")
        }

        extractContent(raw)
    }

    private fun buildChatUrl(base: String, provider: ProviderId): String {
        val b = base.trim().removeSuffix("/")

        // OpenRouter base is fixed to https://openrouter.ai/api/v1
        if (provider == ProviderId.OPENROUTER) {
            return if (b.endsWith("/api/v1", ignoreCase = true)) "$b/chat/completions"
            else "$b/api/v1/chat/completions"
        }

        // Groq is https://api.groq.com/openai/v1 (already ends /v1)
        return if (b.endsWith("/v1", ignoreCase = true)) "$b/chat/completions"
        else "$b/v1/chat/completions"
    }

    @Serializable private data class ChatBody(
        val model: String,
        val messages: List<ChatMsg>,
        val temperature: Float? = null
    )

    @Serializable private data class ChatMsg(
        val role: String,
        val content: String
    )

    private fun extractContent(raw: String): String {
        val marker = "\"content\":"
        val idx = raw.indexOf(marker)
        if (idx < 0) return raw
        val start = raw.indexOf('"', idx + marker.length)
        if (start < 0) return raw

        var i = start + 1
        var escaped = false
        while (i < raw.length) {
            val c = raw[i]
            if (escaped) escaped = false
            else {
                if (c == '\\') escaped = true
                else if (c == '"') break
            }
            i++
        }
        if (i >= raw.length) return raw

        return raw.substring(start + 1, i).replace("\\n", "\n")
    }
}
