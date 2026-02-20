package com.pdfliteai.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

class PdfTextExtractor {

    fun extractAllText(pdfFile: File): String {
        if (!pdfFile.exists()) return ""

        // ✅ Use temp file memory to reduce OOM on large PDFs
        val mem = MemoryUsageSetting.setupTempFileOnly()

        PDDocument.load(pdfFile, mem).use { doc ->
            // Encrypted PDFs: try removing security (may still fail if password required)
            if (doc.isEncrypted) {
                runCatching { doc.setAllSecurityToBeRemoved(true) }
            }

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            return stripper.getText(doc).trim()
        }
    }
}
