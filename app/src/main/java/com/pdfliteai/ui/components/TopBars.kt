package com.pdfliteai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfliteai.R

@Composable
fun PdfTopBar(
    hasDoc: Boolean,
    onOpen: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit,
    onSaveCopy: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val barColor = Color.Black.copy(alpha = 0.26f)
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
                modifier = Modifier
                    .fillMaxWidth()
                    // tighter overall height
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumTitle(primary = primary)

                // keeps title on left + actions on right
                Spacer(Modifier.weight(1f))

                // ensures Open never feels too close to the title on tighter widths
                Spacer(Modifier.width(14.dp))

                if (hasDoc) {
                    // Smaller, premium pill (NO glow)
                    MiniPrimaryPillButton(
                        text = "Open",
                        onClick = onOpen,
                        primary = primary
                    )

                    Spacer(Modifier.width(8.dp))

                    if (onSaveCopy != null) {
                        TopBarIconButton(onClick = onSaveCopy, enabled = true) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = "Save a copy",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }

                TopBarIconButton(onClick = onTools, enabled = hasDoc) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Tools",
                        tint = if (hasDoc) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }

                Spacer(Modifier.width(6.dp))

                TopBarIconButton(onClick = onSettings, enabled = true) {
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
    val markBg = Color.White.copy(alpha = 0.08f)
    val markStroke = Color.White.copy(alpha = 0.12f)

    Row(verticalAlignment = Alignment.CenterVertically) {

        // Brand mark: use app icon (from drawable) instead of purple dot
        Surface(
            color = markBg,
            border = BorderStroke(1.dp, markStroke),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.pdfliteaiicon_playstore),
                    contentDescription = "PDFLite AI",
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape),   // helps if image has white bg edges
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // One-line lockup: "PDFLite AI" (no pill, no circle around AI)
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                ) { append("PDFLite") }

                append(" ")

                withStyle(
                    SpanStyle(
                        color = primary.copy(alpha = 0.98f),
                        fontWeight = FontWeight.Bold
                    )
                ) { append("AI") }
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 19.sp,
                letterSpacing = 0.15.sp
            )
        )
    }
}

/**
 * Small pill action button for "Open" (no glow, compact, premium)
 */
@Composable
private fun MiniPrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    primary: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = primary.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.40f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Consistent, premium icon buttons (slight background, tighter spacing)
 */
@Composable
private fun TopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val bg = Color.White.copy(alpha = if (enabled) 0.06f else 0.03f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun SimpleTopBar(
    title: String,
    onBack: () -> Unit
) {
    val barColor = Color.Black.copy(alpha = 0.26f)
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
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        letterSpacing = 0.2.sp,
                        fontSize = 18.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}