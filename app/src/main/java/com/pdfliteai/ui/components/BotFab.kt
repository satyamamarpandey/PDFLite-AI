package com.pdfliteai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@Composable
fun BotFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.premiumGlow(primary, 1f),
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 16.dp
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = primary.copy(alpha = 0.92f),
            shadowElevation = 0.dp
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clickable { onClick() }
                    .padding(horizontal = 18.dp, vertical = 18.dp)
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
