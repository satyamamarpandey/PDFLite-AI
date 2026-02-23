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

class WorkerProxyProvider(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(workerBaseUrl: String, cfg: AiProvider, userText: String): String = withContext(Dispatchers.IO) {
        val base = workerBaseUrl.trim().removeSuffix("/")
        val url = "$base/chat"

        val providerStr = when (cfg.provider) {
            ProviderId.NOVA -> "nova"
            ProviderId.GROQ -> "groq"
            ProviderId.OPENROUTER -> "openrouter"
            ProviderId.LOCAL_OPENAI_COMPAT -> "openrouter" // shouldn't happen here
        }

        val body = WorkerChatBody(
            provider = providerStr,
            model = cfg.model,
            messages = listOf(WorkerMsg(role = "user", content = userText)),
            temperature = cfg.temperature
        )

        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(body).toRequestBody(mediaJson))
            .build()

        val resp = http.newCall(req).execute()
        val raw = resp.body?.string().orEmpty()

        if (!resp.isSuccessful) {
            throw IllegalStateException("Worker AI error ${resp.code} (${cfg.provider}) @ $url: $raw")
        }

        extractContent(raw)
    }

    @Serializable
    private data class WorkerChatBody(
        val provider: String,
        val model: String,
        val messages: List<WorkerMsg>,
        val temperature: Float? = null
    )

    @Serializable
    private data class WorkerMsg(
        val role: String,
        val content: String
    )

    // Parse OpenAI-style response: choices[0].message.content
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

        return raw.substring(start + 1, i)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
    }
}