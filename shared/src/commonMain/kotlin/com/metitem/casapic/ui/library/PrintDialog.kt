package com.metitem.casapic.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.metitem.casapic.model.PrintFitMode
import com.metitem.casapic.model.PrintLayout
import com.metitem.casapic.model.PrintOptions
import com.metitem.casapic.model.PrintTextLabel
import kotlin.math.ceil
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintDialog(
    photoCount: Int,
    onPrint: (PrintOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var options by remember { mutableStateOf(PrintOptions()) }
    var textMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(20.dp)
                .width(640.dp)
        ) {
            Text("Print Photos", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Left column: settings
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Layout selector
                    Text("Layout", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrintLayout.entries.forEach { layout ->
                            LayoutTile(
                                layout = layout,
                                selected = options.layout == layout,
                                onClick = { options = options.copy(layout = layout) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Fit mode
                    Text("Fit Mode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    PrintFitMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { options = options.copy(fitMode = mode) }) {
                            RadioButton(selected = options.fitMode == mode,
                                onClick = { options = options.copy(fitMode = mode) })
                            Text(mode.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    HorizontalDivider()

                    // Border
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { options = options.copy(showBorder = !options.showBorder) }) {
                        Checkbox(checked = options.showBorder,
                            onCheckedChange = { options = options.copy(showBorder = it) })
                        Text("Show border around photos", style = MaterialTheme.typography.bodySmall)
                    }

                    // Text label
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Print label:", style = MaterialTheme.typography.bodySmall)
                        ExposedDropdownMenuBox(
                            expanded = textMenuExpanded,
                            onExpandedChange = { textMenuExpanded = it },
                            modifier = Modifier.width(150.dp)
                        ) {
                            TextField(
                                value = options.textLabel.label,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(textMenuExpanded) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            ExposedDropdownMenu(expanded = textMenuExpanded, onDismissRequest = { textMenuExpanded = false }) {
                                PrintTextLabel.entries.forEach { label ->
                                    DropdownMenuItem(
                                        text = { Text(label.label, style = MaterialTheme.typography.bodySmall) },
                                        onClick = { options = options.copy(textLabel = label); textMenuExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // Copies
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Copies per photo:", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { if (options.copiesPerPhoto > 1) options = options.copy(copiesPerPhoto = options.copiesPerPhoto - 1) },
                            modifier = Modifier.size(28.dp).padding(0.dp)
                        ) { Text("−", style = MaterialTheme.typography.bodySmall) }
                        Text("${options.copiesPerPhoto}", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(24.dp), maxLines = 1)
                        OutlinedButton(
                            onClick = { options = options.copy(copiesPerPhoto = options.copiesPerPhoto + 1) },
                            modifier = Modifier.size(28.dp).padding(0.dp)
                        ) { Text("+", style = MaterialTheme.typography.bodySmall) }
                    }

                    val pageCount = if (options.layout == PrintLayout.CONTACT_SHEET) 1
                        else ceil(photoCount.toDouble() * options.copiesPerPhoto / options.layout.cellsPerPage.toDouble()).toInt().coerceAtLeast(1)
                    Text("$photoCount photos → $pageCount page${if (pageCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Right column: live preview
                Column(modifier = Modifier.width(180.dp)) {
                    Text("Preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(200.dp)
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        PrintPreviewCanvas(options = options)
                    }
                    Text("Letter (8.5 × 11 in)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onPrint(options) }) { Text("Print…") }
            }
        }
    }
}

@Composable
private fun LayoutTile(layout: PrintLayout, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) 2.dp else 1.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .border(border, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .width(60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
        ) {
            LayoutPreviewGrid(layout)
        }
        Spacer(Modifier.height(4.dp))
        Text(layout.label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LayoutPreviewGrid(layout: PrintLayout) {
    val cellCount = when (layout) {
        PrintLayout.WALLET -> 9
        PrintLayout.SIZE_3_5X5 -> 4
        PrintLayout.SIZE_4X6 -> 2
        PrintLayout.FULL_PAGE -> 1
        PrintLayout.CONTACT_SHEET -> 12
    }
    val cols = when (layout) {
        PrintLayout.WALLET -> 3
        PrintLayout.SIZE_3_5X5 -> 2
        PrintLayout.SIZE_4X6 -> 1
        PrintLayout.FULL_PAGE -> 1
        PrintLayout.CONTACT_SHEET -> 4
    }
    val rows = ceil(cellCount.toDouble() / cols).toInt()
    Canvas(modifier = Modifier.size(40.dp)) {
        val pad = 3f
        val cellW = (size.width - pad * 2 - (cols - 1) * 2f) / cols
        val cellH = (size.height - pad * 2 - (rows - 1) * 2f) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (r * cols + c >= cellCount) break
                drawRect(
                    color = Color(0xFF90CAF9),
                    topLeft = Offset(pad + c * (cellW + 2f), pad + r * (cellH + 2f)),
                    size = Size(cellW, cellH)
                )
            }
        }
    }
}

@Composable
private fun PrintPreviewCanvas(options: PrintOptions) {
    val cellCount = when (options.layout) {
        PrintLayout.WALLET -> 9; PrintLayout.SIZE_3_5X5 -> 4; PrintLayout.SIZE_4X6 -> 2
        PrintLayout.FULL_PAGE -> 1; PrintLayout.CONTACT_SHEET -> options.contactSheetColumns * 3
    }
    val cols = when (options.layout) {
        PrintLayout.WALLET -> 3; PrintLayout.SIZE_3_5X5 -> 2; PrintLayout.SIZE_4X6 -> 1
        PrintLayout.FULL_PAGE -> 1; PrintLayout.CONTACT_SHEET -> options.contactSheetColumns
    }
    val rows = ceil(cellCount.toDouble() / cols).toInt()
    Canvas(modifier = Modifier.size(160.dp, 200.dp)) {
        val pad = 10f
        val gutter = 4f
        val cellW = (size.width - pad * 2 - (cols - 1) * gutter) / cols
        val cellH = (size.height - pad * 2 - (rows - 1) * gutter) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (r * cols + c >= cellCount) break
                val x = pad + c * (cellW + gutter)
                val y = pad + r * (cellH + gutter)
                drawRect(color = Color(0xFFBBDEFB), topLeft = Offset(x, y), size = Size(cellW, cellH))
                if (options.showBorder) {
                    drawRect(color = Color.Gray, topLeft = Offset(x, y), size = Size(cellW, cellH),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                }
            }
        }
    }
}
