package com.pdfliteai.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pdfliteai.billing.PremiumGates
import com.pdfliteai.billing.PremiumState
import com.pdfliteai.data.ProviderId
import com.pdfliteai.telemetry.TelemetryPrefs
import com.pdfliteai.telemetry.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "pdflite_settings")

class SettingsRepository(private val context: Context) {

    private val KEY_PROVIDER = stringPreferencesKey("ai_provider")
    private val KEY_MODEL = stringPreferencesKey("ai_model")
    private val KEY_BASEURL = stringPreferencesKey("ai_baseurl")
    private val KEY_TEMP = floatPreferencesKey("ai_temperature")

    private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val KEY_RECENTS = stringPreferencesKey("recent_uris")
    private val KEY_RECENTS_LIMIT = intPreferencesKey("recents_limit")

    private val KEY_AUTO_OPEN_AI = booleanPreferencesKey("auto_open_ai")
    private val KEY_DEFAULT_ENTIRE_DOC = booleanPreferencesKey("default_entire_doc")
    private val KEY_BG_DIM = floatPreferencesKey("bg_dim")

    private val KEY_CHAT_HISTORY_LIMIT = intPreferencesKey("chat_history_limit")
    private val KEY_RECENTS_MIGRATED_V1 = booleanPreferencesKey("recents_migrated_v1")

    // Premium cache
    private val KEY_PREMIUM = booleanPreferencesKey("premium_is_premium")
    private val KEY_PREMIUM_PRODUCT = stringPreferencesKey("premium_active_product")
    private val KEY_PREMIUM_LASTCHECK = longPreferencesKey("premium_last_checked_at")
    private val KEY_PREMIUM_ERROR = stringPreferencesKey("premium_error")

    // per-document chat counts (docId -> count)
    private val KEY_DOC_CHAT_COUNTS = stringPreferencesKey("doc_chat_counts_v1")

    private val RECENT_SEP = "||"
    private fun recentUri(entry: String): String = entry.substringBefore(RECENT_SEP).trim()

    // Identity + onboarding
    private val KEY_USER_ID = stringPreferencesKey("user_id")
    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val KEY_IDENTITY_KEY = stringPreferencesKey("identity_key")

    suspend fun setIdentityKey(v: String) {
        context.dataStore.edit { it[KEY_IDENTITY_KEY] = v.trim() }
    }

    // Mandatory telemetry flags (always true; no UI)
    private val KEY_CONSENT_ANALYTICS = booleanPreferencesKey("consent_analytics")
    private val KEY_CONSENT_CONTENT = booleanPreferencesKey("consent_content")

    // Profile fields
    private val KEY_AUTH_METHOD = stringPreferencesKey("auth_method") // google|manual
    private val KEY_NAME = stringPreferencesKey("profile_name")

    private val KEY_EMAIL_MANUAL = stringPreferencesKey("email_manual")
    private val KEY_EMAIL_GOOGLE = stringPreferencesKey("email_google")
    private val KEY_EMAIL_VERIFIED = booleanPreferencesKey("email_verified")

    private val KEY_GENDER = stringPreferencesKey("gender")
    private val KEY_CITY = stringPreferencesKey("city")
    private val KEY_STATE = stringPreferencesKey("state")

    // ---------- FLOWS ----------

    val premiumStateFlow: Flow<PremiumState> = context.dataStore.data.map { prefs ->
        PremiumState(
            isPremium = prefs[KEY_PREMIUM] ?: false,
            activeProductId = prefs[KEY_PREMIUM_PRODUCT]?.ifBlank { null },
            lastCheckedAt = prefs[KEY_PREMIUM_LASTCHECK] ?: 0L,
            error = prefs[KEY_PREMIUM_ERROR]?.ifBlank { null }
        )
    }

    val aiSettingsFlow: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val provider = runCatching {
            ProviderId.valueOf(prefs[KEY_PROVIDER] ?: ProviderId.NOVA.name)
        }.getOrDefault(ProviderId.NOVA)

        AiSettings(
            provider = provider,
            model = prefs[KEY_MODEL] ?: defaultModel(provider),
            baseUrl = prefs[KEY_BASEURL] ?: "",
            temperature = prefs[KEY_TEMP] ?: 0.2f
        )
    }

    val readerSettingsFlow: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
            recentsLimit = (prefs[KEY_RECENTS_LIMIT] ?: 10).coerceIn(3, PremiumGates.PREMIUM_RECENTS_LIMIT),
            autoOpenAi = prefs[KEY_AUTO_OPEN_AI] ?: true,
            defaultEntireDoc = prefs[KEY_DEFAULT_ENTIRE_DOC] ?: true,
            bgDim = (prefs[KEY_BG_DIM] ?: 0.22f).coerceIn(0f, 0.45f),
            chatHistoryLimit = (prefs[KEY_CHAT_HISTORY_LIMIT] ?: 3).coerceIn(1, 10)
        )
    }

    val recentDocsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val limit = (prefs[KEY_RECENTS_LIMIT] ?: 10).coerceIn(3, PremiumGates.PREMIUM_RECENTS_LIMIT)
        decodeList(prefs[KEY_RECENTS].orEmpty()).take(limit)
    }

    val onboardingDoneFlow: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    val telemetryPrefsFlow: Flow<TelemetryPrefs> = context.dataStore.data.map { prefs ->
        TelemetryPrefs(
            userId = prefs[KEY_USER_ID] ?: "",
            consentAnalytics = prefs[KEY_CONSENT_ANALYTICS] ?: true,
            consentContent = prefs[KEY_CONSENT_CONTENT] ?: true
        )
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            userId = prefs[KEY_USER_ID] ?: "",
            authMethod = prefs[KEY_AUTH_METHOD] ?: "",
            name = prefs[KEY_NAME] ?: "",
            emailGoogle = prefs[KEY_EMAIL_GOOGLE] ?: "",
            emailManual = prefs[KEY_EMAIL_MANUAL] ?: "",
            emailVerified = prefs[KEY_EMAIL_VERIFIED] ?: false,
            identityKey = prefs[KEY_IDENTITY_KEY] ?: "",
            gender = prefs[KEY_GENDER] ?: "",
            city = prefs[KEY_CITY] ?: "",
            state = prefs[KEY_STATE] ?: ""
        )
    }

    // ---------- INIT / MIGRATIONS ----------

    suspend fun ensureInitialized() {
        val prefs = context.dataStore.data.first()

        context.dataStore.edit { p ->
            if (!prefs.contains(KEY_PROVIDER)) {
                p[KEY_PROVIDER] = ProviderId.NOVA.name
                p[KEY_MODEL] = defaultModel(ProviderId.NOVA)
                p[KEY_TEMP] = 0.2f
            }

            if (!prefs.contains(KEY_RECENTS_LIMIT)) p[KEY_RECENTS_LIMIT] = 10
            if (!prefs.contains(KEY_KEEP_SCREEN_ON)) p[KEY_KEEP_SCREEN_ON] = false
            if (!prefs.contains(KEY_AUTO_OPEN_AI)) p[KEY_AUTO_OPEN_AI] = true
            if (!prefs.contains(KEY_DEFAULT_ENTIRE_DOC)) p[KEY_DEFAULT_ENTIRE_DOC] = true
            if (!prefs.contains(KEY_BG_DIM)) p[KEY_BG_DIM] = 0.22f
            if (!prefs.contains(KEY_CHAT_HISTORY_LIMIT)) p[KEY_CHAT_HISTORY_LIMIT] = 3
            if (!prefs.contains(KEY_RECENTS_MIGRATED_V1)) p[KEY_RECENTS_MIGRATED_V1] = false

            if (!prefs.contains(KEY_USER_ID)) p[KEY_USER_ID] = UUID.randomUUID().toString()
            if (!prefs.contains(KEY_ONBOARDING_DONE)) p[KEY_ONBOARDING_DONE] = false

            // premium cache defaults
            if (!prefs.contains(KEY_PREMIUM)) p[KEY_PREMIUM] = false
            if (!prefs.contains(KEY_PREMIUM_LASTCHECK)) p[KEY_PREMIUM_LASTCHECK] = 0L

            // mandatory (always true)
            p[KEY_CONSENT_ANALYTICS] = true
            p[KEY_CONSENT_CONTENT] = true
        }

        val migrated = prefs[KEY_RECENTS_MIGRATED_V1] ?: false
        if (!migrated) {
            runCatching { migrateRecentsToIncludeNames() }
            context.dataStore.edit { p -> p[KEY_RECENTS_MIGRATED_V1] = true }
        }

        // Enforce plan rules at boot using cached premium
        val isPremiumCached = (context.dataStore.data.first()[KEY_PREMIUM] ?: false)
        enforcePlanRules(isPremiumCached)
    }

    // ---------- PREMIUM CACHE + ENFORCEMENT ----------

    suspend fun setPremiumCache(state: PremiumState) {
        context.dataStore.edit { p ->
            p[KEY_PREMIUM] = state.isPremium
            p[KEY_PREMIUM_PRODUCT] = state.activeProductId.orEmpty()
            p[KEY_PREMIUM_LASTCHECK] = state.lastCheckedAt
            p[KEY_PREMIUM_ERROR] = state.error.orEmpty()
        }
    }

    /**
     * Central “source of truth” enforcement.
     * Call when premium changes AND at app start.
     */
    suspend fun enforcePlanRules(isPremium: Boolean) {
        context.dataStore.edit { p ->
            if (!isPremium) {
                // free plan locks:
                p[KEY_PROVIDER] = ProviderId.NOVA.name
                p[KEY_MODEL] = PremiumGates.MODEL_NOVA_MICRO
                p[KEY_TEMP] = PremiumGates.FREE_TEMPERATURE
                p[KEY_RECENTS_LIMIT] = PremiumGates.FREE_RECENTS_LIMIT

                // also trim recents immediately
                val cur = decodeList(p[KEY_RECENTS].orEmpty())
                p[KEY_RECENTS] = encodeList(cur.take(PremiumGates.FREE_RECENTS_LIMIT))
            } else {
                // premium: allow bigger recents (keep user choice if already set)
                val curLimit = (p[KEY_RECENTS_LIMIT] ?: 10).coerceIn(3, PremiumGates.PREMIUM_RECENTS_LIMIT)
                if (curLimit < 10) p[KEY_RECENTS_LIMIT] = 10

                // ✅ If user was previously forced to free micro on NOVA, promote to premium lite
                val provider = runCatching {
                    ProviderId.valueOf(p[KEY_PROVIDER] ?: ProviderId.NOVA.name)
                }.getOrDefault(ProviderId.NOVA)

                val currentModel = p[KEY_MODEL].orEmpty()

                if (provider == ProviderId.NOVA && (
                            currentModel.isBlank() ||
                                    currentModel == PremiumGates.MODEL_NOVA_MICRO ||
                                    currentModel == PremiumGates.NOVA_MICRO_MODEL
                            )
                ) {
                    p[KEY_MODEL] = PremiumGates.MODEL_NOVA_LITE
                }
            }
        }
    }

    // ---------- CHAT COUNTS PER DOC ----------

    suspend fun getDocChatCount(docId: String): Int {
        val key = docId.trim()
        if (key.isBlank()) return 0
        val prefs = context.dataStore.data.first()
        val map = decodeCounts(prefs[KEY_DOC_CHAT_COUNTS].orEmpty())
        return map[key] ?: 0
    }

    /**
     * increments and returns NEW value
     */
    suspend fun incrementDocChatCount(docId: String): Int {
        val key = docId.trim()
        if (key.isBlank()) return 0

        var newVal = 0
        context.dataStore.edit { p ->
            val map = decodeCounts(p[KEY_DOC_CHAT_COUNTS].orEmpty()).toMutableMap()
            val next = (map[key] ?: 0) + 1
            map[key] = next
            p[KEY_DOC_CHAT_COUNTS] = encodeCounts(map)
            newVal = next
        }
        return newVal
    }

    private fun encodeCounts(map: Map<String, Int>): String =
        map.entries.joinToString("\n") { (k, v) -> "${k.replace("\n", "")}\t$v" }.trim()

    private fun decodeCounts(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        raw.split("\n").forEach { line ->
            val parts = line.split("\t")
            if (parts.size >= 2) {
                val k = parts[0].trim()
                val v = parts[1].trim().toIntOrNull()
                if (k.isNotBlank() && v != null) out[k] = v
            }
        }
        return out
    }

    // ---------- PROFILE / TELEMETRY ----------

    suspend fun getTelemetryPrefsOnce(): TelemetryPrefs = telemetryPrefsFlow.first()
    suspend fun getUserProfileOnce(): UserProfile = userProfileFlow.first()

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    private fun normGender(g: String?): String {
        val v = g?.trim().orEmpty()
        return if (v.equals("Prefer not to say", ignoreCase = true)) "" else v
    }

    private fun normOpt(s: String?): String = s?.trim().orEmpty()

    suspend fun setGoogleProfile(
        name: String,
        email: String,
        gender: String? = null,
        city: String? = null,
        state: String? = null
    ) {
        val existing = getUserProfileOnce()

        val newGender = normGender(gender)
        val newCity = normOpt(city)
        val newState = normOpt(state)

        val gFinal = if (newGender.isBlank()) existing.gender else newGender
        val cFinal = if (newCity.isBlank()) existing.city else newCity
        val sFinal = if (newState.isBlank()) existing.state else newState

        context.dataStore.edit {
            it[KEY_AUTH_METHOD] = "google"
            it[KEY_NAME] = name.trim()
            it[KEY_EMAIL_GOOGLE] = email.trim()
            it[KEY_EMAIL_MANUAL] = ""
            it[KEY_EMAIL_VERIFIED] = true
            it[KEY_IDENTITY_KEY] = email.trim().lowercase()

            it[KEY_GENDER] = gFinal
            it[KEY_CITY] = cFinal
            it[KEY_STATE] = sFinal
        }
    }

    suspend fun setManualProfile(
        name: String,
        email: String,
        gender: String,
        city: String,
        state: String
    ) {
        context.dataStore.edit {
            it[KEY_AUTH_METHOD] = "manual"
            it[KEY_NAME] = name.trim()
            it[KEY_EMAIL_MANUAL] = email.trim()
            it[KEY_EMAIL_GOOGLE] = ""
            it[KEY_EMAIL_VERIFIED] = false
            it[KEY_IDENTITY_KEY] = email.trim().lowercase()
            it[KEY_GENDER] = gender.trim()
            it[KEY_CITY] = city.trim()
            it[KEY_STATE] = state.trim()
        }
    }

    // ---------- AI SETTINGS ----------

    private fun defaultModel(p: ProviderId): String = when (p) {
        // Free/default-safe model for non-premium flows
        ProviderId.GROQ -> PremiumGates.MODEL_NOVA_MICRO
        ProviderId.NOVA -> PremiumGates.MODEL_NOVA_LITE
        ProviderId.OPENROUTER -> "openai/gpt-4o-mini"
        ProviderId.LOCAL_OPENAI_COMPAT -> ""
    }

    suspend fun setProvider(p: ProviderId) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER] = p.name
            prefs[KEY_MODEL] = defaultModel(p)
        }
    }

    suspend fun setUserId(newUserId: String) {
        val u = newUserId.trim()
        if (u.isBlank()) return
        context.dataStore.edit { it[KEY_USER_ID] = u }
        android.util.Log.d("Telemetry", "Persisted canonical user_id=$u")
    }

    suspend fun setModel(m: String) {
        context.dataStore.edit { it[KEY_MODEL] = m.trim() }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASEURL] = url.trim() }
    }

    suspend fun setTemperature(t: Float) {
        context.dataStore.edit { it[KEY_TEMP] = t.coerceIn(0f, 1f) }
    }

    // ---------- READER SETTINGS ----------

    suspend fun setKeepScreenOn(on: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = on }
    }

    suspend fun setRecentsLimit(limit: Int) {
        context.dataStore.edit {
            it[KEY_RECENTS_LIMIT] = limit.coerceIn(3, PremiumGates.PREMIUM_RECENTS_LIMIT)
        }
    }

    suspend fun setAutoOpenAi(on: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_OPEN_AI] = on }
    }

    suspend fun setDefaultEntireDoc(on: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_ENTIRE_DOC] = on }
    }

    suspend fun setBgDim(v: Float) {
        context.dataStore.edit { it[KEY_BG_DIM] = v.coerceIn(0f, 0.45f) }
    }

    suspend fun setChatHistoryLimit(v: Int) {
        context.dataStore.edit { it[KEY_CHAT_HISTORY_LIMIT] = v.coerceIn(1, 10) }
    }

    suspend fun addRecent(uri: String) {
        val u = uri.trim()
        if (u.isBlank()) return

        context.dataStore.edit { prefs ->
            val limit = (prefs[KEY_RECENTS_LIMIT] ?: 10).coerceIn(3, PremiumGates.PREMIUM_RECENTS_LIMIT)
            val cur = decodeList(prefs[KEY_RECENTS].orEmpty())
            val uUri = recentUri(u)

            val next = buildList {
                add(u)
                cur.filterNot { recentUri(it) == uUri }.forEach { add(it) }
            }.take(limit)

            prefs[KEY_RECENTS] = encodeList(next)
        }
    }

    suspend fun clearRecents() {
        context.dataStore.edit { it.remove(KEY_RECENTS) }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.trim() else null
                }
        }.getOrNull().orEmpty().trim().ifBlank { null }
    }

    private suspend fun migrateRecentsToIncludeNames() {
        val prefs = context.dataStore.data.first()
        val cur = decodeList(prefs[KEY_RECENTS].orEmpty())
        if (cur.isEmpty()) return

        val migrated = buildList {
            for (entry in cur) {
                val uriStr = recentUri(entry)
                if (uriStr.isBlank()) continue

                val hasName = entry.contains(RECENT_SEP) && entry.substringAfter(RECENT_SEP).trim().isNotBlank()
                if (hasName) {
                    add(entry)
                    continue
                }

                val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
                if (uri == null) {
                    add(uriStr)
                    continue
                }

                val persistedRead = runCatching {
                    context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                }.getOrDefault(false)

                val name = if (persistedRead) queryDisplayName(uri) else null
                add(if (name != null) "$uriStr$RECENT_SEP$name" else uriStr)
            }
        }

        context.dataStore.edit { p -> p[KEY_RECENTS] = encodeList(migrated) }
    }

    private fun encodeList(items: List<String>): String =
        items.joinToString("\n") { it.replace("\n", "").trim() }.trim()

    private fun decodeList(raw: String): List<String> =
        raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
}