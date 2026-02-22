package com.pdfliteai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BotFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    // ✅ real press feedback
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "fabScale"
    )

    // ✅ Make it round + bigger
    val outerSize = 78.dp
    val innerSize = 64.dp

    // ✅ Premium gradient for dark UI
    val innerGradient = Brush.linearGradient(
        colors = listOf(
            primary.copy(alpha = 0.95f),
            primary.copy(alpha = 0.72f),
            primary.copy(alpha = 0.90f)
        )
    )

    Surface(
        modifier = modifier
            .size(outerSize)
            .scale(pressedScale)
            // ✅ reduce glow intensity (smaller “radius feel”)
            .premiumGlow(primary, 0.55f),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
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
            // ✅ softer + tighter glow ring
            Box(
                modifier = Modifier
                    .size(outerSize)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // inner circle
            Surface(
                modifier = Modifier.size(innerSize),
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(innerGradient),
                    contentAlignment = Alignment.Center
                ) {
                    // ✅ icon: bigger + higher contrast
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SmartToy,
                                contentDescription = "AI",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}