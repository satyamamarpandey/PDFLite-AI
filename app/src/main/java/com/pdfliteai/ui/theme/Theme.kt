package com.pdfliteai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuroraDarkColors = darkColorScheme(
    primary = Color(0xFFB39DFF),
    onPrimary = Color(0xFF0B0712),
    secondary = Color(0xFF7FD7FF),
    onSecondary = Color(0xFF041018),
    tertiary = Color(0xFF8EF0D4),
    onTertiary = Color(0xFF04120E),

    background = Color(0xFF070710),
    onBackground = Color(0xFFECEBFF),
    surface = Color(0xFF0B0B16),
    onSurface = Color(0xFFECEBFF),

    surfaceVariant = Color(0xFF151427),
    onSurfaceVariant = Color(0xFFCFCDE6),

    outline = Color(0xFF2B2A46)
)

@Composable
fun PdfLiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroraDarkColors,
        typography = PdfLiteTypography,
        content = content
    )
}