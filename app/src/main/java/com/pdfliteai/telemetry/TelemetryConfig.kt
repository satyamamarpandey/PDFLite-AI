package com.pdfliteai.telemetry

data class TelemetryConfig(
    val baseUrl: String,
    val authToken: String? = null,
    val appVersion: String = "",
    val deviceTag: String = "android"
) {
    private fun b(): String = baseUrl.trimEnd('/')

    fun eventsBatchUrl(): String = b() + "/v1/events/batch"
    fun profileUrl(): String = b() + "/v1/user/upsert"

    // ✅ NEW
    fun textUploadUrl(): String = b() + "/v1/text/upload"
    fun chatUploadUrl(): String = b() + "/v1/chat/upload"
}