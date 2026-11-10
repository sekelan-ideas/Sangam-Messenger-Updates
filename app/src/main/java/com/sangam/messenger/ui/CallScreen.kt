package com.sangam.messenger.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sangam.messenger.ui.theme.SangamTeal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The call screen, with live two-way voice and video, Mute, Speaker and Camera controls.
 */
@android.annotation.SuppressLint("MissingPermission")
@Composable
fun CallScreen(
    peerName: String,
    /** True when someone is calling us and we have not answered. */
    incoming: Boolean,
    /** True when this is a video call rather than voice-only. */
    isVideo: Boolean = false,
    /** True once the call is live on both sides. */
    active: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    callId: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var seconds by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember(isVideo) { mutableStateOf(isVideo) }
    var cameraOn by remember(isVideo) { mutableStateOf(isVideo) }
    var frontCamera by remember { mutableStateOf(true) }
    var remoteVideoFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val textureViewRef = remember { arrayOfNulls<android.view.TextureView>(1) }

    val mutedState = rememberUpdatedState(muted)
    val cameraOnState = rememberUpdatedState(cameraOn)

    var hasMicPerm by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCamPerm by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        res[android.Manifest.permission.RECORD_AUDIO]?.let { hasMicPerm = it }
        res[android.Manifest.permission.CAMERA]?.let { hasCamPerm = it }
    }
    LaunchedEffect(isVideo) {
        val needed = mutableListOf<String>()
        if (!hasMicPerm) needed.add(android.Manifest.permission.RECORD_AUDIO)
        if (isVideo && !hasCamPerm) needed.add(android.Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(active) {
        if (active) {
            while (true) {
                delay(1000)
                seconds += 1
            }
        }
    }

    LaunchedEffect(active, speaker) {
        if (active) {
            setCallSpeaker(context, speaker)
        }
    }

    // Play the phone's ringtone while an incoming call is ringing.
    androidx.compose.runtime.DisposableEffect(incoming, active) {
        val ringtone = if (incoming && !active) {
            runCatching {
                val uri = android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_RINGTONE
                )
                android.media.RingtoneManager.getRingtone(context, uri)?.also { it.play() }
            }.getOrNull()
        } else null
        onDispose {
            runCatching { ringtone?.stop() }
        }
    }

    // Open local camera into the TextureView when video is active.
    androidx.compose.runtime.DisposableEffect(active, isVideo, cameraOn, frontCamera, hasCamPerm) {
        if (!active || !isVideo || !cameraOn || !hasCamPerm) {
            onDispose { }
        } else {
            val camThread = android.os.HandlerThread("SangamCam").also { it.start() }
            val camHandler = android.os.Handler(camThread.looper)
            var camDevice: android.hardware.camera2.CameraDevice? = null
            var camSession: android.hardware.camera2.CameraCaptureSession? = null

            fun startPreview(tv: android.view.TextureView) {
                val st = tv.surfaceTexture ?: return
                st.setDefaultBufferSize(320, 240)
                val surface = android.view.Surface(st)
                val mgr = context.getSystemService(android.content.Context.CAMERA_SERVICE)
                    as android.hardware.camera2.CameraManager
                val wantLens = if (frontCamera) {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                }
                val camId = runCatching {
                    mgr.cameraIdList.firstOrNull { id ->
                        mgr.getCameraCharacteristics(id)
                            .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == wantLens
                    } ?: mgr.cameraIdList.firstOrNull()
                }.getOrNull() ?: return

                runCatching {
                    mgr.openCamera(
                        camId,
                        object : android.hardware.camera2.CameraDevice.StateCallback() {
                            override fun onOpened(device: android.hardware.camera2.CameraDevice) {
                                camDevice = device
                                runCatching {
                                    val req = device.createCaptureRequest(
                                        android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                                    ).apply { addTarget(surface) }
                                    @Suppress("DEPRECATION")
                                    device.createCaptureSession(
                                        listOf(surface),
                                        object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(
                                                session: android.hardware.camera2.CameraCaptureSession
                                            ) {
                                                camSession = session
                                                runCatching {
                                                    session.setRepeatingRequest(req.build(), null, camHandler)
                                                }
                                            }
                                            override fun onConfigureFailed(
                                                session: android.hardware.camera2.CameraCaptureSession
                                            ) {}
                                        },
                                        camHandler
                                    )
                                }
                            }
                            override fun onDisconnected(device: android.hardware.camera2.CameraDevice) {
                                runCatching { device.close() }
                            }
                            override fun onError(device: android.hardware.camera2.CameraDevice, error: Int) {
                                runCatching { device.close() }
                            }
                        },
                        camHandler
                    )
                }
            }

            val tv = textureViewRef[0]
            if (tv != null) {
                if (tv.isAvailable) {
                    startPreview(tv)
                } else {
                    tv.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: android.graphics.SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            startPreview(tv)
                        }
                        override fun onSurfaceTextureSizeChanged(
                            surface: android.graphics.SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {}
                        override fun onSurfaceTextureDestroyed(
                            surface: android.graphics.SurfaceTexture
                        ): Boolean = true
                        override fun onSurfaceTextureUpdated(
                            surface: android.graphics.SurfaceTexture
                        ) {}
                    }
                }
            }

            onDispose {
                runCatching { camSession?.close() }
                runCatching { camDevice?.close() }
                runCatching { camThread.quitSafely() }
            }
        }
    }

    // Live two-way voice (and video frame) stream once the call is active.
    androidx.compose.runtime.DisposableEffect(active, callId, hasMicPerm, isVideo) {
        if (!active || callId.isNullOrBlank()) {
            onDispose { }
        } else {
            val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            runCatching {
                am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                setCallSpeaker(context, speaker)
            }
            val sampleRate = 8000
            val frameSamples = 1600 // 200ms frame at 8 kHz for lower latency
            val minPlayBuf = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate * 2)
            val player = runCatching {
                android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minPlayBuf)
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .build()
                    .also { it.play() }
            }.getOrNull()

            val minRecBuf = android.media.AudioRecord.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameSamples * 4)
            val recorder = if (hasMicPerm) {
                runCatching {
                    android.media.AudioRecord(
                        android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        android.media.AudioFormat.CHANNEL_IN_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        minRecBuf
                    ).also { rec ->
                        runCatching {
                            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                                android.media.audiofx.AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
                            }
                            if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                                android.media.audiofx.NoiseSuppressor.create(rec.audioSessionId)?.enabled = true
                            }
                            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                                android.media.audiofx.AutomaticGainControl.create(rec.audioSessionId)?.enabled = true
                            }
                        }
                        rec.startRecording()
                    }
                }.getOrNull()
            } else null

            val playQueue = Channel<ShortArray>(
                capacity = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            val sendQueue = Channel<Pair<Int, String>>(
                capacity = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val seqCounter = java.util.concurrent.atomic.AtomicInteger(0)

            // 1. Write incoming audio frames to the earpiece/speaker.
            scope.launch {
                for (pcm in playQueue) {
                    if (!isActive) break
                    runCatching {
                        player?.write(pcm, 0, pcm.size, android.media.AudioTrack.WRITE_BLOCKING)
                    }
                }
            }

            // 2. Poll new audio & video frames sent by the other participant.
            scope.launch {
                var lastSeq = 0
                while (isActive) {
                    val chunks = com.sangam.messenger.data.Sangam.fetchCallChunks(callId, lastSeq)
                    for (c in chunks) {
                        if (c.seq > lastSeq) lastSeq = c.seq
                        if (c.pcmB64.startsWith("V:")) {
                            val bmp = decodeVideoFrame(c.pcmB64.substring(2))
                            if (bmp != null) remoteVideoFrame = bmp
                        } else {
                            val pcm = decodeUlaw(c.pcmB64)
                            if (pcm.isNotEmpty()) playQueue.trySend(pcm)
                        }
                    }
                    delay(120)
                }
            }

            // 3. Upload captured voice & video frames without blocking the mic loop.
            scope.launch {
                for ((seq, b64) in sendQueue) {
                    if (!isActive) break
                    com.sangam.messenger.data.Sangam.sendCallChunk(callId, seq, b64)
                }
            }

            // 4. Continuously capture mic frames and compress with G.711 u-law.
            if (recorder != null) {
                scope.launch {
                    val buf = ShortArray(frameSamples)
                    while (isActive) {
                        val n = runCatching { recorder.read(buf, 0, frameSamples) }.getOrDefault(-1)
                        if (n <= 0) {
                            delay(40)
                            continue
                        }
                        if (mutedState.value) continue
                        var peak = 0
                        for (i in 0 until n) {
                            val a = kotlin.math.abs(buf[i].toInt())
                            if (a > peak) peak = a
                        }
                        if (peak < 150) continue
                        val s = seqCounter.incrementAndGet()
                        sendQueue.trySend(s to encodeUlaw(buf, n))
                    }
                }
            }

            // 5. Capture compressed JPEG video frames when in a video call.
            if (isVideo) {
                scope.launch {
                    while (isActive) {
                        delay(420)
                        if (!cameraOnState.value) continue
                        val bmp = withContext(Dispatchers.Main) {
                            runCatching { textureViewRef[0]?.getBitmap(160, 214) }.getOrNull()
                        } ?: continue
                        val vB64 = encodeVideoFrame(bmp)
                        if (vB64.isNotEmpty()) {
                            val s = seqCounter.incrementAndGet()
                            sendQueue.trySend(s to ("V:" + vB64))
                        }
                    }
                }
            }

            onDispose {
                scope.cancel()
                playQueue.close()
                sendQueue.close()
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                runCatching { player?.stop() }
                runCatching { player?.release() }
                runCatching {
                    setCallSpeaker(context, false)
                    am.mode = android.media.AudioManager.MODE_NORMAL
                }
            }
        }
    }

    val kindLabel = if (isVideo) "video call" else "call"
    val status = when {
        active -> formatCallDuration(seconds) + (if (muted) "  •  Muted" else "")
        incoming -> "Incoming " + kindLabel + "…"
        else -> "Calling…"
    }

    AuroraBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 36.dp)
                ) {
                    if (isVideo && active) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val remote = remoteVideoFrame
                            if (remote != null) {
                                Image(
                                    bitmap = remote,
                                    contentDescription = "Remote video",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SangamTeal,
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                peerName.take(1).uppercase(),
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Connecting video…",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (cameraOn && hasCamPerm) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                        .width(90.dp)
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black)
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.view.TextureView(ctx).also {
                                                textureViewRef[0] = it
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    } else {
                        GlassPill(modifier = Modifier.padding(bottom = 18.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SangamMark(18)
                                Text(
                                    "🔒 Sangam Confluence Call · End-to-End Encrypted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .background(SangamTeal.copy(alpha = 0.12f), CircleShape)
                                .padding(10.dp)
                                .background(SangamTeal.copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SangamTeal,
                                modifier = Modifier.size(92.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        peerName.take(1).uppercase(),
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        peerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(Modifier.weight(1f))
                        if (incoming && !active) {
                            CallButton("Decline", MaterialTheme.colorScheme.error, onDecline)
                            CallButton("Answer", Color(0xFF2E9E4F), onAnswer)
                        } else if (active) {
                            CallButton(
                                label = if (muted) "Unmute" else "Mute",
                                tint = if (muted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                textColor = if (muted) Color.White else MaterialTheme.colorScheme.onSurface,
                                onClick = { muted = !muted }
                            )
                            if (isVideo) {
                                CallButton(
                                    label = if (cameraOn) "Cam Off" else "Cam On",
                                    tint = if (cameraOn) SangamTeal else MaterialTheme.colorScheme.surfaceVariant,
                                    textColor = if (cameraOn) Color.White else MaterialTheme.colorScheme.onSurface,
                                    onClick = { cameraOn = !cameraOn }
                                )
                                CallButton(
                                    label = "Flip",
                                    tint = MaterialTheme.colorScheme.surfaceVariant,
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    onClick = { frontCamera = !frontCamera }
                                )
                            } else {
                                CallButton(
                                    label = if (speaker) "Earpiece" else "Speaker",
                                    tint = if (speaker) SangamTeal else MaterialTheme.colorScheme.surfaceVariant,
                                    textColor = if (speaker) Color.White else MaterialTheme.colorScheme.onSurface,
                                    onClick = { speaker = !speaker }
                                )
                            }
                            CallButton("Hang up", MaterialTheme.colorScheme.error, onHangUp)
                        } else {
                            CallButton("Hang up", MaterialTheme.colorScheme.error, onHangUp)
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CallButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    textColor: Color = Color.White
) {
    Surface(
        shape = CircleShape,
        color = tint,
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return (if (m < 10) "0" else "") + m + ":" + (if (s < 10) "0" else "") + s
}

private fun setCallSpeaker(context: android.content.Context, speakerOn: Boolean) {
    runCatching {
        val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = speakerOn
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!speakerOn) {
                am.clearCommunicationDevice()
            } else {
                val dev = am.availableCommunicationDevices.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (dev != null) am.setCommunicationDevice(dev)
            }
        }
    }
}

private fun encodeVideoFrame(bmp: android.graphics.Bitmap): String = runCatching {
    val baos = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 40, baos)
    android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
}.getOrDefault("")

private fun decodeVideoFrame(b64: String): ImageBitmap? = runCatching {
    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

private fun encodeUlaw(samples: ShortArray, count: Int): String {
    val out = ByteArray(count)
    for (i in 0 until count) {
        val s = samples[i].toInt()
        val sign = if (s < 0) 0x80 else 0
        var mag = if (s < 0) -s else s
        if (mag > 32635) mag = 32635
        mag += 0x84
        var exp = 7
        var mask = 0x4000
        while (exp > 0 && (mag and mask) == 0) {
            exp -= 1
            mask = mask shr 1
        }
        val mant = (mag shr (exp + 3)) and 0x0F
        out[i] = ((sign or (exp shl 4) or mant).inv() and 0xFF).toByte()
    }
    return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP)
}

private fun decodeUlaw(b64: String): ShortArray = runCatching {
    val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    val out = ShortArray(raw.size)
    for (i in raw.indices) {
        val u = raw[i].toInt().inv() and 0xFF
        val sign = u and 0x80
        val exp = (u shr 4) and 0x07
        val mant = u and 0x0F
        var mag = ((mant shl 3) + 0x84) shl exp
        mag -= 0x84
        out[i] = (if (sign != 0) -mag else mag).toShort()
    }
    out
}.getOrDefault(ShortArray(0))

