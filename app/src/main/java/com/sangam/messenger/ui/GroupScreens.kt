package com.sangam.messenger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.sangam.messenger.ui.theme.SangamGreen
import com.sangam.messenger.ui.theme.SangamOrange
import com.sangam.messenger.ui.theme.SangamTeal
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sangam.messenger.data.Models.Profile
import com.sangam.messenger.data.Sangam
import com.sangam.messenger.vm.ChatViewModel
import com.sangam.messenger.vm.NewGroupViewModel

/** Group info: rename, description, members, add/remove, leave. */
@Composable
fun GroupInfoDialog(
    vm: ChatViewModel,
    onDismiss: () -> Unit,
    onLeft: () -> Unit,
    onDeleted: () -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var editingDesc by remember { mutableStateOf(false) }
    var descDraft by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val g = vm.group
    val me = remember { Sangam.userId() }
    val isAdmin = g?.createdBy == me
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) vm.setGroupPhoto(context, uri) }

    if (adding) {
        AddMembersDialog(vm = vm, onDismiss = { adding = false })
    }

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
                            "SANGAM CONFLUENCE CIRCLE",
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
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .background(SangamTeal.copy(alpha = 0.14f), CircleShape)
                            .padding(6.dp)
                            .background(SangamGreen.copy(alpha = 0.20f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (vm.groupPhotoBitmap != null) {
                            Image(
                                bitmap = vm.groupPhotoBitmap!!.asImageBitmap(),
                                contentDescription = "Group photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            InitialAvatar(g?.name ?: "Group", 84)
                        }
                    }
                    if (isAdmin) {
                        TextButton(onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }) { Text("Change circle photo", style = MaterialTheme.typography.labelMedium) }
                    }
                    if (editingName) {
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            singleLine = true,
                            label = { Text("Group name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { editingName = false }) { Text("Cancel") }
                            TextButton(onClick = {
                                val n = nameDraft.trim()
                                if (n.isNotEmpty()) {
                                    vm.renameGroup(n)
                                    editingName = false
                                }
                            }) { Text("Save") }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                g?.name ?: "Group",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (isAdmin) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SangamTeal.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            nameDraft = g?.name ?: ""
                                            editingName = true
                                        }
                                ) {
                                    Text(
                                        "Rename",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SangamTeal,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val creator = g?.createdBy
                    val creatorName = creator?.let { vm.groupMembers[it]?.display_name }
                        ?: if (creator == me) "you" else "a member"
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "${vm.groupRoster.size} Members · Created by " + creatorName +
                                (g?.createdAt?.take(10)?.let { " · " + it } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (editingDesc) {
                            OutlinedTextField(
                                value = descDraft,
                                onValueChange = { descDraft = it },
                                label = { Text("Circle description") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingDesc = false }) { Text("Cancel") }
                                TextButton(onClick = {
                                    vm.setGroupDescription(descDraft)
                                    editingDesc = false
                                }) { Text("Save") }
                            }
                        } else {
                            Text(
                                "ABOUT THIS CIRCLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                g?.description?.takeIf { it.isNotBlank() }
                                    ?: "Tap to add a group description…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        descDraft = g?.description ?: ""
                                        editingDesc = true
                                    }
                            )
                        }
                    }
                }
                if (g?.membersCanAdd != false || isAdmin) {
                    var showGroupQr by remember { mutableStateOf(false) }
                    val groupLink = "sangam://group?id=" + (g?.id ?: "")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "INVITE BY QR OR LINK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SangamTeal.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showGroupQr = !showGroupQr }
                                ) {
                                    Text(
                                        if (showGroupQr) "Hide QR" else "\uD83D\uDD33 QR Code",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SangamTeal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SangamGreen.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            runCatching {
                                                val send = android.content.Intent(
                                                    android.content.Intent.ACTION_SEND
                                                ).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        android.content.Intent.EXTRA_TEXT,
                                                        "Join me on Sangam: " + groupLink
                                                    )
                                                }
                                                context.startActivity(
                                                    android.content.Intent.createChooser(send, "Share group link")
                                                )
                                            }
                                        }
                                ) {
                                    Text(
                                        "\uD83D\uDCE4 Share",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SangamGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SangamOrange.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            runCatching {
                                                val cm = context.getSystemService(
                                                    android.content.ClipboardManager::class.java
                                                )
                                                cm?.setPrimaryClip(
                                                    android.content.ClipData.newPlainText("Sangam group link", groupLink)
                                                )
                                                android.widget.Toast.makeText(
                                                    context, "Group link copied", android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                ) {
                                    Text(
                                        "\uD83D\uDCCB Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SangamOrange,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            if (showGroupQr) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SangamQrBox(
                                        data = groupLink,
                                        subtitle = "Scan with Sangam camera or share link in chat to join " + (g?.name ?: "group"),
                                        sizeDp = 160
                                    )
                                }
                            }
                        }
                    }
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CIRCLE MEMBERS (${vm.groupRoster.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (g?.membersCanAdd != false || isAdmin) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SangamTeal.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { adding = true }
                                ) {
                                    Text(
                                        "+ Add Friends",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SangamTeal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val iCanManage = isAdmin || (me != null && me in vm.groupAdminIds)
                        vm.groupRoster.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InitialAvatar(p.name, 36)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Medium)
                                    if (p.role.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (p.leader) SangamOrange.copy(alpha = 0.18f)
                                                    else SangamTeal.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                p.role,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (p.leader) SangamOrange else SangamTeal,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (isAdmin && !p.leader && p.id != me) {
                                    TextButton(onClick = { vm.setCoLeader(p.id, !p.coLeader) }) {
                                        Text(
                                            if (p.coLeader) "Demote" else "Co-leader",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (iCanManage && !p.leader && p.id != me) {
                                    IconButton(onClick = { vm.removeGroupMember(p.id) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }
                }
                if (isAdmin) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "CIRCLE PERMISSIONS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = g?.membersCanAdd != false,
                                    onCheckedChange = { vm.setMembersCanAdd(it) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("Members can add people", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (g?.membersCanAdd != false)
                                            "Anyone in this circle can invite friends"
                                        else "Only leaders can add members",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = g?.leadersOnly == true,
                                    onCheckedChange = { vm.setLeadersOnly(it) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("Only leaders can post", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (g?.leadersOnly == true)
                                            "Announcement mode · only leaders post"
                                        else "Everyone can send messages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = {
                                if (confirmDelete) vm.deleteGroup(onDeleted) else confirmDelete = true
                            }) {
                                Text(
                                    if (confirmDelete) "Tap again to delete circle" else "Delete group",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.Bold) } },
        dismissButton = {
            TextButton(onClick = {
                if (confirmLeave) {
                    vm.leaveGroup { onLeft() }
                } else {
                    confirmLeave = true
                }
            }) {
                Text(
                    if (confirmLeave) "Tap again to leave" else "Leave group",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/** Pick more friends to add to an existing group. */
@Composable
private fun AddMembersDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var all by remember { mutableStateOf<List<Profile>>(emptyList()) }
    LaunchedEffect(Unit) {
        val me = Sangam.userId()
        val existing = vm.groupMembers.keys + listOfNotNull(me)
        all = runCatching { Sangam.allProfiles().values.toList() }
            .getOrDefault(emptyList())
            .filter { it.id !in existing && !Sangam.isBlocked(it.id) }
            .sortedBy { it.display_name.lowercase() }
    }
    val shown = if (query.isBlank()) all else all.filter {
        it.display_name.contains(query, true) || it.username.contains(query, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add members") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search friends") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(shown, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked =
                                        if (p.id in picked) picked - p.id else picked + p.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = p.id in picked,
                                onCheckedChange = {
                                    picked =
                                        if (p.id in picked) picked - p.id else picked + p.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Avatar(p.display_name, p.avatar_url, 34)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(p.display_name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "@" + p.username,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = {
                    vm.addGroupMembers(picked.toList())
                    onDismiss()
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Create a new group: name, then pick friends. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(onOpenGroup: (String) -> Unit, onBack: () -> Unit) {
    val vm: NewGroupViewModel = viewModel()
    LaunchedEffect(Unit) { vm.loadFriends() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("New group", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = vm.groupName,
                onValueChange = { vm.groupName = it },
                placeholder = { Text("Group name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = vm.query,
                onValueChange = {
                    vm.query = it
                    vm.search()
                },
                placeholder = { Text("Add friends") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            if (vm.selected.isNotEmpty()) {
                Text(
                    vm.selected.size.toString() + " selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            vm.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(vm.results, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggle(p.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = p.id in vm.selected,
                            onCheckedChange = { vm.toggle(p.id) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Avatar(p.display_name, p.avatar_url, 36)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(p.display_name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "@" + p.username,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { vm.create { gid -> onOpenGroup(gid) } },
                enabled = !vm.creating && vm.groupName.isNotBlank() && vm.selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) { Text(if (vm.creating) "Creating…" else "Create group") }
        }
    }
}
