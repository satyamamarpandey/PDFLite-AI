package com.pdfliteai.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfliteai.data.ProviderId
import com.pdfliteai.settings.SettingsViewModel
import com.pdfliteai.ui.components.GlassCard
import com.pdfliteai.ui.components.SimpleTopBar

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
    val r by vm.readerSettings.collectAsState()

    val isLocal = s.provider == ProviderId.LOCAL_OPENAI_COMPAT

    var localKey by remember(s.provider) {
        mutableStateOf(if (isLocal) vm.getApiKey(s.provider) else "")
    }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val vignette = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.42f),
            Color.Black.copy(alpha = 0.62f)
        )
    )

    val verticalScrim = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.55f),
            Color.Black.copy(alpha = 0.20f),
            Color.Black.copy(alpha = 0.55f)
        )
    )

    Scaffold(
        topBar = { SimpleTopBar(title = "Settings", onBack = onBack) },
        containerColor = Color.Transparent
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            // ✅ Aurora background in Settings too (fixes washed gray look)
            Image(
                painter = painterResource(id = com.pdfliteai.R.drawable.aurora_header),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Depth + legibility layers
            Box(Modifier.fillMaxSize().background(verticalScrim))
            Box(Modifier.fillMaxSize().background(vignette))

            // ✅ bgDim now visibly works here too
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = r.bgDim)))

            Column(
                modifier = Modifier
                    .padding(pad)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---------------- AI Provider ----------------
                DarkGlassCard {
                    Text(
                        "AI Provider",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))

                    ProviderGridBig(
                        selected = s.provider,
                        onSelect = { vm.setProvider(it) }
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "AI temperature",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = s.temperature,
                        onValueChange = { vm.setTemperature(it) },
                        valueRange = 0f..1f
                    )
                    Text(
                        "Lower = more factual. Higher = more creative.",
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (isLocal) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Local model",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = s.model,
                            onValueChange = { vm.setModel(it) },
                            label = { Text("Model") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Enter the exact Model name") }
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = s.baseUrl,
                            onValueChange = { vm.setBaseUrl(it) },
                            label = { Text("Base URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("eg: http://10.0.2.2:PORT") }
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = localKey,
                            onValueChange = {
                                localKey = it
                                vm.setApiKey(ProviderId.LOCAL_OPENAI_COMPAT, it)
                            },
                            label = { Text("Local API key (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    localKey = ""
                                    vm.clearApiKey(ProviderId.LOCAL_OPENAI_COMPAT)
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    contentColor = Color.White
                                )
                            ) { Text("Clear key") }

                            Button(
                                onClick = {
                                    testing = true
                                    testResult = null
                                },
                                enabled = !testing,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                    contentColor = Color.Black
                                )
                            ) { Text(if (testing) "Testing…" else "Test") }
                        }

                        if (testing) {
                            LaunchedEffect(testing, s.model, s.baseUrl, localKey, s.temperature) {
                                val msg = runCatching {
                                    onTestConnection(
                                        s.provider,
                                        s.model,
                                        s.baseUrl.trim(),
                                        localKey.trim(),
                                        s.temperature
                                    )
                                }.getOrElse { "Test failed: ${it.message ?: it::class.java.simpleName}" }
                                testResult = msg
                                testing = false
                            }
                        }

                        testResult?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                // ---------------- Reader Settings ----------------
                DarkGlassCard {
                    ToggleRow(
                        title = "Keep screen on",
                        subtitle = "Prevents the display from sleeping while reading.",
                        checked = r.keepScreenOn,
                        onCheckedChange = { vm.setKeepScreenOn(it) }
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Spacer(Modifier.height(10.dp))

                    ToggleRow(
                        title = "Auto open AI panel",
                        subtitle = "Opens the AI panel after you open a PDF.",
                        checked = r.autoOpenAi,
                        onCheckedChange = { vm.setAutoOpenAi(it) }
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "Background dim",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = r.bgDim,
                        onValueChange = { vm.setBgDim(it) },
                        valueRange = 0f..0.45f
                    )
                    Text(
                        "Adjust readability over the aurora background.",
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ---------------- Recents ----------------
                DarkGlassCard {
                    Text("Recents", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(10.dp))

                    Text(
                        "Recent File' limit: ${r.recentsLimit}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = r.recentsLimit.toFloat(),
                        onValueChange = { vm.setRecentsLimit(it.toInt()) },
                        valueRange = 3f..10f,
                        steps = 6
                    )

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.clearRecents() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) { Text("Clear recents") }
                }
            }
        }
    }
}

@Composable
private fun DarkGlassCard(content: @Composable ColumnScope.() -> Unit) {
    // darker than GlassCard → better readability
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 12.dp
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ProviderGridBig(
    selected: ProviderId,
    onSelect: (ProviderId) -> Unit
) {
    val cfg = LocalConfiguration.current
    val twoCols = cfg.screenWidthDp >= 360

    if (twoCols) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderTile("NOVA Micro", selected == ProviderId.GROQ) { onSelect(ProviderId.GROQ) }
                ProviderTile("OpenRouter", selected == ProviderId.OPENROUTER) { onSelect(ProviderId.OPENROUTER) }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderTile("NOVA", selected == ProviderId.NOVA) { onSelect(ProviderId.NOVA) }
                ProviderTile("Local", selected == ProviderId.LOCAL_OPENAI_COMPAT) { onSelect(ProviderId.LOCAL_OPENAI_COMPAT) }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ProviderTile("NOVA Micro", selected == ProviderId.GROQ) { onSelect(ProviderId.GROQ) }
            ProviderTile("OpenRouter", selected == ProviderId.OPENROUTER) { onSelect(ProviderId.OPENROUTER) }
            ProviderTile("NOVA", selected == ProviderId.NOVA) { onSelect(ProviderId.NOVA) }
            ProviderTile("Local", selected == ProviderId.LOCAL_OPENAI_COMPAT) { onSelect(ProviderId.LOCAL_OPENAI_COMPAT) }
        }
    }
}

@Composable
private fun ProviderTile(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    else
        Color.White.copy(alpha = 0.08f)

    val border = if (selected) 0.22f else 0.14f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = border)),
        shadowElevation = if (selected) 10.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp) // ✅ bigger box size
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}