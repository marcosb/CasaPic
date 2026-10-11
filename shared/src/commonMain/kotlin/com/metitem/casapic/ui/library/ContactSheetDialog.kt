package com.metitem.casapic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ContactSheetDialog(
    photoCount: Int,
    folderName: String,
    onPrint: (columns: Int, title: String) -> Unit,
    onSaveAsJpeg: (columns: Int, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(folderName) }
    var columns by remember { mutableStateOf(4f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print Contact Sheet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Columns: ${columns.toInt()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = columns,
                    onValueChange = { columns = it },
                    valueRange = 2f..6f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "$photoCount photos will be included",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSaveAsJpeg(columns.toInt(), title) }) {
                    Text("Save as JPEG…")
                }
                Button(onClick = { onPrint(columns.toInt(), title) }) {
                    Text("Print…")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
