package com.pdfliteai.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun put(keyName: String, value: String) {
        prefs.edit().putString(keyName, value.trim()).apply()
    }

    fun get(keyName: String): String = prefs.getString(keyName, "") ?: ""

    fun clear(keyName: String) {
        prefs.edit().remove(keyName).apply()
    }
}
