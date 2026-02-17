package com.pdfliteai.pdf

import android.content.Context
import android.net.Uri
import com.pdfliteai.util.copyUriToCache
import java.io.File

class PdfRepository(private val ctx: Context) {
    fun importPdf(uri: Uri): File =
        copyUriToCache(ctx, uri, "doc_${System.currentTimeMillis()}.pdf")
}
