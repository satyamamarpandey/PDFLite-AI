package com.pdfliteai.settings

data class ReaderSettings(
    val keepScreenOn: Boolean = false,
    val recentsLimit: Int = 10,

    val autoOpenAi: Boolean = true,
    val defaultEntireDoc: Boolean = true,

    // 0.0..0.45 recommended
    val bgDim: Float = 0.22f,

    // default 3, allowed 1..10
    val chatHistoryLimit: Int = 3
)