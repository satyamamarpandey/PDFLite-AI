package com.pdfliteai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun BotFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "botFabScale"
    )

    val outer = 72.dp
    val inner = 58.dp
    val badge = 40.dp

    // Outer ring
    val ringBg = Color.Black.copy(alpha = 0.24f)
    val ringStroke = Color.White.copy(alpha = 0.16f)

    // Core gradient (more “glass”, less flat)
    val coreGradient = Brush.radialGradient(
        colors = listOf(
            primary.copy(alpha = 0.95f),
            primary.copy(alpha = 0.72f),
            primary.copy(alpha = 0.92f)
        )
    )

    // Badge (frosted glass)
    val badgeBg = Color.White.copy(alpha = 0.14f)
    val badgeBorder = Color.White.copy(alpha = 0.20f)

    // Bot icon tint (dark, premium)
    val botTint = Color.Black.copy(alpha = 0.92f)

    Surface(
        modifier = modifier
            .size(outer)
            .scale(scale)
            // ✅ Pure circle glow: no shadow rasterization -> no hex artifact
            .drawBehind {
                drawCircle(color = primary.copy(alpha = 0.22f), radius = size.minDimension * 0.62f)
                drawCircle(color = primary.copy(alpha = 0.10f), radius = size.minDimension * 0.78f)
            },
        shape = CircleShape,
        color = ringBg,
        border = BorderStroke(1.dp, ringStroke),
        shadowElevation = 0.dp // ✅ remove shadow (main cause of hex artifacts)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Inner core
            Surface(
                modifier = Modifier.size(inner),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(coreGradient),
                    contentAlignment = Alignment.Center
                ) {
                    // Frost badge
                    Surface(
                        shape = CircleShape,
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeBorder),
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier.size(badge),
                            contentAlignment = Alignment.Center
                        ) {
                            // ✅ Better-looking icon style (Rounded)
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = "AI",
                                tint = botTint
                            )

                            // ✅ Tiny sparkle overlay for “premium”
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                            )
                        }
                    }
                }
            }

            // Specular highlight (subtle + clipped correctly)
            BoxWithConstraints(
                modifier = Modifier
                    .size(inner)
                    .clip(CircleShape)
            ) {
                val density = LocalDensity.current
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { maxHeight.toPx() }

                val center = Offset(wPx * 0.34f, hPx * 0.22f)

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (pressed) 0.08f else 0.12f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = (wPx.coerceAtLeast(hPx)) * 0.85f
                            )
                        )
                )
            }

            // Thin inner rim for “hardware edge”
            Box(
                modifier = Modifier
                    .size(inner)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.14f)
                            )
                        )
                    )
            )
        }
    }
}