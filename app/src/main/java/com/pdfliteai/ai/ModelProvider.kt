package com.pdfliteai.ai

data class ChatMessage(val role: String, val content: String)

interface ModelProvider {
    suspend fun chat(messages: List<ChatMessage>): String
}
