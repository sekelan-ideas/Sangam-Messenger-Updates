package com.sangam.messenger.core

import android.content.Context
import android.util.Base64
import com.sangam.messenger.data.Models.PreKeyBundleRow
import com.sangam.messenger.data.Supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.whispersystems.libsignal.DuplicateMessageException
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.InvalidMessageException
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.util.KeyHelper
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

private fun b64e(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
private fun b64d(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

/**
 * Signal Protocol encryption for Sangam, built on the Signal protocol
 * (X3DH key agreement + Double Ratchet messaging) via the vetted
 * libsignal library. The server only ever receives ciphertext.
 */
class CryptoManager(private val context: Context) {

    private val store = SignalStore(SecureStores.secure(context))
    private val userId: () -> String = {
        Supabase.client.auth.currentSessionOrNull()?.user?.id ?: error("Not signed in")
    }

    fun hasIdentity(): Boolean = SecureStores.secure(context).contains(SignalStore.KEY_IDENTITY)

    /** Generate the local identity + signed pre-key once, on this device. */
    @Synchronized
    fun ensureIdentity() {
        if (hasIdentity()) return
        val prefs = SecureStores.secure(context)
        val identity = KeyHelper.generateIdentityKeyPair()
        val registrationId = KeyHelper.generateRegistrationId(false)
        val spkId = Random.nextInt(1, 100000)
        val signedPreKey = KeyHelper.generateSignedPreKey(identity, spkId)
        prefs.edit()
            .putString(SignalStore.KEY_IDENTITY, b64e(identity.serialize()))
            .putInt(SignalStore.KEY_REGISTRATION_ID, registrationId)
            .putInt(SignalStore.KEY_CURRENT_SPK, spkId)
            .putString("spk_$spkId", b64e(signedPreKey.serialize()))
            .apply()
    }

    /**
     * Sign a fresh pre-key with the identity that is on disk right now.
     * After a backup restore the identity is a different one, and the old
     * signed pre-key no longer matches it - peers reject that key.
     */
    fun regenerateSignedPreKey() {
        val prefs = SecureStores.secure(context)
        val stored = prefs.getString(SignalStore.KEY_IDENTITY, null) ?: return
        val identity = IdentityKeyPair(b64d(stored))
        val spkId = Random.nextInt(1, 100000)
        val spk = KeyHelper.generateSignedPreKey(identity, spkId)
        prefs.edit()
            .putInt(SignalStore.KEY_CURRENT_SPK, spkId)
            .putString("spk_$spkId", b64e(spk.serialize()))
            .apply()
    }

    /** Upload public key material so others can start encrypted sessions with us. */
    suspend fun publishKeys() {
        ensureIdentity()
        if (store.localDeviceId() != 1) return
        val prefs = SecureStores.secure(context)
        val me = userId()
        val identity = store.getIdentityKeyPair()
        val spkId = prefs.getInt(SignalStore.KEY_CURRENT_SPK, 0)
        val signedPreKey = store.loadSignedPreKey(spkId)

        Supabase.client.from("prekey_bundles").upsert(
            PreKeyBundleRow(
                user_id = me,
                registration_id = store.getLocalRegistrationId(),
                identity_key = b64e(identity.publicKey.serialize()),
                signed_prekey_id = spkId,
                signed_prekey = b64e(signedPreKey.keyPair.publicKey.serialize()),
                signed_prekey_signature = b64e(signedPreKey.signature)
            ),
            onConflict = "user_id"
        )
    }

    /** Keep a healthy pool of one-time pre-keys on the server (public parts only). */
    suspend fun refillOneTimePreKeys(minPool: Int = 20, targetPool: Int = 100) {
        if (store.localDeviceId() != 1) return
        val me = userId()
        val existing = Supabase.client.from("prekey_bundles")
            .select(Columns.list("one_time_prekeys")) {
                filter { eq("user_id", me) }
                limit(1)
            }
        val remaining = existing.decodeList<PreKeysOnly>().firstOrNull()
            ?.one_time_prekeys?.size ?: 0
        if (remaining >= minPool) return

        val startId = Random.nextInt(1, 1_000_000)
        val newKeys = KeyHelper.generatePreKeys(startId, targetPool - remaining)
        if (newKeys.isEmpty()) return
        for (key in newKeys) store.storePreKey(key.id, key)

        val encoded = newKeys.map { "${it.id}:${b64e(it.keyPair.publicKey.serialize())}" }
        Supabase.client.postgrest.rpc(
            function = "add_one_time_prekeys",
            parameters = buildJsonObject { put("new_keys", JsonArray(encoded.map { JsonPrimitive(it) })) }
        )
    }

    /** Build an encrypted session with [peerId] if we do not have one yet (X3DH). */
    suspend fun ensureSession(peerId: String) {
        val address = SignalProtocolAddress(peerId, SignalStore.DEVICE_ID)
        val row = Supabase.client.from("prekey_bundles")
            .select(Columns.ALL) {
                filter { eq("user_id", peerId) }
                limit(1)
            }
            .decodeList<PreKeyBundleRow>()
            .firstOrNull() ?: if (store.containsSession(address)) return else error("This person is not on Sangam yet")

        if (store.containsSession(address)) {
            val savedIdent = runCatching { store.getIdentity(address)?.let { b64e(it.serialize()) } }.getOrNull()
            if (savedIdent == null || savedIdent == row.identity_key) return
            recordKeyChange(peerId, row.identity_key)
            store.deleteSession(address)
        } else {
            recordKeyIfUnseenOrChanged(peerId, row.identity_key)
        }

        // Atomically take one of their one-time pre-keys.
        val otp: String? = runCatching {
            Supabase.client.postgrest.rpc(
                function = "take_one_time_prekey",
                parameters = buildJsonObject { put("p_user_id", peerId) }
            ).decodeAs<String>().trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        processBundle(peerId, row, otp)
    }

    /** Establish a session from a bundle handed to us directly over Bluetooth,
     *  so two nearby phones can message with no server involved. */
    @Synchronized
    fun ensureSessionFromBundle(peerId: String, row: PreKeyBundleRow) {
        val address = SignalProtocolAddress(peerId, SignalStore.DEVICE_ID)
        if (store.containsSession(address)) return
        processBundle(peerId, row, row.one_time_prekeys?.firstOrNull())
    }

    /** My public pre-key bundle, to hand to a nearby phone over Bluetooth.
     *  A fresh one-time pre-key is generated and kept locally so the peer's
     *  first message can be decrypted. */
    fun nearbyBundle(): PreKeyBundleRow {
        ensureIdentity()
        val prefs = SecureStores.secure(context)
        val identity = store.getIdentityKeyPair()
        val spkId = prefs.getInt(SignalStore.KEY_CURRENT_SPK, 0)
        val signedPreKey = store.loadSignedPreKey(spkId)
        val otp = KeyHelper.generatePreKeys(Random.nextInt(1, 1_000_000), 1)
        otp.forEach { store.storePreKey(it.id, it) }
        return PreKeyBundleRow(
            user_id = "",
            registration_id = store.getLocalRegistrationId(),
            identity_key = b64e(identity.publicKey.serialize()),
            signed_prekey_id = spkId,
            signed_prekey = b64e(signedPreKey.keyPair.publicKey.serialize()),
            signed_prekey_signature = b64e(signedPreKey.signature),
            one_time_prekeys = otp.map { "${it.id}:${b64e(it.keyPair.publicKey.serialize())}" }
        )
    }

    fun localDeviceId(): Int = store.localDeviceId()

    fun setLocalDeviceId(id: Int) = store.setLocalDeviceId(id)

    /** Public Signal pre-key bundle for this device without generating throwaway one-time keys. */
    fun devicePreKeyBundle(): PreKeyBundleRow {
        ensureIdentity()
        val prefs = SecureStores.secure(context)
        val identity = store.getIdentityKeyPair()
        val spkId = prefs.getInt(SignalStore.KEY_CURRENT_SPK, 0)
        val signedPreKey = store.loadSignedPreKey(spkId)
        return PreKeyBundleRow(
            user_id = "",
            registration_id = store.getLocalRegistrationId(),
            identity_key = b64e(identity.publicKey.serialize()),
            signed_prekey_id = spkId,
            signed_prekey = b64e(signedPreKey.keyPair.publicKey.serialize()),
            signed_prekey_signature = b64e(signedPreKey.signature)
        )
    }

    /** Generate and store fresh one-time pre-keys for this device's row in `devices`. */
    fun generateDeviceOneTimePreKeys(count: Int = 25): List<String> {
        ensureIdentity()
        val startId = Random.nextInt(1, 1_000_000)
        val keys = KeyHelper.generatePreKeys(startId, count)
        for (key in keys) store.storePreKey(key.id, key)
        return keys.map { "${it.id}:${b64e(it.keyPair.publicKey.serialize())}" }
    }

    /** Ensure a Signal session with a specific (peerId, deviceId) pair, rotating if peer reinstalled. */
    suspend fun ensureSessionForDevice(
        peerId: String,
        deviceId: Int,
        deviceRow: com.sangam.messenger.data.Models.DeviceRow? = null
    ) {
        val address = SignalProtocolAddress(peerId, deviceId)
        if (store.containsSession(address)) {
            val savedIdent = runCatching { store.getIdentity(address)?.let { b64e(it.serialize()) } }.getOrNull()
            if (deviceRow?.identityKey.isNullOrBlank() || savedIdent == null || savedIdent == deviceRow?.identityKey) {
                return
            }
            recordKeyChange(peerId, deviceRow!!.identityKey!!)
            store.deleteSession(address)
        } else if (!deviceRow?.identityKey.isNullOrBlank() && deviceId == 1) {
            recordKeyIfUnseenOrChanged(peerId, deviceRow!!.identityKey!!)
        }
        if (deviceRow != null &&
            deviceRow.registrationId != null &&
            !deviceRow.identityKey.isNullOrBlank() &&
            deviceRow.signedPrekeyId != null &&
            !deviceRow.signedPrekey.isNullOrBlank() &&
            !deviceRow.signedPrekeySignature.isNullOrBlank()
        ) {
            val otp: String? = runCatching {
                Supabase.client.postgrest.rpc(
                    function = "take_device_one_time_prekey",
                    parameters = buildJsonObject {
                        put("p_user_id", peerId)
                        put("p_device_key", deviceId)
                    }
                ).decodeAs<String>().trim()
            }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: deviceRow.oneTimePrekeys?.firstOrNull()

            val bundleRow = PreKeyBundleRow(
                user_id = peerId,
                registration_id = deviceRow.registrationId,
                identity_key = deviceRow.identityKey,
                signed_prekey_id = deviceRow.signedPrekeyId,
                signed_prekey = deviceRow.signedPrekey,
                signed_prekey_signature = deviceRow.signedPrekeySignature
            )
            processBundleForDevice(peerId, deviceId, bundleRow, otp)
            return
        }
        if (deviceId == 1) {
            ensureSession(peerId)
        }
    }

    private fun processBundleForDevice(
        peerId: String,
        deviceId: Int,
        row: PreKeyBundleRow,
        otp: String?
    ) {
        val address = SignalProtocolAddress(peerId, deviceId)
        var preKeyId: Int? = null
        var preKey: ECPublicKey? = null
        if (otp != null && otp.contains(':')) {
            val idPart = otp.substringBefore(':')
            val keyPart = otp.substringAfter(':')
            preKeyId = idPart.toIntOrNull()
            preKey = runCatching { Curve.decodePoint(b64d(keyPart), 0) }.getOrNull()
        }
        val bundle = PreKeyBundle(
            row.registration_id,
            deviceId,
            preKeyId ?: 0,
            preKey,
            row.signed_prekey_id,
            Curve.decodePoint(b64d(row.signed_prekey), 0),
            b64d(row.signed_prekey_signature),
            IdentityKey(b64d(row.identity_key), 0)
        )
        SessionBuilder(store, address).process(bundle)
    }

    /**
     * Encrypt one Signal ciphertext envelope per recipient device and per sender's
     * own other linked devices. Never appends plaintext to the output.
     */
    suspend fun encryptForDevices(
        peerId: String,
        peerDevices: List<com.sangam.messenger.data.Models.DeviceRow>,
        myOtherDevices: List<com.sangam.messenger.data.Models.DeviceRow>,
        plaintext: ByteArray
    ): String {
        ensureIdentity()
        val me = runCatching { userId() }.getOrDefault("")
        val myDevId = localDeviceId()
        val isSelfOnly = peerId == me && peerDevices.isEmpty()

        val validPeerDevs = if (isSelfOnly) {
            emptyList()
        } else {
            val filtered = peerDevices.filter {
                it.deviceKey == 1 || (!it.identityKey.isNullOrBlank() && it.registrationId != null)
            }.distinctBy { it.deviceKey }
            if (filtered.none { it.deviceKey == 1 }) {
                listOf(com.sangam.messenger.data.Models.DeviceRow(id = "", userId = peerId, deviceKey = 1)) + filtered
            } else {
                filtered
            }
        }

        val rawSelfDevs = myOtherDevices.filter {
            it.deviceKey != myDevId && (it.deviceKey == 1 || (!it.identityKey.isNullOrBlank() && it.registrationId != null))
        }.distinctBy { it.deviceKey }
        val validSelfDevs = if (myDevId != 1 && me.isNotBlank() && rawSelfDevs.none { it.deviceKey == 1 }) {
            listOf(com.sangam.messenger.data.Models.DeviceRow(id = "", userId = me, deviceKey = 1)) + rawSelfDevs
        } else {
            rawSelfDevs
        }

        if (!isSelfOnly && myDevId == 1 && validSelfDevs.isEmpty() &&
            validPeerDevs.size == 1 && validPeerDevs[0].deviceKey == 1
        ) {
            ensureSessionForDevice(peerId, 1, validPeerDevs.firstOrNull())
            return encrypt(peerId, plaintext)
        }

        val segments = mutableListOf<String>()
        for (d in validPeerDevs) {
            runCatching {
                ensureSessionForDevice(peerId, d.deviceKey, d)
                val cipher = SessionCipher(store, SignalProtocolAddress(peerId, d.deviceKey))
                val msg = cipher.encrypt(plaintext)
                segments.add("d${d.deviceKey}@$myDevId:${msg.type}:${b64e(msg.serialize())}")
            }
        }
        for (sd in validSelfDevs) {
            val selfUid = sd.userId.ifBlank { me }
            runCatching {
                ensureSessionForDevice(selfUid, sd.deviceKey, sd)
                val cipher = SessionCipher(store, SignalProtocolAddress(selfUid, sd.deviceKey))
                val msg = cipher.encrypt(plaintext)
                segments.add("s${sd.deviceKey}@$myDevId:${msg.type}:${b64e(msg.serialize())}")
            }
        }
        if (segments.isEmpty()) {
            if (isSelfOnly) return ""
            ensureSession(peerId)
            return encrypt(peerId, plaintext)
        }
        return segments.joinToString(";")
    }

    /**
     * Receiver-side filtering by target device: selects only the ciphertext
     * segment addressed to this device's `localDeviceId()` and ignores segments
     * meant for other devices so their Signal ratchets are never touched.
     */
    fun decryptForDevice(peerId: String, stored: String, isSelfCopy: Boolean = false): ByteArray {
        val myDevId = localDeviceId()
        val clean = stored.substringBefore("|w:").trim()
        if (clean.isEmpty()) error("Empty ciphertext")
        val isMulti = clean.startsWith("d") || clean.startsWith("s") ||
            clean.contains(";d") || clean.contains(";s")
        if (!isMulti) {
            if (isSelfCopy || myDevId != 1) {
                error("Single-device ciphertext not addressed to device #$myDevId")
            }
            return decrypt(peerId, clean)
        }
        val parts = clean.split(";")
        val primaryPrefix = (if (isSelfCopy) "s" else "d") + myDevId + "@"
        val matched = parts.firstOrNull { it.startsWith(primaryPrefix) }
            ?: (if (!isSelfCopy) parts.firstOrNull { it.startsWith("d$myDevId@") } else null)
            ?: (if (!isSelfCopy && myDevId == 1) parts.firstOrNull { !it.startsWith("d") && !it.startsWith("s") } else null)
            ?: error("No ciphertext segment for device #$myDevId")

        if (!matched.startsWith("d") && !matched.startsWith("s")) {
            return decrypt(peerId, matched)
        }
        val afterAt = matched.substringAfter("@")
        val firstColon = afterAt.indexOf(':')
        if (firstColon <= 0) error("Malformed multi-device header")
        val senderDevId = afterAt.substring(0, firstColon).toIntOrNull() ?: 1
        val payload = afterAt.substring(firstColon + 1)
        val splitAt = payload.indexOf(':')
        if (splitAt <= 0) error("Corrupted multi-device payload")
        val type = payload.substring(0, splitAt).toIntOrNull() ?: error("Corrupted message type")
        val bytes = b64d(payload.substring(splitAt + 1))
        val addr = SignalProtocolAddress(peerId, senderDevId)
        val cipher = SessionCipher(store, addr)
        return try {
            when (type) {
                CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(bytes))
                else -> cipher.decrypt(SignalMessage(bytes))
            }
        } catch (e: DuplicateMessageException) {
            throw e
        } catch (e: InvalidMessageException) {
            val msg = e.message.orEmpty()
            val isRealMacFailure = msg.contains("Bad Mac", ignoreCase = true) ||
                msg.contains("MAC", ignoreCase = true)
            if (type != CiphertextMessage.PREKEY_TYPE && isRealMacFailure) {
                runCatching { store.deleteSession(addr) }
            }
            throw e
        }
    }

    private fun processBundle(peerId: String, row: PreKeyBundleRow, otp: String?) {
        processBundleForDevice(peerId, SignalStore.DEVICE_ID, row, otp)
    }

    /** Encrypt for [peerId] (device 1) using Signal Double Ratchet; returns "<type>:<base64>" only. */
    fun encrypt(peerId: String, plaintext: ByteArray): String {
        ensureIdentity()
        val address = SignalProtocolAddress(peerId, SignalStore.DEVICE_ID)
        val cipher = SessionCipher(store, address)
        val message = cipher.encrypt(plaintext)
        return "${message.type}:${b64e(message.serialize())}"
    }

    /** Decrypt a stored Signal ciphertext from [peerId] (device 1). */
    fun decrypt(peerId: String, stored: String): ByteArray {
        val mainPart = stored.substringBefore("|w:").trim()
        val address = SignalProtocolAddress(peerId, SignalStore.DEVICE_ID)
        val cipher = SessionCipher(store, address)
        val splitAt = mainPart.indexOf(':')
        if (splitAt <= 0) error("Corrupted message")
        val type = mainPart.substring(0, splitAt).toIntOrNull() ?: error("Corrupted message")
        val bytes = b64d(mainPart.substring(splitAt + 1))
        return try {
            when (type) {
                CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(bytes))
                else -> cipher.decrypt(SignalMessage(bytes))
            }
        } catch (e: DuplicateMessageException) {
            throw e
        } catch (e: InvalidMessageException) {
            val msg = e.message.orEmpty()
            val isRealMacFailure = msg.contains("Bad Mac", ignoreCase = true) ||
                msg.contains("MAC", ignoreCase = true)
            if (type != CiphertextMessage.PREKEY_TYPE && isRealMacFailure) {
                runCatching { store.deleteSession(address) }
            }
            throw e
        }
    }

    fun localIdentityPubB64(): String {
        ensureIdentity()
        return b64e(store.getIdentityKeyPair().publicKey.serialize())
    }

    /** Derive a 256-bit E2EE call key using Curve25519 ECDH + HKDFv3 ("SangamCallE2EE:<callId>"). */
    fun deriveCallKey(callId: String, peerIdentityPubB64: String): SecretKey? = runCatching {
        ensureIdentity()
        val myPriv = store.getIdentityKeyPair().privateKey
        val rawPub = b64d(peerIdentityPubB64)
        val peerPub = if (rawPub.size == 33) Curve.decodePoint(rawPub, 0)
        else Curve.decodePoint(byteArrayOf(0x05.toByte()) + rawPub, 0)
        val shared = Curve.calculateAgreement(peerPub, myPriv)
        val keyBytes = org.whispersystems.libsignal.kdf.HKDFv3().deriveSecrets(
            shared,
            "SangamCallE2EE:$callId".toByteArray(Charsets.UTF_8),
            32
        )
        SecretKeySpec(keyBytes, "AES")
    }.getOrNull()

    /** Encrypt a voice or video call frame end-to-end with AES-256-GCM. */
    fun encryptCallFrame(callId: String, peerIdentityPubB64: String, plain: String): String = runCatching {
        val key = deriveCallKey(callId, peerIdentityPubB64) ?: return plain
        val myPubB64 = localIdentityPubB64()
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        "E:$myPubB64:${b64e(iv)}:${b64e(ct)}"
    }.getOrDefault(plain)

    /** Decrypt an incoming E2EE voice or video call frame. Returns Pair(decryptedPlain, senderPubB64). */
    fun decryptCallFrame(callId: String, defaultPeerPubB64: String?, payload: String): Pair<String?, String?> {
        if (payload.startsWith("K:")) {
            val kPub = payload.substring(2).trim()
            return null to kPub.ifEmpty { null }
        }
        if (!payload.startsWith("E:")) return payload to null
        return runCatching {
            val parts = payload.substring(2).split(":")
            val (senderPub, ivB64, ctB64) = when {
                parts.size >= 3 -> Triple(parts[0], parts[1], parts[2])
                parts.size == 2 && !defaultPeerPubB64.isNullOrBlank() -> Triple(defaultPeerPubB64, parts[0], parts[1])
                else -> return null to null
            }
            val key = deriveCallKey(callId, senderPub) ?: return null to senderPub
            val iv = b64d(ivB64)
            val ct = b64d(ctB64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val pt = String(cipher.doFinal(ct), Charsets.UTF_8)
            pt to senderPub
        }.getOrDefault(null to null)
    }

    private fun recordKeyChange(peerId: String, newIdentityKeyB64: String) {
        val prefs = SecureStores.secure(context)
        prefs.edit()
            .putString("tofu_key_$peerId", newIdentityKeyB64)
            .putBoolean("key_changed_$peerId", true)
            .apply()
    }

    fun recordKeyIfUnseenOrChanged(peerId: String, identityKeyB64: String) {
        if (identityKeyB64.isBlank()) return
        val prefs = SecureStores.secure(context)
        val seen = prefs.getString("tofu_key_$peerId", null)
        if (seen == null) {
            prefs.edit().putString("tofu_key_$peerId", identityKeyB64).apply()
        } else if (seen != identityKeyB64) {
            recordKeyChange(peerId, identityKeyB64)
        }
    }

    fun checkAndRecordPeerDevices(peerId: String, deviceKeys: List<Int>) {
        if (deviceKeys.isEmpty()) return
        val prefs = SecureStores.secure(context)
        val currentSet = deviceKeys.sorted().joinToString(",")
        val prevSet = prefs.getString("tofu_devs_$peerId", null)
        if (prevSet == null) {
            prefs.edit().putString("tofu_devs_$peerId", currentSet).apply()
        } else {
            val prevIds = prevSet.split(",").filter { it.isNotBlank() }.toSet()
            val added = deviceKeys.any { it.toString() !in prevIds }
            if (added) {
                prefs.edit()
                    .putString("tofu_devs_$peerId", currentSet)
                    .putBoolean("new_device_$peerId", true)
                    .apply()
            } else if (prevSet != currentSet) {
                prefs.edit().putString("tofu_devs_$peerId", currentSet).apply()
            }
        }
    }

    fun isKeyChanged(peerId: String): Boolean =
        SecureStores.secure(context).getBoolean("key_changed_$peerId", false)

    fun isNewDeviceAdded(peerId: String): Boolean =
        SecureStores.secure(context).getBoolean("new_device_$peerId", false)

    fun acknowledgeSecurityNotice(peerId: String) {
        SecureStores.secure(context).edit()
            .putBoolean("key_changed_$peerId", false)
            .putBoolean("new_device_$peerId", false)
            .apply()
    }

    /**
     * Compute the standard 60-digit Signal v0 safety number (12 groups of 5 digits)
     * using libsignal's NumericFingerprintGenerator (5200 iterations of SHA-512).
     */
    fun computeSafetyNumber(peerId: String, peerIdentityPubB64: String): String? = runCatching {
        ensureIdentity()
        val myIdBytes = userId().toByteArray(Charsets.UTF_8)
        val myKey = store.getIdentityKeyPair().publicKey
        val rawPeer = b64d(peerIdentityPubB64)
        val peerKey = IdentityKey(
            if (rawPeer.size == 33) rawPeer else byteArrayOf(0x05.toByte()) + rawPeer,
            0
        )
        val generator = org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator(5200)
        val fp = generator.createFor(
            0,
            myIdBytes,
            myKey,
            peerId.toByteArray(Charsets.UTF_8),
            peerKey
        )
        fp.displayableFingerprint.displayText.chunked(5).joinToString(" ")
    }.getOrNull()

    @Serializable
    data class PreKeysOnly(val one_time_prekeys: List<String> = emptyList())
}

/** AES-256-GCM for image payloads (the key travels inside the Signal Protocol message). */
object ImageCrypto {

    fun encrypt(data: ByteArray): Triple<ByteArray, String, String> {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key: SecretKey = keyGenerator.generateKey()
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data)
        return Triple(encrypted, b64e(key.encoded), b64e(iv))
    }

    fun decrypt(data: ByteArray, keyB64: String, ivB64: String): ByteArray {
        val key = SecretKeySpec(b64d(keyB64), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64d(ivB64)))
        return cipher.doFinal(data)
    }
}
