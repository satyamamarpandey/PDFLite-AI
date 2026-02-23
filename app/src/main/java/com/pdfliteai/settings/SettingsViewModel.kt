package com.pdfliteai.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfliteai.data.ProviderId
import com.pdfliteai.storage.SecureKeyStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    // Keep keystore ONLY for Local OpenAI Compat (optional)
    private val keyStore = SecureKeyStore(app)

    val aiSettings: StateFlow<AiSettings> =
        repo.aiSettingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings())

    val readerSettings: StateFlow<ReaderSettings> =
        repo.readerSettingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    val recentDocs: StateFlow<List<String>> =
        repo.recentDocsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { repo.ensureInitialized() }
    }

    private fun keyName(p: ProviderId) = when (p) {
        ProviderId.LOCAL_OPENAI_COMPAT -> "key_local_compat"
        ProviderId.GROQ -> "key_groq"            // not used anymore
        ProviderId.OPENROUTER -> "key_openrouter"// not used anymore
        ProviderId.NOVA -> "key_nova"            // not used anymore
    }

    /**
     * ✅ Cloudflare mode:
     * - Nova/Groq/OpenRouter keys are NOT stored on-device.
     * - Worker holds provider keys.
     *
     * We return "" for those providers so nothing is ever shipped.
     * Local OpenAI Compat can still use a user-provided key (stored securely).
     */
    fun getApiKey(provider: ProviderId): String {
        return when (provider) {
            ProviderId.LOCAL_OPENAI_COMPAT -> keyStore.get(keyName(provider)).trim()
            ProviderId.GROQ, ProviderId.OPENROUTER, ProviderId.NOVA -> ""
        }
    }

    /**
     * Only allow saving key for Local OpenAI Compat.
     */
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

    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(on) }
    fun setRecentsLimit(limit: Int) = viewModelScope.launch { repo.setRecentsLimit(limit) }
    fun addRecent(uri: String) = viewModelScope.launch { repo.addRecent(uri) }
    fun clearRecents() = viewModelScope.launch { repo.clearRecents() }

    fun setAutoOpenAi(on: Boolean) = viewModelScope.launch { repo.setAutoOpenAi(on) }
    fun setDefaultEntireDoc(on: Boolean) = viewModelScope.launch { repo.setDefaultEntireDoc(on) }
    fun setBgDim(v: Float) = viewModelScope.launch { repo.setBgDim(v) }

    fun setChatHistoryLimit(v: Int) = viewModelScope.launch { repo.setChatHistoryLimit(v) }
}