package com.pdfliteai.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfBitmapView(
    pdfFile: File,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    onPageCount: (Int) -> Unit = {}
) {
    var bitmaps by remember(pdfFile) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(pdfFile) {
        bitmaps = emptyList()
        val rendered = withContext(Dispatchers.IO) {
            val imgs = mutableListOf<Bitmap>()
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                onPageCount(renderer.pageCount)
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        imgs.add(bmp)
                    }
                }
            }
            pfd.close()
            imgs
        }
        bitmaps = rendered
    }

    val scroll = rememberScrollState()
    Column(modifier.verticalScroll(scroll)) {
        bitmaps.forEach { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
