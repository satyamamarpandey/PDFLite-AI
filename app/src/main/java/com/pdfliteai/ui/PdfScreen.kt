// PdfScreen.kt
package com.pdfliteai.ui

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pdfliteai.MainActivity
import com.pdfliteai.ai.AiOrchestrator
import com.pdfliteai.data.ProviderId
import com.pdfliteai.pdf.DocKind
import com.pdfliteai.pdf.PdfEditor
import com.pdfliteai.pdf.PdfRepository
import com.pdfliteai.settings.SettingsViewModel
import com.pdfliteai.ui.components.BotFab
import com.pdfliteai.ui.components.BotSheet
import com.pdfliteai.ui.components.ChatMessage
import com.pdfliteai.ui.components.ChatRole
import com.pdfliteai.ui.components.GlowPrimaryButton
import com.pdfliteai.ui.components.PdfSearchResult
import com.pdfliteai.ui.components.PdfTopBar
import com.pdfliteai.ui.components.ToolsSheetV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

// PDFBox Android
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix.getRotateInstance

private const val TTS_CHUNK_MAX = 3500
private const val TAG_PDF_SCREEN = "PdfScreen"

@Composable
fun PdfScreen(
    repo: PdfRepository,
    ai: AiOrchestrator,
    onOpenSettings: () -> Unit,
    initialOpenUri: Uri? = null,
    initialOpenMime: String? = null
) {
    val vm: SettingsViewModel = viewModel()
    val cs = rememberCoroutineScope()
    val ctx = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(Unit) { runCatching { PDFBoxResourceLoader.init(ctx) } }

    val docKind by repo.docKind.collectAsState()
    val pdfFile by repo.pdfFile.collectAsState()
    val cachedText by repo.cachedText.collectAsState()
    val repoErr by repo.lastError.collectAsState()

    val s by vm.aiSettings.collectAsState()
    val reader by vm.readerSettings.collectAsState()
    val recents by vm.recentDocs.collectAsState()

    DisposableEffect(reader.keepScreenOn) {
        val prev = view.keepScreenOn
        view.keepScreenOn = reader.keepScreenOn
        onDispose { view.keepScreenOn = prev }
    }

    var botOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }

    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var pageCount by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(0) }

    // ✅ Pinch + double-tap zoom
    var zoomScale by remember { mutableFloatStateOf(1f) }

    var docDisplayName by remember { mutableStateOf("document") }

    val history = remember { mutableStateListOf<ChatMessage>() }
    val editor = remember { PdfEditor() }

    // Search state
    var docSessionId by remember { mutableIntStateOf(0) }
    var cleanPdfFile by remember { mutableStateOf<File?>(null) }
    var searchHighlightActive by remember { mutableStateOf(false) }

    // OCR
    val ocrRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // TTS
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsSpeaking by remember { mutableStateOf(false) }
    var ttsJob: Job? by remember { mutableStateOf(null) }

    // ✅ prevents overlapping opens (and UI thread freezes)
    var openJob: Job? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(ctx) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                engine.language = Locale.US
                engine.setSpeechRate(1.0f)
            }
        }
        tts = engine
        onDispose {
            runCatching {
                ttsJob?.cancel()
                engine.stop()
                engine.shutdown()
            }
            tts = null
        }
    }

    // Recents packing
    val recentSep = "||"

    fun unpackRecent(entry: String): Pair<String, String?> {
        val i = entry.indexOf(recentSep)
        return if (i >= 0) {
            val u = entry.substring(0, i).trim()
            val n = entry.substring(i + recentSep.length).trim()
            u to n.ifBlank { null }
        } else entry.trim() to null
    }

    fun packRecent(uriStr: String, name: String): String {
        val u = uriStr.trim()
        val n = name.trim()
        return if (n.isBlank()) u else "$u$recentSep$n"
    }

    // ✅ keep as local functions (no 'private' inside composable)
    fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.trim() else null
                }
        }.getOrNull().orEmpty().trim().ifBlank { null }
    }

    fun resolveDisplayName(uri: Uri): String {
        // 1) Standard query
        queryDisplayName(uri)?.let { return it }

        // 2) Downloads provider fallback (fixes "document:34" on many devices)
        val isDoc = runCatching { DocumentsContract.isDocumentUri(ctx, uri) }.getOrDefault(false)
        if (isDoc && uri.authority == "com.android.providers.downloads.documents") {
            val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrDefault("")
            val id = docId.substringAfterLast(':').toLongOrNull() ?: docId.toLongOrNull()
            if (id != null) {
                val dl = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    id
                )
                queryDisplayName(dl)?.let { return it }
            }
        }

        // 3) Last resort: decoded lastPathSegment
        val seg = (uri.lastPathSegment ?: uri.toString()).trim()
        val decoded = runCatching { URLDecoder.decode(seg, "UTF-8") }.getOrDefault(seg)
        return decoded.ifBlank { uri.toString() }
    }

    fun displayNameForRecent(entry: String): String {
        val (uriStr, savedName) = unpackRecent(entry)
        return savedName?.takeIf { it.isNotBlank() } ?: resolveDisplayName(uriStr.toUri())
    }

    fun isPdfByExtension(uri: Uri): Boolean = uri.toString().lowercase().contains(".pdf")
    fun isTextByExtension(uri: Uri): Boolean {
        val u = uri.toString().lowercase()
        return u.endsWith(".txt") || u.endsWith(".log") || u.endsWith(".md") || u.endsWith(".csv")
    }

    /**
     * ✅ Persist permission correctly:
     * takePersistableUriPermission ONLY accepts READ and/or WRITE (NOT PERSISTABLE flag).
     */
    fun tryTakePersistableRead(uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure {
            // This can fail for some URIs (e.g., ACTION_VIEW temp grants). Not fatal.
            Log.w(TAG_PDF_SCREEN, "Persistable permission not granted: ${it.message}")
        }
    }

    /**
     * ✅ PDF + TEXT only
     * ✅ Open on background thread to avoid ANR/"keeps stopping"
     * ✅ Don’t add to recents unless open succeeded
     */
    fun openAny(uri: Uri, mimeHint: String? = null) {
        val uriStr = uri.toString()
        val mime = (mimeHint ?: ctx.contentResolver.getType(uri)).orEmpty().lowercase()
        val name = resolveDisplayName(uri)
        docDisplayName = name

        // new doc session (UI state)
        docSessionId += 1
        searchHighlightActive = false
        cleanPdfFile = null
        error = null

        // cancel any previous open work
        openJob?.cancel()
        openJob = cs.launch {
            val ok = runCatching {
                // ensure PDFBox resources are initialized before any PDFBox usage
                runCatching { PDFBoxResourceLoader.init(ctx) }

                withContext(Dispatchers.IO) {
                    when {
                        mime == "application/pdf" || isPdfByExtension(uri) -> repo.openPdf(uri)
                        mime.startsWith("text/") || isTextByExtension(uri) -> repo.openText(uri)
                        else -> throw IllegalStateException("Unsupported file type. Please open a PDF or Text file.")
                    }
                }
            }.onFailure {
                val msg = it.message ?: it::class.java.simpleName
                error = msg
                Log.e(TAG_PDF_SCREEN, "openAny failed: $msg", it)
            }.isSuccess

            if (!ok) return@launch

            // only after successful open
            vm.addRecent(packRecent(uriStr, name))
            botOpen = reader.autoOpenAi
        }
    }

    // "Open with"
    LaunchedEffect(initialOpenUri, initialOpenMime) {
        val uri = initialOpenUri ?: return@LaunchedEffect
        tryTakePersistableRead(uri)
        openAny(uri, mimeHint = initialOpenMime)
        MainActivity.clearPendingOpen()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            tryTakePersistableRead(uri)
            openAny(uri)
        }
    }

    // on file change (PDF only)
    LaunchedEffect(pdfFile) {
        val f = pdfFile ?: return@LaunchedEffect
        if (!searchHighlightActive) cleanPdfFile = f
    }

    // on new doc
    LaunchedEffect(docSessionId) {
        pageIndex = 0
        question = ""
        history.clear()
        zoomScale = 1f
        pageCount = 0

        // stop TTS if a new doc is opened
        runCatching {
            ttsJob?.cancel()
            tts?.stop()
            ttsSpeaking = false
        }
    }

    fun addToHistory(role: ChatRole, text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        history.add(ChatMessage(role = role, text = t))
    }

    suspend fun runAsk(qOverride: String? = null) {
        val full = when (docKind) {
            DocKind.PDF -> {
                val f = pdfFile ?: throw IllegalStateException("Open a PDF first.")
                repo.getOrExtractText(f).trim()
            }
            DocKind.TEXT -> cachedText.trim()
            else -> throw IllegalStateException("Open a document first.")
        }

        if (full.isBlank()) throw IllegalStateException("Could not extract text from document.")

        val q = (qOverride ?: question).trim()
        if (q.isBlank()) throw IllegalStateException("Type a question or use a quick prompt.")

        val key = vm.getApiKey(s.provider).trim()
        if (s.provider == ProviderId.LOCAL_OPENAI_COMPAT && key.isBlank()) {
            throw IllegalStateException("Missing API key for Local provider. Please set it in Settings.")
        }

        val prompt = buildAskPromptWithinLimit(
            provider = s.provider,
            docText = full,
            question = q
        )

        addToHistory(ChatRole.User, q)
        val out = ai.chat(s, key, prompt)
        addToHistory(ChatRole.Assistant, out)
    }

    fun rotatePage() {
        val f = pdfFile ?: return
        cs.launch {
            val out = runCatching { editor.rotatePage(f, pageIndex, 90) }
                .getOrElse {
                    error = it.message ?: it::class.java.simpleName
                    return@launch
                }
            repo.replacePdfFile(out)
        }
    }

    fun deletePageAt(idx0: Int) {
        val f = pdfFile ?: return
        val safeIdx = idx0.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        cs.launch {
            val out = runCatching { editor.deletePage(f, safeIdx) }
                .getOrElse {
                    error = it.message ?: it::class.java.simpleName
                    return@launch
                }
            repo.replacePdfFile(out)
            pageIndex = pageIndex.coerceAtMost((pageCount - 2).coerceAtLeast(0))
        }
    }

    // ✅ explicit TTS controls (no toggle desync)
    fun stopReadAloud() {
        val engine = tts
        if (engine == null) {
            ttsSpeaking = false
            return
        }
        runCatching {
            ttsJob?.cancel()
            engine.stop()
        }
        ttsSpeaking = false
    }

    fun startReadAloud() {
        val text = cachedText.trim()
        val engine = tts
        if (engine == null || !ttsReady) {
            error = "Text-to-Speech not ready."
            return
        }
        if (text.isBlank()) {
            error = "No extracted text to read."
            return
        }
        if (ttsSpeaking) return

        stopReadAloud()

        ttsSpeaking = true
        val chunks = chunkText(text)

        ttsJob = cs.launch(Dispatchers.Main) {
            for ((i, c) in chunks.withIndex()) {
                if (!isActive || !ttsSpeaking) break
                engine.speak(
                    c,
                    if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    null,
                    "utt_$i"
                )
                delay(120)
            }
            ttsSpeaking = false
        }
    }

    fun clearSearchHighlights() {
        val base = cleanPdfFile
        if (base != null) {
            searchHighlightActive = false
            repo.replacePdfFile(base)
        } else {
            searchHighlightActive = false
        }
    }

    suspend fun ocrRectanglesForPage(
        pdf: File,
        pageIndex0: Int,
        queryRaw: String,
        pageW: Float,
        pageH: Float
    ): List<PDRectangle> = withContext(Dispatchers.Default) {
        val q = queryRaw.trim().lowercase()
        if (q.isBlank()) return@withContext emptyList()

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex0 !in 0 until renderer.pageCount) return@withContext emptyList()

            page = renderer.openPage(pageIndex0)

            val dpi = 170f
            val bmpW = ((pageW / 72f) * dpi).toInt().coerceAtLeast(1)
            val bmpH = ((pageH / 72f) * dpi).toInt().coerceAtLeast(1)

            val maxPixels = 5_500_000L
            var w = bmpW
            var h = bmpH
            val px = w.toLong() * h.toLong()
            if (px > maxPixels) {
                val factor = sqrt(maxPixels.toDouble() / px.toDouble()).toFloat()
                w = (w * factor).toInt().coerceAtLeast(1)
                h = (h * factor).toInt().coerceAtLeast(1)
            }

            val bitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val image = InputImage.fromBitmap(bitmap, 0)
            val result = ocrRecognizer.process(image).await()

            val rectanglesPx = ArrayList<android.graphics.Rect>(64)

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val elements = line.elements
                    if (elements.isEmpty()) continue

                    val joined = buildString {
                        for (i in elements.indices) {
                            if (i > 0) append(' ')
                            append(elements[i].text)
                        }
                    }
                    val joinedL = joined.lowercase()

                    var idx = joinedL.indexOf(q)
                    while (idx >= 0) {
                        val end = idx + q.length
                        var off = 0
                        for (i in elements.indices) {
                            val token = elements[i].text
                            val start = off
                            val stop = off + token.length
                            val overlaps = stop > idx && start < end
                            if (overlaps) {
                                elements[i].boundingBox?.let { rectanglesPx.add(it) }
                            }
                            off = stop + 1
                        }
                        idx = joinedL.indexOf(q, idx + 1)
                    }
                }
            }

            if (rectanglesPx.isEmpty()) return@withContext emptyList()

            val out = ArrayList<PDRectangle>(rectanglesPx.size)
            for (r in rectanglesPx) {
                val left = (r.left.toFloat() / w) * pageW
                val right = (r.right.toFloat() / w) * pageW

                val topFromTop = (r.top.toFloat() / h) * pageH
                val bottomFromTop = (r.bottom.toFloat() / h) * pageH

                val y = pageH - bottomFromTop
                val ww = (right - left).coerceAtLeast(1f)
                val hh = (bottomFromTop - topFromTop).coerceAtLeast(1f)

                out.add(PDRectangle(left, y, ww, hh))
            }
            out
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { page?.close() }
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    suspend fun searchHighlightAndGo(queryRaw: String): PdfSearchResult {
        val current = pdfFile ?: return PdfSearchResult(false, 0, emptyList(), -1)
        val query = queryRaw.trim()

        if (query.isBlank()) {
            withContext(Dispatchers.Main) { clearSearchHighlights() }
            return PdfSearchResult(false, 0, emptyList(), -1)
        }

        val base = cleanPdfFile ?: current

        return withContext(Dispatchers.IO) {
            runCatching { PDFBoxResourceLoader.init(ctx) }

            val outFile = File(ctx.cacheDir, "pdflite_search_highlight_${System.currentTimeMillis()}.pdf")
            var occurrences = 0
            val pagesFound = mutableSetOf<Int>()
            var firstPage = -1

            PDDocument.load(base).use { doc ->
                val total = doc.numberOfPages
                for (p in 0 until total) {
                    val page = doc.getPage(p)
                    val mb = page.mediaBox ?: PDRectangle(0f, 0f, 600f, 800f)

                    val (pageText, pos) = extractPageTextWithPositions(doc, p + 1)

                    val ranges = if (pageText.isNotBlank() && pos.isNotEmpty()) {
                        findAllOccurrenceRanges(pageText, query)
                    } else emptyList()

                    if (ranges.isNotEmpty()) {
                        pagesFound.add(p)
                        if (firstPage < 0) firstPage = p
                        occurrences += ranges.size
                        burnHighlightsIntoPage(doc, page, mb.height, pos, ranges)
                        continue
                    }

                    val shouldOcr = pos.isEmpty() || pageText.isBlank() || pageText.length < 20
                    if (!shouldOcr) continue

                    val ocrRects = ocrRectanglesForPage(base, p, query, mb.width, mb.height)
                    if (ocrRects.isNotEmpty()) {
                        pagesFound.add(p)
                        if (firstPage < 0) firstPage = p
                        occurrences += ocrRects.size
                        burnRectanglesIntoPage(doc, page, ocrRects)
                    }
                }

                if (occurrences > 0) doc.save(outFile)
            }

            val ok = occurrences > 0
            val pages = pagesFound.toList().sorted()

            withContext(Dispatchers.Main) {
                if (ok) {
                    if (!searchHighlightActive) cleanPdfFile = base
                    searchHighlightActive = true
                    repo.replacePdfFile(outFile)
                    pageIndex = firstPage.coerceAtLeast(0)
                } else {
                    clearSearchHighlights()
                }
            }

            PdfSearchResult(ok, occurrences, pages, firstPage)
        }
    }

    suspend fun compressPdf(): File {
        val f = pdfFile ?: throw IllegalStateException("Open a PDF first.")
        return withContext(Dispatchers.IO) {
            val out = File(ctx.cacheDir, "compressed_${sanitizePdfName(docDisplayName)}")
            PDDocument.load(f).use { doc ->
                doc.documentCatalog.cosObject.setItem(COSName.NEED_APPEARANCES, null)
                doc.save(out)
            }
            out
        }
    }

    suspend fun applyWatermark(textRaw: String): File {
        val f = pdfFile ?: throw IllegalStateException("Open a PDF first.")
        val wm = textRaw.trim()
        if (wm.isBlank()) throw IllegalStateException("Watermark text is empty.")

        return withContext(Dispatchers.IO) {
            runCatching { PDFBoxResourceLoader.init(ctx) }

            val out = File(ctx.cacheDir, "watermark_${System.currentTimeMillis()}.pdf")
            PDDocument.load(f).use { doc ->
                for (i in 0 until doc.numberOfPages) {
                    addWatermarkToPage(doc, doc.getPage(i), wm)
                }
                doc.save(out)
            }
            withContext(Dispatchers.Main) { repo.replacePdfFile(out) }
            out
        }
    }

    suspend fun securePdf(ownerPass: String): File {
        val f = pdfFile ?: throw IllegalStateException("Open a PDF first.")
        val o = ownerPass.trim()
        if (o.isBlank()) throw IllegalStateException("Password cannot be empty.")

        return withContext(Dispatchers.IO) {
            runCatching { PDFBoxResourceLoader.init(ctx) }

            val out = File(ctx.cacheDir, "secured_${System.currentTimeMillis()}.pdf")
            PDDocument.load(f).use { doc ->
                val ap = AccessPermission()
                runCatching { ap.setCanModify(false) }
                runCatching { ap.setCanModifyAnnotations(false) }
                runCatching { ap.setCanAssembleDocument(false) }
                runCatching { ap.setCanFillInForm(false) }
                runCatching { ap.setCanExtractContent(false) }

                val policy = StandardProtectionPolicy(o, "", ap).apply { encryptionKeyLength = 128 }
                doc.protect(policy)
                doc.save(out)
            }
            withContext(Dispatchers.Main) { repo.replacePdfFile(out) }
            out
        }
    }

    val quickPrompts = remember {
        listOf(
            "Summarize Doc" to "Summarize the document in clear bullet points.",
            "Key Points" to "Give key points, decisions, and action items.",
            "Explain Simply" to "Explain this in simple terms with one short example.",
            "Outline Headings" to "Create an outline of headings and subheadings.",
            "Extract Facts" to "Extract important dates, names, numbers, and entities.",
            "Next Steps" to "Suggest next steps and what I should do based on this document."
        )
    }

    val vignette = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.35f),
            Color.Black.copy(alpha = 0.55f)
        )
    )

    val verticalScrim = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.55f),
            Color.Black.copy(alpha = 0.18f),
            Color.Black.copy(alpha = 0.48f)
        )
    )

    // Scroll state + visible page (PDF only)
    val pdfListState = remember(docSessionId) { LazyListState() }
    val firstVisible by remember { derivedStateOf { pdfListState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible, pageCount) {
        if (pageCount > 0) pageIndex = firstVisible.coerceIn(0, pageCount - 1)
    }

    // Page pill only while scrolling
    val isScrolling by remember { derivedStateOf { pdfListState.isScrollInProgress } }
    var showPagePill by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling) {
        if (isScrolling) showPagePill = true
        else {
            delay(650)
            showPagePill = false
        }
    }

    val hasAnyDoc = docKind != DocKind.NONE
    val hasPdfDoc = (docKind == DocKind.PDF && pdfFile != null)

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = com.pdfliteai.R.drawable.aurora_header),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(Modifier.fillMaxSize().background(verticalScrim))
        Box(Modifier.fillMaxSize().background(vignette))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = reader.bgDim)))

        Scaffold(
            topBar = {
                PdfTopBar(
                    hasDoc = hasAnyDoc,
                    onOpen = { picker.launch(arrayOf("application/pdf", "text/*")) },
                    onTools = {
                        if (hasPdfDoc) toolsOpen = true
                        else error = "Tools are available for PDFs only."
                    },
                    onSettings = onOpenSettings
                )
            },
            floatingActionButton = { BotFab(onClick = { botOpen = true }) },
            containerColor = Color.Transparent
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
            ) {
                when (docKind) {
                    DocKind.PDF -> {
                        if (pdfFile != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                key(pdfFile!!.absolutePath) {
                                    PdfScrollViewInternal(
                                        pdfFile = pdfFile!!,
                                        listState = pdfListState,
                                        modifier = Modifier.fillMaxSize(),
                                        onPageCount = { pageCount = it },
                                        zoomScale = zoomScale,
                                        onZoomScaleChange = { z -> zoomScale = z.coerceIn(1f, 4f) }
                                    )
                                }

                                PageIndicatorPill(
                                    visible = showPagePill,
                                    pageIndex0 = pageIndex,
                                    pageCount = pageCount
                                )
                            }
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    GlowPrimaryButton(
                                        text = "Open File",
                                        onClick = { picker.launch(arrayOf("application/pdf", "text/*")) }
                                    )
                                }
                            }
                        }
                    }

                    DocKind.TEXT -> {
                        val scroll = rememberScrollState()
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .fillMaxSize(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .verticalScroll(scroll)
                                    .padding(14.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = cachedText.ifBlank { "No text found in this file." },
                                        color = Color.White.copy(alpha = 0.92f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    DocKind.NONE -> {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                GlowPrimaryButton(
                                    text = "Open File",
                                    onClick = { picker.launch(arrayOf("application/pdf", "text/*")) }
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(22.dp),
                                    color = Color.Black.copy(alpha = 0.44f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    shadowElevation = 10.dp
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            "Recent",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White
                                        )
                                        Spacer(Modifier.padding(top = 8.dp))

                                        val list = recents.take(reader.recentsLimit)
                                        if (list.isEmpty()) {
                                            Text(
                                                "No recent files yet.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.70f)
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                list.forEach { entry ->
                                                    val (uriStr, savedName) = unpackRecent(entry)
                                                    val name = savedName ?: displayNameForRecent(entry)

                                                    Text(
                                                        text = name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = Color.White,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                val uri = uriStr.toUri()
                                                                tryTakePersistableRead(uri)
                                                                openAny(uri)
                                                            }
                                                            .padding(vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BotSheet(
                open = botOpen,
                onClose = { botOpen = false },

                provider = s.provider,
                onProviderChange = { p -> cs.launch { vm.setProvider(p) } },

                busy = busy,

                quickPrompts = quickPrompts,
                onQuickAsk = { prompt ->
                    cs.launch {
                        busy = true
                        error = null
                        try {
                            runAsk(qOverride = prompt)
                        } catch (t: Throwable) {
                            val msg = t.message ?: t::class.java.simpleName
                            error = msg
                            addToHistory(ChatRole.System, msg)
                        } finally {
                            busy = false
                        }
                    }
                },

                question = question,
                onQuestionChange = { question = it },
                onAsk = {
                    cs.launch {
                        busy = true
                        error = null
                        try {
                            runAsk()
                            question = ""
                        } catch (t: Throwable) {
                            val msg = t.message ?: t::class.java.simpleName
                            error = msg
                            addToHistory(ChatRole.System, msg)
                        } finally {
                            busy = false
                        }
                    }
                },
                onClearInput = { question = "" },

                errorText = error ?: repoErr,
                messages = history.toList(),
                historyChatsLimit = reader.chatHistoryLimit
            )

            ToolsSheetV2(
                open = toolsOpen && hasPdfDoc,
                onClose = { toolsOpen = false },

                pdfFile = pdfFile,
                docDisplayName = docDisplayName,
                docText = cachedText,

                pageIndex = pageIndex,
                pageCount = pageCount,

                onSearchHighlight = { q -> searchHighlightAndGo(q) },
                searchHasHighlights = searchHighlightActive,
                onClearSearchHighlights = { clearSearchHighlights() },

                isReadingAloud = ttsSpeaking,
                onReadAloudStart = { startReadAloud() },
                onReadAloudStop = { stopReadAloud() },

                onDeletePageNumber = { pageNum1Based -> deletePageAt(pageNum1Based - 1) },
                onCompress = { compressPdf() },
                onApplyWatermark = { wm -> applyWatermark(wm) },

                onSecurePdfOwnerOnly = { owner -> securePdf(owner) },

                onGoToPage1Based = { page1 ->
                    val max = (pageCount - 1).coerceAtLeast(0)
                    if (pageCount <= 0) return@ToolsSheetV2
                    val idx0 = (page1 - 1).coerceIn(0, max)
                    cs.launch { runCatching { pdfListState.animateScrollToItem(idx0) } }
                },

                onRotateCurrent = { rotatePage() }
            )
        }
    }
}

/* =========================================================
   ✅ Scrollable PDF implementation
   ========================================================= */

private class RendererHolder(
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer
) {
    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
private fun PdfScrollViewInternal(
    pdfFile: File,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onPageCount: (Int) -> Unit,
    zoomScale: Float,
    onZoomScaleChange: (Float) -> Unit
) {
    var holder by remember(pdfFile) { mutableStateOf<RendererHolder?>(null) }
    var count by remember(pdfFile) { mutableIntStateOf(0) }

    DisposableEffect(pdfFile) {
        val h = runCatching {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            RendererHolder(pfd, r)
        }.getOrNull()

        holder = h
        count = h?.renderer?.pageCount ?: 0
        onPageCount(count)

        onDispose {
            runCatching { h?.close() }
            if (holder == h) holder = null
        }
    }

    val zoomNow by rememberUpdatedState(zoomScale)

    val gestureMod =
        Modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val next = (zoomNow * zoom).coerceIn(1f, 4f)
                    onZoomScaleChange(next)
                }
            }
            .pointerInput(zoomScale) {
                detectTapGestures(
                    onDoubleTap = {
                        val next = if (zoomScale < 1.5f) 2f else 1f
                        onZoomScaleChange(next)
                    }
                )
            }

    LazyColumn(
        state = listState,
        modifier = modifier.then(gestureMod),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 10.dp)
    ) {
        items(
            count = count,
            key = { idx -> idx }
        ) { idx ->
            PdfPageItemInternal(holder = holder, pageIndex = idx, zoomScale = zoomScale)
        }
    }
}

@Composable
private fun PdfPageItemInternal(
    holder: RendererHolder?,
    pageIndex: Int,
    zoomScale: Float
) {
    val hScroll = rememberScrollState()

    LaunchedEffect(zoomScale) {
        if (zoomScale <= 1.01f && hScroll.value != 0) {
            runCatching { hScroll.animateScrollTo(0) }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        shadowElevation = 8.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            val density = LocalDensity.current
            val targetWpx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }

            val bmpState = produceState<Bitmap?>(initialValue = null, holder, pageIndex, targetWpx) {
                value = withContext(Dispatchers.IO) {
                    if (holder == null) return@withContext null
                    runCatching {
                        val r = holder.renderer
                        if (pageIndex !in 0 until r.pageCount) return@runCatching null

                        r.openPage(pageIndex).use { page ->
                            val baseW = page.width.coerceAtLeast(1)
                            val baseH = page.height.coerceAtLeast(1)

                            var scale = (targetWpx.toFloat() / baseW.toFloat()) * 1.35f
                            scale = scale.coerceIn(1f, 2f)

                            var w = (baseW * scale).toInt().coerceAtLeast(1)
                            var h = (baseH * scale).toInt().coerceAtLeast(1)

                            val maxPixels = 6_000_000L
                            val px = w.toLong() * h.toLong()
                            if (px > maxPixels) {
                                val factor = sqrt(maxPixels.toDouble() / px.toDouble()).toFloat()
                                w = (w * factor).toInt().coerceAtLeast(1)
                                h = (h * factor).toInt().coerceAtLeast(1)
                            }

                            val bmp = createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }.getOrNull()
                }
            }

            val bmp = bmpState.value

            if (bmp != null) {
                val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                val viewportW = maxWidth
                val contentW = viewportW * zoomScale

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScroll, enabled = zoomScale > 1.01f)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier
                            .width(contentW)
                            .aspectRatio(ratio),
                        contentScale = ContentScale.FillWidth
                    )
                }
            } else {
                Box(Modifier.padding(16.dp)) {
                    Text(
                        "Rendering page ${pageIndex + 1}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.70f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoxScope.PageIndicatorPill(
    visible: Boolean,
    pageIndex0: Int,
    pageCount: Int
) {
    if (!visible || pageCount <= 0) return

    Surface(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 8.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 6.dp
    ) {
        Text(
            text = "${pageIndex0 + 1}/$pageCount",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun chunkText(s: String, max: Int = TTS_CHUNK_MAX): List<String> {
    if (s.length <= max) return listOf(s)
    val out = ArrayList<String>()
    var i = 0
    while (i < s.length) {
        val end = (i + max).coerceAtMost(s.length)
        out.add(s.substring(i, end))
        i = end
    }
    return out
}

private fun sanitizePdfName(name: String): String {
    val base = name.trim().ifBlank { "document.pdf" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
    val withExt = if (base.lowercase().endsWith(".pdf")) base else "$base.pdf"
    return if (withExt.length <= 80) withExt else withExt.take(80)
}

private fun buildAskPromptWithinLimit(
    provider: ProviderId,
    docText: String,
    question: String
): String {
    val q = question.trim()
    val doc = docText.trim()
    val head =
        "You are a helpful assistant (provider: ${provider.name}). " +
                "Answer using the provided text only.\n\nDOCUMENT TEXT:\n"
    val mid = "\n\nUSER QUESTION:\n"
    return head + doc + mid + q
}

/**
 * Fuzzy regex: matches across whitespace/hyphenation/soft-hyphen/NBSP/zero-width space and common dash chars.
 */
private fun findAllOccurrenceRanges(haystack: String, needle: String): List<IntRange> {
    val q = needle.trim()
    if (q.isBlank()) return emptyList()

    val sep = """[\s\u00A0\u00AD\u200B\-\u2010\u2011\u2012\u2013\u2014]*"""
    val pattern = buildString {
        for (ch in q) {
            if (ch.isWhitespace()) append(sep)
            else {
                append(Regex.escape(ch.toString()))
                append(sep)
            }
        }
    }

    val rx = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    return rx.findAll(haystack).map { it.range }.toList()
}

private fun extractPageTextWithPositions(doc: PDDocument, pageNumber1Based: Int): Pair<String, List<TextPosition>> {
    val sb = StringBuilder()
    val pos = ArrayList<TextPosition>(4096)

    fun expandLigatures(s: String): String {
        return s
            .replace("\uFB00", "ff")
            .replace("\uFB01", "fi")
            .replace("\uFB02", "fl")
            .replace("\uFB03", "ffi")
            .replace("\uFB04", "ffl")
    }

    val stripper = object : PDFTextStripper() {
        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val list = textPositions ?: return
            val t = text ?: ""

            val useTextArg = t.isNotEmpty() && t.length == list.size

            for (i in list.indices) {
                val tp = list[i]
                val raw = if (useTextArg) t[i].toString()
                else runCatching { tp.unicode }.getOrDefault("") ?: ""

                val u = expandLigatures(raw)

                if (u.isEmpty()) {
                    sb.append(' ')
                    pos.add(tp)
                } else {
                    for (ch in u) {
                        sb.append(ch)
                        pos.add(tp)
                    }
                }
            }
        }
    }

    stripper.sortByPosition = true
    stripper.startPage = pageNumber1Based
    stripper.endPage = pageNumber1Based
    stripper.getText(doc)
    return sb.toString() to pos
}

private fun PDPageContentStream.setNonStrokingRgb255(r: Int, g: Int, b: Int) {
    setNonStrokingColor(r / 255f, g / 255f, b / 255f)
}

private fun burnRectanglesIntoPage(doc: PDDocument, page: PDPage, rectangles: List<PDRectangle>) {
    if (rectangles.isEmpty()) return

    val gs = PDExtendedGraphicsState().apply {
        nonStrokingAlphaConstant = 0.25f
        strokingAlphaConstant = 0.25f
    }

    PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { cs ->
        cs.setGraphicsStateParameters(gs)
        cs.setNonStrokingRgb255(255, 255, 0)
        for (r in rectangles) {
            cs.addRect(r.lowerLeftX, r.lowerLeftY, r.width, r.height)
            cs.fill()
        }
    }
}

private fun burnHighlightsIntoPage(
    doc: PDDocument,
    page: PDPage,
    pageHeight: Float,
    positions: List<TextPosition>,
    ranges: List<IntRange>
) {
    if (ranges.isEmpty()) return

    val rectangles = ArrayList<PDRectangle>(ranges.size * 2)
    for (r in ranges) {
        val start = r.first.coerceAtLeast(0)
        val endExclusive = (r.last + 1).coerceAtMost(positions.size)
        if (start >= endExclusive || start >= positions.size) continue

        val slice = positions.subList(start, endExclusive)
        rectangles.addAll(buildLineRectangles(slice, pageHeight))
    }

    burnRectanglesIntoPage(doc, page, rectangles)
}

private fun buildLineRectangles(slice: List<TextPosition>, pageHeight: Float): List<PDRectangle> {
    if (slice.isEmpty()) return emptyList()

    val groups = slice.groupBy { (it.yDirAdj / 3f).toInt() }.toSortedMap(compareByDescending { it })
    val out = mutableListOf<PDRectangle>()

    for ((_, g) in groups) {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var topY = -Float.MAX_VALUE
        var bottomY = Float.MAX_VALUE

        for (tp in g) {
            val x = tp.xDirAdj
            val yTop = pageHeight - tp.yDirAdj
            val h = tp.heightDir
            val yBottom = yTop - h
            val w = tp.widthDirAdj

            minX = minOf(minX, x)
            maxX = maxOf(maxX, x + w)
            topY = maxOf(topY, yTop)
            bottomY = minOf(bottomY, yBottom)
        }

        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (topY - bottomY).coerceAtLeast(1f)

        if (minX.isFinite() && bottomY.isFinite() && w.isFinite() && h.isFinite()) {
            out.add(PDRectangle(minX, bottomY, w, h))
        }
    }

    return out
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

private fun addWatermarkToPage(doc: PDDocument, page: PDPage, text: String) {
    val media = page.mediaBox ?: PDRectangle(0f, 0f, 600f, 800f)
    val centerX = media.lowerLeftX + media.width / 2f
    val centerY = media.lowerLeftY + media.height / 2f

    val font = PDType1Font.HELVETICA_BOLD
    val fontSize = 56f
    val wmText = text.take(80)

    val textWidth = (font.getStringWidth(wmText) / 1000f) * fontSize

    val gs = PDExtendedGraphicsState().apply {
        nonStrokingAlphaConstant = 0.30f
        strokingAlphaConstant = 0.30f
    }

    PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { cs ->
        cs.setGraphicsStateParameters(gs)
        cs.beginText()
        cs.setFont(font, fontSize)
        cs.setNonStrokingRgb255(110, 110, 110)

        cs.setTextMatrix(getRotateInstance(Math.toRadians(35.0), centerX, centerY))
        cs.newLineAtOffset(-textWidth / 2f, -fontSize / 2f)

        cs.showText(wmText)
        cs.endText()
    }
}