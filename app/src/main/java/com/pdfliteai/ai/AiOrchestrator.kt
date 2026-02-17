package com.pdfliteai.ai

import com.pdfliteai.pdf.PageText
import com.pdfliteai.util.Chunk
import com.pdfliteai.util.chunkPages

class AiOrchestrator(private val provider: ModelProvider) {

    suspend fun ask(
        question: String,
        scope: String,          // "SelectedText" or "EntireDocument"
        selectedText: String?,
        pages: List<PageText>
    ): String {

        val excerpts: List<Chunk> = if (scope == "SelectedText") {
            val sel = (selectedText ?: "").trim()
            require(sel.isNotEmpty()) { "No selected text. Paste/select text first." }
            listOf(Chunk(pageIndex = -1, text = sel))
        } else {
            val pairs = pages.map { it.pageIndex to it.text }.filter { it.second.isNotBlank() }
            val chunks = chunkPages(pairs)
            retrieveTopK(question, chunks, k = 8)
        }

        val excerptBlock = buildString {
            excerpts.forEach { c ->
                val label = if (c.pageIndex >= 0) "p.${c.pageIndex + 1}" else "selection"
                append("[$label]\n")
                append(c.text.trim())
                append("\n\n")
            }
        }

        val sys = """
You are a PDF assistant.
Rules:
- Use only the provided excerpts.
- If the excerpts don't contain the answer, say what is missing and suggest what to search for.
- Cite sources using labels like [p.12].
""".trim()

        val user = """
Question: $question
Scope: $scope

Excerpts:
$excerptBlock
""".trim()

        return provider.chat(
            listOf(
                ChatMessage("system", sys),
                ChatMessage("user", user)
            )
        )
    }
}
