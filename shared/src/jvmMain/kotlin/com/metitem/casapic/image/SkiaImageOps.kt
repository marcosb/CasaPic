package com.metitem.casapic.image

import com.metitem.casapic.db.Photo_edits
import com.metitem.casapic.metadata.PhotoEdit
import com.metitem.casapic.metadata.RedEyeRegion
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import java.io.File

object SkiaImageOps {

    fun loadAndApplyEdits(filePath: String, editStack: List<Photo_edits>): Bitmap? {
        val bytes = runCatching { File(filePath).readBytes() }.getOrNull() ?: return null
        var bitmap = Image.makeFromEncoded(bytes).toBitmap() ?: return null
        for (edit in editStack) {
            if (edit.is_undone != 0L) continue
            bitmap = applyEditToBitmap(bitmap, edit.edit_proto) ?: bitmap
        }
        return bitmap
    }

    private fun applyEditToBitmap(src: Bitmap, editProto: ByteArray): Bitmap? {
        val edit = runCatching { PhotoEdit.ADAPTER.decode(editProto) }.getOrNull() ?: return src
        return when {
            edit.rotate     != null -> applyRotate(src, edit.rotate.degrees)
            edit.crop       != null -> { val c = edit.crop; applyCrop(src, c.left, c.top, c.right, c.bottom) }
            edit.tilt       != null -> applyTilt(src, edit.tilt.angle, edit.tilt.scale.let { if (it == 0f) 1f else it })
            edit.finetune   != null -> { val f = edit.finetune; applyColorMatrix(src, finetuneMatrix(f.shadows, f.highlights, f.color_temp, f.brightness, f.saturation)) }
            edit.fill_light != null -> applyGamma(src, 1f - edit.fill_light.value_ * 0.5f)
            edit.highlights != null -> applyColorMatrix(src, highlightsMatrix(edit.highlights.value_))
            edit.shadows    != null -> applyColorMatrix(src, shadowsMatrix(edit.shadows.value_))
            edit.saturation != null -> applyColorMatrix(src, saturationMatrix(edit.saturation.value_ + 1f))
            edit.sharpness  != null -> applySharpness(src, edit.sharpness.value_)
            edit.enhance    != null -> applyColorMatrix(applyColorMatrix(src, autoContrastMatrix()), autoColorMatrix())
            edit.autolight  != null -> applyColorMatrix(src, autoContrastMatrix())
            edit.autocolor  != null -> applyColorMatrix(src, autoColorMatrix())
            edit.sepia      != null -> applyColorMatrix(src, sepiaMatrix(edit.sepia.strength))
            edit.bw         != null -> applyColorMatrix(src, bwMatrix(edit.bw.strength))
            edit.softfocus  != null -> applyBlur(src, edit.softfocus.strength * 4f)
            edit.filmgrain  != null -> applyFilmGrain(src, edit.filmgrain.strength)
            edit.glow       != null -> applyGlow(src, edit.glow.strength)
            edit.lomo       != null -> applyColorMatrix(src, lomoMatrix(edit.lomo.saturation, edit.lomo.contrast))
            edit.retrolux   != null -> applyColorMatrix(src, retroluxMatrix(edit.retrolux.strength))
            edit.vignette   != null -> applyVignette(src, edit.vignette.strength, edit.vignette.size)
            edit.tint       != null -> applyColorMatrix(src, tintMatrix(edit.tint.r, edit.tint.g, edit.tint.b, edit.tint.strength))
            edit.redeye      != null -> applyRedEye(src, edit.redeye.regions)
            edit.boost       != null -> applyColorMatrix(src, boostMatrix(edit.boost.strength))
            edit.filtered_bw != null -> applyColorMatrix(src, filteredBwMatrix(edit.filtered_bw.strength))
            edit.orton       != null -> applyOrton(src, edit.orton.strength)
            edit.polarize    != null -> applyColorMatrix(src, polarizeMatrix(edit.polarize.strength))
            else                     -> src
        }
    }

    // ── Geometric ──────────────────────────────────────────────────────────

    private fun applyRotate(src: Bitmap, degrees: Int): Bitmap {
        val norm = ((degrees % 360) + 360) % 360
        if (norm == 0) return src
        val (dstW, dstH) = if (norm == 90 || norm == 270) src.height to src.width else src.width to src.height
        val dst = Bitmap().apply { allocN32Pixels(dstW, dstH) }
        Canvas(dst).use { canvas ->
            val cx = dstW / 2f; val cy = dstH / 2f
            canvas.concat(Matrix33.makeTranslate(cx, cy))
            canvas.concat(Matrix33.makeRotate(norm.toFloat()))
            canvas.concat(Matrix33.makeTranslate(-src.width / 2f, -src.height / 2f))
            canvas.drawImage(Image.makeFromBitmap(src), 0f, 0f)
        }
        return dst
    }

    private fun applyCrop(src: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val x = (left * src.width).toInt().coerceIn(0, src.width)
        val y = (top * src.height).toInt().coerceIn(0, src.height)
        val w = ((right - left) * src.width).toInt().coerceAtLeast(1)
        val h = ((bottom - top) * src.height).toInt().coerceAtLeast(1)
        val subset = Bitmap()
        src.extractSubset(subset, org.jetbrains.skia.IRect.makeXYWH(x, y, w, h))
        return subset
    }

    private fun applyTilt(src: Bitmap, angle: Float, scale: Float): Bitmap {
        if (angle == 0f) return src
        val cx = src.width / 2f
        val cy = src.height / 2f
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        Canvas(dst).use { canvas ->
            canvas.concat(Matrix33.makeTranslate(cx, cy))
            canvas.concat(Matrix33.makeScale(scale, scale))
            canvas.concat(Matrix33.makeRotate(angle))
            canvas.concat(Matrix33.makeTranslate(-cx, -cy))
            canvas.drawImage(Image.makeFromBitmap(src), 0f, 0f)
        }
        return dst
    }

    // ── Colour filters ────────────────────────────────────────────────────

    private fun applyColorMatrix(src: Bitmap, matrix: FloatArray): Bitmap {
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        val paint = Paint().apply {
            colorFilter = ColorFilter.makeMatrix(ColorMatrix(matrix))
        }
        Canvas(dst).use { it.drawImage(Image.makeFromBitmap(src), 0f, 0f, paint) }
        return dst
    }

    private fun applyGamma(src: Bitmap, gamma: Float): Bitmap {
        val brightnessShift = ((1f - gamma) * 50f).coerceIn(-100f, 100f)
        return applyColorMatrix(src, floatArrayOf(
            1f, 0f, 0f, 0f, brightnessShift,
            0f, 1f, 0f, 0f, brightnessShift,
            0f, 0f, 1f, 0f, brightnessShift,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun applyBlur(src: Bitmap, sigma: Float): Bitmap {
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        val paint = Paint().apply {
            imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
        }
        Canvas(dst).use { it.drawImage(Image.makeFromBitmap(src), 0f, 0f, paint) }
        return dst
    }

    private fun applyGlow(src: Bitmap, strength: Float): Bitmap {
        val blurred = applyBlur(src, strength * 6f)
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        Canvas(dst).use { canvas ->
            canvas.drawImage(Image.makeFromBitmap(src), 0f, 0f)
            val paint = Paint().apply { alpha = (strength * 80).toInt().coerceIn(0, 120) }
            canvas.drawImage(Image.makeFromBitmap(blurred), 0f, 0f, paint)
        }
        return dst
    }

    private fun applySharpness(src: Bitmap, value: Float): Bitmap {
        val s = value.coerceIn(-1f, 1f)
        val center = 1f + s * 4f
        val side = -s
        val kernel = floatArrayOf(
            0f, side, 0f,
            side, center, side,
            0f, side, 0f
        )
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        val paint = Paint().apply {
            imageFilter = ImageFilter.makeMatrixConvolution(
                3, 3, kernel, 1f, 0f,
                0, 0, FilterTileMode.CLAMP, true, null, null
            )
        }
        Canvas(dst).use { it.drawImage(Image.makeFromBitmap(src), 0f, 0f, paint) }
        return dst
    }

    // ── Colour matrices ───────────────────────────────────────────────────

    private fun identityMatrix() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun autoContrastMatrix() = floatArrayOf(
        1.2f, 0f,   0f,   0f, -25f,
        0f,   1.2f, 0f,   0f, -25f,
        0f,   0f,   1.2f, 0f, -25f,
        0f,   0f,   0f,   1f,  0f
    )

    private fun autoColorMatrix() = floatArrayOf(
        1.1f, 0f,    0f,   0f, 0f,
        0f,   1.05f, 0f,   0f, 0f,
        0f,   0f,    1.1f, 0f, 0f,
        0f,   0f,    0f,   1f, 0f
    )

    private fun saturationMatrix(saturation: Float): FloatArray {
        val lumR = 0.213f; val lumG = 0.715f; val lumB = 0.072f
        val sr = (1f - saturation) * lumR
        val sg = (1f - saturation) * lumG
        val sb = (1f - saturation) * lumB
        return floatArrayOf(
            sr + saturation, sg, sb, 0f, 0f,
            sr, sg + saturation, sb, 0f, 0f,
            sr, sg, sb + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    private fun highlightsMatrix(v: Float) = floatArrayOf(
        1f, 0f, 0f, 0f, v * 30f,
        0f, 1f, 0f, 0f, v * 30f,
        0f, 0f, 1f, 0f, v * 30f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun shadowsMatrix(v: Float) = floatArrayOf(
        1f + v * 0.3f, 0f, 0f, 0f, 0f,
        0f, 1f + v * 0.3f, 0f, 0f, 0f,
        0f, 0f, 1f + v * 0.3f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun sepiaMatrix(strength: Float): FloatArray {
        val s = strength.coerceIn(0f, 1f)
        val identity = identityMatrix()
        val sepia = floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        return lerp(identity, sepia, s)
    }

    private fun bwMatrix(strength: Float): FloatArray {
        val s = strength.coerceIn(0f, 1f)
        val identity = identityMatrix()
        val bw = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        return lerp(identity, bw, s)
    }

    private fun tintMatrix(r: Float, g: Float, b: Float, strength: Float) = floatArrayOf(
        r * strength + (1f - strength), 0f, 0f, 0f, 0f,
        0f, g * strength + (1f - strength), 0f, 0f, 0f,
        0f, 0f, b * strength + (1f - strength), 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun lomoMatrix(sat: Float, contrast: Float): FloatArray {
        val c = 1f + contrast * 0.5f
        val satM = saturationMatrix(1f + sat * 0.8f)
        val cM = floatArrayOf(
            c, 0f, 0f, 0f, (1f - c) * 128f,
            0f, c, 0f, 0f, (1f - c) * 128f,
            0f, 0f, c, 0f, (1f - c) * 128f,
            0f, 0f, 0f, 1f, 0f
        )
        return multiplyColorMatrices(satM, cM)
    }

    private fun retroluxMatrix(strength: Float) = floatArrayOf(
        1f, 0f, 0f, 0f, strength * 20f,
        0f, 0.9f, 0f, 0f, strength * 10f,
        0f, 0f, 0.8f, 0f, strength * 5f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun finetuneMatrix(
        shadows: Float, highlights: Float, colorTemp: Float,
        brightness: Float, saturation: Float
    ): FloatArray {
        val brightM = floatArrayOf(
            1f, 0f, 0f, 0f, brightness * 50f,
            0f, 1f, 0f, 0f, brightness * 50f,
            0f, 0f, 1f, 0f, brightness * 50f,
            0f, 0f, 0f, 1f, 0f
        )
        val tempM = floatArrayOf(
            1f + colorTemp * 0.2f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - colorTemp * 0.2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val satM = saturationMatrix(1f + saturation)
        val shadowsM = shadowsMatrix(shadows)
        val highlightsM = highlightsMatrix(highlights)
        return multiplyColorMatrices(
            multiplyColorMatrices(multiplyColorMatrices(multiplyColorMatrices(brightM, tempM), satM), shadowsM),
            highlightsM
        )
    }

    private fun lerp(a: FloatArray, b: FloatArray, t: Float): FloatArray =
        FloatArray(a.size) { i -> a[i] * (1f - t) + b[i] * t }

    private fun multiplyColorMatrices(m1: FloatArray, m2: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0..3) {
            for (col in 0..4) {
                result[row * 5 + col] = if (col < 4) {
                    (0..3).sumOf { k -> (m1[row * 5 + k] * m2[k * 5 + col]).toDouble() }.toFloat()
                } else {
                    m1[row * 5 + 4] + (0..3).sumOf { k -> (m1[row * 5 + k] * m2[k * 5 + 4]).toDouble() }.toFloat()
                }
            }
        }
        return result
    }

    // ── Film grain ───────────────────────────────────────────────────────
    private fun boostMatrix(strength: Float): FloatArray {
        val s = strength.coerceIn(0f, 1f)
        val c = 1f + s * 0.15f
        val satM = saturationMatrix(1f + s * 0.8f)
        val cM = floatArrayOf(
            c, 0f, 0f, 0f, (1f - c) * 128f,
            0f, c, 0f, 0f, (1f - c) * 128f,
            0f, 0f, c, 0f, (1f - c) * 128f,
            0f, 0f, 0f, 1f, 0f
        )
        return multiplyColorMatrices(satM, cM)
    }

    private fun filteredBwMatrix(strength: Float): FloatArray {
        val s = strength.coerceIn(0f, 1f)
        val filtered = floatArrayOf(
            0.50f, 0.35f, 0.15f, 0f, 0f,
            0.50f, 0.35f, 0.15f, 0f, 0f,
            0.50f, 0.35f, 0.15f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        return lerp(identityMatrix(), filtered, s)
    }

    private fun applyOrton(src: Bitmap, strength: Float): Bitmap {
        val s = strength.coerceIn(0f, 1f)
        val brightened = applyColorMatrix(src, floatArrayOf(
            1.3f, 0f, 0f, 0f, 20f,
            0f, 1.3f, 0f, 0f, 20f,
            0f, 0f, 1.3f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        val blurred = applyBlur(brightened, s * 8f)
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        Canvas(dst).use { canvas ->
            canvas.drawImage(Image.makeFromBitmap(src), 0f, 0f)
            val paint = Paint().apply {
                blendMode = BlendMode.SCREEN
                alpha = (s * 160).toInt().coerceIn(0, 200)
            }
            canvas.drawImage(Image.makeFromBitmap(blurred), 0f, 0f, paint)
        }
        return dst
    }

    private fun polarizeMatrix(strength: Float): FloatArray {
        val s = strength.coerceIn(0f, 1f)
        val c = 2.5f
        val satM = saturationMatrix(1f + s * 1.2f)
        val cM = floatArrayOf(
            c, 0f, 0f, 0f, (1f - c) * 100f,
            0f, c, 0f, 0f, (1f - c) * 100f,
            0f, 0f, c, 0f, (1f - c) * 100f,
            0f, 0f, 0f, 1f, 0f
        )
        return lerp(identityMatrix(), multiplyColorMatrices(satM, cM), s)
    }

    private fun applyFilmGrain(src: Bitmap, strength: Float): Bitmap {
        if (strength <= 0f) return src
        val tileW = (src.width / 4).coerceAtLeast(4)
        val tileH = (src.height / 4).coerceAtLeast(4)
        val noise = Bitmap().apply { allocN32Pixels(tileW, tileH) }
        val rng = java.util.Random(42L)
        val amp = (strength * 60).toInt().coerceIn(1, 80)
        for (y in 0 until tileH) {
            for (x in 0 until tileW) {
                val v = (128 + (rng.nextInt(amp * 2 + 1) - amp)).coerceIn(0, 255)
                noise.erase(
                    (0x80 shl 24) or (v shl 16) or (v shl 8) or v,
                    org.jetbrains.skia.IRect.makeXYWH(x, y, 1, 1)
                )
            }
        }
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        Canvas(dst).use { canvas ->
            canvas.drawImage(Image.makeFromBitmap(src), 0f, 0f)
            val paint = Paint().apply { blendMode = BlendMode.OVERLAY }
            canvas.drawImageRect(
                Image.makeFromBitmap(noise),
                Rect.makeWH(tileW.toFloat(), tileH.toFloat()),
                Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                paint
            )
        }
        return dst
    }

    // ── Vignette ─────────────────────────────────────────────────────────
    private fun applyVignette(src: Bitmap, strength: Float, size: Float): Bitmap {
        if (strength <= 0f) return src
        val cx = src.width / 2f
        val cy = src.height / 2f
        val radius = maxOf(src.width, src.height) * (0.25f + (1f - size.coerceIn(0f, 1f)) * 0.5f)
        val edgeAlpha = (strength * 220).toInt().coerceIn(0, 230)
        val shader = Shader.makeRadialGradient(
            cx, cy, radius,
            intArrayOf(0x00000000, edgeAlpha shl 24),
            null
        )
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        Canvas(dst).use { canvas ->
            canvas.drawImage(Image.makeFromBitmap(src), 0f, 0f)
            val paint = Paint().apply { this.shader = shader }
            canvas.drawRect(Rect.makeWH(src.width.toFloat(), src.height.toFloat()), paint)
        }
        return dst
    }

    // ── Red-eye removal ──────────────────────────────────────────────────
    private fun applyRedEye(src: Bitmap, regions: List<RedEyeRegion>): Bitmap {
        if (regions.isEmpty()) return src
        val dst = Bitmap().apply { allocN32Pixels(src.width, src.height) }
        Canvas(dst).use { it.drawImage(Image.makeFromBitmap(src), 0f, 0f) }
        for (region in regions) {
            if (region.radius <= 0f) continue
            val cx = (region.x * src.width).toInt()
            val cy = (region.y * src.height).toInt()
            val rad = (region.radius * maxOf(src.width, src.height)).toInt().coerceAtLeast(1)
            for (py in (cy - rad).coerceAtLeast(0)..(cy + rad).coerceAtMost(dst.height - 1)) {
                for (px in (cx - rad).coerceAtLeast(0)..(cx + rad).coerceAtMost(dst.width - 1)) {
                    if ((px - cx).toLong() * (px - cx) + (py - cy).toLong() * (py - cy) > rad.toLong() * rad) continue
                    val color = dst.getColor(px, py)
                    val a = (color ushr 24) and 0xFF
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF
                    if (r > g * 1.4f && r > b * 1.4f && r > 80) {
                        val newR = ((g + b) / 2).coerceIn(0, 255)
                        dst.erase(
                            (a shl 24) or (newR shl 16) or (g shl 8) or b,
                            org.jetbrains.skia.IRect.makeXYWH(px, py, 1, 1)
                        )
                    }
                }
            }
        }
        return dst
    }

    // ── Export ────────────────────────────────────────────────────────────

    fun exportToFile(filePath: String, editStack: List<Photo_edits>, outputPath: String, quality: Int = 90) {
        val bitmap = loadAndApplyEdits(filePath, editStack) ?: return
        val encoded = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.JPEG, quality) ?: return
        File(outputPath).writeBytes(encoded.bytes)
    }
}

private fun Image.toBitmap(): Bitmap? = runCatching {
    val bmp = Bitmap()
    bmp.allocN32Pixels(width, height)
    val canvas = Canvas(bmp)
    canvas.drawImage(this, 0f, 0f)
    bmp
}.getOrNull()
