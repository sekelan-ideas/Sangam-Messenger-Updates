package com.sangam.messenger.ui

import android.graphics.Bitmap
import com.sangam.messenger.BuildConfig
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sangam.messenger.vm.readForCrop
import com.sangam.messenger.vm.shortError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.sangam.messenger.vm.ProfileViewModel

/** A round profile photo with an initial-letter fallback. */
@Composable
fun Avatar(name: String, url: String?, size: Int = 48, bitmap: android.graphics.Bitmap? = null) {
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
        )
    } else if (url != null) {
        // Older uploads stored a bare file name; turn it into the full
        // public URL so the photo always loads either way.
        val full = remember(url) {
            if (url.startsWith("http")) url
            else BuildConfig.SUPABASE_URL.trimEnd('/') +
                "/storage/v1/object/public/avatars/" + url.substringAfterLast('/')
        }
        SubcomposeAsyncImage(
            model = full,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
            loading = { InitialAvatar(name, size) },
            error = { InitialAvatar(name, size) }
        )
    } else {
        InitialAvatar(name, size)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm: ProfileViewModel = viewModel()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var uname by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.profile) {
        vm.profile?.let {
            name = it.display_name
            status = it.status ?: ""
            uname = it.username
        }
    }

    val scope = rememberCoroutineScope()
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        ExternalOps.pending = false
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { readForCrop(context, uri) }
                    .onSuccess { cropBitmap = it }
                    .onFailure {
                        vm.photoFailed(
                            "Could not open that photo — " + shortError(it)
                        )
                    }
            }
        }
    }

    AuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("My profile", fontWeight = FontWeight.SemiBold) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = glassSurfaceColor(0.82f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(22.dp))
            GlassPill(modifier = Modifier.padding(bottom = 12.dp)) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    SangamMark(18)
                    Text(
                        "MY SANGAM CONFLUENCE IDENTITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(
                        vm.profile?.display_name ?: "?",
                        vm.profile?.avatar_url,
                        size = 128
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                    onClick = {
                        ExternalOps.pending = true
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = "Change photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = uname,
                        onValueChange = {
                            uname = it.lowercase()
                                .filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '_' }
                            vm.checkUsername(uname)
                        },
                        label = { Text("Username (without @)") },
                        singleLine = true,
                        isError = vm.usernameCheck == "taken",
                        supportingText = {
                            when (vm.usernameCheck) {
                                "checking" -> Text("Checking @$uname…")
                                "free" -> Text("@$uname is available")
                                "taken" -> Text("@$uname is already taken")
                                else -> Text("Friends find you with this")
                            }
                        },
                        trailingIcon = {
                            TextButton(
                                enabled = vm.usernameCheck == "free" && !vm.busy,
                                onClick = { vm.saveUsername(uname) }
                            ) { Text("Change") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = status,
                        onValueChange = { if (it.length <= 100) status = it },
                        label = { Text("Status (optional)") },
                        placeholder = { Text("e.g. Busy at work") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    vm.message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Saved")) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { vm.save(name.trim(), status.trim()) },
                        enabled = !vm.busy && name.isNotBlank()
                    ) {
                        if (vm.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
            if (uname.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "MY SANGAM QR CARD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(10.dp))
                        SangamQrBox(
                            data = "sangam://add?u=" + uname,
                            subtitle = "Friends can scan this QR to connect with @" + uname,
                            sizeDp = 155
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Your photo, name and status are visible to your Sangam friends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    cropBitmap?.let { bmp ->
        CropScreen(
            bitmap = bmp,
            onCancel = { cropBitmap = null },
            onDone = { cropped ->
                cropBitmap = null
                vm.saveAvatarBitmap(cropped)
            }
        )
    }
    }
}
