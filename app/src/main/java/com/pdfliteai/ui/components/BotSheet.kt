package com.pdfliteai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfliteai.data.ProviderId
import kotlinx.coroutines.launch
import com.pdfliteai.billing.PremiumGates

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
    historyChatsLimit: Int = 50,

    isPremium: Boolean,
    docChatCount: Int,
    onGoPremium: () -> Unit
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

    LaunchedEffect(open, shown.size) {
        if (!open) return@LaunchedEffect
        if (shown.isNotEmpty()) {
            runCatching { listState.scrollToItem(shown.lastIndex) }
        }
    }

    // IME handling (for back dismiss behavior + compact hint)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    BackHandler(enabled = imeVisible) {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    // Measure pinned input height so chat never hides behind it
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val inputHeightDp = with(density) { inputHeightPx.toDp() }

    ModalBottomSheet(
        onDismissRequest = {
            if (imeVisible) {
                keyboard?.hide()
                focusManager.clearFocus()
            } else {
                onClose()
            }
        },
        sheetState = sheetState,
        containerColor = Color(0xFF0B0B10),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false
        ),
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
                .fillMaxHeight(0.94f)
                .fillMaxWidth()
        ) {
            // Main content (does NOT move up on IME)
            Column(
                Modifier
                    .fillMaxSize()
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

                // AI Engine card (NORMAL card, no align() here)
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

                        if (!isPremium) {
                            Text(
                                "Free: $docChatCount/${PremiumGates.FREE_CHATS_PER_PDF} chats used for this PDF",
                                color = Color.White.copy(alpha = 0.70f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = onGoPremium) {
                                Text("Go Premium", color = Color.White)
                            }
                        }

                        ProviderPicker(
                            provider = provider,
                            onProviderChange = onProviderChange,
                            enabled = !busy,
                            isPremium = isPremium
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

                        QuickPromptsDropdown(
                            quickPrompts = quickPrompts,
                            enabled = !busy,
                            onQuickAsk = onQuickAsk
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

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

                        if (shown.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(14.dp)
                                    .padding(bottom = inputHeightDp + 16.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                Text(
                                    text = "Your answer will appear here…",
                                    color = Color.White.copy(alpha = 0.45f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            val baseIndex = (messages.size - shown.size).coerceAtLeast(0)

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = inputHeightDp + 16.dp)
                            ) {
                                items(
                                    count = shown.size,
                                    key = { i -> baseIndex + i }
                                ) { i ->
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
                }

                Spacer(Modifier.height(10.dp))
            }

            // ✅ PINNED input bar (above keyboard)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter) // ✅ valid here (BoxScope)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .onGloballyPositioned { inputHeightPx = it.size.height },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF12121A), // ✅ darker solid background
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(Modifier.padding(12.dp)) {

                    OutlinedTextField(
                        value = question,
                        onValueChange = onQuestionChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        minLines = 2,
                        maxLines = 5,
                        placeholder = {
                            Text("Enter your message here.", color = Color.White.copy(alpha = 0.60f))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0B0B10),
                            unfocusedContainerColor = Color(0xFF0B0B10),
                            disabledContainerColor = Color(0xFF0B0B10),

                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White.copy(alpha = 0.6f),

                            focusedBorderColor = Color.White.copy(alpha = 0.18f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            disabledBorderColor = Color.White.copy(alpha = 0.08f),

                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(Modifier.height(10.dp))

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
                        ) { Text("Send") }

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
                        ) { Text("Clear") }
                    }

                    // Keep compact while typing
                    if (!imeVisible) {
                        Spacer(Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "PDFLite AI can make mistakes. Check important info.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                color = Color.White.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                "When you use AI features, the text you select/extract may be sent to our server and AI providers to generate answers.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                ),
                                color = Color.White.copy(alpha = 0.38f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
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
 * Provider dropdown - full width (matches anchor width)
 */
@Composable
private fun ProviderPicker(
    provider: ProviderId,
    onProviderChange: (ProviderId) -> Unit,
    enabled: Boolean,
    isPremium: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val providers = remember {
        listOf(
            ProviderId.GROQ to "Smart Mode",
            ProviderId.NOVA to "Deep Analysis",
            ProviderId.OPENROUTER to "Extended Processing",
            ProviderId.LOCAL_OPENAI_COMPAT to "On-Device (Private)"
        )
    }
    val label = providers.firstOrNull { it.first == provider }?.second ?: provider.name

    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorSize = it.size }
    ) {
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
                TextButton(onClick = { expanded = true }, enabled = enabled) {
                    Text("Change", color = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f))
                }
            }
        }

        val menuWidth = with(density) { anchorSize.width.toDp() }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(menuWidth)
                .background(Color(0xFF12121A))
        ) {
            providers.forEach { (id, name) ->
                val selected = id == provider
                val allowed = isPremium || id == ProviderId.NOVA
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onProviderChange(id)
                    },
                    enabled = enabled && allowed
                )
            }
        }
    }
}

/**
 * Quick prompts dropdown - full width (matches anchor width)
 */
@Composable
private fun QuickPromptsDropdown(
    quickPrompts: List<Pair<String, String>>,
    enabled: Boolean,
    onQuickAsk: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorSize = it.size }
    ) {
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
                        "Quick prompts",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Select a prompt",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = true }, enabled = enabled) {
                    Text("Choose", color = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f))
                }
            }
        }

        val menuWidth = with(density) { anchorSize.width.toDp() }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(menuWidth)
                .background(Color(0xFF12121A))
        ) {
            quickPrompts.forEach { (label, prompt) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White.copy(alpha = 0.92f)) },
                    onClick = {
                        expanded = false
                        onQuickAsk(prompt)
                    },
                    enabled = enabled
                )
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