package com.pdfliteai.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrTextExtractor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractTextFromBitmap(bmp: Bitmap): String = withContext(Dispatchers.Default) {
        val img = InputImage.fromBitmap(bmp, 0)
        val res = recognizer.process(img).await()
        res.text?.trim().orEmpty()
    }
}
