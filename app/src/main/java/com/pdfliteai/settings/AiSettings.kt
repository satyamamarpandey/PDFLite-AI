package com.pdfliteai.settings

import com.pdfliteai.data.ProviderId

data class AiSettings(
    val provider: ProviderId = ProviderId.GROQ,
    val model: String = "llama-3.1-8b-instant",
    val baseUrl: String = "", // used only for LOCAL_OPENAI_COMPAT
    val temperature: Float = 0.2f
)
