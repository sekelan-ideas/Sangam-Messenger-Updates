package com.sangam.messenger.nearby

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * The Nearby transport: a direct Wi-Fi connection between two phones, then one
 * ordinary TCP socket carrying length-prefixed JSON frames.
 *
 * Finding the other Sangam phone is the tricky part. A phone's Wi-Fi Direct
 * name is the owner's own name ("Muthu's S24 Ultra"), not something we can set
 * reliably - so instead we advertise a small Wi-Fi Direct *service* and look
 * for that. If that finds nothing we fall back to simply trying the devices
 * nearby, one at a time, and keeping whichever answers our handshake.
 */
@SuppressLint("MissingPermission")
class NearbyLink(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onPeerSeen(peer: NearbyPeer)
        fun onPeerLost(id: String)
        fun onFrame(peerId: String, frame: NearbyFrame)
        fun onLinkChanged(peerId: String, connected: Boolean)
        fun onStatus(text: String)
    }

    private class Link(val socket: Socket, val out: DataOutputStream)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val main = Handler(Looper.getMainLooper())

    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var channel: WifiP2pManager.Channel? = null

    private var running = false
    /** peerId -> open link (only known once the handshake arrives). */
    private val links = ConcurrentHashMap<String, Link>()
    /** Devices we have already tried to join. */
    private val tried = ConcurrentHashMap<String, Long>()
    /** Devices we could try, by address. */
    private val candidates = LinkedHashMap<String, WifiP2pDevice>()
    private var serverSocket: ServerSocket? = null
    private var dialing = false
    private var linked = false


    // ------------------------------------------------------------- receiver

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        listener.onStatus("Turn Wi-Fi on for Nearby")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestInfo()
            }
        }
    }

    // ------------------------------------------------------------ lifecycle

    fun start() {
        if (running) return
        if (Build.VERSION.SDK_INT < 29) {
            listener.onStatus("Nearby needs Android 10 or newer")
            return
        }
        val m = manager ?: run {
            listener.onStatus("Wi-Fi Direct is not available on this phone"); return
        }
        if (wifi?.isWifiEnabled != true) {
            listener.onStatus("Turn Wi-Fi on for Nearby")
            return
        }
        running = true
        channel = m.initialize(context, Looper.getMainLooper(), null)
        // Advertise as a Sangam phone so the other side can tell us apart from
        // a printer. Called by reflection because setDeviceName only exists
        // from API 29 and will not resolve directly at this compile level.
        val ch = channel
        if (ch != null) {
            runCatching {
                val tag = com.sangam.messenger.data.Sangam.userId()?.take(6) ?: "phone"
                val name = NearbyProtocol.NAME_PREFIX + tag
                val meth = m.javaClass.getMethod(
                    "setDeviceName", ch.javaClass.interfaces.firstOrNull() ?: ch.javaClass, String::class.java
                )
                meth.invoke(m, ch, name)
            }
        }
        // Advertise ourselves as a Sangam phone. Without this the other phone
        // sees a generic device name and cannot tell us from a printer, so it
        // never offers to connect.
        runCatching {
            ContextCompat.registerReceiver(
                context, receiver,
                IntentFilter().apply {
                    addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                    addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                    addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        listener.onStatus("Searching for Sangam friends nearby…")
        // A link may already exist (set up earlier), so check for one.
        requestInfo()
        runCatching { m.discoverPeers(channel, null) }
        // Give discovery a moment, then start trying devices one at a time.
        main.postDelayed({ tryNextCandidate() }, 3000)
    }

    fun stop() {
        running = false
        runCatching { context.unregisterReceiver(receiver) }
        for (l in links.values) runCatching { l.socket.close() }
        links.clear()
        tried.clear()
        candidates.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { manager?.cancelConnect(channel, null) }
        runCatching { manager?.removeGroup(channel, null) }
        channel = null
        dialing = false
        linked = false
    }

    fun isConnected(peerId: String): Boolean = links.containsKey(peerId)

    // ------------------------------------------------------------ discovery

    private fun requestPeers() {
        val m = manager ?: return
        runCatching {
            m.requestPeers(channel) { peers ->
                for (d in peers.deviceList) {
                    val addr = d.deviceAddress ?: continue
                    candidates[addr] = d
                    // A device that named itself "Sangam-…" is one of ours.
                    if ((d.deviceName ?: "").startsWith(NearbyProtocol.NAME_PREFIX)) {
                        join(d, "Found a Sangam friend nearby - joining…")
                    }
                }
            }
        }
    }

    /** Join a device we know is a Sangam phone. */
    private fun join(d: WifiP2pDevice, why: String) {
        if (linked || dialing) return
        val addr = d.deviceAddress ?: return
        val last = tried[addr] ?: 0L
        if (System.currentTimeMillis() - last < 15_000L) return
        tried[addr] = System.currentTimeMillis()
        listener.onPeerSeen(
            NearbyPeer(d.deviceName ?: addr, d.deviceName ?: "", "", 0, System.currentTimeMillis(), false)
        )
        listener.onStatus(why)
        dialing = true
        connect(d)
        main.postDelayed({ if (!linked) giveUpAndRescan() }, 15_000)
    }

    /**
     * Nothing advertised itself as Sangam, so try the devices around us one at
     * a time and keep whichever answers our handshake.
     */
    private fun tryNextCandidate() {
        if (!running || linked || dialing) return
        val next = candidates.values.firstOrNull {
            val a = it.deviceAddress
            a != null && !tried.containsKey(a)
        }
        if (next == null) {
            main.postDelayed({ tryNextCandidate() }, 4000)
            return
        }
        listener.onStatus("Trying a nearby device…")
        dialing = true
        next.deviceAddress?.let { tried[it] = System.currentTimeMillis() }
        connect(next)
        main.postDelayed({ if (!linked) giveUpAndRescan() }, 14_000)
    }

    private fun giveUpAndRescan() {
        if (linked || !running) return
        dialing = false
        runCatching { manager?.removeGroup(channel, null) }
        runCatching { serverSocket?.close() }
        serverSocket = null
        listener.onStatus("Not a Sangam phone - looking again…")
        runCatching { manager?.discoverPeers(channel, null) }
        main.postDelayed({ tryNextCandidate() }, 4000)
    }

    private fun connect(d: WifiP2pDevice) {
        val m = manager ?: return
        val cfg = WifiP2pConfig().apply {
            deviceAddress = d.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 15
        }
        runCatching {
            m.connect(channel, cfg, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { listener.onStatus("Joining the Wi-Fi link…") }
                override fun onFailure(reason: Int) {
                    dialing = false
                    listener.onStatus("Could not join the Wi-Fi link ($reason)")
                }
            })
        }
    }

    // ----------------------------------------------------------- connection

    private fun requestInfo() {
        val m = manager ?: return
        runCatching {
            m.requestConnectionInfo(channel) { info ->
                if (!info.groupFormed) {
                    if (linked) {
                        listener.onStatus("The other phone disconnected")
                        listener.onLinkChanged("", false)
                    }
                    linked = false
                    dialing = false
                    links.clear()
                    runCatching { serverSocket?.close() }
                    serverSocket = null
                    return@requestConnectionInfo
                }
                val host = info.groupOwnerAddress?.hostAddress
                if (info.isGroupOwner) {
                    listener.onStatus("Linked - waiting for the other phone…")
                    startServer()
                } else {
                    listener.onStatus("Linked - saying hello…")
                    dialIn(host)
                }
            }
        }
    }

    private fun startServer() {
        if (serverSocket != null) return
        thread(name = "nearby-host") {
            runCatching {
                val ss = ServerSocket(NearbyProtocol.PORT)
                serverSocket = ss
                while (running && !ss.isClosed) {
                    val s = ss.accept()
                    open(s)
                }
            }
        }
    }

    private fun dialIn(host: String?) {
        if (host.isNullOrBlank()) {
            listener.onStatus("Could not reach the other phone")
            return
        }
        thread(name = "nearby-dial") {
            var attempt = 0
            while (running && attempt < 8 && links.isEmpty()) {
                attempt++
                val s = runCatching { Socket(host, NearbyProtocol.PORT) }.getOrNull()
                if (s != null) { open(s); return@thread }
                Thread.sleep(1200)
            }
        }
    }

    // --------------------------------------------------------------- socket

    private fun open(s: Socket) {
        runCatching { s.tcpNoDelay = true }
        val out = DataOutputStream(s.getOutputStream())
        linked = true
        dialing = false
        main.post { listener.onStatus("Linked - saying hello…") }
        // The hello MUST go out on a background thread. Android throws
        // NetworkOnMainThreadException for a socket write on the main thread.
        thread(name = "nearby-hello") {
            if (!write(out, helloFrame())) {
                main.post { listener.onStatus("Could not send our details") }
            }
        }
        main.postDelayed({
            if (links.isEmpty()) listener.onStatus("Linked, but no reply from the other phone")
        }, 12_000)
        thread(name = "nearby-read") {
            runCatching {
                val ins = DataInputStream(s.getInputStream())
                while (running && !s.isClosed) {
                    val len = ins.readInt()
                    if (len <= 0 || len > NearbyProtocol.MAX_FRAME) break
                    val buf = ByteArray(len)
                    ins.readFully(buf)
                    val frame = runCatching {
                        json.decodeFromString(NearbyFrame.serializer(), String(buf, Charsets.UTF_8))
                    }.getOrNull() ?: continue
                    handleFrame(s, out, frame)
                }
            }
            runCatching { s.close() }
            val gone = links.entries.filter { it.value.socket == s }.map { it.key }
            for (g in gone) {
                links.remove(g)
                main.post { listener.onLinkChanged(g, false) }
            }
            linked = false
            if (gone.isNotEmpty()) main.post { listener.onStatus("The other phone disconnected") }
        }
    }

    private fun helloFrame() = NearbyFrame(
        k = "h",
        uid = Nearby.myUid(),
        name = Nearby.myName(),
        username = Nearby.myUsername(),
        bundle = Nearby.myBundle()
    )

    private fun handleFrame(s: Socket, out: DataOutputStream, frame: NearbyFrame) {
        if (frame.k == "h") {
            if (frame.uid.isBlank()) {
                main.post { listener.onStatus("The other phone is not signed in") }
                return
            }
            links[frame.uid] = Link(s, out)
            main.post {
                listener.onPeerSeen(
                    NearbyPeer(
                        frame.uid, frame.name, frame.username, 0,
                        System.currentTimeMillis(), true
                    )
                )
                listener.onLinkChanged(frame.uid, true)
                listener.onStatus("Connected to " + frame.name.ifBlank { "a nearby phone" })
            }
        }
        listener.onFrame(frame.uid, frame)
    }

    /** Send a frame to a peer we are linked with. */
    fun send(peerId: String, frame: NearbyFrame) {
        val link = links[peerId] ?: return
        thread(name = "nearby-send") { write(link.out, frame) }
    }

    private fun write(out: DataOutputStream, frame: NearbyFrame): Boolean = runCatching {
        val bytes = json.encodeToString(NearbyFrame.serializer(), frame)
            .toByteArray(Charsets.UTF_8)
        synchronized(out) {
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
        }
        true
    }.getOrDefault(false)
}
