package com.budgetpace.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest SharedPreferences for identity-adjacent data (session email, whether Sheets
 * authorization was granted) — never for anything as sensitive as a live access/ID token, which
 * shouldn't be persisted at all regardless of encryption.
 *
 * Falls back to plain SharedPreferences if the Android Keystore is unavailable (rare, but seen
 * across some OS upgrades) — losing the "encrypted" property for that one restart is far better
 * than crashing the app at every single launch because a singleton's field initializer threw.
 */
fun securePrefs(context: Context, fileName: String): SharedPreferences {
    return try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.e("SecurePrefs", "Falling back to unencrypted prefs for $fileName: ${e.message}", e)
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }
}
