package com.pdfliteai.ai

import com.pdfliteai.data.ProviderId
import com.pdfliteai.settings.AiSettings
import okhttp3.OkHttpClient
import java.time.Duration
import kotlin.math.max

class AiOrchestrator(
    private val openAICompat: OpenAICompatProvider
) {
    companion object {
        fun defaultHttp(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build()
    }

    // ✅ In-memory LRU cache for condensed docs
    private val condensedCache = LinkedHashMap<String, String>(8, 0.75f, true)

    private fun condensedCachePut(key: String, value: String) {
        condensedCache[key] = value
        while (condensedCache.size > 6) {
            val firstKey = condensedCache.entries.first().key
            condensedCache.remove(firstKey)
        }
    }

    fun hasCondensed(cacheKey: String): Boolean = condensedCache.containsKey(cacheKey)

    private fun resolveBaseUrl(s: AiSettings): String {
        val base = when (s.provider) {
            ProviderId.GROQ -> "https://api.groq.com/openai/v1"
            ProviderId.OPENROUTER -> "https://openrouter.ai/api/v1"
            ProviderId.NOVA -> "https://api.nova.amazon.com"
            ProviderId.LOCAL_OPENAI_COMPAT -> s.baseUrl
        }
        return base.trim().removeSuffix("/")
    }

    private fun resolveConfig(s: AiSettings, apiKey: String): AiProvider {
        val baseUrl = resolveBaseUrl(s)
        if (baseUrl.isBlank()) error("Base URL is empty for provider ${s.provider}. Set it in Settings.")
        return AiProvider(
            provider = s.provider,
            model = s.model.trim(),
            baseUrl = baseUrl,
            apiKey = apiKey.trim(),
            temperature = s.temperature
        )
    }

    suspend fun chat(s: AiSettings, apiKey: String, userText: String): String {
        val cfg = resolveConfig(s, apiKey)
        return openAICompat.chat(cfg, userText)
    }

    /**
     * ✅ NEW: Condense a large document into <= maxDocChars characters.
     * - Designed for Groq "6000 chars" limit by keeping EACH request <= maxRequestChars.
     * - Uses chunk -> dense notes -> reduce if needed.
     * - Cached by cacheKey.
     */
    suspend fun condenseDocument(
        s: AiSettings,
        apiKey: String,
        cacheKey: String,
        fullText: String,
        maxRequestChars: Int = 6000,
        maxDocChars: Int = 6000
    ): String {
        condensedCache[cacheKey]?.let { return it }

        val cfg = resolveConfig(s, apiKey)
        val raw = fullText.trim()
        if (raw.isBlank()) return ""

        // If already small enough, no need to call AI
        if (raw.length <= maxDocChars) {
            condensedCachePut(cacheKey, raw)
            return raw
        }

        // Keep room for instructions + headers
        val overhead = 900
        val chunkChars = (maxRequestChars - overhead).coerceAtLeast(1500)

        val chunks = splitByChars(raw, chunkChars)
        val bracketCount = max(1, chunks.size)

        // Your "bracket" logic: count chunks (ceil(len/chunkChars))
        // Then make each chunk summary smaller so combined stays <= maxDocChars.
        val targetPerChunk = ((maxDocChars - 200) / bracketCount).coerceAtLeast(350)

        val denseNotes = ArrayList<String>(bracketCount)
        for ((i, c) in chunks.withIndex()) {
            val note = summarizeChunkDense(
                cfg = cfg,
                chunk = c,
                part = i + 1,
                total = bracketCount,
                targetChars = targetPerChunk,
                maxRequestChars = maxRequestChars
            ).trim()
            if (note.isNotBlank()) denseNotes.add(note)
        }

        var combined = denseNotes.joinToString("\n").trim()

        // If still too large, reduce to <= maxDocChars
        if (combined.length > maxDocChars) {
            combined = reduceToMax(
                cfg = cfg,
                text = combined,
                maxDocChars = maxDocChars,
                maxRequestChars = maxRequestChars
            ).trim()
        }

        if (combined.length > maxDocChars) combined = combined.take(maxDocChars)

        condensedCachePut(cacheKey, combined)
        return combined
    }

    private suspend fun summarizeChunkDense(
        cfg: AiProvider,
        chunk: String,
        part: Int,
        total: Int,
        targetChars: Int,
        maxRequestChars: Int
    ): String {
        val prefix = buildString {
            append("Condense the following document text into dense notes while preserving facts, names, numbers.\n")
            append("Keep it under $targetChars characters.\n")
            append("No preamble. Use tight bullet points.\n")
            append("Part $part/$total.\n\n")
            append("TEXT:\n")
        }

        val prompt = fitPrompt(prefix, chunk, maxRequestChars)
        return openAICompat.chat(cfg, prompt)
    }

    private suspend fun reduceToMax(
        cfg: AiProvider,
        text: String,
        maxDocChars: Int,
        maxRequestChars: Int
    ): String {
        val prefix = buildString {
            append("Compress the following notes into <= $maxDocChars characters.\n")
            append("Preserve all key facts and numbers. No preamble.\n\n")
            append("NOTES:\n")
        }

        val prompt = fitPrompt(prefix, text, maxRequestChars)
        return openAICompat.chat(cfg, prompt)
    }

    private fun fitPrompt(prefix: String, payload: String, maxChars: Int): String {
        if (prefix.length >= maxChars) return prefix.take(maxChars)

        val allowed = (maxChars - prefix.length).coerceAtLeast(0)
        val body = if (payload.length <= allowed) payload else payload.take(allowed)
        return prefix + body
    }

    private fun splitByChars(s: String, chunkChars: Int): List<String> {
        val text = s.trim()
        if (text.isEmpty()) return emptyList()
        if (text.length <= chunkChars) return listOf(text)

        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val end = (i + chunkChars).coerceAtMost(text.length)

            // try breaking on newline for nicer chunks
            val niceEnd = text.lastIndexOf('\n', end).takeIf { it > i + (chunkChars * 0.6).toInt() } ?: end

            out.add(text.substring(i, niceEnd).trim())
            i = niceEnd
        }
        return out.filter { it.isNotBlank() }
    }
}