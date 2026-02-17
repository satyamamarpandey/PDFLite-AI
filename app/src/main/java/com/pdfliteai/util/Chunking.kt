package com.pdfliteai.util

data class Chunk(val pageIndex: Int, val text: String)

fun chunkPages(
    pages: List<Pair<Int, String>>,
    maxChars: Int = 3500,
    overlapChars: Int = 300
): List<Chunk> {
    val out = mutableListOf<Chunk>()
    for ((pi, raw) in pages) {
        val t = raw.trim()
        if (t.isEmpty()) continue
        var i = 0
        while (i < t.length) {
            val end = (i + maxChars).coerceAtMost(t.length)
            out += Chunk(pi, t.substring(i, end))
            if (end >= t.length) break
            val next = end - overlapChars
            i = if (next <= i) end else next
        }
    }
    return out
}
