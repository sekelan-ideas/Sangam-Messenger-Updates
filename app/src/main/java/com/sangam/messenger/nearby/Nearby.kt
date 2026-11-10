package com.sangam.messenger.nearby

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sangam.messenger.data.Models.PreKeyBundleRow
import com.sangam.messenger.data.Sangam

/**
 * Public state and API for Nearby mode - offline messaging between Sangam
 * users who are physically close, carried over Bluetooth. Off by default;
 * the user turns it on, and normal messaging is never affected.
 */
object Nearby {

    var enabled by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var peers by mutableStateOf<List<NearbyPeer>>(emptyList())
        private set

    private var engine: NearbyLink? = null
    private var uidCache: String? = null

    /** Fired when a nearby message arrives, so an open chat can refresh. */
    var onMessage: ((String) -> Unit)? = null

    /** Bumped on every nearby message received, so open chats can refresh. */
    var inbox by mutableStateOf(0)
        private set

    /** My account id. A blank answer is never cached, so a later call can
     *  still succeed once the session is ready. */
    fun myUid(): String {
        val cached = uidCache
        if (!cached.isNullOrEmpty()) return cached
        val v = Sangam.userId() ?: Sangam.cachedUid() ?: ""
        if (v.isNotEmpty()) uidCache = v
        return v
    }
    fun myName(): String = Sangam.nearbyName()
    fun myUsername(): String = Sangam.nearbyUsername()

    /** A fresh bundle for every handshake, so each peer gets its own key. */
    fun myBundle(): PreKeyBundleRow? = Sangam.nearbyBundle()

    /** Turn Nearby mode on or off (starts/stops the foreground service). */
    fun setEnabled(context: Context, on: Boolean) {
        if (on == enabled) return
        enabled = on
        val ctx = context.applicationContext
        val i = Intent(ctx, NearbyService::class.java)
        runCatching {
            if (on) {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } else {
                ctx.stopService(i)
            }
        }
    }

    // ---- called from the service / engine ----

    internal fun attach(e: NearbyLink) { engine = e }
    internal fun detach() {
        engine = null
        peers = emptyList()
        status = null
    }

    internal fun peerSeen(peer: NearbyPeer) {
        peers = (peers.filterNot { it.id == peer.id } + peer)
            .sortedByDescending { it.lastSeen }
    }

    internal fun peerLost(id: String) { peers = peers.filterNot { it.id == id } }

    internal fun linkChanged(peerId: String, connected: Boolean) {
        peers = peers.map { if (it.id == peerId) it.copy(connected = connected) else it }
    }

    internal fun note(text: String) { status = text }

    /** A reassembled frame arrived from a nearby peer. */
    internal fun frame(peerId: String, frame: NearbyFrame) {
        if (frame.k == "h") {
            frame.bundle?.let { Sangam.establishNearbySession(peerId, it) }
            return
        }
        if (frame.k == "m" && !frame.ct.isNullOrBlank()) {
            if (Sangam.receiveNearby(peerId, frame.ct)) {
                inbox += 1
                onMessage?.invoke(peerId)
            }
        }
    }

    fun isConnected(peerId: String): Boolean = engine?.isConnected(peerId) == true

    /** Send a text message to a nearby peer over Bluetooth. */
    fun sendText(peerId: String, text: String): Boolean {
        val e = engine ?: return false
        if (!e.isConnected(peerId)) return false
        val row = Sangam.sendNearbyText(peerId, text) ?: return false
        e.send(peerId, NearbyFrame(k = "m", uid = myUid(), ct = row.ciphertext))
        return true
    }
}
