package com.pdfliteai.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

fun Modifier.premiumGlow(color: Color, strength: Float = 1f): Modifier = drawBehind {
    val c = color.copy(alpha = 0.20f * strength)
    val c2 = color.copy(alpha = 0.10f * strength)
    val c3 = color.copy(alpha = 0.06f * strength)

    val center = Offset(size.width / 2f, size.height / 2f)
    val base = (kotlin.math.min(size.width, size.height) * 0.72f)

    drawCircle(color = c3, radius = base * 1.40f, center = center)
    drawCircle(color = c2, radius = base * 1.10f, center = center)
    drawCircle(color = c, radius = base * 0.85f, center = center)
}
