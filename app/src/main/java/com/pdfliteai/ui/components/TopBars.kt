package com.pdfliteai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PdfTopBar(
    hasDoc: Boolean,
    onOpen: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val barColor = Color.Black.copy(alpha = 0.28f)
    val border = Color.White.copy(alpha = 0.10f)

    // Full-width header (premium), only bottom corners rounded
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = barColor,
            border = BorderStroke(1.dp, border),
            shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumTitle(primary = primary)

                Spacer(Modifier.weight(1f))

                // Keep your existing behavior
                if (hasDoc) {
                    GlowPrimaryButton(text = "Open", onClick = onOpen)
                    Spacer(Modifier.width(10.dp))
                }

                IconButton(onClick = onTools, enabled = hasDoc) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Tools",
                        tint = if (hasDoc) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }

                Spacer(Modifier.width(2.dp))

                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumTitle(primary: Color) {
    val glow = primary.copy(alpha = 0.55f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Small premium accent block (subtle “brand mark”)
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .background(primary.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )

        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append("PDFLite ")
                }
                withStyle(
                    SpanStyle(
                        color = primary.copy(alpha = 0.95f),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("AI")
                }
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                letterSpacing = 0.4.sp,
                shadow = Shadow(
                    color = glow,
                    offset = Offset(0f, 0f),
                    blurRadius = 14f
                )
            )
        )
    }
}

@Composable
fun SimpleTopBar(
    title: String,
    onBack: () -> Unit
) {
    val barColor = Color.Black.copy(alpha = 0.28f)
    val border = Color.White.copy(alpha = 0.10f)

    Surface(color = Color.Transparent, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = barColor,
            border = BorderStroke(1.dp, border),
            shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        letterSpacing = 0.2.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}