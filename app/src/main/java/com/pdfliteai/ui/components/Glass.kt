package com.pdfliteai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun GlassBubble(
    modifier: Modifier = Modifier,
    background: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun GlowPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.premiumGlow(primary, strength = 1f),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primary.copy(alpha = 0.95f),
            contentColor = Color.Black,
            disabledContainerColor = primary.copy(alpha = 0.35f),
            disabledContentColor = Color.Black.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun GlowSecondaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val glow = MaterialTheme.colorScheme.tertiary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.premiumGlow(glow, strength = 0.55f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        )
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.titleSmall
        )
    }
}
