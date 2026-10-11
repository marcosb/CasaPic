package com.metitem.casapic.print

import com.metitem.casapic.db.Photos
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Font
import org.jetbrains.skia.Typeface
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File
import javax.imageio.ImageIO

object ContactSheetPrinter {

    fun print(photos: List<Photos>, columns: Int = 4, title: String = "Contact Sheet") {
        val job = PrinterJob.getPrinterJob()
        job.jobName = title
        job.setPrintable { graphics, pageFormat, pageIndex ->
            if (pageIndex > 0) return@setPrintable Printable.NO_SUCH_PAGE

            val g = graphics as java.awt.Graphics2D
            val width = pageFormat.imageableWidth.toInt()
            val height = pageFormat.imageableHeight.toInt()
            val x0 = pageFormat.imageableX.toInt()
            val y0 = pageFormat.imageableY.toInt()

            val rows = Math.ceil(photos.size.toDouble() / columns).toInt()
            val cellW = width / columns
            val cellH = height / (rows.coerceAtLeast(1))
            val padding = 4
            val labelHeight = 12

            photos.forEachIndexed { idx, photo ->
                val col = idx % columns
                val row = idx / columns
                val cellX = x0 + col * cellW
                val cellY = y0 + row * cellH

                val imgFile = File(photo.thumbnail_path ?: photo.file_path)
                if (imgFile.exists()) {
                    runCatching {
                        val img = ImageIO.read(imgFile)
                        if (img != null) {
                            val imgW = cellW - padding * 2
                            val imgH = cellH - labelHeight - padding * 2
                            val scale = minOf(imgW.toDouble() / img.width, imgH.toDouble() / img.height)
                            val drawW = (img.width * scale).toInt()
                            val drawH = (img.height * scale).toInt()
                            val imgX = cellX + padding + (imgW - drawW) / 2
                            val imgY = cellY + padding
                            g.drawImage(img, imgX, imgY, drawW, drawH, null)
                        }
                    }
                }

                g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 8)
                g.color = java.awt.Color.BLACK
                val label = photo.file_name.take(20)
                g.drawString(label, cellX + padding, cellY + cellH - padding)
            }

            Printable.PAGE_EXISTS
        }

        if (job.printDialog()) {
            job.print()
        }
    }

    fun exportToFile(photos: List<Photos>, outputPath: String, columns: Int = 4) {
        val pageW = 2480  // A4 at 300dpi
        val pageH = 3508
        val surface = Surface.makeRasterN32Premul(pageW, pageH)
        val canvas = surface.canvas
        val paint = Paint()

        canvas.clear(0xFFFFFFFF.toInt())

        val rows = Math.ceil(photos.size.toDouble() / columns).toInt().coerceAtLeast(1)
        val cellW = pageW / columns
        val cellH = pageH / rows
        val padding = 8
        val labelH = 20

        photos.forEachIndexed { idx, photo ->
            val col = idx % columns
            val row = idx / columns
            val cellX = col * cellW
            val cellY = row * cellH

            val imgFile = File(photo.thumbnail_path ?: photo.file_path)
            if (imgFile.exists()) {
                runCatching {
                    val bytes = imgFile.readBytes()
                    val img = Image.makeFromEncoded(bytes)
                    val imgW = (cellW - padding * 2).toFloat()
                    val imgH = (cellH - labelH - padding * 2).toFloat()
                    val scale = minOf(imgW / img.width, imgH / img.height)
                    val drawW = img.width * scale
                    val drawH = img.height * scale
                    val imgX = cellX + padding + (imgW - drawW) / 2
                    val imgY = cellY.toFloat() + padding
                    canvas.drawImageRect(
                        img,
                        Rect.makeXYWH(imgX, imgY, drawW, drawH)
                    )
                }
            }
        }

        val image = surface.makeImageSnapshot()
        val data = image.encodeToData(EncodedImageFormat.JPEG, 90)
        if (data != null) {
            File(outputPath).writeBytes(data.bytes)
        }
    }
}
