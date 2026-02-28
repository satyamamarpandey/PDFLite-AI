package com.pdfliteai.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val userId: String = "",

    // ✅ NEW (stable identity across reinstalls)
    val identityKey: String = "",

    // "google" | "manual"
    val authMethod: String = "",

    // mandatory for both modes
    val name: String = "",

    // google sign-in
    val emailGoogle: String = "",

    // manual sign-in
    val emailManual: String = "",

    // true for google, false for manual (unless you add verification later)
    val emailVerified: Boolean = false,

    // optional
    val gender: String = "",
    val city: String = "",
    val state: String = ""
)