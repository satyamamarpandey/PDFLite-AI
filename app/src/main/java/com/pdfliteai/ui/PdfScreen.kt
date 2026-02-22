// PdfScreen.kt
package com.pdfliteai.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pdfliteai.ai.AiOrchestrator
import com.pdfliteai.data.ProviderId
import com.pdfliteai.pdf.PdfEditor
import com.pdfliteai.pdf.PdfRepository
import com.pdfliteai.settings.SettingsViewModel
import com.pdfliteai.ui.components.BotFab
import com.pdfliteai.ui.components.BotSheet
import com.pdfliteai.ui.components.ChatMessage
import com.pdfliteai.ui.components.ChatRole
import com.pdfliteai.ui.components.GlowPrimaryButton
import com.pdfliteai.ui.components.PdfBitmapView
import com.pdfliteai.ui.components.PdfTopBar
import com.pdfliteai.ui.components.PdfSearchResult
import com.pdfliteai.ui.components.ToolsSheetV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
import com.tom_roush.pdfbox.util.Matrix

@Composable
fun PdfScreen(
    repo: PdfRepository,
    ai: AiOrchestrator,
    onOpenSettings: () -> Unit
) {
    val vm: SettingsViewModel = viewModel()
    val cs = rememberCoroutineScope()
    val ctx = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(Unit) {
        runCatching { PDFBoxResourceLoader.init(ctx) }
    }

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

    var pageCount by remember { mutableStateOf(0) }
    var pageIndex by remember { mutableStateOf(0) }

    var docDisplayName by remember { mutableStateOf("document.pdf") }

    val history = remember { mutableStateListOf<ChatMessage>() }
    val editor = remember { PdfEditor() }

    // ✅ Search state
    var docSessionId by remember { mutableStateOf(0) }   // increments only when opening a NEW PDF
    var cleanPdfFile by remember { mutableStateOf<File?>(null) } // base PDF without search highlights
    var searchHighlightActive by remember { mutableStateOf(false) }

    // ✅ OCR (ML Kit)
    val ocrRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // ----- TTS
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsSpeaking by remember { mutableStateOf(false) }
    var ttsJob: Job? by remember { mutableStateOf(null) }

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

    fun displayNameFor(uriStr: String): String {
        return runCatching {
            val uri = Uri.parse(uriStr)
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) {
                        val name = c.getString(idx)?.trim().orEmpty()
                        if (name.isNotBlank()) return@runCatching name
                    }
                }
            uri.lastPathSegment ?: uriStr
        }.getOrDefault(uriStr)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            // ✅ NEW DOC session
            docSessionId += 1
            searchHighlightActive = false
            cleanPdfFile = null

            docDisplayName = displayNameFor(uri.toString())
            repo.openPdf(uri)
            vm.addRecent(uri.toString())
            botOpen = reader.autoOpenAi
        }
    }

    // Runs on any file change (rotate/delete/watermark/search/etc) -> only warm text
    LaunchedEffect(pdfFile) {
        val f = pdfFile ?: return@LaunchedEffect
        repo.warmExtract(f)
        error = null

        // Only update clean base when we're NOT showing search highlights
        if (!searchHighlightActive) {
            cleanPdfFile = f
        }
    }

    // Runs ONLY when a NEW document is opened
    LaunchedEffect(docSessionId) {
        pageIndex = 0
        question = ""
        history.clear()
    }

    fun addToHistory(role: ChatRole, text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        history.add(ChatMessage(role = role, text = t))
    }

    suspend fun runAsk(qOverride: String? = null) {
        val f = pdfFile ?: throw IllegalStateException("Open a PDF first.")
        val full = repo.getOrExtractText(f).trim()
        if (full.isBlank()) throw IllegalStateException("Could not extract text from PDF.")

        val q = (qOverride ?: question).trim()
        if (q.isBlank()) throw IllegalStateException("Type a question or use a quick prompt.")

        val prompt = buildString {
            append("You are a helpful assistant. Answer using the provided text only.\n\n")
            append("DOCUMENT TEXT:\n$full")
            append("\n\nUSER QUESTION:\n$q")
        }

        val key = vm.getApiKey(s.provider).trim()
        if (key.isBlank() && s.provider != ProviderId.LOCAL_OPENAI_COMPAT) {
            throw IllegalStateException("Missing API key for ${s.provider}. Check local.properties / BuildConfig.")
        }

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

    fun toggleReadAloud() {
        val text = cachedText.trim()
        val engine = tts
        if (engine == null || !ttsReady) {
            error = "Text-to-Speech not ready."
            return
        }

        if (ttsSpeaking) {
            ttsJob?.cancel()
            engine.stop()
            ttsSpeaking = false
            return
        }

        if (text.isBlank()) {
            error = "No extracted text to read."
            return
        }

        ttsJob?.cancel()
        engine.stop()
        ttsSpeaking = true

        val chunks = chunkText(text, 3500)
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

    suspend fun ocrRectsForPage(
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

            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val image = InputImage.fromBitmap(bitmap, 0)
            val result = ocrRecognizer.process(image).await()

            val rectsPx = ArrayList<android.graphics.Rect>(64)

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
                                elements[i].boundingBox?.let { rectsPx.add(it) }
                            }
                            off = stop + 1
                        }
                        idx = joinedL.indexOf(q, idx + 1)
                    }
                }
            }

            if (rectsPx.isEmpty()) return@withContext emptyList()

            val out = ArrayList<PDRectangle>(rectsPx.size)
            for (r in rectsPx) {
                val left = (r.left.toFloat() / bmpW) * pageW
                val right = (r.right.toFloat() / bmpW) * pageW

                val topFromTop = (r.top.toFloat() / bmpH) * pageH
                val bottomFromTop = (r.bottom.toFloat() / bmpH) * pageH

                val y = pageH - bottomFromTop
                val w = (right - left).coerceAtLeast(1f)
                val h = (bottomFromTop - topFromTop).coerceAtLeast(1f)

                out.add(PDRectangle(left, y, w, h))
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

    // -----------------------------
    // Search: fuzzy regex + highlight all + go to first match
    // Text-based first; OCR fallback for scanned/image PDFs
    // -----------------------------
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

            val outFile = File(ctx.cacheDir, "pdflite_searchhl_${System.currentTimeMillis()}.pdf")
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
                    } else {
                        emptyList()
                    }

                    if (ranges.isNotEmpty()) {
                        pagesFound.add(p)
                        if (firstPage < 0) firstPage = p
                        occurrences += ranges.size
                        burnHighlightsIntoPage(doc, page, mb.height, pos, ranges)
                        continue
                    }

                    // ✅ OCR only when the page looks image-only (or no text positions)
                    val shouldOcr = pos.isEmpty() || pageText.isBlank() || pageText.length < 20
                    if (!shouldOcr) continue

                    val ocrRects = ocrRectsForPage(base, p, query, mb.width, mb.height)
                    if (ocrRects.isNotEmpty()) {
                        pagesFound.add(p)
                        if (firstPage < 0) firstPage = p
                        occurrences += ocrRects.size
                        burnRectsIntoPage(doc, page, ocrRects)
                    }
                }

                if (occurrences > 0) {
                    doc.save(outFile)
                }
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
                    hasDoc = pdfFile != null,
                    onOpen = { picker.launch(arrayOf("application/pdf")) },
                    onTools = { toolsOpen = true },
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
                if (pdfFile != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = pageIndex > 0,
                            onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.35f)
                            ),
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) { Text("Prev") }

                        Text(
                            "Page ${pageIndex + 1} / ${maxOf(pageCount, 1)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )

                        TextButton(
                            enabled = pageCount > 0 && pageIndex < pageCount - 1,
                            onClick = { pageIndex = (pageIndex + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0)) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.35f)
                            ),
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) { Text("Next") }
                    }

                    PdfBitmapView(
                        pdfFile = pdfFile as File,
                        pageIndex = pageIndex,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onPageCount = { pageCount = it },
                        pageTextProvider = { cachedText },
                        onSelectedText = { _ -> botOpen = true }
                    )
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
                                text = "Open PDF",
                                onClick = { picker.launch(arrayOf("application/pdf")) }
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
                                            list.forEach { u ->
                                                val name = displayNameFor(u)
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            // ✅ NEW DOC session
                                                            docSessionId += 1
                                                            searchHighlightActive = false
                                                            cleanPdfFile = null

                                                            docDisplayName = name
                                                            val uri = Uri.parse(u)
                                                            repo.openPdf(uri)
                                                            vm.addRecent(u)
                                                            botOpen = reader.autoOpenAi
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
                open = toolsOpen,
                onClose = { toolsOpen = false },

                pdfFile = pdfFile,
                docDisplayName = docDisplayName,
                docText = cachedText,

                pageIndex = pageIndex,
                pageCount = pageCount,

                onSearchHighlight = { q -> searchHighlightAndGo(q) },
                searchHasHighlights = searchHighlightActive,
                onClearSearchHighlights = { clearSearchHighlights() },

                onReadAloudToggle = { toggleReadAloud() },

                onDeletePageNumber = { pageNum1Based -> deletePageAt(pageNum1Based - 1) },
                onCompress = { compressPdf() },
                onApplyWatermark = { wm -> applyWatermark(wm) },

                onSecurePdfOwnerOnly = { owner -> securePdf(owner) },

                onGoToPage1Based = { page1 ->
                    val idx0 = (page1 - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    pageIndex = idx0
                },

                onRotateCurrent = { rotatePage() }
            )
        }
    }
}

/* -----------------------------
   Helpers
------------------------------ */

private fun chunkText(s: String, max: Int): List<String> {
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
    val base = (name.trim().ifBlank { "document.pdf" })
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
    val withExt = if (base.lowercase().endsWith(".pdf")) base else "$base.pdf"
    return if (withExt.length <= 80) withExt else withExt.take(80)
}

/**
 * Fuzzy regex: matches across whitespace/hyphenation/soft-hyphen/NBSP/ZWSP and common dash chars.
 * Returns ranges so highlights still work even if separators exist inside the match.
 */
private fun findAllOccurrenceRanges(haystack: String, needle: String): List<IntRange> {
    val q = needle.trim()
    if (q.isBlank()) return emptyList()

    val sep = """[\s\u00A0\u00AD\u200B\-\u2010\u2011\u2012\u2013\u2014]*"""
    val pattern = buildString {
        for (ch in q) {
            if (ch.isWhitespace()) {
                append(sep)
            } else {
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
                val raw = if (useTextArg) {
                    t[i].toString()
                } else {
                    runCatching { tp.unicode }.getOrDefault("") ?: ""
                }

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

private fun burnRectsIntoPage(doc: PDDocument, page: PDPage, rects: List<PDRectangle>) {
    if (rects.isEmpty()) return

    val gs = PDExtendedGraphicsState().apply {
        nonStrokingAlphaConstant = 0.25f
        strokingAlphaConstant = 0.25f
    }

    PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { cs ->
        cs.setGraphicsStateParameters(gs)
        cs.setNonStrokingColor(255, 255, 0)
        for (r in rects) {
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

    val rects = ArrayList<PDRectangle>(ranges.size * 2)
    for (r in ranges) {
        val start = r.first.coerceAtLeast(0)
        val endExclusive = (r.last + 1).coerceAtMost(positions.size)
        if (start >= endExclusive || start >= positions.size) continue

        val slice = positions.subList(start, endExclusive)
        rects.addAll(buildLineRects(slice, pageHeight))
    }

    burnRectsIntoPage(doc, page, rects)
}

private fun buildLineRects(slice: List<TextPosition>, pageHeight: Float): List<PDRectangle> {
    if (slice.isEmpty()) return emptyList()

    // Group by approximate line using yDirAdj (stable for grouping)
    val groups = slice.groupBy { (it.yDirAdj / 3f).toInt() }.toSortedMap(compareByDescending { it })
    val out = mutableListOf<PDRectangle>()

    for ((_, g) in groups) {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var topY = -Float.MAX_VALUE
        var bottomY = Float.MAX_VALUE

        for (tp in g) {
            val x = tp.xDirAdj
            val yTop = pageHeight - tp.yDirAdj   // ✅ convert to PDF user space
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
        cs.setNonStrokingColor(110, 110, 110)

        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35.0), centerX, centerY))
        cs.newLineAtOffset(-textWidth / 2f, -fontSize / 2f)

        cs.showText(wmText)
        cs.endText()
    }
}