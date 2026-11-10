package com.sangam.messenger.ui

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sangam.messenger.data.ChatSummary
import com.sangam.messenger.data.Sangam
import com.sangam.messenger.Ui
import com.sangam.messenger.ui.theme.SangamOrange
import com.sangam.messenger.ui.theme.SangamTeal
import com.sangam.messenger.vm.ChatsViewModel
import com.sangam.messenger.vm.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (peerId: String, peerName: String, jumpTo: String?) -> Unit,
    onOpenGroup: (groupId: String, groupName: String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onOpenNearby: () -> Unit,
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWhatsNew: () -> Unit
) {
    val vm: ChatsViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.start(context) }

    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var legalOpen by remember { mutableStateOf(false) }
    var scanOpen by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var hdPhotos by remember { mutableStateOf(Sangam.hdMedia()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pinnedIds by remember { mutableStateOf(Sangam.pinnedChats()) }
    var pinDialogFor by remember { mutableStateOf<ChatSummary?>(null) }
    var blockedDialog by remember { mutableStateOf(false) }
    var appLockDialog by remember { mutableStateOf(false) }
    var notifDialog by remember { mutableStateOf(false) }
    var privacyDialog by remember { mutableStateOf(false) }
    var backupDialog by remember { mutableStateOf(false) }
    var cloudDialog by remember { mutableStateOf(false) }
    var homeTab by remember { mutableStateOf("messages") }
    var callOpen by remember { mutableStateOf(false) }
    var callPeerName by remember { mutableStateOf("Sangam friend") }
    var callId by remember { mutableStateOf<String?>(null) }
    var callActive by remember { mutableStateOf(false) }
    var callIsVideo by remember { mutableStateOf(false) }
    val callScope = androidx.compose.runtime.rememberCoroutineScope()
    fun startCallFromTab(peerId: String, peerName: String, video: Boolean) {
        callPeerName = peerName
        callIsVideo = video
        callOpen = true
        callActive = false
        callScope.launch {
            val id = Sangam.startCall(peerId, if (video) "video" else "audio")
            callId = id
            var ticks = 0
            while (id != null && callOpen) {
                kotlinx.coroutines.delay(1500)
                ticks += 1
                if (!callActive && ticks > 30) {
                    Sangam.endCall(id)
                    callOpen = false
                    return@launch
                }
                when (Sangam.callStatus(id)) {
                    "active" -> callActive = true
                    "declined", "ended" -> { callOpen = false; return@launch }
                }
            }
        }
    }

    // Keep my own photo/name fresh in the menu header.
    LaunchedEffect(menuOpen) {
        if (menuOpen) vm.loadMyProfile()
    }

    // Phones and tablets signed in to this account.
    if (showDevices) {
        AlertDialog(
            onDismissRequest = { showDevices = false },
            containerColor = glassSurfaceColor(0.95f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SangamMark(24)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Confluence Devices & Join Hub", fontWeight = FontWeight.Bold)
                        Text(
                            "Link another phone or join a group via QR / link",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.devices.isEmpty()) {
                        GlassCard(cornerRadius = 16) {
                            Text(
                                "This phone (" + Sangam.deviceLabel() + ") is active on your account.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    vm.devices.forEach { d ->
                        val isThis = d.id == Sangam.deviceRowId() || d.deviceKey == Sangam.deviceKey()
                        GlassCard(cornerRadius = 16) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.label ?: "Sangam Phone", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isThis) SangamTeal.copy(alpha = 0.18f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            if (isThis) "THIS PHONE · DEVICE #" + d.deviceKey
                                            else "LINKED · DEVICE #" + d.deviceKey,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isThis) SangamTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "ID: " + d.id.take(8) +
                                            (if (d.registrationId != null) " · Reg #" + d.registrationId else "") +
                                            (if (!d.identityKey.isNullOrBlank()) " · E2EE Ready" else ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!isThis) {
                                    TextButton(onClick = { vm.removeDevice(d.id) }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    var showLinkQr by remember { mutableStateOf(false) }
                    var pastedCode by remember { mutableStateOf("") }
                    Button(
                        onClick = {
                            showDevices = false
                            scanOpen = true
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            "\uD83D\uDCF7  Link Windows App / Scan QR Code",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val syncCtx = LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SangamTeal.copy(alpha = 0.16f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showLinkQr = !showLinkQr }
                        ) {
                            Text(
                                if (showLinkQr) "Hide Phone QR" else "\uD83D\uDCF1 Phone Login QR",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SangamTeal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SangamOrange.copy(alpha = 0.16f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val ok = Sangam.syncDesktopOutbox()
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(
                                                syncCtx,
                                                if (ok) "\uD83D\uDCBB Synced chat history to Windows!"
                                                else "Could not sync history",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                        ) {
                            Text(
                                "\uD83D\uDD04 Sync to Windows",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SangamOrange,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                        }
                    }
                    if (showLinkQr) {
                        val myUser = vm.myProfile?.username ?: Sangam.nearbyUsername()
                        val qrData = remember(myUser) { Sangam.deviceLoginQrPayload(myUser) }
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SangamQrBox(
                                data = qrData,
                                subtitle = "On your 2nd phone's Login screen, tap 'Link this phone by scanning QR'",
                                sizeDp = 155
                            )
                        }
                    }
                    OutlinedTextField(
                        value = pastedCode,
                        onValueChange = { pastedCode = it },
                        placeholder = { Text("Paste group or invite link (sangam://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pastedCode.isNotBlank()) {
                        TextButton(onClick = {
                            val raw = pastedCode.trim()
                            val dMatch = Regex("sangam://desktop\\?id=([A-Za-z0-9_\\-]+)(?:&k=([A-Za-z0-9_\\-]+))?").find(raw)
                            val gMatch = Regex("sangam://group\\?id=([A-Za-z0-9\\-]+)").find(raw)
                            val uMatch = Regex("sangam://add\\?u=([A-Za-z0-9_]+)").find(raw)
                            when {
                                dMatch != null -> {
                                    val codeId = dMatch.groupValues[1]
                                    val dKey = dMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                                    showDevices = false
                                    kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val ok = Sangam.approveDesktopPairing(codeId, dKey)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(
                                                syncCtx,
                                                if (ok) "\uD83D\uDCBB Desktop login approved!"
                                                else "Could not approve desktop login — scan a fresh QR code",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                                gMatch != null -> {
                                    showDevices = false
                                    com.sangam.messenger.MainActivity.pendingGroup = gMatch.groupValues[1]
                                }
                                uMatch != null -> {
                                    showDevices = false
                                    com.sangam.messenger.MainActivity.pendingInvite = uMatch.groupValues[1]
                                }
                                raw.length >= 20 && !raw.contains(" ") -> {
                                    showDevices = false
                                    com.sangam.messenger.MainActivity.pendingGroup = raw
                                }
                            }
                        }) {
                            Text("Join / Open Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevices = false }) { Text("Close") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!vm.deleting) confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Column {
                    Text(
                        "This permanently erases your account, your profile and every " +
                            "message on the server. It cannot be undone, and friends lose " +
                            "the chats they had with you.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    vm.deleteError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAccount {
                            confirmDelete = false
                            onAccountDeleted()
                        }
                    },
                    enabled = !vm.deleting
                ) {
                    if (vm.deleting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                    }
                    Text("Delete my account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!vm.deleting) confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SangamMark(30)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Sangam",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search chats",
                            tint = if (searchOpen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        BadgedBox(badge = { if (vm.update != null) Badge() }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                    }
                    if (appLockDialog) {
        AppLockSetupDialog(onDismiss = { appLockDialog = false })
    }

    if (notifDialog) {
        NotificationSettingsDialog(onDismiss = { notifDialog = false })
    }

    if (pinDialogFor != null) {
        val chat = pinDialogFor!!
        AlertDialog(
            onDismissRequest = { pinDialogFor = null },
            title = { Text(chat.peerName) },
            text = {
                Text(
                    if (chat.peerId in pinnedIds) "Unpin this chat from the top of the list?"
                    else "Pin this chat to the top of your chats list?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Sangam.togglePinnedChat(chat.peerId)
                    pinnedIds = Sangam.pinnedChats()
                    pinDialogFor = null
                }) {
                    Text(if (chat.peerId in pinnedIds) "Unpin" else "Pin")
                }
            },
            dismissButton = {
                TextButton(onClick = { pinDialogFor = null }) { Text("Cancel") }
            }
        )
    }

    if (blockedDialog) {
        BlockedUsersDialog(
            onDismiss = { blockedDialog = false },
            onChanged = {
                pinnedIds = Sangam.pinnedChats()
                vm.refresh()
            }
        )
    }

    if (privacyDialog) {
        PrivacyDialog(onDismiss = { privacyDialog = false })
    }

    if (backupDialog) {
        BackupDialog(onDismiss = { backupDialog = false })
    }
    if (cloudDialog) CloudBackupDialog(onDismiss = { cloudDialog = false })

    if (legalOpen) LegalAndGrievanceDialog(onDismiss = { legalOpen = false })

    if (scanOpen) {
        val scanCtx = LocalContext.current
        QrScannerScreen(
            title = "Scan Desktop, Group, or Friend QR",
            onScanned = { text ->
                scanOpen = false
                val raw = text.trim()
                val dMatch = Regex("sangam://desktop\\?id=([A-Za-z0-9_\\-]+)(?:&k=([A-Za-z0-9_\\-]+))?").find(raw)
                val gMatch = Regex("sangam://group\\?id=([A-Za-z0-9\\-]+)").find(raw)
                val uMatch = Regex("sangam://add\\?u=([A-Za-z0-9_]+)").find(raw)
                when {
                    dMatch != null -> {
                        val codeId = dMatch.groupValues[1]
                        val dKey = dMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                        kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
                            val ok = Sangam.approveDesktopPairing(codeId, dKey)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    scanCtx,
                                    if (ok) "\uD83D\uDCBB Desktop login approved!"
                                    else "Could not approve desktop login — scan a fresh QR code on Windows",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    gMatch != null -> {
                        com.sangam.messenger.MainActivity.pendingGroup = gMatch.groupValues[1]
                    }
                    uMatch != null -> {
                        com.sangam.messenger.MainActivity.pendingInvite = uMatch.groupValues[1]
                    }
                    raw.length >= 20 && !raw.contains(" ") && !raw.startsWith("http") -> {
                        com.sangam.messenger.MainActivity.pendingGroup = raw
                    }
                    else -> {
                        scanResult = raw
                    }
                }
            },
            onClose = { scanOpen = false }
        )
    }
    scanResult?.let { text ->
        AlertDialog(
            onDismissRequest = { scanResult = null },
            containerColor = glassSurfaceColor(0.95f),
            title = { Text("QR Code Scanned", fontWeight = FontWeight.Bold) },
            text = { Text(text, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { scanResult = null }) { Text("Close") }
            }
        )
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            containerColor = glassSurfaceColor(0.94f),
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        GlassCard(cornerRadius = 18) {
                            Column {
                                GlassSettingsRow(
                                    icon = Icons.Filled.PhotoCamera,
                                    tint = SangamTeal,
                                    title = if (hdPhotos) "HD photos: On" else "HD photos: Off",
                                    subtitle = "Tap to switch default photo quality"
                                ) {
                                    hdPhotos = !hdPhotos
                                    Sangam.setHdMedia(hdPhotos)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                GlassSettingsRow(
                                    icon = Icons.Filled.Settings,
                                    tint = Color(0xFF7E57C2),
                                    title = "Theme",
                                    subtitle = "Auto, Light or Dark appearance"
                                ) {
                                    settingsOpen = false; themeDialog = true
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                GlassSettingsRow(
                                    icon = Icons.Filled.Notifications,
                                    tint = SangamOrange,
                                    title = "Notifications",
                                    subtitle = "Sound, vibration and message preview"
                                ) {
                                    settingsOpen = false; notifDialog = true
                                }
                            }
                        }
                    }
                    item {
                        GlassCard(cornerRadius = 18) {
                            Column {
                                GlassSettingsRow(
                                    icon = Icons.Filled.Lock,
                                    tint = Color(0xFF2E9E4F),
                                    title = "App lock",
                                    subtitle = "Passcode and fingerprint unlock"
                                ) {
                                    settingsOpen = false; appLockDialog = true
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                GlassSettingsRow(
                                    icon = Icons.Filled.Person,
                                    tint = SangamTeal,
                                    title = "Privacy",
                                    subtitle = "Read receipts and online status"
                                ) {
                                    settingsOpen = false; privacyDialog = true
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                GlassSettingsRow(
                                    icon = Icons.Filled.Wifi,
                                    tint = Color(0xFF5C6BC0),
                                    title = "Linked devices",
                                    subtitle = "Phones and tablets on your account"
                                ) {
                                    settingsOpen = false
                                    vm.loadDevices()
                                    showDevices = true
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                GlassSettingsRow(
                                    icon = Icons.Filled.Block,
                                    tint = MaterialTheme.colorScheme.error,
                                    title = "Blocked users",
                                    subtitle = "Manage people you have blocked"
                                ) {
                                    settingsOpen = false; blockedDialog = true
                                }
                            }
                        }
                    }
                    item {
                        GlassCard(cornerRadius = 18) {
                            Column {
                                GlassSettingsRow(
                                    icon = Icons.Filled.Refresh,
                                    tint = SangamTeal,
                                    title = "Cloud backup",
                                    subtitle = "Encrypted backup with recovery key"
                                ) {
                                    settingsOpen = false; cloudDialog = true
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                GlassSettingsRow(
                                    icon = Icons.Filled.Check,
                                    tint = Color(0xFF2E9E4F),
                                    title = "Backup & restore (file)",
                                    subtitle = "Export or view an encrypted backup file"
                                ) {
                                    settingsOpen = false; backupDialog = true
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                GlassSettingsRow(
                                    icon = Icons.Filled.NewReleases,
                                    tint = SangamOrange,
                                    title = "Privacy, terms & grievance",
                                    subtitle = "DPDP rights and Grievance Officer contact"
                                ) {
                                    settingsOpen = false; legalOpen = true
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                )
                                GlassSettingsRow(
                                    icon = Icons.Filled.Search,
                                    tint = SangamTeal,
                                    title = "Scan a QR code (Windows / Group / Friend)",
                                    subtitle = "Scan Windows App QR to log in on PC, or join a group"
                                ) {
                                    settingsOpen = false; scanOpen = true
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) { Text("Close") }
            }
        )
    }

    if (themeDialog) {
                        AlertDialog(
                            onDismissRequest = { themeDialog = false },
                            title = { Text("Theme") },
                            text = {
                                Column {
                                    listOf(
                                        "auto" to "Auto (device theme)",
                                        "light" to "Light",
                                        "dark" to "Dark"
                                    ).forEach { (mode, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    Ui.themeMode = mode
                                                    Sangam.setThemeMode(mode)
                                                    themeDialog = false
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = Ui.themeMode == mode,
                                                onClick = null
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(label)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { themeDialog = false }) { Text("Close") }
                            }
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        vm.myProfile?.let { me ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        menuOpen = false
                                        onOpenProfile()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(me.display_name, me.avatar_url, size = 40)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        me.display_name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "@" + me.username,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Edit profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("New group") },
                            leadingIcon = {
                                Icon(Icons.Filled.Group, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onNewGroup()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Link Windows / Devices") },
                            leadingIcon = {
                                Icon(Icons.Filled.Devices, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                vm.loadDevices()
                                showDevices = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Scan QR code") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                scanOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("What's new") },
                            leadingIcon = {
                                Icon(Icons.Filled.NewReleases, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onOpenWhatsNew()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                settingsOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Log out") },
                            onClick = {
                                menuOpen = false
                                onSignOut()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Delete account", color = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = glassSurfaceColor(0.82f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = "New chat",
                    tint = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            SangamTeal.copy(alpha = 0.12f),
                            Color.Transparent,
                            SangamOrange.copy(alpha = 0.07f)
                        )
                    )
                )
                .padding(padding)
        ) {
            // Sangam Confluence Switcher: floating frosted glass pill (Messages · Calls · Nearby)
            val totalUnread = vm.chats.sumOf { it.unread }
            ConfluenceSwitcher(
                selectedTab = homeTab,
                unreadCount = totalUnread,
                onSelectTab = { tab ->
                    if (tab == "nearby") onOpenNearby()
                    else homeTab = tab
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            vm.update?.let { UpdateBanner(vm) }

            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(if (homeTab == "calls") "Search friends to call" else "Search chats")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (homeTab == "calls") {
                val friendChats = vm.chats.filter {
                    !it.isGroup && (query.isBlank() ||
                        it.peerName.contains(query.trim(), ignoreCase = true) ||
                        it.peerUsername.contains(query.trim(), ignoreCase = true))
                }
                CallsHubView(
                    friends = friendChats,
                    loadCalls = { Sangam.allCalls() },
                    myId = { Sangam.userId() ?: "" },
                    onStartCall = { peerId, peerName, video ->
                        startCallFromTab(peerId, peerName, video)
                    },
                    onFindFriend = onNewChat
                )
            } else if (!vm.initialLoaded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                ) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            } else if (vm.chats.isEmpty()) {
                EmptyChats(onNewChat = onNewChat)
            } else {
                val pinned = pinnedIds
                val visible = if (query.isBlank()) {
                    vm.chats.sortedWith(
                        compareByDescending<ChatSummary> { it.peerId in pinned }
                            .thenByDescending { it.lastAt }
                    )
                } else {
                    val q = query.trim()
                    vm.chats.filter {
                        it.peerName.contains(q, ignoreCase = true) ||
                            it.peerUsername.contains(q, ignoreCase = true)
                    }
                }
                // Messages matching the search, across every chat.
                val msgResults = if (query.isBlank()) emptyList() else {
                    val q = query.trim()
                    vm.messagesByPeer.flatMap { (peerId, msgs) ->
                        msgs.filter {
                            (it.type == "t" || it.type == "d") &&
                                it.text?.contains(q, ignoreCase = true) == true
                        }.take(3).map { peerId to it }
                    }.sortedByDescending { it.second.createdAt }.take(20)
                }
                if (visible.isEmpty() && msgResults.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        Text(
                            "No chats match your search",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { "chat_" + it.peerId }) { chat ->
                        Box(Modifier.animateItem()) {
                            ChatRow(
                                chat,
                                pinned = chat.peerId in pinned,
                                groupBitmap = if (chat.isGroup)
                                    vm.groupAvatars[chat.peerId] else null,
                                onLongClick = { pinDialogFor = chat }
                            ) {
                                if (chat.isGroup) onOpenGroup(chat.peerId, chat.peerName)
                                else onOpenChat(chat.peerId, chat.peerName, null)
                            }
                        }
                    }
                    if (msgResults.isNotEmpty()) {
                        item(key = "msg_header") {
                            Text(
                                "Messages",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    start = 16.dp, top = 14.dp, bottom = 6.dp
                                )
                            )
                        }
                        items(msgResults, key = { "msg_" + it.second.id }) { (peerId, m) ->
                            val chatRow = vm.chats.firstOrNull { it.peerId == peerId }
                            val name = chatRow?.peerName ?: "Chat"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (chatRow?.isGroup == true) onOpenGroup(peerId, name)
                                        else onOpenChat(peerId, name, m.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        m.text ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    formatTime(m.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
                }
            }
        }
    }

    if (callOpen) {
        CallScreen(
            peerName = callPeerName,
            incoming = false,
            active = callActive,
            callId = callId,
            isVideo = callIsVideo,
            onAnswer = {},
            onDecline = {
                callId?.let { id -> callScope.launch { Sangam.endCall(id, true) } }
                callOpen = false
            },
            onHangUp = {
                callId?.let { id -> callScope.launch { Sangam.endCall(id, false) } }
                callOpen = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: ChatSummary,
    pinned: Boolean,
    groupBitmap: android.graphics.Bitmap?,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val draft = Sangam.draftFor(chat.peerId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            chat.peerName,
            chat.avatarUrl,
            bitmap = groupBitmap
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.online) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    chat.peerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = if (chat.typing) "typing…" else when {
                    draft.isNotBlank() -> "Draft: $draft"
                    chat.lastPreview.isBlank() -> "Photo"
                    else -> chat.lastPreview
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    chat.typing -> MaterialTheme.colorScheme.primary
                    draft.isNotBlank() && !chat.typing -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(
                formatTime(chat.lastAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (chat.unread > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(SangamOrange, CircleShape)
                        .size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (chat.unread > 99) "99+" else chat.unread.toString(),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/** Manage everyone blocked on this phone. */
@Composable
private fun BlockedUsersDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    var list by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Sangam.blockedList()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked users", fontWeight = FontWeight.SemiBold) },
        text = {
            if (list.isEmpty()) {
                Text("No one is blocked.")
            } else {
                Column {
                    list.forEach { (id, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                name,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.TextButton(onClick = {
                                Sangam.unblockPeer(id)
                                list = Sangam.blockedList()
                                onChanged()
                            }) { Text("Unblock") }
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun EmptyChats(onNewChat: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SangamMark(90)
        Spacer(Modifier.height(16.dp))
        Text(
            "No chats yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Start a conversation — find a friend below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.OutlinedButton(onClick = onNewChat) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Find a friend")
        }
    }
}


@Composable
private fun UpdateBanner(vm: ChatsViewModel) {
    val upd = vm.update ?: return
    if (vm.dismissedUpdate == upd.version) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "\u2728 Sangam ${upd.version} is ready",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { vm.dismissUpdate() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Later",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            when (vm.updateStatus) {
                "downloading" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        "Downloading update\u2026",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                "ready" -> TextButton(onClick = { vm.installUpdate() }) {
                    Text("Install now \u2713")
                }
                "error" -> TextButton(onClick = { vm.downloadUpdate() }) {
                    Text("Download failed \u2014 tap to retry")
                }
                else -> TextButton(onClick = { vm.downloadUpdate() }) {
                    Text("Download & update")
                }
            }
        }
    }
}

/** Global notification options (sound, vibration, privacy). */
@Composable
fun NotificationSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(com.sangam.messenger.data.Sangam.notifSoundOn()) }
    var vibrate by remember { mutableStateOf(com.sangam.messenger.data.Sangam.notifVibrateOn()) }
    var showText by remember { mutableStateOf(com.sangam.messenger.data.Sangam.notifShowText()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifications") },
        text = {
            Column {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sound", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        Text(
                            "Play a sound for new messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = sound, onCheckedChange = {
                        sound = it
                        com.sangam.messenger.data.Sangam.setNotifSound(it)
                        com.sangam.messenger.push.Push.applyNotificationSettings(context)
                    })
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Vibration", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        Text(
                            "Vibrate for new messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vibrate, onCheckedChange = {
                        vibrate = it
                        com.sangam.messenger.data.Sangam.setNotifVibrate(it)
                        com.sangam.messenger.push.Push.applyNotificationSettings(context)
                    })
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show message text", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        Text(
                            "If off, notifications only say \"New message\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = showText, onCheckedChange = {
                        showText = it
                        com.sangam.messenger.data.Sangam.setNotifShowText(it)
                    })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/**
 * Sangam's signature floating Frosted Glass Confluence Switcher (Messages · Calls · Nearby).
 */
@Composable
fun ConfluenceSwitcher(
    selectedTab: String,
    unreadCount: Int,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPill(modifier = modifier, alpha = 0.86f) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                "messages" to "Messages",
                "calls" to "Calls",
                "nearby" to "Nearby"
            )
            tabs.forEach { (id, title) ->
                val active = selectedTab == id
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (active) SangamTeal else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onSelectTab(id) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (id == "messages" && unreadCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (active) Color.White else SangamOrange,
                                        CircleShape
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) SangamTeal else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Sangam Calls Hub: Quick Friend Dial (Voice & Video) + Recent Call Timeline.
 */
@Composable
fun CallsHubView(
    friends: List<com.sangam.messenger.data.ChatSummary>,
    loadCalls: suspend () -> List<com.sangam.messenger.data.CallRow>,
    myId: () -> String,
    onStartCall: (peerId: String, peerName: String, video: Boolean) -> Unit,
    onFindFriend: () -> Unit
) {
    var calls by remember { mutableStateOf<List<com.sangam.messenger.data.CallRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var callFilter by remember { mutableStateOf("all") }
    LaunchedEffect(Unit) {
        calls = runCatching { loadCalls() }.getOrDefault(emptyList())
        loading = false
    }
    val friendById = remember(friends) { friends.associateBy { it.peerId } }
    val meId = myId()
    val filteredCalls = remember(calls, callFilter, meId) {
        when (callFilter) {
            "missed" -> calls.filter { (it.status == "declined" || it.status == "ringing") && it.caller != meId }
            "video" -> calls.filter { it.kind == "video" }
            else -> calls
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "confluence_banner") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SangamMark(36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Sangam Confluence Lounge",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "End-to-end encrypted voice & video calls over any network",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item(key = "quick_connect_header") {
            Text(
                "CONFLUENCE ORBIT · QUICK DIAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
        }
        item(key = "quick_connect_card") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20
            ) {
                if (friends.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No friends in your list yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Find a friend first to start a voice or video call.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = onFindFriend) {
                            Text("Find a friend")
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        friends.forEachIndexed { idx, f ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InitialAvatar(f.peerName, size = 40)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (f.online) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .background(Color(0xFF4CAF50), CircleShape)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            f.peerName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (f.peerUsername.isNotBlank()) {
                                        Text(
                                            "@" + f.peerUsername,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SangamTeal.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onStartCall(f.peerId, f.peerName, false) }
                                ) {
                                    Text(
                                        "Voice",
                                        color = SangamTeal,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SangamOrange.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onStartCall(f.peerId, f.peerName, true) }
                                ) {
                                    Text(
                                        "Video",
                                        color = SangamOrange,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "recent_calls_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CALLPULSE LOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                listOf("all" to "All", "missed" to "Missed", "video" to "Video").forEach { (id, label) ->
                    val sel = callFilter == id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (sel) SangamTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { callFilter = id }
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        item(key = "recent_calls_card") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20
            ) {
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Loading recent calls…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (filteredCalls.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (callFilter == "all")
                                "No call history yet — tap Voice or Video above to call a friend."
                            else "No " + callFilter + " calls in your log.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val me = myId()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        filteredCalls.forEachIndexed { idx, c ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                                )
                            }
                            val outgoing = c.caller == me
                            val otherId = if (outgoing) c.callee else c.caller
                            val friend = friendById[otherId]
                            val peerName = friend?.peerName ?: "Sangam friend"
                            val missed = c.status == "declined" || c.status == "ringing"
                            val kindTag = if (c.kind == "video") "Video" else "Voice"
                            val dirLabel = when {
                                missed && !outgoing -> "Missed " + kindTag.lowercase() + " call"
                                missed -> "Unanswered " + kindTag.lowercase() + " call"
                                outgoing -> "Outgoing " + kindTag.lowercase() + " call"
                                else -> "Incoming " + kindTag.lowercase() + " call"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStartCall(otherId, peerName, c.kind == "video") }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InitialAvatar(peerName, size = 38)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        peerName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (missed && !outgoing) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        dirLabel + "  •  " + (c.createdAt ?: "").replace("T", " ").take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onStartCall(otherId, peerName, c.kind == "video") }
                                ) {
                                    Text(
                                        kindTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom_space") {
            Spacer(Modifier.height(88.dp))
        }
    }
}

/**
 * Call history for one person. Loads its own data through [load] so this file
 * stays free of Supabase imports, and takes [myId] so it can tell an outgoing
 * call from an incoming one.
 */
@Composable
fun CallHistoryDialog(
    peerName: String,
    load: suspend () -> List<com.sangam.messenger.data.CallRow>,
    myId: () -> String,
    onDismiss: () -> Unit
) {
    var rows by remember { mutableStateOf<List<com.sangam.messenger.data.CallRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = runCatching { load() }.getOrDefault(emptyList())
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calls with " + peerName, fontWeight = FontWeight.SemiBold) },
        text = {
            if (loading) {
                Text("Loading…", style = MaterialTheme.typography.bodySmall)
            } else if (rows.isEmpty()) {
                Text(
                    "No calls yet. Open the Calls tab on the home screen to start a call.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn {
                    items(rows.size) { i ->
                        val c = rows[i]
                        val outgoing = c.caller == myId()
                        val missed = c.status == "declined" || c.status == "ringing"
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Text(
                                when {
                                    missed && !outgoing -> "Missed call"
                                    missed -> "Declined"
                                    outgoing -> "Outgoing call"
                                    else -> "Incoming call"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (missed && !outgoing) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                (c.createdAt ?: "").replace("T", " ").take(16),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
