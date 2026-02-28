package com.pdfliteai.telemetry

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

object Gzip {
    fun gzip(bytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz ->
            gz.write(bytes)
        }
        return bos.toByteArray()
    }
}