package com.pdfliteai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfliteai.data.ProviderId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSheet(
    open: Boolean,
    onClose: () -> Unit,

    provider: ProviderId,
    onProviderChange: (ProviderId) -> Unit,
    model: String,
    modelPresets: List<String>,
    onModelChange: (String) -> Unit,
    onModelPick: (String) -> Unit,

    selectedMode: Boolean,
    onSelectedMode: () -> Unit,
    onEntireDocMode: () -> Unit,
    onQuickPrompt: (String) -> Unit,

    question: String,
    onQuestionChange: (String) -> Unit,
    busy: Boolean,
    onAsk: () -> Unit,
    onClear: () -> Unit,

    showEdit: Boolean,
    onRotateP1: () -> Unit,
    onDeleteP1: () -> Unit,
    onShare: () -> Unit,

    selectedText: String,
    onSelectedTextChange: (String) -> Unit,

    errorText: String?,
    answerText: String
) {
    if (!open) return

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        tonalElevation = 8.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Ask PDFLite AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Chat over your PDF (selected text or entire document)", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProviderDropdown(
                            modifier = Modifier.weight(1f),
                            value = provider,
                            onChange = onProviderChange
                        )
                        ModelDropdown(
                            modifier = Modifier.weight(1f),
                            current = model,
                            presets = modelPresets,
                            onPick = onModelPick,
                            onEdit = onModelChange
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { onQuickPrompt("Summarize the content clearly in bullet points.") }, label = { Text("Summarize") })
                        AssistChip(onClick = { onQuickPrompt("Explain this in simple terms with an example.") }, label = { Text("Explain") })
                        AssistChip(onClick = { onQuickPrompt("Give me key points and action items.") }, label = { Text("Key points") })
                        AssistChip(onClick = { onQuickPrompt("Find important dates, names, numbers, and entities.") }, label = { Text("Find data") })
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode,
                    onClick = onSelectedMode,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Selected text") }
                )
                SegmentedButton(
                    selected = !selectedMode,
                    onClick = onEntireDocMode,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Entire doc") }
                )
            }

            Text(
                if (selectedMode) "Selected mode uses only the text captured from your selection."
                else "Entire doc extracts text once (cached) and uses it for AI.",
                style = MaterialTheme.typography.labelMedium
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = onQuestionChange,
                        label = { Text("Ask a question") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onAsk, enabled = !busy) { Text(if (busy) "Asking..." else "Ask") }
                        OutlinedButton(onClick = onClear, enabled = !busy) { Text("Clear") }
                    }
                }
            }

            if (showEdit) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Edit (lite)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onRotateP1) { Text("Rotate p1") }
                            Button(onClick = onDeleteP1) { Text("Delete p1") }
                            OutlinedButton(onClick = onShare) { Text("Share") }
                        }
                    }
                }
            }

            if (selectedMode) {
                OutlinedTextField(
                    value = selectedText,
                    onValueChange = onSelectedTextChange,
                    label = { Text("Selected text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }

            if (!errorText.isNullOrBlank()) {
                Text(errorText, color = MaterialTheme.colorScheme.error)
            }

            if (answerText.isNotBlank()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Answer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SelectionContainer {
                            Text(answerText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    modifier: Modifier,
    value: ProviderId,
    onChange: (ProviderId) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ProviderId.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
                    onClick = {
                        expanded = false
                        onChange(p)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelDropdown(
    modifier: Modifier,
    current: String,
    presets: List<String>,
    onPick: (String) -> Unit,
    onEdit: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        OutlinedTextField(
            value = current,
            onValueChange = onEdit,
            label = { Text("Model") },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m) },
                    onClick = {
                        expanded = false
                        onPick(m)
                    }
                )
            }
        }
    }
}
