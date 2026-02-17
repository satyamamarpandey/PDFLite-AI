package com.pdfliteai.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OllamaProvider(
    private val baseUrl: String,
    private val model: String
) : ModelProvider {

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(messages: List<ChatMessage>): String {
        val url = baseUrl.trimEnd('/') + "/api/chat"

        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            putJsonArray("messages") {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            }
        }

        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Ollama error ${resp.code}: $raw")

            val root = json.parseToJsonElement(raw).jsonObject
            val msgObj = root["message"]?.jsonObject ?: error("Missing 'message': $raw")
            return msgObj["content"]?.jsonPrimitive?.content ?: error("Missing 'message.content': $raw")
        }
    }
}
