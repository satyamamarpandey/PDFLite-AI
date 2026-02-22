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

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
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
        ProviderId.GROQ -> "key_groq"
        ProviderId.OPENROUTER -> "key_openrouter"
        ProviderId.NOVA -> "key_nova"
        ProviderId.LOCAL_OPENAI_COMPAT -> "key_local_compat"
    }

    fun getApiKey(provider: ProviderId): String {
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