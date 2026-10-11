package com.metitem.casapic.image

import com.metitem.casapic.db.Photo_edits
import com.metitem.casapic.model.HistogramData
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect

actual fun computeHistogram(filePath: String, editStack: List<Photo_edits>): HistogramData? {
    val src = SkiaImageOps.loadAndApplyEdits(filePath, editStack) ?: return null
    // Scale down to 150px max to keep pixel iteration fast on large photos
    val maxDim = 150
    val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
    val sw = (src.width * scale).toInt().coerceAtLeast(1)
    val sh = (src.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap().also { it.allocN32Pixels(sw, sh) }
    val canvas = Canvas(scaled)
    canvas.drawImageRect(
        Image.makeFromBitmap(src),
        Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
        Rect.makeWH(sw.toFloat(), sh.toFloat()),
        Paint()
    )
    canvas.close()
    val r = IntArray(256)
    val g = IntArray(256)
    val b = IntArray(256)
    for (x in 0 until sw) {
        for (y in 0 until sh) {
            val color = scaled.getColor(x, y)
            r[(color shr 16) and 0xFF]++
            g[(color shr 8) and 0xFF]++
            b[color and 0xFF]++
        }
    }
    scaled.close()
    return HistogramData(r, g, b)
}
