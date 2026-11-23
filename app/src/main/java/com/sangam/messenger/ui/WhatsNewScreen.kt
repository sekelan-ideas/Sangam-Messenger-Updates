package com.sangam.messenger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sangam.messenger.vm.UpdateViewModel
import com.sangam.messenger.BuildConfig

/** One release and the changes in it. */
data class ReleaseNotes(val version: String, val items: List<String>)

val releaseNotes: List<ReleaseNotes> = listOf(
    ReleaseNotes("2.14.2", listOf(
        "Signal Protocol v3 multi-device sessions across Android and Windows Desktop",
        "Fixed multi-message PreKeySignalMessage bursts and automatic identity-key rotation recovery across linked devices",
        "AES-256-GCM encrypted QR pairing and outbox sync with hardened Windows DPAPI key vault"
    )),
    ReleaseNotes("2.14.1", listOf(
        "Instant QR login & full message history sync with Sangam for Windows Desktop",
        "Fixed ISO 18004 QR Version 4 alignment & format bits for instant camera scanning",
        "Seamless multi-device message decryption across linked phones and Windows PC"
    )),
    ReleaseNotes("2.13.1", listOf(
        "Full-screen camera QR scanner with Sangam Confluence HUD to log in Sangam for Windows or join groups",
        "Upgraded Connect Devices hub & top menu shortcut with bidirectional Windows <-> Phone message sync"
    )),
    ReleaseNotes("2.13.0", listOf(
        "Sangam Desktop Split-Pane Workspace with navigation rail, live chat sidebar, and Signal Protocol chat & call pane",
        "Desktop QR Login: scan the QR on the Desktop login screen from your mobile Sangam app (or sign in with email & password)",
        "Phone UI remains 100% unchanged on mobile phones"
    )),
    ReleaseNotes("2.14.0", listOf(
        "True Signal Protocol multi-device messaging with per-install Device IDs and per-device prekey bundles",
        "Sender-side device-list encryption and receiver-side target-device filtering so secondary phones receive every message",
        "Linked devices screen now displays real Device IDs, registration numbers, and Signal Protocol key status"
    )),
    ReleaseNotes("2.12.2", listOf(
        "Redesigned Friend & Group profiles into Frosted Glass Sangam Confluence Identity & Circle cards",
        "One-scan Second Phone Login via QR code right from the Login screen and Linked Devices hub",
        "Camera QR scanner now automatically joins groups and opens friend invites on scan"
    )),
    ReleaseNotes("2.11.3", listOf(
        "Redesigned Sangam Confluence Call screen and CallPulse Lounge with All/Missed/Video filters",
        "Fixed notifications repeating old messages or links and added rich call & media cards",
        "Group QR codes, Device Link QR codes, and one-tap Join Group cards inside chat messages"
    )),
    ReleaseNotes("2.11.1", listOf(
        "Live two-way video calls with picture-in-picture camera preview, camera flip and camera off",
        "Faster 200ms voice call audio frames for lower call delay"
    )),
    ReleaseNotes("2.11.0", listOf(
        "New frosted glass Confluence switcher: Messages, Calls and Nearby in one tap",
        "Dedicated Calls hub with quick friend voice/video dial and recent call log",
        "Cleaner chat header and collapsible attachment pill above the message bar",
        "Live two-way video calls with picture-in-picture preview and camera flip"
    )),
    ReleaseNotes("2.10.5", listOf(
        "Call audio now flows in both directions",
        "Fixed long names and last-seen times wrapping or clipping in the chat top bar",
        "Compact single-line notice for older pre-reinstall messages",
        "The encryption note scrolls with the conversation"
    )),
    ReleaseNotes("2.10.4", listOf(
        "Aligned call audio chunk column names with the database schema"
    )),
    ReleaseNotes("2.10.3", listOf(
        "Live two-way voice audio on calls with Mute and Speakerphone controls",
        "Push notification wake-up when someone calls you while the app is closed",
        "Works across mobile data and Wi-Fi without needing an external TURN server"
    )),
    ReleaseNotes("2.10.2", listOf(
        "Incoming calls ring anywhere in the app with Answer and Decline buttons",
        "Frosted glass call screen with ringtone and ghost-call protection"
    )),
    ReleaseNotes("2.10.1", listOf(
        "Tap the phone icon in any 1-to-1 chat to start a voice call"
    )),
    ReleaseNotes("2.9.3", listOf(
        "Login and My profile screens now have the aurora glow and frosted glass cards",
        "New updates screen uses frosted glass release cards"
    )),
    ReleaseNotes("2.9.2", listOf(
        "Grouped frosted glass Settings sheet with icon badges and subtitles",
        "Chat screen has a frosted top bar, floating glass message bar and specular bubble borders"
    )),
    ReleaseNotes("2.9.1", listOf(
        "Chat list has the frosted glass look - aurora wash and glass top bar"
    )),
    ReleaseNotes("2.9.0", listOf(
        "New frosted glass look - aurora background and translucent top bar",
        "Glass cards, pills and grouped settings rows"
    )),
    ReleaseNotes("2.8.24", listOf(
        "Tapping a notification opens the right chat"
    )),
    ReleaseNotes("2.8.23", listOf(
        "Report a person from their profile"
    )),
    ReleaseNotes("2.8.22", listOf(
        "Sealing a photo now works - the button no longer lies",
        "Voice notes respect leaders-only groups",
        "Your profile and search survive a missing column",
        "Report a person from their profile"
    )),
    ReleaseNotes("2.8.21", listOf(
        "Report a message from the long-press menu"
    )),
    ReleaseNotes("2.8.20", listOf(
        "Sealed capsules and view-once photos cannot be quoted or forwarded",
        "New privacy, terms and grievance page in Settings"
    )),
    ReleaseNotes("2.8.19", listOf(
        "View-once photos stay on screen while you look",
        "The media gallery no longer shows view-once photos"
    )),
    ReleaseNotes("2.8.18", listOf(
        "Leaders-only groups block photos, video, files and capsules too"
    )),
    ReleaseNotes("2.8.17", listOf(
        "Members can add people when the group allows it"
    )),
    ReleaseNotes("2.8.16", listOf(
        "Profile pictures show on friend, share and group screens"
    )),
    ReleaseNotes("2.8.15", listOf(
        "Lock screen is opaque - your chats no longer show through it"
    )),
    ReleaseNotes("2.8.14", listOf(
        "App Lock keeps you where you were instead of jumping to the chat list"
    )),
    ReleaseNotes("2.8.13", listOf(
        "Sealing a photo or file no longer asks for text",
        "Group @mentions are readable on your own bubbles"
    )),
    ReleaseNotes("2.8.12", listOf(
        "Contact names survive a missing database column now",
        "Video and document replies show a proper quote"
    )),
    ReleaseNotes("2.8.11", listOf(
        "Log in with a username - the button no longer demands an @",
        "Removed the duplicate badge on the menu",
        "What's new shows the full release history"
    )),
    ReleaseNotes("2.8.10", listOf(
        "Nearby and New updates are icons in the top bar",
        "The button row above the chats is gone"
    )),
    ReleaseNotes("2.8.9", listOf(
        "New chat, New group and Nearby are buttons above your chats"
    )),
    ReleaseNotes("2.8.8", listOf(
        "Leaving a group now waits for the server before closing",
        "Smaller download - unused processor architectures removed"
    )),
    ReleaseNotes("2.8.7", listOf(
        "Sending works again after restoring a backup",
        "New accounts keep the name and username you chose",
        "Restore is much faster on large histories"
    )),
    ReleaseNotes("2.8.6", listOf(
        "Settings opens again - it was nested inside another dialog",
        "Online dots and typing indicators are back",
        "Linked devices shows the list"
    )),
    ReleaseNotes("2.8.5", listOf(
        "Short menu: New group, Nearby, Settings, Log out, Delete account",
        "New Settings screen holds the rest in one place"
    )),
    ReleaseNotes("2.8.4", listOf(
        "Contact names, avatars and statuses show again"
    )),
    ReleaseNotes("2.8.3", listOf(
        "A fresh install no longer shows a recovery key that opens nothing",
        "The key works typed with or without dashes",
        "Clearer wording when a restore finishes"
    )),
    ReleaseNotes("2.8.2", listOf(
        "Restore now asks for your recovery key - reinstalling works",
        "Type the key with or without dashes"
    )),
    ReleaseNotes("2.8.1", listOf(
        "Cloud backup now has its own screen - the overlapping text is gone"
    )),
    ReleaseNotes("2.8.0", listOf(
        "Chat backup with a recovery key - restore your chats on a new phone",
        "Back up now, or restore, from Backup & restore",
        "Your identity is restored too, so friends can message you straight away"
    )),
    ReleaseNotes("2.7.13", listOf(
        "Messages from a friend who reinstalled are readable again",
        "Stronger App Lock PIN hashing",
        "Much less background traffic and faster chat loading"
    )),
    ReleaseNotes("2.7.12", listOf(
        "Signing in by email link no longer logs you out after an hour",
        "Only the author of a message can edit it now",
        "The old password is cleared after you change it"
    )),
    ReleaseNotes("2.7.11", listOf(
        "Lost messages now say plainly that they cannot be recovered",
        "Editing a message in a group now works",
        "Group chat themes now save"
    )),
    ReleaseNotes("2.7.10", listOf(
        "Password reset links now open the Set a new password screen",
        "Email confirmation now takes you straight into your chats",
        "Fixed the save being cancelled while setting a new password"
    )),
    ReleaseNotes("2.7.9", listOf(
        "Signing out no longer deletes your message text",
        "Caches clear only when a different account signs in",
        "Chats open faster because the text is kept"
    )),
    ReleaseNotes("2.7.8", listOf(
        "Reset links now open Set a new password, reliably",
        "Username availability no longer says available for a taken name"
    )),
    ReleaseNotes("2.7.7", listOf(
        "Password reset now leads with the email link, not a code",
        "Plain-English errors instead of validation_failed",
        "Old errors no longer follow you between screens"
    )),
    ReleaseNotes("2.7.6", listOf(
        "Password reset links now open the Set a new password screen"
    )),
    ReleaseNotes("2.7.5", listOf(
        "Notifications now show who messaged and what they sent",
        "Fewer Encrypted message bubbles - the text cache is 5x bigger",
        "Change your @username, with available or taken shown as you type"
    )),
    ReleaseNotes("2.7.4", listOf(
        "Confirm your email with a 6-digit code inside the app - no link needed",
        "Password reset by code too, then set your new password in the app"
    )),
    ReleaseNotes("2.7.3", listOf(
        "Signing out now wipes the old account's chats from the phone",
        "Notifications show what arrived - photo, video, voice note or text",
        "Reset links open a Set a new password screen"
    )),
    ReleaseNotes("2.7.2", listOf(
        "Email confirmation now opens the app instead of localhost",
        "Confirming your email signs you straight in"
    )),
    ReleaseNotes("2.7.1", listOf(
        "Forgot password - get a reset link by email or username",
        "Show password button, and confirm password when signing up",
        "Clearer sign-in messages"
    )),
    ReleaseNotes("2.7.0", listOf(
        "New: Linked devices - see every phone and tablet on your account",
        "This is part 1 of multi-device; the shared-chat part comes next"
    )),
    ReleaseNotes("2.6.0", listOf(
        "View once now tells you when your friend has opened it",
        "Disappearing messages are cleared from the server too",
        "Smoother screen transitions throughout the app"
    )),
    ReleaseNotes("2.5.0", listOf(
        "Photos now stay in memory - reopening a chat is instant",
        "New HD chip in the photo editor to send one photo at full quality"
    )),
    ReleaseNotes("2.4.4", listOf(
        "Leader and up to two Co-leaders, with roles shown by each name",
        "You now see yourself in the group member list",
        "New setting: only leaders can send messages",
        "Group picture now shows in the chat list too"
    )),
    ReleaseNotes("2.4.3", listOf(
        "Group admins - the creator is admin and can promote or demote others",
        "See who created a group and when, right in group info",
        "Admins decide whether members can add people",
        "Copy group link to invite someone straight into the group"
    )),
    ReleaseNotes("2.4.2", listOf(
        "Fixed a crash that could close the app in a chat",
        "New crop tool - drag the corners or edges to frame the photo exactly",
        "View once and time capsule now share one button"
    )),
    ReleaseNotes("2.4.1", listOf(
        "Time capsule for photos, videos and files - seal them until a set time",
        "View once - send a photo or video the friend can open only one time"
    )),
    ReleaseNotes("2.4.0", listOf(
        "Disappearing messages - choose 1 hour, 24 hours or 7 days per chat",
        "HD photos switch in the main menu for full-quality pictures"
    )),
    ReleaseNotes("2.3.1", listOf(
        "Fixed Nearby not connecting by itself - no more going to Wi-Fi Direct settings"
    )),
    ReleaseNotes("2.3.0", listOf(
        "Nearby has a new, cleaner screen with a Message button per friend",
        "Wi-Fi mark on nearby messages instead of the old Bluetooth one",
        "Turn Wi-Fi on right inside the app - no trip to Settings"
    )),
    ReleaseNotes("2.2.9", listOf(
        "Fixed Nearby stopping at \"saying hello\" - the phones now exchange details"
    )),
    ReleaseNotes("2.2.8", listOf(
        "Nearby rebuilt on Wi-Fi Direct - it connects over a direct Wi-Fi link now",
        "Needs Android 10 or newer, and Wi-Fi switched on. Still no internet needed."
    )),
    ReleaseNotes("2.2.7", listOf(
        "Fixed the two phones never exchanging details over Bluetooth"
    )),
    ReleaseNotes("2.2.6", listOf(
        "Fixed Nearby connections failing with Bluetooth error 133"
    )),
    ReleaseNotes("2.2.5", listOf(
        "Fixed Nearby getting stuck on \"checking services\"",
        "Bluetooth messages are now sent reliably, one piece at a time"
    )),
    ReleaseNotes("2.2.4", listOf(
        "Fixed the two phones colliding when connecting - only one connects now"
    )),
    ReleaseNotes("2.2.3", listOf(
        "Nearby: more reliable Bluetooth connection between the two phones",
        "Nearby now shows each step, so a stuck connection is easy to spot"
    )),
    ReleaseNotes("2.2.2", listOf(
        "Fixed Nearby mode not listing nearby phones - both should see each other now"
    )),
    ReleaseNotes("2.2.1", listOf(
        "Fixed Nearby mode not finding anyone - phones now see each other",
        "Nearby mode asks to turn Bluetooth on when it is off",
        "The crash screen now shows the cause and can copy the report"
    )),
    ReleaseNotes("2.2.0", listOf(
        "Nearby mode: message Sangam friends close to you over Bluetooth, with no internet",
        "Still Signal Protocol encrypted - the phones swap keys directly",
        "Turn it on from the chats menu; it stays off until you do"
    )),
    ReleaseNotes("2.0.3", listOf(
        "Mention friends in a group with @ - type @ and pick from the list",
        "Set a group photo from Group info",
        "See when someone is typing in a group",
        "Clear a group chat or delete the whole group"
    )),
    ReleaseNotes("2.0.2", listOf(
        "Fixed a new group not showing in the chats list until a message was sent"
    )),
    ReleaseNotes("2.0.1", listOf(
        "Fixed creating a group - it failed with a connection error"
    )),
    ReleaseNotes("2.0.0", listOf(
        "Group chats are here - create a group and chat with friends and family",
        "Group messages stay Signal Protocol encrypted for every member",
        "Sender names, member list and group info with admin controls",
        "Rename, add or remove members, and leave a group anytime"
    )),
    ReleaseNotes("1.11.3", listOf(
        "Last seen shows today / yesterday instead of a date",
        "Sending photos no longer freezes the chat",
        "Photo scrolling is smooth and memory use is much lower"
    )),
    ReleaseNotes("1.11.2", listOf(
        "Opening with airplane mode or no internet now works every time",
        "Chats load from the phone, no loading circle, no crash",
        "If a crash ever happens, the next start shows exactly why"
    )),
    ReleaseNotes("1.11.1", listOf(
        "The app now opens normally with no internet or airplane mode",
        "Your chats open from the phone's cache and sync when back online"
    )),
    ReleaseNotes("1.11.0", listOf(
        "Block a friend from their profile - hidden chats, no notifications",
        "Offline messages wait with a clock icon and send when back online",
        "Photos are compressed under 400 KB to protect the free storage"
    )),
    ReleaseNotes("1.10.4", listOf(
        "Invites now include a direct app download link",
        "The download link always gives the latest version"
    )),
    ReleaseNotes("1.10.3", listOf(
        "Times now always show the clock - yesterday and older dates too",
        "New Sangam-style notifications with the Tamil letter icon",
        "Faster notifications and faster chat loading, more stable"
    )),
    ReleaseNotes("1.10.2", listOf(
        "Search now lands on the message and stays there",
        "App icon no longer crops the Sangam letter on the home screen"
    )),
    ReleaseNotes("1.10.1", listOf(
        "Tapping a search result now opens the chat right at that message",
        "In-chat search jumps to the newest match as you type",
        "Times now show Yesterday and proper dates for older messages"
    )),
    ReleaseNotes("1.10.0", listOf(
        "Pin chats - long-press a chat to keep it at the top",
        "Drafts - unfinished messages are remembered when you come back",
        "Search now finds messages across all your chats"
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("New updates", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SangamMark(size = 44)
                Spacer(Modifier.size(10.dp))
                Text(
                    "Sangam " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Where friends and family meet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // Check for updates
            val uvm: UpdateViewModel = viewModel()
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(onClick = { uvm.check() }, enabled = !uvm.checking) {
                        if (uvm.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Check for updates")
                        }
                    }
                    uvm.update?.let { info ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Version " + info.version + " is available",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (info.notes.isNotBlank()) {
                            Text(
                                info.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { uvm.downloadAndInstall(context) },
                            enabled = !uvm.downloading
                        ) {
                            if (uvm.downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Download and install")
                            }
                        }
                    }
                    when (uvm.status) {
                        "latest" -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "You are on the latest version",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        "error" -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Download failed — check your connection and try again.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            releaseNotes.take(15).forEach { release ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    cornerRadius = 18
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Version " + release.version,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        release.items.forEach { item ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    "\u2022",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    item,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
