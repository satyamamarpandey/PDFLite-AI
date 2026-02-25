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
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // Size: premium but not huge
    val outer = 72.dp
    val inner = 58.dp
    val badge = 38.dp

    // Outer ring background: deep glass (not flat black)
    val ringBg = Color.Black.copy(alpha = 0.26f)
    val ringStroke = Color.White.copy(alpha = 0.18f)

    // Core gradient: richer + less “cartoon”
    val coreGradient = Brush.linearGradient(
        colors = listOf(
            primary.copy(alpha = 0.98f),
            primary.copy(alpha = 0.70f),
            primary.copy(alpha = 0.92f)
        )
    )

    // Soft halo: controlled, premium
    val halo = Brush.radialGradient(
        colors = listOf(
            primary.copy(alpha = 0.20f),
            Color.Transparent
        )
    )

    // Badge: frosted glass
    val badgeBg = Color.White.copy(alpha = 0.16f)
    val badgeBorder = Color.White.copy(alpha = 0.22f)

    // Icon: BLACK bot
    val botTint = Color.Black.copy(alpha = 0.92f)

    Surface(
        modifier = modifier
            .size(outer)
            .scale(scale)
            .premiumGlow(primary, 0.40f),
        shape = CircleShape,
        color = ringBg,
        border = BorderStroke(1.dp, ringStroke),
        shadowElevation = 10.dp
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
            // Single halo layer (behind everything)
            Box(
                modifier = Modifier
                    .size(outer)
                    .background(halo)
            )

            // Inner core shell (ring + gradient)
            Surface(
                modifier = Modifier.size(inner),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(coreGradient),
                    contentAlignment = Alignment.Center
                ) {
                    // Badge (frosted glass)
                    Surface(
                        shape = CircleShape,
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeBorder),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier.size(badge),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SmartToy,
                                contentDescription = "AI",
                                tint = botTint
                            )
                        }
                    }
                }
            }

            // Premium specular highlight (correct center in px, not 0..1)
            BoxWithConstraints(
                modifier = Modifier
                    .size(inner)
                    .clip(CircleShape)
            ) {
                val density = LocalDensity.current
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { maxHeight.toPx() }

                // highlight near top-left
                val center = Offset(wPx * 0.35f, hPx * 0.25f)

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (pressed) 0.10f else 0.16f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = (wPx.coerceAtLeast(hPx)) * 0.9f
                            )
                        )
                )
            }

            // Thin inner rim (adds “hardware” edge)
            Box(
                modifier = Modifier
                    .size(inner)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f)
                            )
                        )
                    )
            )
        }
    }
}