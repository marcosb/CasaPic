package com.metitem.casapic.ui.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.db.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DuplicateGroup(val fingerprint: Long, val photos: List<Photos>)

@Composable
fun DuplicatesDialog(
    db: CasaPicDatabase,
    onDeletePhoto: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }

    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.Default) { findDuplicates(db) }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                "Find Duplicate Photos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                "Scanning for duplicates…",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                groups.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No duplicate photos found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Text(
                        "${groups.size} duplicate group(s) found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups, key = { it.fingerprint }) { group ->
                            DuplicateGroupCard(
                                group = group,
                                onDelete = onDeletePhoto
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            "${group.photos.size} copies",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        group.photos.forEach { photo ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        photo.file_name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        photo.file_path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { onDelete(photo.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(width = 64.dp, height = 28.dp)
                ) {
                    Text("Del", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun findDuplicates(db: CasaPicDatabase): List<DuplicateGroup> {
    val all = db.casaPicQueries.getPhotosAll(1L, 100_000L, 0L).executeAsList()
    return all
        .filter { it.fingerprint != null }
        .groupBy { it.fingerprint!! }
        .filter { (_, photos) -> photos.size > 1 }
        .map { (fingerprint, photos) -> DuplicateGroup(fingerprint, photos) }
        .sortedByDescending { it.photos.size }
}
