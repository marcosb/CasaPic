package com.metitem.casapic.metadata

import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.model.AlbumKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MetadataFileService {

    private const val FILE_NAME = "casapic.pb"

    fun read(folderPath: String): FolderMetadata? {
        val file = File(folderPath, FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            FolderMetadata.ADAPTER.decode(file.readBytes())
        }.getOrNull()
    }

    fun write(folderPath: String, db: CasaPicDatabase, folderId: Long) {
        val existing = read(folderPath) ?: FolderMetadata()
        val updated = buildFolderMetadata(db, folderId, existing)
        writeAtomic(folderPath, updated)
    }

    private fun writeAtomic(folderPath: String, metadata: FolderMetadata) {
        val dir = File(folderPath)
        if (!dir.isDirectory) return
        val target = File(dir, FILE_NAME)
        val tmp = File(dir, "$FILE_NAME.tmp")
        runCatching {
            tmp.writeBytes(FolderMetadata.ADAPTER.encode(metadata))
            tmp.renameTo(target)
        }
    }

    private fun buildFolderMetadata(
        db: CasaPicDatabase,
        folderId: Long,
        existing: FolderMetadata
    ): FolderMetadata {
        val photoRows = db.casaPicQueries.getPhotosForFolder(
            folderId = folderId,
            showHidden = 1L,
            limit = Long.MAX_VALUE,
            offset = 0L
        ).executeAsList()

        val photoEntries = photoRows.mapNotNull { photo ->
            val hasEdits  = db.casaPicQueries.getActiveEditsForPhoto(photo.id).executeAsList().isNotEmpty()
            val hasFaces  = db.casaPicQueries.getFaceDetectionsForPhoto(photo.id).executeAsList().isNotEmpty()
            val hasTags   = photo.iptc_keywords != null || photo.xmp_subject != null
            val hasAlbums = db.casaPicQueries.getPhotosInAlbum(photo.id).executeAsList().isNotEmpty()
            val nonDefault = photo.star_rating != 0L
                || photo.caption != null
                || photo.is_hidden != 0L
                || photo.block_upload != 0L
                || hasEdits || hasFaces || hasTags || hasAlbums
            if (!nonDefault) return@mapNotNull null

            val editRows    = db.casaPicQueries.getActiveEditsForPhoto(photo.id).executeAsList()
            val faceRows    = db.casaPicQueries.getFaceDetectionsForPhoto(photo.id).executeAsList()
            val albumPhotos = db.casaPicQueries.getPhotosInAlbum(photo.id).executeAsList()

            val edits = editRows.mapNotNull { row ->
                runCatching { PhotoEdit.ADAPTER.decode(row.edit_proto) }.getOrNull()
            }

            val faceEntries = faceRows.map { f ->
                FaceEntry(
                    contact_id = f.face_group_id?.toString() ?: "",
                    left = f.box_left.toFloat(),
                    top = f.box_top.toFloat(),
                    right = f.box_right.toFloat(),
                    bottom = f.box_bottom.toFloat(),
                    is_manual = f.is_manual != 0L
                )
            }

            val keywords = buildList {
                photo.iptc_keywords?.split(";")?.forEach { add(it.trim()) }
                photo.xmp_subject?.split(";")?.forEach { add(it.trim()) }
            }.distinct().filter { it.isNotEmpty() }

            PhotoEntry(
                file_name    = photo.file_name,
                star_rating  = photo.star_rating.toInt(),
                caption      = photo.caption ?: "",
                keywords     = keywords,
                edits        = edits,
                faces        = faceEntries,
                gps_latitude = photo.gps_latitude ?: 0.0,
                gps_longitude= photo.gps_longitude ?: 0.0,
                album_tokens = albumPhotos.map { it.id.toString() },
                is_hidden    = photo.is_hidden != 0L,
                block_upload = photo.block_upload != 0L
            )
        }

        return FolderMetadata(
            schema_version = 1,
            photos         = photoEntries,
            albums         = existing.albums,
            face_contacts  = existing.face_contacts
        )
    }

    // Apply a previously serialised FolderMetadata back into the DB.
    // Called during folder scan AFTER all EXIF rows have been inserted.
    fun applyToDb(meta: FolderMetadata, db: CasaPicDatabase, folderId: Long) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()

        val tokenToAlbumId = mutableMapOf<String, Long>()
        for (album in meta.albums) {
            db.casaPicQueries.transaction {
                db.casaPicQueries.insertAlbum(
                    name = album.name,
                    description = album.description,
                    created_date_millis = album.created_date_millis.takeIf { it > 0L } ?: now,
                    modified_date_millis = now,
                    kind = AlbumKind.ALBUM
                )
            }
            db.casaPicQueries.getAllAlbums().executeAsList()
                .firstOrNull { it.name == album.name }?.id
                ?.also { tokenToAlbumId[album.token] = it }
        }

        val contactIdToGroupId = mutableMapOf<String, Long>()
        for (contact in meta.face_contacts) {
            val existing = db.casaPicQueries.getAlbumsByKind(AlbumKind.FACE_GROUP).executeAsList()
                .firstOrNull { it.name == contact.name }
            val groupId = if (existing != null) {
                existing.id
            } else {
                db.casaPicQueries.transaction {
                    db.casaPicQueries.insertAlbum(
                        name = contact.name,
                        description = "",
                        created_date_millis = now,
                        modified_date_millis = now,
                        kind = AlbumKind.FACE_GROUP
                    )
                }
                db.casaPicQueries.getAlbumsByKind(AlbumKind.FACE_GROUP).executeAsList()
                    .firstOrNull { it.name == contact.name }?.id ?: continue
            }
            contactIdToGroupId[contact.id] = groupId
        }

        for (photoEntry in meta.photos) {
            val photo = db.casaPicQueries
                .getPhotosForFolder(folderId, 1L, Long.MAX_VALUE, 0L)
                .executeAsList()
                .firstOrNull { it.file_name == photoEntry.file_name }
                ?: continue

            db.casaPicQueries.transaction {
                if (photoEntry.star_rating > 0 && photo.star_rating == 0L) {
                    db.casaPicQueries.updatePhotoStar(photoEntry.star_rating.toLong(), photo.id)
                }
                if (photoEntry.caption.isNotEmpty() && photo.caption == null) {
                    db.casaPicQueries.updatePhotoCaption(photoEntry.caption, photo.id)
                }
                if (photoEntry.gps_latitude != 0.0 && photo.gps_latitude == null) {
                    db.casaPicQueries.updatePhotoOnRescan(
                        file_size = photo.file_size,
                        width = photo.width,
                        height = photo.height,
                        capture_date_millis = photo.capture_date_millis,
                        modified_date_millis = photo.modified_date_millis,
                        camera_make = photo.camera_make,
                        camera_model = photo.camera_model,
                        focal_length = photo.focal_length,
                        f_number = photo.f_number,
                        iso = photo.iso,
                        exposure_time = photo.exposure_time,
                        flash = photo.flash,
                        gps_latitude = photoEntry.gps_latitude,
                        gps_longitude = photoEntry.gps_longitude,
                        gps_altitude = photo.gps_altitude,
                        orientation = photo.orientation,
                        jpeg_quality = photo.jpeg_quality,
                        unique_id = photo.unique_id,
                        description = photo.description,
                        caption = photo.caption,
                        iptc_copyright = photo.iptc_copyright,
                        iptc_author = photo.iptc_author,
                        iptc_headline = photo.iptc_headline,
                        iptc_keywords = photo.iptc_keywords,
                        xmp_rating = photo.xmp_rating,
                        xmp_label = photo.xmp_label,
                        xmp_subject = photo.xmp_subject,
                        file_path = photo.file_path
                    )
                }
                if (photoEntry.is_hidden && photo.is_hidden == 0L) {
                    db.casaPicQueries.updatePhotoHidden(1L, photo.id)
                }
                if (photoEntry.block_upload && photo.block_upload == 0L) {
                    db.casaPicQueries.updatePhotoBlockUpload(1L, photo.id)
                }

                for (keyword in photoEntry.keywords) {
                    db.casaPicQueries.insertOrIgnoreTag(keyword)
                    val tagId = db.casaPicQueries.getTagByName(keyword).executeAsOneOrNull()?.id
                        ?: return@transaction
                    db.casaPicQueries.insertPhotoTag(photo.id, tagId)
                }

                val existingEdits = db.casaPicQueries.getActiveEditsForPhoto(photo.id).executeAsList()
                for (edit in photoEntry.edits) {
                    val editBytes = PhotoEdit.ADAPTER.encode(edit)
                    if (existingEdits.none { it.edit_proto.contentEquals(editBytes) }) {
                        db.casaPicQueries.insertPhotoEdit(
                            photo_id = photo.id,
                            edit_proto = editBytes,
                            applied_date_millis = now
                        )
                    }
                }

                for (face in photoEntry.faces) {
                    val groupId = if (face.contact_id.isNotEmpty()) contactIdToGroupId[face.contact_id] else null
                    db.casaPicQueries.insertFaceDetection(
                        photo_id = photo.id,
                        face_group_id = groupId,
                        box_left = face.left.toDouble(),
                        box_top = face.top.toDouble(),
                        box_right = face.right.toDouble(),
                        box_bottom = face.bottom.toDouble(),
                        confidence = null,
                        left_eye_open_prob = null,
                        right_eye_open_prob = null,
                        smiling_prob = null,
                        embedding = null,
                        tracking_id = null,
                        is_manual = if (face.is_manual) 1L else 0L
                    )
                }

                for (token in photoEntry.album_tokens) {
                    val albumId = tokenToAlbumId[token] ?: continue
                    db.casaPicQueries.insertAlbumPhoto(albumId, photo.id, 0)
                }
            }
        }
    }
}

class MetadataWriteDispatcher(
    private val db: CasaPicDatabase,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 500L
) {
    private val pending = ConcurrentHashMap<String, Job>()

    fun dispatch(folderPath: String, folderId: Long) {
        pending[folderPath]?.cancel()
        pending[folderPath] = scope.launch(Dispatchers.IO) {
            delay(debounceMs)
            MetadataFileService.write(folderPath, db, folderId)
            pending.remove(folderPath)
        }
    }
}
