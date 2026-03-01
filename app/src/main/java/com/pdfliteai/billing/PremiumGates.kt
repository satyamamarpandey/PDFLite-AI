package com.pdfliteai.billing

object PremiumGates {

    // ---------- Play Billing Product IDs ----------
    // Keep these EXACTLY same as Play Console product IDs
    const val SUB_MONTHLY = "pdflite_premium_monthly"
    const val SUB_ANNUAL = "pdflite_premium_annual"

    // ---------- Models ----------
    // (You were using MODEL_NOVA_MICRO / MODEL_NOVA_LITE elsewhere)
    const val MODEL_NOVA_MICRO = "nova-micro-v1"
    const val MODEL_NOVA_LITE = "nova-lite-v1"

    // ✅ Backward-compatible aliases (so older code compiles)
    const val NOVA_MICRO_MODEL = MODEL_NOVA_MICRO
    const val NOVA_LITE_MODEL = MODEL_NOVA_LITE

    // ---------- Free plan gates ----------
    const val FREE_CHATS_PER_PDF = 3

    // ✅ Backward-compatible alias (your BotSheet uses FREE_CHATS_PER_DOC)
    const val FREE_CHATS_PER_DOC = FREE_CHATS_PER_PDF

    const val FREE_RECENTS_LIMIT = 3
    const val PREMIUM_RECENTS_LIMIT = 50
    const val FREE_TEMPERATURE = 0f

    // ---------- Premium tools ----------
    fun isToolPremium(toolId: String): Boolean {
        return when (toolId) {
            "merge", "compress", "secure", "watermark", "rotate" -> true
            else -> false
        }
    }
}