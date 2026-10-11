package com.metitem.casapic.metadata

import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.model.AlbumKind
import kotlin.time.Clock

object PicasaImporter {

    fun importToDb(ini: ParsedPicasaIni, db: CasaPicDatabase, folderId: Long) {
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. Import album definitions from [.album:hash] sections
        val tokenToAlbumId = mutableMapOf<String, Long>()
        for (album in ini.albums) {
            db.casaPicQueries.transaction {
                db.casaPicQueries.insertAlbum(
                    name = album.name,
                    description = album.description ?: "",
                    created_date_millis = now,
                    modified_date_millis = now,
                    kind = AlbumKind.ALBUM
                )
            }
            val albumId = db.casaPicQueries.getAllAlbums().executeAsList()
                .firstOrNull { it.name == album.name }?.id
            if (albumId != null) tokenToAlbumId[album.token] = albumId
        }

        // 2. Import face contacts → face-group albums
        val contactIdToGroupId = mutableMapOf<String, Long>()
        for ((contactId, displayName) in ini.contacts) {
            val existing = db.casaPicQueries.getAlbumsByKind(AlbumKind.FACE_GROUP).executeAsList()
                .firstOrNull { it.name == displayName }
            val groupId = if (existing != null) {
                existing.id
            } else {
                db.casaPicQueries.transaction {
                    db.casaPicQueries.insertAlbum(
                        name = displayName,
                        description = "",
                        created_date_millis = now,
                        modified_date_millis = now,
                        kind = AlbumKind.FACE_GROUP
                    )
                }
                db.casaPicQueries.getAlbumsByKind(AlbumKind.FACE_GROUP).executeAsList()
                    .firstOrNull { it.name == displayName }?.id ?: continue
            }
            contactIdToGroupId[contactId] = groupId
        }

        // 3. Per-photo import (idempotent: only updates null fields)
        for ((fileName, entry) in ini.photos) {
            val photo = db.casaPicQueries
                .getPhotosForFolder(folderId, 1L, Long.MAX_VALUE, 0L)
                .executeAsList()
                .firstOrNull { it.file_name == fileName }
                ?: continue

            db.casaPicQueries.transaction {
                // Star
                if (entry.star && photo.star_rating == 0L) {
                    db.casaPicQueries.updatePhotoStar(1L, photo.id)
                }

                // Caption (only if currently null in DB)
                if (entry.caption != null && photo.caption == null) {
                    db.casaPicQueries.updatePhotoCaption(entry.caption, photo.id)
                }

                // GPS (only if currently null)
                if (entry.gpsLatitude != null && photo.gps_latitude == null) {
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
                        gps_latitude = entry.gpsLatitude,
                        gps_longitude = entry.gpsLongitude,
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

                // Keywords / tags
                for (keyword in entry.keywords) {
                    db.casaPicQueries.insertOrIgnoreTag(keyword)
                    val tagId = db.casaPicQueries.getTagByName(keyword)
                        .executeAsOneOrNull()?.id ?: return@transaction
                    db.casaPicQueries.insertPhotoTag(photo.id, tagId)
                }

                // Typed edits → photo_edit rows (idempotent: skip if bytes already present)
                val existingEdits = db.casaPicQueries.getActiveEditsForPhoto(photo.id).executeAsList()
                for (edit in entry.edits) {
                    val editBytes = PhotoEdit.ADAPTER.encode(edit)
                    if (existingEdits.none { it.edit_proto.contentEquals(editBytes) }) {
                        db.casaPicQueries.insertPhotoEdit(
                            photo_id = photo.id,
                            edit_proto = editBytes,
                            applied_date_millis = now
                        )
                    }
                }

                // Faces → face_detections rows
                for (face in entry.faces) {
                    val groupId = if (face.contact_id.isNotEmpty())
                        contactIdToGroupId[face.contact_id]
                    else
                        null
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
                        is_manual = 0L
                    )
                }

                // Album memberships
                for (token in entry.albumTokens) {
                    val albumId = tokenToAlbumId[token] ?: continue
                    db.casaPicQueries.insertAlbumPhoto(albumId, photo.id, 0)
                }
            }
        }
    }

}
