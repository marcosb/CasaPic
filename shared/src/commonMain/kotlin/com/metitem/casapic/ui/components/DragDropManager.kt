package com.metitem.casapic.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

class DragDropManager {
    var isDragging by mutableStateOf(false)
        private set
    var draggedPhotoIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var currentOffset by mutableStateOf(Offset.Zero)
        private set

    private val albumBounds = mutableMapOf<Long, Rect>()
    private val folderBounds = mutableMapOf<Long, Rect>()

    fun startDrag(photoIds: Set<Long>, windowOffset: Offset) {
        draggedPhotoIds = photoIds
        currentOffset = windowOffset
        isDragging = true
    }

    fun updateOffset(newOffset: Offset) {
        if (isDragging) currentOffset = newOffset
    }

    fun endDrag(): DropResult {
        val albumId = albumBounds.entries.firstOrNull { (_, r) -> r.contains(currentOffset) }?.key
        val folderId = folderBounds.entries.firstOrNull { (_, r) -> r.contains(currentOffset) }?.key
        val result = DropResult(albumId, folderId, draggedPhotoIds)
        isDragging = false
        draggedPhotoIds = emptySet()
        return result
    }

    fun cancelDrag() {
        isDragging = false
        draggedPhotoIds = emptySet()
    }

    fun registerAlbumBounds(albumId: Long, rect: Rect) {
        albumBounds[albumId] = rect
    }

    fun registerFolderBounds(folderId: Long, rect: Rect) {
        folderBounds[folderId] = rect
    }

    fun isHoveringAlbum(albumId: Long): Boolean =
        isDragging && albumBounds[albumId]?.contains(currentOffset) == true

    fun isHoveringFolder(folderId: Long): Boolean =
        isDragging && folderBounds[folderId]?.contains(currentOffset) == true

    data class DropResult(
        val targetAlbumId: Long?,
        val targetFolderId: Long?,
        val photoIds: Set<Long>
    )
}
