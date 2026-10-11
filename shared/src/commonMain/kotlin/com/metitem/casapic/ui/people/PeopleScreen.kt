package com.metitem.casapic.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.db.Albums
import com.metitem.casapic.viewmodel.PeopleViewModel

@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    onBack: () -> Unit
) {
    val faceGroups by viewModel.faceGroups.collectAsState()
    val unassigned by viewModel.unassignedFaces.collectAsState()

    var renameTarget by remember { mutableStateOf<Albums?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text(
                "People (${faceGroups.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
        }
        HorizontalDivider()

        if (faceGroups.isEmpty() && unassigned.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👤", fontSize = 48.sp)
                    Text(
                        "No faces detected yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Add a folder with photos to detect faces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.weight(1f).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(faceGroups, key = { it.id }) { group ->
                FaceGroupTile(
                    group = group,
                    onRename = { renameTarget = group },
                    onDelete = { viewModel.deleteGroup(group.id) }
                )
            }

            if (unassigned.isNotEmpty()) {
                item {
                    UnknownFacesTile(count = unassigned.size)
                }
            }
        }
    }

    renameTarget?.let { group ->
        RenameDialog(
            currentName = group.name,
            onRename = { newName ->
                viewModel.renameGroup(group.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
private fun FaceGroupTile(
    group: Albums,
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
        // Face avatar placeholder
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                group.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = group.name,
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
                Text("Delete", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun UnknownFacesTile(count: Int) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            Text("?", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "Unknown ($count)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Person") },
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
