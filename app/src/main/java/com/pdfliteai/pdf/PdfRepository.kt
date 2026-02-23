package com.pdfliteai.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.pdfliteai.data.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import kotlin.math.max

class PdfRepository(
    private val ctx: Context,
    private val extractor: PdfTextExtractor = PdfTextExtractor(),
    private val ocr: OcrTextExtractor = OcrTextExtractor()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile

    private val _cachedText = MutableStateFlow("")
    val cachedText: StateFlow<String> = _cachedText

    private val _extracting = MutableStateFlow(false)
    val extracting: StateFlow<Boolean> = _extracting

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    // ✅ existing extracted text cache
    private val memoryCache = LinkedHashMap<String, String>(8, 0.75f, true)

    // ✅ NEW: condensed doc cache (Groq-safe <= 6000 chars)
    private val condensedCache = LinkedHashMap<String, String>(8, 0.75f, true)

    private val _cachedCondensedText = MutableStateFlow("")
    val cachedCondensedText: StateFlow<String> = _cachedCondensedText

    fun openPdf(uri: Uri) {
        scope.launch {
            try {
                _lastError.value = null
                _cachedText.value = ""
                _cachedCondensedText.value = ""
                _extracting.value = false

                val f = copyToCache(uri)
                _pdfFile.value = f

                warmExtract(f)
            } catch (t: Throwable) {
                _lastError.value = "Failed to open PDF: ${t.message ?: t::class.java.simpleName}"
            }
        }
    }

    fun replacePdfFile(file: File) {
        scope.launch {
            _lastError.value = null
            _cachedText.value = ""
            _cachedCondensedText.value = ""
            _extracting.value = false
            _pdfFile.value = file
            warmExtract(file)
        }
    }

    fun warmExtract(file: File) {
        scope.launch { getOrExtractText(file) }
    }

    // ✅ Key used for text extraction caching (existing behavior)
    fun docKey(file: File): String = cacheKey(file)

    // ✅ Key used for condensed caching
    fun condensedKey(file: File, provider: ProviderId, model: String): String {
        return "${cacheKey(file)}|${provider.name}|${model.trim()}"
    }

    fun hasCondensed(key: String): Boolean = condensedCache.containsKey(key)

    fun putCondensed(key: String, value: String) {
        val v = value.trim()
        if (v.isBlank()) return

        condensedCache[key] = v
        while (condensedCache.size > 6) {
            val firstKey = condensedCache.entries.first().key
            condensedCache.remove(firstKey)
        }
        _cachedCondensedText.value = v
    }

    suspend fun getOrComputeCondensed(
        key: String,
        compute: suspend () -> String
    ): String {
        condensedCache[key]?.let {
            _cachedCondensedText.value = it
            return it
        }

        val v = runCatching { compute() }.getOrDefault("").trim()
        if (v.isNotBlank()) {
            putCondensed(key, v)
        }
        return v
    }

    suspend fun getOrExtractText(file: File): String {
        val key = cacheKey(file)

        memoryCache[key]?.let {
            _cachedText.value = it
            return it
        }

        val disk = diskCacheFile(key)
        if (disk.exists()) {
            val txt = runCatching { disk.readText() }.getOrDefault("")
            if (txt.isNotBlank()) {
                memoryCachePut(key, txt)
                _cachedText.value = txt
                return txt
            }
        }

        _extracting.value = true

        val txt = try {
            val direct = runCatching { extractor.extractAllText(file) }.getOrDefault("").trim()
            if (direct.isNotBlank()) {
                direct
            } else {
                val ocrText = runCatching { ocrPdfFirstPages(file, maxPages = 6) }
                    .getOrElse { e ->
                        _lastError.value = "OCR failed: ${e.message ?: e::class.java.simpleName}"
                        ""
                    }
                    .trim()

                if (ocrText.isBlank()) {
                    _lastError.value = "No selectable text found. This PDF may be scanned (image-only)."
                }
                ocrText
            }
        } catch (t: Throwable) {
            _lastError.value = "Text extraction failed: ${t.message ?: t::class.java.simpleName}"
            ""
        } finally {
            _extracting.value = false
        }

        if (txt.isNotBlank()) {
            memoryCachePut(key, txt)
            runCatching { disk.writeText(txt) }
        } else {
            _lastError.value = _lastError.value ?: "Could not extract text from this PDF."
        }

        _cachedText.value = txt
        return txt
    }

    private fun memoryCachePut(key: String, value: String) {
        memoryCache[key] = value
        while (memoryCache.size > 6) {
            val firstKey = memoryCache.entries.first().key
            memoryCache.remove(firstKey)
        }
    }

    private fun copyToCache(uri: Uri): File {
        val inStream = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream from Uri")

        val out = File(ctx.cacheDir, "pdflite_${System.currentTimeMillis()}.pdf")
        inStream.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun cacheKey(file: File): String {
        val meta = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val md = MessageDigest.getInstance("SHA-256").digest(meta.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    private fun diskCacheFile(key: String): File {
        val dir = File(ctx.cacheDir, "text_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.txt")
    }

    private suspend fun ocrPdfFirstPages(file: File, maxPages: Int): String {
        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                val pageCount = renderer.pageCount
                val pages = minOf(maxPages, pageCount)

                val out = StringBuilder()

                for (i in 0 until pages) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val w = max(1, page.width * scale)
                        val h = max(1, page.height * scale)

                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val pageText = ocr.extractTextFromBitmap(bmp).trim()
                        bmp.recycle()

                        if (pageText.isNotBlank()) {
                            out.appendLine("=== PAGE ${i + 1} ===")
                            out.appendLine(pageText)
                            out.appendLine()
                        }
                    }
                }

                out.toString().trim()
            }
        } finally {
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }
}