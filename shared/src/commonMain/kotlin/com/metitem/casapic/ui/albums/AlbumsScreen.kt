package com.metitem.casapic.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.db.Albums
import com.metitem.casapic.viewmodel.AlbumsViewModel

@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onBack: () -> Unit
) {
    val albums by viewModel.albums.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Albums?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text(
                "Albums (${albums.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { showCreateDialog = true }) { Text("New Album") }
        }
        HorizontalDivider()

        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖼", fontSize = 48.sp)
                    Text(
                        "No albums yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Create an album to organize your photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Create Album") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumTile(
                        album = album,
                        onRename = { renameTarget = album },
                        onDelete = { viewModel.deleteAlbum(album.id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            onCreate = { name ->
                viewModel.createAlbum(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renameTarget?.let { album ->
        RenameAlbumDialog(
            currentName = album.name,
            onRename = { newName ->
                viewModel.renameAlbum(album.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
private fun AlbumTile(
    album: Albums,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onRename)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("🖼", fontSize = 40.sp)
        }

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onRename, modifier = Modifier.height(28.dp)) {
                Text("Rename", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onDelete, modifier = Modifier.height(28.dp)) {
                Text(
                    "Delete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CreateAlbumDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Album") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Album name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onCreate(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RenameAlbumDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Album") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onRename(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
