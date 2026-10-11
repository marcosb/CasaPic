package com.metitem.casapic.image

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.io.File

object FingerprintCalculator {

    fun compute(imageFile: File): Long? = runCatching {
        val bytes = imageFile.readBytes()
        val image = Image.makeFromEncoded(bytes)

        val grayInfo = ImageInfo(
            colorInfo = ColorInfo(
                colorType = ColorType.GRAY_8,
                alphaType = ColorAlphaType.OPAQUE,
                colorSpace = ColorSpace.sRGB
            ),
            width = 9,
            height = 8
        )
        val smallBitmap = Bitmap()
        smallBitmap.allocPixels(grayInfo)

        val canvas = Canvas(smallBitmap)
        val paint = Paint()
        canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(9f, 8f),
            paint
        )

        var hash = 0L
        var bit = 63
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                // GRAY_8 stores luma in the red channel byte of the ARGB int
                val left = (smallBitmap.getColor(col, row) shr 16) and 0xFF
                val right = (smallBitmap.getColor(col + 1, row) shr 16) and 0xFF
                if (left > right) hash = hash or (1L shl bit)
                bit--
            }
        }

        smallBitmap.close()
        canvas.close()
        image.close()

        hash
    }.getOrNull()

    fun hammingDistance(a: Long, b: Long): Int = (a xor b).countOneBits()
}
