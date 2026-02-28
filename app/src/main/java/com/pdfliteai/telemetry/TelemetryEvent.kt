package com.pdfliteai.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryEvent(
    val ts: Long,
    val name: String,
    val userId: String,
    val params: Map<String, String> = emptyMap()
)