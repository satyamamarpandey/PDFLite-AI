package com.pdfliteai.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pdfliteai.ai.AiOrchestrator
import com.pdfliteai.ai.ModelProvider
import com.pdfliteai.ai.OllamaProvider
import com.pdfliteai.ai.OpenAICompatProvider
import com.pdfliteai.data.PrefKeys
import com.pdfliteai.data.Prefs
import com.pdfliteai.data.ProviderId
import com.pdfliteai.data.Scope
import com.pdfliteai.pdf.PageText
import com.pdfliteai.pdf.PdfEditor
import com.pdfliteai.pdf.PdfRepository
import com.pdfliteai.pdf.PdfTextExtractor
import com.pdfliteai.ui.components.BotFab
import com.pdfliteai.ui.components.BotSheet
import com.pdfliteai.ui.components.PdfBitmapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val cs = rememberCoroutineScope()

    val repo = remember { PdfRepository(ctx) }
    val extractor = remember { PdfTextExtractor() }
    val editor = remember { PdfEditor() }
    val prefs = remember { Prefs(ctx) }

    val providerId by prefs.providerFlow.collectAsState(initial = ProviderId.OpenAI)

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableStateOf<List<PageText>>(emptyList()) }

    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }

    var selectedText by remember { mutableStateOf("") } // user paste
    var scopeMode by remember { mutableStateOf(Scope.SelectedText) }

    var botOpen by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf("") }
    var aiError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            cs.launch {
                busy = true
                aiError = null
                aiAnswer = ""
                val f = withContext(Dispatchers.IO) { repo.importPdf(uri) }
                pdfFile = f
                pages = emptyList() // ✅ don’t extract on open (prevents freezing)
                selectedText = ""
                currentPage = 0
                busy = false
            }
        }
    }

    fun shareCurrentPdf() {
        val f = pdfFile ?: return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    suspend fun buildProvider(): ModelProvider {
        val openaiUrl = prefs.stringFlow(PrefKeys.openaiBaseUrl, "https://api.openai.com").first()
        val openaiKey = prefs.stringFlow(PrefKeys.openaiKey, "").first()

        val openrouterUrl = prefs.stringFlow(PrefKeys.openrouterBaseUrl, "https://openrouter.ai/api").first()
        val openrouterKey = prefs.stringFlow(PrefKeys.openrouterKey, "").first()

        val groqUrl = prefs.stringFlow(PrefKeys.groqBaseUrl, "https://api.groq.com/openai").first()
        val groqKey = prefs.stringFlow(PrefKeys.groqKey, "").first()

        val ollamaUrl = prefs.stringFlow(PrefKeys.ollamaBaseUrl, "http://192.168.1.20:11434").first()
        val model = prefs.modelFlow.first()

        return when (providerId) {
            ProviderId.OpenRouter -> OpenAICompatProvider(
                baseUrl = openrouterUrl,
                apiKey = openrouterKey,
                model = model,
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://yourapp.example",
                    "X-Title" to "PDFLite AI"
                )
            )
            ProviderId.Groq -> OpenAICompatProvider(
                baseUrl = groqUrl,
                apiKey = groqKey,
                model = model
            )
            ProviderId.OllamaLan -> OllamaProvider(
                baseUrl = ollamaUrl,
                model = if (model.isBlank()) "llama3.1" else model
            )
            else -> OpenAICompatProvider(
                baseUrl = openaiUrl,
                apiKey = openaiKey,
                model = model
            )
        }
    }

    fun ensureTextExtractedThen(run: suspend () -> Unit) {
        val f = pdfFile ?: run {
            aiError = "Open a PDF first."
            return
        }
        cs.launch {
            busy = true
            aiError = null
            try {
                if (pages.isEmpty()) {
                    pages = withContext(Dispatchers.IO) { extractor.extractAllPages(f) }
                }
                run()
            } catch (e: Exception) {
                aiError = e.message
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDFLite AI") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                    TextButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text("Open") }
                }
            )
        }
    ) { padding ->

        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ✅ PDF background
            val f = pdfFile
            if (f == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Open a PDF to begin.")
                }
            } else {
                Column(Modifier.fillMaxSize()) {

                    // Page controls
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            enabled = currentPage > 0,
                            onClick = { currentPage -= 1 }
                        ) { Text("Prev") }

                        Text("Page ${currentPage + 1} / ${if (pageCount == 0) "?" else pageCount}")

                        OutlinedButton(
                            enabled = pageCount > 0 && currentPage < pageCount - 1,
                            onClick = { currentPage += 1 }
                        ) { Text("Next") }
                    }

                    // Single-page view (light)
                    PdfBitmapView(
                        pdfFile = f,
                        pageIndex = currentPage,
                        modifier = Modifier.fillMaxSize(),
                        onPageCount = { pageCount = it }
                    )
                }
            }

            // ✅ Floating robot bottom-right
            BotFab(
                onClick = { botOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
            )

            // ✅ Bot sheet overlay (PDF stays behind)
            BotSheet(
                isOpen = botOpen,
                onDismiss = { botOpen = false },
                scope = scopeMode,
                onScopeChange = { scopeMode = it },
                question = question,
                onQuestionChange = { question = it },
                onQuickAction = { q ->
                    question = q
                    // auto-open ask style? keep manual for safety
                },
                onAsk = {
                    val q = question.trim().ifEmpty { "Summarize this." }

                    if (scopeMode == Scope.SelectedText) {
                        // Selected text mode: uses only pasted selection
                        cs.launch {
                            busy = true
                            aiError = null
                            aiAnswer = "Thinking…"
                            try {
                                val sel = selectedText.trim()
                                if (sel.isEmpty()) error("Paste selected text first.")

                                val provider = buildProvider()
                                val orch = AiOrchestrator(provider)

                                val ans = withContext(Dispatchers.IO) {
                                    orch.ask(
                                        question = q,
                                        scope = Scope.SelectedText.name,
                                        selectedText = sel,
                                        pages = emptyList()
                                    )
                                }
                                aiAnswer = ans
                            } catch (e: Exception) {
                                aiAnswer = ""
                                aiError = e.message
                            } finally {
                                busy = false
                            }
                        }
                    } else {
                        // Entire doc: extract if needed, then run
                        ensureTextExtractedThen {
                            val provider = buildProvider()
                            val orch = AiOrchestrator(provider)
                            aiAnswer = "Thinking…"

                            val ans = withContext(Dispatchers.IO) {
                                orch.ask(
                                    question = q,
                                    scope = Scope.EntireDocument.name,
                                    selectedText = null,
                                    pages = pages
                                )
                            }
                            aiAnswer = ans
                        }
                    }
                },
                busy = busy,
                answer = aiAnswer,
                error = aiError
            )
        }
    }

    // Keep “lite tools” accessible via long-press? For now: you can add them into BotSheet later.
}
