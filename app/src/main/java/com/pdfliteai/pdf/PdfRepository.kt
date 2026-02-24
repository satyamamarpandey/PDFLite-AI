package com.pdfliteai.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.pdfliteai.data.ProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import kotlin.math.max

enum class DocKind { NONE, PDF, TEXT }

class PdfRepository(
    private val ctx: Context,
    private val extractor: PdfTextExtractor = PdfTextExtractor(),
    private val ocr: OcrTextExtractor = OcrTextExtractor()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _docKind = MutableStateFlow(DocKind.NONE)
    val docKind: StateFlow<DocKind> = _docKind

    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile

    private val _cachedText = MutableStateFlow("")
    val cachedText: StateFlow<String> = _cachedText

    private val _extracting = MutableStateFlow(false)
    val extracting: StateFlow<Boolean> = _extracting

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    // ✅ extracted text cache
    private val memoryCache = LinkedHashMap<String, String>(8, 0.75f, true)

    // ✅ condensed cache
    private val condensedCache = LinkedHashMap<String, String>(8, 0.75f, true)

    private val _cachedCondensedText = MutableStateFlow("")
    val cachedCondensedText: StateFlow<String> = _cachedCondensedText

    // ✅ serialize "open/replace" so state doesn't race during Lazy measure
    private var openJob: Job? = null

    fun openPdf(uri: Uri) {
        openJob?.cancel()
        openJob = scope.launch {
            try {
                resetStateForNewDoc()
                _docKind.value = DocKind.PDF

                // ✅ IMPORTANT: immediately drop old PDF to dispose LazyColumn fast
                _pdfFile.value = null

                val f = copyToCachePdf(uri)
                _pdfFile.value = f

                getOrExtractText(f)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _lastError.value = "Failed to open PDF: ${t.message ?: t::class.java.simpleName}"
            }
        }
    }

    fun openText(uri: Uri) {
        openJob?.cancel()
        openJob = scope.launch {
            try {
                resetStateForNewDoc()
                _docKind.value = DocKind.TEXT
                _pdfFile.value = null

                val text = ctx.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }.orEmpty()

                if (text.isBlank()) _lastError.value = "Could not read text file."
                _cachedText.value = text
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _lastError.value = "Failed to open text file: ${t.message ?: t::class.java.simpleName}"
            }
        }
    }

    fun replacePdfFile(file: File) {
        openJob?.cancel()
        openJob = scope.launch {
            try {
                resetStateForNewDoc()
                _docKind.value = DocKind.PDF

                // ✅ dispose current PDF UI immediately
                _pdfFile.value = null

                _pdfFile.value = file
                getOrExtractText(file)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _lastError.value = "Failed to replace PDF: ${t.message ?: t::class.java.simpleName}"
            }
        }
    }

    fun warmExtract(file: File) {
        scope.launch { runCatching { getOrExtractText(file) } }
    }

    fun docKey(file: File): String = cacheKey(file)

    fun condensedKey(file: File, provider: ProviderId, model: String): String {
        return "${cacheKey(file)}|${provider.name}|${model.trim()}"
    }

    fun hasCondensed(key: String): Boolean = condensedCache.containsKey(key)

    fun putCondensed(key: String, value: String) {
        val v = value.trim()
        if (v.isBlank()) return

        condensedCache[key] = v
        while (condensedCache.size > 6) condensedCache.remove(condensedCache.entries.first().key)
        _cachedCondensedText.value = v
    }

    suspend fun getOrComputeCondensed(key: String, compute: suspend () -> String): String {
        condensedCache[key]?.let {
            _cachedCondensedText.value = it
            return it
        }
        val v = runCatching { compute() }.getOrDefault("").trim()
        if (v.isNotBlank()) putCondensed(key, v)
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

                if (ocrText.isBlank()) _lastError.value =
                    "No selectable text found. This PDF may be scanned (image-only)."

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

    private fun resetStateForNewDoc() {
        _lastError.value = null
        _cachedText.value = ""
        _cachedCondensedText.value = ""
        _extracting.value = false
    }

    private fun memoryCachePut(key: String, value: String) {
        memoryCache[key] = value
        while (memoryCache.size > 6) memoryCache.remove(memoryCache.entries.first().key)
    }

    private fun copyToCachePdf(uri: Uri): File {
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