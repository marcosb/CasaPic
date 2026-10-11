package com.metitem.casapic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.metitem.casapic.db.Folders
import com.metitem.casapic.model.ScanMode

@Composable
fun FolderManagerDialog(
    folders: List<Folders>,
    faceDetectionByFolder: Map<Long, Boolean> = emptyMap(),
    defaultWatchedPaths: List<String> = emptyList(),
    onScanModeChange: (Long, Long) -> Unit,
    onFaceDetectionChange: (Long, Boolean) -> Unit = { _, _ -> },
    onRemoveFolder: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onAddFolderByPath: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedFolderId by remember { mutableStateOf(folders.firstOrNull()?.id) }
    val selectedFolder = folders.find { it.id == selectedFolderId }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(20.dp)
                .width(680.dp)
        ) {
            Text("Folder Manager", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(320.dp)
            ) {
                // Left panel: folder list
                Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                    Text("Folders", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            val isSelected = folder.id == selectedFolderId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { selectedFolderId = folder.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = folder.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                        if (folders.isEmpty()) {
                            item {
                                Text("No folders added yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAddFolder) { Text("Add Folder…") }
                        if (selectedFolder != null) {
                            OutlinedButton(onClick = { onRemoveFolder(selectedFolder.id) }) {
                                Text("Remove")
                            }
                        }
                    }
                }

                VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Right panel: per-folder settings
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (selectedFolder == null) {
                        Text("Select a folder to edit its settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Scan for new pictures",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp))

                        val scanModes = listOf(
                            ScanMode.NONE to "Never",
                            ScanMode.ONCE to "Once",
                            ScanMode.ALL  to "Always"
                        )
                        scanModes.forEach { (mode, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onScanModeChange(selectedFolder.id, mode) }
                            ) {
                                RadioButton(
                                    selected = selectedFolder.scan_mode == mode,
                                    onClick = { onScanModeChange(selectedFolder.id, mode) }
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        Text("Face Detection",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp))

                        val faceEnabled = faceDetectionByFolder[selectedFolder.id] ?: true
                        listOf(true to "Automatically detect faces", false to "Don't detect faces").forEach { (enabled, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onFaceDetectionChange(selectedFolder.id, enabled) }
                            ) {
                                RadioButton(
                                    selected = faceEnabled == enabled,
                                    onClick = { onFaceDetectionChange(selectedFolder.id, enabled) }
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Watched Folders section (OS default paths)
            if (defaultWatchedPaths.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Watched Folders",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp))
                defaultWatchedPaths.forEach { path ->
                    val existingFolder = folders.find { it.path == path }
                    val isWatched = existingFolder != null && existingFolder.scan_mode != ScanMode.NONE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (isWatched) {
                                onScanModeChange(existingFolder.id, ScanMode.NONE)
                            } else {
                                if (existingFolder != null) onScanModeChange(existingFolder.id, ScanMode.ALL)
                                else onAddFolderByPath(path)
                            }
                        }
                    ) {
                        Checkbox(checked = isWatched, onCheckedChange = { checked ->
                            if (checked) {
                                if (existingFolder != null) onScanModeChange(existingFolder.id, ScanMode.ALL)
                                else onAddFolderByPath(path)
                            } else {
                                if (existingFolder != null) onScanModeChange(existingFolder.id, ScanMode.NONE)
                            }
                        })
                        Text(path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
