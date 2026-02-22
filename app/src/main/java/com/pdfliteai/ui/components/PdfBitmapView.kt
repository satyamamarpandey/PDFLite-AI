package com.pdfliteai.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ✅ New: Scrollable PDF view (no Prev/Next)
 * - Renders each page as a bitmap
 * - Updates "current page" based on first visible page
 * - Calls onPageCount once the renderer is ready
 *
 * NOTE: This does NOT remove your existing PdfBitmapView() (keep it if you already have it).
 */
@Composable
fun PdfScrollView(
    pdfFile: File,
    modifier: Modifier = Modifier,
    onPageCount: (Int) -> Unit = {},
    onVisiblePageChanged: (Int) -> Unit = {},
    pageTextProvider: () -> String = { "" },
    onSelectedText: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()

    var rendererHolder by remember(pdfFile) { mutableStateOf<RendererHolder?>(null) }
    var pageCount by remember(pdfFile) { mutableIntStateOf(0) }

    // Open renderer once per file
    DisposableEffect(pdfFile) {
        val holder = openRenderer(pdfFile)
        rendererHolder = holder
        pageCount = holder?.renderer?.pageCount ?: 0
        onPageCount(pageCount)

        onDispose {
            rendererHolder?.close()
            rendererHolder = null
        }
    }

    // Track current visible page for tools (rotate/delete/go-to)
    val firstVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(firstVisible) {
        if (pageCount > 0) onVisiblePageChanged(firstVisible.coerceIn(0, pageCount - 1))
    }

    if (pageCount <= 0 || rendererHolder == null) {
        Box(modifier = modifier.padding(16.dp)) {
            Text(
                "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
        return
    }

    // List pages 0..pageCount-1
    val pages = remember(pageCount) { (0 until pageCount).toList() }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pages) { pageIndex ->
            PdfPageItem(
                holder = rendererHolder!!,
                pageIndex = pageIndex
            )
        }
    }
}

@Composable
private fun PdfPageItem(
    holder: RendererHolder,
    pageIndex: Int
) {
    // Render bitmap async (only when item composes)
    val bmpState = produceState<Bitmap?>(initialValue = null, holder, pageIndex) {
        value = withContext(Dispatchers.IO) {
            renderPageBitmap(holder, pageIndex)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.20f),
        tonalElevation = 0.dp
    ) {
        Box(Modifier.background(Color.Black.copy(alpha = 0.12f))) {
            val bmp = bmpState.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
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

private class RendererHolder(
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer
) {
    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

private fun openRenderer(pdfFile: File): RendererHolder? {
    return runCatching {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        RendererHolder(pfd, renderer)
    }.getOrNull()
}

private fun renderPageBitmap(holder: RendererHolder, pageIndex: Int): Bitmap? {
    return runCatching {
        val renderer = holder.renderer
        if (pageIndex !in 0 until renderer.pageCount) return@runCatching null

        renderer.openPage(pageIndex).use { page ->
            // Render at higher scale for clarity
            val scale = 2.0f
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }.getOrNull()
}