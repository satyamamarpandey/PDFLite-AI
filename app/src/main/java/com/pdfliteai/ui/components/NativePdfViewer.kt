// NativePdfViewer.kt
package com.pdfliteai.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.barteksc.pdfviewer.PDFView
import java.io.File

@Composable
fun NativePdfViewer(
    file: File,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onSingleTap: () -> Unit,
    onPageChanged: (pageIndex0: Int, pageCount: Int) -> Unit,
    onUserScroll: () -> Unit,
    onPdfViewReady: (PDFView) -> Unit
) {
    val safeInitial = initialPage.coerceAtLeast(0)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PDFView(ctx, null).apply {
                id = View.generateViewId()
                onPdfViewReady(this)
            }
        },
        update = { pdfView ->
            pdfView.recycle()

            pdfView.fromFile(file)
                .defaultPage(safeInitial)
                .enableSwipe(true)
                .swipeHorizontal(false)      // vertical only
                .enableDoubletap(true)

                // remove page gaps
                .spacing(0)
                .autoSpacing(false)
                .fitEachPage(true)

                // IMPORTANT: remove the vertical page scroller
                // .scrollHandle(...)

                .pageSnap(false)            // snap adds “jump” feel; keep natural scroll
                .pageFling(true)

                .onTap {
                    onSingleTap()
                    true
                }
                .onPageScroll { _, _ ->
                    onUserScroll()
                }
                .onPageChange { page, pageCount ->
                    onPageChanged(page, pageCount)
                }
                .onLoad { total ->
                    onPageChanged(pdfView.currentPage, total)
                }
                .load()
        }
    )

    // optional: cleanup when leaving composition
    DisposableEffect(file.absolutePath) {
        onDispose { /* PDFView is owned by AndroidView; recycle happens on next update */ }
    }
}