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
        // ✅ Your Cloudflare Worker base URL
        // If you ever change Worker name/subdomain, update this one line.
        private const val WORKER_BASE_URL = "https://pdflite-ai-proxy.7satyampandey.workers.dev"

        fun defaultHttp(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build()
    }

    private val workerProxy = WorkerProxyProvider(openAICompatHttp(openAICompat))

    // Small helper to reuse the same OkHttpClient used by OpenAICompatProvider
    // (OpenAICompatProvider already has an http field internally, but it’s private,
    // so we just build a default one here to keep changes minimal and predictable.)
    private fun openAICompatHttp(@Suppress("UNUSED_PARAMETER") p: OpenAICompatProvider): OkHttpClient =
        defaultHttp()

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

    /**
     * ✅ IMPORTANT CHANGE:
     * - For NOVA (micro/lite) and OPENROUTER: baseUrl becomes WORKER_BASE_URL and apiKey is ignored.
     * - For LOCAL_OPENAI_COMPAT: baseUrl comes from settings and apiKey is used.
     */
    private fun resolveConfig(s: AiSettings, apiKey: String): AiProvider {
        return when (s.provider) {
            ProviderId.LOCAL_OPENAI_COMPAT -> {
                val baseUrl = s.baseUrl.trim().removeSuffix("/")
                if (baseUrl.isBlank()) error("Base URL is empty for Local provider. Set it in Settings.")
                AiProvider(
                    provider = s.provider,
                    model = s.model.trim(),
                    baseUrl = baseUrl,
                    apiKey = apiKey.trim(),
                    temperature = s.temperature
                )
            }

            ProviderId.NOVA, ProviderId.GROQ, ProviderId.OPENROUTER -> {
                AiProvider(
                    provider = s.provider,
                    model = s.model.trim(),
                    baseUrl = WORKER_BASE_URL, // Worker base
                    apiKey = "",               // not used; keys are in Worker secrets
                    temperature = s.temperature
                )
            }
        }
    }

    /**
     * ✅ Main chat:
     * - LOCAL_OPENAI_COMPAT -> direct OpenAI-compatible baseUrl
     * - others -> Cloudflare Worker (/chat)
     */
    suspend fun chat(s: AiSettings, apiKey: String, userText: String): String {
        val cfg = resolveConfig(s, apiKey)
        return chatInternal(cfg, userText)
    }

    private suspend fun chatInternal(cfg: AiProvider, userText: String): String {
        return if (cfg.provider == ProviderId.LOCAL_OPENAI_COMPAT) {
            openAICompat.chat(cfg, userText)
        } else {
            workerProxy.chat(WORKER_BASE_URL, cfg, userText)
        }
    }

    /**
     * ✅ Condense large doc into <= maxDocChars characters.
     * Now uses the same routing (Worker vs Local) so it won’t break.
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

        if (raw.length <= maxDocChars) {
            condensedCachePut(cacheKey, raw)
            return raw
        }

        val overhead = 900
        val chunkChars = (maxRequestChars - overhead).coerceAtLeast(1500)

        val chunks = splitByChars(raw, chunkChars)
        val bracketCount = max(1, chunks.size)

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
        return chatInternal(cfg, prompt)
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
        return chatInternal(cfg, prompt)
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
            val niceEnd = text.lastIndexOf('\n', end)
                .takeIf { it > i + (chunkChars * 0.6).toInt() } ?: end

            out.add(text.substring(i, niceEnd).trim())
            i = niceEnd
        }
        return out.filter { it.isNotBlank() }
    }
}