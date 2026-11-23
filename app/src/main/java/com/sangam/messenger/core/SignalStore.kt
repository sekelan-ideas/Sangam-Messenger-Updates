package com.sangam.messenger.core

import android.content.SharedPreferences
import android.util.Base64
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.InvalidKeyIdException
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.IdentityKeyStore
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.PreKeyStore
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SessionStore
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyStore

/**
 * Signal protocol state (identity, trusted identities, sessions, pre-keys)
 * persisted in Android's encrypted preferences. Private keys never leave
 * this device; nothing in this store is ever uploaded anywhere.
 */
class SignalStore(private val prefs: SharedPreferences) : SignalProtocolStore {

    private fun b64e(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun b64d(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    // ---------------------------------------------------------- identities

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val stored = prefs.getString(KEY_IDENTITY, null)
            ?: throw IllegalStateException("Identity not generated yet")
        return IdentityKeyPair(b64d(stored))
    }

    override fun getLocalRegistrationId(): Int = prefs.getInt(KEY_REGISTRATION_ID, 0)

    /**
     * Per-install Signal device id (1..127), generated once and persisted.
     * Existing single-phone installs that already have an identity keep 1 so
     * single-device chats see zero change.
     */
    fun localDeviceId(): Int {
        val saved = prefs.getInt(KEY_DEVICE_ID, 0)
        if (saved in 1..127) return saved
        prefs.edit().putInt(KEY_DEVICE_ID, 1).apply()
        return 1
    }

    fun setLocalDeviceId(id: Int) {
        if (id in 1..127) {
            prefs.edit().putInt(KEY_DEVICE_ID, id).apply()
        }
    }

    private fun identityKey(address: SignalProtocolAddress): String =
        if (address.deviceId <= 1) KEY_IDENTITY_PREFIX + address.name
        else KEY_IDENTITY_PREFIX + address.name + "." + address.deviceId

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey
    ): Boolean {
        val existing = getIdentity(address)
        prefs.edit()
            .putString(identityKey(address), b64e(identityKey.serialize()))
            .apply()
        return existing == null || existing == identityKey
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        return true
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val stored = prefs.getString(identityKey(address), null) ?: return null
        return IdentityKey(b64d(stored), 0)
    }

    // ---------------------------------------------------------- one-time pre-keys

    @Throws(InvalidKeyIdException::class)
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val stored = prefs.getString(KEY_PREKEY_PREFIX + preKeyId, null)
            ?: throw InvalidKeyIdException("No pre-key $preKeyId")
        return PreKeyRecord(b64d(stored))
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        prefs.edit().putString(KEY_PREKEY_PREFIX + preKeyId, b64e(record.serialize())).apply()
    }

    override fun containsPreKey(preKeyId: Int): Boolean =
        prefs.contains(KEY_PREKEY_PREFIX + preKeyId)

    override fun removePreKey(preKeyId: Int) {
        prefs.edit().remove(KEY_PREKEY_PREFIX + preKeyId).apply()
    }

    // ---------------------------------------------------------- signed pre-keys

    @Throws(InvalidKeyIdException::class)
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val stored = prefs.getString(KEY_SIGNED_PREKEY_PREFIX + signedPreKeyId, null)
            ?: throw InvalidKeyIdException("No signed pre-key $signedPreKeyId")
        return SignedPreKeyRecord(b64d(stored))
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        val records = mutableListOf<SignedPreKeyRecord>()
        for ((key, value) in prefs.all) {
            if (key is String && key.startsWith(KEY_SIGNED_PREKEY_PREFIX)) {
                runCatching { records.add(SignedPreKeyRecord(b64d(value.toString()))) }
            }
        }
        return records
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        prefs.edit()
            .putString(KEY_SIGNED_PREKEY_PREFIX + signedPreKeyId, b64e(record.serialize()))
            .apply()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        prefs.contains(KEY_SIGNED_PREKEY_PREFIX + signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        prefs.edit().remove(KEY_SIGNED_PREKEY_PREFIX + signedPreKeyId).apply()
    }

    // ---------------------------------------------------------- sessions

    override fun loadSession(remoteAddress: SignalProtocolAddress): SessionRecord {
        val stored = prefs.getString(sessionKey(remoteAddress), null) ?: return SessionRecord()
        return SessionRecord(b64d(stored))
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        val devices = mutableListOf<Int>()
        val prefix = KEY_SESSION_PREFIX + name
        for (key in prefs.all.keys) {
            if (key == prefix) {
                devices.add(1)
            } else if (key.startsWith(prefix + ".")) {
                key.removePrefix(prefix + ".").toIntOrNull()?.let { devices.add(it) }
            }
        }
        return devices
    }

    override fun storeSession(remoteAddress: SignalProtocolAddress, record: SessionRecord) {
        prefs.edit().putString(sessionKey(remoteAddress), b64e(record.serialize())).apply()
    }

    override fun containsSession(remoteAddress: SignalProtocolAddress): Boolean =
        prefs.contains(sessionKey(remoteAddress))

    override fun deleteSession(remoteAddress: SignalProtocolAddress) {
        prefs.edit().remove(sessionKey(remoteAddress)).apply()
    }

    override fun deleteAllSessions(name: String) {
        val editor = prefs.edit()
        val prefix = KEY_SESSION_PREFIX + name
        for (key in prefs.all.keys) {
            if (key == prefix || key.startsWith(prefix + ".")) editor.remove(key)
        }
        editor.apply()
    }

    private fun sessionKey(address: SignalProtocolAddress): String =
        if (address.deviceId <= 1) KEY_SESSION_PREFIX + address.name
        else KEY_SESSION_PREFIX + address.name + "." + address.deviceId

    companion object {
        const val DEVICE_ID = 1
        const val KEY_DEVICE_ID = "local_device_id"
        const val KEY_IDENTITY = "identity_key_pair"
        const val KEY_REGISTRATION_ID = "registration_id"
        const val KEY_CURRENT_SPK = "current_signed_prekey_id"
        private const val KEY_IDENTITY_PREFIX = "ident_"
        private const val KEY_PREKEY_PREFIX = "pk_"
        private const val KEY_SIGNED_PREKEY_PREFIX = "spk_"
        private const val KEY_SESSION_PREFIX = "sess_"
    }
}
