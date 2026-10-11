package com.metitem.casapic.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.metitem.casapic.db.Photo_edits
import com.metitem.casapic.image.SkiaImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

@Composable
actual fun SkiaImageCanvas(
    filePath: String,
    editStack: List<Photo_edits>,
    modifier: Modifier
) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(filePath, editStack) {
        loadError = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                SkiaImageOps.loadAndApplyEdits(filePath, editStack)
                    ?.asComposeImageBitmap()
            }.getOrNull()
        }
        if (bitmap == null) loadError = true
    }

    Box(
        modifier = modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.1f, 10f)
                    offset += pan
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val img = bitmap
        if (img != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, Offset(size.width / 2, size.height / 2))
                }) {
                    val imgW = img.width.toFloat()
                    val imgH = img.height.toFloat()
                    val canvasW = size.width
                    val canvasH = size.height
                    val fitScale = minOf(canvasW / imgW, canvasH / imgH)
                    val drawW = imgW * fitScale
                    val drawH = imgH * fitScale
                    val left = (canvasW - drawW) / 2
                    val top = (canvasH - drawH) / 2
                    drawImage(
                        image = img,
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
                    )
                }
            }
        } else if (loadError) {
            Text("Unable to load image", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Loading…", style = MaterialTheme.typography.bodySmall)
        }
    }
}
