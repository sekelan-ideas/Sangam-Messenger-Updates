package com.sangam.messenger.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sangam.messenger.data.Models.Profile
import com.sangam.messenger.data.Sangam
import com.sangam.messenger.ui.theme.SangamOrange
import com.sangam.messenger.vm.NewChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    prefill: String? = null,
    onOpenChat: (String) -> Unit,
    onBack: () -> Unit
) {
    val vm: NewChatViewModel = viewModel()
    val context = LocalContext.current
    var myUsername by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefill) {
        if (!prefill.isNullOrBlank()) {
            vm.query = prefill.trim().removePrefix("@")
            vm.search()
        }
    }
    LaunchedEffect(Unit) {
        myUsername = Sangam.myProfile()?.username
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("New chat", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = {
                    vm.query = it
                    vm.search()
                },
                placeholder = { Text("Search by name or @username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.padding(12.dp))

            // Invite row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                if (myUsername == null) {
                                    "Hi! I'm using Sangam — a simple, private messenger " +
                                        "for friends and family.\n\n" +
                                        "Download the app: https://github.com/sekelan-ideas/" +
                                        "Sangam-releases/releases/latest/download/Sangam.apk\n\n" +
                                        "Then ask me for my username and find me there."
                                } else {
                                    "Join me on Sangam 🌊 — a simple, private messenger " +
                                        "for friends and family.\n\n" +
                                        "1) Download the app here:\n" +
                                        "https://github.com/sekelan-ideas/Sangam-releases/" +
                                        "releases/latest/download/Sangam.apk\n\n" +
                                        "2) Install it, then open this link to add me:\n" +
                                        "sangam://add?u=${myUsername}\n\n" +
                                        "My username: @${myUsername}"
                                }
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Invite friends"))
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        tint = SangamOrange,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("Invite friends to Sangam", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Share a friendly note with anyone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            if (vm.query.trim().length >= 2) {
                if (vm.searching) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Searching...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (vm.results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No one found. Invite them!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn {
                        items(vm.results, key = { it.id }) { profile ->
                            if (!Sangam.isBlocked(profile.id)) {
                                ProfileRow(profile) { onOpenChat(profile.id) }
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Type a friend's name or username",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: Profile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile.display_name, profile.avatar_url)
        Spacer(Modifier.size(14.dp))
        Column {
            Text(profile.display_name, fontWeight = FontWeight.SemiBold)
            Text(
                "@" + profile.username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.PersonAddAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
