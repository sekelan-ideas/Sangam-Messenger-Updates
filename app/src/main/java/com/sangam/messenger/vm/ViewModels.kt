package com.sangam.messenger.vm

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangam.messenger.data.ChatSummary
import com.sangam.messenger.data.Models
import com.sangam.messenger.data.Models.Envelope
import com.sangam.messenger.data.Models.GroupRow
import com.sangam.messenger.data.Models.Profile
import com.sangam.messenger.data.Models.MessageRow
import com.sangam.messenger.data.Models.PendingMsg
import com.sangam.messenger.data.Sangam
import com.sangam.messenger.data.Updater
import com.sangam.messenger.data.UiMessage
import com.sangam.messenger.push.Push
import com.sangam.messenger.ui.previewOf
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

/** Decodes a photo at screen size instead of the full resolution - keeps
 *  scrolling smooth and memory low; a fine power-of-two pre-pass avoids
 *  ever holding a huge bitmap, then an exact scale lands on the cap. */
private fun decodeForDisplay(bytes: ByteArray, maxDim: Int = 1080): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null
    val biggest = maxOf(decoded.width, decoded.height)
    if (biggest > maxDim) {
        val scale = maxDim.toFloat() / biggest
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
    return decoded
}

fun formatTime(millis: Long): String {
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = millis }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(now, t) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        sameDay((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }, t) ->
            "Yesterday, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        now.get(Calendar.YEAR) == t.get(Calendar.YEAR) ->
            SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))
        else ->
            SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))
    }
}

/** Last-seen line: "today at 9:41 AM", "yesterday at 9:41 PM",
 *  and only genuinely old ones show the date with the time. */
fun formatSeen(millis: Long): String {
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = millis }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(now, t) ->
            "today at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        sameDay((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }, t) ->
            "yesterday at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        else -> formatTime(millis)
    }
}

/** Short quote shown when replying to a message. */
fun quotePreviewOf(m: UiMessage): String = when (m.type) {
    "i" -> "📷 Photo"
    "v" -> "🎬 Video"
    "a" -> "🎤 Voice message"
    "d" -> "📄 Document"
    "c" -> "🕰️ Time capsule"
    "?" -> "Message unavailable"
    else -> (m.text ?: "").replace('\n', ' ').take(90)
}

/** Turn library errors into plain, parent-friendly text. */
private fun friendlyAuthError(e: Throwable, fallback: String): String {
    // The library often puts the useful text in .error rather than .message.
    val raw = ((e as? RestException)?.error ?: "") + " " + (e.message ?: "")
    return when {
        e is Sangam.SignupNeedsConfirmation -> e.message ?: fallback
        raw.contains("validation_failed", true) || raw.contains("otp_expired", true) ->
            "That code was not right, or it has expired. Tap \"Send the email again\" " +
                "and use the newest code from the email."
        e is RestException && e.statusCode >= 500 ->
            "Sangam's chat service is having trouble right now. Please try again in a couple of minutes."
        e is RestException && e.error.isNotBlank() -> e.error
        else -> e.message ?: fallback
    }
}

// --------------------------------------------------------------------- auth

class AuthViewModel : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    /** True when a sign-up still needs the emailed code. */
    var needsConfirm by mutableStateOf(false)
        private set
    /** The address the code was sent to. */
    var confirmEmail by mutableStateOf("")
        private set

    /** "", "checking", "free" or "taken" while choosing a username. */
    var usernameCheck by mutableStateOf("")
        private set

    private var usernameJob: kotlinx.coroutines.Job? = null

    fun checkUsername(u: String) {
        val v = u.trim().lowercase()
        // Drop the previous lookup, so an earlier answer cannot land last and
        // claim a taken name is free.
        usernameJob?.cancel()
        if (v.length < 3) {
            usernameCheck = ""
            return
        }
        usernameCheck = "checking"
        usernameJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(400)
            val free = Sangam.usernameAvailable(v)
            if (isActive) usernameCheck = if (free) "free" else "taken"
        }
    }

    /** Confirm with the six-digit code from the email. */
    fun verifyCode(code: String, recovery: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true; error = null
            val r = Sangam.verifyEmailCode(confirmEmail, code, recovery)
            busy = false
            r.fold(
                onSuccess = {
                    needsConfirm = false
                    if (recovery) notice = "Code accepted - choose a new password below."
                    onDone()
                },
                onFailure = { error = friendlyAuthError(it, "That code was not accepted") }
            )
        }
    }

    fun cancelConfirm() {
        needsConfirm = false
    }

    /** Wipe the leftover error / notice when the user moves to another screen. */
    fun clearMessages() {
        error = null
        notice = null
    }

    fun resendCode(recovery: Boolean) {
        viewModelScope.launch {
            busy = true; error = null
            val r = Sangam.resendEmail(confirmEmail, recovery)
            busy = false
            r.fold(
                onSuccess = { notice = "Sent again to " + confirmEmail },
                onFailure = { error = friendlyAuthError(it, "Could not resend") }
            )
        }
    }

    fun setNewPassword(pw: String, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true; error = null
            val r = Sangam.updatePassword(pw)
            busy = false
            r.fold(
                onSuccess = { notice = "Password updated - you can log in with it now."; onDone() },
                onFailure = { error = friendlyAuthError(it, "Could not update the password") }
            )
        }
    }

    /** Send a password-reset email, resolving a username to its address. */
    fun resetPassword(identifier: String) {
        viewModelScope.launch {
            busy = true; error = null; notice = null
            val email = if (identifier.contains('@')) {
                identifier.trim()
            } else {
                Sangam.lookupEmail(identifier.trim().lowercase())
            }
            if (email == null) {
                busy = false
                error = "No account found for that username. Try your email address."
                return@launch
            }
            confirmEmail = email
            val result = Sangam.resetPassword(email)
            busy = false
            result.fold(
                onSuccess = {
                    notice = "Sent to " + email +
                        ". Enter the 6-digit code from that email, or tap the link."
                },
                onFailure = { error = friendlyAuthError(it, "Could not send the reset email") }
            )
        }
    }

    fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            busy = true; error = null
            val result = Sangam.signUp(email.trim(), password, username.trim(), displayName.trim())
            busy = false
            result.fold(
                onSuccess = { onDone() },
                onFailure = {
                    if (it is Sangam.SignupNeedsConfirmation) {
                        confirmEmail = email.trim()
                        needsConfirm = true
                        notice = "We emailed " + email.trim() +
                            ". Enter the 6-digit code from that email below."
                    } else {
                        error = friendlyAuthError(it, "Could not sign up")
                    }
                }
            )
        }
    }

    fun signIn(identifier: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true; error = null
            // Accepts either an email address or a username.
            val email = if (identifier.contains('@')) {
                identifier.trim()
            } else {
                val found = Sangam.lookupEmail(identifier.trim().lowercase())
                if (found == null) {
                    busy = false
                    error = "No account found for that username. Log in with your email address."
                    return@launch
                }
                found
            }
            val result = Sangam.signIn(email, password)
            busy = false
            result.fold(
                onSuccess = { onDone() },
                onFailure = { error = friendlyAuthError(it, "Could not log in") }
            )
        }
    }
}

// ------------------------------------------------------------------ chats list

class ChatsViewModel : ViewModel() {

    /** Decrypted group pictures, keyed by group id, for the chat list. */
    var groupAvatars by mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap())
        private set

    /** Pull in any group picture we have not decrypted yet. */
    private fun loadGroupAvatars(groups: Map<String, GroupRow>) {
        val want = groups.filter { it.value.photoPath != null && it.key !in groupAvatars }
        if (want.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for ((gid, g) in want) {
                val bmp = runCatching {
                    Sangam.groupPhoto(g)?.let { decodeForDisplay(it) }
                }.getOrNull() ?: continue
                groupAvatars = groupAvatars + (gid to bmp)
            }
        }
    }
    var chats by mutableStateOf<List<ChatSummary>>(emptyList())
        private set
    /** Phones and tablets signed in to this account. */
    var devices by mutableStateOf<List<Models.DeviceRow>>(emptyList())
        private set

    fun loadDevices() {
        viewModelScope.launch(Dispatchers.IO) { devices = Sangam.myDevices() }
    }

    fun removeDevice(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { Sangam.removeDevice(id) }
            devices = Sangam.myDevices()
        }
    }

    /** Recent decrypted messages per chat, for the global search. */
    var messagesByPeer by mutableStateOf<Map<String, List<UiMessage>>>(emptyMap())
        private set
    /** Set when blocked users were changed, so the list re-filters. */
    var blockedVersion by mutableStateOf(0)
        private set

    fun refreshBlocked() { blockedVersion += 1 }
    var loading by mutableStateOf(false)
        private set
    var update by mutableStateOf<Updater.UpdateInfo?>(null)
        private set
    var updateFile by mutableStateOf<File?>(null)
        private set
    var updateStatus by mutableStateOf<String?>(null)
        private set
    var dismissedUpdate by mutableStateOf<String?>(null)
        private set
    var initialLoaded by mutableStateOf(false)
        private set
    var myProfile by mutableStateOf<Profile?>(null)
        private set
    private var me: String? = null
    private var appContext: Context? = null

    /** Loads my own profile (photo + name) for the menu header. */
    fun loadMyProfile() {
        viewModelScope.launch {
            runCatching { myProfile = Sangam.myProfile() }
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (me == null) me = Sangam.userId()
        loadChatsCache()
        refresh()
        checkForUpdate()
        viewModelScope.launch {
            while (isActive) {
                delay(8_000)
                refresh()
            }
        }
    }

    /** Instant-open cache: last known chats list saved on this phone,
     *  shown immediately while the network refresh runs. */
    private fun loadChatsCache() {
        val ctx = appContext ?: return
        runCatching {
            val f = java.io.File(ctx.filesDir, "chats_cache.json")
            if (!f.exists() || chats.isNotEmpty()) return@runCatching
            // Live dots from a previous session are meaningless now.
            chats = Json.decodeFromString(
                ListSerializer(ChatSummary.serializer()), f.readText()
            ).map { it.copy(online = false, typing = false) }
                .filter { !Sangam.isBlocked(it.peerId) }
        }
    }

    private fun saveChatsCache(list: List<ChatSummary>) {
        val ctx = appContext ?: return
        runCatching {
            java.io.File(ctx.filesDir, "chats_cache.json").writeText(
                Json.encodeToString(ListSerializer(ChatSummary.serializer()), list)
            )
        }
    }

    fun checkForUpdate() {
        val ctx = appContext ?: return
        viewModelScope.launch {
            dismissedUpdate = ctx.getSharedPreferences("sangam_update", Context.MODE_PRIVATE)
                .getString("dismissed_version", null)
            update = Updater.check()
            updateFile = null
            updateStatus = null
        }
    }

    fun dismissUpdate() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences("sangam_update", Context.MODE_PRIVATE)
            .edit().putString("dismissed_version", update?.version).apply()
        dismissedUpdate = update?.version
    }

    fun downloadUpdate() {
        val ctx = appContext ?: return
        val info = update ?: return
        viewModelScope.launch {
            updateStatus = "downloading"
            runCatching { Updater.download(info.apkUrl, ctx) }.fold(
                onSuccess = { file ->
                    updateFile = file
                    updateStatus = "ready"
                },
                onFailure = {
                    updateFile = null
                    updateStatus = "error"
                }
            )
        }
    }

    fun installUpdate() {
        val ctx = appContext ?: return
        val file = updateFile ?: return
        runCatching { Updater.install(ctx, file) }
    }

    var deleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    /** Permanently delete the account, then hand control back. */
    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            deleting = true
            deleteError = null
            Sangam.deleteAccount().fold(
                onSuccess = {
                    deleting = false
                    onDone()
                },
                onFailure = { e ->
                    deleting = false
                    deleteError = e.message ?: "Could not delete the account"
                }
            )
        }
    }

    fun refresh() {
        // The handler is a safety net: a network failure mid-poll can never
        // crash the app, it just means this round found nothing.
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }) {
            loading = true
            runCatching {
                // Keep my "online" status fresh while the app is open.
                Sangam.touchLastSeen()
                val profilesJob = async { runCatching { Sangam.allProfiles() } }
                val rows = Sangam.fetchRecentInvolvingMe()
                me = me ?: Sangam.userId()
                val myId = me
                if (myId == null) {
                    // No session right now (offline start) - still show the
                    // cached chats instead of an endless loading circle.
                    loading = false
                    initialLoaded = true
                    return@launch
                }
                if (rows.any { it.recipient_id == myId && it.delivered_at == null }) {
                    Sangam.markDelivered()
                }
                val byPeer = LinkedHashMap<String, MutableList<UiMessage>>()
                val byGroup = LinkedHashMap<String, MutableList<UiMessage>>()
                for (row in rows) {
                    val ui = Sangam.toUi(row)
                    if (ui.type == "r" || ui.type == "e") continue  // folded into their targets
                    if (row.group_id != null) byGroup.getOrPut(row.group_id!!) { mutableListOf() }.add(ui)
                    else byPeer.getOrPut(ui.peerId) { mutableListOf() }.add(ui)
                }
                val profiles = profilesJob.await().getOrDefault(emptyMap())
                val groups = runCatching { Sangam.myGroups() }.getOrDefault(emptyList())
                    .associateBy { it.id }
                loadGroupAvatars(groups)
                val summaries = byPeer.map { (peerId, msgs) ->
                    // The rows arrive newest-first; pick the newest message
                    // by time so the preview and the list order stay correct
                    // when a new message is sent or received.
                    val last = msgs.maxByOrNull { it.createdAt }
                        ?: msgs.first()
                    val profile = profiles[peerId]
                    ChatSummary(
                        peerId = peerId,
                        peerName = profile?.display_name ?: "Sangam friend",
                        peerUsername = profile?.username ?: "",
                        lastPreview = previewOf(last.type, last.text),
                        lastAt = last.createdAt,
                        mine = last.mine,
                        unread = msgs.count { !it.mine && !it.read },
                        avatarUrl = profile?.avatar_url,
                        online = profile != null &&
                            Sangam.parseTime(profile.lastSeenAt ?: "") >
                            System.currentTimeMillis() - 45_000,
                        typing = profile != null && profile.typingFor == myId &&
                            Sangam.parseTime(profile.typingUntil ?: "") >
                            System.currentTimeMillis()
                    )
                }.sortedByDescending { it.lastAt }
                val groupSummaries = byGroup.map { (gid, msgs) ->
                    val last = msgs.maxByOrNull { it.createdAt } ?: msgs.first()
                    val who = if (!last.mine) profiles[last.senderId]?.display_name else null
                    ChatSummary(
                        peerId = gid,
                        peerName = groups[gid]?.name ?: "Group",
                        peerUsername = "",
                        lastPreview = if (who != null)
                            who + ": " + previewOf(last.type, last.text)
                        else previewOf(last.type, last.text),
                        lastAt = last.createdAt,
                        mine = last.mine,
                        unread = msgs.count { !it.mine && !it.read },
                        avatarUrl = null,
                        isGroup = true
                    )
                }
                // Groups with no messages yet still need a row, otherwise a
                // freshly created group is invisible until someone speaks.
                val quietGroups = groups.values
                    .filter { it.id !in byGroup }
                    .map { g ->
                        ChatSummary(
                            peerId = g.id,
                            peerName = g.name,
                            peerUsername = "",
                            lastPreview = "No messages yet",
                            lastAt = Sangam.parseTime(g.createdAt ?: ""),
                            mine = false,
                            unread = 0,
                            avatarUrl = null,
                            isGroup = true
                        )
                    }
                val blocked = Sangam.blockedIds()
                val all = summaries + groupSummaries + quietGroups
                val visible = all.filterNot { !it.isGroup && it.peerId in blocked }
                    .sortedByDescending { it.lastAt }
                chats = visible
                saveChatsCache(visible)
                messagesByPeer = byPeer
                    .filterKeys { it !in blocked }
                    .mapValues { it.value.toList() } +
                    byGroup.mapValues { it.value.toList() }
            }
            loading = false
            initialLoaded = true
        }
    }
}

// ------------------------------------------------------------------- one chat

class ChatViewModel : ViewModel() {
    var peerId: String? = null
    /** Set when this screen shows a group instead of a one-to-one chat. */
    var groupId: String? = null
    var group by mutableStateOf<GroupRow?>(null)
    var groupMembers by mutableStateOf<Map<String, Profile>>(emptyMap())
    /** Members who are group admins. */
    var groupAdminIds by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Everyone in the group - including me - with their role. */
    var groupRoster by mutableStateOf<List<Models.MemberUi>>(emptyList())
        private set
    /** View-once photos we sent that the friend has opened. */
    var openedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Expired messages we have already cleared from the server. */
    private val purgedIds = mutableSetOf<String>()
    private var memberIds: List<String> = emptyList()
    private var groupTicksCache: Map<String, Pair<Int, Int>> = emptyMap()
    /** Decrypted group picture, when the group has one. */
    var groupPhotoBitmap by mutableStateOf<Bitmap?>(null)
    /** Members currently typing in this group. */
    var groupTypingNames by mutableStateOf<List<String>>(emptyList())
        private set
    private var lastNearbyInbox = -1
    val isGroup: Boolean get() = groupId != null
    private val chatKey: String get() = groupId ?: peerId ?: ""
    var messages by mutableStateOf<List<UiMessage>>(emptyList())
        private set
    var peer by mutableStateOf<Profile?>(null)
    var images by mutableStateOf<Map<String, Bitmap>>(emptyMap())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val failedImages = mutableSetOf<String>()
    var reactions by mutableStateOf<Map<String, Pair<String?, String?>>>(emptyMap())
        private set
    var savedMessage by mutableStateOf<String?>(null)
    var peerTyping by mutableStateOf(false)
        private set
    var peerOnline by mutableStateOf(false)
        private set
    var peerLastSeen by mutableStateOf(0L)
        private set
    /** Message being replied to, shown above the input box. */
    var replyTarget by mutableStateOf<UiMessage?>(null)
        private set
    /** Ids of messages starred on this phone. */
    var starredIds by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Per-chat wallpaper / bubble colour id. */
    var chatThemeId by mutableStateOf("default")
        private set
    private var lastTypingPing = 0L
    var recording by mutableStateOf(false)
        private set
    var recordSeconds by mutableStateOf(0)
        private set
    var playingId by mutableStateOf<String?>(null)
        private set
    var playProgress by mutableStateOf(0f)
        private set
    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordStart = 0L
    private var player: MediaPlayer? = null
    private var playJob: Job? = null
    /** The conversation's rows kept in memory for the fast poll. */
    private var rowCache: List<MessageRow> = emptyList()
    /** Newest synced message time; the fast poll fetches only newer rows. */
    private var lastSyncAt: String? = null
    private var pollCount = 0

    /** Errors clear themselves from the screen after a moment. */
    fun clearError() {
        error = null
    }

    fun start(peerId: String) {
        if (this.peerId == peerId) return
        this.peerId = peerId
        groupId = null
        group = null
        memberIds = emptyList()
        groupTicksCache = emptyMap()
        chatThemeId = Sangam.chatTheme(peerId)
        starredIds = Sangam.starredIds()
        Push.activeChatPeerId = peerId
        loadPending()
        // Open instantly with the last cached messages, then sync.
        rowCache = Sangam.readChatCache(peerId)
        if (rowCache.isNotEmpty()) {
            lastSyncAt =
                rowCache.maxByOrNull { Sangam.parseTime(it.created_at) }?.created_at
            applyRows()
        }
        refresh(full = true)
        viewModelScope.launch {
            runCatching { peer = Sangam.profileOf(peerId) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                refresh()
            }
        }
    }

    /** Open a group chat: load the group, its members and cached messages. */
    fun startGroup(groupId: String) {
        if (this.groupId == groupId) return
        this.groupId = groupId
        peerId = null
        chatThemeId = Sangam.chatTheme(groupId)
        starredIds = Sangam.starredIds()
        Push.activeChatPeerId = null
        loadPending()
        groupPhotoBitmap = null
        groupTypingNames = emptyList()
        rowCache = Sangam.readChatCache("g_" + groupId)
        if (rowCache.isNotEmpty()) {
            lastSyncAt =
                rowCache.maxByOrNull { Sangam.parseTime(it.created_at) }?.created_at
            applyRows()
        }
        refresh(full = true)
        viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                refresh()
            }
        }
    }

    // ---- group-aware send helpers: same call sites serve both chat types ----
    private suspend fun envSend(env: Envelope): MessageRow {
        val g = groupId
        return if (g != null) Sangam.sendToGroup(g, memberIds, env)
        else Sangam.send(peerId!!, env)
    }

    private suspend fun imgSend(
        bytes: ByteArray, replyTo: String?, quote: String?
    ): Result<MessageRow> {
        val g = groupId
        return if (g != null) Sangam.sendGroupImage(g, memberIds, bytes, replyTo, quote)
        else Sangam.sendImage(peerId!!, bytes, replyTo, quote)
    }

    private suspend fun audioSend(
        bytes: ByteArray, seconds: Int, replyTo: String?, quote: String?
    ): Result<MessageRow> {
        val g = groupId
        return if (g != null) Sangam.sendGroupAudio(g, memberIds, bytes, seconds, replyTo, quote)
        else Sangam.sendAudio(peerId!!, bytes, seconds, replyTo, quote)
    }

    private suspend fun docSend(
        bytes: ByteArray, name: String, replyTo: String?, quote: String?
    ): Result<MessageRow> {
        val g = groupId
        return if (g != null) Sangam.sendGroupDocument(g, memberIds, bytes, name, replyTo, quote)
        else Sangam.sendDocument(peerId!!, bytes, name, replyTo, quote)
    }

    private suspend fun videoSend(bytes: ByteArray, thumb: ByteArray?): Result<MessageRow> {
        val g = groupId
        return if (g != null) Sangam.sendGroupVideo(g, memberIds, bytes, thumb)
        else Sangam.sendVideo(peerId!!, bytes, thumb)
    }

    // ---- group management (used by the group info dialog) ----
    /** Make a member an admin, or remove their admin rights. */
    fun setAdmin(userId: String, isAdmin: Boolean) {
        val gid = groupId ?: return
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }) {
            runCatching { Sangam.setGroupAdmin(gid, userId, isAdmin) }
            refreshGroup(full = true)
        }
    }

    /** Co-leader, with a cap of two - as the leader decides. */
    fun setCoLeader(userId: String, on: Boolean) {
        val gid = groupId ?: return
        val leaderId = group?.createdBy
        val current = groupAdminIds.count { it != leaderId }
        if (on && userId !in groupAdminIds && current >= 2) {
            error = "Only two co-leaders allowed - remove one first."
            return
        }
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }) {
            runCatching { Sangam.setGroupAdmin(gid, userId, on) }
            refreshGroup(full = true)
        }
    }

    /** Everyone can post, or only the leader and co-leaders. */
    fun setLeadersOnly(only: Boolean) {
        val gid = groupId ?: return
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }) {
            runCatching { Sangam.setLeadersOnly(gid, only) }
            refreshGroup(full = true)
        }
    }

    /** Allow, or stop, ordinary members from adding people. */
    fun setMembersCanAdd(can: Boolean) {
        val gid = groupId ?: return
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }) {
            runCatching { Sangam.setMembersCanAdd(gid, can) }
            refreshGroup(full = true)
        }
    }

    fun renameGroup(name: String) {
        val g = groupId ?: return
        viewModelScope.launch { runCatching { Sangam.renameGroup(g, name); refresh(full = true) } }
    }
    fun setGroupDescription(desc: String) {
        val g = groupId ?: return
        viewModelScope.launch {
            runCatching { Sangam.setGroupDescription(g, desc); refresh(full = true) }
        }
    }
    fun removeGroupMember(uid: String) {
        val g = groupId ?: return
        viewModelScope.launch {
            runCatching { Sangam.removeGroupMember(g, uid); refresh(full = true) }
        }
    }
    fun addGroupMembers(ids: List<String>) {
        val g = groupId ?: return
        viewModelScope.launch {
            runCatching { Sangam.addGroupMembers(g, ids); refresh(full = true) }
        }
    }
    /**
     * Leave the group and only then navigate. The old version launched the
     * request and returned immediately, so navigating away destroyed this
     * ViewModel and cancelled the DELETE before it reached the server.
     */
    fun leaveGroup(onDone: () -> Unit = {}) {
        val g = groupId ?: return
        viewModelScope.launch {
            runCatching { Sangam.leaveGroup(g) }
            onDone()
        }
    }
    fun setGroupPhoto(context: Context, uri: Uri) {
        val g = groupId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = readAndScale(context, uri)
                Sangam.setGroupPhoto(g, bytes)
                groupPhotoBitmap = decodeForDisplay(bytes)
            }
            refresh(full = true)
        }
    }
    fun clearGroupChat() {
        val g = groupId ?: return
        viewModelScope.launch {
            runCatching { Sangam.clearGroupChat(g); refresh(full = true) }
        }
    }
    fun deleteGroup(onDone: () -> Unit) {
        val g = groupId ?: return
        viewModelScope.launch {
            runCatching { Sangam.deleteGroup(g) }
            onDone()
        }
    }

    override fun onCleared() {
        if (Push.activeChatPeerId == peerId) Push.activeChatPeerId = null
        stopPlayback()
        if (recording) {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            recording = false
            recordFile?.delete()
            recordFile = null
        }
    }

    /** Group poll: messages, members, profiles and per-message ticks. */
    private suspend fun refreshGroup(full: Boolean): Unit = kotlinx.coroutines.coroutineScope {
        val gid = groupId ?: return@coroutineScope
        val memJob = async { runCatching { Sangam.groupMembers(gid) } }
        val infoJob = async { runCatching { Sangam.groupInfo(gid) } }
        pollCount += 1
        val since = lastSyncAt
        val doFull = full || since == null || pollCount % 10 == 0
        var fresh: List<MessageRow> = emptyList()
        if (doFull) {
            rowCache = Sangam.fetchGroup(gid)
        } else {
            fresh = Sangam.fetchGroupSince(gid, since!!)
            if (fresh.isNotEmpty()) {
                val byId = rowCache.associateBy { it.id }.toMutableMap()
                fresh.forEach { byId[it.id] = it }
                rowCache = byId.values.sortedBy { Sangam.parseTime(it.created_at) }
            }
        }
        val me = Sangam.userId()
        val mem = memJob.await().getOrDefault(emptyList())
        memberIds = mem.map { it.userId }.filter { it != me }
        groupAdminIds = mem.filter { it.isAdmin }.map { it.userId }.toSet()
        val profiles = runCatching { Sangam.allProfiles() }.getOrDefault(emptyMap())
        groupMembers = memberIds.mapNotNull { uid -> profiles[uid]?.let { uid to it } }.toMap()
        val nowMs = System.currentTimeMillis()
        groupTypingNames = groupMembers.values
            .filter { it.typingFor == gid && Sangam.parseTime(it.typingUntil ?: "") > nowMs }
            .map { it.display_name }
        val info = infoJob.await().getOrNull()
        group = info
        // The full roster, me included, each with their role.
        val leaderId = info?.createdBy
        groupRoster = mem.map { row ->
            val uid = row.userId
            Models.MemberUi(
                id = uid,
                name = if (uid == me) "You" else profiles[uid]?.display_name ?: "Member",
                leader = uid == leaderId,
                coLeader = row.isAdmin && uid != leaderId
            )
        }.sortedWith(
            compareByDescending<Models.MemberUi> { it.leader }
                .thenByDescending { it.coLeader }
                .thenBy { it.name.lowercase() }
        )
        if (doFull) {
            if (info?.photoPath != null && groupPhotoBitmap == null) {
                runCatching { Sangam.groupPhoto(info) }.getOrNull()
                    ?.let { groupPhotoBitmap = decodeForDisplay(it) }
            }
            groupTicksCache = runCatching { Sangam.groupTicks(gid) }.getOrDefault(emptyMap())
        }
        if (doFull || fresh.isNotEmpty()) {
            if (rowCache.isNotEmpty()) {
                lastSyncAt =
                    rowCache.maxByOrNull { Sangam.parseTime(it.created_at) }?.created_at
                Sangam.writeChatCache("g_" + gid, rowCache)
            }
            applyRows()
        }
        val unreadPool = if (doFull) rowCache else fresh
        if (unreadPool.any { it.sender_id != me && it.read_at == null }) {
            if (Sangam.readReceiptsOn()) Sangam.markReadGroup(gid) else Sangam.markDelivered()
        }
        runCatching { Sangam.touchLastSeen() }
    }

    fun refresh(full: Boolean = false) {
        val id = chatKey
        if (id.isBlank()) return
        // Bluetooth messages arrive outside the server poll - notice them.
        if (com.sangam.messenger.nearby.Nearby.inbox != lastNearbyInbox) {
            lastNearbyInbox = com.sangam.messenger.nearby.Nearby.inbox
            applyRows()
        }
        // Retry anything waiting in the offline outbox while online.
        flushPending()
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }) {
            runCatching {
                if (isGroup) {
                    refreshGroup(full)
                    return@runCatching
                }
                // Peer profile (typing/online) is fetched in parallel with
                // the messages so the chat loads faster.
                val profileJob = async { runCatching { Sangam.profileOf(id) } }
                pollCount += 1
                val since = lastSyncAt
                // The fast poll fetches only the new rows; a full refresh
                // runs on open and about every 20 seconds (for ticks,
                // deletions and read states).
                val doFull = full || since == null || pollCount % 10 == 0
                var fresh: List<MessageRow> = emptyList()
                if (doFull) {
                    rowCache = Sangam.fetchConversation(id)
                } else {
                    fresh = Sangam.fetchConversationSince(id, since!!)
                    if (fresh.isNotEmpty()) {
                        val byId = rowCache.associateBy { it.id }.toMutableMap()
                        fresh.forEach { byId[it.id] = it }
                        rowCache = byId.values.sortedBy { Sangam.parseTime(it.created_at) }
                    }
                }
                // Only rebuild and re-save the screen when something actually
                // changed - a full refresh (ticks, read states) or new rows.
                // The idle poll then costs nothing.
                if (doFull || fresh.isNotEmpty()) {
                    if (rowCache.isNotEmpty()) {
                        lastSyncAt =
                            rowCache.maxByOrNull { Sangam.parseTime(it.created_at) }?.created_at
                        Sangam.writeChatCache(id, rowCache)
                    }
                    applyRows()
                }
                // Mark unread friend messages as read/delivered.
                val unreadPool = if (doFull) rowCache else fresh
                if (unreadPool.any { it.sender_id == id && it.read_at == null }) {
                    // Privacy option: without read receipts we still mark delivered.
                    if (Sangam.readReceiptsOn()) Sangam.markRead(id) else Sangam.markDelivered()
                }
                // Live status line: typing / online, and keep my own
                // last-seen fresh while this chat is open.
                runCatching {
                    val me = Sangam.userId()
                    val p = profileJob.await().getOrNull()
                    peer = p
                    peerTyping = p != null && me != null &&
                        p.typingFor == me &&
                        Sangam.parseTime(p.typingUntil ?: "") > System.currentTimeMillis()
                    peerLastSeen = p?.lastSeenAt?.let { Sangam.parseTime(it) } ?: 0L
                    peerOnline = peerLastSeen > System.currentTimeMillis() - 45_000
                    Sangam.touchLastSeen()
                }
            }
        }
    }

    /** Turns the cached rows into what the chat screen shows. */
    private fun applyRows() {
        // Bluetooth (Nearby) messages live on this phone; merge them in.
        val nearbyRows = if (groupId == null) Sangam.readNearbyMessages(chatKey) else emptyList()
        val nearbyIds = nearbyRows.map { it.id }.toSet()
        val all = (rowCache + nearbyRows)
            .distinctBy { it.id }
            .sortedBy { Sangam.parseTime(it.created_at) }
            .map { Sangam.toUi(it) }
        // Disappearing messages: hide anything past its timer.
        val win = Sangam.chatDisappear(chatKey)
        val since = Sangam.disappearSince(chatKey)
        val nowMs = System.currentTimeMillis()
        // Clear our own expired messages from the server as well, so the
        // ciphertext does not linger after the timer runs out.
        if (win > 0 && since > 0) {
            val gone = all.filter {
                it.mine && it.createdAt > since && nowMs > it.createdAt + win * 1000L &&
                    purgedIds.add(it.id)
            }.map { it.id }
            if (gone.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }) {
                    gone.forEach { runCatching { Sangam.deleteMessageEverywhere(it) } }
                }
            }
        }
        // Edit instructions replace the text of the message they target.
        // Only the author of a message may rewrite it - otherwise anyone in a
        // chat could change how somebody else's words appear. When several
        // edits exist, the newest wins.
        val byId = all.associateBy { it.id }
        val edits = HashMap<String, UiMessage>()
        for (m in all) {
            if (m.type != "e" || m.editOf == null) continue
            val target = byId[m.editOf!!] ?: continue
            if (target.senderId != m.senderId) continue
            val prev = edits[m.editOf!!]
            if (prev == null || m.createdAt >= prev.createdAt) edits[m.editOf!!] = m
        }
        val hidden = Sangam.hiddenIds()
        messages = all
            .filter { it.type != "r" && it.type != "e" && it.type != "o" && it.id !in hidden }
            .filter { m ->
                !(win > 0 && since > 0 && m.createdAt > since && nowMs > m.createdAt + win * 1000L)
            }
            // A view-once photo or video is gone once it has been opened.
            // A photo you have just opened stays on screen until you leave
            // the chat. Marking it used immediately made it vanish mid-look.
            .filter { m ->
                !(m.viewOnce && Sangam.viewOnceUsed(m.id) && m.id !in openedThisSession)
            }
            .also { rows ->
                openedIds = rows.filter { it.viewOnce && it.mine && Sangam.wasOpened(it.id) }
                    .map { it.id }.toSet()
            }
            .map { m ->
                val e = edits[m.id]
                if (e != null && m.type == "t" && !e.text.isNullOrEmpty())
                    m.copy(text = e.text, edited = true)
                else m
            }
            .map { m ->
                if (m.groupId == null) m
                else {
                    val t = m.clientMsgId?.let { groupTicksCache[it] }
                    m.copy(
                        senderName = m.senderId?.let { groupMembers[it]?.display_name },
                        groupTotal = memberIds.size,
                        groupDelivered = t?.first ?: 0,
                        groupRead = t?.second ?: 0
                    )
                }
            }
            .map { m -> if (m.id in nearbyIds) m.copy(viaNearby = true) else m }
        val map = HashMap<String, Pair<String?, String?>>()
        for (m in all) {
            if (m.type != "r" || m.reactionTo == null) continue
            val cur = map[m.reactionTo] ?: (null to null)
            val emoji = m.text ?: ""
            map[m.reactionTo] = if (m.mine) emoji to cur.second else cur.first to emoji
        }
        reactions = map
        loadImages()
    }

    /**
     * May the current user post here? In a leaders-only group only the leader
     * and co-leaders may - and that has to hold for every kind of message,
     * not just text.
     */
    private fun canPostHere(): Boolean {
        val g = group ?: return true
        val me = Sangam.userId()
        return !(g.leadersOnly && me != g.createdBy && me !in groupAdminIds)
    }

    private fun blockIfCannotPost(): Boolean {
        if (canPostHere()) return false
        error = "Only the leader and co-leaders can send here."
        return true
    }

    /** View-once ids opened in this session - kept visible while you look. */
    private val openedThisSession = mutableSetOf<String>()

    fun keepViewOnceVisible(id: String) {
        openedThisSession.add(id)
    }

    fun sendText(text: String) {
        if (blockIfCannotPost()) return
        val key = chatKey
        if (key.isBlank()) return
        if (text.isBlank()) return
        val reply = replyTarget
        replyTarget = null
        // Nearby (Bluetooth): if this friend is close, send straight over
        // Bluetooth - no server, still Signal Protocol encrypted.
        if (groupId == null && reply == null &&
            com.sangam.messenger.nearby.Nearby.isConnected(key)
        ) {
            if (com.sangam.messenger.nearby.Nearby.sendText(key, text.trim())) {
                applyRows()
                return
            }
        }
        // Offline outbox: show it instantly with a clock icon, then try to
        // send. If the network is down it waits and is retried every poll.
        val p = Sangam.addPending(
            key, text.trim(),
            replyTo = reply?.id, quote = reply?.let { quotePreviewOf(it) },
            members = if (isGroup) memberIds else null
        )
        pendingMsgs = pendingMsgs + pendingUi(p, key)
        flushPending()
    }

    /** Messages waiting in this chat's offline outbox, shown with a clock. */
    var pendingMsgs by mutableStateOf<List<UiMessage>>(emptyList())
        private set
    private var flushing = false

    private fun pendingUi(p: PendingMsg, id: String) = UiMessage(
        id = "p_" + p.id, peerId = id, mine = true, type = "t", text = p.text,
        imagePath = null, imageKey = null, imageIv = null,
        createdAt = p.at, delivered = false, read = false, pending = true,
        replyTo = p.replyTo, quote = p.quote
    )

    private fun loadPending() {
        val key = chatKey
        if (key.isBlank()) return
        pendingMsgs = Sangam.pendingFor(key).map { pendingUi(it, key) }
    }

    /** Try to send everything waiting in this chat's offline outbox. */
    fun flushPending() {
        val key = chatKey
        if (key.isBlank()) return
        if (flushing) return
        val list = Sangam.pendingFor(key)
        if (list.isEmpty()) return
        flushing = true
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }) {
            try {
                var sentAny = false
                for (p in list) {
                    try {
                        val env = Envelope(t = "t", b = p.text, q = p.replyTo, p = p.quote)
                        val g = groupId
                        if (g != null) Sangam.sendToGroup(g, p.members ?: memberIds, env)
                        else Sangam.send(key, env)
                        Sangam.removePending(key, p.id)
                        sentAny = true
                    } catch (_: Exception) {
                        break  // offline - stop and retry on the next poll
                    }
                }
                if (sentAny) refresh()
            } finally {
                flushing = false
                loadPending()
            }
        }
    }

    fun startReply(m: UiMessage) { replyTarget = m }

    fun cancelReply() { replyTarget = null }

    fun setChatTheme(theme: String) {
        chatThemeId = theme
        // chatKey, not peerId: in a group peerId is null and the theme was
        // never saved at all.
        val key = chatKey
        if (key.isBlank()) return
        runCatching { Sangam.setChatTheme(key, theme) }
    }

    fun toggleStar(id: String) {
        val nowStarred = Sangam.toggleStar(id)
        starredIds = if (nowStarred) starredIds + id else starredIds - id
    }

    fun isStarred(id: String): Boolean = id in starredIds

    /** Edit one of my sent text messages (shows as "edited" on both phones). */
    fun editMessage(id: String, newText: String) {
        // In a group peerId is null, so editing used to fail silently there.
        if (newText.isBlank()) return
        val g = groupId
        val pid = peerId
        if (g == null && pid == null) return
        viewModelScope.launch {
            busy = true; error = null
            runCatching {
                if (g != null) Sangam.editGroupMessage(g, memberIds, id, newText)
                else Sangam.editMessage(pid!!, id, newText)
            }
                .fold(
                    onSuccess = { refresh() },
                    onFailure = { error = "Could not edit. Check your connection." }
                )
            busy = false
        }
    }

    /** Hide a message on this phone only. */
    fun hideMessage(id: String) {
        Sangam.hideMessage(id)
        messages = messages.filter { it.id != id }
    }

    /** Forward a text message to another friend. */
    fun forwardText(toPeerId: String, text: String) {
        viewModelScope.launch {
            busy = true; error = null
            runCatching {
                Sangam.send(toPeerId, com.sangam.messenger.data.Models.Envelope(t = "t", b = text))
            }.fold(
                onSuccess = { savedMessage = "Forwarded ✓" },
                onFailure = { error = "Could not send. Check your connection." }
            )
            busy = false
        }
    }

    /** Forward a photo to another friend. */
    fun forwardImage(toPeerId: String, bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            busy = true; error = null
            val bytes = runCatching {
                val out = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
            if (bytes.isFailure) {
                error = "Could not forward that photo."
                busy = false
                return@launch
            }
            runCatching { Sangam.sendImage(toPeerId, bytes.getOrThrow()).getOrThrow() }.fold(
                onSuccess = { savedMessage = "Forwarded ✓" },
                onFailure = { error = "Could not send. Check your connection." }
            )
            busy = false
        }
    }

    /** Send the edited photo from the edit screen. */
    fun sendEditedBitmap(bitmap: android.graphics.Bitmap) {
        if (blockIfCannotPost()) return
        if (chatKey.isBlank()) return
        val reply = replyTarget
        replyTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            busy = true; error = null
            val bytes = runCatching {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
            if (bytes.isFailure) {
                error = "Could not send that photo."
                busy = false
                return@launch
            }
            runCatching {
                imgSend(
                    bytes.getOrThrow(),
                    reply?.id, reply?.let { quotePreviewOf(it) }
                ).getOrThrow()
            }.fold(
                onSuccess = { refresh() },
                onFailure = { e -> error = "Photo send failed — " + shortError(e) }
            )
            busy = false
        }
    }

    /** Decode a picked photo for the edit screen. */
    fun decodeForEdit(context: Context, uri: Uri, onReady: (android.graphics.Bitmap?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cache = copyPickedPhoto(context, uri)
            try {
                val bmp = runCatching { decodePickedBitmap(cache, 2048) }.getOrNull()
                onReady(bmp)
            } finally {
                cache.delete()
            }
        }
    }

    /** Download + decrypt a video into the cache for in-app playback. */
    fun videoFile(context: Context, message: UiMessage, onReady: (String?) -> Unit) {
        val path = message.imagePath ?: return onReady(null)
        val key = message.imageKey ?: return onReady(null)
        val iv = message.imageIv ?: return onReady(null)
        viewModelScope.launch(Dispatchers.IO) {
            val f = java.io.File(context.cacheDir, "video_${message.id}.mp4")
            val ok = (f.exists() && f.length() > 0L) || runCatching {
                val bytes = Sangam.downloadImage(path, key, iv)
                f.writeBytes(bytes)
                true
            }.getOrDefault(false)
            onReady(if (ok) f.absolutePath else null)
        }
    }

    /** Send a video picked from the gallery (encrypted like photos), with
     *  a small thumbnail so the chat shows a preview. */
    fun sendPickedVideo(context: Context, uri: Uri) {
        if (blockIfCannotPost()) return
        if (chatKey.isBlank()) return
        viewModelScope.launch {
            busy = true; error = null
            val thumb = runCatching { videoThumbnail(context, uri) }.getOrNull()
            val bytes = runCatching { readRaw(context, uri, 100_000_000L) }
            if (bytes.isFailure) {
                error = "Could not read that video."
                busy = false
                return@launch
            }
            runCatching {
                videoSend(bytes.getOrThrow(), thumb).getOrThrow()
            }.fold(
                onSuccess = { refresh() },
                onFailure = { e -> error = "Video send failed — " + shortError(e) }
            )
            busy = false
        }
    }

    /** Decode the full-resolution photo the system camera saved for us. */
    fun decodeCameraFile(file: File, onReady: (android.graphics.Bitmap?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = runCatching { decodePickedBitmap(file, 4096) }.getOrNull()
            onReady(bmp)
        }
    }

    /** Send a document (PDF etc.) picked with the attach button. */
    /** Send a photo or video the friend can open once, then it is gone. */
    fun sendViewOnce(context: Context, uri: Uri) {
        Sangam.nextViewOnce = true
        viewModelScope.launch(Dispatchers.IO) {
            val isVideo = runCatching { context.contentResolver.getType(uri) }
                .getOrNull()?.startsWith("video/") == true
            if (isVideo) sendPickedVideo(context, uri) else sendImage(context, uri)
        }
    }

    /** Seal a photo, video or file so it only opens at a chosen time. */
    fun sendSealed(context: Context, uri: Uri, unlockAt: Long) {
        Sangam.nextSealedUntil = unlockAt
        viewModelScope.launch(Dispatchers.IO) {
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: ""
            when {
                mime.startsWith("video/") -> sendPickedVideo(context, uri)
                mime.startsWith("image/") -> sendImage(context, uri)
                else -> sendDocumentFile(context, uri)
            }
        }
    }

    fun sendDocumentFile(context: Context, uri: Uri) {
        if (blockIfCannotPost()) return
        if (chatKey.isBlank()) return
        val reply = replyTarget
        replyTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            busy = true; error = null
            val bytes = runCatching { readRaw(context, uri, 50_000_000L) }
            if (bytes.isFailure) {
                error = "Could not read that file."
                busy = false
                return@launch
            }
            val name = runCatching { queryDisplayName(context, uri) }.getOrDefault("document")
            runCatching {
                docSend(
                    bytes.getOrThrow(), name,
                    reply?.id, reply?.let { quotePreviewOf(it) }
                ).getOrThrow()
            }.fold(
                onSuccess = { refresh() },
                onFailure = { e -> error = "File send failed — " + shortError(e) }
            )
            busy = false
        }
    }

    /** Save a received document or video to the phone's Downloads. */
    fun saveMedia(context: Context, message: UiMessage) {
        val path = message.imagePath ?: return
        val key = message.imageKey ?: return
        val iv = message.imageIv ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = Sangam.downloadImage(path, key, iv)
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(
                            MediaStore.Downloads.DISPLAY_NAME,
                            safeFileName(message)
                        )
                        put(
                            MediaStore.Downloads.MIME_TYPE,
                            if (message.type == "d") "application/octet-stream" else "video/mp4"
                        )
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    ) ?: error("could not save")
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("could not save")
                } else {
                    val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                        ?: error("no storage available")
                    java.io.File(dir, safeFileName(message)).writeBytes(bytes)
                }
            }.fold(
                onSuccess = { savedMessage = "Saved to Downloads ✓" },
                onFailure = { savedMessage = "Could not save: ${it.message}" }
            )
        }
    }

    private fun safeFileName(message: UiMessage): String =
        if (message.type == "d")
            ((message.text ?: "document").replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" })
        else "Sangam_${System.currentTimeMillis()}.mp4"

    /** Send a photo taken with the in-chat camera. */
    fun sendPhotoBitmap(bmp: android.graphics.Bitmap) {
        if (chatKey.isBlank()) return
        val reply = replyTarget
        replyTarget = null
        viewModelScope.launch {
            busy = true; error = null
            val bytes = runCatching {
                val out = ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
            if (bytes.isFailure) {
                error = "Could not use that photo."
                busy = false
                return@launch
            }
            runCatching {
                imgSend(
                    bytes.getOrThrow(),
                    reply?.id, reply?.let { quotePreviewOf(it) }
                ).getOrThrow()
            }.fold(
                onSuccess = { refresh() },
                onFailure = { e -> error = "Photo send failed — " + shortError(e) }
            )
            busy = false
        }
    }

    fun sendImage(context: Context, uri: Uri) {
        if (blockIfCannotPost()) return
        if (chatKey.isBlank()) return
        val reply = replyTarget
        replyTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            busy = true; error = null
            val bytes = runCatching { readAndScale(context, uri) }
            if (bytes.isFailure) {
                error = "Could not read that photo — " +
                    shortError(bytes.exceptionOrNull() ?: IllegalStateException("no reason given"))
                busy = false
                return@launch
            }
            runCatching {
                imgSend(
                    bytes.getOrThrow(),
                    reply?.id, reply?.let { quotePreviewOf(it) }
                ).getOrThrow()
            }.fold(
                onSuccess = { refresh() },
                onFailure = { e ->
                    error = "Photo send failed — " + shortError(e)
                }
            )
            busy = false
        }
    }

    fun sendCapsule(text: String, unlockAt: Long) {
        if (blockIfCannotPost()) return
        if (chatKey.isBlank()) return
        if (text.isBlank()) return
        val reply = replyTarget
        replyTarget = null
        viewModelScope.launch {
            busy = true; error = null
            runCatching {
                envSend(
                    Envelope(
                        t = "c", b = text.trim(), u = unlockAt.toString(),
                        q = reply?.id, p = reply?.let { quotePreviewOf(it) }
                    )
                )
            }.fold(
                onSuccess = { refresh() },
                onFailure = { error = "Could not send. Check your connection." }
            )
            busy = false
        }
    }

    /** React to a message. Empty emoji clears my reaction. */
    fun react(targetId: String, emoji: String) {
        if (chatKey.isBlank()) return
        viewModelScope.launch {
            runCatching {
                envSend(Envelope(t = "r", u = targetId, b = emoji))
            }.onFailure { error = "Could not react. Check your connection." }
        }
    }

    /** Throttled ping so the peer sees "typing..." while I type. */
    fun noteTyping() {
        val now = System.currentTimeMillis()
        if (now - lastTypingPing < 2000) return
        lastTypingPing = now
        val id = chatKey
        if (id.isBlank()) return
        viewModelScope.launch { runCatching { Sangam.setTypingFor(id) } }
    }

    /** Deletes one of my messages for both sides. */
    fun deleteMessage(id: String) {
        viewModelScope.launch {
            runCatching { Sangam.deleteMessageEverywhere(id) }
                .onFailure { error = "Could not delete. Check your connection." }
                .onSuccess { refresh() }
        }
    }

    // ------------------------------------------------------ voice messages

    fun startRecording(context: Context) {
        if (blockIfCannotPost()) return
        if (recording) return
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val started = runCatching {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            r
        }
        if (started.isFailure) {
            error = "Could not start recording. Close other apps using the microphone."
            return
        }
        recorder = started.getOrNull()
        recordFile = file
        recordStart = System.currentTimeMillis()
        recording = true
        viewModelScope.launch {
            while (recording && isActive) {
                recordSeconds = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
                delay(300)
            }
        }
    }

    fun cancelRecording() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        recordFile?.delete()
        recordFile = null
        recordSeconds = 0
    }

    fun stopAndSendRecording() {
        val file = recordFile ?: return
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        val seconds = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
        val reply = replyTarget
        replyTarget = null
        recordFile = null
        recordSeconds = 0
        if (seconds < 1) {
            file.delete()
            return
        }
        if (chatKey.isBlank()) { file.delete(); return }
        viewModelScope.launch {
            busy = true; error = null
            val bytes = runCatching { file.readBytes() }
            file.delete()
            if (bytes.isFailure) {
                error = "Could not read the recording."
                busy = false
                return@launch
            }
            runCatching {
                audioSend(
                    bytes.getOrThrow(), seconds,
                    reply?.id, reply?.let { quotePreviewOf(it) }
                ).getOrThrow()
            }.fold(
                onSuccess = { refresh() },
                onFailure = { e -> error = "Voice send failed — " + shortError(e) }
            )
            busy = false
        }
    }

    fun toggleAudio(message: UiMessage, context: Context) {
        if (playingId == message.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        playingId = message.id
        playProgress = 0f
        viewModelScope.launch(Dispatchers.IO) {
            val file = runCatching {
                val bytes = Sangam.downloadImage(
                    message.imagePath!!, message.imageKey!!, message.imageIv!!
                )
                val f = File(context.cacheDir, "voice_play_${message.id}.m4a")
                f.writeBytes(bytes)
                f
            }.getOrNull()
            if (file == null) {
                error = "Could not play that voice message."
                playingId = null
                return@launch
            }
            val started = runCatching {
                val p = MediaPlayer()
                p.setDataSource(file.absolutePath)
                p.prepare()
                p.setOnCompletionListener {
                    playJob?.cancel()
                    playingId = null
                    playProgress = 0f
                    runCatching { p.release() }
                }
                p.start()
                p
            }.getOrNull()
            if (started == null) {
                playingId = null
                return@launch
            }
            player = started
            playJob = viewModelScope.launch {
                while (isActive && playingId == message.id) {
                    val total = started.duration.coerceAtLeast(1)
                    playProgress = started.currentPosition.toFloat() / total
                    delay(200)
                }
            }
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
        playingId = null
        playProgress = 0f
    }

    fun savePhoto(context: Context, messageId: String) {
        val bitmap = images[messageId] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Sangam_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: error("the gallery refused")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)) {
                        error("could not write the file")
                    }
                } ?: error("could not open the gallery")
            }.fold(
                onSuccess = { savedMessage = "Saved to gallery ✓" },
                onFailure = { savedMessage = "Could not save: ${it.message}" }
            )
        }
    }

    /** Tell the sender we opened their view-once photo (one-to-one). */
    fun notifyOpened(message: UiMessage) {
        // Keep the photo on screen for the rest of this visit.
        openedThisSession.add(message.id)
        val pid = peerId ?: return
        if (groupId != null) return
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }) {
            runCatching { Sangam.sendViewOnceOpened(pid, message.id) }
        }
    }

    private fun loadImages() {
        // Bring back anything already decoded this session - instant, no network.
        val fromCache = messages.filter {
            (it.type == "i" || it.type == "v") && !images.containsKey(it.id) &&
                Sangam.cachedImage(it.id) != null
        }
        if (fromCache.isNotEmpty()) {
            images = images + fromCache.mapNotNull { m ->
                Sangam.cachedImage(m.id)?.let { m.id to it }
            }
        }
        // Photos, and the thumbnails that travel with video messages.
        val pending = messages.filter {
            (it.type == "i" || (it.type == "v" && it.thumbPath != null)) &&
                it.imageKey != null && it.imageIv != null &&
                !images.containsKey(it.id) && !failedImages.contains(it.id) &&
                (it.type == "i" || it.thumbKey != null && it.thumbIv != null)
        }
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (message in pending) {
                val bitmap = runCatching {
                    val path = if (message.type == "v") message.thumbPath!! else message.imagePath!!
                    val key = if (message.type == "v") message.thumbKey!! else message.imageKey!!
                    val iv = if (message.type == "v") message.thumbIv!! else message.imageIv!!
                    val bytes = Sangam.downloadImage(path, key, iv)
                    decodeForDisplay(bytes)
                }.getOrNull()
                if (bitmap != null) {
                    Sangam.putImage(message.id, bitmap)
                    images = images + (message.id to bitmap)
                } else {
                    failedImages.add(message.id)
                }
            }
        }
    }

}

// ---------------------------------------------------------------- photo reading

/**
 * Copy a picked photo into our own cache file first. Some galleries give
 * Android a momentary permission lapse or an empty stream; retrying the
 * copy a few times sidesteps both.
 */
private fun copyPickedPhoto(context: Context, uri: Uri): File {
    val cache = File(context.cacheDir, "pick_${System.currentTimeMillis()}")
    var lastError: Exception? = null
    for (attempt in 1..3) {
        try {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                lastError = IllegalStateException("the gallery returned no data")
            } else {
                input.use { ins -> cache.outputStream().use { ins.copyTo(it) } }
                if (cache.length() > 0) return cache
                lastError = IllegalStateException("the photo arrived empty")
            }
        } catch (e: SecurityException) {
            cache.delete()
            throw IllegalStateException("Android refused access to the photo (${e.message})")
        } catch (e: Exception) {
            lastError = e
        }
        Thread.sleep(250)
    }
    cache.delete()
    throw IllegalStateException("could not copy the photo (${lastError?.message ?: "unknown reason"})")
}

private fun decodePickedBitmap(file: File, maxSide: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        file.delete()
        throw IllegalStateException(
            "your phone cannot decode this photo format - try a different photo"
        )
    }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(file.path, opts)
    if (bitmap == null) {
        file.delete()
        throw IllegalStateException("your phone cannot decode this photo")
    }
    return applyExifRotation(file, bitmap)
}

/**
 * Some cameras store "rotate 90 degrees" as a tag instead of real pixels;
 * honour it so portrait photos do not come out sideways.
 */
private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f); matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f); matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}

/** Read a picked photo (rotation corrected) ready for the crop screen. */
fun readForCrop(context: Context, uri: Uri): Bitmap {
    val cache = copyPickedPhoto(context, uri)
    try {
        return decodePickedBitmap(cache, 1440)
    } finally {
        cache.delete()
    }
}

/**
 * One short, friendly line from any error. URLs are stripped (they eat the
 * whole line) and the real reason is pulled out of the library's wording.
 */
fun shortError(e: Throwable): String {
    val raw = e.message.orEmpty()
    // "HTTP request to <url> failed with message: <reason>" -> just <reason>
    val reason = raw.substringAfter("failed with message:", "").trim()
    val source = reason.ifEmpty { raw }
    val cleaned = source
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("[\\s\\n\\r]+"), " ")
        .trim(' ', '-', ',', '.')
    val first = cleaned.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    if (!first.isNullOrBlank()) return first.take(140)
    val cause = e.cause?.message?.trim()
    if (!cause.isNullOrBlank()) return cause.take(140)
    return e.javaClass.simpleName
}

/** Read a picked photo, scaled to at most 1600px, as JPEG bytes. */
private fun readAndScale(context: Context, uri: Uri): ByteArray {
    val cache = copyPickedPhoto(context, uri)
    try {
        val bitmap = decodePickedBitmap(cache, 1600)
        val longest = maxOf(bitmap.width, bitmap.height)
        val scale = if (longest > 1600) longest / 1600f else 1f
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bitmap, (bitmap.width / scale).toInt(), (bitmap.height / scale).toInt(), true
            )
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    } finally {
        cache.delete()
    }
}

/** Read a picked file's raw bytes, refusing files above the cap. */
private fun readRaw(context: Context, uri: Uri, cap: Long): ByteArray {
    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    require(size in 1..cap) { "File is too large or empty" }
    return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Could not read the file")
}

/** The display name of a picked document. */
private fun queryDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx) ?: "document"
        }
    }
    return "document"
}

/** A small JPEG preview of a video, for the message bubble. */
private fun videoThumbnail(context: Context, uri: Uri, width: Int = 480): ByteArray? {
    return runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val frame = r.getFrameAtTime(0L)
                ?: return@runCatching null
            val scale = width.toFloat() / frame.width.coerceAtLeast(1)
            val thumb = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    frame, width,
                    (frame.height * scale).toInt().coerceAtLeast(1), true
                )
            } else frame
            val out = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()
}

// ------------------------------------------------------------------ backup

class BackupViewModel : ViewModel() {
    var busy by mutableStateOf(false)
        private set

    /** Collect the readable conversations and encrypt them with the password. */
    suspend fun export(password: String): ByteArray = withContext(Dispatchers.IO) {
        val me = Sangam.userId() ?: error("Not signed in")
        val profiles = Sangam.allProfiles()
        val convs = profiles.filterKeys { it != me }.values.mapNotNull { profile ->
            val rows = runCatching { Sangam.fetchConversation(profile.id) }
                .getOrDefault(emptyList())
            if (rows.isEmpty()) return@mapNotNull null
            val msgs = rows.map { Sangam.toUi(it) }
                .filter { it.type != "r" && it.type != "e" && !it.decryptFailed }
                .map {
                    com.sangam.messenger.data.BackupMessage(
                        mine = it.mine, at = it.createdAt, type = it.type, text = it.text
                    )
                }
            com.sangam.messenger.data.BackupConversation(
                name = profile.display_name,
                username = profile.username,
                msgs = msgs
            )
        }
        val json = Json.encodeToString(
            ListSerializer(com.sangam.messenger.data.BackupConversation.serializer()),
            convs
        )
        Sangam.backupEncrypt(json.toByteArray(), password)
    }

    // ---- v2.8: recovery key + cloud ----

    var recoveryKey by mutableStateOf("")
        private set

    var cloudNote by mutableStateOf<String?>(null)
        private set

    var hasBackup by mutableStateOf(false)
        private set

    fun loadRecoveryKey() {
        // Only show a key this phone actually made. A freshly generated one
        // would be a lie - it cannot open an older backup.
        recoveryKey = Sangam.existingRecoveryKey() ?: ""
        hasBackup = Sangam.hasCloudBackup()
    }

    fun backUpToCloud(onDone: (String) -> Unit) {
        viewModelScope.launch {
            busy = true; cloudNote = null
            val r = Sangam.uploadBackup()
            busy = false
            val msg = r.fold(
                onSuccess = {
                    hasBackup = true
                    recoveryKey = Sangam.existingRecoveryKey() ?: recoveryKey
                    "Backed up $it messages ✓"
                },
                onFailure = { "Could not back up: ${it.message}" }
            )
            cloudNote = msg
            onDone(msg)
        }
    }

    fun restoreFromCloud(typedKey: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            busy = true; cloudNote = null
            val r = Sangam.downloadBackup(typedKey)
            busy = false
            val msg = r.fold(
                onSuccess = {
                    "Restored $it messages. Reopen your chats. Messages that were " +
                        "already lost before the backup cannot be rebuilt."
                },
                onFailure = { "Could not restore: ${it.message}" }
            )
            cloudNote = msg
            onDone(msg)
        }
    }

    fun parseBackup(data: ByteArray, password: String): List<com.sangam.messenger.data.BackupConversation> {
        val plain = Sangam.backupDecrypt(data, password)
        return Json.decodeFromString(
            ListSerializer(com.sangam.messenger.data.BackupConversation.serializer()),
            String(plain)
        )
    }
}

// ---------------------------------------------------------------- my profile

class ProfileViewModel : ViewModel() {
    var profile by mutableStateOf<Profile?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            runCatching { profile = Sangam.myProfile() }
        }
    }

    /** "", "checking", "free" or "taken" for the username box. */
    var usernameCheck by mutableStateOf("")
        private set

    /** Look up whether a username is free, as it is typed. */
    private var usernameJob: kotlinx.coroutines.Job? = null

    fun checkUsername(u: String) {
        val v = u.trim().lowercase()
        // Drop the previous lookup, so an earlier answer cannot land last.
        usernameJob?.cancel()
        if (v.length < 3) {
            usernameCheck = ""
            return
        }
        usernameCheck = "checking"
        usernameJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(400)
            val free = Sangam.usernameAvailable(v)
            if (isActive) usernameCheck = if (free) "free" else "taken"
        }
    }

    fun saveUsername(u: String) {
        viewModelScope.launch {
            busy = true; message = null
            val r = Sangam.setUsername(u)
            busy = false
            r.fold(
                onSuccess = {
                    message = "Username changed to @" + u.trim().lowercase()
                    load()
                },
                onFailure = { message = it.message ?: "Could not change the username" }
            )
        }
    }

    fun save(displayName: String, status: String) {
        viewModelScope.launch {
            busy = true
            message = null
            val statusValue = status.takeIf { it.isNotBlank() }
            runCatching { Sangam.updateMyProfile(displayName, statusValue) }
                .onSuccess {
                    profile = profile?.copy(display_name = displayName, status = statusValue)
                    message = "Saved \u2713"
                }
                .onFailure { message = "Could not save — " + shortError(it) }
            busy = false
        }
    }

    /** Saves the cropped square photo as my avatar. */
    fun saveAvatarBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            message = null
            runCatching {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                val url = Sangam.uploadAvatar(out.toByteArray())
                profile = profile?.copy(avatar_url = url)
            }.onFailure { message = "Photo failed — " + shortError(it) }
            busy = false
        }
    }

    fun photoFailed(msg: String) {
        message = msg
    }

    fun clearMessage() {
        message = null
    }
}

// ----------------------------------------------------------------- updates

class UpdateViewModel : ViewModel() {
    var update by mutableStateOf<Updater.UpdateInfo?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    /** Ask the release server if a newer version exists. */
    fun check() {
        if (checking) return
        viewModelScope.launch {
            checking = true
            status = null
            update = runCatching { Updater.check() }.getOrNull()
            if (update == null) status = "latest"
            checking = false
        }
    }

    /** Download the newer APK and hand it to Android's installer. */
    fun downloadAndInstall(context: Context) {
        val info = update ?: return
        viewModelScope.launch {
            downloading = true
            status = null
            runCatching { Updater.download(info.apkUrl, context) }.fold(
                onSuccess = { file ->
                    downloading = false
                    Updater.install(context, file)
                },
                onFailure = {
                    downloading = false
                    status = "error"
                }
            )
        }
    }
}

// ----------------------------------------------------------------- new chat

class NewChatViewModel : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Profile>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set

    fun search() {
        val term = query.trim()
        if (term.length < 2) {
            results = emptyList()
            return
        }
        viewModelScope.launch {
            searching = true
            runCatching { results = Sangam.searchProfiles(term) }
            searching = false
        }
    }
}

// ---------------------------------------------------------------- new group

class NewGroupViewModel : ViewModel() {
    var query by mutableStateOf("")
    var groupName by mutableStateOf("")
    var results by mutableStateOf<List<Profile>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var creating by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    private var all: List<Profile> = emptyList()

    fun loadFriends() {
        viewModelScope.launch {
            searching = true
            val me = Sangam.userId()
            all = runCatching { Sangam.allProfiles().values.toList() }
                .getOrDefault(emptyList())
                .filter { it.id != me && !Sangam.isBlocked(it.id) }
                .sortedBy { it.display_name.lowercase() }
            results = all
            searching = false
        }
    }

    fun search() {
        val q = query.trim().lowercase()
        results = if (q.isEmpty()) all else all.filter {
            it.display_name.lowercase().contains(q) ||
                it.username.lowercase().contains(q)
        }
    }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun create(onDone: (String) -> Unit) {
        val name = groupName.trim()
        if (name.isEmpty()) {
            error = "Give the group a name"
            return
        }
        if (selected.isEmpty()) {
            error = "Pick at least one friend"
            return
        }
        creating = true
        error = null
        viewModelScope.launch {
            runCatching { Sangam.createGroup(name, selected.toList()) }.fold(
                onSuccess = {
                    creating = false
                    onDone(it.id)
                },
                onFailure = {
                    creating = false
                    error = "Could not create the group. Check your connection."
                }
            )
        }
    }
}
