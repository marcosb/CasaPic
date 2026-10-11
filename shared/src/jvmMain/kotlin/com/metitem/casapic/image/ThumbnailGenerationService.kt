package com.metitem.casapic.image

import com.metitem.casapic.db.CasaPicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.io.File

sealed class ThumbnailResult {
    data class Generated(val photoId: Long, val path: String) : ThumbnailResult()
    data class Failed(val photoId: Long, val cause: Throwable) : ThumbnailResult()
}

class ThumbnailGenerationService(private val db: CasaPicDatabase) {

    private val thumbDir: File by lazy {
        File(System.getProperty("user.home"), ".casapic/cache/thumbs").also { it.mkdirs() }
    }

    fun generatePending(): Flow<ThumbnailResult> = flow {
        while (true) {
            val batch = db.casaPicQueries.getPhotosWithNullThumbnail().executeAsList()
            if (batch.isEmpty()) break
            for (row in batch) {
                val result = generateThumbnail(row.id, row.file_path)
                emit(result)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun generateThumbnailForPhoto(photoId: Long, filePath: String): ThumbnailResult =
        generateThumbnail(photoId, filePath)

    private fun generateThumbnail(photoId: Long, filePath: String): ThumbnailResult {
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) return ThumbnailResult.Failed(photoId, NoSuchFileException(file))

            val bytes = file.readBytes()
            val image = Image.makeFromEncoded(bytes)

            val (tw, th) = scaledDimensions(image.width, image.height, 512)

            val thumbBitmap = Bitmap()
            thumbBitmap.allocN32Pixels(tw, th)
            val canvas = Canvas(thumbBitmap)
            val paint = Paint()
            canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(tw.toFloat(), th.toFloat()),
                paint
            )

            val thumbImage = Image.makeFromBitmap(thumbBitmap)
            val data = thumbImage.encodeToData(EncodedImageFormat.JPEG, 85)
                ?: error("Failed to encode thumbnail for $filePath")

            val outFile = File(thumbDir, "$photoId.jpg")
            outFile.writeBytes(data.bytes)

            db.casaPicQueries.updatePhotoThumbnail(outFile.absolutePath, photoId)

            thumbBitmap.close()
            canvas.close()
            image.close()
            thumbImage.close()

            ThumbnailResult.Generated(photoId, outFile.absolutePath)
        }.getOrElse { ThumbnailResult.Failed(photoId, it) }
    }

    private fun scaledDimensions(srcW: Int, srcH: Int, maxDim: Int): Pair<Int, Int> {
        if (srcW <= maxDim && srcH <= maxDim) return srcW to srcH
        return if (srcW > srcH) {
            maxDim to (srcH * maxDim / srcW)
        } else {
            (srcW * maxDim / srcH) to maxDim
        }
    }
}
