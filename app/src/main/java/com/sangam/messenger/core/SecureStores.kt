package com.sangam.messenger.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device encrypted storage. Everything here lives only on this phone,
 * protected by the Android Keystore.
 */
object SecureStores {

    // Building a MasterKey and opening EncryptedSharedPreferences is a
    // Keystore round trip, and these are called in tight loops. Keep one of
    // each for the life of the process.
    @Volatile private var secureCache: SharedPreferences? = null
    @Volatile private var outboxCache: SharedPreferences? = null

    /** Credentials + Signal protocol state (identity, sessions, pre-keys). */
    fun secure(context: Context): SharedPreferences =
        secureCache ?: synchronized(this) {
            secureCache ?: build(context, "sangam_secure").also { secureCache = it }
        }

    /** Local cache of our own outgoing message bodies (so we can show history). */
    fun outbox(context: Context): SharedPreferences =
        outboxCache ?: synchronized(this) {
            outboxCache ?: build(context, "sangam_outbox").also { outboxCache = it }
        }

    private fun build(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
