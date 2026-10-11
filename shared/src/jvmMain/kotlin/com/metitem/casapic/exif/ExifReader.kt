package com.metitem.casapic.exif

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.xmp.XmpDirectory
import java.io.File
import java.util.Date

data class PhotoExif(
    val width: Int?,
    val height: Int?,
    val captureDateMillis: Long?,
    val modifiedDateMillis: Long?,
    val cameraMake: String?,
    val cameraModel: String?,
    val focalLength: Double?,
    val fNumber: Double?,
    val iso: Int?,
    val exposureTime: String?,
    val flash: Int?,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsAltitude: Double?,
    val orientation: Int?,
    val jpegQuality: Int?,
    val uniqueId: String?,
    val description: String?,
    val iptcCopyright: String?,
    val iptcAuthor: String?,
    val iptcHeadline: String?,
    val iptcKeywords: String?,
    val xmpRating: Int?,
    val xmpLabel: String?,
    val xmpSubject: String?
)

object ExifReader {
    fun read(file: File): PhotoExif {
        return try {
            val metadata = ImageMetadataReader.readMetadata(file)

            val exif0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            val exifSub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
            val iptc = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            val xmp = metadata.getFirstDirectoryOfType(XmpDirectory::class.java)
            val jpeg = metadata.getFirstDirectoryOfType(JpegDirectory::class.java)

            val width = jpeg?.getInteger(JpegDirectory.TAG_IMAGE_WIDTH)
                ?: exifSub?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
                ?: exif0?.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH)

            val height = jpeg?.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT)
                ?: exifSub?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
                ?: exif0?.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT)

            val captureDate: Date? = exifSub?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                ?: exif0?.getDate(ExifIFD0Directory.TAG_DATETIME)

            val focalLength = exifSub?.getRational(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)?.toDouble()
            val fNumber = exifSub?.getRational(ExifSubIFDDirectory.TAG_FNUMBER)?.toDouble()
            val iso = exifSub?.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
            val exposureTime = exifSub?.getRational(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)?.let {
                if (it.denominator == 1L) "${it.numerator}s" else "${it.numerator}/${it.denominator}s"
            }
            val flash = exifSub?.getInteger(ExifSubIFDDirectory.TAG_FLASH)
            val orientation = exif0?.getInteger(ExifIFD0Directory.TAG_ORIENTATION)

            val gpsRef = gps?.let {
                val lat = runCatching { it.geoLocation?.latitude }.getOrNull()
                val lng = runCatching { it.geoLocation?.longitude }.getOrNull()
                val alt = it.getRational(GpsDirectory.TAG_ALTITUDE)?.toDouble()
                Triple(lat, lng, alt)
            }

            val iptcKeywordsList = iptc?.getStringArray(IptcDirectory.TAG_KEYWORDS)
            val iptcKeywords = iptcKeywordsList?.joinToString(";")
            val iptcAuthor = iptc?.getString(IptcDirectory.TAG_BY_LINE)
            val iptcCopyright = iptc?.getString(IptcDirectory.TAG_COPYRIGHT_NOTICE)
            val iptcHeadline = iptc?.getString(IptcDirectory.TAG_HEADLINE)

            val xmpProps = xmp?.xmpProperties
            val xmpRating = xmpProps?.get("xmp:Rating")?.toIntOrNull()
            val xmpLabel = xmpProps?.get("xmp:Label")
            val xmpSubjectRaw = xmpProps?.get("dc:subject")

            PhotoExif(
                width = width,
                height = height,
                captureDateMillis = captureDate?.time,
                modifiedDateMillis = file.lastModified(),
                cameraMake = exif0?.getString(ExifIFD0Directory.TAG_MAKE)?.trim(),
                cameraModel = exif0?.getString(ExifIFD0Directory.TAG_MODEL)?.trim(),
                focalLength = focalLength,
                fNumber = fNumber,
                iso = iso,
                exposureTime = exposureTime,
                flash = flash,
                gpsLatitude = gpsRef?.first,
                gpsLongitude = gpsRef?.second,
                gpsAltitude = gpsRef?.third,
                orientation = orientation,
                jpegQuality = null,
                uniqueId = exifSub?.getString(ExifSubIFDDirectory.TAG_IMAGE_UNIQUE_ID),
                description = exif0?.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)?.trim(),
                iptcCopyright = iptcCopyright,
                iptcAuthor = iptcAuthor,
                iptcHeadline = iptcHeadline,
                iptcKeywords = iptcKeywords,
                xmpRating = xmpRating,
                xmpLabel = xmpLabel,
                xmpSubject = xmpSubjectRaw
            )
        } catch (_: Exception) {
            PhotoExif(
                width = null, height = null, captureDateMillis = null,
                modifiedDateMillis = file.lastModified(),
                cameraMake = null, cameraModel = null, focalLength = null,
                fNumber = null, iso = null, exposureTime = null, flash = null,
                gpsLatitude = null, gpsLongitude = null, gpsAltitude = null,
                orientation = null, jpegQuality = null, uniqueId = null,
                description = null, iptcCopyright = null, iptcAuthor = null,
                iptcHeadline = null, iptcKeywords = null,
                xmpRating = null, xmpLabel = null, xmpSubject = null
            )
        }
    }
}
