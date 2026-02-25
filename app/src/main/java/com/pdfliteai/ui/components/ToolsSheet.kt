// ToolsSheet.kt
package com.pdfliteai.ui.components

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class PdfSearchResult(
    val found: Boolean,
    val occurrences: Int,
    val pages0Based: List<Int>,
    val firstPage0Based: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheetV2(
    open: Boolean,
    onClose: () -> Unit,

    pdfFile: File?,
    docDisplayName: String,
    docText: String,

    pageIndex: Int,
    pageCount: Int,

    // Search -> highlight -> go
    onSearchHighlight: suspend (String) -> PdfSearchResult,
    searchHasHighlights: Boolean,
    onClearSearchHighlights: () -> Unit,

    // Read aloud
    isReadingAloud: Boolean,
    onReadAloudStart: () -> Unit,
    onReadAloudStop: () -> Unit,
    onReadAloudRestart: () -> Unit,

    onDeletePageNumber: (Int) -> Unit,

    // ✅ NEW: Merge PDFs
    onMergePdfs: () -> Unit,

    onCompress: suspend () -> File,
    onApplyWatermark: suspend (String) -> File,

    // ✅ owner password only
    onSecurePdfOwnerOnly: suspend (String) -> File,

    // ✅ go to page number (1-based)
    onGoToPage1Based: (Int) -> Unit,

    onRotateCurrent: () -> Unit
) {
    if (!open) return

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()

    val hasDoc = pdfFile != null && pdfFile.exists()
    val safeName = remember(docDisplayName) { sanitizePdfName(docDisplayName) }

    var busy by remember { mutableStateOf(false) }

    // Search
    var searchQ by remember { mutableStateOf("") }
    var searchStatus by remember { mutableStateOf<String?>(null) }

    // Delete
    var deletePageInput by remember { mutableStateOf("") }

    // Save copy
    var saveName by remember { mutableStateOf(safeName) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { outUri: Uri? ->
        val f = pendingSaveFile
        pendingSaveFile = null
        if (outUri == null || f == null) return@rememberLauncherForActivityResult

        scope.launch {
            runCatching {
                ctx.contentResolver.openOutputStream(outUri)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                } ?: error("Unable to write file.")
            }.onSuccess {
                snack.showSnackbar("Saved")
            }.onFailure {
                snack.showSnackbar(it.message ?: "Save failed")
            }
        }
    }

    // Watermark
    var watermarkText by remember { mutableStateOf("") }

    // Secure (single owner password)
    var ownerPass by remember { mutableStateOf("") }

    // Go to page
    var goToPageInput by remember { mutableStateOf("") }

    fun sharePdfWithName(file: File, name: String) {
        runCatching {
            val shareDir = File(ctx.filesDir, "share").apply { mkdirs() }
            val shareFile = File(shareDir, sanitizePdfName(name))
            file.copyTo(shareFile, overwrite = true)

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shareFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, sanitizePdfName(name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(ctx.contentResolver, sanitizePdfName(name), uri)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share PDF"))
        }.onFailure {
            scope.launch { snack.showSnackbar(it.message ?: "Share failed") }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color(0xFF0B0B10),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                )
            }
        }
    ) {
        Box(
            Modifier
                .fillMaxHeight(0.90f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tools", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Column(
                        Modifier
                            .padding(14.dp)
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1) Search
                        SectionTitle("Search for Text")
                        OutlinedTextField(
                            value = searchQ,
                            onValueChange = { searchQ = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasDoc && !busy,
                            singleLine = true,
                            placeholder = { Text("Enter text to search", color = Color.White.copy(alpha = 0.45f)) }
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!hasDoc) return@Button
                                    val q = searchQ.trim()
                                    if (q.isBlank()) return@Button

                                    scope.launch {
                                        busy = true
                                        searchStatus = null

                                        // Remove previous highlights before applying new ones
                                        if (searchHasHighlights) onClearSearchHighlights()

                                        val outcome = runCatching { onSearchHighlight(q) }.getOrElse {
                                            busy = false
                                            snack.showSnackbar(it.message ?: "Search failed")
                                            return@launch
                                        }

                                        busy = false

                                        if (!outcome.found) {
                                            searchStatus = "Not found in the doc"
                                            // auto-clear after 3s
                                            launch {
                                                delay(3000)
                                                searchStatus = null
                                            }
                                        } else {
                                            val pagesHuman = outcome.pages0Based.joinToString(", ") { (it + 1).toString() }
                                            searchStatus = "Found ${outcome.occurrences} matches on pages: $pagesHuman"
                                            if (outcome.firstPage0Based >= 0) onGoToPage1Based(outcome.firstPage0Based + 1)
                                        }
                                    }
                                },
                                enabled = hasDoc && !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text(if (busy) "Searching..." else "Search") }

                            Button(
                                onClick = {
                                    searchQ = ""
                                    searchStatus = null
                                    onClearSearchHighlights()
                                },
                                enabled = hasDoc && !busy && (searchHasHighlights || searchQ.isNotBlank() || !searchStatus.isNullOrBlank()),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Clear") }
                        }

                        if (!searchStatus.isNullOrBlank()) {
                            Text(
                                searchStatus!!,
                                color = Color.White.copy(alpha = 0.70f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // 2) Read Loud
                        SectionTitle("Read PDF Loud")

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onReadAloudStart,
                                enabled = hasDoc && docText.isNotBlank() && !busy && !isReadingAloud,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Read Aloud") }

                            Button(
                                onClick = onReadAloudStop,
                                enabled = hasDoc && !busy && isReadingAloud,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Stop") }

                            // ✅ Reload button (restart from beginning)
                            Button(
                                onClick = onReadAloudRestart,
                                enabled = hasDoc && docText.isNotBlank() && !busy,
                                modifier = Modifier.width(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Reload")
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // 3) Share
                        SectionTitle("Share")
                        Button(
                            onClick = { if (hasDoc) sharePdfWithName(pdfFile!!, safeName) },
                            enabled = hasDoc && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Share PDF") }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // 4) Delete
                        SectionTitle("Delete Page")
                        OutlinedTextField(
                            value = deletePageInput,
                            onValueChange = { deletePageInput = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasDoc && !busy,
                            singleLine = true,
                            placeholder = { Text("Page number (e.g., 2)", color = Color.White.copy(alpha = 0.45f)) }
                        )
                        Button(
                            onClick = {
                                if (!hasDoc) return@Button
                                val n = deletePageInput.toIntOrNull()
                                if (n == null || n <= 0) {
                                    scope.launch { snack.showSnackbar("Enter a valid page number") }
                                    return@Button
                                }
                                onDeletePageNumber(n)
                                scope.launch { snack.showSnackbar("Deleted page $n") }
                            },
                            enabled = hasDoc && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A1414).copy(alpha = 0.9f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Delete Page") }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // ✅ NEW: Merge PDFs (added after delete page)
                        SectionTitle("Merge PDFs")
                        Button(
                            onClick = onMergePdfs,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Merge PDFs") }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // 5) Save a Copy
                        SectionTitle("Save a Copy")
                        OutlinedTextField(
                            value = saveName,
                            onValueChange = { saveName = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasDoc && !busy,
                            singleLine = true,
                            placeholder = { Text("New file name (e.g., invoice.pdf)", color = Color.White.copy(alpha = 0.45f)) }
                        )
                        Button(
                            onClick = {
                                if (!hasDoc) return@Button
                                pendingSaveFile = pdfFile
                                saveLauncher.launch(sanitizePdfName(saveName))
                            },
                            enabled = hasDoc && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Save Copy") }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // 6) Compress
                        SectionTitle("Compress PDF")
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!hasDoc) return@Button
                                    scope.launch {
                                        busy = true
                                        val out = runCatching { onCompress() }.getOrElse {
                                            busy = false
                                            snack.showSnackbar(it.message ?: "Compress failed")
                                            return@launch
                                        }
                                        busy = false
                                        sharePdfWithName(out, "compressed_$safeName")
                                    }
                                },
                                enabled = hasDoc && !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Shrink & Share") }

                            Button(
                                onClick = {
                                    if (!hasDoc) return@Button
                                    scope.launch {
                                        busy = true
                                        val out = runCatching { onCompress() }.getOrElse {
                                            busy = false
                                            snack.showSnackbar(it.message ?: "Compress failed")
                                            return@launch
                                        }
                                        busy = false
                                        pendingSaveFile = out
                                        saveLauncher.launch("compressed_$safeName")
                                    }
                                },
                                enabled = hasDoc && !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Shrink & Save") }
                        }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // 7) Watermark
                        SectionTitle("Add Watermark")
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasDoc && !busy,
                            singleLine = true,
                            placeholder = { Text("Watermark text (e.g., CONFIDENTIAL)", color = Color.White.copy(alpha = 0.45f)) }
                        )
                        Button(
                            onClick = {
                                if (!hasDoc) return@Button
                                val wm = watermarkText.trim()
                                if (wm.isBlank()) {
                                    scope.launch { snack.showSnackbar("Enter watermark text") }
                                    return@Button
                                }
                                scope.launch {
                                    busy = true
                                    runCatching { onApplyWatermark(wm) }
                                        .onSuccess { snack.showSnackbar("Watermark applied") }
                                        .onFailure { snack.showSnackbar(it.message ?: "Watermark failed") }
                                    busy = false
                                }
                            },
                            enabled = hasDoc && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(if (busy) "Applying..." else "Apply Watermark") }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // 8) Secure (owner only)
                        SectionTitle("Secure PDF")
                        OutlinedTextField(
                            value = ownerPass,
                            onValueChange = { ownerPass = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasDoc && !busy,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = { Text("Owner password (edit/remove security)", color = Color.White.copy(alpha = 0.45f)) }
                        )
                        Button(
                            onClick = {
                                if (!hasDoc) return@Button
                                val o = ownerPass.trim()
                                if (o.isBlank()) {
                                    scope.launch { snack.showSnackbar("Enter password") }
                                    return@Button
                                }
                                scope.launch {
                                    busy = true
                                    runCatching { onSecurePdfOwnerOnly(o) }
                                        .onSuccess { snack.showSnackbar("PDF secured") }
                                        .onFailure { snack.showSnackbar(it.message ?: "Secure failed") }
                                    busy = false
                                }
                            },
                            enabled = hasDoc && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(if (busy) "Securing..." else "Apply Security") }

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        // Other
                        SectionTitle("Other")
                        ToolPill(
                            text = "Rotate Current Document",
                            enabled = hasDoc && !busy,
                            onClick = onRotateCurrent,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = goToPageInput,
                            onValueChange = { goToPageInput = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasDoc && !busy,
                            singleLine = true,
                            placeholder = { Text("Go to page (e.g., 1)", color = Color.White.copy(alpha = 0.45f)) }
                        )
                        Button(
                            onClick = {
                                val n = goToPageInput.toIntOrNull()
                                if (n == null || n <= 0) {
                                    scope.launch { snack.showSnackbar("Enter a valid page number") }
                                    return@Button
                                }
                                onGoToPage1Based(n)
                            },
                            enabled = hasDoc && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Go") }

                        Text(
                            "File: $safeName • Pages: ${if (pageCount <= 0) 1 else pageCount} • Current: ${pageIndex + 1}",
                            color = Color.White.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            SnackbarHost(
                hostState = snack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White.copy(alpha = 0.92f)
    )
}

@Composable
private fun ToolPill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = if (enabled) 0.06f else 0.03f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.35f)
            )
        ) { Text(text) }
    }
}

private fun sanitizePdfName(name: String): String {
    val base = (name.trim().ifBlank { "document.pdf" })
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()

    val withExt = if (base.lowercase().endsWith(".pdf")) base else "$base.pdf"
    return if (withExt.length <= 80) withExt else withExt.take(80)
}