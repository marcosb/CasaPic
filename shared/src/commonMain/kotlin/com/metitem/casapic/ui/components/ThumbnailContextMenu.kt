package com.metitem.casapic.ui.components

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import com.metitem.casapic.db.Photos

@Composable
fun ThumbnailContextMenu(
    photo: Photos,
    selectedCount: Int,
    onOpenPhoto: () -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onToggleStar: () -> Unit,
    onCopyPath: () -> Unit,
    onLocateOnDisk: () -> Unit,
    onBlockUpload: () -> Unit,
    onResetFaces: () -> Unit,
    onAddToAlbum: () -> Unit = {},
    onUndoAllEdits: () -> Unit = {},
    onMoveToFolder: () -> Unit = {},
    onOpenFile: () -> Unit = {},
    onSaveDestructive: () -> Unit = {},
    onShowProperties: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val isBatch = selectedCount > 1
    ContextMenuArea(
        items = {
            buildList {
                if (!isBatch) add(ContextMenuItem("View and Edit") { onOpenPhoto() })
                add(ContextMenuItem("Add to Album...") { onAddToAlbum() })
                add(ContextMenuItem("Rotate Clockwise") { onRotateCW() })
                add(ContextMenuItem("Rotate Counterclockwise") { onRotateCCW() })
                add(ContextMenuItem("Undo All Edits") { onUndoAllEdits() })
                add(ContextMenuItem(if (photo.star_rating > 0L) "Unstar" else "Star") { onToggleStar() })
                add(ContextMenuItem("Hide") { onHide() })
                add(ContextMenuItem("Move to New Folder...") { onMoveToFolder() })
                if (!isBatch) add(ContextMenuItem("Open File") { onOpenFile() })
                if (!isBatch) add(ContextMenuItem("Copy Full Path") { onCopyPath() })
                if (!isBatch) add(ContextMenuItem("Locate on Disk") { onLocateOnDisk() })
                if (!isBatch) add(ContextMenuItem("Save") { onSaveDestructive() })
                add(ContextMenuItem("Block from Uploading") { onBlockUpload() })
                add(ContextMenuItem("Reset Faces") { onResetFaces() })
                add(ContextMenuItem("Delete from Disk") { onDelete() })
                if (!isBatch) add(ContextMenuItem("Properties") { onShowProperties() })
            }
        },
        content = content
    )
}
