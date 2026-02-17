package com.pdfliteai.ai

import com.pdfliteai.util.Chunk

fun retrieveTopK(question: String, chunks: List<Chunk>, k: Int = 8): List<Chunk> {
    val terms = question.lowercase()
        .split(Regex("\\W+"))
        .filter { it.length >= 3 }
        .toSet()

    fun score(text: String): Int {
        val low = text.lowercase()
        var s = 0
        for (t in terms) if (low.contains(t)) s += 2
        return s
    }

    return chunks
        .map { it to score(it.text) }
        .sortedByDescending { it.second }
        .take(k)
        .map { it.first }
}
