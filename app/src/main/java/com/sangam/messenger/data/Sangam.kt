package com.sangam.messenger.data

import android.content.Context
import com.sangam.messenger.core.CryptoManager
import com.sangam.messenger.core.SignalStore
import com.sangam.messenger.core.ImageCrypto
import com.sangam.messenger.core.SecureStores
import com.sangam.messenger.BuildConfig
import com.sangam.messenger.data.Models.Envelope
import com.sangam.messenger.data.Models.DeviceRow
import com.sangam.messenger.data.Models.GroupMemberRow
import com.sangam.messenger.data.Models.GroupRow
import com.sangam.messenger.data.Models.MessageRow
import com.sangam.messenger.data.Models.Profile
import com.sangam.messenger.push.Push
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Central service layer. Talks to Supabase (accounts, ciphertext relay,
 * encrypted image blobs) and hands plaintext only to/from the local
 * Signal-protocol layer.
 */
object Sangam {

    private const val IMAGE_BUCKET = "message-images"

    private lateinit var appContext: Context

    @Volatile
    var crypto: CryptoManager? = null
        private set

    val client get() = Supabase.client
    val configured get() = Supabase.isConfigured

    /** For background key uploads - they must never block app startup. */
    private val keyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        installCrashLogger()
        startSessionKeeper()
    }

    /** Keep the last crash on disk so it can be shown after the next start. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                java.io.File(appContext.filesDir, "sangam_crash.log").writeText(
                    "Time: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date()) + "\nThread: " + t.name + "\n\n" +
                        android.util.Log.getStackTraceString(e)
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** The last crash report, if any; showing it clears it. */
    fun takeCrashReport(): String? = runCatching {
        val f = java.io.File(appContext.filesDir, "sangam_crash.log")
        if (!f.exists()) null else f.readText().also { f.delete() }
    }.getOrNull()

    /**
     * The server replaces the login token about once an hour while the app
     * is open. We keep saving the fresh one, so restarting the app never
     * lands on the login screen with a stale session.
     */
    private fun startSessionKeeper() {
        keyScope.launch {
            var lastSaved: String? = null
            while (isActive) {
                client.auth.currentSessionOrNull()?.let { session ->
                    runCatching {
                        val json = Json.encodeToString(UserSession.serializer(), session)
                        if (json != lastSaved) {
                            SecureStores.secure(appContext).edit()
                                .putString("login_session", json)
                                .apply()
                            lastSaved = json
                        }
                    }
                }
                delay(10_000)
            }
        }
    }

    fun userId(): String? =
        runCatching { client.auth.currentSessionOrNull()?.user?.id }
            .getOrNull()
            ?.also { prefs("sangam_me")?.edit()?.putString("uid", it)?.apply() }

    /** The last known user id, kept so Nearby works with no network. */
    fun cachedUid(): String? = prefs("sangam_me")?.getString("uid", null)

    // ------------------------------------------------------------------ auth

    /** The address email links come back to, so they open the app itself. */
    const val AUTH_REDIRECT = "sangam://auth-callback"

    /**
     * Finish signing in from a confirmation or reset link. Supabase sends the
     * session either in the link's fragment (access_token) or as a code.
     */
    suspend fun adoptAuthLink(uri: android.net.Uri): Result<Boolean> = runCatching {
        val frag = uri.fragment ?: ""
        val params = frag.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }.toMap()
        val access = params["access_token"]
        val refresh = params["refresh_token"]
        val recovery = params["type"] == "recovery" ||
            uri.getQueryParameter("type") == "recovery"
        val tokenHash = uri.getQueryParameter("token_hash") ?: params["token_hash"]
        when {
            access != null && refresh != null -> {
                client.auth.importAuthToken(access, refresh)
            }
            tokenHash != null -> {
                // Newer email links carry a token hash instead of tokens.
                client.auth.verifyEmailOtp(
                    type = if (recovery) OtpType.Email.RECOVERY else OtpType.Email.SIGNUP,
                    tokenHash = tokenHash
                )
            }
            uri.getQueryParameter("code") != null -> {
                client.auth.exchangeCodeForSession(uri.getQueryParameter("code")!!)
            }
            else -> throw IllegalStateException("That link did not carry a sign-in token.")
        }
        // Save the session we just obtained. Calling restoreSession() here
        // would re-import whatever old session was on disk and overwrite it.
        adoptSession()
        recovery
    }

    /** Write the name and handle chosen at sign-up, now that the account exists. */
    private suspend fun writePendingProfile() {
        val me = userId() ?: return
        val store = prefs("sangam_auth") ?: return
        val username = store.getString("pending_username", null) ?: return
        val name = store.getString("pending_display_name", null) ?: return
        runCatching {
            client.from("profiles").upsert(
                Profile(id = me, username = username, display_name = name),
                onConflict = "id"
            )
        }.onSuccess {
            store.edit()
                .remove("pending_username")
                .remove("pending_display_name")
                .apply()
        }
    }

    /** Keep the session that is active right now, and get the app ready. */
    private suspend fun adoptSession() {
        val session = client.auth.currentSessionOrNull()
            ?: throw IllegalStateException("No session after the link")
        SecureStores.secure(appContext).edit()
            .putString(
                "login_session",
                Json.encodeToString(UserSession.serializer(), session)
            )
            .apply()
        userId()?.let { guardCaches(it) }
        writePendingProfile()
        // Keep the email too, so the fallback path has something to use.
        runCatching {
            client.auth.currentUserOrNull()?.email?.let { e ->
                SecureStores.secure(appContext).edit().putString("login_email", e).apply()
            }
        }
        if (crypto == null) crypto = CryptoManager(appContext)
        keyScope.launch {
            runCatching { registerDevice() }
            runCatching { crypto?.publishKeys() }
            runCatching { crypto?.refillOneTimePreKeys() }
        }
    }

    /**
     * Confirm an email with the six-digit code from the email - no link and
     * no browser needed, so it works even when the link points at localhost.
     */
    suspend fun verifyEmailCode(
        email: String,
        code: String,
        recovery: Boolean
    ): Result<Unit> = runCatching {
        client.auth.verifyEmailOtp(
            type = if (recovery) OtpType.Email.RECOVERY else OtpType.Email.SIGNUP,
            email = email.trim(),
            token = code.trim()
        )
        if (!recovery) adoptSession()
    }

    /** Ask Supabase to send the confirmation / reset email again. */
    suspend fun resendEmail(email: String, recovery: Boolean): Result<Unit> = runCatching {
        if (recovery) {
            // The resend endpoint does not accept RECOVERY - ask Supabase for
            // a fresh reset email instead.
            client.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_REDIRECT)
        } else {
            client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
        }
    }

    /** Set a new password after following a reset link. */
    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        client.auth.updateUser { password = newPassword }
        // The saved fallback password is now wrong. Drop it instead of letting
        // the silent re-login retry the old one forever.
        SecureStores.secure(appContext).edit().remove("login_password").apply()
    }

    /**
     * Remember that a reset was just asked for. When a link comes back and we
     * sent a reset in the last hour, it is a reset - whatever the link says.
     */
    fun markResetPending() {
        prefs("sangam_auth")?.edit()
            ?.putLong("reset_pending", System.currentTimeMillis())
            ?.apply()
    }

    fun resetPending(): Boolean {
        val at = prefs("sangam_auth")?.getLong("reset_pending", 0L) ?: 0L
        return at > 0L && System.currentTimeMillis() - at < 60L * 60L * 1000L
    }

    fun clearResetPending() {
        prefs("sangam_auth")?.edit()?.remove("reset_pending")?.apply()
    }

    /** Email a password-reset link. Supabase Auth sends it. */
    suspend fun resetPassword(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_REDIRECT)
        markResetPending()
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        onAuthenticated(email, password)
    }

    /**
     * Sign up. If the Supabase project requires email confirmation, no session
     * is established yet and the caller should tell the user to confirm first.
     */
    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String
    ): Result<Unit> = runCatching {
        // Send the confirmation link back to the app, not to localhost.
        client.auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT) {
            this.email = email
            this.password = password
        }
        val me = userId()
        if (me == null) {
            // No session yet, so no profile row. Remember what they chose and
            // write it once the account is confirmed - otherwise every new
            // account stays "Sangam friend" forever.
            prefs("sangam_auth")?.edit()
                ?.putString("pending_username", username.lowercase())
                ?.putString("pending_display_name", displayName)
                ?.apply()
            throw SignupNeedsConfirmation(
                "Almost there — confirm the email in your inbox, then log in."
            )
        }
        client.from("profiles").upsert(
            Profile(id = me, username = username.lowercase(), display_name = displayName),
            onConflict = "id"
        )
        onAuthenticated(email, password)
    }

    class SignupNeedsConfirmation(message: String) : Exception(message)

    // ------------------------------------------------ cache ownership

    /** Which account the local caches belong to. */
    private fun cacheOwner(): String? = prefs("sangam_cache")?.getString("owner", null)

    private fun setCacheOwner(uid: String) {
        prefs("sangam_cache")?.edit()?.putString("owner", uid)?.apply()
    }

    /**
     * Caches belong to one account. If a different account signs in on this
     * phone, the previous one's chats are cleared; if it is the same account
     * coming back, everything is kept - so a sign-out no longer costs you
     * your history.
     */
    private fun guardCaches(uid: String) {
        val owner = cacheOwner()
        if (owner != null && owner != uid) wipeLocalData()
        setCacheOwner(uid)
    }

    private suspend fun onAuthenticated(email: String, password: String) {
        userId()?.let { guardCaches(it) }
        writePendingProfile()
        if (crypto == null) crypto = CryptoManager(appContext)
        // Remember credentials + session (device-local, encrypted) so cold
        // starts are instant instead of a full login round trip.
        val editor = SecureStores.secure(appContext).edit()
            .putString("login_email", email)
            .putString("login_password", password)
        client.auth.currentSessionOrNull()?.let { session ->
            runCatching {
                editor.putString("login_session", Json.encodeToString(UserSession.serializer(), session))
            }
        }
        editor.apply()
        // Register this install's device id & per-device bundle first, then
        // publish primary prekey_bundles only if this is device #1.
        keyScope.launch {
            runCatching { registerDevice() }
            runCatching { crypto?.publishKeys() }
            runCatching { crypto?.refillOneTimePreKeys() }
        }
    }

    suspend fun restoreSession(): Boolean {
        val prefs = SecureStores.secure(appContext)
        // Decide from the saved data alone - starting the app NEVER touches
        // the network, so airplane mode can neither slow it down nor break it.
        val savedSession = prefs.getString("login_session", null)
        val parsed = savedSession?.let {
            runCatching { Json.decodeFromString(UserSession.serializer(), it) }.getOrNull()
        }
        // The saved expiry as milliseconds since the epoch.
        val expMillis = parsed?.expiresAt?.toEpochMilliseconds()
        // Import the saved session even when its access token has expired -
        // the library refreshes it with the saved refresh token. Requiring a
        // live token here is what logged people out about an hour after they
        // signed in through an email or reset link, because that path never
        // stores a password to fall back on.
        if (parsed != null) {
            val ok = runCatching {
                client.auth.importSession(parsed)
                client.auth.currentSessionOrNull() != null
            }.getOrDefault(false)
            if (ok) {
                userId()?.let { guardCaches(it) }
                if (crypto == null) crypto = CryptoManager(appContext)
                keyScope.launch {
                    // Register this phone's deviceKey first so secondary phones don't overwrite device #1's prekey_bundles.
                    runCatching { registerDevice() }
                    runCatching { crypto?.publishKeys() }
                    runCatching { crypto?.refillOneTimePreKeys() }
                    runCatching { backfillEmail() }
                }
                return true
            }
        }
        val email = prefs.getString("login_email", null)
        val password = prefs.getString("login_password", null)
        if (email == null || password == null) return false
        // Expired or unrestored session: trust the saved login and open from
        // the local caches. Sign in quietly in the background - straight away
        // when online, retried every 15 s until the network returns.
        if (crypto == null) crypto = CryptoManager(appContext)
        keyScope.launch {
            for (i in 1..60) {
                if (client.auth.currentSessionOrNull() != null) return@launch
                if (runCatching { signIn(email, password) }.isSuccess) return@launch
                delay(15_000)
            }
        }
        return true
    }

    fun savedCredentials(): Pair<String, String>? {
        val prefs = SecureStores.secure(appContext)
        val email = prefs.getString("login_email", null) ?: return null
        val password = prefs.getString("login_password", null) ?: return null
        return email to password
    }

    /** Compact QR payload so a second phone can scan and log in immediately. */
    fun deviceLoginQrPayload(usernameHint: String): String {
        val u = usernameHint.trim().ifBlank { nearbyUsername() }
        val creds = savedCredentials()
        if (creds != null && creds.second.isNotBlank()) {
            val pwB64 = android.util.Base64.encodeToString(
                creds.second.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
            )
            val candidate = "sangam://login?u=" + u + "&p=" + pwB64
            if (candidate.toByteArray(Charsets.UTF_8).size <= 78) return candidate
        }
        return "sangam://login?u=" + u
    }

    fun desktopMode(): Boolean =
        prefs("sangam_ui")?.getBoolean("desktop_mode", false) ?: false

    fun setDesktopMode(enabled: Boolean) {
        prefs("sangam_ui")?.edit()?.putBoolean("desktop_mode", enabled)?.apply()
        com.sangam.messenger.Ui.desktopMode = enabled
    }

    /** 256-bit key stored in Android Keystore-backed prefs for AES-256-GCM history sync with paired desktop. */
    fun desktopSyncKey(): String {
        val prefs = SecureStores.secure(appContext)
        prefs.getString("desktop_sync_key", null)?.takeIf { it.isNotBlank() }?.let { return it }
        val raw = ByteArray(24)
        java.security.SecureRandom().nextBytes(raw)
        val k = android.util.Base64.encodeToString(
            raw,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        prefs.edit().putString("desktop_sync_key", k).apply()
        return k
    }

    /**
     * Syncs local Outbox history encrypted with AES-256-GCM (`backupEncrypt` with `desktopSyncKey()`)
     * so paired Windows / companion devices can display historical messages without exposing
     * any plaintext to the server.
     */
    suspend fun syncDesktopOutbox(): Boolean = runCatching {
        val me = userId() ?: return false
        val dump = Outbox.dumpAll()
        if (dump.isEmpty()) return true
        val envObj = org.json.JSONObject()
        for ((k, v) in dump) envObj.put(k, v)
        val rootObj = org.json.JSONObject().apply {
            put("envelopes", envObj)
        }
        val plainBytes = rootObj.toString().toByteArray(Charsets.UTF_8)
        val encrypted = backupEncrypt(plainBytes, desktopSyncKey())
        client.storage.from("avatars").upload("outbox_$me.bin", encrypted, upsert = true)
        runCatching { client.storage.from("avatars").delete(listOf("outbox_$me.json")) }
        true
    }.getOrDefault(false)

    /**
     * Called on the mobile phone when its QR scanner reads a `sangam://desktop?id=...&k=...`
     * QR code displayed on a Desktop login screen. Encrypts the pairing payload with the
     * ephemeral `desktopKey` from the QR code (PBKDF2-HMAC-SHA256 + AES-256-GCM) so the
     * server never sees plaintext credentials, tokens, or message history.
     */
    suspend fun approveDesktopPairing(code: String, desktopKey: String? = null): Boolean = runCatching {
        val clean = code.trim()
        val ephKey = desktopKey?.trim() ?: ""
        if (clean.isBlank()) return false
        val creds = savedCredentials()
        val session = client.auth.currentSessionOrNull()
        val pwB64 = if (creds != null && creds.second.isNotBlank()) {
            android.util.Base64.encodeToString(
                creds.second.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
        } else ""
        val emailStr = creds?.first?.ifBlank { null } ?: session?.user?.email ?: ""
        val row = when {
            pwB64.isNotBlank() && emailStr.isNotBlank() -> {
                Models.DesktopPairingRow(
                    code = clean,
                    email = emailStr,
                    token = "pw:" + pwB64
                )
            }
            session != null && session.refreshToken.isNotBlank() -> {
                Models.DesktopPairingRow(
                    code = clean,
                    email = emailStr,
                    token = "rt:" + session.refreshToken + "|" + session.accessToken
                )
            }
            else -> return false
        }
        val outboxObj = org.json.JSONObject()
        for ((k, v) in Outbox.dumpAll()) outboxObj.put(k, v)
        val payloadBytes = org.json.JSONObject().apply {
            put("code", row.code)
            put("email", row.email ?: "")
            put("token", row.token ?: "")
            if (pwB64.isNotBlank()) put("pw", pwB64)
            if (session != null) {
                put("refreshToken", session.refreshToken)
                put("accessToken", session.accessToken)
            }
            put("syncKey", desktopSyncKey())
            put("outbox", outboxObj)
        }.toString().toByteArray(Charsets.UTF_8)

        val storageOk = if (ephKey.length >= 16) {
            val encryptedBytes = backupEncrypt(payloadBytes, ephKey)
            runCatching {
                client.storage.from("avatars").upload("pair_$clean.bin", encryptedBytes, upsert = true)
                runCatching { client.storage.from("avatars").delete(listOf("pair_$clean.json")) }
                true
            }.getOrDefault(false)
        } else {
            runCatching {
                client.storage.from("avatars").upload("pair_$clean.json", payloadBytes, upsert = true)
                true
            }.getOrDefault(false)
        }
        val dbOk = runCatching {
            client.from("desktop_pairings").upsert(row, onConflict = "code")
            true
        }.getOrDefault(false)

        runCatching { syncDesktopOutbox() }
        storageOk || dbOk
    }.getOrDefault(false)

    /**
     * Polled by the Desktop login screen while showing its QR code. Returns true
     * and signs the Desktop in as soon as the mobile app scans the QR code.
     */
    suspend fun pollDesktopPairing(code: String): Boolean = runCatching {
        val clean = code.trim()
        val row = client.from("desktop_pairings").select(Columns.ALL) {
            filter { eq("code", clean) }
            limit(1)
        }.decodeList<Models.DesktopPairingRow>().firstOrNull() ?: return false
        val tok = row.token ?: return false
        runCatching {
            client.from("desktop_pairings").delete { filter { eq("code", clean) } }
        }
        when {
            tok.startsWith("pw:") -> {
                val email = row.email?.takeIf { it.isNotBlank() } ?: return false
                val pw = String(
                    android.util.Base64.decode(tok.removePrefix("pw:"), android.util.Base64.NO_WRAP),
                    Charsets.UTF_8
                )
                signIn(email, pw).isSuccess
            }
            tok.startsWith("rt:") -> {
                val parts = tok.removePrefix("rt:").split("|", limit = 2)
                if (parts.size < 2) return false
                client.auth.importAuthToken(parts[1], parts[0])
                adoptSession()
                true
            }
            else -> false
        }
    }.getOrDefault(false)

    /**
     * Sender name and a short "what it is" line for a notification - a photo,
     * a video, a voice note, a document, a time capsule or the text itself.
     */
    suspend fun previewFor(peerId: String): Pair<String, String>? = runCatching {
        val me = userId() ?: return@runCatching null
        val inc = incomingCall()
        if (inc?.caller == peerId) {
            val callerName = runCatching { profileOf(peerId)?.display_name }.getOrNull()
                ?: "Sangam friend"
            val label = if (inc.kind == "video") "\uD83C\uDFA5  Incoming video call"
            else "\uD83D\uDCDE  Incoming voice call"
            return@runCatching callerName to label
        }
        val rows = client.from("messages").select(Columns.ALL) {
            filter {
                eq("sender_id", peerId)
                eq("recipient_id", me)
            }
            order("created_at", Order.DESCENDING)
            limit(5)
        }.decodeList<MessageRow>().filter { it.deleted != true }
        val row = rows.maxByOrNull { parseTime(it.created_at) } ?: return@runCatching null
        val msgAge = System.currentTimeMillis() - parseTime(row.created_at)
        if (row.read_at != null || msgAge > 180_000L) return@runCatching null
        val plaintext = Outbox.lookup(row.id) ?: run {
            val c = crypto ?: return@runCatching null
            String(c.decryptForDevice(peerId, row.ciphertext, false)).also { Outbox.remember(row.id, it) }
        }
        val env = Json.decodeFromString(Envelope.serializer(), plaintext)
        val name = runCatching { profileOf(peerId)?.display_name }.getOrNull()
            ?: "Sangam friend"
        val what = when (env.t) {
            "t" -> env.b?.trim()?.take(140)?.ifBlank { null } ?: "New message"
            "i" -> "\uD83D\uDCF7  Photo"
            "v" -> "\uD83C\uDFA5  Video"
            "a" -> "\uD83C\uDFA4  Voice message"
            "d" -> "\uD83D\uDCC4  " + (env.b ?: "Document")
            "c" -> "\u23F0  Time capsule"
            else -> "New message"
        }
        name to what
    }.getOrNull()

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
        crypto = null
        SecureStores.secure(appContext).edit()
            .remove("login_email")
            .remove("login_password")
            .remove("login_session")
            .apply()
        // The caches are NOT wiped here any more. Wiping on sign-out threw
        // away the decrypted text of every message, and because the
        // encryption only allows one read per message, that text could never
        // be recovered - the whole history turned into "Encrypted message".
        // Instead the caches are tagged with the account that owns them, and
        // cleared only when a DIFFERENT account signs in. See guardCaches().
    }

    // ------------------------------------------------------------------ profiles

    suspend fun myProfile(): Profile? {
        val me = userId() ?: return null
        val p = runCatching {
            client.from("profiles").select(PUBLIC_PROFILE_COLUMNS) {
                filter { eq("id", me) }
                limit(1)
            }.decodeList<Profile>().firstOrNull()
        }.getOrElse {
            client.from("profiles").select(BASIC_PROFILE_COLUMNS) {
                filter { eq("id", me) }
                limit(1)
            }.decodeList<Profile>().firstOrNull()
        }
        p?.let { cacheMyIdentity(it.display_name, it.username) }
        return p
    }

    /** My display name / username, cached so Nearby mode works offline. */
    private fun cacheMyIdentity(name: String, username: String) {
        prefs("sangam_me")?.edit()
            ?.putString("name", name)?.putString("username", username)?.apply()
    }

    fun nearbyName(): String = prefs("sangam_me")?.getString("name", "") ?: ""

    fun nearbyUsername(): String = prefs("sangam_me")?.getString("username", "") ?: ""

    suspend fun profileOf(peerId: String): Profile? {
        return runCatching {
            client.from("profiles").select(PUBLIC_PROFILE_COLUMNS) {
                filter { eq("id", peerId) }
                limit(1)
            }.decodeList<Profile>().firstOrNull()
        }.getOrElse {
            client.from("profiles").select(BASIC_PROFILE_COLUMNS) {
                filter { eq("id", peerId) }
                limit(1)
            }.decodeList<Profile>().firstOrNull()
        }
    }

    suspend fun searchProfiles(query: String): List<Profile> {
        val me = userId()
        if (query.isBlank()) return emptyList()
        val term = "%${query.trim().lowercase().removePrefix("@")}%"
        val byUsername = runCatching {
            client.from("profiles").select(PUBLIC_PROFILE_COLUMNS) {
                filter { ilike("username", term) }
                limit(20)
            }.decodeList<Profile>()
        }.getOrElse {
            client.from("profiles").select(BASIC_PROFILE_COLUMNS) {
                filter { ilike("username", term) }
                limit(20)
            }.decodeList<Profile>()
        }
        val byName = runCatching {
            client.from("profiles").select(PUBLIC_PROFILE_COLUMNS) {
                filter { ilike("display_name", term) }
                limit(20)
            }.decodeList<Profile>()
        }.getOrElse {
            client.from("profiles").select(BASIC_PROFILE_COLUMNS) {
                filter { ilike("display_name", term) }
                limit(20)
            }.decodeList<Profile>()
        }
        return (byUsername + byName)
            .distinctBy { it.id }
            .filter { it.id != me }
            .sortedBy { it.display_name }
    }

    /** All profiles in one query (tiny table) - saves one query per chat
     *  on every chats-list refresh. */
    /**
     * What one user may see of another: name, handle, status, avatar and the
     * presence fields the online dot and typing indicator need. Never email
     * or the push token.
     */
    private val PUBLIC_PROFILE_COLUMNS = Columns.list(
        "id", "username", "display_name", "status", "avatar_url",
        "last_seen_at", "typing_for", "typing_until"
    )

    /** The columns that have always existed. Used when the presence ones do not. */
    private val BASIC_PROFILE_COLUMNS = Columns.list(
        "id", "username", "display_name", "status", "avatar_url"
    )

    /**
     * Ask for the public columns, and fall back to the basic set if the
     * server does not have the presence ones. Without this, one missing
     * column fails the whole query and every contact becomes "Sangam friend".
     */
    private suspend fun selectProfiles(): List<Profile> {
        val wide = runCatching {
            client.from("profiles").select(PUBLIC_PROFILE_COLUMNS).decodeList<Profile>()
        }
        if (wide.isSuccess) return wide.getOrThrow()
        return client.from("profiles").select(BASIC_PROFILE_COLUMNS).decodeList<Profile>()
    }

    // --------------------------------------------------------------- calls

    /** Ring someone. Returns the call id so the caller can watch for an answer. */
    suspend fun startCall(peerId: String, kind: String = "audio"): String? = runCatching {
        val me = userId() ?: return null
        client.from("calls").insert(CallInsert(caller = me, callee = peerId, kind = kind))
        val id = client.from("calls").select(Columns.ALL) {
            filter {
                eq("caller", me)
                eq("callee", peerId)
                eq("status", "ringing")
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<CallRow>().firstOrNull()?.id
        if (id != null) Push.fire(peerId)
        id
    }.getOrNull()

    /** The status of a call: ringing, active, declined or ended. */
    suspend fun callStatus(callId: String): String? = runCatching {
        client.from("calls").select(Columns.ALL) {
            filter { eq("id", callId) }
            limit(1)
        }.decodeList<CallRow>().firstOrNull()?.status
    }.getOrNull()

    /** Answer a ringing call. */
    suspend fun answerCall(callId: String) {
        runCatching {
            client.from("calls").update({ set("status", "active") }) {
                filter { eq("id", callId) }
            }
        }
    }

    /** Decline or hang up, and wipe ephemeral voice frames for this call. */
    suspend fun endCall(callId: String, declined: Boolean = false) {
        runCatching {
            client.from("calls").update({ set("status", if (declined) "declined" else "ended") }) {
                filter { eq("id", callId) }
            }
        }
        runCatching {
            client.from("call_chunks").delete {
                filter { eq("call_id", callId) }
            }
        }
    }

    private val callPeerPubCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun resolveCallPeerPub(callId: String): String? {
        callPeerPubCache[callId]?.let { if (it.isNotBlank()) return it }
        val me = userId() ?: return null
        return runCatching {
            val call = client.from("calls").select(Columns.ALL) {
                filter { eq("id", callId) }
                limit(1)
            }.decodeList<CallRow>().firstOrNull() ?: return null
            val peerId = if (call.caller == me) call.callee else call.caller
            val bundle = client.from("prekey_bundles").select(Columns.ALL) {
                filter { eq("user_id", peerId) }
                limit(1)
            }.decodeList<Models.PreKeyBundleRow>().firstOrNull()
            val pub = bundle?.identity_key?.takeIf { it.isNotBlank() }
            if (pub != null) callPeerPubCache[callId] = pub
            pub
        }.getOrNull()
    }

    /** Send one E2EE G.711 u-law audio or JPEG video frame for an active call. */
    suspend fun sendCallChunk(callId: String, seq: Int, pcmB64: String) {
        val me = userId() ?: return
        runCatching {
            if (seq == 1) {
                runCatching {
                    val myPub = crypto.localIdentityPubB64()
                    client.from("call_chunks").insert(
                        CallChunkInsert(callId = callId, sender = me, seq = 0, pcmB64 = "K:$myPub")
                    )
                }
            }
            val peerPub = resolveCallPeerPub(callId)
            val payload = if (!peerPub.isNullOrBlank()) {
                crypto.encryptCallFrame(callId, peerPub, pcmB64)
            } else {
                pcmB64
            }
            client.from("call_chunks").insert(
                CallChunkInsert(callId = callId, sender = me, seq = seq, pcmB64 = payload)
            )
        }
    }

    /** Fetch and decrypt E2EE audio/video frames sent by the other participant after `afterSeq`. */
    suspend fun fetchCallChunks(callId: String, afterSeq: Int): List<CallChunkRow> = runCatching {
        val me = userId() ?: return emptyList()
        val defaultPeerPub = callPeerPubCache[callId] ?: resolveCallPeerPub(callId)
        val rawRows = client.from("call_chunks").select(Columns.ALL) {
            filter {
                eq("call_id", callId)
                neq("from_user", me)
                gt("seq", afterSeq)
            }
            limit(25)
        }.decodeList<CallChunkRow>().sortedBy { it.seq }
        val out = mutableListOf<CallChunkRow>()
        for (r in rawRows) {
            val (dec, learnedPub) = crypto.decryptCallFrame(callId, callPeerPubCache[callId] ?: defaultPeerPub, r.pcmB64)
            if (!learnedPub.isNullOrBlank()) {
                callPeerPubCache[callId] = learnedPub
            }
            if (!dec.isNullOrBlank()) {
                out.add(r.copy(pcmB64 = dec))
            }
        }
        out
    }.getOrDefault(emptyList())

    /** A call ringing at me right now (started within the last 60s), if any. */
    /** Every call I have had with one person, newest first. */
    suspend fun callHistory(peerId: String, limit: Int = 40): List<CallRow> = runCatching {
        val me = userId() ?: return emptyList()
        val mine = client.from("calls").select(Columns.ALL) {
            filter {
                eq("caller", me)
                eq("callee", peerId)
            }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<CallRow>()
        val theirs = client.from("calls").select(Columns.ALL) {
            filter {
                eq("caller", peerId)
                eq("callee", me)
            }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<CallRow>()
        (mine + theirs).sortedByDescending { it.createdAt ?: "" }
    }.getOrDefault(emptyList())

    /** Every recent call involving me across all friends, newest first. */
    suspend fun allCalls(limit: Int = 50): List<CallRow> = runCatching {
        val me = userId() ?: return emptyList()
        val mine = client.from("calls").select(Columns.ALL) {
            filter { eq("caller", me) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<CallRow>()
        val theirs = client.from("calls").select(Columns.ALL) {
            filter { eq("callee", me) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<CallRow>()
        (mine + theirs).distinctBy { it.id }.sortedByDescending { it.createdAt ?: "" }.take(limit)
    }.getOrDefault(emptyList())

    suspend fun incomingCall(): CallRow? = runCatching {
        val me = userId() ?: return null
        val row = client.from("calls").select(Columns.ALL) {
            filter {
                eq("callee", me)
                eq("status", "ringing")
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<CallRow>().firstOrNull() ?: return null
        val age = System.currentTimeMillis() - parseTime(row.createdAt ?: "")
        if (age in 0..60_000L) row else null
    }.getOrNull()

    suspend fun allProfiles(): Map<String, Profile> =
        // Public columns only - this used to fetch every user's email and
        // push token just to show names and avatars.
        selectProfiles().associateBy { it.id }

    suspend fun usernameTaken(username: String): Boolean = runCatching {
        val available = client.postgrest.rpc(
            function = "username_available",
            parameters = buildJsonObject { put("p_username", username.lowercase().trim()) }
        ).decodeAs<Boolean>()
        !available
        // If the check cannot be completed, treat the name as taken. Offering
        // a name that might already be in use is the worse mistake.
    }.getOrDefault(true)

    /** Is this username free? False when it is mine already. */
    suspend fun usernameAvailable(username: String): Boolean = runCatching {
        val u = username.lowercase().trim()
        if (u.length !in 3..20) return@runCatching false
        if (userId() != null && myProfile()?.username?.lowercase() == u) return@runCatching true
        !usernameTaken(u)
    }.getOrDefault(false)

    /** Change the @username. Fails with a clear message if it is taken. */
    suspend fun setUsername(newUsername: String): Result<Unit> = runCatching {
        val me = userId() ?: error("Not signed in")
        val u = newUsername.trim().lowercase()
        if (u.length !in 3..20) error("Usernames are 3 to 20 characters.")
        if (usernameTaken(u)) error("@" + u + " is already taken.")
        client.from("profiles").update({ set("username", u) }) {
            filter { eq("id", me) }
        }
        Unit
    }

    // ------------------------------------------------------------------ messages

    suspend fun fetchConversation(peerId: String, limit: Long = 200L): List<MessageRow> {
        val me = userId() ?: return emptyList()
        // Both sides of the conversation are fetched at the same time —
        // this halves the wait when opening a chat.
        return coroutineScope {
            val sent = async {
                client.from("messages").select(Columns.ALL) {
                    filter { eq("sender_id", me); eq("recipient_id", peerId) }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }.decodeList<MessageRow>()
            }
            val received = async {
                client.from("messages").select(Columns.ALL) {
                    filter { eq("sender_id", peerId); eq("recipient_id", me) }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }.decodeList<MessageRow>()
            }
            sent.await() + received.await()
        }
            .filter { it.deleted != true }
            .sortedBy { parseTime(it.created_at) }
    }

    /** Only the rows newer than `sinceIso` — the light-weight poll used
     *  while a chat is open, so the app no longer re-downloads the whole
     *  conversation every two seconds. */
    suspend fun fetchConversationSince(
        peerId: String,
        sinceIso: String,
        limit: Long = 60L
    ): List<MessageRow> {
        val me = userId() ?: return emptyList()
        return coroutineScope {
            val sent = async {
                client.from("messages").select(Columns.ALL) {
                    filter {
                        eq("sender_id", me); eq("recipient_id", peerId)
                        gt("created_at", sinceIso)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }.decodeList<MessageRow>()
            }
            val received = async {
                client.from("messages").select(Columns.ALL) {
                    filter {
                        eq("sender_id", peerId); eq("recipient_id", me)
                        gt("created_at", sinceIso)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }.decodeList<MessageRow>()
            }
            sent.await() + received.await()
        }
            .filter { it.deleted != true }
            .sortedBy { parseTime(it.created_at) }
    }

    // ------------------------------------------------- per-chat offline cache

    /** Rows of a conversation cached on disk, so a chat opens instantly
     *  and is never blank while the network is slow. */
    fun readChatCache(peerId: String): List<MessageRow> = runCatching {
        if (!::appContext.isInitialized) return emptyList()
        val f = File(appContext.filesDir, "chat_cache_$peerId.json")
        if (!f.exists()) return emptyList()
        Json.decodeFromString(ListSerializer(MessageRow.serializer()), f.readText())
    }.getOrDefault(emptyList())

    fun writeChatCache(peerId: String, rows: List<MessageRow>) {
        runCatching {
            if (!::appContext.isInitialized) return
            val f = File(appContext.filesDir, "chat_cache_$peerId.json")
            f.writeText(
                Json.encodeToString(
                    ListSerializer(MessageRow.serializer()),
                    rows.takeLast(400)
                )
            )
        }
    }

    suspend fun fetchRecentInvolvingMe(limitPerSide: Long = 150L): List<MessageRow> {
        val me = userId() ?: return emptyList()
        return coroutineScope {
            val sent = async {
                client.from("messages").select(Columns.ALL) {
                    filter { eq("sender_id", me) }
                    order("created_at", Order.DESCENDING)
                    limit(limitPerSide)
                }.decodeList<MessageRow>()
            }
            val received = async {
                client.from("messages").select(Columns.ALL) {
                    filter { eq("recipient_id", me) }
                    order("created_at", Order.DESCENDING)
                    limit(limitPerSide)
                }.decodeList<MessageRow>()
            }
            sent.await() + received.await()
        }
            .filter { it.deleted != true }
            .sortedByDescending { parseTime(it.created_at) }
    }

    /** Send any Signal Protocol envelope (text or image reference) to all registered devices of a peer. */
    suspend fun send(peerId: String, envelope: Envelope): MessageRow {
        val me = userId() ?: error("Not signed in")
        val c = crypto ?: error("Crypto not ready")
        val peerDevs = devicesOf(peerId)
        val myOtherDevs = myDevices().filter { it.deviceKey != deviceKey() }
        val env = withDisappear(peerId, envelope)
        val plaintext = Json.encodeToString(Envelope.serializer(), env)
        val ciphertext = c.encryptForDevices(peerId, peerDevs, myOtherDevs, plaintext.toByteArray())
        val row = MessageRow(
            id = UUID.randomUUID().toString(),
            sender_id = me,
            recipient_id = peerId,
            ciphertext = ciphertext,
            created_at = Instant.now().toString()
        )
        client.from("messages").insert(row)
        Outbox.remember(row.id, plaintext)
        // Wake the friend's phone (unless it's just a silent reaction update).
        if (envelope.t != "r") Push.fire(peerId)
        return row
    }

    /** Upload an AES-GCM encrypted image and send its key inside the Signal Protocol message. */
    suspend fun sendImage(
        peerId: String, bytes: ByteArray,
        replyTo: String? = null, quote: String? = null
    ): Result<MessageRow> = runCatching {
        // Storage safeguard: never upload a photo above the size cap.
        val capped = capImageBytes(bytes)
        val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(capped)
        val path = "img/${UUID.randomUUID()}.bin"
        client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
        send(peerId, Envelope(t = "i", u = path, k = keyB64, v = ivB64,
            q = replyTo, p = quote))
    }

    /** Upload an AES-GCM encrypted voice note and send its key inside the Signal Protocol message. */
    suspend fun sendAudio(
        peerId: String, bytes: ByteArray, seconds: Int,
        replyTo: String? = null, quote: String? = null
    ): Result<MessageRow> =
        runCatching {
            val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(bytes)
            val path = "audio/${UUID.randomUUID()}.bin"
            client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
            send(peerId, Envelope(t = "a", b = seconds.toString(), u = path,
                k = keyB64, v = ivB64, q = replyTo, p = quote))
        }

    /** Download + decrypt an image received inside an Signal Protocol message. */
    suspend fun downloadImage(path: String, keyB64: String, ivB64: String): ByteArray =
        withContext(Dispatchers.IO) {
            val encrypted = client.storage.from(IMAGE_BUCKET).downloadPublic(path)
            ImageCrypto.decrypt(encrypted, keyB64, ivB64)
        }

    /** Convert a raw row to displayable content, decrypting locally. */
    fun toUi(row: MessageRow): UiMessage {
        val mine = row.sender_id == userId()
        val envelope: Envelope? = if (mine) {
            // Our own messages are read from the local encrypted Outbox cache, or decrypted
            // from the self-addressed Signal segment (`s<dev>@<senderDev>`) if sent from a linked device.
            val cached = Outbox.lookup(row.id)
            if (cached != null) {
                runCatching { Json.decodeFromString(Envelope.serializer(), cached) }.getOrNull()
            } else {
                val c = crypto
                if (c == null || row.ciphertext.isBlank()) null else {
                    runCatching {
                        val plaintext = String(c.decryptForDevice(row.sender_id, row.ciphertext, true))
                        Outbox.remember(row.id, plaintext)
                        Json.decodeFromString(Envelope.serializer(), plaintext)
                    }.getOrNull()
                }
            }
        } else {
            val c = crypto
            if (c == null) null else {
                val cached = Outbox.lookup(row.id)
                if (cached != null) {
                    runCatching { Json.decodeFromString(Envelope.serializer(), cached) }.getOrNull()
                } else {
                    runCatching {
                        val plaintext = String(c.decryptForDevice(row.sender_id, row.ciphertext, false))
                        // Each ciphertext can be decrypted exactly once (Signal
                        // protocol), so remember the plaintext for later views.
                        Outbox.remember(row.id, plaintext)
                        Json.decodeFromString(Envelope.serializer(), plaintext)
                    }.getOrNull()
                }
            }
        }
        // If the other phone is using a disappearing timer, take it on.
        if (!mine) {
            val key = row.group_id ?: row.sender_id
            envelope?.z?.let { if (it > 0) adoptDisappear(key, it) }
            // A friend opened one of our view-once photos.
            if (envelope?.t == "o") envelope.u?.let { markOpened(it) }
        }
        return UiMessage(
            id = row.id,
            peerId = if (mine) row.recipient_id else row.sender_id,
            senderId = row.sender_id,
            groupId = row.group_id,
            clientMsgId = row.client_msg_id,
            mine = mine,
            type = envelope?.t ?: "?",
            text = envelope?.b,
            imagePath = if (envelope?.t == "i" || envelope?.t == "a" ||
                envelope?.t == "d" || envelope?.t == "v") envelope?.u else null,
            imageKey = if (envelope?.t == "i" || envelope?.t == "a" ||
                envelope?.t == "d" || envelope?.t == "v") envelope?.k else null,
            imageIv = if (envelope?.t == "i" || envelope?.t == "a" ||
                envelope?.t == "d" || envelope?.t == "v") envelope?.v else null,
            thumbPath = if (envelope?.t == "v") envelope?.w else null,
            thumbKey = if (envelope?.t == "v") envelope?.x else null,
            thumbIv = if (envelope?.t == "v") envelope?.y else null,
            unlockAt = if (envelope?.t == "c") envelope?.u?.toLongOrNull() else envelope?.o,
            viewOnce = envelope?.once == true,
            reactionTo = if (envelope?.t == "r") envelope?.u else null,
            replyTo = if (envelope?.t != "r" && envelope?.t != "e") envelope?.q else null,
            quote = envelope?.p,
            editOf = if (envelope?.t == "e") envelope?.u else null,
            createdAt = parseTime(row.created_at),
            delivered = row.delivered_at != null || row.read_at != null,
            read = row.read_at != null,
            decryptFailed = envelope == null
        )
    }

    /** Called by the receiver once messages have arrived on the device. */
    suspend fun markDelivered() {
        val me = userId() ?: return
        client.from("messages").update({
            set("delivered_at", Instant.now().toString())
        }) {
            filter { eq("recipient_id", me); exact("delivered_at", null) }
        }
    }

    /** Called by the receiver when a chat is on screen. */
    suspend fun markRead(peerId: String) {
        val me = userId() ?: return
        client.from("messages").update({
            set("delivered_at", Instant.now().toString())
            set("read_at", Instant.now().toString())
        }) {
            filter {
                eq("sender_id", peerId)
                eq("recipient_id", me)
                exact("read_at", null)
            }
        }
    }

    /** Upload an encrypted document (PDF etc.) and send it as a message. */
    suspend fun sendDocument(
        peerId: String, bytes: ByteArray, fileName: String,
        replyTo: String? = null, quote: String? = null
    ): Result<MessageRow> = runCatching {
        val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(bytes)
        val path = "docs/${UUID.randomUUID()}.bin"
        client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
        send(
            peerId,
            Envelope(t = "d", b = fileName, u = path, k = keyB64, v = ivB64,
                q = replyTo, p = quote)
        )
    }

    /** Upload an encrypted video and send it as a message, with a small
     *  encrypted thumbnail so the chat shows a preview. */
    suspend fun sendVideo(
        peerId: String, bytes: ByteArray, thumbBytes: ByteArray? = null
    ): Result<MessageRow> =
        runCatching {
            val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(bytes)
            val path = "video/${UUID.randomUUID()}.bin"
            client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
            var w: String? = null
            var x: String? = null
            var y: String? = null
            if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                val (te, tk, tv) = ImageCrypto.encrypt(thumbBytes)
                w = "thumbs/${UUID.randomUUID()}.bin"
                client.storage.from(IMAGE_BUCKET).upload(w, te, upsert = true)
                x = tk
                y = tv
            }
            send(
                peerId,
                Envelope(t = "v", b = "0", u = path, k = keyB64, v = ivB64, w = w, x = x, y = y)
            )
        }

    /** Edit a sent text message: sends an encrypted edit instruction that
     *  replaces the original text on both phones. */
    suspend fun editMessage(peerId: String, originalId: String, newText: String): MessageRow =
        send(peerId, Envelope(t = "e", b = newText.trim(), u = originalId))

    /** Edit one of my messages inside a group. */
    suspend fun editGroupMessage(
        groupId: String,
        members: List<String>,
        originalId: String,
        newText: String
    ): MessageRow =
        sendToGroup(groupId, members, Envelope(t = "e", b = newText.trim(), u = originalId))

    /** Marks one of my sent messages as deleted for both sides. */
    suspend fun deleteMessageEverywhere(id: String) {
        val me = userId() ?: error("Not signed in")
        client.from("messages").update({ set("deleted", true) }) {
            filter { eq("id", id); eq("sender_id", me) }
        }
    }

    // ----------------------------------------------------------- groups

    /** Create a group and add every member (plus me, as admin). */
    suspend fun createGroup(
        name: String,
        memberIds: List<String>,
        description: String? = null
    ): GroupRow {
        val me = userId() ?: error("Not signed in")
        val gid = UUID.randomUUID().toString()
        val row = GroupRow(
            id = gid,
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            createdBy = me,
            createdAt = Instant.now().toString()
        )
        client.from("chat_groups").insert(row)
        // My own membership goes in FIRST: the access rule only lets me add
        // other people once I am already a member of the group.
        client.from("group_members").insert(
            GroupMemberRow(groupId = gid, userId = me, isAdmin = true)
        )
        val others = memberIds.filter { it != me }.distinct().map {
            GroupMemberRow(groupId = gid, userId = it, isAdmin = false)
        }
        if (others.isNotEmpty()) client.from("group_members").insert(others)
        return row
    }

    /** Every group I belong to (row level security returns only mine). */
    suspend fun myGroups(): List<GroupRow> =
        client.from("chat_groups").select(Columns.ALL)
            .decodeList<GroupRow>()
            .sortedBy { it.createdAt ?: "" }

    suspend fun groupInfo(groupId: String): GroupRow? =
        client.from("chat_groups").select(Columns.ALL) {
            filter { eq("id", groupId) }
            limit(1)
        }.decodeList<GroupRow>().firstOrNull()

    /** Join a group via an invite link or QR code if not already a member. */
    suspend fun joinGroup(groupId: String): Boolean = runCatching {
        val me = userId() ?: return false
        val cleanId = groupId.trim()
        val existing = runCatching {
            groupMembers(cleanId).any { it.userId == me }
        }.getOrDefault(false)
        if (!existing) {
            runCatching {
                client.from("group_members").insert(
                    GroupMemberRow(groupId = cleanId, userId = me, isAdmin = false)
                )
            }
        }
        true
    }.getOrDefault(false)

    suspend fun groupMembers(groupId: String): List<GroupMemberRow> =
        client.from("group_members").select(Columns.ALL) {
            filter { eq("group_id", groupId) }
        }.decodeList<GroupMemberRow>()

    suspend fun addGroupMembers(groupId: String, ids: List<String>) {
        val existing = groupMembers(groupId).map { it.userId }.toSet()
        val rows = ids.filter { it !in existing }.map {
            GroupMemberRow(groupId = groupId, userId = it, isAdmin = false)
        }
        if (rows.isNotEmpty()) client.from("group_members").insert(rows)
    }

    suspend fun removeGroupMember(groupId: String, userId: String) {
        client.from("group_members").delete {
            filter { eq("group_id", groupId); eq("user_id", userId) }
        }
    }

    suspend fun leaveGroup(groupId: String) {
        val me = userId() ?: return
        client.from("group_members").delete {
            filter { eq("group_id", groupId); eq("user_id", me) }
        }
    }

    suspend fun renameGroup(groupId: String, name: String) {
        client.from("chat_groups").update({ set("name", name.trim()) }) {
            filter { eq("id", groupId) }
        }
    }

    /** The raw membership rows - these carry the admin flag. */
    suspend fun groupMemberRows(groupId: String): List<GroupMemberRow> =
        client.from("group_members").select(Columns.ALL) {
            filter { eq("group_id", groupId) }
        }.decodeList<GroupMemberRow>()

    /** Make someone a group admin, or take it away. */
    suspend fun setGroupAdmin(groupId: String, userId: String, isAdmin: Boolean) {
        client.from("group_members").update({ set("is_admin", isAdmin) }) {
            filter {
                eq("group_id", groupId)
                eq("user_id", userId)
            }
        }
    }

    /** Let ordinary members add people, or keep it to admins only. */
    suspend fun setMembersCanAdd(groupId: String, can: Boolean) {
        client.from("chat_groups").update({ set("members_can_add", can) }) {
            filter { eq("id", groupId) }
        }
    }

    /** Let everyone post, or make it a leaders-only announcement group. */
    suspend fun setLeadersOnly(groupId: String, only: Boolean) {
        client.from("chat_groups").update({ set("leaders_only", only) }) {
            filter { eq("id", groupId) }
        }
    }

    // ------------------------------------------------------------ devices

    /** Which device number this phone is (persisted per install, 1 = primary phone). */
    fun deviceKey(): Int {
        val devPrefs = prefs("sangam_device")
        val saved = devPrefs?.getInt("key", 0) ?: 0
        if (saved in 1..127) return saved
        return crypto?.localDeviceId() ?: 1
    }

    fun deviceRowId(): String? = prefs("sangam_device")?.getString("row_id", null)

    fun deviceLabel(): String =
        (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim()

    /**
     * Register this install in `public.devices` with a real per-install `deviceKey`
     * and upload this device's Signal PreKeyBundle so senders can encrypt per device.
     */
    suspend fun registerDevice(): DeviceRow? = runCatching {
        val me = userId() ?: return@runCatching null
        if (crypto == null) crypto = CryptoManager(appContext)
        val c = crypto ?: return@runCatching null
        c.ensureIdentity()

        val devPrefs = prefs("sangam_device")
        val mine = devicesOf(me)
        val savedRowId = devPrefs?.getString("row_id", null)
        val savedKey = devPrefs?.getInt("key", 0) ?: 0
        val existing = mine.firstOrNull { it.id == savedRowId }
            ?: (if (savedKey > 0) mine.firstOrNull { it.deviceKey == savedKey } else null)

        val key = existing?.deviceKey ?: if (mine.isEmpty()) 1 else {
            ((mine.maxOfOrNull { it.deviceKey } ?: 1) + 1).coerceAtMost(127)
        }
        val rowId = existing?.id ?: UUID.randomUUID().toString()
        c.setLocalDeviceId(key)

        val bundle = c.devicePreKeyBundle()
        val otps = if ((existing?.oneTimePrekeys?.size ?: 0) >= 15) {
            existing?.oneTimePrekeys ?: emptyList()
        } else {
            c.generateDeviceOneTimePreKeys(30)
        }
        val row = DeviceRow(
            id = rowId,
            userId = me,
            label = deviceLabel(),
            deviceKey = key,
            registrationId = bundle.registration_id,
            identityKey = bundle.identity_key,
            signedPrekeyId = bundle.signed_prekey_id,
            signedPrekey = bundle.signed_prekey,
            signedPrekeySignature = bundle.signed_prekey_signature,
            oneTimePrekeys = otps
        )
        if (existing != null) {
            runCatching {
                client.from("devices").update(row) { filter { eq("id", rowId) } }
            }
        } else {
            runCatching {
                client.from("devices").insert(row)
            }
        }
        runCatching {
            val merged = (mine.filter { it.deviceKey != key && it.id != rowId } + row)
                .distinctBy { it.deviceKey }
                .sortedBy { it.deviceKey }
            val pubBytes = Json.encodeToString(ListSerializer(DeviceRow.serializer()), merged)
                .toByteArray(Charsets.UTF_8)
            client.storage.from("avatars").upload("devs_$me.json", pubBytes, upsert = true)
        }
        devPrefs?.edit()
            ?.putInt("key", key)
            ?.putString("row_id", rowId)
            ?.putBoolean("registered", true)
            ?.apply()
        runCatching { syncDesktopOutbox() }
        row
    }.getOrNull()

    /** Fetch all registered devices for any user so the sender can encrypt per device. */
    suspend fun devicesOf(targetUserId: String): List<DeviceRow> = runCatching {
        val dbRows = runCatching {
            client.from("devices").select(Columns.ALL) {
                filter { eq("user_id", targetUserId) }
            }.decodeList<DeviceRow>()
        }.getOrDefault(emptyList())
        val storageRows = if (dbRows.any { !it.identityKey.isNullOrBlank() && it.deviceKey > 1 }) {
            emptyList()
        } else {
            runCatching {
                val raw = client.storage.from("avatars").downloadPublic("devs_$targetUserId.json")
                Json.decodeFromString(ListSerializer(DeviceRow.serializer()), String(raw, Charsets.UTF_8))
            }.getOrDefault(emptyList())
        }
        val byKey = LinkedHashMap<Int, DeviceRow>()
        for (r in storageRows) byKey[r.deviceKey] = r
        for (r in dbRows) {
            val prev = byKey[r.deviceKey]
            if (prev == null || !r.identityKey.isNullOrBlank()) byKey[r.deviceKey] = r
        }
        byKey.values.sortedBy { it.deviceKey }
    }.getOrDefault(emptyList())

    suspend fun myDevices(): List<DeviceRow> = runCatching {
        val me = userId() ?: return@runCatching emptyList()
        devicesOf(me)
    }.getOrDefault(emptyList())

    suspend fun removeDevice(id: String) {
        client.from("devices").delete { filter { eq("id", id) } }
    }

    suspend fun setGroupDescription(groupId: String, description: String?) {
        client.from("chat_groups").update({
            set("description", description?.trim()?.ifBlank { null })
        }) {
            filter { eq("id", groupId) }
        }
    }

    suspend fun setGroupPhoto(groupId: String, bytes: ByteArray) {
        val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(capImageBytes(bytes))
        val path = "gphoto/${UUID.randomUUID()}.bin"
        client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
        client.from("chat_groups").update({
            set("photo_path", path); set("photo_key", keyB64); set("photo_iv", ivB64)
        }) {
            filter { eq("id", groupId) }
        }
    }

    /** The decrypted group photo, or null when there is none. */
    suspend fun groupPhoto(group: GroupRow): ByteArray? = runCatching {
        val p = group.photoPath ?: return null
        val k = group.photoKey ?: return null
        val v = group.photoIv ?: return null
        downloadImage(p, k, v)
    }.getOrNull()

    /** Send one Signal Protocol envelope to a whole group: a separate ciphertext row is
     *  written for every member (including a private self-copy), so the
     *  server still only ever holds ciphertext and each member decrypts
     *  with their own key. */
    suspend fun sendToGroup(
        groupId: String,
        members: List<String>,
        envelope: Envelope
    ): MessageRow {
        val me = userId() ?: error("Not signed in")
        val c = crypto ?: error("Crypto not ready")
        val all = (members + me).distinct()
        val myOtherDevs = myDevices().filter { it.deviceKey != deviceKey() }
        val env = withDisappear(groupId, envelope)
        val plaintext = Json.encodeToString(Envelope.serializer(), env)
        val mid = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val rows = all.map { m ->
            val ct = if (m == me) {
                if (myOtherDevs.isEmpty()) ""
                else c.encryptForDevices(me, emptyList(), myOtherDevs, plaintext.toByteArray())
            } else {
                val peerDevs = devicesOf(m)
                c.encryptForDevices(m, peerDevs, emptyList(), plaintext.toByteArray())
            }
            MessageRow(
                id = UUID.randomUUID().toString(),
                sender_id = me,
                recipient_id = m,
                ciphertext = ct,
                created_at = now,
                group_id = groupId,
                client_msg_id = mid
            )
        }
        rows.firstOrNull { it.recipient_id == me }?.let { Outbox.remember(it.id, plaintext) }
        client.from("messages").insert(rows)
        all.filter { it != me }.forEach { Push.fire(it) }
        return rows.first { it.recipient_id == me }
    }

    suspend fun sendGroupImage(
        groupId: String, members: List<String>, bytes: ByteArray,
        replyTo: String? = null, quote: String? = null
    ): Result<MessageRow> = runCatching {
        val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(capImageBytes(bytes))
        val path = "img/${UUID.randomUUID()}.bin"
        client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
        sendToGroup(groupId, members,
            Envelope(t = "i", u = path, k = keyB64, v = ivB64, q = replyTo, p = quote))
    }

    suspend fun sendGroupAudio(
        groupId: String, members: List<String>, bytes: ByteArray, seconds: Int,
        replyTo: String? = null, quote: String? = null
    ): Result<MessageRow> = runCatching {
        val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(bytes)
        val path = "audio/${UUID.randomUUID()}.bin"
        client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
        sendToGroup(groupId, members,
            Envelope(t = "a", b = seconds.toString(), u = path, k = keyB64, v = ivB64,
                q = replyTo, p = quote))
    }

    suspend fun sendGroupDocument(
        groupId: String, members: List<String>, bytes: ByteArray, fileName: String,
        replyTo: String? = null, quote: String? = null
    ): Result<MessageRow> = runCatching {
        val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(bytes)
        val path = "docs/${UUID.randomUUID()}.bin"
        client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
        sendToGroup(groupId, members,
            Envelope(t = "d", b = fileName, u = path, k = keyB64, v = ivB64,
                q = replyTo, p = quote))
    }

    suspend fun sendGroupVideo(
        groupId: String, members: List<String>, bytes: ByteArray, thumbBytes: ByteArray? = null
    ): Result<MessageRow> = runCatching {
        val (encrypted, keyB64, ivB64) = ImageCrypto.encrypt(bytes)
        val path = "video/${UUID.randomUUID()}.bin"
        client.storage.from(IMAGE_BUCKET).upload(path, encrypted, upsert = true)
        var w: String? = null
        var x: String? = null
        var y: String? = null
        if (thumbBytes != null && thumbBytes.isNotEmpty()) {
            val (te, tk, tv) = ImageCrypto.encrypt(thumbBytes)
            w = "thumbs/${UUID.randomUUID()}.bin"
            client.storage.from(IMAGE_BUCKET).upload(w, te, upsert = true)
            x = tk; y = tv
        }
        sendToGroup(groupId, members,
            Envelope(t = "v", b = "0", u = path, k = keyB64, v = ivB64, w = w, x = x, y = y))
    }

    suspend fun fetchGroup(groupId: String, limit: Long = 200L): List<MessageRow> {
        val me = userId() ?: return emptyList()
        return client.from("messages").select(Columns.ALL) {
            filter { eq("group_id", groupId); eq("recipient_id", me) }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<MessageRow>()
            .filter { it.deleted != true }
            .sortedBy { parseTime(it.created_at) }
    }

    suspend fun fetchGroupSince(
        groupId: String, sinceIso: String, limit: Long = 60L
    ): List<MessageRow> {
        val me = userId() ?: return emptyList()
        return client.from("messages").select(Columns.ALL) {
            filter {
                eq("group_id", groupId); eq("recipient_id", me)
                gt("created_at", sinceIso)
            }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<MessageRow>()
            .filter { it.deleted != true }
            .sortedBy { parseTime(it.created_at) }
    }

    /** Per-message tallies for my own group sends: delivered-by and read-by
     *  counts across the other members, keyed by client message id. */
    suspend fun groupTicks(groupId: String): Map<String, Pair<Int, Int>> {
        val me = userId() ?: return emptyMap()
        val rows = client.from("messages").select(Columns.ALL) {
            filter { eq("group_id", groupId); eq("sender_id", me) }
        }.decodeList<MessageRow>()
        return rows.filter { it.recipient_id != me }
            .groupBy { it.client_msg_id ?: it.id }
            .mapValues { (_, rs) ->
                rs.count { it.delivered_at != null } to rs.count { it.read_at != null }
            }
    }

    /** Clear my copy of a group chat - hides every message on this phone. */
    suspend fun clearGroupChat(groupId: String) {
        val me = userId() ?: return
        client.from("messages").update({ set("deleted", true) }) {
            filter { eq("group_id", groupId); eq("recipient_id", me) }
        }
    }

    /** Delete the whole group (creator only); memberships cascade with it. */
    suspend fun deleteGroup(groupId: String) {
        client.from("chat_groups").delete {
            filter { eq("id", groupId) }
        }
    }

    /** Mark every unread group message addressed to me as read. */
    suspend fun markReadGroup(groupId: String) {
        val me = userId() ?: return
        client.from("messages").update({
            set("delivered_at", Instant.now().toString())
            set("read_at", Instant.now().toString())
        }) {
            filter {
                eq("group_id", groupId)
                eq("recipient_id", me)
                neq("sender_id", me)
                exact("read_at", null)
            }
        }
    }

    // ------------------------------------------------- nearby (Bluetooth)

    /** My public bundle to hand a nearby phone (Bluetooth only, no server). */
    fun nearbyBundle(): Models.PreKeyBundleRow? =
        runCatching { crypto?.nearbyBundle() }.getOrNull()

    /** Build a session from a bundle a nearby phone handed us. */
    fun establishNearbySession(peerId: String, bundle: Models.PreKeyBundleRow) {
        runCatching { crypto?.ensureSessionFromBundle(peerId, bundle) }
    }

    /** Encrypt and keep a text message sent to a nearby peer over Bluetooth. */
    fun sendNearbyText(peerId: String, text: String): MessageRow? = runCatching {
        val me = userId() ?: ""
        val c = crypto ?: error("Crypto not ready")
        val plaintext = Json.encodeToString(Envelope.serializer(), Envelope(t = "t", b = text))
        val row = MessageRow(
            id = UUID.randomUUID().toString(),
            sender_id = me,
            recipient_id = peerId,
            ciphertext = c.encrypt(peerId, plaintext.toByteArray()),
            created_at = Instant.now().toString()
        )
        appendNearbyMessage(peerId, row, plaintext)
        row
    }.getOrNull()

    /** Decrypt and keep a message that arrived over Bluetooth. */
    fun receiveNearby(peerId: String, ciphertext: String): Boolean = runCatching {
        val c = crypto ?: return false
        val plaintext = String(c.decrypt(peerId, ciphertext))
        val row = MessageRow(
            id = UUID.randomUUID().toString(),
            sender_id = peerId,
            recipient_id = userId() ?: "",
            ciphertext = ciphertext,
            created_at = Instant.now().toString()
        )
        appendNearbyMessage(peerId, row, plaintext)
        true
    }.getOrDefault(false)

    /** Bluetooth messages for a chat, kept on this phone only. */
    fun readNearbyMessages(peerId: String): List<MessageRow> = runCatching {
        if (!::appContext.isInitialized) return emptyList()
        val f = File(appContext.filesDir, "nearby_msgs_$peerId.json")
        if (!f.exists()) return emptyList()
        Json.decodeFromString(ListSerializer(MessageRow.serializer()), f.readText())
    }.getOrDefault(emptyList())

    private fun appendNearbyMessage(peerId: String, row: MessageRow, plaintext: String) {
        runCatching {
            if (!::appContext.isInitialized) return
            Outbox.remember(row.id, plaintext)
            val existing = readNearbyMessages(peerId)
            if (existing.any { it.id == row.id }) return
            val f = File(appContext.filesDir, "nearby_msgs_$peerId.json")
            f.writeText(
                Json.encodeToString(
                    ListSerializer(MessageRow.serializer()),
                    (existing + row).takeLast(400)
                )
            )
        }
    }

    // ------------------------------------------- blocked users (local)

    /** Peers blocked on this phone: hidden everywhere, no notifications. */
    fun blockedIds(): Set<String> =
        prefs("sangam_blocked")?.getStringSet("ids", emptySet()) ?: emptySet()

    fun isBlocked(peerId: String): Boolean = peerId in blockedIds()

    fun blockPeer(peerId: String, name: String) {
        val p = prefs("sangam_blocked") ?: return
        val cur = p.getStringSet("ids", emptySet()) ?: emptySet()
        p.edit().putStringSet("ids", cur + peerId)
            .putString("name_$peerId", name)
            .apply()
    }

    fun unblockPeer(peerId: String) {
        val p = prefs("sangam_blocked") ?: return
        val cur = p.getStringSet("ids", emptySet()) ?: emptySet()
        p.edit().putStringSet("ids", cur - peerId).apply()
    }

    /** (id, name) of everyone blocked, for the settings dialog. */
    fun blockedList(): List<Pair<String, String>> =
        blockedIds().map { id ->
            id to (prefs("sangam_blocked")?.getString("name_$id", null) ?: "Blocked user")
        }

    // ------------------------------------------- offline outbox (local)

    /** Messages written offline for one chat, newest last. */
    fun pendingFor(peerId: String): List<Models.PendingMsg> {
        val raw = prefs("sangam_pending")?.getString(peerId, null) ?: return emptyList()
        return runCatching {
            Json.decodeFromString(
                ListSerializer(Models.PendingMsg.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun addPending(
        peerId: String, text: String,
        replyTo: String? = null, quote: String? = null,
        members: List<String>? = null
    ): Models.PendingMsg {
        val p = Models.PendingMsg(
            id = UUID.randomUUID().toString(),
            text = text, at = System.currentTimeMillis(),
            replyTo = replyTo, quote = quote, members = members
        )
        writePending(peerId, pendingFor(peerId) + p)
        return p
    }

    fun removePending(peerId: String, id: String) {
        writePending(peerId, pendingFor(peerId).filter { it.id != id })
    }

    private fun writePending(peerId: String, list: List<Models.PendingMsg>) {
        prefs("sangam_pending")?.edit()?.putString(
            peerId,
            Json.encodeToString(ListSerializer(Models.PendingMsg.serializer()), list)
        )?.apply()
    }

    // ------------------------------------------- image size cap

    /**
     * Keep an outgoing photo under the upload cap: first step the JPEG
     * quality down, then shrink the picture if needed. Keeps the free-tier
     * storage small without making normal photos noticeably worse.
     */
    fun capImageBytes(bytes: ByteArray, maxBytes: Int = 0): ByteArray {
        // A one-off "HD" choice for this photo wins over the global setting.
        val oneShot = nextHd
        nextHd = null
        val hd = oneShot ?: hdMedia()
        val cap = if (maxBytes > 0) maxBytes else if (hd) 2 * 1024 * 1024 else 400 * 1024
        if (bytes.size <= cap) return bytes
        var bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return bytes
        var out = ByteArrayOutputStream()
        var quality = 92
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > cap && quality > 55) {
            quality -= 8
            out = ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        }
        var scale = 0.85f
        while (out.size() > cap && scale > 0.25f) {
            val w = (bmp.width * scale).toInt().coerceAtLeast(200)
            val h = (bmp.height * scale).toInt().coerceAtLeast(200)
            bmp = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
            out = ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            scale *= 0.85f
        }
        val capped = out.toByteArray()
        return if (capped.isNotEmpty()) capped else bytes
    }

    // ------------------------------------------- media quality & disappearing

    /** Send photos at high quality instead of the small default. */
    fun hdMedia(): Boolean = prefs("sangam_media")?.getBoolean("hd", false) ?: false

    fun setHdMedia(on: Boolean) {
        prefs("sangam_media")?.edit()?.putBoolean("hd", on)?.apply()
    }

    /** How long messages in a chat survive, in seconds (0 = keep forever). */
    fun chatDisappear(chatKey: String): Long =
        prefs("sangam_disappear")?.getLong("d_$chatKey", 0L) ?: 0L

    /** When the timer was switched on - older messages are left alone. */
    fun disappearSince(chatKey: String): Long =
        prefs("sangam_disappear")?.getLong("s_$chatKey", 0L) ?: 0L

    fun setChatDisappear(chatKey: String, seconds: Long) {
        prefs("sangam_disappear")?.edit()
            ?.putLong("d_$chatKey", seconds)
            ?.putLong("s_$chatKey", System.currentTimeMillis())
            ?.apply()
    }

    /** Take on the timer the other phone is using, the first time we see it. */
    fun adoptDisappear(chatKey: String, seconds: Long) {
        if (seconds <= 0) return
        val had = chatDisappear(chatKey)
        if (had == seconds) return
        val e = prefs("sangam_disappear")?.edit()?.putLong("d_$chatKey", seconds)
        if (had <= 0L) e?.putLong("s_$chatKey", System.currentTimeMillis())
        e?.apply()
    }

    /** Applied to the next outgoing message only, then cleared. */
    var nextSealedUntil: Long? = null
    var nextViewOnce: Boolean = false
    var nextHd: Boolean? = null

    // ------------------------------------------------------------ photo cache

    /** Decrypted photos kept in memory, so reopening a chat is instant. */
    private val imageCache = LinkedHashMap<String, android.graphics.Bitmap>()
    private const val IMAGE_CACHE_MAX = 60

    fun cachedImage(id: String): android.graphics.Bitmap? = imageCache[id]

    fun putImage(id: String, bmp: android.graphics.Bitmap) {
        imageCache[id] = bmp
        while (imageCache.size > IMAGE_CACHE_MAX) {
            val oldest = imageCache.keys.firstOrNull() ?: break
            imageCache.remove(oldest)
        }
    }

    /** Tell the other phone that a view-once photo has been opened. */
    suspend fun sendViewOnceOpened(peerId: String, messageId: String) {
        runCatching { send(peerId, Envelope(t = "o", u = messageId)) }
    }

    /** View-once photos we sent that the friend has opened. */
    fun markOpened(id: String) {
        prefs("sangam_opened")?.edit()?.putBoolean("o_$id", true)?.apply()
    }

    fun wasOpened(id: String): Boolean =
        prefs("sangam_opened")?.getBoolean("o_$id", false) ?: false

    /** Has this view-once photo or video already been opened? */
    fun viewOnceUsed(id: String): Boolean =
        prefs("sangam_viewonce")?.getBoolean("u_$id", false) ?: false

    fun markViewOnceUsed(id: String) {
        prefs("sangam_viewonce")?.edit()?.putBoolean("u_$id", true)?.apply()
    }

    /** Stamp the chat's disappearing timer, and any seal / view-once, onto an
     *  outgoing envelope. The seal and view-once apply to one message only. */
    private fun withDisappear(chatKey: String, env: Envelope): Envelope {
        var e = env
        if (e.z == null) {
            val secs = chatDisappear(chatKey)
            if (secs > 0) e = e.copy(z = secs)
        }
        val sealed = nextSealedUntil
        if (sealed != null && e.o == null) e = e.copy(o = sealed)
        if (nextViewOnce && e.once == null) e = e.copy(once = true)
        nextSealedUntil = null
        nextViewOnce = false
        return e
    }

    /** Per-chat look (wallpaper + bubble colour), stored only on this phone. */
    fun chatTheme(peerId: String): String {
        if (!::appContext.isInitialized) return "default"
        return appContext.getSharedPreferences("sangam_chat_themes", Context.MODE_PRIVATE)
            .getString(peerId, "default") ?: "default"
    }

    fun setChatTheme(peerId: String, theme: String) {
        if (!::appContext.isInitialized) return
        appContext.getSharedPreferences("sangam_chat_themes", Context.MODE_PRIVATE)
            .edit().putString(peerId, theme).apply()
    }

    // ------------------------------------------- notification options (local)

    private fun prefs(name: String): android.content.SharedPreferences? =
        if (::appContext.isInitialized) appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        else null

    fun notifSoundOn(): Boolean = prefs("sangam_notif")?.getBoolean("sound", true) ?: true
    fun setNotifSound(on: Boolean) { prefs("sangam_notif")?.edit()?.putBoolean("sound", on)?.apply() }

    fun notifVibrateOn(): Boolean = prefs("sangam_notif")?.getBoolean("vibrate", true) ?: true
    fun setNotifVibrate(on: Boolean) { prefs("sangam_notif")?.edit()?.putBoolean("vibrate", on)?.apply() }

    /** If off, notifications show only "New message" instead of the text. */
    fun notifShowText(): Boolean = prefs("sangam_notif")?.getBoolean("show_text", true) ?: true
    fun setNotifShowText(on: Boolean) { prefs("sangam_notif")?.edit()?.putBoolean("show_text", on)?.apply() }

    // ---- pinned chats ----

    /** The chats pinned to the top of the chats list. */
    fun pinnedChats(): Set<String> =
        prefs("sangam_pinned")?.getStringSet("pinned", emptySet()) ?: emptySet()

    fun isPinned(peerId: String): Boolean = pinnedChats().contains(peerId)

    /** Pin or unpin a chat. */
    fun togglePinnedChat(peerId: String) {
        val p = prefs("sangam_pinned") ?: return
        val current = p.getStringSet("pinned", emptySet()) ?: emptySet()
        p.edit().putStringSet(
            "pinned",
            if (peerId in current) current - peerId else current + peerId
        ).apply()
    }

    // ---- message drafts ----

    /** The unsent text saved for one chat. */
    fun draftFor(peerId: String): String =
        prefs("sangam_drafts")?.getString("draft_$peerId", "") ?: ""

    /** Save (or clear, when blank) the draft for one chat. */
    fun saveDraft(peerId: String, text: String) {
        val p = prefs("sangam_drafts") ?: return
        if (text.isBlank()) p.edit().remove("draft_$peerId").apply()
        else p.edit().putString("draft_$peerId", text).apply()
    }

    fun isChatMuted(peerId: String): Boolean =
        prefs("sangam_muted")?.getBoolean(peerId, false) ?: false

    fun setChatMuted(peerId: String, muted: Boolean) {
        prefs("sangam_muted")?.edit()?.putBoolean(peerId, muted)?.apply()
    }

    // -------------------------------------------- hidden messages (local only)

    fun hiddenIds(): Set<String> =
        prefs("sangam_hidden")?.getStringSet("ids", emptySet()) ?: emptySet()

    /** Hide a message on this phone only ("Delete for me"). */
    fun hideMessage(id: String) {
        val cur = hiddenIds().toMutableSet()
        cur.add(id)
        prefs("sangam_hidden")?.edit()?.putStringSet("ids", cur)?.apply()
    }

    // ----------------------------------------------------- privacy options (local)

    /** If off, friends don't see your blue read ticks. */
    fun readReceiptsOn(): Boolean =
        prefs("sangam_privacy")?.getBoolean("read_receipts", true) ?: true

    fun setReadReceipts(on: Boolean) {
        prefs("sangam_privacy")?.edit()?.putBoolean("read_receipts", on)?.apply()
    }

    /** If off, friends don't see your online / last seen. */
    fun lastSeenShareOn(): Boolean =
        prefs("sangam_privacy")?.getBoolean("last_seen", true) ?: true

    fun setLastSeenShare(on: Boolean) {
        prefs("sangam_privacy")?.edit()?.putBoolean("last_seen", on)?.apply()
    }

    // -------------------------------------------------- starred messages (local)

    fun starredIds(): Set<String> =
        prefs("sangam_starred")?.getStringSet("ids", emptySet()) ?: emptySet()

    /** Toggle a star; returns true if the message is starred afterwards. */
    fun toggleStar(id: String): Boolean {
        val cur = starredIds().toMutableSet()
        val nowStarred = if (cur.contains(id)) { cur.remove(id); false } else { cur.add(id); true }
        prefs("sangam_starred")?.edit()?.putStringSet("ids", cur)?.apply()
        return nowStarred
    }

    // ------------------------------------------------------------- app lock (local)

    /** The old scheme: one SHA-256 with a salt that is in the source code. */
    private fun legacyPinHash(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest((pin + "sangam-lock-salt").toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** PBKDF2 with a random per-device salt, 120k rounds. */
    private fun pinHash(pin: String): String {
        val prefs = SecureStores.secure(appContext)
        var saltB64 = prefs.getString("app_lock_salt", null)
        if (saltB64 == null) {
            val s = ByteArray(16)
            java.security.SecureRandom().nextBytes(s)
            saltB64 = android.util.Base64.encodeToString(s, android.util.Base64.NO_WRAP)
            prefs.edit().putString("app_lock_salt", saltB64).apply()
        }
        val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val key = javax.crypto.SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return key.joinToString("") { "%02x".format(it) }
    }

    fun appLockEnabled(): Boolean =
        if (::appContext.isInitialized) SecureStores.secure(appContext).getBoolean("app_lock", false)
        else false

    fun setAppLockPin(pin: String) {
        if (!::appContext.isInitialized) return
        SecureStores.secure(appContext).edit()
            .putString("app_lock_pin", pinHash(pin))
            .putBoolean("app_lock", true)
            .apply()
    }

    fun clearAppLock() {
        if (!::appContext.isInitialized) return
        SecureStores.secure(appContext).edit()
            .remove("app_lock_pin")
            .putBoolean("app_lock", false)
            .apply()
    }

    fun checkAppLockPin(pin: String): Boolean {
        if (!::appContext.isInitialized) return false
        val stored = SecureStores.secure(appContext).getString("app_lock_pin", null) ?: return false
        val upgraded = pinHash(pin)
        if (stored == upgraded) return true
        // Older installs used the weak hash. Accept it once, then store the
        // stronger one so the old value does not linger.
        if (stored == legacyPinHash(pin)) {
            SecureStores.secure(appContext).edit().putString("app_lock_pin", upgraded).apply()
            return true
        }
        return false
    }

    // ------------------------------------------- cloud backup (v2.8)

    /** The key on this phone, or null if none has been made here yet. */
    fun existingRecoveryKey(): String? =
        SecureStores.secure(appContext).getString("backup_key", null)

    /** Has this phone ever uploaded a backup? */
    fun hasCloudBackup(): Boolean =
        SecureStores.secure(appContext).getBoolean("backup_done", false)

    /** The recovery key: created on first use, then kept in the encrypted store. */
    fun recoveryKey(): String {
        val prefs = SecureStores.secure(appContext)
        prefs.getString("backup_key", null)?.let { return it }
        val raw = ByteArray(24)
        java.security.SecureRandom().nextBytes(raw)
        val text = android.util.Base64.encodeToString(
            raw,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or
                android.util.Base64.NO_PADDING
        )
        val grouped = text.chunked(4).joinToString("-")
        prefs.edit().putString("backup_key", grouped).apply()
        return grouped
    }

    /** Everything needed to rebuild this phone's readable history. */
    fun backupPayload(): String {
        val secure = SecureStores.secure(appContext)
        val json = Json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                envelopes = Outbox.dumpAll(),
                identity = secure.getString(SignalStore.KEY_IDENTITY, null),
                registrationId = secure.getInt(SignalStore.KEY_REGISTRATION_ID, 0)
            )
        )
        return json
    }

    /** How many messages in the last backup had no readable text. */
    var lastRestoreUnreadable: Int = 0
        private set

    /** Write a restored payload back into this phone. Returns messages restored. */
    fun applyBackupPayload(json: String): Int {
        val payload = Json.decodeFromString(BackupPayload.serializer(), json)
        Outbox.loadAll(payload.envelopes)
        val secure = SecureStores.secure(appContext)
        // Restore the identity so contacts do not see a stranger. Sessions are
        // deliberately NOT restored - an old ratchet would desync.
        if (!payload.identity.isNullOrBlank() && payload.registrationId > 0) {
            secure.edit()
                .putString(SignalStore.KEY_IDENTITY, payload.identity)
                .putInt(SignalStore.KEY_REGISTRATION_ID, payload.registrationId)
                .apply()
            // Drop cached conversation pages, which hold ciphertext tied to the
            // old identity.
            runCatching {
                appContext.filesDir.listFiles()
                    ?.filter { it.name.startsWith("chat_cache_") }
                    ?.forEach { it.delete() }
            }
            // Rebuild the crypto manager and sign a pre-key with the restored
            // identity. Leaving crypto null here made sending fail outright.
            crypto = CryptoManager(appContext).also { it.ensureIdentity() }
            crypto?.regenerateSignedPreKey()
            keyScope.launch {
                runCatching { crypto?.publishKeys() }
                runCatching { crypto?.refillOneTimePreKeys() }
            }
        }
        return payload.envelopes.size
    }

    /** Push an encrypted backup to the server. */
    suspend fun uploadBackup(): Result<Int> = runCatching {
        val me = userId() ?: error("Not signed in")
        val payload = backupPayload()
        // Encrypt with the letters-and-digits form so the key can be typed back
        // with or without the dashes.
        val blob = backupEncrypt(payload.toByteArray(), normalizeKey(recoveryKey()))
        client.from("backups").upsert(
            BackupRow(
                userId = me,
                blob = android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP),
                updatedAt = Instant.now().toString()
            )
        )
        SecureStores.secure(appContext).edit().putBoolean("backup_done", true).apply()
        Outbox.dumpAll().size
    }

    /** Letters and digits only, so a hand-typed key still matches. */
    private fun normalizeKey(k: String): String = k.filter { it.isLetterOrDigit() }

    /**
     * Pull the newest backup and restore it into this phone.
     *
     * The key comes from the USER, not from this phone: after a reinstall the
     * phone's own key is a brand new one and would never open an old backup.
     * That was the whole point of a recovery key, and it was missing.
     */
    suspend fun downloadBackup(typedKey: String): Result<Int> = runCatching {
        val me = userId() ?: error("Not signed in")
        val rows = client.from("backups").select {
            filter { eq("user_id", me) }
            limit(1)
        }.decodeList<BackupRow>()
        val row = rows.firstOrNull() ?: error("No backup found for this account")
        val blob = android.util.Base64.decode(row.blob, android.util.Base64.NO_WRAP)
        // Accept the key typed with or without dashes, and fall back to the
        // key already on this phone (same device, same install).
        val candidates = listOf(
            normalizeKey(typedKey),
            typedKey.trim(),
            recoveryKey()
        ).filter { it.isNotBlank() }
        var plain: ByteArray? = null
        for (k in candidates) {
            plain = runCatching { backupDecrypt(blob, k) }.getOrNull()
            if (plain != null) break
        }
        val bytes = plain
            ?: error("That recovery key does not open this backup")
        // Remember the key that worked, so the next backup uses the same one.
        SecureStores.secure(appContext).edit()
            .putString("backup_key", typedKey.trim())
            .apply()
        applyBackupPayload(String(bytes))
    }

    // ------------------------------------------------- chat backup (encrypted file)

    /** Password-encrypt the backup for the file the user saves. */
    fun backupEncrypt(plain: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120_000, 256))
            .encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"))
        return salt + cipher.iv + cipher.doFinal(plain)
    }

    /** Decrypt a backup file. Throws if the password is wrong. */
    fun backupDecrypt(data: ByteArray, password: String): ByteArray {
        require(data.size > 16 + 12) { "Not a Sangam backup file" }
        val salt = data.copyOfRange(0, 16)
        val iv = data.copyOfRange(16, 28)
        val body = data.copyOfRange(28, data.size)
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120_000, 256))
            .encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private var lastSeenSentAt = 0L

    /**
     * Report a message or a person. Stored so it can be reviewed; the reported
     * user is never told who reported them.
     */
    suspend fun reportAbuse(kind: String, targetId: String, note: String? = null): Result<Unit> =
        runCatching {
            val me = userId() ?: error("Not signed in")
            client.from("abuse_reports").insert(
                AbuseReportRow(
                    reporterId = me,
                    kind = kind,
                    targetId = targetId,
                    note = note
                )
            )
            Unit
        }

    /** Marks me as online right now (chat header "online" / last seen). */
    suspend fun touchLastSeen() {
        if (!lastSeenShareOn()) return  // privacy: don't share my last seen
        // The chat screen polls every 2 s; writing the profile that often is
        // wasteful. Once every 30 s is plenty for an "online" dot.
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSeenSentAt < 30_000L) return
        lastSeenSentAt = nowMs
        val me = userId() ?: return
        runCatching {
            client.from("profiles").update({
                set("last_seen_at", Instant.now().toString())
            }) {
                filter { eq("id", me) }
            }
        }
    }

    /** Tells the peer I am typing, valid for a short window. */
    suspend fun setTypingFor(peerId: String) {
        val me = userId() ?: return
        runCatching {
            client.from("profiles").update({
                set("typing_for", peerId)
                set("typing_until", Instant.now().plusSeconds(8).toString())
            }) {
                filter { eq("id", me) }
            }
        }
    }

    /** Resolves a username to the account email (login-lookup function). */
    suspend fun lookupEmail(username: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val conn = java.net.URL(
                    BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/login-lookup"
                ).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.doOutput = true
                conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                conn.setRequestProperty(
                    "Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY
                )
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use {
                    it.write("""{"username":"$username"}""".toByteArray())
                }
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                org.json.JSONObject(body).optString("email").takeUnless { it.isNullOrEmpty() }
            }.getOrNull()
        }

    /** Stores my login email on my profile so username login can find it. */
    private suspend fun backfillEmail() {
        val me = userId() ?: return
        val email = client.auth.currentSessionOrNull()?.user?.email ?: return
        runCatching {
            client.from("profiles").update({ set("email", email) }) {
                filter { eq("id", me) }
            }
        }
    }

    /** Update my display name and status line. */
    suspend fun updateMyProfile(displayName: String, status: String?) {
        val me = userId() ?: error("Not signed in")
        try {
            client.from("profiles").update({
                set("display_name", displayName)
                set("status", status)
            }) {
                filter { eq("id", me) }
            }
        } catch (e: Exception) {
            // The status column comes with the one-line v1.7.0 SQL; until
            // it has been run, save the name alone instead of failing.
            if (!e.message.orEmpty().contains("status", ignoreCase = true)) throw e
            client.from("profiles").update({
                set("display_name", displayName)
            }) {
                filter { eq("id", me) }
            }
        }
    }

    /** Upload a square profile photo and point my profile at it. */
    suspend fun uploadAvatar(bytes: ByteArray): String {
        val me = userId() ?: error("Not signed in")
        // A fresh name per upload, so every phone sees the new photo
        // straight away instead of a cached old one.
        val path = "u_${me}_${System.currentTimeMillis()}.jpg"
        client.storage.from("avatars").upload(path, bytes, upsert = true)
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/storage/v1/object/public/avatars/" + path
        client.from("profiles").update({
            set("avatar_url", url)
        }) {
            filter { eq("id", me) }
        }
        return url
    }

    /** Theme choice: "auto" follows the device, "light"/"dark" force it. */
    fun themeMode(): String {
        val prefs = appContext.getSharedPreferences("sangam_ui", Context.MODE_PRIVATE)
        return prefs.getString("theme", null)
            ?: if (prefs.getBoolean("dark", false)) "dark" else "auto"
    }

    fun setThemeMode(mode: String) {
        appContext.getSharedPreferences("sangam_ui", Context.MODE_PRIVATE)
            .edit().putString("theme", mode).apply()
    }

    /** Resolves the theme for the Android window at launch. */
    fun wantsDarkTheme(res: android.content.res.Resources): Boolean {
        val night = (res.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return when (themeMode()) {
            "dark" -> true
            "light" -> false
            else -> night
        }
    }

    fun parseTime(iso: String): Long = runCatching {
        Instant.parse(iso).toEpochMilli()
    }.getOrElse {
        // Postgres can return offsets like "+00:00" instead of "Z".
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)
    }

    /** Store this device's Firebase push token so friends' messages can wake it. */
    fun saveFcmToken(token: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val me = userId() ?: return@launch
                client.from("profiles").update({
                    set("fcm_token", token)
                }) {
                    filter { eq("id", me) }
                }
            }
        }
    }

    /** Permanently delete this account on the server, then wipe this phone. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val session = client.auth.currentSessionOrNull() ?: error("Not signed in")
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/delete-account"
        HttpClient(Android) {
            install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        }.use { http ->
            val resp = http.post(url) {
                header(HttpHeaders.Authorization, "Bearer " + session.accessToken)
            }
            if (!resp.status.isSuccess()) {
                error("The server refused (HTTP " + resp.status.value + "). Is the delete-account function deployed?")
            }
        }
        // Gone on the server - now forget everything on this phone.
        crypto = null
        SecureStores.secure(appContext).edit().clear().apply()
        SecureStores.outbox(appContext).edit().clear().apply()
        wipeLocalData()
    }

    /** Remove every Sangam setting and cached file on this phone. */
    fun wipeLocalData() {
        if (!::appContext.isInitialized) return
        runCatching {
            appContext.filesDir.listFiles()?.forEach {
                if (it.name.startsWith("chat_cache_") ||
                    it.name.startsWith("chats_cache") ||
                    it.name.startsWith("nearby_msgs_")
                ) it.delete()
            }
            appContext.cacheDir.listFiles()?.forEach { it.delete() }
            listOf(
                "sangam_drafts", "sangam_pinned", "sangam_muted", "sangam_hidden",
                "sangam_blocked", "sangam_chat_themes", "sangam_notif",
                "sangam_starred", "sangam_pending", "sangam_privacy",
                "sangam_disappear", "sangam_viewonce", "sangam_opened",
                "sangam_media", "sangam_device", "sangam_me"
            ).forEach {
                appContext.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply()
            }
            // The decrypted plaintext of messages must never outlive the
            // account that was signed in - this is the one that leaked.
            SecureStores.outbox(appContext).edit().clear().apply()
            imageCache.clear()
        }
    }

    // ------------------------------------------------------------------ outbox cache

    /**
     * Locally cached plaintext of our own sent messages and successfully
     * decrypted incoming messages, so chat history renders after restarts and
     * is never decrypted twice (the Signal protocol allows exactly one
     * successful decrypt per ciphertext). Stored only in this device's
     * encrypted storage, one entry per message.
     */
    object Outbox {
        private const val KEY_INDEX = "outbox_index"
        private const val KEY_MSG_PREFIX = "outbox_msg_"
        private const val CAP = 3000

        @Synchronized
        fun remember(id: String, envelopeJson: String) {
            val prefs = SecureStores.outbox(appContext)
            val index = readIndex(prefs)
            val keep = (index + id).distinct().takeLast(CAP)
            val editor = prefs.edit()
            for (old in index) if (old !in keep) editor.remove(KEY_MSG_PREFIX + old)
            editor.putString(KEY_MSG_PREFIX + id, envelopeJson)
            editor.putString(KEY_INDEX, Json.encodeToString(ListSerializer(String.serializer()), keep))
            editor.apply()
        }

        fun lookup(id: String): String? {
            return SecureStores.outbox(appContext).getString(KEY_MSG_PREFIX + id, null)
        }

        /** Every stored message id -> envelope, for the backup. */
        @Synchronized
        fun dumpAll(): Map<String, String> {
            val prefs = SecureStores.outbox(appContext)
            val out = LinkedHashMap<String, String>()
            for (id in readIndex(prefs)) {
                prefs.getString(KEY_MSG_PREFIX + id, null)?.let { out[id] = it }
            }
            return out
        }

        /**
         * Put restored messages back in ONE write. Calling remember() in a
         * loop meant up to 3,000 separate encrypted-prefs commits.
         */
        @Synchronized
        fun loadAll(map: Map<String, String>) {
            if (map.isEmpty()) return
            val prefs = SecureStores.outbox(appContext)
            val index = readIndex(prefs)
            val keep = (index + map.keys).distinct().takeLast(CAP)
            val editor = prefs.edit()
            for (old in index) if (old !in keep) editor.remove(KEY_MSG_PREFIX + old)
            for ((id, env) in map) editor.putString(KEY_MSG_PREFIX + id, env)
            editor.putString(
                KEY_INDEX,
                Json.encodeToString(ListSerializer(String.serializer()), keep)
            )
            editor.apply()
        }

        private fun readIndex(prefs: android.content.SharedPreferences): List<String> {
            val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
            return runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), raw)
            }.getOrDefault(emptyList())
        }
    }
}
