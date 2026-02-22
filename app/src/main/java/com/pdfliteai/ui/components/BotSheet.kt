package com.pdfliteai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfliteai.data.ProviderId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSheet(
    open: Boolean,
    onClose: () -> Unit,

    provider: ProviderId,
    onProviderChange: (ProviderId) -> Unit,

    busy: Boolean,

    quickPrompts: List<Pair<String, String>>,
    onQuickAsk: (String) -> Unit,

    question: String,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onClearInput: () -> Unit,

    errorText: String?,
    messages: List<ChatMessage>,

    historyChatsLimit: Int = 50
) {
    if (!open) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    val listState = rememberLazyListState()

    val shown = remember(messages, historyChatsLimit) {
        if (messages.size <= historyChatsLimit) messages else messages.takeLast(historyChatsLimit)
    }

    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty()) listState.animateScrollToItem(shown.lastIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color(0xFF0B0B10),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                )
            }
        }
    ) {
        Box(
            Modifier
                .fillMaxHeight(0.92f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                // Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ask PDFLite AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // AI Engine card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "AI Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )

                        ProviderPicker(
                            provider = provider,
                            onProviderChange = onProviderChange,
                            enabled = !busy
                        )

                        Divider(color = Color.White.copy(alpha = 0.10f))

                        Text(
                            "Quick prompts",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.92f)
                        )

                        QuickPromptsGrid(
                            quickPrompts = quickPrompts,
                            enabled = !busy,
                            onQuickAsk = onQuickAsk
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Chat area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Column(Modifier.fillMaxWidth()) {

                        if (!errorText.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF2A1111),
                                border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = errorText,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.White.copy(alpha = 0.92f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(shown.size) { i ->
                                val m = shown[i]
                                ChatBubble(
                                    msg = m,
                                    onCopy = { raw ->
                                        clipboard.setText(AnnotatedString(markdownToPlainText(raw)))
                                        scope.launch { snack.showSnackbar("Copied") }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Input area
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Column(Modifier.padding(12.dp)) {

                        // ✅ No placeholder text
                        OutlinedTextField(
                            value = question,
                            onValueChange = onQuestionChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            minLines = 2,
                            maxLines = 5,
                            placeholder = { Text("Enter your message here.", color = Color.White.copy(alpha = 0.45f)) }
                        )

                        Spacer(Modifier.height(10.dp))

                        // ✅ Filled buttons
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onAsk,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    disabledContentColor = Color.White.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Send")
                            }

                            Button(
                                onClick = onClearInput,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color.White.copy(alpha = 0.06f),
                                    disabledContentColor = Color.White.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Cancel")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // ✅ Disclaimer
                        Text(
                            "PDFLite AI can make mistakes, Check important info.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            SnackbarHost(
                hostState = snack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    }
}

/**
 * ✅ Anchored dropdown (Material3 DropdownMenu) — will open directly under Provider field
 * Works on older Material3 (no ExposedDropdownMenu dependency).
 */
@Composable
private fun ProviderPicker(
    provider: ProviderId,
    onProviderChange: (ProviderId) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    val providers = remember {
        listOf(
            ProviderId.GROQ to "Groq",
            ProviderId.NOVA to "NOVA",
            ProviderId.OPENROUTER to "OpenRouter",
            ProviderId.LOCAL_OPENAI_COMPAT to "Local"
        )
    }
    val label = providers.firstOrNull { it.first == provider }?.second ?: provider.name

    Box(Modifier.fillMaxWidth()) {

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Provider",
                        color = Color.White.copy(alpha = 0.60f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { expanded = true },
                    enabled = enabled
                ) {
                    Text("Change", color = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f))
                }
            }
        }

        // ✅ This is the key: dropdown is declared right here (same Box), so it anchors under Provider.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            providers.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onProviderChange(id)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickPromptsGrid(
    quickPrompts: List<Pair<String, String>>,
    enabled: Boolean,
    onQuickAsk: (String) -> Unit
) {
    val rows = quickPrompts.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (label, prompt) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = if (enabled) 0.06f else 0.03f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                    ) {
                        TextButton(
                            onClick = { onQuickAsk(prompt) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(label)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    onCopy: (String) -> Unit
) {
    val isUser = msg.role == ChatRole.User
    val isAssistant = msg.role == ChatRole.Assistant

    val bubbleColor = when (msg.role) {
        ChatRole.User -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        ChatRole.Assistant -> Color.Black.copy(alpha = 0.35f)
        ChatRole.System -> Color.White.copy(alpha = 0.06f)
    }

    val border = when (msg.role) {
        ChatRole.User -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ChatRole.Assistant -> Color.White.copy(alpha = 0.10f)
        ChatRole.System -> Color.White.copy(alpha = 0.10f)
    }

    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            border = BorderStroke(1.dp, border),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.94f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (msg.role) {
                            ChatRole.User -> "You"
                            ChatRole.Assistant -> "PDFLite AI"
                            ChatRole.System -> "System"
                        },
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.weight(1f))

                    if (isAssistant) {
                        IconButton(onClick = { onCopy(msg.text) }) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                SelectionContainer {
                    MarkdownText(
                        text = msg.text,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownText(text: String, color: Color) {
    val display = remember(text) { preprocessMarkdownForDisplay(text) }
    val annotated = remember(display) { markdownToAnnotated(display) }

    Text(
        text = annotated,
        color = color,
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun preprocessMarkdownForDisplay(raw: String): String {
    var t = raw.replace("\r\n", "\n")
    t = t.replace(Regex("(?m)^#{1,6}\\s+"), "")
    t = t.replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
    return t.trim()
}

private fun markdownToPlainText(raw: String): String {
    return preprocessMarkdownForDisplay(raw).replace("**", "")
}

private fun markdownToAnnotated(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        var bold = false
        while (i < text.length) {
            val token = (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*')
            if (token) {
                if (!bold) pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) else pop()
                bold = !bold
                i += 2
            } else {
                append(text[i])
                i += 1
            }
        }
        if (bold) pop()
    }
}