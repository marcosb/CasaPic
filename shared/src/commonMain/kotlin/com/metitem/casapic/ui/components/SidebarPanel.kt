package com.metitem.casapic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.db.Albums
import com.metitem.casapic.db.Folders
import com.metitem.casapic.model.AlbumKind

@Composable
fun SidebarPanel(
    folders: List<Folders>,
    albums: List<Albums> = emptyList(),
    albumCount: Int,
    peopleCount: Int,
    activeFolderId: Long?,
    activeAlbumId: Long? = null,
    onFolderSelected: (Long?) -> Unit,
    onAlbumSelected: (Long?) -> Unit = {},
    onOpenPeople: () -> Unit = {},
    dragManager: DragDropManager? = null,
    modifier: Modifier = Modifier
) {
    var foldersExpanded by remember { mutableStateOf(true) }
    var albumsExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 4.dp)
    ) {
        item {
            SidebarSectionHeader(
                title = "Albums ($albumCount)",
                expanded = albumsExpanded,
                onClick = { albumsExpanded = !albumsExpanded }
            )
        }

        if (albumsExpanded) {
            items(albums.filter { it.kind == AlbumKind.ALBUM }, key = { "album_${it.id}" }) { album ->
                val isHovering = dragManager?.isHoveringAlbum(album.id) == true
                val isSelected = album.id == activeAlbumId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { dragManager?.registerAlbumBounds(album.id, it.boundsInWindow()) }
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isHovering -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onAlbumSelected(album.id) }
                        .padding(start = 32.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                ) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isHovering -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                }
            }
        }

        item {
            SidebarSectionHeader(
                title = "People ($peopleCount)",
                expanded = false,
                onClick = onOpenPeople
            )
        }

        item {
            SidebarSectionHeader(
                title = "Folders (${folders.size})",
                expanded = foldersExpanded,
                onClick = { foldersExpanded = !foldersExpanded }
            )
        }

        if (foldersExpanded) {
            item {
                SidebarFolderRow(
                    name = "All Photos",
                    isSelected = activeFolderId == null && activeAlbumId == null,
                    depth = 1,
                    onClick = { onFolderSelected(null) }
                )
            }
            items(folders, key = { it.id }) { folder ->
                SidebarFolderRow(
                    name = folder.name,
                    isSelected = folder.id == activeFolderId,
                    depth = 1,
                    onClick = { onFolderSelected(folder.id) },
                    dragManager = dragManager,
                    folderId = folder.id
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SidebarSectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (expanded) "▼" else "▶",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SidebarFolderRow(
    name: String,
    isSelected: Boolean,
    depth: Int,
    onClick: () -> Unit,
    dragManager: DragDropManager? = null,
    folderId: Long? = null
) {
    val isHovering = folderId != null && dragManager?.isHoveringFolder(folderId) == true
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (folderId != null) dragManager?.registerFolderBounds(folderId, coords.boundsInWindow())
            }
            .background(
                when {
                    isHovering -> MaterialTheme.colorScheme.tertiaryContainer
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                isHovering -> MaterialTheme.colorScheme.onTertiaryContainer
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}
