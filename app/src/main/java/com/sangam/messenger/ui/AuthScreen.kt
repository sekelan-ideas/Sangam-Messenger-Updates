package com.sangam.messenger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sangam.messenger.vm.AuthViewModel

/** Splash shown while restoring the session. */
@Composable
fun SplashScreen() {
    val pulse = rememberInfiniteTransition(label = "logo")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SangamMark(140, Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        })
        Spacer(Modifier.height(16.dp))
        Text(
            "Sangam",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Where friends & family meet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Shown when the app was built without backend credentials. */
@Composable
fun SetupScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SangamMark(120)
        Spacer(Modifier.height(16.dp))
        Text(
            "Sangam",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This build is not connected to a chat service yet. " +
                "The next build, with the backend switched on, will let you sign in and chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AuthScreen(onDone: () -> Unit) {
    val vm: AuthViewModel = viewModel()
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var forgotOpen by remember { mutableStateOf(false) }
    var forgotId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeMode by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var newPw2 by remember { mutableStateOf("") }
    var scanLoginOpen by remember { mutableStateOf(false) }
    AuroraBackground(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        SangamMark(110)
        Spacer(Modifier.height(12.dp))
        Text(
            "Sangam",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Where friends & family meet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        if (vm.needsConfirm || codeMode.isNotEmpty()) {
            Text(
                if (codeMode == "newpass") "Set a new password" else "Check your email",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                vm.confirmEmail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (codeMode != "newpass") {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (codeMode == "reset")
                        "Open that email and tap the link inside to set a new password."
                    else
                        "Open that email and tap the link inside to confirm your address.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            if (codeMode == "newpass") {
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPw2,
                    onValueChange = { newPw2 = it },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    isError = newPw2.isNotEmpty() && newPw != newPw2,
                    supportingText = {
                        if (newPw2.isNotEmpty()) {
                            Text(
                                if (newPw == newPw2) "Passwords match"
                                else "Passwords do not match"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.setNewPassword(newPw) { codeMode = "" } },
                    enabled = !vm.busy && newPw.length >= 6 && newPw == newPw2,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Save password") }
            } else {
                Text(
                    "If the email also shows a 6-digit code, you can type it here instead:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { t -> code = t.filter { it.isDigit() }.take(6) },
                    label = { Text("Code from the email (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        val isReset = codeMode == "reset"
                        vm.verifyCode(code, isReset) {
                            if (isReset) codeMode = "newpass" else onDone()
                        }
                    },
                    enabled = !vm.busy && code.length >= 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (vm.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else Text("Confirm")
                }
                TextButton(onClick = { vm.resendCode(codeMode == "reset") }) {
                    Text("Send the email again")
                }
                TextButton(onClick = {
                    codeMode = ""
                    vm.cancelConfirm()
                    vm.clearMessages()
                }) {
                    Text("Back")
                }
            }
            vm.notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            vm.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
        if (mode == "signup") {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.lowercase()
                        .filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' }
                    vm.checkUsername(username)
                },
                label = { Text("Username (a-z, 0-9, _)") },
                singleLine = true,
                isError = vm.usernameCheck == "taken",
                supportingText = {
                    when (vm.usernameCheck) {
                        "checking" -> Text("Checking @$username…")
                        "free" -> Text("@$username is available")
                        "taken" -> Text("@$username is already taken")
                        else -> Text("Friends find you with this")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email or username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff,
                        contentDescription =
                            if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (mode == "signup") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirm password") },
                singleLine = true,
                isError = confirm.isNotEmpty() && confirm != password,
                supportingText = {
                    if (confirm.isNotEmpty()) {
                        Text(
                            if (confirm == password) "Passwords match"
                            else "Passwords do not match"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        }

        vm.notice?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (mode == "login") {
            TextButton(onClick = {
                vm.clearMessages()
                forgotId = email
                forgotOpen = true
            }) {
                Text("Forgot password?")
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (mode == "signup") {
                    vm.signUp(email, password, username, displayName, onDone)
                } else {
                    vm.signIn(email, password, onDone)
                }
            },
            enabled = !vm.busy &&
                email.isNotBlank() &&
                password.length >= 6 &&
                (mode == "login" ||
                    (email.contains('@') &&
                        username.length in 3..20 && displayName.isNotBlank() &&
                        confirm == password && vm.usernameCheck != "taken")),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (vm.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (mode == "signup") "Create my account" else "Log in")
            }
        }

        if (mode == "login") {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    vm.clearMessages()
                    scanLoginOpen = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "\uD83D\uDCF1  Link this phone by scanning QR from your other phone",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = {
            mode = if (mode == "signup") "login" else "signup"
            vm.clearMessages()
        }) {
            Text(
                if (mode == "signup") "I already have an account"
                else "New to Sangam? Create an account"
            )
        }
        }
        }

        Spacer(Modifier.height(18.dp))
        EncryptionNote()
        Spacer(Modifier.height(32.dp))
        }
    }
    }

    if (scanLoginOpen) {
        QrScannerScreen(
            title = "Scan Link QR from your signed-in phone",
            onScanned = { raw ->
                scanLoginOpen = false
                val uMatch = Regex("[?&]u=([^&\\s]+)").find(raw)
                val pMatch = Regex("[?&]p=([^&\\s]+)").find(raw)
                val addMatch = Regex("sangam://add\\?u=([A-Za-z0-9_]+)").find(raw)
                val scannedUser = uMatch?.groupValues?.get(1) ?: addMatch?.groupValues?.get(1)
                val scannedPw = pMatch?.groupValues?.get(1)?.let { b64 ->
                    runCatching {
                        String(
                            android.util.Base64.decode(
                                b64,
                                android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
                            ),
                            Charsets.UTF_8
                        )
                    }.getOrNull()
                }
                if (!scannedUser.isNullOrBlank() && !scannedPw.isNullOrBlank()) {
                    email = scannedUser
                    password = scannedPw
                    vm.signIn(scannedUser, scannedPw, onDone)
                } else if (!scannedUser.isNullOrBlank()) {
                    email = scannedUser
                }
            },
            onClose = { scanLoginOpen = false }
        )
    }

    if (forgotOpen) {
        AlertDialog(
            onDismissRequest = { forgotOpen = false },
            title = { Text("Reset password", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "We will email you a link to set a new password. You can " +
                            "enter either your email address or your username.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = forgotId,
                        onValueChange = { forgotId = it },
                        label = { Text("Email or username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (forgotId.isNotBlank()) {
                            vm.resetPassword(forgotId)
                            forgotOpen = false
                            codeMode = "reset"
                        }
                    }
                ) { Text("Send link") }
            },
            dismissButton = {
                TextButton(onClick = { forgotOpen = false }) { Text("Cancel") }
            }
        )
    }
}
