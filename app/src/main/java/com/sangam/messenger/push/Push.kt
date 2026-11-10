package com.sangam.messenger.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sangam.messenger.BuildConfig
import com.sangam.messenger.R
import com.sangam.messenger.data.Sangam
import io.github.jan.supabase.gotrue.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Push notification plumbing. The app asks Google for this device's
 * notification token and uploads it to our server; after sending a message
 * it quietly asks our server to wake the friend's phone. Message content
 * never travels through Firebase - only "you have a message".
 */
object Push {

    /** The chat currently on screen (so we stay quiet for messages you can already see). */
    @Volatile
    var activeChatPeerId: String? = null

    const val CHANNEL_ID = "sangam_messages"
    const val CALL_CHANNEL_ID = "sangam_calls"

    /** Sangam's own teal, used as the notification accent and LED colour. */
    private val TEAL = 0xFF3CB3D6.toInt()

    /** A short double pulse - a gentle "someone thought of you". */
    private val VIBRATE_PATTERN = longArrayOf(0, 45, 70, 45)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * One shared connection: reusing it makes the wake-up call much faster
     * than building a brand-new client for every message sent.
     */
    private val http by lazy {
        HttpClient(Android) {
            install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        }
    }

    /** The notification channel as Sangam wants it. */
    /** Separate, louder channel so a ringing call can wake the screen. */
    fun callChannel(): NotificationChannel =
        NotificationChannel(
            CALL_CHANNEL_ID, "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_RINGTONE
                ),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = VIBRATE_PATTERN
            enableLights(true)
            lightColor = TEAL
            setBypassDnd(false)
        }

    fun channel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_ID, "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            if (!Sangam.notifSoundOn()) setSound(null, null)
            if (Sangam.notifVibrateOn()) {
                enableVibration(true)
                vibrationPattern = VIBRATE_PATTERN
            } else enableVibration(false)
            enableLights(true)
            lightColor = TEAL
        }

    /** (Re)create the notification channel with the user's sound and
     *  vibration choices. Channels can't be updated after creation, so we
     *  delete and rebuild it whenever a setting changes. */
    fun applyNotificationSettings(context: android.content.Context) {
        runCatching {
            val manager = context.getSystemService(
                android.content.Context.NOTIFICATION_SERVICE
            ) as android.app.NotificationManager
            manager.deleteNotificationChannel(CHANNEL_ID)
            manager.createNotificationChannel(channel())
        }
    }

    /** Ask Google for this device's push token and upload it to our server. */
    fun registerToken() {
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> Sangam.saveFcmToken(token) }
        }
    }

    /** Fire-and-forget: tell our server this peer should get a notification. */
    fun fire(peerId: String) {
        scope.launch {
            runCatching {
                val session = Sangam.client.auth.currentSessionOrNull() ?: return@launch
                val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/push"
                http.post(url) {
                    header(HttpHeaders.Authorization, "Bearer " + session.accessToken)
                    contentType(ContentType.Application.Json)
                    setBody("{\"to\":\"" + peerId + "\"}")
                }
            }
        }
    }
}

/**
 * Receives Firebase messages and shows them as Android notifications:
 * Sangam's own look - the Tamil letter in the status bar, a teal accent,
 * and a proper conversation-style card.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Sangam.saveFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Showing a notification must never be able to crash the app.
        runCatching { showNotification(message) }
    }

    private fun showNotification(message: RemoteMessage) {
        val data = message.data
        val peerId = data["peer"] ?: return
        // Ask the app what was actually sent, so the card says what it is
        // (photo, video, voice note, document...) instead of a blank line.
        val real = runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    // This may be a fresh process - load the session and keys
                    // first, or there is nothing to read the message with.
                    Sangam.restoreSession()
                    Sangam.previewFor(peerId)
                }
            }
        }.getOrNull()
        // Only post a notification when there is an active incoming call or a fresh unread message.
        val resolved = real ?: return
        val title = resolved.first
        val body = resolved.second

        // This chat has been muted in its ⋮ menu.
        if (Sangam.isChatMuted(peerId)) return
        // This person has been blocked - no notification for them either.
        if (Sangam.isBlocked(peerId)) return

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // A ringing voice or video call gets its own card that takes over the screen.
        val isCall = body.contains("Incoming voice call") || body.contains("Incoming video call")
        if (isCall) {
            manager.createNotificationChannel(Push.callChannel())
            val ring = Intent(this, com.sangam.messenger.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val ringPending = PendingIntent.getActivity(
                this, peerId.hashCode() + 7, ring,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val callCard = NotificationCompat.Builder(this, Push.CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_sangam)
                .setColor(0xFF3CB3D6.toInt())
                .setSubText("Sangam Call")
                .setContentTitle(title)
                .setContentText(body + " · Tap to answer")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(45_000L)
                .setFullScreenIntent(ringPending, true)
                .setContentIntent(ringPending)
                .build()
            manager.notify(peerId.hashCode() + 7, callCard)
            return
        }

        // If this chat is already open on screen, the message is visible - stay quiet.
        if (Push.activeChatPeerId == peerId) return

        manager.createNotificationChannel(Push.channel())

        val open = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("sangam://chat?id=" + peerId)
        )

        val pending = PendingIntent.getActivity(
            this,
            peerId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Privacy option: hide the text of the message if turned off.
        val shownBody = if (Sangam.notifShowText()) body else "New encrypted message"

        // Sangam rich conversation card showing who sent it and what they sent.
        val me = Person.Builder().setName("You").build()
        val sender = Person.Builder().setName(title).setImportant(true).build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Sangam · " + title)
            .addMessage(shownBody, System.currentTimeMillis(), sender)

        val notification = NotificationCompat.Builder(this, Push.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sangam)
            .setColor(0xFF3CB3D6.toInt())
            .setSubText("Sangam")
            .setStyle(style)
            .setContentTitle(title)
            .setContentText(shownBody)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(R.drawable.ic_stat_sangam, "Open chat", pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .setLights(0xFF3CB3D6.toInt(), 400, 400)
            .build()

        manager.notify(peerId.hashCode(), notification)
    }
}
