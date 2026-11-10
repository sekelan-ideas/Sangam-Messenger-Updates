package com.sangam.messenger.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sangam.messenger.data.Sangam

/** Fingerprint unlock helper shared by the lock screen and setup dialog. */
fun tryFingerprint(onSuccess: () -> Unit, onFallback: () -> Unit) {
    val context = LocalContextHolder.context ?: return onFallback()
    val activity = context as? FragmentActivity ?: return onFallback()
    if (!canFingerprint()) return onFallback()
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                onFallback()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Sangam")
        .setNegativeButtonText("Use password")
        .build()
    prompt.authenticate(info)
}

/** True if this phone has fingerprint (or face) unlock enrolled. */
fun canFingerprint(): Boolean {
    val context = LocalContextHolder.context ?: return false
    val manager = BiometricManager.from(context)
    return manager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.BIOMETRIC_STRONG
    ) == BiometricManager.BIOMETRIC_SUCCESS
}

/** Tracks app-launched system screens (camera, photo picker) so the app
 *  lock does not trigger while waiting for their result. */
object ExternalOps {
    @Volatile
    var pending = false
}

/** Holds the current activity so the non-composable fingerprint helper can use it. */
object LocalContextHolder {
    @Volatile
    var context: Context? = null
}

/** Full-screen lock shown when the app starts or comes back from background.
 *  Takes a full password - numbers, letters and symbols - or a fingerprint. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pwVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun tryUnlock() {
        if (Sangam.checkAppLockPin(entered)) onUnlocked()
        else {
            error = "Wrong password, try again"
            entered = ""
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Solid background: the lock is drawn over the app now, so without
            // this the chat list showed straight through it.
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(88.dp)
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(23.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Sangam is locked", fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: "Enter your password",
            fontSize = 16.sp,
            color = if (error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = entered,
            onValueChange = {
                error = null
                entered = it
            },
            label = { Text("Password") },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
            visualTransformation =
                if (pwVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { tryUnlock() }),
            isError = error != null,
            trailingIcon = {
                IconButton(onClick = { pwVisible = !pwVisible }) {
                    Icon(
                        if (pwVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription =
                            if (pwVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { tryUnlock() },
            enabled = entered.isNotEmpty(),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Unlock", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (canFingerprint()) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    tryFingerprint(
                        onSuccess = onUnlocked,
                        onFallback = { error = "Fingerprint did not match" }
                    )
                },
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Use fingerprint", fontSize = 16.sp)
            }
        }
    }
}

/** Enable / disable / change the app lock from the chats screen menu. */
@Composable
fun AppLockSetupDialog(onDismiss: () -> Unit) {
    val enabled = remember { Sangam.appLockEnabled() }
    var pwVisible by remember { mutableStateOf(false) }
    var pw1 by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var currentPw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val fieldModifier = Modifier.fillMaxWidth()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App lock", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (!enabled) {
                    Text(
                        "Set a password - numbers, letters and symbols. " +
                            "Sangam will ask for it (or your fingerprint) every time it opens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pw1,
                        onValueChange = { error = null; pw1 = it },
                        label = { Text("New password") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        visualTransformation =
                            if (pwVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        trailingIcon = {
                            IconButton(onClick = { pwVisible = !pwVisible }) {
                                Icon(
                                    if (pwVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = fieldModifier
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pw2,
                        onValueChange = { error = null; pw2 = it },
                        label = { Text("Repeat password") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        visualTransformation =
                            if (pwVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = fieldModifier
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        "App lock is on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentPw,
                        onValueChange = { error = null; currentPw = it },
                        label = { Text("Current password") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        visualTransformation =
                            if (pwVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        trailingIcon = {
                            IconButton(onClick = { pwVisible = !pwVisible }) {
                                Icon(
                                    if (pwVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = fieldModifier
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pw1,
                        onValueChange = { error = null; pw1 = it },
                        label = { Text("New password (optional)") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        visualTransformation =
                            if (pwVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = fieldModifier
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pw2,
                        onValueChange = { error = null; pw2 = it },
                        label = { Text("Repeat new password") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        visualTransformation =
                            if (pwVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = fieldModifier
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!enabled) {
                Button(
                    onClick = {
                        when {
                            pw1.length < 4 -> error = "Password needs at least 4 characters"
                            pw1 != pw2 -> error = "Passwords don't match"
                            else -> {
                                Sangam.setAppLockPin(pw1)
                                onDismiss()
                            }
                        }
                    },
                    enabled = pw1.length >= 4 && pw2.length >= 4
                ) { Text("Enable") }
            } else {
                Button(
                    onClick = {
                        when {
                            !Sangam.checkAppLockPin(currentPw) -> error = "Wrong current password"
                            pw1.isNotEmpty() && pw1.length < 4 ->
                                error = "New password needs at least 4 characters"
                            pw1.isNotEmpty() && pw1 != pw2 -> error = "Passwords don't match"
                            pw1.isNotEmpty() -> {
                                Sangam.setAppLockPin(pw1)
                                onDismiss()
                            }
                            else -> {
                                Sangam.clearAppLock()
                                onDismiss()
                            }
                        }
                    },
                    enabled = currentPw.isNotEmpty() &&
                        (pw1.isEmpty() || pw2.length >= 4)
                ) { Text(if (pw1.isEmpty()) "Turn off" else "Change") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
