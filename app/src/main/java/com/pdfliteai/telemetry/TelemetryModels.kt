package com.pdfliteai.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryPrefs(
    val userId: String = "",
    val consentAnalytics: Boolean = true,
    val consentContent: Boolean = true
)