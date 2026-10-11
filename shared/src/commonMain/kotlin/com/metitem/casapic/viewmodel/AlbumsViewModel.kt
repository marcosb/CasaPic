package com.metitem.casapic.viewmodel

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.metitem.casapic.db.Albums
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.model.AlbumKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

class AlbumsViewModel(private val db: CasaPicDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val albums: StateFlow<List<Albums>> =
        db.casaPicQueries.getAlbumsByKind(AlbumKind.ALBUM)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun createAlbum(name: String) {
        scope.launch(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            db.casaPicQueries.insertAlbum(
                name = name,
                description = "",
                created_date_millis = now,
                modified_date_millis = now,
                kind = AlbumKind.ALBUM
            )
        }
    }

    fun renameAlbum(albumId: Long, newName: String) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.updateAlbumName(
                newName,
                Clock.System.now().toEpochMilliseconds(),
                albumId
            )
        }
    }

    fun deleteAlbum(albumId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.deleteAlbum(albumId)
        }
    }

    fun addPhotoToAlbum(albumId: Long, photoId: Long) {
        scope.launch(Dispatchers.Default) {
            val count = db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne()
            db.casaPicQueries.insertAlbumPhoto(albumId, photoId, count)
        }
    }

    fun removePhotoFromAlbum(albumId: Long, photoId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.removeAlbumPhoto(albumId, photoId)
        }
    }
}
