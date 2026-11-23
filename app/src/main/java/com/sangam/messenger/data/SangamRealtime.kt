package com.sangam.messenger.data

import com.sangam.messenger.BuildConfig
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight Supabase Realtime (Phoenix Channels) WebSocket client.
 * Wakes up ChatsViewModel and ChatViewModel immediately when a new message,
 * read receipt, or incoming call arrives — cutting idle polling and battery use
 * while keeping HTTP polling as an automatic safety net.
 */
object SangamRealtime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val refCounter = AtomicInteger(1)
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    @Volatile private var ws: WebSocket? = null
    @Volatile private var connectedUserId: String? = null
    @Volatile private var isConnected: Boolean = false
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    fun ensureConnected() {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        if (baseUrl.isBlank() || anonKey.isBlank()) return
        val session = Supabase.client.auth.currentSessionOrNull() ?: return
        val uid = session.user?.id ?: return
        val jwt = session.accessToken
        if (jwt.isBlank()) return

        if (isConnected && connectedUserId == uid && ws != null) return
        disconnect()
        connectedUserId = uid

        val wsUrl = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=" + anonKey + "&vsn=1.0.0"

        val req = Request.Builder().url(wsUrl).build()
        ws = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                sendJoin(webSocket, "realtime:public:messages", jwt, listOf(
                    Triple("*", "public", "messages")
                ))
                sendJoin(webSocket, "realtime:public:calls", jwt, listOf(
                    Triple("*", "public", "calls")
                ))
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains(""postgres_changes"") || text.contains(""INSERT"") || text.contains(""UPDATE"")) {
                    events.tryEmit("change")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                scheduleReconnect()
            }
        })
    }

    private fun sendJoin(
        webSocket: WebSocket,
        topic: String,
        jwt: String,
        changes: List<Triple<String, String, String>>
    ) {
        val pgArr = JSONArray()
        for ((ev, schema, table) in changes) {
            pgArr.put(JSONObject().apply {
                put("event", ev)
                put("schema", schema)
                put("table", table)
            })
        }
        val payload = JSONObject().apply {
            put("config", JSONObject().apply {
                put("postgres_changes", pgArr)
            })
            put("access_token", jwt)
        }
        val msg = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", payload)
            put("ref", refCounter.getAndIncrement().toString())
        }
        webSocket.send(msg.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(25_000)
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                if (!webSocket.send(hb.toString())) break
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(5_000)
            if (Supabase.client.auth.currentSessionOrNull() != null) {
                ensureConnected()
            }
        }
    }

    fun disconnect() {
        isConnected = false
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        runCatching { ws?.close(1000, "disconnect") }
        ws = null
        connectedUserId = null
    }

    /**
     * Suspends until either a Realtime event arrives (instant wake-up) or
     * [fallbackConnectedMs] / [fallbackDisconnectedMs] elapses.
     */
    suspend fun awaitWake(fallbackConnectedMs: Long = 12_000L, fallbackDisconnectedMs: Long = 4_000L) {
        val waitMs = if (isConnected) fallbackConnectedMs else fallbackDisconnectedMs
        withTimeoutOrNull(waitMs) {
            events.first()
        }
    }
}
