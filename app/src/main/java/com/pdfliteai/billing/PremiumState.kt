package com.pdfliteai.billing

data class PremiumState(
    val isPremium: Boolean = false,
    val activeProductId: String? = null,
    val lastCheckedAt: Long = 0L,
    val error: String? = null
)