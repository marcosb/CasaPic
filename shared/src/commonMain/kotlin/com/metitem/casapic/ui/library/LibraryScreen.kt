package com.metitem.casapic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.db.Albums
import com.metitem.casapic.db.Folders
import com.metitem.casapic.db.Photos
import com.metitem.casapic.model.AlbumKind
import com.metitem.casapic.model.PrintOptions
import com.metitem.casapic.model.ThumbnailCaption
import com.metitem.casapic.paging.collectAsLazyPagingItems
import com.metitem.casapic.ui.adjustdate.AdjustDateDialog
import com.metitem.casapic.ui.components.DragDropManager
import com.metitem.casapic.ui.components.ProcessingStatusStrip
import com.metitem.casapic.ui.components.PropertiesPanel
import com.metitem.casapic.ui.components.SidebarPanel
import com.metitem.casapic.ui.components.ThumbnailGrid
import com.metitem.casapic.ui.components.ThumbnailImage
import com.metitem.casapic.ui.duplicates.DuplicatesDialog
import com.metitem.casapic.ui.export.ExportDialog
import com.metitem.casapic.LocalAppDependencies
import com.metitem.casapic.viewmodel.LibraryViewModel
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    albums: List<Albums> = emptyList(),
    peopleCount: Int = 0,
    defaultWatchedPaths: List<String> = emptyList(),
    onAddFolder: () -> Unit = {},
    onAddFolderByPath: (String) -> Unit = {},
    onOpenPhoto: (Long) -> Unit = {},
    onOpenPeople: () -> Unit = {},
    onOpenAlbums: () -> Unit = {},
    onOpenOptions: () -> Unit = {},
    onOpenSlideshow: () -> Unit = {},
    onPrintContactSheet: (photos: List<Photos>, columns: Int, title: String) -> Unit = { _, _, _ -> },
    onSaveContactSheetAsJpeg: (photos: List<Photos>, columns: Int, title: String) -> Unit = { _, _, _ -> },
    onPrint: (photos: List<Photos>, options: PrintOptions) -> Unit = { _, _ -> },
    onOpenFile: (filePath: String) -> Unit = {},
    onMovePhotoToFolder: (photoId: Long, currentPath: String) -> Unit = { _, _ -> },
    onSaveDestructive: (photoId: Long) -> Unit = {},
    onMovePhotoToKnownFolder: (photoId: Long, filePath: String, targetFolderPath: String) -> Unit = { _, _, _ -> },
    externalFolderManagerTrigger: Int = 0,
    externalDuplicatesTrigger: Int = 0,
    externalExportTrigger: Int = 0,
    externalAdjustDateTrigger: Int = 0,
    externalContactSheetTrigger: Int = 0,
    externalPrintTrigger: Int = 0
) {
    val folders by viewModel.folders.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val pagedPhotos = viewModel.pagedPhotos.collectAsLazyPagingItems()
    val photoCount by viewModel.photoCount.collectAsState()
    val selectedIds by viewModel.selectedPhotoIds.collectAsState()
    val activeFolderId by viewModel.activeFolderId.collectAsState()
    val activeAlbumId by viewModel.activeAlbumId.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showHidden by viewModel.showHiddenPhotos.collectAsState()
    val thumbnailCaption by viewModel.thumbnailCaption.collectAsState()
    val thumbnailSizeFloat by viewModel.thumbnailSizeFloat.collectAsState()
    val fingerprintQueueSize by viewModel.fingerprintQueueSize.collectAsState()
    val detectionQueueSize by viewModel.detectionQueueSize.collectAsState()
    val faceDetectionByFolder by viewModel.faceDetectionByFolder.collectAsState()

    val columns = ((1f - thumbnailSizeFloat) * 6f + 2f).roundToInt().coerceIn(2, 8)
    val gridState = rememberLazyGridState()

    var showFolderManager by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showDuplicates by remember { mutableStateOf(false) }
    var showAdjustDate by remember { mutableStateOf(false) }
    var showContactSheet by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showUploadPlaceholder by remember { mutableStateOf(false) }
    var addToAlbumPhotoId by remember { mutableStateOf<Long?>(null) }
    var saveConfirmPhotoId by remember { mutableStateOf<Long?>(null) }
    val deps = LocalAppDependencies.current

    LaunchedEffect(externalFolderManagerTrigger) { if (externalFolderManagerTrigger > 0) showFolderManager = true }
    LaunchedEffect(externalDuplicatesTrigger)    { if (externalDuplicatesTrigger    > 0) showDuplicates  = true }
    LaunchedEffect(externalExportTrigger)        { if (externalExportTrigger        > 0) showExport       = true }
    LaunchedEffect(externalAdjustDateTrigger)    { if (externalAdjustDateTrigger    > 0) showAdjustDate   = true }
    LaunchedEffect(externalContactSheetTrigger)  { if (externalContactSheetTrigger  > 0) showContactSheet = true }
    LaunchedEffect(externalPrintTrigger)         { if (externalPrintTrigger         > 0) showPrintDialog  = true }

    val selectedPhoto: Photos? = remember(selectedIds, photos) {
        if (selectedIds.size == 1) photos.find { it.id == selectedIds.first() } else null
    }
    val selectedPhotos: List<Photos> = remember(selectedIds, photos) {
        photos.filter { it.id in selectedIds }
    }

    // Date range from the in-memory list (acceptable for display)
    val minDate = remember(photos) { photos.mapNotNull { it.capture_date_millis }.minOrNull() }
    val maxDate = remember(photos) { photos.mapNotNull { it.capture_date_millis }.maxOrNull() }

    val dragManager = remember { DragDropManager() }
    var screenBoundsInWindow by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenBoundsInWindow = it.boundsInWindow() }
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        LibraryToolbar(
            searchQuery = searchQuery,
            onSearchChange = viewModel::setSearch,
            onAddFolder = onAddFolder,
            onOpenFolderManager = { showFolderManager = true },
            showHidden = showHidden,
            onToggleHidden = { viewModel.setShowHiddenPhotos(!showHidden) },
            thumbnailCaption = thumbnailCaption,
            onSetCaption = viewModel::setThumbnailCaption,
            onOpenOptions = onOpenOptions,
            onOpenSlideshow = onOpenSlideshow,
            onFindDuplicates = { showDuplicates = true },
            onAdjustDate = { if (selectedIds.isNotEmpty()) showAdjustDate = true }
        )
        HorizontalDivider()

        Row(modifier = Modifier.weight(1f)) {
            SidebarPanel(
                folders = folders,
                albums = albums,
                albumCount = albums.count { it.kind == AlbumKind.ALBUM },
                peopleCount = peopleCount,
                activeFolderId = activeFolderId,
                activeAlbumId = activeAlbumId,
                onFolderSelected = viewModel::selectFolder,
                onAlbumSelected = viewModel::selectAlbum,
                onOpenPeople = onOpenPeople,
                dragManager = dragManager,
                modifier = Modifier.width(200.dp)
            )
            VerticalDivider()

            Column(modifier = Modifier.weight(1f)) {
                FolderHeader(
                    folders = folders,
                    albums = albums,
                    activeFolderId = activeFolderId,
                    activeAlbumId = activeAlbumId,
                    photoCount = photoCount.toInt(),
                    minDate = minDate,
                    maxDate = maxDate
                )
                HorizontalDivider()
                ThumbnailGrid(
                    photos = pagedPhotos,
                    selectedIds = selectedIds,
                    thumbnailCaption = thumbnailCaption,
                    columns = columns,
                    gridState = gridState,
                    onPhotoClick = { id, shiftDown, ctrlDown ->
                        viewModel.handlePhotoClick(id, shiftDown, ctrlDown)
                    },
                    onRotateCW = { photoId -> viewModel.batchRotateCW(setOf(photoId)) },
                    onRotateCCW = { photoId -> viewModel.batchRotateCCW(setOf(photoId)) },
                    onStarClick = viewModel::toggleStar,
                    onHidePhoto = viewModel::hidePhoto,
                    onDeletePhoto = viewModel::deletePhoto,
                    onOpenPhoto = onOpenPhoto,
                    onAddToAlbum = { photoId -> addToAlbumPhotoId = photoId },
                    onUndoAllEdits = viewModel::undoAllEditsForPhoto,
                    onMoveToFolder = onMovePhotoToFolder,
                    onOpenFile = onOpenFile,
                    onSaveDestructive = { photoId -> saveConfirmPhotoId = photoId },
                    dragManager = dragManager,
                    onDropToAlbum = { albumId, photoIds ->
                        photoIds.forEach { photoId -> viewModel.addPhotoToAlbum(photoId, albumId) }
                    },
                    onDropToFolder = { folderId, photoIds ->
                        val folder = folders.find { it.id == folderId }
                        if (folder != null) {
                            photoIds.forEach { photoId ->
                                val photo = photos.find { it.id == photoId }
                                if (photo != null) onMovePhotoToKnownFolder(photoId, photo.file_path, folder.path)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            VerticalDivider()
            PropertiesPanel(
                photo = selectedPhoto,
                modifier = Modifier.width(220.dp)
            )
        }

        HorizontalDivider()
        ProcessingStatusStrip(
            scanState = scanState,
            detectionQueueSize = detectionQueueSize,
            fingerprintQueueSize = fingerprintQueueSize,
            onCancelScan = viewModel::cancelScan
        )
        // Photo tray: selected thumbnails + batch actions + thumbnail size slider
        PhotoTrayRow(
            selectedPhotos = selectedPhotos,
            thumbnailSizeFloat = thumbnailSizeFloat,
            onThumbnailSizeChange = viewModel::setThumbnailSizeFloat,
            onRotateCW = { viewModel.batchRotateCW(selectedIds) },
            onRotateCCW = { viewModel.batchRotateCCW(selectedIds) },
            onStar = { viewModel.batchStar(selectedIds) },
            onHide = { viewModel.batchHide(selectedIds) },
            onDelete = { viewModel.batchDelete(selectedIds) },
            onDeselectPhoto = viewModel::togglePhotoSelection,
            onClearSelection = viewModel::clearSelection
        )
        HorizontalDivider()
        val selectedDiskBytes = if (selectedIds.isEmpty()) 0L
            else photos.filter { it.id in selectedIds }.sumOf { it.file_size ?: 0L }
        StatusBar(
            photoCount = photoCount.toInt(),
            selectedCount = selectedIds.size,
            selectedDiskBytes = selectedDiskBytes,
            onExport = { showExport = true },
            onAdjustDate = { if (selectedIds.isNotEmpty()) showAdjustDate = true },
            onPrintContactSheet = { showContactSheet = true },
            onPrint = { showPrintDialog = true },
            onUpload = { showUploadPlaceholder = true }
        )
    }

    if (dragManager.isDragging) {
        val offsetPx = dragManager.currentOffset - screenBoundsInWindow.topLeft
        val badgeX = with(density) { offsetPx.x.toDp() } - 12.dp
        val badgeY = with(density) { offsetPx.y.toDp() } - 12.dp
        Box(
            modifier = Modifier
                .offset(x = badgeX, y = badgeY)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dragManager.draggedPhotoIds.size.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    }

    if (showFolderManager) {
        FolderManagerDialog(
            folders = folders,
            faceDetectionByFolder = faceDetectionByFolder,
            defaultWatchedPaths = defaultWatchedPaths,
            onScanModeChange = viewModel::setFolderScanMode,
            onFaceDetectionChange = viewModel::setFolderFaceDetection,
            onRemoveFolder = viewModel::removeFolder,
            onAddFolder = {
                showFolderManager = false
                onAddFolder()
            },
            onAddFolderByPath = { path ->
                onAddFolderByPath(path)
            },
            onDismiss = { showFolderManager = false }
        )
    }

    if (showExport) {
        ExportDialog(
            photoCount = if (selectedIds.isNotEmpty()) selectedIds.size else photos.size,
            defaultOutputFolder = "",
            onExport = { showExport = false },
            onDismiss = { showExport = false }
        )
    }

    if (showDuplicates) {
        DuplicatesDialog(
            db = deps.db,
            onDeletePhoto = viewModel::deletePhoto,
            onDismiss = { showDuplicates = false }
        )
    }

    if (showAdjustDate && selectedIds.isNotEmpty()) {
        AdjustDateDialog(
            photoCount = selectedIds.size,
            onAdjust = { newDate, shiftMs ->
                viewModel.adjustDateForPhotos(selectedIds, newDate, shiftMs)
                showAdjustDate = false
            },
            onDismiss = { showAdjustDate = false }
        )
    }

    addToAlbumPhotoId?.let { photoId ->
        AlbumPickerDialog(
            albums = albums.filter { it.kind == AlbumKind.ALBUM },
            onAlbumSelected = { albumId ->
                viewModel.addPhotoToAlbum(photoId, albumId)
                addToAlbumPhotoId = null
            },
            onDismiss = { addToAlbumPhotoId = null }
        )
    }

    saveConfirmPhotoId?.let { photoId ->
        AlertDialog(
            onDismissRequest = { saveConfirmPhotoId = null },
            title = { Text("Save Changes") },
            text = { Text("This will permanently modify the file on disk and cannot be undone. The original file will be overwritten.") },
            confirmButton = {
                TextButton(onClick = {
                    onSaveDestructive(photoId)
                    saveConfirmPhotoId = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { saveConfirmPhotoId = null }) { Text("Cancel") }
            }
        )
    }

    if (showContactSheet) {
        val folderName = if (activeFolderId == null) "All Photos"
            else folders.find { it.id == activeFolderId }?.name ?: "Photos"
        ContactSheetDialog(
            photoCount = photos.size,
            folderName = folderName,
            onPrint = { cols, title ->
                onPrintContactSheet(photos, cols, title)
                showContactSheet = false
            },
            onSaveAsJpeg = { cols, title ->
                onSaveContactSheetAsJpeg(photos, cols, title)
                showContactSheet = false
            },
            onDismiss = { showContactSheet = false }
        )
    }

    if (showPrintDialog) {
        val printPhotos = if (selectedIds.isNotEmpty()) selectedPhotos else photos
        PrintDialog(
            photoCount = printPhotos.size,
            onPrint = { opts ->
                onPrint(printPhotos, opts)
                showPrintDialog = false
            },
            onDismiss = { showPrintDialog = false }
        )
    }

    if (showUploadPlaceholder) {
        AlertDialog(
            onDismissRequest = { showUploadPlaceholder = false },
            title = { Text("Upload to Google Photos") },
            text = { Text("Google Photos upload is not yet supported. Coming soon.") },
            confirmButton = {
                TextButton(onClick = { showUploadPlaceholder = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun LibraryToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAddFolder: () -> Unit,
    onOpenFolderManager: () -> Unit,
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    thumbnailCaption: ThumbnailCaption,
    onSetCaption: (ThumbnailCaption) -> Unit,
    onOpenOptions: () -> Unit = {},
    onOpenSlideshow: () -> Unit = {},
    onFindDuplicates: () -> Unit = {},
    onAdjustDate: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onAddFolder) { Text("Add Folder") }
        TextButton(onClick = onOpenFolderManager) { Text("Folder Manager") }
        TextButton(onClick = onToggleHidden) {
            Text(if (showHidden) "Hide Hidden" else "Show Hidden")
        }
        TextButton(onClick = onOpenSlideshow) { Text("Slideshow") }
        TextButton(onClick = onFindDuplicates) { Text("Find Dupes") }
        TextButton(onClick = onAdjustDate) { Text("Adj. Date") }
        TextButton(onClick = onOpenOptions) { Text("Options") }
        Spacer(Modifier.weight(1f))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.width(220.dp).height(40.dp)
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun FolderHeader(
    folders: List<Folders>,
    albums: List<Albums> = emptyList(),
    activeFolderId: Long?,
    activeAlbumId: Long? = null,
    photoCount: Int,
    minDate: Long? = null,
    maxDate: Long? = null
) {
    val name = when {
        activeAlbumId != null -> albums.find { it.id == activeAlbumId }?.name ?: "Album"
        activeFolderId != null -> folders.find { it.id == activeFolderId }?.name ?: "Folder"
        else -> "All Photos"
    }

    val dateRangeText = if (minDate != null && maxDate != null) {
        runCatching {
            val min = Instant.fromEpochMilliseconds(minDate)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val max = Instant.fromEpochMilliseconds(maxDate)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            if (min.year == max.year) {
                if (min.month == max.month && min.day == max.day)
                    "%s %d, %d".format(monthAbbr(min.month.ordinal + 1), min.day, min.year)
                else
                    "%s %d – %s %d, %d".format(monthAbbr(min.month.ordinal + 1), min.day,
                        monthAbbr(max.month.ordinal + 1), max.day, max.year)
            } else {
                "%s %d, %d – %s %d, %d".format(
                    monthAbbr(min.month.ordinal + 1), min.day, min.year,
                    monthAbbr(max.month.ordinal + 1), max.day, max.year)
            }
        }.getOrNull()
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (dateRangeText != null) {
                Text(text = dateRangeText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = "$photoCount photos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun monthAbbr(month: Int) = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[month.coerceIn(1, 12)]

@Composable
private fun PhotoTrayRow(
    selectedPhotos: List<Photos>,
    thumbnailSizeFloat: Float,
    onThumbnailSizeChange: (Float) -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onStar: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDeselectPhoto: (Long) -> Unit,
    onClearSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedPhotos.isNotEmpty()) {
            Text(
                "${selectedPhotos.size} selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp)
            )
            // Thumbnail chips (cap at 20)
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(selectedPhotos.take(20)) { photo ->
                    Box(modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))
                    ) {
                        ThumbnailImage(
                            thumbnailPath = photo.thumbnail_path,
                            filePath = photo.file_path,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.TopEnd)
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                .clickable { onDeselectPhoto(photo.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("×", fontSize = 9.sp, color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 9.sp)
                        }
                    }
                }
                if (selectedPhotos.size > 20) {
                    item {
                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Text("+${selectedPhotos.size - 20}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            // Batch action buttons
            TextButton(onClick = onRotateCW, modifier = Modifier.height(36.dp)) {
                Text("↻", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onRotateCCW, modifier = Modifier.height(36.dp)) {
                Text("↺", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onStar, modifier = Modifier.height(36.dp)) {
                Text("★", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onHide, modifier = Modifier.height(36.dp)) {
                Text("Hide", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onDelete, modifier = Modifier.height(36.dp)) {
                Text("Del", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onClearSelection, modifier = Modifier.height(36.dp)) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Thumbnail size slider always at right
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⊞", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = thumbnailSizeFloat,
                onValueChange = onThumbnailSizeChange,
                modifier = Modifier.width(120.dp).padding(horizontal = 4.dp)
            )
            Text("⊞", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusBar(
    photoCount: Int,
    selectedCount: Int,
    selectedDiskBytes: Long,
    onExport: () -> Unit = {},
    onAdjustDate: () -> Unit = {},
    onPrintContactSheet: () -> Unit = {},
    onPrint: () -> Unit = {},
    onUpload: () -> Unit = {}
) {
    val diskSizeText = when {
        selectedDiskBytes <= 0L -> ""
        selectedDiskBytes < 1_024L * 1_024L -> " • ${selectedDiskBytes / 1024} KB"
        selectedDiskBytes < 1_024L * 1_024L * 1_024L -> " • ${"%.1f".format(selectedDiskBytes / 1_048_576.0)} MB"
        else -> " • ${"%.1f".format(selectedDiskBytes / 1_073_741_824.0)} GB"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Upload to Google Photos CTA
        TextButton(onClick = onUpload, modifier = Modifier.height(28.dp)) {
            Text("Upload to Google Photos", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = if (selectedCount > 0) "$selectedCount of $photoCount selected$diskSizeText"
                   else "$photoCount pictures",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (selectedCount > 0) {
            TextButton(onClick = onAdjustDate, modifier = Modifier.height(28.dp)) {
                Text("Adj. Date", style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = onPrint, modifier = Modifier.height(28.dp)) {
            Text("Print…", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onExport, modifier = Modifier.height(28.dp)) {
            Text("Export", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onPrintContactSheet, modifier = Modifier.height(28.dp)) {
            Text("Contact Sheet", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AlbumPickerDialog(
    albums: List<Albums>,
    onAlbumSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Album") },
        text = {
            if (albums.isEmpty()) {
                Text("No albums found. Create an album first.")
            } else {
                LazyColumn {
                    items(albums) { album ->
                        TextButton(
                            onClick = { onAlbumSelected(album.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(album.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
