package com.pdfliteai.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun PdfBitmapView(
    pdfFile: File,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    onPageCount: (Int) -> Unit = {},

    // ✅ New: supply page text (from PdfRepository/PdfTextExtractor)
    pageTextProvider: suspend (pageIndex: Int) -> String,

    // ✅ New: callback to map selected text into UI
    onSelectedText: (String) -> Unit
) {
    var bmp by remember(pdfFile, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(pdfFile, pageIndex) { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(pdfFile, pageIndex) {
        loading = true
        bmp = null

        val rendered = withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd).use { renderer ->
                    val pc = renderer.pageCount
                    onPageCount(pc)

                    if (pc <= 0) return@withContext null

                    val safeIndex = min(max(pageIndex, 0), pc - 1)

                    renderer.openPage(safeIndex).use { page ->
                        val scale = 2
                        val w = max(1, page.width * scale)
                        val h = max(1, page.height * scale)

                        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        out.eraseColor(android.graphics.Color.WHITE)
                        page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out
                    }
                }
            } catch (_: Throwable) {
                null
            } finally {
                try { pfd?.close() } catch (_: Throwable) {}
            }
        }

        bmp = rendered
        loading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pdfFile, pageIndex) {
                detectTapGestures(
                    onLongPress = {
                        // ✅ Best possible "selection" with bitmap-based renderer:
                        // Long-press pulls current page text and maps it to selected text box.
                        scope.launch {
                            val txt = runCatching { pageTextProvider(pageIndex) }.getOrDefault("")
                            val cleaned = txt.trim()
                            if (cleaned.isNotBlank()) {
                                onSelectedText(cleaned)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (loading) {
            CircularProgressIndicator()
        }
    }
}
