package com.sangam.messenger.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.sangam.messenger.ui.theme.SangamGreen
import com.sangam.messenger.ui.theme.SangamOrange
import com.sangam.messenger.ui.theme.SangamTeal
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.sangam.messenger.data.Sangam
import com.sangam.messenger.data.UiMessage
import com.sangam.messenger.ui.theme.SangamGreen
import com.sangam.messenger.ui.theme.SangamIncomingBubble
import com.sangam.messenger.ui.theme.SangamTeal
import com.sangam.messenger.ui.theme.SangamTealDark
import com.sangam.messenger.vm.ChatViewModel
import com.sangam.messenger.vm.formatSeen
import com.sangam.messenger.vm.formatTime
import com.sangam.messenger.vm.quotePreviewOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Quick emoji strip for the input row. */
private val QUICK_EMOJIS = listOf(
    "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE0A", "\uD83D\uDE0D",
    "\uD83D\uDE18", "\uD83D\uDE0E", "\uD83E\uDD14", "\uD83D\uDE34", "\uD83D\uDE22", "\uD83D\uDE2D",
    "\uD83D\uDE24", "\uD83D\uDE20", "\uD83E\uDD2F", "\uD83E\uDD73", "\uD83D\uDE07", "\uD83E\uDD17",
    "\uD83D\uDE43", "\uD83D\uDE09", "\uD83D\uDC4D", "\uD83D\uDC4F", "\uD83D\uDE4F", "\uD83E\uDD1D",
    "\uD83D\uDCAA", "\u270C\uFE0F", "\uD83E\uDEB6", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83C\uDF89",
    "\u2728", "\uD83C\uDF8A", "\uD83C\uDF38", "\u2600\uFE0F", "\uD83C\uDF19", "\u2B50",
    "\u2615", "\uD83C\uDF55", "\uD83C\uDF82", "\uD83D\uDE80", "\u26BD", "\uD83C\uDFCF",
    "\uD83C\uDFC6", "\uD83C\uDFB5", "\uD83D\uDCF1", "\uD83D\uDCAF", "\uD83C\uDDEE\uD83C\uDFF3", "\u2705"
)

/** Emoji choices for message reactions. */
private val REACTION_EMOJIS = listOf(
    "\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerId: String,
    nameHint: String? = null,
    jumpTo: String? = null,
    groupId: String? = null,
    onBack: () -> Unit
) {
    val chatKey = groupId ?: peerId
    val vm: ChatViewModel = viewModel(key = "chat_" + chatKey)
    val context = LocalContext.current
    var input by remember(chatKey) { mutableStateOf(Sangam.draftFor(chatKey)) }
    var showEmoji by remember { mutableStateOf(false) }
    var showCapsule by remember { mutableStateOf(false) }
    var reactTarget by remember { mutableStateOf<UiMessage?>(null) }
    var reportTarget by remember { mutableStateOf<UiMessage?>(null) }
    var editTarget by remember { mutableStateOf<UiMessage?>(null) }
    var forwardTarget by remember { mutableStateOf<UiMessage?>(null) }
    var showFriendProfile by remember { mutableStateOf(false) }
    var showGroupInfo by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editPhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var videoPlayer by remember { mutableStateOf<Pair<UiMessage, String>?>(null) }
    var viewerPhotoId by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Calls: ring, answer, hang up. No audio yet.
    var callOpen by remember { mutableStateOf(false) }
    var showCallHistory by remember { mutableStateOf(false) }
    var callId by remember { mutableStateOf<String?>(null) }
    var callActive by remember { mutableStateOf(false) }
    var callIsVideo by remember { mutableStateOf(false) }
    val callScope = androidx.compose.runtime.rememberCoroutineScope()
    fun startCall(video: Boolean = false) {
        if (groupId != null) return
        callIsVideo = video
        callOpen = true
        callActive = false
        callScope.launch {
            val id = com.sangam.messenger.data.Sangam.startCall(peerId, if (video) "video" else "audio")
            callId = id
            var ticks = 0
            // Watch for the other side answering (auto-timeout after 45s if unanswered).
            while (id != null && callOpen) {
                kotlinx.coroutines.delay(1500)
                ticks += 1
                if (!callActive && ticks > 30) {
                    com.sangam.messenger.data.Sangam.endCall(id)
                    callOpen = false
                    return@launch
                }
                when (com.sangam.messenger.data.Sangam.callStatus(id)) {
                    "active" -> callActive = true
                    "declined", "ended" -> { callOpen = false; return@launch }
                }
            }
        }
    }
    var matchIndex by remember { mutableStateOf(-1) }
    var jumpTargetId by remember { mutableStateOf<String?>(null) }
    var flashId by remember { mutableStateOf<String?>(null) }
    // Message to scroll to when opened from the global search.
    var pendingJump by remember { mutableStateOf(jumpTo) }
    // After any jump, stay put until the user returns to the latest messages.
    var jumpedRecently by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var showStarred by remember { mutableStateOf(false) }
    var muted by remember(chatKey) { mutableStateOf(Sangam.isChatMuted(chatKey)) }
    var showDisappear by remember { mutableStateOf(false) }
    var disappearSecs by remember(chatKey) { mutableStateOf(Sangam.chatDisappear(chatKey)) }
    var sealUri by remember { mutableStateOf<Uri?>(null) }
    var showSealTime by remember { mutableStateOf(false) }
    var showSpecial by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(chatKey) {
        if (groupId != null) vm.startGroup(groupId) else vm.start(peerId)
    }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isEmpty() || searchOpen) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (jumpedRecently) {
            // We jumped somewhere on purpose - stay there until the user
            // scrolls back to the latest messages.
            if (lastVisible < vm.messages.size - 3) return@LaunchedEffect
            jumpedRecently = false
        }
        // Follow new messages when reading the latest, or when it is my own
        // message - but never yank the screen while reading older ones.
        val followMine = vm.messages.last().mine
        if (followMine || lastVisible >= vm.messages.size - 3) {
            listState.animateScrollToItem(vm.messages.size - 1)
        }
    }
    // Errors fade away on their own instead of staying on screen.
    LaunchedEffect(vm.error) {
        if (vm.error != null) {
            delay(4_000)
            vm.clearError()
        }
    }

    LaunchedEffect(vm.savedMessage) {
        vm.savedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.savedMessage = null
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        ExternalOps.pending = false
        if (uri != null) {
            // Photos and videos share the button; videos are sent encrypted.
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            if (isVideo) vm.sendPickedVideo(context, uri)
            else vm.decodeForEdit(context, uri) { bmp ->
                if (bmp != null) editPhoto = bmp else vm.sendImage(context, uri)
            }
        }
    }

    // Documents (PDF etc.) picked with the attach button.
    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        ExternalOps.pending = false
        if (uri != null) vm.sendDocumentFile(context, uri)
    }

    // A photo or video the friend may open once, then it is gone.
    val pickOnce = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) vm.sendViewOnce(context, uri)
    }

    // A photo, video or file to seal until a chosen time.
    val pickSeal = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            sealUri = uri
            showSealTime = true
        }
    }

    val requestMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startRecording(context)
        else Toast.makeText(
            context,
            "Microphone permission is needed for voice messages",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Full-resolution camera capture: the system camera writes into our
    // cache file through the file provider (the old preview API returned
    // only a small, blurry image).
    val cameraFile = remember { java.io.File(context.cacheDir, "camera_photo.jpg") }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok: Boolean ->
        ExternalOps.pending = false
        if (ok) {
            vm.decodeCameraFile(cameraFile) { bmp ->
                if (bmp != null) editPhoto = bmp
                else Toast.makeText(
                    context, "Could not read the photo", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ---- chat look (per-chat wallpaper + bubble colour) ----
    val dark = isSystemInDarkTheme()
    val themeSpec = remember(vm.chatThemeId) { chatThemeOf(vm.chatThemeId) }
    val wall = if (dark) themeSpec.darkWall else themeSpec.lightWall
    val mineColor = Color(themeSpec.bubble)

    // ---- in-chat search ----
    val matches = remember(vm.messages, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else vm.messages.filter {
            (it.type == "t" || it.type == "c") &&
                (it.text ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    fun stepMatch(delta: Int) {
        if (matches.isEmpty()) return
        val n = matches.size
        val cur = if (matchIndex < 0) n - 1 else matchIndex
        matchIndex = ((cur + delta) % n + n) % n
        jumpTargetId = matches[matchIndex].id
        flashId = matches[matchIndex].id
    }

    // As soon as you type, jump to the newest match.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            matchIndex = -1
        } else if (matches.isNotEmpty()) {
            matchIndex = matches.size - 1
            jumpTargetId = matches[matchIndex].id
            flashId = matches[matchIndex].id
        }
    }

    val dayFmt = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val showDate = remember(vm.messages) {
        if (vm.messages.isEmpty()) emptyList()
        else vm.messages.mapIndexed { i, m ->
            i == 0 || dayFmt.format(Date(m.createdAt)) !=
                dayFmt.format(Date(vm.messages[i - 1].createdAt))
        }
    }

    // Jump to a message (search hit or a reply quote tap).
    LaunchedEffect(jumpTargetId) {
        val id = jumpTargetId ?: return@LaunchedEffect
        val idx = vm.messages.indexOfFirst { it.id == id }
        // Scroll FIRST and only clear the target afterwards - clearing
        // while animating cancels the scroll half-way.
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            jumpedRecently = true
        }
        jumpTargetId = null  // lets the same target be tapped again
    }
    LaunchedEffect(flashId) {
        if (flashId != null) {
            delay(1200)
            flashId = null
        }
    }

    // Jump to the message found by the global search, once it is loaded.
    LaunchedEffect(vm.messages, pendingJump) {
        val id = pendingJump ?: return@LaunchedEffect
        val idx = vm.messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            listState.scrollToItem(idx)
            flashId = id
            jumpedRecently = true
            pendingJump = null
        }
    }
    LaunchedEffect(pendingJump) {
        // Give up if the message is not in the loaded history.
        if (pendingJump != null) {
            delay(8000)
            pendingJump = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (searchOpen) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    matchIndex = -1
                                },
                                placeholder = { Text("Search messages") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (matches.isEmpty()) "" else "${matchIndex + 1}/${matches.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { stepMatch(1) },
                                enabled = matches.isNotEmpty()
                            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Newer match") }
                            IconButton(
                                onClick = { stepMatch(-1) },
                                enabled = matches.isNotEmpty()
                            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Older match") }
                            IconButton(
                                onClick = { searchOpen = false; searchQuery = ""; matchIndex = -1 }
                            ) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
                        }
                    } else if (vm.isGroup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val gp = vm.groupPhotoBitmap
                        if (gp != null) {
                            Image(
                                bitmap = gp.asImageBitmap(),
                                contentDescription = "Group photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            InitialAvatar(vm.group?.name ?: "Group", size = 36)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { showGroupInfo = true }
                        ) {
                            Text(
                                vm.group?.name ?: "Group",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (vm.groupTypingNames.isNotEmpty()) {
                                Text(
                                    vm.groupTypingNames.joinToString(", ") +
                                        (if (vm.groupTypingNames.size == 1) " is typing…"
                                         else " are typing…"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    if (vm.groupMembers.isEmpty()) "group"
                                    else vm.groupMembers.values.joinToString(", ") { it.display_name },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            vm.peer?.display_name ?: nameHint ?: "Sangam friend",
                            vm.peer?.avatar_url,
                            size = 36
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { showFriendProfile = true }
                        ) {
                        Text(
                            vm.peer?.display_name ?: nameHint ?: "Sangam friend",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (vm.peerTyping) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots()
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "typing…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                        Text(
                            when {
                                vm.peerOnline -> "online"
                                vm.peerLastSeen > 0L -> "last seen " + formatSeen(vm.peerLastSeen)
                                !vm.peer?.status.isNullOrBlank() -> vm.peer?.status ?: ""
                                else -> "@" + (vm.peer?.username ?: "")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        }
                        }
                    }
                    }
                },
                actions = {
                    if (!searchOpen) {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search in chat")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Chat options")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                if (vm.isGroup) {
                                    DropdownMenuItem(
                                        text = { Text("Group info") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Info, contentDescription = null)
                                        },
                                        onClick = { menuOpen = false; showGroupInfo = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear chat") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Delete, contentDescription = null)
                                        },
                                        onClick = { menuOpen = false; confirmClear = true }
                                    )
                                }
                                if (!vm.isGroup) {
                                    DropdownMenuItem(
                                        text = { Text("Voice call") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Call, contentDescription = null)
                                        },
                                        onClick = { menuOpen = false; startCall(false) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Video call") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Videocam, contentDescription = null)
                                        },
                                        onClick = { menuOpen = false; startCall(true) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Call history") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Schedule, contentDescription = null)
                                        },
                                        onClick = { menuOpen = false; showCallHistory = true }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Chat theme") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Palette, contentDescription = null)
                                    },
                                    onClick = { menuOpen = false; showThemeDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Media") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Collections, contentDescription = null)
                                    },
                                    onClick = { menuOpen = false; showMedia = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Starred messages") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Star, contentDescription = null)
                                    },
                                    onClick = { menuOpen = false; showStarred = true }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (muted) "Unmute notifications" else "Mute notifications")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (muted) Icons.Filled.NotificationsOff
                                            else Icons.Filled.Notifications,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        muted = !muted
                                        Sangam.setChatMuted(chatKey, muted)
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Disappearing messages" +
                                                if (disappearSecs > 0) " · ${disappearLabel(disappearSecs)}"
                                                else " · Off"
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Schedule, contentDescription = null)
                                    },
                                    onClick = { menuOpen = false; showDisappear = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Search messages") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null)
                                    },
                                    onClick = { menuOpen = false; searchOpen = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = glassSurfaceColor(0.82f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(
                    if (wall != null)
                        Brush.linearGradient(listOf(Color(wall.first), Color(wall.second)))
                    else
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background
                            )
                        )
                )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "signal_note") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EncryptionNote()
                    }
                }
                itemsIndexed(vm.messages, key = { _, m -> "m_" + m.id }) { index, message ->
                    Column(Modifier.animateItem()) {
                    if (showDate.getOrNull(index) == true) {
                        DateChip(dayLabel(message.createdAt))
                    }
                    MessageBubble(
                        message = message,
                        bitmap = vm.images[message.id],
                        reaction = vm.reactions[message.id],
                        isPlaying = vm.playingId == message.id,
                        playProgress = if (vm.playingId == message.id) vm.playProgress else 0f,
                        mineColor = mineColor,
                        highlight = flashId == message.id,
                        starred = vm.isStarred(message.id),
                        opened = message.id in vm.openedIds,
                        onOpened = { vm.notifyOpened(message) },
                        onPlayToggle = { vm.toggleAudio(message, context) },
                        onOpenPhoto = { viewerPhotoId = message.id },
                        onSaveMedia = { vm.saveMedia(context, message) },
                        onPlayVideo = {
                            vm.videoFile(context, message) { p ->
                                if (p != null) videoPlayer = message to p
                                else Toast.makeText(
                                    context, "Could not load the video", Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onQuoteClick = if (message.replyTo != null) ({
                            jumpTargetId = message.replyTo
                            flashId = message.replyTo
                        }) else null,
                        onLongPress = { reactTarget = message }
                    )
                    }
                }
                // Messages written offline, waiting to be sent (clock icon).
                // A message can briefly be in both lists, so skip any that has
                // already arrived - and keep the two key spaces separate, or
                // Compose throws "Key ... was already used".
                val sentIds = vm.messages.map { it.id }.toSet()
                items(vm.pendingMsgs.filter { it.id !in sentIds }, key = { "p_" + it.id }) { message ->
                    Box(Modifier.animateItem()) {
                        MessageBubble(
                            message = message,
                            bitmap = null,
                            reaction = null,
                            isPlaying = false,
                            playProgress = 0f,
                            mineColor = mineColor,
                            highlight = false,
                            starred = false,
                            opened = false,
                            onOpened = {},
                            onPlayToggle = {},
                            onOpenPhoto = {},
                            onSaveMedia = {},
                            onPlayVideo = {},
                            onQuoteClick = null,
                            onLongPress = {}
                        )
                    }
                }
            }

            vm.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            if (showEmoji) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(QUICK_EMOJIS) { e ->
                        Text(
                            e,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { input += e }
                                .padding(6.dp)
                        )
                    }
                }
            }

            vm.replyTarget?.let { target ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Replying",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                quotePreviewOf(target),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { vm.cancelReply() }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel reply",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (vm.recording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "%d:%02d".format(vm.recordSeconds / 60, vm.recordSeconds % 60),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.cancelRecording() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancel recording",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { vm.stopAndSendRecording() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send voice message",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
            var showAttachTray by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (showAttachTray) {
                    GlassPill(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    showAttachTray = false
                                    ExternalOps.pending = true
                                    cameraFile.delete()
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "com.sangam.messenger.fileprovider", cameraFile
                                    )
                                    takePhoto.launch(uri)
                                },
                                enabled = !vm.busy
                            ) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = "Take a photo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showAttachTray = false
                                    ExternalOps.pending = true
                                    pickImage.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                        )
                                    )
                                },
                                enabled = !vm.busy
                            ) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = "Send a photo or video",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showAttachTray = false
                                    ExternalOps.pending = true
                                    pickDocument.launch("*/*")
                                },
                                enabled = !vm.busy
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = "Send a document",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showAttachTray = false
                                    showSpecial = true
                                },
                                enabled = !vm.busy
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Send a special photo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showAttachTray = false
                                    showCapsule = true
                                },
                                enabled = !vm.busy
                            ) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = "Send a time capsule",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
                if (vm.isGroup) {
                    val mentionQuery = Regex("(^|\\s)@([A-Za-z0-9_]*)$").find(input)
                        ?.groupValues?.get(2)
                    val suggestions = if (mentionQuery == null) emptyList()
                    else vm.groupMembers.values.filter {
                        it.username.startsWith(mentionQuery, true) ||
                            it.display_name.startsWith(mentionQuery, true)
                    }.take(4)
                    if (suggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.forEach { p ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        "@" + p.username,
                                        modifier = Modifier
                                            .clickable {
                                                val m = Regex("(^|\\s)@([A-Za-z0-9_]*)$")
                                                    .find(input)
                                                if (m != null) {
                                                    val prefix = input.substring(0, m.range.first) +
                                                        (if (m.groupValues[1].isEmpty()) "" else " ")
                                                    input = prefix + "@" + p.username + " "
                                                    Sangam.saveDraft(chatKey, input)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The message bar: floating frosted glass pill with specular border.
                    GlassPill(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            IconButton(
                                onClick = { showEmoji = !showEmoji },
                                enabled = !vm.busy
                            ) {
                                Icon(
                                    Icons.Filled.EmojiEmotions,
                                    contentDescription = "Emoji",
                                    tint = if (showEmoji) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showAttachTray = !showAttachTray },
                                enabled = !vm.busy
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = "Attach media or capsule",
                                    tint = if (showAttachTray) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = input,
                                onValueChange = {
                                    input = it
                                    Sangam.saveDraft(chatKey, it)
                                    vm.noteTyping()
                                },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 4,
                                decorationBox = { inner ->
                                    Box {
                                        if (input.isEmpty()) {
                                            Text(
                                                "Message",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Round action button: microphone when empty, send when typing.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    vm.sendText(input)
                                    input = ""
                                    Sangam.saveDraft(chatKey, "")
                                    showEmoji = false
                                } else {
                                    if (ContextCompat.checkSelfPermission(
                                            context, android.Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        vm.startRecording(context)
                                    } else {
                                        requestMic.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = !vm.busy,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (vm.busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Crossfade(
                                    targetState = input.isNotBlank(),
                                    animationSpec = tween(150),
                                    label = "action"
                                ) { typing ->
                                    if (typing) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Mic,
                                            contentDescription = "Record a voice message",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    reportTarget?.let { target ->
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Report this message?") },
            text = {
                Text(
                    "Send this message to the Sangam team for review. " +
                        "The person who sent it is not told."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    reportTarget = null
                    kotlinx.coroutines.MainScope().launch {
                        Sangam.reportAbuse("message", id)
                        android.widget.Toast.makeText(
                            ctx, "Report sent - thank you", android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }) { Text("Report") }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
            }
        )
    }

    reactTarget?.let { target ->
        // A still-sealed time capsule or an unopened view-once photo must not
        // be quoted or forwarded: either would hand the recipient the contents
        // before it is meant to be seen, or let them keep it forever.
        val stillSealed = target.unlockAt != null &&
            target.unlockAt!! > System.currentTimeMillis()
        val locked = stillSealed || (target.viewOnce && !target.mine)
        ReactionPicker(
            currentMine = vm.reactions[target.id]?.first,
            canDelete = target.mine && target.type != "r",
            starred = vm.isStarred(target.id),
            canEdit = target.mine && target.type == "t",
            canForward = !locked && (target.type == "t" || target.type == "i"),
            onReply = {
                if (!locked) vm.startReply(target)
                reactTarget = null
            },
            onStar = {
                vm.toggleStar(target.id)
                reactTarget = null
            },
            onEdit = {
                editTarget = target
                reactTarget = null
            },
            onForward = {
                forwardTarget = target
                reactTarget = null
            },
            onHide = {
                vm.hideMessage(target.id)
                reactTarget = null
            },
            onReport = {
                reportTarget = target
                reactTarget = null
            },
            onDelete = {
                vm.deleteMessage(target.id)
                reactTarget = null
            },
            onPick = { emoji ->
                // Tapping my current reaction again removes it.
                val next = if (vm.reactions[target.id]?.first == emoji) "" else emoji
                vm.react(target.id, next)
                reactTarget = null
            },
            onDismiss = { reactTarget = null }
        )
    }

    editTarget?.let { target ->
        EditMessageDialog(
            initial = target.text ?: "",
            onSave = {
                vm.editMessage(target.id, it)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    forwardTarget?.let { target ->
        FriendPickerDialog(
            title = "Forward to",
            onPick = { profile ->
                forwardTarget = null
                when {
                    target.type == "t" && !target.text.isNullOrEmpty() ->
                        vm.forwardText(profile.id, target.text!!)
                    target.type == "i" && vm.images[target.id] != null ->
                        vm.forwardImage(profile.id, vm.images[target.id]!!)
                    else -> Toast.makeText(
                        context,
                        "Only text messages and photos can be forwarded",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { forwardTarget = null }
        )
    }

    if (showFriendProfile) {
        FriendProfileDialog(
            profile = vm.peer,
            onVoiceCall = {
                showFriendProfile = false
                startCall(false)
            },
            onVideoCall = {
                showFriendProfile = false
                startCall(true)
            },
            onBlock = {
                val name = vm.peer?.display_name ?: nameHint ?: "this friend"
                Sangam.blockPeer(peerId, name)
                showFriendProfile = false
                onBack()
            },
            onDismiss = { showFriendProfile = false }
        )
    }

    if (showGroupInfo && vm.isGroup) {
        GroupInfoDialog(
            vm = vm,
            onDismiss = { showGroupInfo = false },
            onLeft = {
                showGroupInfo = false
                onBack()
            },
            onDeleted = {
                showGroupInfo = false
                onBack()
            }
        )
    }

    if (showDisappear) {
        AlertDialog(
            onDismissRequest = { showDisappear = false },
            title = { Text("Disappearing messages") },
            text = {
                Column {
                    Text(
                        "New messages in this chat disappear from both phones " +
                            "after the time you choose. Older messages stay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        0L to "Off",
                        3600L to "1 hour",
                        86400L to "24 hours",
                        604800L to "7 days"
                    ).forEach { (secs, label) ->
                        val on = disappearSecs == secs
                        Text(
                            label,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Sangam.setChatDisappear(chatKey, secs)
                                    disappearSecs = secs
                                    showDisappear = false
                                }
                                .padding(vertical = 13.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisappear = false }) { Text("Close") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat") },
            text = {
                Text("Remove every message in this group from this phone? " +
                    "Other members keep their copy.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearGroupChat()
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }

    editPhoto?.let { bmp ->
        Dialog(
            onDismissRequest = { editPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PhotoEditScreen(
                initial = bmp,
                onSend = { edited, hd ->
                    Sangam.nextHd = hd
                    vm.sendEditedBitmap(edited)
                    editPhoto = null
                },
                onCancel = { editPhoto = null }
            )
        }
    }

    videoPlayer?.let { (msg, path) ->
        VideoPlayerDialog(
            path = path,
            onDismiss = { videoPlayer = null },
            onSave = { vm.saveMedia(context, msg) }
        )
    }

    if (showCapsule) {
        CapsuleDialog(
            onSend = { text, unlockAt ->
                vm.sendCapsule(text, unlockAt)
                showCapsule = false
            },
            onDismiss = { showCapsule = false }
        )
    }

    // One button for both special sends: view once, or sealed.
    if (showSpecial) {
        AlertDialog(
            onDismissRequest = { showSpecial = false },
            title = { Text("Send a special photo") },
            text = {
                Column {
                    Text(
                        "Pick a photo, video or file, then choose how it travels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "\uD83D\uDC41\uFE0F  View once",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSpecial = false
                                pickOnce.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                )
                            }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        "They can open it one time, then it is gone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "\u23F0  Time capsule",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSpecial = false
                                pickSeal.launch("*/*")
                            }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        "It stays sealed until the time you choose.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpecial = false }) { Text("Close") }
            }
        )
    }

    // Sealing a picked photo / video / file: reuse the capsule time picker.
    if (showSealTime && sealUri != null) {
        CapsuleDialog(
            requireText = false,
            onSend = { _, unlockAt ->
                vm.sendSealed(context, sealUri!!, unlockAt)
                showSealTime = false
                sealUri = null
            },
            onDismiss = {
                showSealTime = false
                sealUri = null
            }
        )
    }

    if (showThemeDialog) {
        ChatThemeDialog(
            current = vm.chatThemeId,
            onPick = {
                vm.setChatTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showMedia) {
        MediaGalleryDialog(
            vm = vm,
            onDismiss = { showMedia = false },
            onOpenPhoto = {
                showMedia = false
                viewerPhotoId = it
            }
        )
    }

    if (showStarred) {
        StarredMessagesDialog(
            vm = vm,
            onDismiss = { showStarred = false },
            onJump = { id ->
                showStarred = false
                jumpTargetId = id
                flashId = id
            }
        )
    }

    viewerPhotoId?.let { id ->
        vm.images[id]?.let { bmp ->
            PhotoViewer(
                bitmap = bmp,
                onSave = { vm.savePhoto(context, id) },
                onDismiss = { viewerPhotoId = null }
            )
        }
    }

    if (showCallHistory) {
        CallHistoryDialog(
            peerName = nameHint ?: "Calls",
            load = { com.sangam.messenger.data.Sangam.callHistory(peerId) },
            myId = { com.sangam.messenger.data.Sangam.userId() ?: "" },
            onDismiss = { showCallHistory = false }
        )
    }

    if (callOpen) {
        CallScreen(
            peerName = vm.peer?.display_name ?: nameHint ?: "Sangam friend",
            incoming = false,
            active = callActive,
            callId = callId,
            isVideo = callIsVideo,
            onAnswer = {},
            onDecline = {
                callId?.let { callScope.launch { com.sangam.messenger.data.Sangam.endCall(it, true) } }
                callOpen = false
            },
            onHangUp = {
                callId?.let { callScope.launch { com.sangam.messenger.data.Sangam.endCall(it) } }
                callOpen = false
            }
        )
    }
}

// ------------------------------------------------------------------ bubbles

/** Colours @username mentions inside a group message. */
/** Mark http(s) and sangam:// links so the bubble can make them tappable. */
private fun linkify(text: String): AnnotatedString {
    val b = AnnotatedString.Builder()
    val re = Regex("(https?://[^\\s]+|sangam://[^\\s]+)")
    var last = 0
    for (m in re.findAll(text)) {
        b.append(text.substring(last, m.range.first))
        b.pushStringAnnotation("URL", m.value)
        b.append(m.value)
        b.pop()
        last = m.range.last + 1
    }
    b.append(text.substring(last))
    return b.toAnnotatedString()
}

private fun mentionAnnotated(text: String): AnnotatedString {
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        val at = text.indexOf('@', i)
        if (at < 0) {
            b.append(text.substring(i))
            break
        }
        b.append(text.substring(i, at))
        var j = at + 1
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
        if (j > at + 1) {
            b.withStyle(SpanStyle(color = SangamTeal, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(at, j))
            }
        } else {
            b.append("@")
        }
        i = j
    }
    return b.toAnnotatedString()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: UiMessage,
    bitmap: android.graphics.Bitmap?,
    reaction: Pair<String?, String?>?,
    isPlaying: Boolean,
    playProgress: Float,
    mineColor: Color,
    highlight: Boolean,
    starred: Boolean,
    opened: Boolean,
    onOpened: () -> Unit,
    onPlayToggle: () -> Unit,
    onOpenPhoto: () -> Unit,
    onSaveMedia: () -> Unit,
    onPlayVideo: () -> Unit,
    onQuoteClick: (() -> Unit)?,
    onLongPress: () -> Unit
) {
    val bubbleColor =
        if (message.mine) mineColor else glassSurfaceColor(0.86f)
    val onBubble = if (message.mine) Color.White else MaterialTheme.colorScheme.onSurface
    val onBubbleSubtle =
        if (message.mine) Color.White.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (message.mine) 18.dp else 4.dp,
        bottomEnd = if (message.mine) 4.dp else 18.dp
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight)
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                else Modifier
            )
            .combinedClickable(
                onClick = { if (message.type == "i" && bitmap != null) onOpenPhoto() },
                onLongClick = onLongPress
            ),
        contentAlignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
            if (!message.mine && message.groupId != null && !message.senderName.isNullOrBlank()) {
                Text(
                    message.senderName ?: "",
                    color = SangamTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .border(
                        width = 0.8.dp,
                        brush = if (message.mine)
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                            )
                        else glassBorderBrush(),
                        shape = bubbleShape
                    )
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.quote != null) {
                        val qClick = onQuoteClick
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(onBubble.copy(alpha = 0.16f))
                                .then(
                                    if (qClick != null) Modifier.clickable { qClick() }
                                    else Modifier
                                )
                                .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(width = 3.dp, height = 32.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(onBubble.copy(alpha = 0.7f))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                message.quote ?: "",
                                color = onBubble.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    val now = System.currentTimeMillis()
                    when {
                        message.decryptFailed -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = onBubbleSubtle,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "Earlier message (key updated)",
                                color = onBubbleSubtle,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        message.viewOnce && message.mine -> {
                            Column(Modifier.padding(4.dp)) {
                                Text("\uD83D\uDD12", fontSize = 28.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (opened) "View once · Opened" else "View once · Sent",
                                    color = onBubble,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    if (opened) "Your friend has seen it"
                                    else "They can open it one time",
                                    color = onBubbleSubtle,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        message.viewOnce && !viewOnceRevealed.containsKey(message.id) -> {
                            Column(
                                Modifier
                                    .clickable {
                                        Sangam.markViewOnceUsed(message.id)
                                        viewOnceRevealed[message.id] = true
                                        onOpened()
                                    }
                                    .padding(4.dp)
                            ) {
                                Text("\uD83D\uDD12", fontSize = 30.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "View once",
                                    color = onBubble,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Tap to open - then it is gone",
                                    color = onBubbleSubtle,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        message.unlockAt != null && now < message.unlockAt -> {
                            Text("\uD83D\uDD70\uFE0F", fontSize = 34.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Time capsule — still sealed",
                                color = onBubble,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Opens " + capsuleOpensAt(message.unlockAt),
                                color = onBubbleSubtle,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        message.type == "i" -> {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Photo",
                                    modifier = Modifier
                                        .width(240.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.FillWidth
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        message.type == "a" -> {
                            val secs = (message.text ?: "0").toIntOrNull() ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = onBubble,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onPlayToggle() }
                                        .padding(4.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "%d:%02d".format(secs / 60, secs % 60),
                                        color = onBubble,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = playProgress,
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = onBubble,
                                        trackColor = onBubble.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                        message.type == "d" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSaveMedia() }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.InsertDriveFile,
                                    contentDescription = "Document",
                                    tint = onBubble,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.width(190.dp)) {
                                    Text(
                                        message.text ?: "Document",
                                        color = onBubble,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Tap to save to Downloads",
                                        color = onBubbleSubtle,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        message.type == "v" -> {
                            // Big video bubble with the thumbnail preview.
                            Box(
                                modifier = Modifier
                                    .width(260.dp)
                                    .aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF15191C))
                                    .clickable { onPlayVideo() }
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Video",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(52.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Play video",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Text(
                                    "Video",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.45f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        else -> {
                            if (message.type == "c") {
                                Text(
                                    "\uD83D\uDD70\uFE0F Capsule opened",
                                    color = onBubbleSubtle,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            val body = message.text ?: ""
                            val hasLink = body.contains("http://") ||
                                body.contains("https://") || body.contains("sangam://")
                            if (hasLink) {
                                // A link in a message is tappable: opens the group
                                // invite, or hands a web link to the browser.
                                val linked = linkify(body)
                                val ctx = androidx.compose.ui.platform.LocalContext.current
                                androidx.compose.foundation.text.ClickableText(
                                    text = linked,
                                    style = MaterialTheme.typography.bodyMedium
                                        .copy(color = onBubble),
                                    onClick = { off ->
                                        val url = linked.getStringAnnotations(
                                            "URL", off, off
                                        ).firstOrNull()?.item ?: return@ClickableText
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            )
                                        }
                                    }
                                )
                            } else if (message.groupId != null && body.contains("@")) {
                                Text(
                                    mentionAnnotated(body),
                                    color = onBubble,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    body,
                                    color = onBubble,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            val groupMatch = Regex("sangam://group\\?id=([A-Za-z0-9\\-]+)").find(body)
                            val addMatch = Regex("sangam://add\\?u=([A-Za-z0-9_]+)").find(body)
                            val webMatch = Regex("https?://\\S+").find(body)
                            if (groupMatch != null) {
                                val targetGid = groupMatch.groupValues[1]
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = onBubble.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            com.sangam.messenger.MainActivity.pendingGroup = targetGid
                                        }
                                ) {
                                    Text(
                                        "\uD83C\uDF0A  Join Sangam Group",
                                        color = onBubble,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            } else if (addMatch != null) {
                                val targetUser = addMatch.groupValues[1]
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = onBubble.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            com.sangam.messenger.MainActivity.pendingInvite = targetUser
                                        }
                                ) {
                                    Text(
                                        "\uD83D\uDC4B  Connect with @" + targetUser,
                                        color = onBubble,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            } else if (webMatch != null) {
                                val webUrl = webMatch.value
                                val uriCtx = LocalContext.current
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = onBubble.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            runCatching {
                                                uriCtx.startActivity(
                                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(webUrl))
                                                )
                                            }
                                        }
                                ) {
                                    Text(
                                        "\uD83D\uDD17  Open link",
                                        color = onBubble,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.viaNearby) {
                            Icon(
                                Icons.Filled.Wifi,
                                contentDescription = "Sent over Wi-Fi Direct",
                                tint = if (message.mine) Color.White.copy(alpha = 0.9f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (starred) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Starred",
                                tint = Color(0xFFF5A623),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (message.edited) {
                            Text(
                                "edited",
                                color = if (message.mine) Color.White.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            formatTime(message.createdAt),
                            color = if (message.mine) Color.White.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        if (message.mine) {
                            when {
                                message.pending -> Icon(
                                    Icons.Filled.Schedule, contentDescription = "Waiting to send",
                                    tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp)
                                )
                                message.groupId != null -> {
                                    val allRead = message.groupTotal > 0 &&
                                        message.groupRead >= message.groupTotal
                                    if (message.groupDelivered > 0 || allRead) Icon(
                                        Icons.Filled.DoneAll,
                                        contentDescription =
                                            if (allRead) "Read by everyone" else "Delivered",
                                        tint = if (allRead) SangamTeal else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    ) else Icon(
                                        Icons.Filled.Done, contentDescription = "Sent",
                                        tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp)
                                    )
                                }
                                message.read -> Icon(
                                    Icons.Filled.DoneAll, contentDescription = "Read",
                                    tint = Color(0xFFFF8A80), modifier = Modifier.size(13.dp)
                                )
                                message.delivered -> Icon(
                                    Icons.Filled.DoneAll, contentDescription = "Delivered",
                                    tint = Color.White, modifier = Modifier.size(13.dp)
                                )
                                else -> Icon(
                                    Icons.Filled.Done, contentDescription = "Sent",
                                    tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reaction chips under the bubble.
            val r = reaction
            if (r != null) {
                val emojis = listOfNotNull(
                    r.first?.takeIf { it.isNotEmpty() },
                    r.second?.takeIf { it.isNotEmpty() }
                )
                if (emojis.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        emojis.groupingBy { it }.eachCount().forEach { (e, n) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (n > 1) "$e \u00D7$n" else e,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Today" / "Yesterday" / a date, for the little day chips in chats. */
private fun dayLabel(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    return when {
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
        else -> {
            val y = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            if (cal.get(Calendar.YEAR) == y.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
            ) "Yesterday"
            else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
        }
    }
}

@Composable
private fun DateChip(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

private fun capsuleOpensAt(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

/** UTC midnight of today's LOCAL date - what the date picker expects. */
private fun todayUtcMillis(): Long {
    val local = Calendar.getInstance()
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.set(
        local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH),
        0, 0, 0
    )
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// ------------------------------------------------------------------ reactions

@Composable
private fun ReactionPicker(
    currentMine: String?,
    canDelete: Boolean,
    starred: Boolean,
    canEdit: Boolean,
    canForward: Boolean,
    onPick: (String) -> Unit,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onEdit: () -> Unit,
    onForward: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("React", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                REACTION_EMOJIS.forEach { e ->
                    Text(
                        e,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (currentMine == e) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .clickable { onPick(e) }
                            .padding(6.dp)
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReply() }
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Reply",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStar() }
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (starred) Color(0xFFF5A623) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (starred) "Unstar" else "Star",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (canEdit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit() }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Edit",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReport() }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "\u26A0",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Report",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (canForward) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onForward() }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Forward",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                "Delete for me",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHide() }
                    .padding(vertical = 8.dp)
            )
            if (canDelete) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Delete for everyone",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDelete() }
                        .padding(vertical = 8.dp)
                )
            }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ capsules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapsuleDialog(
    onSend: (String, Long) -> Unit,
    onDismiss: () -> Unit,
    // Sealing a photo or file needs no caption, so do not force one. The text
    // typed here is discarded for media anyway.
    requireText: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    val nowCal = Calendar.getInstance()
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = todayUtcMillis()
    )
    val timeState = rememberTimePickerState(
        initialHour = nowCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = nowCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    fun unlockMillis(): Long? {
        val selected = dateState.selectedDateMillis ?: return null
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selected
        }
        val local = Calendar.getInstance().apply {
            clear()
            set(
                utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                timeState.hour, timeState.minute, 0
            )
        }
        return local.timeInMillis
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time capsule \uD83D\uDD70\uFE0F", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Write a message that stays sealed. It will appear for your friend at the moment you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Your message") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                DatePicker(state = dateState)
                Spacer(Modifier.height(12.dp))
                TimePicker(state = timeState)
                val at = unlockMillis()
                if (at != null && at <= System.currentTimeMillis()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "That time is already past — the capsule would open right away.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val at = unlockMillis() ?: return@TextButton
                    if (!requireText || text.isNotBlank()) onSend(text.trim(), at)
                },
                enabled = !requireText || text.isNotBlank()
            ) { Text("Seal it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ photo viewer

@Composable
private fun PhotoViewer(
    bitmap: android.graphics.Bitmap,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            onTap = { onDismiss() }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            Surface(
                shape = CircleShape,
                color = Color(0xAA000000),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Row {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.Save, contentDescription = "Save to gallery", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ chat themes

/** Per-chat look. Wallpaper colours + my bubble colour. */
private data class ChatThemeSpec(
    val id: String,
    val label: String,
    val lightWall: Pair<Long, Long>?,
    val darkWall: Pair<Long, Long>?,
    val bubble: Long
)

private val CHAT_THEMES = listOf(
    ChatThemeSpec("default", "Default", null, null, 0xFF2E8FAD),
    ChatThemeSpec("teal", "Sangam", 0xFFD8EFF7 to 0xFFA9DEEF, 0xFF12313C to 0xFF0B222B, 0xFF1F7A96),
    ChatThemeSpec("orange", "Sunset", 0xFFFDEBDC to 0xFFF8CFA8, 0xFF3B2013 to 0xFF2A150D, 0xFFE06A1B),
    ChatThemeSpec("forest", "Forest", 0xFFDFEFE0 to 0xFFBADFB9, 0xFF143018 to 0xFF0C1F0F, 0xFF2E7D4F),
    ChatThemeSpec("lavender", "Lavender", 0xFFEAE4F7 to 0xFFD0C5F0, 0xFF231F37 to 0xFF161226, 0xFF6A5AB8),
    ChatThemeSpec("rose", "Rose", 0xFFFBE4E9 to 0xFFF5C4D0, 0xFF361920 to 0xFF260F16, 0xFFC25570),
    ChatThemeSpec("midnight", "Midnight", 0xFF2C3E50 to 0xFF1B2A38, 0xFF0D141B to 0xFF080D12, 0xFF3E5871)
)

private fun chatThemeOf(id: String): ChatThemeSpec =
    CHAT_THEMES.firstOrNull { it.id == id } ?: CHAT_THEMES[0]

@Composable
private fun ChatThemeDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat theme", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CHAT_THEMES.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t ->
                            val wall = if (dark) t.darkWall else t.lightWall
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (wall != null)
                                                Brush.linearGradient(
                                                    listOf(Color(wall.first), Color(wall.second))
                                                )
                                            else
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                )
                                        )
                                        .border(
                                            width = if (t.id == current) 2.dp else 1.dp,
                                            color = if (t.id == current)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onPick(t.id) },
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    if (t.id != "default") {
                                        Box(
                                            Modifier
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(t.bubble))
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(t.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ media gallery

/** All photos and voice notes of this chat in one grid. */
@Composable
private fun MediaGalleryDialog(
    vm: ChatViewModel,
    onDismiss: () -> Unit,
    onOpenPhoto: (String) -> Unit
) {
    val context = LocalContext.current
    val media = remember(vm.messages) {
        vm.messages
            .filter { it.type == "i" || it.type == "a" }
            // View-once photos are not browsable: the gallery would let a
            // recipient open and save them without ever marking them seen.
            .filter { !it.viewOnce }
            .sortedByDescending { it.createdAt }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Media", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                if (media.isEmpty()) {
                    Text(
                        "No photos or voice notes yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(media.size) { i ->
                            val m = media[i]
                            if (m.type == "i") {
                                val bmp = vm.images[m.id]
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onOpenPhoto(m.id) }
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                                        )
                                    }
                                }
                            } else {
                                val secs = (m.text ?: "0").toIntOrNull() ?: 0
                                val playing = vm.playingId == m.id
                                Box(
                                    Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { vm.toggleAudio(m, context) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (playing) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Text(
                                            "%d:%02d".format(secs / 60, secs % 60),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Starred messages of this chat; tap one to jump to it. */
@Composable
private fun StarredMessagesDialog(
    vm: ChatViewModel,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit
) {
    val starred = remember(vm.messages, vm.starredIds) {
        vm.messages.filter { it.id in vm.starredIds }.sortedByDescending { it.createdAt }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Starred messages", fontWeight = FontWeight.SemiBold) },
        text = {
            if (starred.isEmpty()) {
                Text(
                    "No starred messages yet. Long-press a message and tap Star.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(starred.size) { i ->
                        val m = starred[i]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJump(m.id) }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFF5A623),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (m.mine) "You" else (vm.peer?.display_name ?: "Friend"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                quotePreviewOf(m),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatTime(m.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** View-once photos and videos opened during this sitting. */
private val viewOnceRevealed = mutableStateMapOf<String, Boolean>()

/** "1 hour" / "24 hours" / "7 days" for a disappearing window in seconds. */
private fun disappearLabel(secs: Long): String = when (secs) {
    3600L -> "1 hour"
    86400L -> "24 hours"
    604800L -> "7 days"
    else -> when {
        secs >= 86400L -> "${secs / 86400} days"
        secs >= 3600L -> "${secs / 3600} hours"
        else -> "$secs seconds"
    }
}
