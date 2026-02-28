package com.pdfliteai.telemetry

import android.util.Log
import com.pdfliteai.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TelemetryClient(
    private val cfg: TelemetryConfig
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun baseHeaders(builder: Request.Builder, userId: String) {
        val token = BuildConfig.APP_TOKEN.trim()
        if (token.isNotBlank()) builder.header("X-App-Token", token)

        builder.header("x-user-id", userId)
        if (cfg.appVersion.isNotBlank()) builder.header("x-app-version", cfg.appVersion)
        builder.header("x-device", cfg.deviceTag)
    }

    fun postProfile(userId: String, json: String): Pair<Int, String> {
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(cfg.profileUrl())
            .post(body)
            .also { baseHeaders(it, userId) }
            .build()

        http.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun postEventsBatchGz(
        userId: String,
        batchId: String,
        count: Int,
        gzBytes: ByteArray
    ): Pair<Int, String> {
        val body = gzBytes.toRequestBody("application/octet-stream".toMediaType())

        val req = Request.Builder()
            .url(cfg.eventsBatchUrl())
            .post(body)
            .header("Content-Encoding", "gzip")
            .header("x-batch-id", batchId)
            .header("x-count", count.toString())
            .also { baseHeaders(it, userId) }
            .build()

        http.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun postDocTextGz(
        userId: String,
        docId: String,
        kind: String,
        charCount: Int?,
        gzBytes: ByteArray
    ): Pair<Int, String> {
        val body = gzBytes.toRequestBody("application/octet-stream".toMediaType())

        val b = Request.Builder()
            .url(cfg.textUploadUrl())
            .post(body)
            .header("Content-Encoding", "gzip")
            .header("x-doc-id", docId)
            .header("x-kind", kind)

        if (charCount != null) b.header("x-char-count", charCount.toString())

        baseHeaders(b, userId)

        val req = b.build()

        Log.d("Telemetry", "postDocTextGz -> url=${cfg.textUploadUrl()} userId=$userId docId=$docId kind=$kind bytes=${gzBytes.size}")
        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            Log.d("Telemetry", "postDocTextGz <- status=${resp.code} body=$bodyStr")
            return resp.code to bodyStr
        }
    }

    fun postChatGz(
        userId: String,
        docId: String,
        chatId: String,
        model: String?,
        gzBytes: ByteArray
    ): Pair<Int, String> {
        val body = gzBytes.toRequestBody("application/octet-stream".toMediaType())

        val b = Request.Builder()
            .url(cfg.chatUploadUrl())
            .post(body)
            .header("Content-Encoding", "gzip")
            .header("x-doc-id", docId)
            .header("x-chat-id", chatId)

        if (!model.isNullOrBlank()) b.header("x-model", model)

        baseHeaders(b, userId)

        val req = b.build()

        Log.d("Telemetry", "postChatGz -> url=${cfg.chatUploadUrl()} userId=$userId docId=$docId chatId=$chatId bytes=${gzBytes.size}")
        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            Log.d("Telemetry", "postChatGz <- status=${resp.code} body=$bodyStr")
            return resp.code to bodyStr
        }
    }
}