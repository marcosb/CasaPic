package com.metitem.casapic.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

enum class ExportFormat { JPEG, PNG, WEBP }

data class ExportSettings(
    val outputFolder: String,
    val format: ExportFormat,
    val quality: Int,
    val maxDimension: Int?
)

@Composable
fun ExportDialog(
    photoCount: Int,
    defaultOutputFolder: String = "",
    onExport: (ExportSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var outputFolder by remember { mutableStateOf(defaultOutputFolder) }
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableFloatStateOf(85f) }
    var resizeEnabled by remember { mutableStateOf(false) }
    var maxDimension by remember { mutableFloatStateOf(1600f) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Export ${if (photoCount == 1) "1 Photo" else "$photoCount Photos"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text("Output folder:", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = outputFolder,
                onValueChange = { outputFolder = it },
                placeholder = { Text("Select folder…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("Format:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            ExportFormat.entries.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = format == f, onClick = { format = f })
                    Text(f.name, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (format == ExportFormat.JPEG || format == ExportFormat.WEBP) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Quality: ${quality.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 10f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = resizeEnabled, onClick = { resizeEnabled = !resizeEnabled })
                Text("Resize to max dimension:", style = MaterialTheme.typography.bodySmall)
            }
            if (resizeEnabled) {
                Text(
                    "${maxDimension.toInt()} px",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = maxDimension,
                    onValueChange = { maxDimension = it },
                    valueRange = 320f..4096f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        onExport(
                            ExportSettings(
                                outputFolder = outputFolder,
                                format = format,
                                quality = quality.toInt(),
                                maxDimension = if (resizeEnabled) maxDimension.toInt() else null
                            )
                        )
                    },
                    enabled = outputFolder.isNotBlank()
                ) { Text("Export") }
            }
        }
    }
}
