package com.sangam.messenger.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.input.pointer.pointerInput
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
 * Full-screen profile photo cropper: drag to move, pinch to zoom,
 * then confirm — the circle is what gets saved.
 */
@Composable
fun CropScreen(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var minScale by remember { mutableStateOf(1f) }
    var ready by remember { mutableStateOf(false) }

    val bw = bitmap.width
    val bh = bitmap.height

    fun cropSide(): Float = min(viewport.width, viewport.height) * 0.72f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1113))
            .onSizeChanged { size ->
                viewport = size
                if (!ready && size.width > 0 && size.height > 0) {
                    val fit = min(size.width.toFloat() / bw, size.height.toFloat() / bh)
                    minScale = max(cropSide() / (bw * fit), cropSide() / (bh * fit))
                    scale = minScale
                    offset = Offset.Zero
                    ready = true
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!ready) return@detectTransformGestures
                    scale = (scale * zoom).coerceIn(minScale, minScale * 6f)
                    val fit = min(viewport.width.toFloat() / bw, viewport.height.toFloat() / bh)
                    val maxX = max(0f, (bw * fit * scale - cropSide()) / 2f)
                    val maxY = max(0f, (bh * fit * scale - cropSide()) / 2f)
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
                // Dark scrim with a clear circle in the middle
                val side = min(size.width, size.height) * 0.72f
                val circle = Rect(
                    left = (size.width - side) / 2f,
                    top = (size.height - side) / 2f,
                    right = (size.width + side) / 2f,
                    bottom = (size.height + side) / 2f
                )
                val frame = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
                val hole = Path().apply { addOval(circle) }
                val mask = Path.combine(PathOperation.Difference, frame, hole)
                drawPath(mask, Color(0xB3000000))
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = side / 2f,
                    center = circle.center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 4.dp),
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
                "Drag to move \u2022 Pinch to zoom",
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
                        val sideScreen = cropSide()
                        var x = ((viewport.width - sideScreen) / 2f - left).toInt()
                        var y = ((viewport.height - sideScreen) / 2f - top).toInt()
                        var side = (sideScreen / (fit * scale)).toInt()
                        x = x.coerceIn(0, bw - 1)
                        y = y.coerceIn(0, bh - 1)
                        side = side.coerceIn(1, min(bw - x, bh - y))
                        val square = Bitmap.createBitmap(bitmap, x, y, side, side)
                        onDone(Bitmap.createScaledBitmap(square, 512, 512, true))
                    }
                }
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Save photo",
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}
