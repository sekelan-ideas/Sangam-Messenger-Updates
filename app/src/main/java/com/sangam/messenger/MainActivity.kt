package com.sangam.messenger

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.sangam.messenger.data.Sangam
import com.sangam.messenger.push.Push
import com.sangam.messenger.ui.AuthScreen
import com.sangam.messenger.ui.ChatScreen
import com.sangam.messenger.ui.ChatsScreen
import com.sangam.messenger.ui.LockScreen
import com.sangam.messenger.ui.ExternalOps
import com.sangam.messenger.ui.NewChatScreen
import com.sangam.messenger.ui.NewGroupScreen
import com.sangam.messenger.ui.NearbyScreen
import com.sangam.messenger.ui.ProfileScreen
import com.sangam.messenger.ui.WhatsNewScreen
import com.sangam.messenger.ui.SetupScreen
import com.sangam.messenger.ui.SplashScreen
import com.sangam.messenger.ui.theme.SangamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Boot {
    data object Loading : Boot
    data object NotConfigured : Boot
    data object SignedOut : Boot
    data class SignedIn(val userId: String) : Boot
}

class MainActivity : FragmentActivity() {

    companion object {
        /** Username from a sangam://add?u=... invite link, awaiting navigation. */
        var pendingInvite by mutableStateOf<String?>(null)

        /** Peer id from a sangam://chat?id=... notification tap, awaiting navigation. */
        var pendingChat by mutableStateOf<String?>(null)
        var pendingGroup by mutableStateOf<String?>(null)

        /** A confirmation / reset link from the email, awaiting sign-in. */
        var pendingAuthLink by mutableStateOf<String?>(null)

        /** True when the link was a password reset, so we ask for a new one. */
        var pendingRecovery by mutableStateOf(false)

        /** Path of a photo shared from another app, awaiting a friend pick. */
        var pendingShare by mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Match the window to the theme before the first frame, so the app
        // never flashes the wrong colour when opening.
        if (Sangam.wantsDarkTheme(resources)) setTheme(R.style.Theme_Sangam_Dark)
        super.onCreate(savedInstanceState)
        captureDeepLink(intent)
        requestNotificationPermission()
        com.sangam.messenger.ui.LocalContextHolder.context = this
        setContent {
            SangamTheme {
                // Every screen paints its own themed background, so the
                // window can never desync from the app theme (e.g. the
                // login screen after logging out in dark mode).
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SangamAppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        // Android Share button: a photo shared from the gallery or elsewhere.
        if (intent?.action == android.content.Intent.ACTION_SEND &&
            intent.type?.startsWith("image/") == true
        ) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<android.net.Uri>(
                android.content.Intent.EXTRA_STREAM
            )
            if (uri != null) {
                runCatching {
                    // Copy it now - the share permission expires once we return.
                    val out = java.io.File(cacheDir, "shared_image.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                    pendingShare = out.absolutePath
                }
            }
            return
        }
        val data = intent?.data ?: return
        if (data.scheme != "sangam") return
        when (data.host) {
            "add" -> data.getQueryParameter("u")?.takeIf { it.isNotBlank() }?.let { pendingInvite = it }
            "chat" -> data.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { pendingChat = it }
            "group" -> data.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { pendingGroup = it }
            "auth-callback" -> pendingAuthLink = data.toString()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
}

@Composable
private fun SangamAppRoot() {
    var boot by remember { mutableStateOf<Boot>(Boot.Loading) }
    val scope = rememberCoroutineScope()

    // ---- app lock: locked on cold start and whenever the app goes to
    // the background while the lock is enabled ----
    var locked by remember { mutableStateOf(Sangam.appLockEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    // Skip the lock while the camera / photo picker we opened
                    // is still coming back with its result.
                    if (Sangam.appLockEnabled() && !ExternalOps.pending) locked = true
                Lifecycle.Event.ON_RESUME ->
                    ExternalOps.pending = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        boot = withContext(Dispatchers.IO) {
            if (!Sangam.configured) Boot.NotConfigured
            else if (Sangam.restoreSession()) Boot.SignedIn(Sangam.userId() ?: "")
            else Boot.SignedOut
        }
        if (boot is Boot.SignedIn) Push.registerToken()
    }

    // The lock is drawn ON TOP of the app rather than replacing it. Returning
    // early here discarded the whole composition, so the navigation stack was
    // rebuilt on unlock and always landed back at the chat list.
    Box(Modifier.fillMaxSize()) {

    // If Sangam closed unexpectedly last time, show why - please screenshot
    // this screen if it appears.
    var crashReport by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { crashReport = Sangam.takeCrashReport() }
    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text("Sangam closed unexpectedly") },
            text = {
                // The TOP of the stack is the useful part - the exception and
                // the first app frames - so show that, not the framework tail.
                Text(
                    report.take(1600),
                    fontSize = 11.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val ctx = com.sangam.messenger.ui.LocalContextHolder.context
                        val cm = ctx?.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText("Sangam crash", report)
                        )
                        ctx?.let {
                            android.widget.Toast.makeText(
                                it, "Crash report copied", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    crashReport = null
                }) { Text("Copy & close") }
            },
            dismissButton = {
                TextButton(onClick = { crashReport = null }) { Text("Close") }
            }
        )
    }

    when (val state = boot) {
        Boot.Loading -> SplashScreen()
        Boot.NotConfigured -> SetupScreen()
        is Boot.SignedIn, Boot.SignedOut -> {
            val nav = rememberNavController()
            val start = if (state is Boot.SignedIn) "chats" else "auth"

            // Opened through an invite link? Go straight to it.
            LaunchedEffect(state, MainActivity.pendingInvite) {
                val invite = MainActivity.pendingInvite
                if (state is Boot.SignedIn && invite != null) {
                    MainActivity.pendingInvite = null
                    nav.navigate("newchat?u=$invite")
                }
            }
            // Opened through a notification tap? Go straight to that chat.
            LaunchedEffect(state, MainActivity.pendingChat) {
                val chat = MainActivity.pendingChat
                if (state is Boot.SignedIn && chat != null) {
                    MainActivity.pendingChat = null
                    nav.navigate("chat/$chat")
                }
            }
            // Opened a group invite link or QR? Join the group first, then open it.
            LaunchedEffect(state, MainActivity.pendingGroup) {
                val gid = MainActivity.pendingGroup
                if (state is Boot.SignedIn && gid != null) {
                    MainActivity.pendingGroup = null
                    val gName = withContext(Dispatchers.IO) {
                        Sangam.joinGroup(gid)
                        Sangam.groupInfo(gid)?.name ?: "Group"
                    }
                    nav.navigate("group/" + gid + "?name=" + Uri.encode(gName))
                }
            }
            // Came back from a confirmation or reset email? Finish signing in.
            val authCtx = androidx.compose.ui.platform.LocalContext.current
            // NOTE: do NOT clear pendingAuthLink before the suspending work.
            // It is this effect's key - clearing it early makes Compose cancel
            // this very coroutine on the next frame, which is why the reset
            // link used to open the app and then do nothing.
            LaunchedEffect(MainActivity.pendingAuthLink) {
                val link = MainActivity.pendingAuthLink ?: return@LaunchedEffect
                val wasSignedIn = Sangam.userId() != null
                val hadResetPending = Sangam.resetPending()
                try {
                    val res = withContext(Dispatchers.IO) {
                        Sangam.adoptAuthLink(android.net.Uri.parse(link))
                    }
                    val ok = res.isSuccess
                    val isReset = res.getOrDefault(false) || wasSignedIn || hadResetPending
                    if (ok && isReset) {
                        Sangam.clearResetPending()
                        MainActivity.pendingRecovery = true
                        boot = Boot.SignedIn(Sangam.userId() ?: "")
                    } else if (isReset) {
                        android.widget.Toast.makeText(
                            authCtx,
                            "That reset link is invalid or expired. Request a new one.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            authCtx,
                            if (ok) "Email confirmed - you are signed in"
                            else "That link did not work. Try logging in.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        if (ok) boot = Boot.SignedIn(Sangam.userId() ?: "")
                    }
                } finally {
                    // Clear only once the work above has finished.
                    if (MainActivity.pendingAuthLink == link) {
                        MainActivity.pendingAuthLink = null
                    }
                }
            }
            // Signed in while still showing the login screen? Go to the chats.
            LaunchedEffect(state, MainActivity.pendingRecovery) {
                if (state is Boot.SignedIn && !MainActivity.pendingRecovery) {
                    val cur = nav.currentBackStackEntry?.destination?.route
                    if (cur == "auth") {
                        Push.registerToken()
                        nav.navigate("chats") { popUpTo(0) { inclusive = true } }
                    }
                }
            }
            // A reset link came back - ask for the new password. The scope is
            // created out here so closing the dialog cannot cancel the save.
            val rootScope = rememberCoroutineScope()
            if (MainActivity.pendingRecovery) {
                var p1 by remember { mutableStateOf("") }
                var p2 by remember { mutableStateOf("") }
                var savingPw by remember { mutableStateOf(false) }
                val hidden = androidx.compose.ui.text.input.PasswordVisualTransformation()
                AlertDialog(
                    onDismissRequest = { if (!savingPw) MainActivity.pendingRecovery = false },
                    title = { Text("Set a new password") },
                    text = {
                        Column {
                            Text(
                                "Choose a new password for your account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = p1,
                                onValueChange = { p1 = it },
                                label = { Text("New password") },
                                singleLine = true,
                                visualTransformation = hidden,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = p2,
                                onValueChange = { p2 = it },
                                label = { Text("Confirm new password") },
                                singleLine = true,
                                isError = p2.isNotEmpty() && p1 != p2,
                                supportingText = {
                                    if (p2.isNotEmpty()) {
                                        Text(
                                            if (p1 == p2) "Passwords match"
                                            else "Passwords do not match"
                                        )
                                    }
                                },
                                visualTransformation = hidden,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = p1.length >= 6 && p1 == p2,
                            onClick = {
                                savingPw = true
                                rootScope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        Sangam.updatePassword(p1)
                                    }
                                    savingPw = false
                                    MainActivity.pendingRecovery = false
                                    android.widget.Toast.makeText(
                                        authCtx,
                                        if (r.isSuccess) "Password updated"
                                        else "Could not update the password",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { MainActivity.pendingRecovery = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            // Shared a photo from another app? Pick who to send it to.
            LaunchedEffect(state, MainActivity.pendingShare) {
                if (state is Boot.SignedIn && MainActivity.pendingShare != null) {
                    nav.navigate("share")
                }
            }

            // Watch for incoming calls anywhere in the app while signed in.
            var incomingCall by remember { mutableStateOf<com.sangam.messenger.data.CallRow?>(null) }
            var incomingName by remember { mutableStateOf("Sangam friend") }
            var incomingActive by remember { mutableStateOf(false) }
            var dismissedCallId by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(state) {
                if (state is Boot.SignedIn) {
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        val cur = incomingCall
                        if (cur == null) {
                            val inc = withContext(Dispatchers.IO) { Sangam.incomingCall() }
                            if (inc != null && inc.id != dismissedCallId) {
                                val prof = withContext(Dispatchers.IO) { Sangam.profileOf(inc.caller) }
                                incomingName = prof?.display_name ?: "Sangam friend"
                                incomingActive = false
                                incomingCall = inc
                            }
                        } else {
                            val st = withContext(Dispatchers.IO) { Sangam.callStatus(cur.id) }
                            when (st) {
                                "active" -> incomingActive = true
                                "declined", "ended", null -> {
                                    dismissedCallId = cur.id
                                    incomingCall = null
                                    incomingActive = false
                                }
                            }
                        }
                    }
                }
            }

            NavHost(
                navController = nav,
                startDestination = start,
                // Screens ease in and out instead of snapping.
                enterTransition = {
                    fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(150)) + slideOutHorizontally(tween(260)) { it / 14 }
                }
            ) {
                composable("auth") {
                    AuthScreen(onDone = {
                        Push.registerToken()
                        nav.navigate("chats") {
                            popUpTo("auth") { inclusive = true }
                        }
                    })
                }
                composable("chats") {
                    ChatsScreen(
                        onOpenChat = { peerId, peerName, jumpTo ->
                            nav.navigate("chat/" + peerId + "?name=" + Uri.encode(peerName) +
                                (if (jumpTo != null) "&jump=" + Uri.encode(jumpTo) else ""))
                        },
                        onNewChat = { nav.navigate("newchat") },
                        onOpenGroup = { groupId, groupName ->
                            nav.navigate("group/" + groupId + "?name=" + Uri.encode(groupName))
                        },
                        onNewGroup = { nav.navigate("newgroup") },
                        onOpenNearby = { nav.navigate("nearby") },
                        onSignOut = {
                            scope.launch {
                                Sangam.signOut()
                                nav.navigate("auth") { popUpTo(0) { inclusive = true } }
                            }
                        },
                        onAccountDeleted = {
                            nav.navigate("auth") { popUpTo(0) { inclusive = true } }
                        },
                        onOpenProfile = { nav.navigate("profile") },
                        onOpenWhatsNew = { nav.navigate("whatsnew") }
                    )
                }
                composable("profile") {
                    ProfileScreen(onBack = { nav.popBackStack() })
                }
                composable("whatsnew") {
                    WhatsNewScreen(onBack = { nav.popBackStack() })
                }
                composable("share") {
                    com.sangam.messenger.ui.ShareScreen(
                        sharedPath = MainActivity.pendingShare ?: "",
                        onDone = {
                            MainActivity.pendingShare = null
                            nav.navigate("chats") { popUpTo(0) { inclusive = true } }
                        }
                    )
                }
                composable(
                    route = "newchat?u={u}",
                    arguments = listOf(
                        navArgument("u") { type = NavType.StringType; nullable = true }
                    )
                ) { entry ->
                    NewChatScreen(
                        prefill = entry.arguments?.getString("u"),
                        onOpenChat = { peerId ->
                            nav.navigate("chat/$peerId") { launchSingleTop = true }
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(
                    route = "chat/{peerId}?name={name}&jump={jump}",
                    arguments = listOf(
                        navArgument("peerId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; nullable = true },
                        navArgument("jump") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { entry ->
                    val peerId = entry.arguments?.getString("peerId") ?: return@composable
                    ChatScreen(
                        peerId = peerId,
                        nameHint = entry.arguments?.getString("name"),
                        jumpTo = entry.arguments?.getString("jump"),
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(
                    route = "group/{groupId}?name={name}",
                    arguments = listOf(
                        navArgument("groupId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; nullable = true }
                    )
                ) { entry ->
                    val gid = entry.arguments?.getString("groupId") ?: return@composable
                    ChatScreen(
                        peerId = "",
                        nameHint = entry.arguments?.getString("name"),
                        groupId = gid,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable("newgroup") {
                    NewGroupScreen(
                        onOpenGroup = { gid ->
                            nav.navigate("group/" + gid) { popUpTo("chats") }
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable("nearby") {
                    NearbyScreen(
                        onOpenChat = { peerId, name ->
                            nav.navigate("chat/" + peerId + "?name=" + Uri.encode(name))
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
            }
            incomingCall?.let { call ->
                com.sangam.messenger.ui.CallScreen(
                    peerName = incomingName,
                    incoming = true,
                    active = incomingActive,
                    callId = call.id,
                    isVideo = call.kind == "video",
                    onAnswer = {
                        incomingActive = true
                        rootScope.launch(Dispatchers.IO) { Sangam.answerCall(call.id) }
                    },
                    onDecline = {
                        dismissedCallId = call.id
                        incomingCall = null
                        incomingActive = false
                        rootScope.launch(Dispatchers.IO) { Sangam.endCall(call.id, true) }
                    },
                    onHangUp = {
                        dismissedCallId = call.id
                        incomingCall = null
                        incomingActive = false
                        rootScope.launch(Dispatchers.IO) { Sangam.endCall(call.id, false) }
                    }
                )
            }
        }
    }
    if (locked) {
        LockScreen(onUnlocked = { locked = false })
    }
    }
}
