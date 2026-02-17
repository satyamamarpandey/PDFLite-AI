package com.pdfliteai.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

data class PageText(val pageIndex: Int, val text: String)

class PdfTextExtractor {
    fun extractAllPages(pdfFile: File): List<PageText> {
        PDDocument.load(pdfFile).use { doc ->
            val stripper = PDFTextStripper()
            val out = mutableListOf<PageText>()
            val total = doc.numberOfPages

            for (i in 1..total) {
                stripper.startPage = i
                stripper.endPage = i
                val text = stripper.getText(doc).trim()
                out += PageText(pageIndex = i - 1, text = text)
            }
            return out
        }
    }
}
