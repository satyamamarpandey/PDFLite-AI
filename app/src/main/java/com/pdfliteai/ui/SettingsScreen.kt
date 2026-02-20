package com.pdfliteai.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfliteai.data.ProviderId
import com.pdfliteai.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTestConnection: suspend (
        provider: ProviderId,
        model: String,
        baseUrl: String,
        apiKey: String,
        temperature: Float
    ) -> String
) {
    val vm: SettingsViewModel = viewModel()
    val s by vm.aiSettings.collectAsState()

    val spec = vm.specFor(s.provider)

    // ✅ For fixed-key providers, do NOT store/edit key in UI.
    // ✅ For LOCAL_OPENAI_COMPAT only, allow an optional user key.
    var localKey by remember(s.provider) {
        mutableStateOf(
            if (s.provider == ProviderId.LOCAL_OPENAI_COMPAT) vm.getApiKey(s.provider) else ""
        )
    }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val isFixedKeyProvider =
        s.provider == ProviderId.GROQ ||
                s.provider == ProviderId.NOVA ||
                s.provider == ProviderId.OPENROUTER

    val isLocal = s.provider == ProviderId.LOCAL_OPENAI_COMPAT

    // ✅ Base URL used for testing
    val effectiveBaseUrl = remember(s.provider, s.baseUrl, spec.fixedBaseUrl) {
        (if (spec.allowCustomBaseUrl) s.baseUrl else (spec.fixedBaseUrl ?: "")).trim()
    }

    // ✅ Key used for testing
    val effectiveKey = remember(s.provider, localKey) {
        when {
            isFixedKeyProvider -> vm.getApiKey(s.provider).trim() // BuildConfig
            isLocal -> localKey.trim()
            else -> ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // --------------------------
            // AI Provider / Model / Base URL
            // --------------------------
            ElevatedCard {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("AI Provider", style = MaterialTheme.typography.titleMedium)

                    ProviderDropdown(
                        providers = vm.providers.map { it.provider to it.title },
                        selected = s.provider,
                        onSelect = { vm.setProvider(it) }
                    )

                    Text("Model", style = MaterialTheme.typography.labelLarge)

                    if (spec.allowFreeModelEntry) {
                        OutlinedTextField(
                            value = s.model,
                            onValueChange = { vm.setModel(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Enter model name") }
                        )
                    } else {
                        ModelDropdown(
                            models = spec.models,
                            selected = s.model,
                            onSelect = { vm.setModel(it) }
                        )
                    }

                    Text("Base URL", style = MaterialTheme.typography.labelLarge)

                    if (spec.allowCustomBaseUrl) {
                        OutlinedTextField(
                            value = s.baseUrl,
                            onValueChange = { vm.setBaseUrl(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("http://10.0.2.2:PORT or http://192.168.x.x:PORT") }
                        )
                    } else {
                        OutlinedTextField(
                            value = spec.fixedBaseUrl ?: "",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            singleLine = true
                        )
                    }

                    spec.note?.let {
                        HorizontalDivider()
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // --------------------------
            // Security / Test Connection
            // --------------------------
            ElevatedCard {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Security", style = MaterialTheme.typography.titleMedium)

                    when {
                        isFixedKeyProvider -> {
                            Text(
                                "API key is loaded from local.properties (BuildConfig). It is not shown in the app.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Button(onClick = {
                                testing = true
                                testResult = null
                            }) { Text("Test connection") }
                        }

                        isLocal -> {
                            Text(
                                "Local server typically doesn’t require an API key. If yours requires one, set it below.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = localKey,
                                onValueChange = {
                                    localKey = it
                                    vm.setApiKey(ProviderId.LOCAL_OPENAI_COMPAT, it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Local API key (optional)") },
                                placeholder = { Text("Optional") },
                                singleLine = true
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = {
                                    localKey = ""
                                    vm.clearApiKey(ProviderId.LOCAL_OPENAI_COMPAT)
                                }) { Text("Clear") }

                                Button(onClick = {
                                    testing = true
                                    testResult = null
                                }) { Text("Test connection") }
                            }
                        }
                    }

                    if (testing) {
                        LaunchedEffect(
                            s.provider,
                            s.model,
                            effectiveBaseUrl,
                            effectiveKey,
                            s.temperature,
                            testing
                        ) {
                            val msg = runCatching {
                                withContext(Dispatchers.IO) {
                                    onTestConnection(
                                        s.provider,
                                        s.model,
                                        effectiveBaseUrl,
                                        effectiveKey,
                                        s.temperature
                                    )
                                }
                            }.getOrElse { "Test failed: ${it.message ?: it::class.java.simpleName}" }

                            testResult = msg
                            testing = false
                        }
                    }

                    testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            // --------------------------
            // Temperature
            // --------------------------
            ElevatedCard {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Basic App Settings", style = MaterialTheme.typography.titleMedium)

                    Text("Temperature", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = s.temperature,
                        onValueChange = { vm.setTemperature(it) },
                        valueRange = 0f..1f
                    )
                    Text(
                        "Lower = more factual. Higher = more creative.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    providers: List<Pair<ProviderId, String>>,
    selected: ProviderId,
    onSelect: (ProviderId) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = providers.firstOrNull { it.first == selected }?.second ?: selected.name

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
            label = { Text("Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val safeSelected = if (models.contains(selected)) selected else models.firstOrNull().orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = safeSelected,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m) },
                    onClick = {
                        expanded = false
                        onSelect(m)
                    }
                )
            }
        }
    }
}
