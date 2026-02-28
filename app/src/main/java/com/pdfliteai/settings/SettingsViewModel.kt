package com.pdfliteai.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfliteai.data.ProviderId
import com.pdfliteai.storage.SecureKeyStore
import com.pdfliteai.telemetry.TelemetryManager
import com.pdfliteai.telemetry.TelemetryPrefs
import com.pdfliteai.telemetry.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val keyStore = SecureKeyStore(app)

    val aiSettings: StateFlow<AiSettings> =
        repo.aiSettingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings())

    val readerSettings: StateFlow<ReaderSettings> =
        repo.readerSettingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    val recentDocs: StateFlow<List<String>> =
        repo.recentDocsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val telemetryPrefs: StateFlow<TelemetryPrefs> =
        repo.telemetryPrefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, TelemetryPrefs())

    val onboardingDone: StateFlow<Boolean> =
        repo.onboardingDoneFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val userProfile: StateFlow<UserProfile> =
        repo.userProfileFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())

    init {
        // ✅ Avoid main-thread disk work
        viewModelScope.launch(Dispatchers.IO) { repo.ensureInitialized() }
    }

    private fun keyName(p: ProviderId) = when (p) {
        ProviderId.LOCAL_OPENAI_COMPAT -> "key_local_compat"
        ProviderId.GROQ -> "key_groq"
        ProviderId.OPENROUTER -> "key_openrouter"
        ProviderId.NOVA -> "key_nova"
    }

    fun getApiKey(provider: ProviderId): String {
        return when (provider) {
            ProviderId.LOCAL_OPENAI_COMPAT -> keyStore.get(keyName(provider)).trim()
            ProviderId.GROQ, ProviderId.OPENROUTER, ProviderId.NOVA -> ""
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

    fun completeOnboardingGoogle(
        name: String,
        email: String,
        gender: String? = null,
        city: String? = null,
        state: String? = null
    ) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repo.setGoogleProfile(name, email, gender, city, state)
            repo.setOnboardingDone(true)
        }
        TelemetryManager.syncProfileNow()
    }

    fun completeOnboardingManual(
        name: String,
        email: String,
        gender: String,
        city: String,
        state: String
    ) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repo.setManualProfile(name, email, gender, city, state)
            repo.setOnboardingDone(true)
        }
        TelemetryManager.syncProfileNow()
    }
}