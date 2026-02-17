package com.pdfliteai.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAICompatProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val extraHeaders: Map<String, String> = emptyMap()
) : ModelProvider {

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(messages: List<ChatMessage>): String {
        if (apiKey.isBlank()) error("API key is empty.")
        if (model.isBlank()) error("Model is empty.")

        val url = baseUrl.trimEnd('/') + "/v1/chat/completions"

        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            putJsonArray("messages") {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            }
        }

        val rb = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")

        extraHeaders.forEach { (k, v) -> rb.addHeader(k, v) }

        val req = rb.post(body.toString().toRequestBody("application/json".toMediaType())).build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("AI error ${resp.code}: $raw")

            val root = json.parseToJsonElement(raw).jsonObject
            val choices = root["choices"]?.jsonArray ?: error("Missing choices: $raw")
            if (choices.isEmpty()) error("Empty choices: $raw")

            val msg = choices[0].jsonObject["message"]?.jsonObject ?: error("Missing message: $raw")
            return msg["content"]?.jsonPrimitive?.content ?: error("Missing message.content: $raw")
        }
    }
}
