package com.sangam.messenger.nearby

import com.sangam.messenger.data.Models.PreKeyBundleRow
import kotlinx.serialization.Serializable

/**
 * Wire details for the Nearby link.
 *
 * The two phones join a direct Wi-Fi connection (Wi-Fi Direct) and then talk
 * over one ordinary TCP socket. That is far simpler and far more reliable
 * than the Bluetooth GATT path it replaces: no advert size limit, no packet
 * size negotiation, no service discovery, no subscribe step - just a socket.
 */
object NearbyProtocol {

    /** The port the group owner listens on. */
    const val PORT = 8988

    /** Every Sangam phone names itself "Sangam-<id>" so the other phone can
     *  tell a Sangam user from any other Wi-Fi Direct device nearby. */
    const val NAME_PREFIX = "Sangam-"

    /** Longest frame we will accept, as a sanity limit. */
    const val MAX_FRAME = 4_000_000
}

/** A nearby Sangam user found over Wi-Fi Direct. */
data class NearbyPeer(
    val id: String,
    val name: String,
    val username: String,
    val rssi: Int,
    val lastSeen: Long,
    val connected: Boolean = false
)

/**
 * One frame on the wire: the opening handshake (k = "h", carrying the
 * pre-key bundle) or a message (k = "m", carrying the ciphertext).
 */
@Serializable
data class NearbyFrame(
    val k: String,
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val bundle: PreKeyBundleRow? = null,
    val ct: String? = null
)
