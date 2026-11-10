package com.sangam.messenger.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.sangam.messenger.ui.theme.SangamOrange
import com.sangam.messenger.ui.theme.SangamGreen
import com.sangam.messenger.ui.theme.SangamTeal
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sangam.messenger.data.BackupConversation
import com.sangam.messenger.data.Models.Profile
import com.sangam.messenger.data.Sangam
import com.sangam.messenger.vm.BackupViewModel
import kotlinx.coroutines.launch

/**
 * Extra screens and dialogs introduced in v1.8.0: friend picker, friend
 * profile, message editing, the share-to-Sangam flow, privacy options and
 * the encrypted chat backup.
 */

/** List of Sangam friends to pick from (used by Forward and Share). */
@Composable
fun FriendPickerDialog(
    title: String,
    onPick: (Profile) -> Unit,
    onDismiss: () -> Unit
) {
    var profiles by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    LaunchedEffect(Unit) {
        runCatching { Sangam.allProfiles() }.onSuccess { profiles = it }
    }
    val me = Sangam.userId()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            val list = profiles.values
                .filter { it.id != me }
                .sortedBy { it.display_name.lowercase() }
            if (list.isEmpty()) {
                Text(
                    "No friends found yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(list) { profile ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(profile) }
                                .padding(vertical = 8.dp)
                        ) {
                            Avatar(profile.display_name, profile.avatar_url, 40)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    profile.display_name,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "@" + profile.username,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Full profile of the friend whose chat is open — Sangam Confluence Identity Card. */
@Composable
fun FriendProfileDialog(
    profile: Profile?,
    onVoiceCall: () -> Unit = {},
    onVideoCall: () -> Unit = {},
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmReport by remember { mutableStateOf(false) }
    var showContactQr by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassSurfaceColor(0.95f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                GlassPill {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SangamMark(18)
                        Text(
                            "SANGAM CONFLUENCE IDENTITY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (profile == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp), strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Loading…")
                } else {
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .background(SangamTeal.copy(alpha = 0.14f), CircleShape)
                            .padding(7.dp)
                            .background(SangamOrange.copy(alpha = 0.20f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Avatar(profile.display_name, profile.avatar_url, 92)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        profile.display_name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SangamTeal.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "@" + profile.username,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SangamTeal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (!profile.status.isNullOrBlank()) profile.status
                                else "Where friends and family meet · End-to-End Encrypted",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SangamTeal.copy(alpha = 0.16f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onVoiceCall() }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("\uD83D\uDCDE", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Voice",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SangamTeal
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SangamGreen.copy(alpha = 0.18f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onVideoCall() }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("\uD83C\uDFA5", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Video",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SangamGreen
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SangamOrange.copy(alpha = 0.18f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showContactQr = !showContactQr }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("\uD83D\uDD33", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (showContactQr) "Hide QR" else "QR Card",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SangamOrange
                                )
                            }
                        }
                    }
                    if (showContactQr) {
                        Spacer(Modifier.height(10.dp))
                        SangamQrBox(
                            data = "sangam://add?u=" + profile.username,
                            subtitle = "Scan to connect with @" + profile.username,
                            sizeDp = 150
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "\uD83D\uDEE1\uFE0F  Report " + profile.display_name,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { confirmReport = true }
                                    .padding(vertical = 8.dp)
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            )
                            Text(
                                "\uD83D\uDEAB  Block " + profile.display_name,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { confirmBlock = true }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.Bold) } }
    )
    if (confirmReport && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmReport = false },
            title = {
                Text(
                    "Report " + profile.display_name + "?",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "Send this account to the Sangam team for review. " +
                        "They are not told who reported them."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = profile.id
                    confirmReport = false
                    kotlinx.coroutines.MainScope().launch {
                        Sangam.reportAbuse("user", id)
                        android.widget.Toast.makeText(
                            ctx, "Report sent - thank you", android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }) { Text("Report") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReport = false }) { Text("Cancel") }
            }
        )
    }
    if (confirmBlock && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block " + profile.display_name + "?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Their chats will be hidden and you will not get notifications " +
                        "from them. You can unblock anytime from the ⋮ menu on the chats screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    onBlock()
                }) { Text("Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) { Text("Cancel") }
            }
        )
    }
}

/** Edit one of my sent text messages. */
@Composable
fun EditMessageDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message", fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onSave(text) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Pick a friend to send a shared photo to (Android Share button). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(sharedPath: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var profiles by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }

    LaunchedEffect(Unit) {
        runCatching { Sangam.allProfiles() }.onSuccess { profiles = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = { Text("Send to", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (sending) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Sending…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            val me = Sangam.userId()
            val list = profiles.values
                .filter { it.id != me }
                .sortedBy { it.display_name.lowercase() }
            if (list.isEmpty() && !sending) {
                Text(
                    "No friends found yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn {
                items(list) { profile ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !sending) {
                                sending = true
                                scope.launch {
                                    runCatching {
                                        val bytes = java.io.File(sharedPath).readBytes()
                                        Sangam.sendImage(profile.id, bytes).getOrThrow()
                                    }.fold(
                                        onSuccess = {
                                            Toast.makeText(
                                                context,
                                                "Sent to " + profile.display_name,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onDone()
                                        },
                                        onFailure = { e ->
                                            error = "Could not send: ${e.message}"
                                            sending = false
                                        }
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Avatar(profile.display_name, profile.avatar_url, 48)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                profile.display_name,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "@" + profile.username,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Read receipts and last seen options. */
@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    var receipts by remember { mutableStateOf(Sangam.readReceiptsOn()) }
    var lastSeen by remember { mutableStateOf(Sangam.lastSeenShareOn()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Read receipts",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Friends see when you have read their messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = receipts,
                        onCheckedChange = {
                            receipts = it
                            Sangam.setReadReceipts(it)
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Share online / last seen",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Friends see when you were last online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lastSeen,
                        onCheckedChange = {
                            lastSeen = it
                            Sangam.setLastSeenShare(it)
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** Export chats to an encrypted file, or view an old backup. */
@Composable
fun BackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bvm: BackupViewModel = viewModel()
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var archive by remember { mutableStateOf<List<BackupConversation>?>(null) }
    var openConv by remember { mutableStateOf<BackupConversation?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                note = null
                runCatching {
                    val data = bvm.export(password)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        ?: error("could not write the file")
                }.fold(
                    onSuccess = { note = "Backup saved ✓" },
                    onFailure = { note = "Could not back up: ${it.message}" }
                )
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                note = null
                runCatching {
                    val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("could not read the file")
                    bvm.parseBackup(data, importPassword)
                }.fold(
                    onSuccess = { archive = it },
                    onFailure = {
                        note = "Could not open the backup — wrong password or not a backup file"
                    }
                )
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { bvm.loadRecoveryKey() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (openConv != null) openConv!!.name
                else if (archive != null) "Backup contents"
                else "Backup & restore",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            val conv = openConv

            if (conv != null) {
                // One conversation's messages from the backup.
                LazyColumn {
                    items(conv.msgs) { m ->
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Text(
                                if (m.mine) "You" else conv.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                backupPreview(m.type, m.text),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else if (archive != null) {
                Column {
                    Text(
                        "${archive!!.size} conversation(s) in this backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(archive!!) { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openConv = c }
                                    .padding(vertical = 8.dp)
                            ) {
                                InitialAvatar(c.name, 40)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(c.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${c.msgs.size} messages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Save your chats to an encrypted file. Keep the password safe - it cannot be opened without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Backup password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password2,
                        onValueChange = { password2 = it },
                        label = { Text("Repeat password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = password2.isNotEmpty() && password2 != password
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { exportLauncher.launch("sangam-backup.sangam") },
                        enabled = !busy && password.length >= 4 && password == password2
                    ) {
                        Text("Create backup file")
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "View a backup",
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("Backup password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !busy && importPassword.length >= 4
                    ) {
                        Text("Choose backup file")
                    }
                    note?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                if (openConv != null) openConv = null
                else if (archive != null) { archive = null; note = null }
                else onDismiss()
            }) { Text(if (openConv != null || archive != null) "Back" else "Close") }
        }
    )
}

private fun backupPreview(type: String, text: String?): String = when (type) {
    "a" -> "🎤 Voice message"
    "i" -> "Photo"
    "d" -> "📄 " + (text ?: "Document")
    "v" -> "🎬 Video"
    "c" -> "🕰️ Time capsule"
    else -> text ?: ""
}

/** In-app encrypted video player: decrypts to cache and plays full-screen. */
@Composable
fun VideoPlayerDialog(
    path: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null
) {
    var failed by remember { mutableStateOf(false) }
    var videoView by remember { mutableStateOf<android.widget.VideoView?>(null) }

    DisposableEffect(Unit) {
        onDispose { videoView?.stopPlayback() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (failed) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Could not play this video", color = Color.White)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(path)
                            setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            val controller = android.widget.MediaController(ctx)
                            controller.setMediaPlayer(this)
                            setMediaController(controller)
                            setOnPreparedListener { start() }
                        }.also { videoView = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Top bar with close and save
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(top = 28.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (onSave != null) {
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "Save to Downloads",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * v2.8.1: the cloud backup lives in its own dialog. It used to be squeezed
 * into BackupDialog and drew on top of the file section.
 */
@Composable
fun CloudBackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bvm: BackupViewModel = viewModel()
    var note by remember { mutableStateOf<String?>(null) }
    var typedKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { bvm.loadRecoveryKey() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud backup", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (bvm.recoveryKey.isBlank()) {
                    // A fresh install has no key of its own. Showing a new one
                    // here would be a lie: it opens nothing.
                    Text(
                        "No backup has been made from this phone yet.",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Moving from another phone? Type that phone's recovery key " +
                            "below and tap \"Restore my chats\".",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Your recovery key",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        bvm.recoveryKey,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Write this down. It is the only way to open your backup " +
                            "on a new phone - nobody else can.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        val cm = context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Sangam recovery key", bvm.recoveryKey
                            )
                        )
                        note = "Recovery key copied"
                    }) { Text("Copy") }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    TextButton(
                        enabled = !bvm.busy,
                        onClick = { bvm.backUpToCloud { note = it } }
                    ) { Text("Back up now") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typedKey,
                    onValueChange = { typedKey = it },
                    label = { Text("Recovery key, to restore") },
                    placeholder = { Text("AbCd-EfGh-IjKl-...") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(
                        enabled = !bvm.busy && typedKey.isNotBlank(),
                        onClick = { bvm.restoreFromCloud(typedKey) { note = it } }
                    ) { Text("Restore my chats") }
                }
                (bvm.cloudNote ?: note)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Privacy policy, terms and the grievance officer - needed for Play Store. */
@Composable
fun LegalAndGrievanceDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy, terms & grievance", fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn {
                item {
                    Text("What Sangam stores", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your messages are encrypted on your phone before they " +
                            "are sent. The server holds only unreadable text and " +
                            "encrypted files. It also holds your email, your " +
                            "username, your display name and your push token.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Text("What we never do", fontWeight = FontWeight.SemiBold)
                    Text(
                        "We do not read your messages, sell your data, or share " +
                            "it with advertisers. There is no tracking and no " +
                            "analytics in this app.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Text("Your rights", fontWeight = FontWeight.SemiBold)
                    Text(
                        "You can delete your account at any time from the menu. " +
                            "Deleting removes your profile, your messages and your " +
                            "media. You can export a copy of your chats from " +
                            "Backup & restore.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Text("Reporting content", fontWeight = FontWeight.SemiBold)
                    Text(
                        "To report a message, a group or a person, long-press the " +
                            "message and choose Report, or use Report on their " +
                            "profile. Reports are reviewed within 24 hours.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Text("Grievance officer", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Sekelan\nGrievance Officer, Sangam\n" +
                            "sekelan2005@gmail.com\n" +
                            "Complaints are acknowledged within 24 hours and " +
                            "resolved within 15 days, as required by the IT Rules, " +
                            "2021 and the DPDP Act.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
