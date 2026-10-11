package com.metitem.casapic.scanner

import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.exif.ExifReader
import com.metitem.casapic.metadata.MetadataFileService
import com.metitem.casapic.metadata.PicasaImporter
import com.metitem.casapic.metadata.PicasaIniParser
import com.metitem.casapic.model.ScanMode
import com.metitem.casapic.model.ScanProgress
import com.metitem.casapic.model.WatchEvent
import com.metitem.casapic.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Clock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private const val BATCH_SIZE = 100

class FolderScanner(
    private val db: CasaPicDatabase,
    private val settings: SettingsRepository
) {
    val faceDetectionQueue: Channel<Pair<Long, String>> = Channel(Channel.UNLIMITED)
    val fingerprintQueue: Channel<Pair<Long, String>> = Channel(Channel.UNLIMITED)

    fun scanFolder(folderPath: String): Flow<ScanProgress> = flow {
        val root = Paths.get(folderPath)
        if (!Files.isDirectory(root)) return@flow

        val folderName = root.fileName?.toString() ?: folderPath
        val extensions = settings.getEnabledExtensions()
        val now = Clock.System.now().toEpochMilliseconds()

        val existingFolder = db.casaPicQueries.getFolderByPath(folderPath).executeAsOneOrNull()
        if (existingFolder == null) {
            db.casaPicQueries.insertFolder(folderPath, folderName, ScanMode.ALL, 1L, now)
        }
        val folderId = db.casaPicQueries.getFolderByPath(folderPath).executeAsOneOrNull()?.id
            ?: return@flow

        // Parse sidecar files before iterating — applied to DB after EXIF scan completes.
        val pbMeta = MetadataFileService.read(folderPath)
        val ini    = PicasaIniParser.parse(File(folderPath, "picasa.ini"))

        val allFiles = mutableListOf<File>()
        collectFilesRecursive(root.toFile(), extensions, allFiles)

        var added = 0
        var updated = 0
        var errors = 0

        for (batch in allFiles.chunked(BATCH_SIZE)) {
            val batchResults = mutableListOf<ScanProgress>()
            val newPhotoQueue = mutableListOf<Pair<Long, String>>()

            db.casaPicQueries.transaction {
                for (file in batch) {
                    try {
                        val existingModified = db.casaPicQueries
                            .getModifiedDateMillis(file.absolutePath)
                            .executeAsOneOrNull()
                            ?.modified_date_millis

                        if (existingModified != null && existingModified == file.lastModified()) {
                            val photoId = db.casaPicQueries
                                .getPhotoIdByPath(file.absolutePath)
                                .executeAsOneOrNull()
                            if (photoId != null) {
                                batchResults += ScanProgress.PhotoUpdated(photoId)
                            }
                        } else if (existingModified != null) {
                            val exif = ExifReader.read(file)
                            db.casaPicQueries.updatePhotoOnRescan(
                                file_size = file.length(),
                                width = exif.width?.toLong(),
                                height = exif.height?.toLong(),
                                capture_date_millis = exif.captureDateMillis,
                                modified_date_millis = exif.modifiedDateMillis,
                                camera_make = exif.cameraMake,
                                camera_model = exif.cameraModel,
                                focal_length = exif.focalLength,
                                f_number = exif.fNumber,
                                iso = exif.iso?.toLong(),
                                exposure_time = exif.exposureTime,
                                flash = exif.flash?.toLong(),
                                gps_latitude = exif.gpsLatitude,
                                gps_longitude = exif.gpsLongitude,
                                gps_altitude = exif.gpsAltitude,
                                orientation = exif.orientation?.toLong(),
                                jpeg_quality = exif.jpegQuality?.toLong(),
                                unique_id = exif.uniqueId,
                                description = exif.description,
                                caption = null,
                                iptc_copyright = exif.iptcCopyright,
                                iptc_author = exif.iptcAuthor,
                                iptc_headline = exif.iptcHeadline,
                                iptc_keywords = exif.iptcKeywords,
                                xmp_rating = exif.xmpRating?.toLong(),
                                xmp_label = exif.xmpLabel,
                                xmp_subject = exif.xmpSubject,
                                file_path = file.absolutePath
                            )
                            val photoId = db.casaPicQueries
                                .getPhotoIdByPath(file.absolutePath)
                                .executeAsOne()
                            updated++
                            batchResults += ScanProgress.PhotoUpdated(photoId)
                        } else {
                            val exif = ExifReader.read(file)
                            db.casaPicQueries.insertPhoto(
                                file_path = file.absolutePath,
                                folder_id = folderId,
                                file_name = file.name,
                                file_size = file.length(),
                                width = exif.width?.toLong(),
                                height = exif.height?.toLong(),
                                capture_date_millis = exif.captureDateMillis,
                                modified_date_millis = exif.modifiedDateMillis,
                                imported_date_millis = now,
                                camera_make = exif.cameraMake,
                                camera_model = exif.cameraModel,
                                focal_length = exif.focalLength,
                                f_number = exif.fNumber,
                                iso = exif.iso?.toLong(),
                                exposure_time = exif.exposureTime,
                                flash = exif.flash?.toLong(),
                                gps_latitude = exif.gpsLatitude,
                                gps_longitude = exif.gpsLongitude,
                                gps_altitude = exif.gpsAltitude,
                                orientation = exif.orientation?.toLong(),
                                jpeg_quality = exif.jpegQuality?.toLong(),
                                unique_id = exif.uniqueId,
                                description = exif.description,
                                caption = null,
                                iptc_copyright = exif.iptcCopyright,
                                iptc_author = exif.iptcAuthor,
                                iptc_headline = exif.iptcHeadline,
                                iptc_keywords = exif.iptcKeywords,
                                xmp_rating = exif.xmpRating?.toLong(),
                                xmp_label = exif.xmpLabel,
                                xmp_subject = exif.xmpSubject,
                                star_rating = 0,
                                is_hidden = 0,
                                block_upload = 0
                            )
                            val photoId = db.casaPicQueries
                                .getPhotoIdByPath(file.absolutePath)
                                .executeAsOne()
                            added++
                            insertKeywordsAsTags(photoId, exif.iptcKeywords, exif.xmpSubject)
                            batchResults += ScanProgress.PhotoAdded(photoId, file.absolutePath)
                            newPhotoQueue += Pair(photoId, file.absolutePath)
                        }
                    } catch (e: Exception) {
                        errors++
                        batchResults += ScanProgress.Error(file.absolutePath, e)
                    }
                }
            }

            // Emit progress and enqueue outside the transaction
            for (progress in batchResults) emit(progress)
            for (entry in newPhotoQueue) {
                faceDetectionQueue.trySend(entry)
                fingerprintQueue.trySend(entry)
            }
            emit(ScanProgress.BatchComplete(added, updated))
        }

        // Apply sidecar metadata AFTER all photos are in the DB.
        // casapic.pb wins on conflict; picasa.ini is secondary (INSERT OR IGNORE / null-check).
        if (pbMeta != null) MetadataFileService.applyToDb(pbMeta, db, folderId)
        if (ini != null)    PicasaImporter.importToDb(ini, db, folderId)
    }.flowOn(Dispatchers.IO)

    fun processEvent(event: WatchEvent): Flow<ScanProgress> = flow {
        val now = Clock.System.now().toEpochMilliseconds()
        when (event) {
            is WatchEvent.Created -> {
                val file = File(event.path)
                if (!file.isFile) return@flow
                if (".${file.extension.lowercase()}" !in settings.getEnabledExtensions()) return@flow

                val exif = ExifReader.read(file)
                val folderId = resolveFolderId(file.parent, now)

                db.casaPicQueries.insertPhoto(
                    file_path = file.absolutePath,
                    folder_id = folderId,
                    file_name = file.name,
                    file_size = file.length(),
                    width = exif.width?.toLong(),
                    height = exif.height?.toLong(),
                    capture_date_millis = exif.captureDateMillis,
                    modified_date_millis = exif.modifiedDateMillis,
                    imported_date_millis = now,
                    camera_make = exif.cameraMake,
                    camera_model = exif.cameraModel,
                    focal_length = exif.focalLength,
                    f_number = exif.fNumber,
                    iso = exif.iso?.toLong(),
                    exposure_time = exif.exposureTime,
                    flash = exif.flash?.toLong(),
                    gps_latitude = exif.gpsLatitude,
                    gps_longitude = exif.gpsLongitude,
                    gps_altitude = exif.gpsAltitude,
                    orientation = exif.orientation?.toLong(),
                    jpeg_quality = exif.jpegQuality?.toLong(),
                    unique_id = exif.uniqueId,
                    description = exif.description,
                    caption = null,
                    iptc_copyright = exif.iptcCopyright,
                    iptc_author = exif.iptcAuthor,
                    iptc_headline = exif.iptcHeadline,
                    iptc_keywords = exif.iptcKeywords,
                    xmp_rating = exif.xmpRating?.toLong(),
                    xmp_label = exif.xmpLabel,
                    xmp_subject = exif.xmpSubject,
                    star_rating = 0,
                    is_hidden = 0,
                    block_upload = 0
                )
                val photoId = db.casaPicQueries
                    .getPhotoIdByPath(file.absolutePath)
                    .executeAsOneOrNull() ?: return@flow
                insertKeywordsAsTags(photoId, exif.iptcKeywords, exif.xmpSubject)
                faceDetectionQueue.trySend(Pair(photoId, file.absolutePath))
                fingerprintQueue.trySend(Pair(photoId, file.absolutePath))
                emit(ScanProgress.PhotoAdded(photoId, file.absolutePath))
            }

            is WatchEvent.Modified -> {
                val file = File(event.path)
                if (!file.isFile) return@flow
                val existing = db.casaPicQueries.getPhotoByPath(file.absolutePath)
                    .executeAsOneOrNull() ?: return@flow
                if (existing.modified_date_millis == file.lastModified()) return@flow

                val exif = ExifReader.read(file)
                db.casaPicQueries.updatePhotoOnRescan(
                    file_size = file.length(),
                    width = exif.width?.toLong(),
                    height = exif.height?.toLong(),
                    capture_date_millis = exif.captureDateMillis,
                    modified_date_millis = exif.modifiedDateMillis,
                    camera_make = exif.cameraMake,
                    camera_model = exif.cameraModel,
                    focal_length = exif.focalLength,
                    f_number = exif.fNumber,
                    iso = exif.iso?.toLong(),
                    exposure_time = exif.exposureTime,
                    flash = exif.flash?.toLong(),
                    gps_latitude = exif.gpsLatitude,
                    gps_longitude = exif.gpsLongitude,
                    gps_altitude = exif.gpsAltitude,
                    orientation = exif.orientation?.toLong(),
                    jpeg_quality = exif.jpegQuality?.toLong(),
                    unique_id = exif.uniqueId,
                    description = exif.description,
                    caption = null,
                    iptc_copyright = exif.iptcCopyright,
                    iptc_author = exif.iptcAuthor,
                    iptc_headline = exif.iptcHeadline,
                    iptc_keywords = exif.iptcKeywords,
                    xmp_rating = exif.xmpRating?.toLong(),
                    xmp_label = exif.xmpLabel,
                    xmp_subject = exif.xmpSubject,
                    file_path = file.absolutePath
                )
                emit(ScanProgress.PhotoUpdated(existing.id))
            }

            is WatchEvent.Deleted -> {
                db.casaPicQueries.deletePhotoByPath(event.path)
                emit(ScanProgress.PhotoDeleted(event.path))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun insertKeywordsAsTags(photoId: Long, iptcKeywords: String?, xmpSubject: String?) {
        val allKeywords = mutableSetOf<String>()
        iptcKeywords?.split(";")?.forEach { allKeywords += it.trim() }
        xmpSubject?.split(";")?.forEach { allKeywords += it.trim() }
        for (keyword in allKeywords) {
            if (keyword.isEmpty()) continue
            db.casaPicQueries.insertOrIgnoreTag(keyword)
            val tagId = db.casaPicQueries.getTagByName(keyword).executeAsOneOrNull()?.id
                ?: continue
            db.casaPicQueries.insertPhotoTag(photoId, tagId)
        }
    }

    private fun resolveFolderId(parentPath: String, now: Long): Long? {
        val existing = db.casaPicQueries.getFolderByPath(parentPath).executeAsOneOrNull()
        if (existing != null) return existing.id
        val folderName = File(parentPath).name
        db.casaPicQueries.insertFolder(parentPath, folderName, ScanMode.ALL, 1L, now)
        return db.casaPicQueries.getFolderByPath(parentPath).executeAsOneOrNull()?.id
    }

    private fun collectFilesRecursive(dir: File, extensions: Set<String>, result: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> collectFilesRecursive(file, extensions, result)
                file.isFile -> if (".${file.extension.lowercase()}" in extensions) result += file
            }
        }
    }
}
