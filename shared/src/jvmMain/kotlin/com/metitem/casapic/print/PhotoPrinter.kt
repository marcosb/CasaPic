package com.metitem.casapic.print

import com.metitem.casapic.db.Photos
import com.metitem.casapic.model.PrintFitMode
import com.metitem.casapic.model.PrintLayout
import com.metitem.casapic.model.PrintOptions
import com.metitem.casapic.model.PrintTextLabel
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

object PhotoPrinter {

    // DPI assumed for letter page (8.5 × 11 in)
    private const val DPI = 150f

    fun print(photos: List<Photos>, options: PrintOptions) {
        val job = PrinterJob.getPrinterJob()
        job.jobName = "CasaPic Print"
        val pages = buildPages(photos, options)
        job.setPrintable { graphics, pageFormat, pageIndex ->
            if (pageIndex >= pages.size) return@setPrintable Printable.NO_SUCH_PAGE
            val g = graphics as java.awt.Graphics2D
            val pageW = pageFormat.imageableWidth.toInt()
            val pageH = pageFormat.imageableHeight.toInt()
            val ox = pageFormat.imageableX.toInt()
            val oy = pageFormat.imageableY.toInt()
            val surface = pages[pageIndex]
            val awtImage = surfaceToAwtImage(surface, pageW, pageH)
            g.drawImage(awtImage, ox, oy, null)
            Printable.PAGE_EXISTS
        }
        if (job.printDialog()) {
            job.print()
        }
    }

    private fun buildPages(photos: List<Photos>, options: PrintOptions): List<Surface> {
        val pageWIn = 8.5f; val pageHIn = 11f
        val marginIn = 0.5f
        val usableW = (pageWIn - marginIn * 2) * DPI
        val usableH = (pageHIn - marginIn * 2) * DPI
        val pageW = (pageWIn * DPI).toInt()
        val pageH = (pageHIn * DPI).toInt()

        val cols: Int
        val rows: Int
        val cellW: Float
        val cellH: Float

        when (options.layout) {
            PrintLayout.WALLET -> { cols = 3; rows = 3 }
            PrintLayout.SIZE_3_5X5 -> { cols = 2; rows = 2 }
            PrintLayout.SIZE_4X6 -> { cols = 1; rows = 2 }
            PrintLayout.FULL_PAGE -> { cols = 1; rows = 1 }
            PrintLayout.CONTACT_SHEET -> {
                cols = options.contactSheetColumns
                rows = options.contactSheetColumns  // square-ish
            }
        }
        val gutter = if (options.layout == PrintLayout.FULL_PAGE) 0f else 8f * DPI / 72f
        cellW = (usableW - (cols - 1) * gutter) / cols
        cellH = (usableH - (rows - 1) * gutter) / rows

        val cellsPerPage = cols * rows
        val totalCopies = photos.size * options.copiesPerPhoto
        val pageCount = if (totalCopies == 0) 1
            else ceil(totalCopies.toDouble() / cellsPerPage).toInt()

        val expandedPhotos = photos.flatMap { p -> List(options.copiesPerPhoto) { p } }

        return List(pageCount) { pageIdx ->
            val surface = Surface.makeRasterN32Premul(pageW, pageH)
            val canvas = surface.canvas
            canvas.clear(0xFFFFFFFF.toInt())

            val pad = marginIn * DPI
            for (cellIdx in 0 until cellsPerPage) {
                val photoIdx = pageIdx * cellsPerPage + cellIdx
                if (photoIdx >= expandedPhotos.size) break
                val photo = expandedPhotos[photoIdx]

                val col = cellIdx % cols
                val row = cellIdx / cols
                val cellX = pad + col * (cellW + gutter)
                val cellY = pad + row * (cellH + gutter)
                val cellRect = Rect.makeXYWH(cellX, cellY, cellW, cellH)

                drawPhotoCell(canvas, photo, cellRect, options)
            }
            surface
        }
    }

    private fun drawPhotoCell(canvas: Canvas, photo: Photos, cell: Rect, options: PrintOptions) {
        val bitmap = loadBitmap(photo) ?: run {
            // Gray placeholder
            val p = Paint().apply { color4f = Color4f(0.85f, 0.85f, 0.85f, 1f) }
            canvas.drawRect(cell, p)
            return
        }

        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val cellAspect = cell.width / cell.height
        val imgAspect = srcW / srcH

        val destRect = when (options.fitMode) {
            PrintFitMode.SHRINK_TO_FIT -> {
                if (imgAspect > cellAspect) {
                    val h = cell.width / imgAspect
                    Rect.makeXYWH(cell.left, cell.top + (cell.height - h) / 2f, cell.width, h)
                } else {
                    val w = cell.height * imgAspect
                    Rect.makeXYWH(cell.left + (cell.width - w) / 2f, cell.top, w, cell.height)
                }
            }
            PrintFitMode.CROP_TO_FIT -> cell
        }

        val srcRect = when (options.fitMode) {
            PrintFitMode.SHRINK_TO_FIT -> Rect.makeXYWH(0f, 0f, srcW, srcH)
            PrintFitMode.CROP_TO_FIT -> {
                if (imgAspect > cellAspect) {
                    val cropW = srcH * cellAspect
                    Rect.makeXYWH((srcW - cropW) / 2f, 0f, cropW, srcH)
                } else {
                    val cropH = srcW / cellAspect
                    Rect.makeXYWH(0f, (srcH - cropH) / 2f, srcW, cropH)
                }
            }
        }

        val image = Image.makeFromBitmap(bitmap)
        canvas.drawImageRect(image, srcRect, destRect)

        if (options.showBorder) {
            val border = Paint().apply {
                color4f = Color4f(0f, 0f, 0f, 1f)
                strokeWidth = 1f
                isAntiAlias = false
                mode = org.jetbrains.skia.PaintMode.STROKE
            }
            canvas.drawRect(destRect, border)
        }

        if (options.textLabel != PrintTextLabel.NONE) {
            val label = when (options.textLabel) {
                PrintTextLabel.FILENAME -> photo.file_name
                PrintTextLabel.DATE -> photo.capture_date_millis?.let { formatMillis(it) } ?: photo.file_name
                PrintTextLabel.CAPTION -> photo.caption ?: photo.file_name
                PrintTextLabel.NONE -> null
            }
            if (label != null) {
                val fontSize = (cell.height * 0.04f).coerceIn(8f, 14f)
                val font = Font(null, fontSize)
                val textPaint = Paint().apply { color4f = Color4f(0.1f, 0.1f, 0.1f, 1f) }
                canvas.drawString(label, cell.left + 2f, cell.bottom + fontSize + 2f, font, textPaint)
            }
        }
    }

    private fun loadBitmap(photo: Photos): Bitmap? {
        val path = photo.thumbnail_path ?: photo.file_path
        return runCatching {
            val bytes = File(path).readBytes()
            val img = Image.makeFromEncoded(bytes)
            val bmp = Bitmap()
            bmp.allocN32Pixels(img.width, img.height)
            val canvas = Canvas(bmp)
            canvas.drawImage(img, 0f, 0f)
            bmp
        }.getOrNull()
    }

    private fun surfaceToAwtImage(surface: Surface, w: Int, h: Int): java.awt.image.BufferedImage {
        val encoded = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
            ?: return java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val bis = java.io.ByteArrayInputStream(encoded.bytes)
        return javax.imageio.ImageIO.read(bis)
            ?: java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
    }

    private fun formatMillis(ms: Long): String = runCatching {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        "%d/%02d/%02d".format(cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }.getOrElse { "" }
}
