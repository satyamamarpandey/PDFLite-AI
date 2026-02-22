package com.pdfliteai.ui.components

data class ChatMessage(
    val role: ChatRole,
    val text: String
)

enum class ChatRole {
    User,
    Assistant,
    System
}
