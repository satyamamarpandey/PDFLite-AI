// TopBars.kt
package com.pdfliteai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.text.style.TextOverflow
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
    val barColor = Color.Black.copy(alpha = 0.28f)
    val border = Color.White.copy(alpha = 0.10f)

    val openTint = Color(0xFFFCAB99)
    val saveTint = Color(0xFF9BEF88)
    val toolsTint = Color(0xFFD5B96F)
    val settingsTint = Color(0xFF999DF6)

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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumTitle()

                Spacer(Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumActionButton(
                        label = "Open",
                        icon = { tint ->
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = "Open",
                                tint = tint,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        tint = openTint,
                        enabled = true,
                        onClick = onOpen
                    )

                    PremiumActionButton(
                        label = "Save",
                        icon = { tint ->
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = "Save",
                                tint = tint,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        tint = saveTint,
                        enabled = (hasDoc && onSaveCopy != null),
                        onClick = { onSaveCopy?.invoke() }
                    )

                    PremiumActionButton(
                        label = "Tools",
                        icon = { tint ->
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Tools",
                                tint = tint,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        tint = toolsTint,
                        enabled = hasDoc,
                        onClick = onTools
                    )

                    PremiumActionButton(
                        label = "Settings",
                        icon = { tint ->
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = tint,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        tint = settingsTint,
                        enabled = true,
                        onClick = onSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumTitle() {
    val markBg = Color.White.copy(alpha = 0.08f)
    val markStroke = Color.White.copy(alpha = 0.12f)

    // ✅ non-purple accent for "AI"
    val aiAccent = Color(0xFF7DD3FC) // ice blue

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = markBg,
            border = BorderStroke(1.dp, markStroke),
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.pdfliteaiicon_playstore),
                    contentDescription = "PDFLite AI",
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.width(9.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                ) { append("PDFLite") }

                append(" ")

                withStyle(
                    SpanStyle(
                        color = aiAccent,
                        fontWeight = FontWeight.Black
                    )
                ) { append("AI") }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 19.sp,
                letterSpacing = 0.20.sp
            )
        )
    }
}

@Composable
private fun PremiumActionButton(
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val iconTint = if (enabled) tint else Color.White.copy(alpha = 0.28f)
    val labelColor =
        if (enabled) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.28f)

    val bg = if (enabled) tint.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .width(54.dp)
            .height(44.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon(iconTint)

            Spacer(Modifier.height(1.dp))

            Text(
                text = label,
                color = labelColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.05.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
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