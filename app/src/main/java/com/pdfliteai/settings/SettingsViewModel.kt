package com.pdfliteai.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfliteai.BuildConfig
import com.pdfliteai.data.ProviderId
import com.pdfliteai.storage.SecureKeyStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderUiSpec(
    val provider: ProviderId,
    val title: String,
    val fixedBaseUrl: String? = null,
    val models: List<String> = emptyList(),
    val allowFreeModelEntry: Boolean = false,
    val allowCustomBaseUrl: Boolean = false,
    val requiresKey: Boolean = true,
    // ✅ for Settings UI hints
    val note: String? = null
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val keyStore = SecureKeyStore(app)

    val aiSettings: StateFlow<AiSettings> =
        repo.aiSettingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings())

    val providers: List<ProviderUiSpec> = listOf(
        ProviderUiSpec(
            provider = ProviderId.GROQ,
            title = "Groq",
            fixedBaseUrl = "https://api.groq.com/openai/v1",
            models = listOf("llama-3.1-8b-instant", "llama3-8b-8192", "llama3-70b-8192"),
            allowFreeModelEntry = true,
            allowCustomBaseUrl = false,
            requiresKey = true
        ),
        ProviderUiSpec(
            provider = ProviderId.OPENROUTER,
            title = "OpenRouter",
            fixedBaseUrl = "https://openrouter.ai/api/v1",
            models = listOf("openai/gpt-4o-mini", "openai/gpt-4o"),
            allowFreeModelEntry = true,
            allowCustomBaseUrl = false,
            requiresKey = true
        ),
        ProviderUiSpec(
            provider = ProviderId.NOVA,
            title = "NOVA (Amazon)",
            fixedBaseUrl = "https://api.nova.amazon.com",
            models = listOf("nova-lite-v1", "nova-pro-v1"),
            allowFreeModelEntry = true,
            allowCustomBaseUrl = false,
            requiresKey = true
        ),
        ProviderUiSpec(
            provider = ProviderId.LOCAL_OPENAI_COMPAT,
            title = "Local (OpenAI-compatible)",
            fixedBaseUrl = null,
            models = listOf("gpt-4o-mini"),
            allowFreeModelEntry = true,
            allowCustomBaseUrl = true,
            requiresKey = false, // user may enter key, but not required
            note = """
How to use Local (OpenAI-compatible) on mobile:
1) Run an OpenAI-compatible server on your LAN or device.
   Examples: llama.cpp server, vLLM, Text-Generation-WebUI, LM Studio server.
2) Ensure your phone/emulator can reach it.
   - Emulator → host: use http://10.0.2.2:PORT
   - Physical phone → use your PC’s LAN IP: http://192.168.x.x:PORT
3) Set Base URL in Settings to that server root (with or without /v1).
   Example: http://10.0.2.2:11434/v1  OR  http://192.168.1.50:8000/v1
4) Pick a model name your server exposes.
""".trimIndent()
        )
    )

    fun specFor(p: ProviderId): ProviderUiSpec =
        providers.first { it.provider == p }

    private fun keyName(p: ProviderId) = when (p) {
        ProviderId.GROQ -> "key_groq"
        ProviderId.OPENROUTER -> "key_openrouter"
        ProviderId.NOVA -> "key_nova"
        ProviderId.LOCAL_OPENAI_COMPAT -> "key_local_compat"
    }

    fun getApiKey(provider: ProviderId): String {
        // ✅ For cloud providers, ALWAYS use BuildConfig (no UI display/edit)
        return when (provider) {
            ProviderId.GROQ -> BuildConfig.GROQ_KEY.trim()
            ProviderId.OPENROUTER -> BuildConfig.OPENROUTER_KEY.trim()
            ProviderId.NOVA -> BuildConfig.NOVA_KEY.trim()
            ProviderId.LOCAL_OPENAI_COMPAT -> {
                val saved = keyStore.get(keyName(provider)).trim()
                if (saved.isNotBlank()) saved else BuildConfig.LOCAL_COMPAT_KEY.trim()
            }
        }
    }

    // ✅ Only allow storing/clearing key for LOCAL_OPENAI_COMPAT
    fun setApiKey(provider: ProviderId, key: String) {
        if (provider != ProviderId.LOCAL_OPENAI_COMPAT) return
        keyStore.put(keyName(provider), key)
    }

    fun clearApiKey(provider: ProviderId) {
        if (provider != ProviderId.LOCAL_OPENAI_COMPAT) return
        keyStore.clear(keyName(provider))
    }

    fun setProvider(p: ProviderId) = viewModelScope.launch { repo.setProvider(p) }
    fun setModel(m: String) = viewModelScope.launch { repo.setModel(m) }
    fun setBaseUrl(url: String) = viewModelScope.launch { repo.setBaseUrl(url) }
    fun setTemperature(t: Float) = viewModelScope.launch { repo.setTemperature(t) }
}
