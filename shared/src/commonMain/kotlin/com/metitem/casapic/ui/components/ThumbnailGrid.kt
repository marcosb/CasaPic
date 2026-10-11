package com.metitem.casapic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.metitem.casapic.paging.LazyPagingItems
import com.metitem.casapic.paging.itemKey
import com.metitem.casapic.paging.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.db.Photos
import com.metitem.casapic.model.ThumbnailCaption
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ThumbnailGrid(
    photos: LazyPagingItems<Photos>,
    selectedIds: Set<Long>,
    thumbnailCaption: ThumbnailCaption,
    columns: Int = 4,
    gridState: LazyGridState = rememberLazyGridState(),
    onPhotoClick: (id: Long, shiftDown: Boolean, ctrlDown: Boolean) -> Unit,
    onPhotoLongClick: (Long) -> Unit = {},
    onStarClick: (Long) -> Unit,
    onHidePhoto: (Long) -> Unit = {},
    onDeletePhoto: (Long) -> Unit = {},
    onOpenPhoto: (Long) -> Unit = {},
    onRotateCW: (Long) -> Unit = {},
    onRotateCCW: (Long) -> Unit = {},
    onCopyPath: (Long) -> Unit = {},
    onLocateOnDisk: (Long) -> Unit = {},
    onBlockUpload: (Long) -> Unit = {},
    onResetFaces: (Long) -> Unit = {},
    onAddToAlbum: (photoId: Long) -> Unit = {},
    onUndoAllEdits: (photoId: Long) -> Unit = {},
    onMoveToFolder: (photoId: Long, filePath: String) -> Unit = { _, _ -> },
    onOpenFile: (filePath: String) -> Unit = {},
    onSaveDestructive: (photoId: Long) -> Unit = {},
    onShowProperties: () -> Unit = {},
    dragManager: DragDropManager? = null,
    onDropToAlbum: (albumId: Long, photoIds: Set<Long>) -> Unit = { _, _ -> },
    onDropToFolder: (folderId: Long, photoIds: Set<Long>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier.fillMaxSize()
    ) {
        items(
            count = photos.itemCount,
            key = photos.itemKey { it.id }
        ) { index ->
            val photo = photos[index] ?: return@items
            val latestSelectedIds = rememberUpdatedState(selectedIds)
            var itemWindowTopLeft by remember { mutableStateOf(Offset.Zero) }
            // Read keyboard modifier state from incoming pointer events (Initial pass runs
            // before clickable's Main pass, so the values are ready when onClick fires).
            var shiftDown by remember { mutableStateOf(false) }
            var ctrlDown  by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .onGloballyPositioned { itemWindowTopLeft = it.boundsInWindow().topLeft }
                    .pointerInput(photo.id, "modifiers") {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val km = event.keyboardModifiers
                                shiftDown = km.isShiftPressed
                                ctrlDown  = km.isCtrlPressed || km.isMetaPressed
                            }
                        }
                    }
                    .pointerInput(photo.id) {
                        if (dragManager == null) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val ids = if (photo.id in latestSelectedIds.value)
                                    latestSelectedIds.value else setOf(photo.id)
                                dragManager.startDrag(ids, itemWindowTopLeft + offset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragManager.updateOffset(dragManager.currentOffset + dragAmount)
                            },
                            onDragEnd = {
                                val result = dragManager.endDrag()
                                when {
                                    result.targetAlbumId != null ->
                                        onDropToAlbum(result.targetAlbumId, result.photoIds)
                                    result.targetFolderId != null ->
                                        onDropToFolder(result.targetFolderId, result.photoIds)
                                }
                            },
                            onDragCancel = { dragManager.cancelDrag() }
                        )
                    }
            ) {
                ThumbnailContextMenu(
                    photo = photo,
                    selectedCount = selectedIds.size,
                    onOpenPhoto = { onOpenPhoto(photo.id) },
                    onRotateCW = { onRotateCW(photo.id) },
                    onRotateCCW = { onRotateCCW(photo.id) },
                    onHide = { onHidePhoto(photo.id) },
                    onDelete = { onDeletePhoto(photo.id) },
                    onToggleStar = { onStarClick(photo.id) },
                    onCopyPath = { onCopyPath(photo.id) },
                    onLocateOnDisk = { onLocateOnDisk(photo.id) },
                    onBlockUpload = { onBlockUpload(photo.id) },
                    onResetFaces = { onResetFaces(photo.id) },
                    onAddToAlbum = { onAddToAlbum(photo.id) },
                    onUndoAllEdits = { onUndoAllEdits(photo.id) },
                    onMoveToFolder = { onMoveToFolder(photo.id, photo.file_path) },
                    onOpenFile = { onOpenFile(photo.file_path) },
                    onSaveDestructive = { onSaveDestructive(photo.id) },
                    onShowProperties = onShowProperties
                ) {
                    ThumbnailItem(
                        photo = photo,
                        isSelected = photo.id in selectedIds,
                        thumbnailCaption = thumbnailCaption,
                        onClick = { onPhotoClick(photo.id, shiftDown, ctrlDown) },
                        onDoubleClick = { onOpenPhoto(photo.id) },
                        onStarClick = { onStarClick(photo.id) },
                        contextMenuContent = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThumbnailItem(
    photo: Photos,
    isSelected: Boolean,
    thumbnailCaption: ThumbnailCaption,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onStarClick: () -> Unit,
    contextMenuContent: (@Composable () -> Unit)? = null
) {
    val isHidden = photo.is_hidden != 0L
    Column(
        modifier = Modifier
            .padding(2.dp)
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else BorderStroke(0.dp, Color.Transparent),
                    RoundedCornerShape(4.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Image placeholder — in the full implementation, Coil AsyncImage loads from
            // photo.thumbnail_path (fast path) or samples the original file
            ThumbnailImage(
                thumbnailPath = photo.thumbnail_path,
                filePath = photo.file_path,
                modifier = Modifier.fillMaxSize()
                    .alpha(if (isHidden) 0.4f else 1f)
            )

            // Hidden overlay badge
            if (isHidden) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                Text(
                    "Hidden",
                    fontSize = 9.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }

            // Star overlay
            if (photo.star_rating > 0L) {
                Text(
                    "★",
                    fontSize = 14.sp,
                    color = Color(0xFFFFD700),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clickable(onClick = onStarClick)
                )
            } else {
                Text(
                    "☆",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clickable(onClick = onStarClick)
                )
            }
        }

        // Caption label
        val captionText = when (thumbnailCaption) {
            ThumbnailCaption.NONE -> null
            ThumbnailCaption.FILENAME -> photo.file_name
            ThumbnailCaption.DATE -> photo.capture_date_millis?.let { formatThumbDate(it) }
                ?: photo.file_name
            ThumbnailCaption.STARS -> "★".repeat(photo.star_rating.toInt().coerceIn(0, 5))
                .ifEmpty { "☆" }
        }
        if (captionText != null) {
            Text(
                text = captionText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
    }
}

private fun formatThumbDate(millis: Long): String = runCatching {
    val local = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    "%d/%02d/%02d".format(local.year, local.month, local.date)
}.getOrElse { "" }
