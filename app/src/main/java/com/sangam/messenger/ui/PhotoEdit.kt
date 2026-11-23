package com.sangam.messenger.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sangam.messenger.ui.theme.SangamTeal
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Photo editor shown before sending: rotate, crop and simple filters.
 * Shown full-screen over the chat when a photo is picked or taken.
 */
@Composable
fun PhotoEditScreen(
    initial: Bitmap,
    onSend: (Bitmap, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var bitmap by remember { mutableStateOf(initial) }
    var filter by remember { mutableStateOf(0) }
    var cropping by remember { mutableStateOf(false) }
    // Send this one at full quality, whatever the app-wide setting says.
    var hd by remember { mutableStateOf(false) }

    if (cropping) {
        RectCropScreen(
            bitmap = bitmap,
            onDone = {
                bitmap = it
                cropping = false
            },
            onCancel = { cropping = false }
        )
        return
    }

    val shown = remember(filter, bitmap) { applyPhotoFilter(bitmap, filter) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10151A))
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Text(
                "Edit photo",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = SangamTeal,
                modifier = Modifier.size(44.dp),
                onClick = { onSend(shown, hd) }
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Send photo",
                    tint = Color.White,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        // The photo
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = "Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Filters — scroll sideways on small screens, never wrap.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("None", "B&W", "Warm", "Cool", "Bright").forEachIndexed { i, label ->
                val selected = filter == i
                Text(
                    label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) SangamTeal.copy(alpha = 0.9f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .clickable { filter = i }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        // Rotate + crop, centred — fits any screen width.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.clickable {
                    bitmap = rotatePhoto(bitmap, 90f)
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.RotateRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Rotate", color = Color.White)
                }
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.clickable { cropping = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Crop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Crop", color = Color.White)
                }
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (hd) SangamTeal else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable { hd = !hd }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (hd) "HD on" else "HD",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Rectangular drag/pinch cropper used by the photo editor. */
@Composable
private fun RectCropScreen(
    bitmap: Bitmap,
    onDone: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var minScale by remember { mutableStateOf(1f) }
    var ready by remember { mutableStateOf(false) }

    val bw = bitmap.width
    val bh = bitmap.height

    // The crop frame, in screen coordinates. Drag a corner or an edge to
    // resize it; drag the photo itself to move or pinch it.
    var crop by remember { mutableStateOf(Rect.Zero) }

    fun frame(): Rect {
        val w = viewport.width * 0.84f
        val h = viewport.height * 0.62f
        return Rect(
            (viewport.width - w) / 2f, (viewport.height - h) / 2f,
            (viewport.width + w) / 2f, (viewport.height + h) / 2f
        )
    }

    /** Drag one corner or edge of the frame. */
    fun resized(tag: String, d: Offset): Rect {
        val minSide = 90f
        var l = crop.left
        var t = crop.top
        var r = crop.right
        var b = crop.bottom
        if (tag.contains("l")) l = (l + d.x).coerceIn(0f, r - minSide)
        if (tag.contains("r")) r = (r + d.x).coerceIn(l + minSide, viewport.width.toFloat())
        if (tag.contains("t")) t = (t + d.y).coerceIn(0f, b - minSide)
        if (tag.contains("b")) b = (b + d.y).coerceIn(t + minSide, viewport.height.toFloat())
        return Rect(l, t, r, b)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1113))
            .onSizeChanged { size ->
                viewport = size
                if (!ready && size.width > 0 && size.height > 0) {
                    // Start with the whole photo visible; pinch to zoom
                    // in on the part you want.
                    minScale = 1f
                    scale = minScale
                    offset = Offset.Zero
                    crop = frame()
                    ready = true
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!ready) return@detectTransformGestures
                    scale = (scale * zoom).coerceIn(minScale, minScale * 6f)
                    val fit = min(viewport.width.toFloat() / bw, viewport.height.toFloat() / bh)
                    val maxX = max(0f, (bw * fit * scale - frame().width) / 2f)
                    val maxY = max(0f, (bh * fit * scale - frame().height) / 2f)
                    offset = Offset(
                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                        (offset.y + pan.y).coerceIn(-maxY, maxY)
                    )
                }
            }
    ) {
        if (ready) {
            Canvas(Modifier.fillMaxSize()) {
                val fit = min(size.width / bw, size.height / bh)
                val dispW = bw * fit * scale
                val dispH = bh * fit * scale
                val left = (size.width - dispW) / 2f + offset.x
                val top = (size.height - dispH) / 2f + offset.y
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(
                        dispW.roundToInt().coerceAtLeast(1),
                        dispH.roundToInt().coerceAtLeast(1)
                    )
                )
                // Dark scrim outside the crop frame
                val f = crop
                val framePath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
                val hole = Path().apply { addRect(f) }
                val mask = Path.combine(PathOperation.Difference, framePath, hole)
                drawPath(mask, Color(0xB3000000))
                drawRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = f.topLeft,
                    size = f.size,
                    style = Stroke(width = 2.dp.toPx())
                )
                // Rule-of-thirds guides
                val guide = Color.White.copy(alpha = 0.3f)
                for (i in 1..2) {
                    val gx = f.left + f.width * i / 3f
                    val gy = f.top + f.height * i / 3f
                    drawLine(guide, Offset(gx, f.top), Offset(gx, f.bottom), 1.dp.toPx())
                    drawLine(guide, Offset(f.left, gy), Offset(f.right, gy), 1.dp.toPx())
                }
                // Corner handles
                val hr = 10.dp.toPx()
                listOf(f.topLeft, f.topRight, f.bottomLeft, f.bottomRight).forEach {
                    drawCircle(SangamTeal, hr, it)
                    drawCircle(Color.White, hr, it, style = Stroke(width = 2.dp.toPx()))
                }
            }

            // Grab zones: drag a corner or an edge to resize the frame.
            val touch = 48.dp
            val spots = listOf(
                "tl" to crop.topLeft, "tr" to crop.topRight,
                "bl" to crop.bottomLeft, "br" to crop.bottomRight,
                "l" to Offset(crop.left, crop.center.y),
                "r" to Offset(crop.right, crop.center.y),
                "t" to Offset(crop.center.x, crop.top),
                "b" to Offset(crop.center.x, crop.bottom)
            )
            spots.forEach { (tag, p) ->
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (p.x - touch.toPx() / 2f).roundToInt(),
                                (p.y - touch.toPx() / 2f).roundToInt()
                            )
                        }
                        .size(touch)
                        .pointerInput(tag) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                crop = resized(tag, drag)
                            }
                        }
                )
            }
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Text("Crop photo", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        // Confirm button, bottom centre
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Drag the corners to resize • Pinch to zoom",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = CircleShape,
                color = SangamTeal,
                modifier = Modifier.size(56.dp),
                onClick = {
                    if (ready && viewport.width > 0 && viewport.height > 0) {
                        val fit = min(
                            viewport.width.toFloat() / bw,
                            viewport.height.toFloat() / bh
                        )
                        val dispW = bw * fit * scale
                        val dispH = bh * fit * scale
                        val left = (viewport.width - dispW) / 2f + offset.x
                        val top = (viewport.height - dispH) / 2f + offset.y
                        val f = crop
                        var x = ((f.left - left) / (fit * scale)).toInt()
                        var y = ((f.top - top) / (fit * scale)).toInt()
                        var w = (f.width / (fit * scale)).toInt()
                        var h = (f.height / (fit * scale)).toInt()
                        x = x.coerceIn(0, bw - 1)
                        y = y.coerceIn(0, bh - 1)
                        w = w.coerceIn(1, bw - x)
                        h = h.coerceIn(1, bh - y)
                        val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                        // Keep the sent photo a sensible size.
                        val longest = max(w, h)
                        val out = if (longest > 1600) {
                            val s = 1600f / longest
                            Bitmap.createScaledBitmap(
                                cropped, (w * s).toInt(), (h * s).toInt(), true
                            )
                        } else cropped
                        onDone(out)
                    }
                }
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

/** Apply one of the simple photo filters. */
internal fun applyPhotoFilter(src: Bitmap, filter: Int): Bitmap {
    if (filter == 0) return src
    val m = when (filter) {
        1 -> android.graphics.ColorMatrix().apply { setSaturation(0f) }
        2 -> android.graphics.ColorMatrix(
            floatArrayOf(
                0.95f, 0.05f, 0.0f, 0f, 14f,
                0.04f, 0.90f, 0.02f, 0f, 8f,
                0.0f, 0.03f, 0.80f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        3 -> android.graphics.ColorMatrix(
            floatArrayOf(
                0.86f, 0.0f, 0.09f, 0f, 0f,
                0.0f, 0.92f, 0.04f, 0f, 0f,
                0.07f, 0.0f, 1.08f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        else -> android.graphics.ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 20f,
                0f, 1.1f, 0f, 0f, 20f,
                0f, 0f, 1.1f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val paint = android.graphics.Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(m)
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
}

/** Rotate a photo by 90-degree steps. */
internal fun rotatePhoto(src: Bitmap, degrees: Float): Bitmap {
    val m = android.graphics.Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
