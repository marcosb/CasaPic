package com.metitem.casapic.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private enum class DragHandle { NONE, MOVE, TL, TR, BL, BR }

@Composable
fun CropOverlay(
    imageWidth: Int,
    imageHeight: Int,
    onApply: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()

        val imgW = imageWidth.toFloat().coerceAtLeast(1f)
        val imgH = imageHeight.toFloat().coerceAtLeast(1f)
        val fitScale = if (cw > 0f && ch > 0f) minOf(cw / imgW, ch / imgH) else 1f
        val drawW = imgW * fitScale
        val drawH = imgH * fitScale
        val imgLeft = (cw - drawW) / 2f
        val imgTop = (ch - drawH) / 2f

        var cropL by remember { mutableFloatStateOf(imgLeft + drawW * 0.1f) }
        var cropT by remember { mutableFloatStateOf(imgTop + drawH * 0.1f) }
        var cropR by remember { mutableFloatStateOf(imgLeft + drawW * 0.9f) }
        var cropB by remember { mutableFloatStateOf(imgTop + drawH * 0.9f) }

        var dragHandle by remember { mutableStateOf(DragHandle.NONE) }
        val hitRadius = 36f
        val minSize = 20f

        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        dragHandle = when {
                            (pos - Offset(cropL, cropT)).getDistance() < hitRadius -> DragHandle.TL
                            (pos - Offset(cropR, cropT)).getDistance() < hitRadius -> DragHandle.TR
                            (pos - Offset(cropL, cropB)).getDistance() < hitRadius -> DragHandle.BL
                            (pos - Offset(cropR, cropB)).getDistance() < hitRadius -> DragHandle.BR
                            pos.x in cropL..cropR && pos.y in cropT..cropB -> DragHandle.MOVE
                            else -> DragHandle.NONE
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        when (dragHandle) {
                            DragHandle.TL -> {
                                cropL = (cropL + delta.x).coerceIn(imgLeft, cropR - minSize)
                                cropT = (cropT + delta.y).coerceIn(imgTop, cropB - minSize)
                            }
                            DragHandle.TR -> {
                                cropR = (cropR + delta.x).coerceIn(cropL + minSize, imgLeft + drawW)
                                cropT = (cropT + delta.y).coerceIn(imgTop, cropB - minSize)
                            }
                            DragHandle.BL -> {
                                cropL = (cropL + delta.x).coerceIn(imgLeft, cropR - minSize)
                                cropB = (cropB + delta.y).coerceIn(cropT + minSize, imgTop + drawH)
                            }
                            DragHandle.BR -> {
                                cropR = (cropR + delta.x).coerceIn(cropL + minSize, imgLeft + drawW)
                                cropB = (cropB + delta.y).coerceIn(cropT + minSize, imgTop + drawH)
                            }
                            DragHandle.MOVE -> {
                                val rw = cropR - cropL
                                val rh = cropB - cropT
                                val newL = (cropL + delta.x).coerceIn(imgLeft, imgLeft + drawW - rw)
                                val newT = (cropT + delta.y).coerceIn(imgTop, imgTop + drawH - rh)
                                cropL = newL; cropT = newT
                                cropR = newL + rw; cropB = newT + rh
                            }
                            DragHandle.NONE -> Unit
                        }
                    },
                    onDragEnd = { dragHandle = DragHandle.NONE },
                    onDragCancel = { dragHandle = DragHandle.NONE }
                )
            }
        ) {
            val mask = Color.Black.copy(alpha = 0.5f)
            // Darken outside the crop rect
            drawRect(mask, topLeft = Offset(0f, 0f), size = Size(cw, cropT))
            drawRect(mask, topLeft = Offset(0f, cropB), size = Size(cw, ch - cropB))
            drawRect(mask, topLeft = Offset(0f, cropT), size = Size(cropL, cropB - cropT))
            drawRect(mask, topLeft = Offset(cropR, cropT), size = Size(cw - cropR, cropB - cropT))

            val rw = cropR - cropL
            val rh = cropB - cropT

            // Crop rect border
            drawRect(Color.White, topLeft = Offset(cropL, cropT), size = Size(rw, rh), style = Stroke(width = 2f))

            // Rule-of-thirds grid
            val thirds = Color.White.copy(alpha = 0.4f)
            drawLine(thirds, Offset(cropL + rw / 3f, cropT), Offset(cropL + rw / 3f, cropB), strokeWidth = 1f)
            drawLine(thirds, Offset(cropL + rw * 2f / 3f, cropT), Offset(cropL + rw * 2f / 3f, cropB), strokeWidth = 1f)
            drawLine(thirds, Offset(cropL, cropT + rh / 3f), Offset(cropR, cropT + rh / 3f), strokeWidth = 1f)
            drawLine(thirds, Offset(cropL, cropT + rh * 2f / 3f), Offset(cropR, cropT + rh * 2f / 3f), strokeWidth = 1f)

            // Corner handles
            val hs = 12f
            listOf(
                Offset(cropL - hs / 2f, cropT - hs / 2f),
                Offset(cropR - hs / 2f, cropT - hs / 2f),
                Offset(cropL - hs / 2f, cropB - hs / 2f),
                Offset(cropR - hs / 2f, cropB - hs / 2f)
            ).forEach { drawRect(Color.White, topLeft = it, size = Size(hs, hs)) }
        }

        // Apply / Cancel buttons overlaid at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                if (drawW > 0f && drawH > 0f) {
                    val l = ((cropL - imgLeft) / drawW).coerceIn(0f, 1f)
                    val t = ((cropT - imgTop) / drawH).coerceIn(0f, 1f)
                    val r = ((cropR - imgLeft) / drawW).coerceIn(0f, 1f)
                    val b = ((cropB - imgTop) / drawH).coerceIn(0f, 1f)
                    onApply(l, t, r, b)
                }
            }) { Text("Apply Crop") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
