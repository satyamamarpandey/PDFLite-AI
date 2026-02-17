package com.pdfliteai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pdfliteai.data.Scope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    scope: Scope,
    onScopeChange: (Scope) -> Unit,
    question: String,
    onQuestionChange: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    onAsk: () -> Unit,
    busy: Boolean,
    answer: String,
    error: String?
) {
    if (!isOpen) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // keeps it “chat widget” style instead of full page takeover
        sheetMaxWidth = 640.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Header row like a chat widget
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Satyam Bot", style = MaterialTheme.typography.titleMedium)
                    Text("Ask me about this PDF", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onDismiss) {
                    Text("✕")
                }
            }

            // Scope toggle
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = scope == Scope.SelectedText,
                    onClick = { onScopeChange(Scope.SelectedText) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Selected") }

                SegmentedButton(
                    selected = scope == Scope.EntireDocument,
                    onClick = { onScopeChange(Scope.EntireDocument) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Entire doc") }
            }

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onQuickAction("Summarize this") },
                    label = { Text("Summarize") }
                )
                AssistChip(
                    onClick = { onQuickAction("Explain this section") },
                    label = { Text("Explain") }
                )
                AssistChip(
                    onClick = { onQuickAction("Key points in bullets") },
                    label = { Text("Key points") }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onQuickAction("What is this document about?") },
                    label = { Text("About doc") }
                )
                AssistChip(
                    onClick = { onQuickAction("Find dates/numbers and list them") },
                    label = { Text("Find data") }
                )
            }

            // Question input
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                label = { Text("Type your question…") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Actions
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAsk,
                    enabled = !busy
                ) { Text(if (busy) "Thinking…" else "Ask") }

                OutlinedButton(
                    onClick = onDismiss
                ) { Text("Close") }
            }

            // Output area
            error?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }

            if (answer.isNotBlank()) {
                val scroll = rememberScrollState()
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Column(
                            Modifier.padding(12.dp).verticalScroll(scroll)
                        ) {
                            Text(answer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
