package com.pdfliteai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfliteai.ai.AiOrchestrator
import com.pdfliteai.data.ProviderId
import com.pdfliteai.pdf.PdfRepository
import com.pdfliteai.settings.SettingsViewModel
import com.pdfliteai.ui.components.BotFab
import com.pdfliteai.ui.components.BotSheet
import com.pdfliteai.ui.components.PdfBitmapView
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    repo: PdfRepository,
    ai: AiOrchestrator,
    onOpenSettings: () -> Unit
) {
    val vm: SettingsViewModel = viewModel()
    val cs = rememberCoroutineScope()

    val pdfFile by repo.pdfFile.collectAsState()
    val cachedText by repo.cachedText.collectAsState()
    val extracting by repo.extracting.collectAsState()
    val repoErr by repo.lastError.collectAsState()

    val s by vm.aiSettings.collectAsState()

    var botOpen by remember { mutableStateOf(false) }

    // false = entire doc, true = selected text
    var selectedMode by remember { mutableStateOf(false) }

    var question by remember { mutableStateOf("What's in this doc about?") }
    var selectedText by remember { mutableStateOf("") }

    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var pageCount by remember { mutableStateOf(0) }
    var pageIndex by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            repo.openPdf(uri)
            botOpen = true
            selectedMode = false
        }
    }

    LaunchedEffect(pdfFile) {
        val f = pdfFile ?: return@LaunchedEffect
        repo.warmExtract(f)
        error = null
        answer = ""
        pageIndex = 0
        selectedMode = false
    }

    fun modelPresetsFor(p: ProviderId): List<String> = when (p) {
        ProviderId.GROQ -> listOf("llama-3.1-8b-instant", "llama3-8b-8192", "llama3-70b-8192")
        ProviderId.OPENROUTER -> listOf("openai/gpt-4o-mini", "openai/gpt-4o")
        ProviderId.NOVA -> listOf("nova-lite-v1", "nova-pro-v1")
        ProviderId.LOCAL_OPENAI_COMPAT -> listOf("gpt-4o-mini")
    }

    suspend fun runAsk() {
        val f = pdfFile

        val contextBlock = if (selectedMode) {
            val sel = selectedText.trim()
            if (sel.isBlank()) throw IllegalStateException("Selected text is empty. Paste text or switch to Entire doc.")
            "SELECTED TEXT:\n$sel"
        } else {
            if (f == null) throw IllegalStateException("Open a PDF first.")
            val full = repo.getOrExtractText(f).trim()
            if (full.isBlank()) throw IllegalStateException("Could not extract text from PDF.")
            "DOCUMENT TEXT:\n$full"
        }

        val prompt = buildString {
            append("You are a helpful assistant. Answer using the provided text only.\n\n")
            append(contextBlock)
            append("\n\nUSER QUESTION:\n")
            append(question.trim())
        }

        // For fixed-key providers: vm.getApiKey reads BuildConfig. For local: optional.
        val key = vm.getApiKey(s.provider).trim()
        if (key.isBlank() && s.provider != ProviderId.LOCAL_OPENAI_COMPAT) {
            throw IllegalStateException("Missing API key for ${s.provider}. Check local.properties / BuildConfig.")
        }

        answer = ai.chat(s, key, prompt)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDFLite AI") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                    TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Open") }
                }
            )
        },
        floatingActionButton = { BotFab(onClick = { botOpen = true }) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            if (pdfFile != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        enabled = pageIndex > 0
                    ) { Text("Prev") }

                    Text("Page ${pageIndex + 1} / ${maxOf(pageCount, 1)}")

                    OutlinedButton(
                        onClick = { pageIndex = (pageIndex + 1).coerceAtMost(maxOf(pageCount - 1, 0)) },
                        enabled = pageCount > 0 && pageIndex < pageCount - 1
                    ) { Text("Next") }
                }

                PdfBitmapView(
                    pdfFile = pdfFile as File,
                    pageIndex = pageIndex,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onPageCount = { pageCount = it },

                    // ✅ REQUIRED by your updated PdfBitmapView signature
                    // Keep it lightweight; don't do heavy extraction here.
                    pageTextProvider = { _ ->
                        cachedText
                    },

                    // ✅ REQUIRED by your updated PdfBitmapView signature
                    onSelectedText = { txt ->
                        val t = txt.trim()
                        if (t.isNotBlank()) {
                            selectedText = t
                            selectedMode = true
                            botOpen = true
                        }
                    }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Open a PDF to begin.")
                }
            }
        }

        BotSheet(
            open = botOpen,
            onClose = { botOpen = false },

            provider = s.provider,
            onProviderChange = { p ->
                cs.launch {
                    vm.setProvider(p)
                    val defaults = modelPresetsFor(p)
                    if (defaults.isNotEmpty()) vm.setModel(defaults.first())
                }
            },

            model = s.model,
            modelPresets = modelPresetsFor(s.provider),
            onModelChange = { v -> cs.launch { vm.setModel(v) } },
            onModelPick = { v -> cs.launch { vm.setModel(v) } },

            selectedMode = selectedMode,
            onSelectedMode = { selectedMode = true },
            onEntireDocMode = { selectedMode = false },
            onQuickPrompt = { promptText -> question = promptText },

            question = question,
            onQuestionChange = { question = it },
            busy = busy,
            onAsk = {
                cs.launch {
                    busy = true
                    error = null
                    try {
                        runAsk()
                    } catch (t: Throwable) {
                        error = t.message ?: t::class.java.simpleName
                        answer = ""
                    } finally {
                        busy = false
                    }
                }
            },
            onClear = {
                question = ""
                answer = ""
                error = null
            },

            showEdit = true,
            onRotateP1 = { },
            onDeleteP1 = { },
            onShare = { },

            selectedText = selectedText,
            onSelectedTextChange = { selectedText = it },

            errorText = error ?: repoErr,
            answerText = buildString {
                if (!selectedMode) {
                    if (extracting) append("Extracting document text in background…\n\n")
                    if (cachedText.isNotBlank()) append("Doc cached ✅\n\n")
                }
                append(answer)
            }
        )
    }
}
