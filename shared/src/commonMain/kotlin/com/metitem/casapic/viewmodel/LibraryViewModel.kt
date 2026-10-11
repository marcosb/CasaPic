package com.metitem.casapic.viewmodel

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.metitem.casapic.data.PhotoPagingSource
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.db.Folders
import com.metitem.casapic.db.Photos
import com.metitem.casapic.model.ScanState
import com.metitem.casapic.model.ThumbnailCaption
import com.metitem.casapic.metadata.PhotoEdit
import com.metitem.casapic.metadata.RotateEdit
import com.metitem.casapic.settings.SettingsKeys
import com.metitem.casapic.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val db: CasaPicDatabase,
    private val settings: SettingsRepository
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _selectedPhotoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<Long>> = _selectedPhotoIds.asStateFlow()

    private val _activeFolderId = MutableStateFlow<Long?>(null)
    val activeFolderId: StateFlow<Long?> = _activeFolderId.asStateFlow()

    private val _activeAlbumId = MutableStateFlow<Long?>(null)
    val activeAlbumId: StateFlow<Long?> = _activeAlbumId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showHiddenPhotos = MutableStateFlow(settings.getBoolean(SettingsKeys.SHOW_HIDDEN_PHOTOS))
    val showHiddenPhotos: StateFlow<Boolean> = _showHiddenPhotos.asStateFlow()

    private val _thumbnailCaption = MutableStateFlow(
        when (settings.get(SettingsKeys.THUMBNAIL_CAPTION)) {
            "FILENAME" -> ThumbnailCaption.FILENAME
            "STARS"    -> ThumbnailCaption.STARS
            "NONE"     -> ThumbnailCaption.NONE
            else       -> ThumbnailCaption.DATE
        }
    )
    val thumbnailCaption: StateFlow<ThumbnailCaption> = _thumbnailCaption.asStateFlow()

    private val _thumbnailSizeFloat = MutableStateFlow(
        settings.getFloat(SettingsKeys.THUMBNAIL_SIZE_FLOAT,
            if (settings.get(SettingsKeys.THUMBNAIL_SIZE) == "SMALL") 0.333f else 0.667f)
    )
    val thumbnailSizeFloat: StateFlow<Float> = _thumbnailSizeFloat.asStateFlow()

    // Backward-compat for App.kt / MenuBridge (≥ 0.5 → "normal" = fewer, larger columns)
    val thumbnailSizeNormal: StateFlow<Boolean> = _thumbnailSizeFloat
        .map { it >= 0.5f }
        .stateIn(scope, SharingStarted.Eagerly, _thumbnailSizeFloat.value >= 0.5f)

    private val _fingerprintQueueSize = MutableStateFlow(0)
    val fingerprintQueueSize: StateFlow<Int> = _fingerprintQueueSize.asStateFlow()

    private val _detectionQueueSize = MutableStateFlow(0)
    val detectionQueueSize: StateFlow<Int> = _detectionQueueSize.asStateFlow()

    private var activeScanJob: Job? = null

    val folders: StateFlow<List<Folders>> =
        db.casaPicQueries.getAllFolders()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Reactive photo list: re-queries whenever folder, album, hidden filter, or search changes,
    // and also whenever the underlying photos table is written (SQLDelight reactive query).
    val photos: StateFlow<List<Photos>> =
        combine(
            combine(_activeFolderId, _activeAlbumId) { f, a -> f to a },
            _showHiddenPhotos,
            _searchQuery
        ) { folderAlbum, hidden, q ->
            Triple(folderAlbum, hidden, q)
        }
            .distinctUntilChanged()
            .flatMapLatest { (folderAlbum, showHidden, query) ->
                val (folderId, albumId) = folderAlbum
                val showHiddenLong = if (showHidden) 1L else 0L
                if (albumId != null) {
                    db.casaPicQueries.getPhotosInAlbum(albumId)
                        .asFlow().mapToList(Dispatchers.Default)
                } else if (query.isBlank()) {
                    if (folderId != null) {
                        db.casaPicQueries.getPhotosForFolder(folderId, showHiddenLong, 2000L, 0L)
                            .asFlow().mapToList(Dispatchers.Default)
                    } else {
                        db.casaPicQueries.getPhotosAll(showHiddenLong, 2000L, 0L)
                            .asFlow().mapToList(Dispatchers.Default)
                    }
                } else {
                    if (folderId != null) {
                        db.casaPicQueries.searchPhotosInFolder(query, folderId, showHiddenLong, 2000L, 0L)
                            .asFlow().mapToList(Dispatchers.Default)
                    } else {
                        db.casaPicQueries.searchPhotos(query, showHiddenLong, 2000L, 0L)
                            .asFlow().mapToList(Dispatchers.Default)
                    }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Paged photo stream for ThumbnailGrid — creates a new Pager when filters change.
    // PhotoPagingSource registers a SQLDelight Query.Listener to invalidate on DB writes.
    val pagedPhotos: Flow<PagingData<Photos>> =
        combine(
            combine(_activeFolderId, _activeAlbumId) { f, a -> f to a },
            _showHiddenPhotos,
            _searchQuery
        ) { folderAlbum, hidden, q ->
            Triple(folderAlbum, hidden, q)
        }
        .distinctUntilChanged()
        .flatMapLatest { (folderAlbum, showHidden, query) ->
            val (folderId, albumId) = folderAlbum
            Pager(
                config = PagingConfig(pageSize = 200, prefetchDistance = 50, enablePlaceholders = false),
                pagingSourceFactory = { PhotoPagingSource(db, folderId, albumId, showHidden, query) }
            ).flow
        }
        .cachedIn(scope)

    // Accurate count for status bar / folder header, reactive to filter changes.
    // Total file size on disk for the current view (status bar)
    val totalDiskBytes: StateFlow<Long> =
        combine(
            combine(_activeFolderId, _activeAlbumId) { f, a -> f to a },
            _showHiddenPhotos,
            _searchQuery
        ) { folderAlbum, hidden, q ->
            Triple(folderAlbum, hidden, q)
        }
        .distinctUntilChanged()
        .flatMapLatest { (folderAlbum, showHidden, _) ->
            val (folderId, albumId) = folderAlbum
            val showHiddenLong = if (showHidden) 1L else 0L
            when {
                albumId != null -> db.casaPicQueries.getTotalDiskSizeForAlbum(albumId)
                    .asFlow().mapToOne(Dispatchers.Default)
                folderId != null -> db.casaPicQueries.getTotalDiskSizeForFolder(folderId, showHiddenLong)
                    .asFlow().mapToOne(Dispatchers.Default)
                else -> db.casaPicQueries.getTotalDiskSizeAll(showHiddenLong)
                    .asFlow().mapToOne(Dispatchers.Default)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    val photoCount: StateFlow<Long> =
        combine(
            combine(_activeFolderId, _activeAlbumId) { f, a -> f to a },
            _showHiddenPhotos,
            _searchQuery
        ) { folderAlbum, hidden, q ->
            Triple(folderAlbum, hidden, q)
        }
        .distinctUntilChanged()
        .flatMapLatest { (folderAlbum, showHidden, query) ->
            val (folderId, albumId) = folderAlbum
            val showHiddenLong = if (showHidden) 1L else 0L
            when {
                albumId != null -> db.casaPicQueries.countPhotosInAlbum(albumId)
                    .asFlow().mapToOne(Dispatchers.Default)
                query.isBlank() -> {
                    if (folderId != null)
                        db.casaPicQueries.countPhotosForFolder(folderId, showHiddenLong)
                            .asFlow().mapToOne(Dispatchers.Default)
                    else
                        db.casaPicQueries.countPhotosAll(showHiddenLong)
                            .asFlow().mapToOne(Dispatchers.Default)
                }
                else -> {
                    if (folderId != null)
                        db.casaPicQueries.countSearchPhotosInFolder(query, folderId, showHiddenLong)
                            .asFlow().mapToOne(Dispatchers.Default)
                    else
                        db.casaPicQueries.countSearchPhotos(query, showHiddenLong)
                            .asFlow().mapToOne(Dispatchers.Default)
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    // Anchor for Shift+click range selection — not observable, just internal state.
    private var lastClickedId: Long? = null

    fun selectFolder(folderId: Long?) {
        _activeFolderId.value = folderId
        _activeAlbumId.value = null
        _selectedPhotoIds.value = emptySet()
        lastClickedId = null
    }

    fun selectAlbum(albumId: Long?) {
        _activeAlbumId.value = albumId
        _activeFolderId.value = null
        _selectedPhotoIds.value = emptySet()
        lastClickedId = null
    }

    fun setSearch(query: String) {
        _searchQuery.value = query
    }

    /** Standard modifier-key selection behaviour:
     *  - plain click   → select only this photo
     *  - Ctrl/Meta     → toggle this photo in/out of selection
     *  - Shift         → range-select from the last clicked photo to this one
     *  - Shift+Ctrl    → range-select, adding to (not replacing) the existing selection
     */
    fun handlePhotoClick(id: Long, shiftDown: Boolean, ctrlDown: Boolean) {
        val ordered = photos.value.map { it.id }
        when {
            shiftDown -> {
                val anchor = lastClickedId ?: id
                val anchorIdx = ordered.indexOf(anchor).takeIf { it >= 0 } ?: 0
                val targetIdx = ordered.indexOf(id).takeIf { it >= 0 } ?: 0
                val lo = minOf(anchorIdx, targetIdx)
                val hi = maxOf(anchorIdx, targetIdx)
                val rangeIds = ordered.subList(lo, hi + 1).toSet()
                // Shift keeps (or replaces) the current selection based on whether Ctrl is also held
                _selectedPhotoIds.value =
                    if (ctrlDown) _selectedPhotoIds.value + rangeIds else rangeIds
                // lastClickedId stays as the anchor — not updated on shift-click
            }
            ctrlDown -> {
                togglePhotoSelection(id)
                lastClickedId = id
            }
            else -> {
                _selectedPhotoIds.value = setOf(id)
                lastClickedId = id
            }
        }
    }

    fun togglePhotoSelection(photoId: Long) {
        val current = _selectedPhotoIds.value
        _selectedPhotoIds.value =
            if (photoId in current) current - photoId else current + photoId
    }

    fun selectOnlyPhoto(photoId: Long) {
        _selectedPhotoIds.value = setOf(photoId)
        lastClickedId = photoId
    }

    fun clearSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    fun selectAll() {
        _selectedPhotoIds.value = photos.value.map { it.id }.toSet()
    }

    fun toggleStar(photoId: Long) {
        scope.launch(Dispatchers.Default) {
            val photo = db.casaPicQueries.getPhotoById(photoId).executeAsOneOrNull() ?: return@launch
            val newRating = if (photo.star_rating > 0L) 0L else 1L
            db.casaPicQueries.updatePhotoStar(newRating, photoId)
        }
    }

    fun hidePhoto(photoId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.updatePhotoHidden(1L, photoId)
        }
    }

    fun deletePhoto(photoId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.deletePhotoById(photoId)
        }
    }

    fun setShowHiddenPhotos(show: Boolean) {
        settings.setBoolean(SettingsKeys.SHOW_HIDDEN_PHOTOS, show)
        _showHiddenPhotos.value = show
    }

    fun setThumbnailCaption(caption: ThumbnailCaption) {
        settings.set(SettingsKeys.THUMBNAIL_CAPTION, caption.name)
        _thumbnailCaption.value = caption
    }

    fun setThumbnailSizeFloat(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        settings.setFloat(SettingsKeys.THUMBNAIL_SIZE_FLOAT, clamped)
        _thumbnailSizeFloat.value = clamped
    }

    // Backward-compat for App.kt / MenuBridge keyboard shortcuts (Ctrl+1 / Ctrl+2)
    fun setThumbnailSize(normal: Boolean) {
        setThumbnailSizeFloat(if (normal) 0.667f else 0.333f)
    }

    fun updateScanState(state: ScanState) {
        _scanState.value = state
    }

    fun updateFingerprintQueueSize(size: Int) {
        _fingerprintQueueSize.value = size
    }

    fun updateDetectionQueueSize(size: Int) {
        _detectionQueueSize.value = size
    }

    fun cancelScan() {
        activeScanJob?.cancel()
        activeScanJob = null
        _scanState.value = ScanState.Idle
    }

    fun setScanJob(job: Job) {
        activeScanJob = job
    }

    fun setFolderScanMode(folderId: Long, mode: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.updateFolderScanMode(mode, folderId)
        }
    }

    val faceDetectionByFolder: StateFlow<Map<Long, Boolean>> =
        combine(folders, settings.allSettings) { folderList, allSettings ->
            folderList.associate { f ->
                val key = "face_detection_folder_${f.id}"
                f.id to ((allSettings[key] ?: "1") == "1")
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun setFolderFaceDetection(folderId: Long, enabled: Boolean) {
        settings.set("face_detection_folder_$folderId", if (enabled) "1" else "0")
    }

    fun removeFolder(folderId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.deleteFolder(folderId)
        }
    }

    fun batchRotateCW(photoIds: Set<Long>) {
        scope.launch(Dispatchers.Default) {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val proto = PhotoEdit.ADAPTER.encode(PhotoEdit(rotate = RotateEdit(degrees = 90)))
            db.casaPicQueries.transaction {
                photoIds.forEach { id ->
                    db.casaPicQueries.insertPhotoEdit(id, proto, now)
                }
            }
        }
    }

    fun batchRotateCCW(photoIds: Set<Long>) {
        scope.launch(Dispatchers.Default) {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val proto = PhotoEdit.ADAPTER.encode(PhotoEdit(rotate = RotateEdit(degrees = -90)))
            db.casaPicQueries.transaction {
                photoIds.forEach { id ->
                    db.casaPicQueries.insertPhotoEdit(id, proto, now)
                }
            }
        }
    }

    fun batchStar(photoIds: Set<Long>) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.transaction {
                photoIds.forEach { id ->
                    db.casaPicQueries.updatePhotoStar(1L, id)
                }
            }
        }
    }

    fun batchHide(photoIds: Set<Long>) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.transaction {
                photoIds.forEach { id ->
                    db.casaPicQueries.updatePhotoHidden(1L, id)
                }
            }
        }
    }

    fun batchDelete(photoIds: Set<Long>) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.transaction {
                photoIds.forEach { id -> db.casaPicQueries.deletePhotoById(id) }
            }
        }
    }

    fun selectStarred() {
        _selectedPhotoIds.value = photos.value.filter { it.star_rating > 0L }.map { it.id }.toSet()
    }

    fun invertSelection() {
        _selectedPhotoIds.value = photos.value.map { it.id }.toSet() - _selectedPhotoIds.value
    }

    fun batchUndoAllEdits(photoIds: Set<Long>) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.transaction {
                photoIds.forEach { id ->
                    db.casaPicQueries.undoAllEditsForPhoto(id)
                    db.casaPicQueries.updatePhotoThumbnail(null, id)
                }
            }
        }
    }

    fun batchResetFaces(photoIds: Set<Long>) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.transaction {
                photoIds.forEach { id ->
                    db.casaPicQueries.deleteFaceDetectionsForPhoto(id)
                }
            }
        }
    }

    fun undoAllEditsForPhoto(photoId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.undoAllEditsForPhoto(photoId)
            db.casaPicQueries.updatePhotoThumbnail(null, photoId)
        }
    }

    fun addPhotoToAlbum(photoId: Long, albumId: Long) {
        scope.launch(Dispatchers.Default) {
            val position = db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne()
            db.casaPicQueries.insertAlbumPhoto(albumId, photoId, position)
        }
    }

    fun adjustDateForPhotos(photoIds: Set<Long>, newDateMillis: Long?, shiftMillis: Long?) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.transaction {
                photoIds.forEach { id ->
                    if (newDateMillis != null) {
                        db.casaPicQueries.updatePhotoCaptureDate(newDateMillis, id)
                    } else if (shiftMillis != null) {
                        val current = db.casaPicQueries.getPhotoById(id)
                            .executeAsOneOrNull()?.capture_date_millis ?: return@forEach
                        db.casaPicQueries.updatePhotoCaptureDate(current + shiftMillis, id)
                    }
                }
            }
        }
    }
}
