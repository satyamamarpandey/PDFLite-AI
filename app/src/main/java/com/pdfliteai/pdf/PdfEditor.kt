package com.pdfliteai.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

class PdfEditor {

    fun rotatePage(pdfFile: File, pageIndex: Int, degrees: Int): File {
        PDDocument.load(pdfFile).use { doc ->
            val page = doc.getPage(pageIndex)
            page.rotation = (page.rotation + degrees) % 360
            return saveAsNew(doc, pdfFile, "_rot")
        }
    }

    fun deletePage(pdfFile: File, pageIndex: Int): File {
        PDDocument.load(pdfFile).use { doc ->
            doc.removePage(pageIndex)
            return saveAsNew(doc, pdfFile, "_del")
        }
    }

    fun reversePages(pdfFile: File): File {
        PDDocument.load(pdfFile).use { doc ->
            val pages = (0 until doc.numberOfPages).map { doc.getPage(it) }
            val outDoc = PDDocument()
            try {
                for (p in pages.reversed()) outDoc.addPage(p)
                return saveAsNew(outDoc, pdfFile, "_rev")
            } finally {
                outDoc.close()
            }
        }
    }

    private fun saveAsNew(doc: PDDocument, original: File, suffix: String): File {
        val out = File(original.parentFile, original.nameWithoutExtension + suffix + ".pdf")
        doc.save(out)
        return out
    }
}
