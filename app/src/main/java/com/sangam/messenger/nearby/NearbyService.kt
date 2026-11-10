package com.sangam.messenger.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sangam.messenger.R

/**
 * Keeps the Nearby link alive while Nearby mode is on. Android requires a
 * foreground service for this, which is why there is a small ongoing
 * notification for as long as it runs.
 */
class NearbyService : Service() {

    private var engine: NearbyLink? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { startForegroundCompat() }
        val e = NearbyLink(applicationContext, listener)
        engine = e
        Nearby.attach(e)
        runCatching { e.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { engine?.stop() }
        Nearby.detach()
        engine = null
        super.onDestroy()
    }

    private val listener = object : NearbyLink.Listener {
        override fun onPeerSeen(peer: NearbyPeer) { Nearby.peerSeen(peer) }
        override fun onPeerLost(id: String) { Nearby.peerLost(id) }
        override fun onFrame(peerId: String, frame: NearbyFrame) { Nearby.frame(peerId, frame) }
        override fun onLinkChanged(peerId: String, connected: Boolean) {
            Nearby.linkChanged(peerId, connected)
        }
        override fun onStatus(text: String) { Nearby.note(text) }
    }

    private fun startForegroundCompat() {
        val channelId = "sangam_nearby"
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr != null && mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(channelId, "Nearby", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sangam Nearby is on")
            .setContentText("Looking for Sangam friends nearby over Wi-Fi")
            .setSmallIcon(R.drawable.ic_stat_sangam)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(8801, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(8801, n)
        }
    }
}
