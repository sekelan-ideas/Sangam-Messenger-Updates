package com.sangam.messenger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangam.messenger.R
import com.sangam.messenger.ui.theme.SangamOrange
import com.sangam.messenger.ui.theme.SangamTeal
import com.sangam.messenger.ui.theme.SangamTealDark
import kotlin.math.abs
import java.util.Locale

/** The Sangam confluence mark; the letter turns gold in dark mode. */
@Composable
fun SangamMark(size: Int = 96, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(
            if (dark) R.drawable.sangam_logo_dark else R.drawable.sangam_logo
        ),
        contentDescription = "Sangam",
        modifier = modifier.then(Modifier.size(size.dp))
    )
}

// TODO: Only state that the server cannot read message text after the live database is clean and real-device tests pass.
/** Simple, plain-language encryption note with Tamil Sangam badge. */
@Composable
fun EncryptionNote(modifier: Modifier = Modifier, background: Color = glassSurfaceColor(0.75f)) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Surface(
        modifier = modifier.then(Modifier.border(0.8.dp, glassBorderBrush(), shape)),
        shape = shape,
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = SangamOrange,
                modifier = Modifier.size(13.dp)
            )
            Text(
                "Messages and calls are end-to-end encrypted · Signal Protocol",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** Friendly avatar colors; every friend gets a stable one from their name. */
private val AvatarColors = listOf(
    SangamTeal, SangamOrange, SangamTealDark,
    Color(0xFF7E57C2), Color(0xFF2E9E4F), Color(0xFF5C6BC0)
)

/** Round avatar with the contact's first letter. */
@Composable
fun InitialAvatar(name: String, size: Int = 48, color: Color = Color.Unspecified) {
    val bg = if (color == Color.Unspecified) {
        AvatarColors[((name.hashCode() % AvatarColors.size) + AvatarColors.size) % AvatarColors.size]
    } else color
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase(Locale.ROOT).ifBlank { "?" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.42).sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Small helper for previews of message bodies. */
fun previewOf(type: String, text: String?): String = when (type) {
    "a" -> "🎤 Voice message"
    "i" -> "Photo"
    "t" -> text ?: ""
    "c" -> "🕰️ Time capsule"
    "d" -> "📄 " + (text ?: "Document")
    "v" -> "🎬 Video"
    else -> "Message"
}

/** Animated dots shown next to "typing…". */
@Composable
fun TypingDots(color: Color = Color.Unspecified) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val on = phase.toInt() % 3 == i
            Box(
                Modifier
                    .size(5.dp)
                    .background(if (on) c else c.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

// ------------------------------------------------------- frosted glass system

/** Top-lit specular border: brighter along the top edge, fading downward. */
@Composable
fun glassBorderBrush(): androidx.compose.ui.graphics.Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (dark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.85f)
    val bottom = if (dark) Color.White.copy(alpha = 0.06f) else SangamTeal.copy(alpha = 0.22f)
    return androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom))
}

/** Translucent frosted surface that adapts to light and dark themes. */
@Composable
fun glassSurfaceColor(alpha: Float = 0.78f): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color(0xFF1A252A).copy(alpha = alpha)
    else Color(0xFFF6FBFD).copy(alpha = alpha)
}

/** Soft ambient glow behind a screen so glass surfaces have colour depth. */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = MaterialTheme.colorScheme.background
    val topGlow = SangamTeal.copy(alpha = if (dark) 0.14f else 0.12f)
    val bottomGlow = SangamOrange.copy(alpha = if (dark) 0.08f else 0.07f)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(bg)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(topGlow, Color.Transparent, bottomGlow)
                )
            ),
        content = content
    )
}

/** Frosted card with a 1.dp top-lit border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 22,
    alpha: Float = 0.80f,
    content: @Composable () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp)
    Surface(
        modifier = modifier.then(Modifier.border(1.dp, glassBorderBrush(), shape)),
        shape = shape,
        color = glassSurfaceColor(alpha),
        tonalElevation = 0.dp,
        content = content
    )
}

/** Floating frosted pill, used for the composer and the search bar. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    alpha: Float = 0.84f,
    content: @Composable () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier.then(Modifier.border(1.dp, glassBorderBrush(), shape)),
        shape = shape,
        color = glassSurfaceColor(alpha),
        tonalElevation = 0.dp,
        content = content
    )
}

/** Grouped settings row: rounded tinted icon badge, title, optional subtitle. */
@Composable
fun GlassSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = SangamTeal,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    tint.copy(alpha = 0.16f),
                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Pure-Kotlin ISO 18004 QR Code (Version 4-L, 33x33) rendered in Sangam's
 * custom Teal & Sunset Orange aesthetic — zero external libraries required.
 */
@Composable
fun SangamQrBox(
    data: String,
    subtitle: String? = null,
    sizeDp: Int = 180
) {
    val grid = androidx.compose.runtime.remember(data) { buildSangamQrV4(data) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = Color.White,
            modifier = Modifier
                .border(
                    1.5.dp,
                    glassBorderBrush(),
                    androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
                )
                .padding(14.dp)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(sizeDp.dp)
            ) {
                val n = grid.size
                val cell = size.width / n
                for (r in 0 until n) {
                    for (c in 0 until n) {
                        if (grid[r][c]) {
                            val isCorner = (r < 7 && c < 7) ||
                                (r < 7 && c >= n - 7) ||
                                (r >= n - 7 && c < 7)
                            drawRoundRect(
                                color = if (isCorner) SangamTealDark else Color(0xFF142228),
                                topLeft = androidx.compose.ui.geometry.Offset(c * cell, r * cell),
                                size = androidx.compose.ui.geometry.Size(cell * 0.94f, cell * 0.94f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    cell * 0.25f,
                                    cell * 0.25f
                                )
                            )
                        }
                    }
                }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildSangamQrV4(text: String): Array<BooleanArray> {
    val data = text.toByteArray(Charsets.UTF_8).let {
        if (it.size > 78) it.copyOf(78) else it
    }
    val bits = ArrayList<Int>(800)
    bits.add(0); bits.add(1); bits.add(0); bits.add(0)
    for (i in 7 downTo 0) bits.add((data.size shr i) and 1)
    for (b in data) {
        val v = b.toInt() and 0xFF
        for (i in 7 downTo 0) bits.add((v shr i) and 1)
    }
    repeat(minOf(4, 800 - bits.size)) { bits.add(0) }
    while (bits.size % 8 != 0) bits.add(0)
    val cw = IntArray(80)
    var cwIdx = 0
    var i = 0
    while (i < bits.size && cwIdx < 80) {
        var v = 0
        for (j in 0 until 8) v = (v shl 1) or bits[i + j]
        cw[cwIdx++] = v
        i += 8
    }
    val pad = intArrayOf(0xEC, 0x11)
    var pi = 0
    while (cwIdx < 80) cw[cwIdx++] = pad[pi++ % 2]

    val exp = IntArray(512)
    val log = IntArray(256)
    var x = 1
    for (k in 0 until 255) {
        exp[k] = x; log[x] = k
        x = x shl 1
        if ((x and 0x100) != 0) x = x xor 0x11D
    }
    for (k in 255 until 512) exp[k] = exp[k - 255]
    var gen = intArrayOf(1)
    for (k in 0 until 20) {
        val nxt = IntArray(gen.size + 1)
        for (j in gen.indices) {
            nxt[j] = nxt[j] xor gen[j]
            if (gen[j] != 0) nxt[j + 1] = nxt[j + 1] xor exp[log[gen[j]] + k]
        }
        gen = nxt
    }
    val ecc = IntArray(20)
    for (b in cw) {
        val f = b xor ecc[0]
        for (j in 0 until 19) ecc[j] = ecc[j + 1]
        ecc[19] = 0
        if (f != 0) {
            val lf = log[f]
            for (j in 0 until 20) {
                if (gen[j + 1] != 0) ecc[j] = ecc[j] xor exp[lf + log[gen[j + 1]]]
            }
        }
    }
    val full = IntArray(100)
    for (k in 0 until 80) full[k] = cw[k]
    for (k in 0 until 20) full[80 + k] = ecc[k]

    val sz = 33
    val grid = Array(sz) { BooleanArray(sz) }
    val res = Array(sz) { BooleanArray(sz) }
    fun setMod(r: Int, c: Int, v: Boolean) {
        if (r in 0 until sz && c in 0 until sz) {
            grid[r][c] = v; res[r][c] = true
        }
    }
    fun finder(r0: Int, c0: Int) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val in7 = dr in 0..6 && dc in 0..6
                val on = in7 && (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                setMod(r0 + dr, c0 + dc, on)
            }
        }
    }
    finder(0, 0); finder(0, sz - 7); finder(sz - 7, 0)
    for (dr in -2..2) {
        for (dc in -2..2) {
            val m = maxOf(abs(dr), abs(dc))
            setMod(26 + dr, 26 + dc, m == 0 || m == 2)
        }
    }
    for (k in 8 until sz - 8) {
        setMod(6, k, k % 2 == 0)
        setMod(k, 6, k % 2 == 0)
    }
    setMod(sz - 8, 8, true)
    for (k in 0..8) {
        if (k != 6) { res[8][k] = true; res[k][8] = true }
    }
    for (k in 0..7) {
        res[8][sz - 1 - k] = true; res[sz - 1 - k][8] = true
    }
    var bitIdx = 0
    var up = true
    var c = sz - 1
    while (c > 0) {
        if (c == 6) c -= 1
        for (step in 0 until sz) {
            val r = if (up) sz - 1 - step else step
            for (dc in 0 downTo -1) {
                val cc = c + dc
                if (!res[r][cc]) {
                    var bit = if (bitIdx < 800) {
                        ((full[bitIdx shr 3] shr (7 - (bitIdx and 7))) and 1) == 1
                    } else false
                    bitIdx += 1
                    if ((r + cc) % 2 == 0) bit = !bit
                    grid[r][cc] = bit
                }
            }
        }
        up = !up
        c -= 2
    }
    val fmt = 0x77C4
    val coords = arrayOf(
        8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 7, 8 to 8,
        7 to 8, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8
    )
    for (k in 0 until 15) {
        val b = ((fmt shr (14 - k)) and 1) == 1
        val (r0, c0) = coords[k]
        grid[r0][c0] = b
        if (k < 7) grid[sz - 1 - k][8] = b
        else grid[8][sz - 15 + k] = b
    }
    return grid
}
