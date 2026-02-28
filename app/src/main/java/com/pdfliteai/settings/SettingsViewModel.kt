package com.pdfliteai.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfliteai.data.ProviderId
import com.pdfliteai.storage.SecureKeyStore
import com.pdfliteai.telemetry.TelemetryManager
import com.pdfliteai.telemetry.TelemetryPrefs
import com.pdfliteai.telemetry.UserProfile
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

    // ✅ New
    val telemetryPrefs: StateFlow<TelemetryPrefs> =
        repo.telemetryPrefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, TelemetryPrefs())

    val onboardingDone: StateFlow<Boolean> =
        repo.onboardingDoneFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val userProfile: StateFlow<UserProfile> =
        repo.userProfileFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())

    init {
        viewModelScope.launch { repo.ensureInitialized() }
    }

    private fun keyName(p: ProviderId) = when (p) {
        ProviderId.LOCAL_OPENAI_COMPAT -> "key_local_compat"
        ProviderId.GROQ -> "key_groq"             // not used anymore
        ProviderId.OPENROUTER -> "key_openrouter" // not used anymore
        ProviderId.NOVA -> "key_nova"             // not used anymore
    }

    /**
     * ✅ Cloudflare mode:
     * - Nova/OpenRouter keys are NOT stored on-device.
     * - Worker holds provider keys.
     *
     * Local OpenAI Compat can still use a user-provided key (stored securely).
     */
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

    // ---- AI settings ----
    fun setProvider(p: ProviderId) = viewModelScope.launch { repo.setProvider(p) }
    fun setModel(m: String) = viewModelScope.launch { repo.setModel(m) }
    fun setBaseUrl(url: String) = viewModelScope.launch { repo.setBaseUrl(url) }
    fun setTemperature(t: Float) = viewModelScope.launch { repo.setTemperature(t) }

    // ---- Reader settings ----
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(on) }
    fun setRecentsLimit(limit: Int) = viewModelScope.launch { repo.setRecentsLimit(limit) }
    fun addRecent(uri: String) = viewModelScope.launch { repo.addRecent(uri) }
    fun clearRecents() = viewModelScope.launch { repo.clearRecents() }

    fun setAutoOpenAi(on: Boolean) = viewModelScope.launch { repo.setAutoOpenAi(on) }
    fun setDefaultEntireDoc(on: Boolean) = viewModelScope.launch { repo.setDefaultEntireDoc(on) }
    fun setBgDim(v: Float) = viewModelScope.launch { repo.setBgDim(v) }
    fun setChatHistoryLimit(v: Int) = viewModelScope.launch { repo.setChatHistoryLimit(v) }

    // ---- Onboarding completion (MANDATORY telemetry, no toggles) ----
    fun completeOnboardingGoogle(name: String, email: String) = viewModelScope.launch {
        repo.setGoogleProfile(name, email)
        repo.setOnboardingDone(true)
        TelemetryManager.syncProfileNow()
    }

    fun completeOnboardingManual(
        name: String,
        email: String,
        gender: String,
        city: String,
        state: String
    ) = viewModelScope.launch {
        repo.setManualProfile(name, email, gender, city, state)
        repo.setOnboardingDone(true)
        TelemetryManager.syncProfileNow()
    }
}