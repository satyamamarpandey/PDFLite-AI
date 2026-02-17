package com.pdfliteai.util

import android.content.Context
import android.net.Uri
import java.io.File

fun copyUriToCache(ctx: Context, uri: Uri, filename: String): File {
    val out = File(ctx.cacheDir, filename)
    ctx.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Cannot open input stream for $uri" }
        out.outputStream().use { output -> input.copyTo(output) }
    }
    return out
}
