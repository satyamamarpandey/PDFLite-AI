package com.pdfliteai.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray

object TelemetryManager {

    private const val TAG = "Telemetry"
    private val started = AtomicBoolean(false)

    private val uploadedDocTextKeys = HashSet<String>() // session dedupe
    private const val MAX_DOC_TEXT_CHARS = 350_000
    private const val MAX_CHAT_JSON_CHARS = 400_000

    // Lint warns about "static Context" because this is an object singleton.
    // We only store applicationContext, which is safe.
    @SuppressLint("StaticFieldLeak")
    private lateinit var appContext: Context

    private lateinit var cfg: TelemetryConfig
    private lateinit var client: TelemetryClient

    // Also flagged by lint; safe because TelemetryStore stores applicationContext
    @SuppressLint("StaticFieldLeak")
    private lateinit var store: TelemetryStore

    private var userIdProvider: (suspend () -> String) = { "" }
    private var profileProvider: (suspend () -> UserProfile) = { UserProfile() }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(
        context: Context,
        config: TelemetryConfig,
        userIdProvider: suspend () -> String,
        profileProvider: suspend () -> UserProfile
    ) {
        if (started.getAndSet(true)) return

        appContext = context.applicationContext
        cfg = config
        client = TelemetryClient(cfg)
        store = TelemetryStore(appContext)

        this.userIdProvider = userIdProvider
        this.profileProvider = profileProvider

        Log.d(
            TAG,
            "TelemetryManager.init baseUrl=${cfg.baseUrl} tokenPresent=${!cfg.authToken.isNullOrBlank()} appVersion=${cfg.appVersion}"
        )
    }

    fun log(name: String, params: Map<String, String> = emptyMap()) {
        if (!started.get()) return

        ioScope.launch {
            val uid = runCatching { userIdProvider() }.getOrDefault("")
            if (uid.isBlank()) return@launch

            val e = TelemetryEvent(
                ts = System.currentTimeMillis(),
                name = name,
                userId = uid,
                params = params
            )
            store.appendEvent(e)
        }
    }

    fun syncProfileNow() {
        if (!started.get()) return

        ioScope.launch {
            val profile = runCatching { profileProvider() }.getOrDefault(UserProfile())
            if (profile.userId.isBlank()) return@launch

            // ✅ Worker expects snake_case keys
            val tzOffsetMin = TimeZone.getDefault().rawOffset / 60000
            val locale = runCatching {
                appContext.resources.configuration.locales[0].toLanguageTag()
            }.getOrDefault("")

            fun putOpt(o: JSONObject, k: String, v: String) {
                if (v.isBlank()) o.put(k, JSONObject.NULL) else o.put(k, v)
            }

            val o = JSONObject()

            // ✅ REQUIRED by Worker
            o.put("user_id", profile.userId)

            // ✅ Only include fields that exist in YOUR UserProfile model
            putOpt(o, "auth_method", profile.authMethod)
            putOpt(o, "name", profile.name)
            putOpt(o, "gender", profile.gender)
            putOpt(o, "city", profile.city)
            putOpt(o, "state", profile.state)

            putOpt(o, "email_google", profile.emailGoogle)
            putOpt(o, "email_manual", profile.emailManual)
            o.put("email_verified", profile.emailVerified)

            if (locale.isBlank()) o.put("locale", JSONObject.NULL) else o.put("locale", locale)
            o.put("tz_offset_min", tzOffsetMin)
            putOpt(o, "app_version", cfg.appVersion)

            Log.d(
                TAG,
                "syncProfileNow -> userId=${profile.userId} auth_method=${profile.authMethod} email_manual=${profile.emailManual} email_google=${profile.emailGoogle}"
            )

            val (code, resp) = runCatching {
                client.postProfile(profile.userId, o.toString())
            }.getOrElse {
                Log.d(TAG, "syncProfileNow <- failed: ${it.message ?: it::class.java.simpleName}")
                return@launch
            }

            Log.d(TAG, "syncProfileNow <- code=$code resp=$resp")
        }
    }

    fun uploadDocTextNow(docId: String, kind: String, rawText: String) {
        if (!started.get()) return

        ioScope.launch {
            val uid = runCatching { userIdProvider() }.getOrDefault("")
            if (uid.isBlank()) return@launch

            val d = docId.trim()
            if (d.isBlank()) return@launch

            val k = kind.trim().ifBlank { "extracted" }

            val text = rawText.trim().take(MAX_DOC_TEXT_CHARS)
            if (text.isBlank()) return@launch

            // avoid uploading same (docId + kind) repeatedly in one app session
            val dedupeKey = "$d|$k|${text.length}"
            if (!uploadedDocTextKeys.add(dedupeKey)) return@launch

            val gz = Gzip.gzip(text.toByteArray(Charsets.UTF_8))
            val charCount = text.length

            val (code, resp) = runCatching {
                client.postDocTextGz(
                    userId = uid,
                    docId = d,
                    kind = k,
                    charCount = charCount,
                    gzBytes = gz
                )
            }.getOrElse {
                Log.d(TAG, "uploadDocTextNow <- failed: ${it.message ?: it::class.java.simpleName}")
                return@launch
            }

            Log.d(TAG, "uploadDocTextNow <- code=$code resp=$resp")
        }
    }

    /**
     * Pass a pre-built JSON string (so telemetry module doesn't depend on UI ChatMessage types).
     * This uploads ONE chat snapshot for (userId + docId + chatId) and overwrites the same R2 key.
     */
    fun uploadChatJsonNow(docId: String, chatId: String, model: String?, json: String) {
        if (!started.get()) return

        ioScope.launch {
            val uid = runCatching { userIdProvider() }.getOrDefault("")
            if (uid.isBlank()) return@launch

            val d = docId.trim()
            val c = chatId.trim()
            if (d.isBlank() || c.isBlank()) return@launch

            val body = json.trim().take(MAX_CHAT_JSON_CHARS)
            if (body.isBlank()) return@launch

            val gz = Gzip.gzip(body.toByteArray(Charsets.UTF_8))

            val (code, resp) = runCatching {
                client.postChatGz(
                    userId = uid,
                    docId = d,
                    chatId = c,
                    model = model,
                    gzBytes = gz
                )
            }.getOrElse {
                Log.d(TAG, "uploadChatJsonNow <- failed: ${it.message ?: it::class.java.simpleName}")
                return@launch
            }

            Log.d(TAG, "uploadChatJsonNow <- code=$code resp=$resp")
        }
    }

    suspend fun flushOnce(): Boolean {
        if (!started.get()) return false

        val uid = runCatching { userIdProvider() }.getOrDefault("")
        if (uid.isBlank()) return false

        val batch = store.peekBatch() ?: return true

        val gz = Gzip.gzip(batch.jsonl.toByteArray(Charsets.UTF_8))
        val (code, _) = runCatching {
            client.postEventsBatchGz(uid, batch.batchId, batch.count, gz)
        }.getOrElse { return false }

        return if (code in 200..299) {
            store.commitBatch(batch.remainingJsonl)
            true
        } else {
            false
        }
    }

    suspend fun flushNow(maxAttempts: Int = 10): Boolean {
        if (!started.get()) return false

        var attempts = 0
        while (attempts < maxAttempts) {
            attempts++

            val ok = flushOnce()
            if (!ok) return false

            val hasMore = runCatching { store.peekBatch() != null }.getOrDefault(false)
            if (!hasMore) return true
        }
        return true
    }
}