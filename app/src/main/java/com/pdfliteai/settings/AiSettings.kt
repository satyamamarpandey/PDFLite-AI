package com.pdfliteai.settings

import com.pdfliteai.data.ProviderId

data class AiSettings(
    val provider: ProviderId = ProviderId.NOVA,
    val model: String = "nova-lite-v1",
    val baseUrl: String = "",
    val temperature: Float = 0.2f
)
