package com.pdfliteai.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OllamaProvider(
    private val http: OkHttpClient,
    private val json: Json
) {
    suspend fun chat(cfg: AiProvider, prompt: String): String {
        val url = buildGenerateUrl(cfg.baseUrl)

        val payload = GenerateReq(
            model = cfg.model.ifBlank { "qwen2.5:1.5b" },
            prompt = prompt,
            stream = false,
            options = Options(num_ctx = 512, num_predict = 192)
        )

        val bodyStr = json.encodeToString(payload)
        val reqBody = bodyStr.toRequestBody("application/json".toMediaType())

        val reqBuilder = Request.Builder()
            .url(url)
            .post(reqBody)
            .addHeader("Accept", "application/json")

        if (cfg.apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${cfg.apiKey.trim()}")
        }

        val resp = http.newCall(reqBuilder.build()).execute()
        val txt = resp.body?.string().orEmpty()

        if (!resp.isSuccessful) throw RuntimeException("Ollama HTTP ${resp.code}: $txt")

        return runCatching {
            val parsed = json.decodeFromString(GenerateResp.serializer(), txt)
            parsed.response ?: parsed.message?.content ?: txt
        }.getOrDefault(txt)
    }

    private fun buildGenerateUrl(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        return when {
            root.endsWith("/api/generate", ignoreCase = true) -> root
            root.endsWith("/api", ignoreCase = true) -> "$root/generate"
            else -> "$root/api/generate"
        }
    }

    @Serializable data class GenerateReq(val model: String, val prompt: String, val stream: Boolean, val options: Options)
    @Serializable data class Options(val num_ctx: Int, val num_predict: Int)
    @Serializable data class GenerateResp(val response: String? = null, val message: Msg? = null, val error: String? = null)
    @Serializable data class Msg(val content: String? = null)
}
