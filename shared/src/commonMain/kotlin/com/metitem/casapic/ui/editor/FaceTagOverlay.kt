package com.metitem.casapic.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.db.Albums
import com.metitem.casapic.db.Face_detections
import com.metitem.casapic.viewmodel.EditViewModel
import com.metitem.casapic.viewmodel.PeopleViewModel
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun FaceTagOverlay(
    editViewModel: EditViewModel,
    peopleViewModel: PeopleViewModel,
    modifier: Modifier = Modifier
) {
    val faces by editViewModel.faceDetections.collectAsState()
    val groups by peopleViewModel.faceGroups.collectAsState()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var drawStart by remember { mutableStateOf<Offset?>(null) }
    var drawEnd by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }

    var assignTarget by remember { mutableStateOf<Face_detections?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        drawStart = offset
                        drawEnd = offset
                        isDrawing = true
                    },
                    onDrag = { change, _ ->
                        drawEnd = change.position
                    },
                    onDragEnd = {
                        val start = drawStart
                        val end = drawEnd
                        if (start != null && end != null && canvasSize != IntSize.Zero) {
                            val w = canvasSize.width.toFloat()
                            val h = canvasSize.height.toFloat()
                            val left = min(start.x, end.x) / w
                            val top = min(start.y, end.y) / h
                            val right = (min(start.x, end.x) + abs(end.x - start.x)) / w
                            val bottom = (min(start.y, end.y) + abs(end.y - start.y)) / h
                            if (right - left > 0.02f && bottom - top > 0.02f) {
                                editViewModel.addManualFace(left, top, right, bottom)
                            }
                        }
                        drawStart = null
                        drawEnd = null
                        isDrawing = false
                    },
                    onDragCancel = {
                        drawStart = null
                        drawEnd = null
                        isDrawing = false
                    }
                )
            }
            .drawWithContent {
                drawContent()
                // Draw in-progress rectangle
                val start = drawStart
                val end = drawEnd
                if (isDrawing && start != null && end != null) {
                    drawRect(
                        color = Color.Yellow,
                        topLeft = Offset(min(start.x, end.x), min(start.y, end.y)),
                        size = Size(abs(end.x - start.x), abs(end.y - start.y)),
                        style = Stroke(width = 2f)
                    )
                }
            }
    ) {
        // Draw face boxes and name chips
        faces.forEach { face ->
            if (canvasSize != IntSize.Zero) {
                val w = canvasSize.width
                val h = canvasSize.height
                val left = (face.box_left * w).roundToInt()
                val top = (face.box_top * h).roundToInt()
                val right = (face.box_right * w).roundToInt()
                val bottom = (face.box_bottom * h).roundToInt()
                val boxWidth = right - left
                val boxHeight = bottom - top

                Box(
                    modifier = Modifier
                        .offset { IntOffset(left, top) }
                        .size(boxWidth.dp / 2, boxHeight.dp / 2)
                        .border(2.dp, Color.Yellow, RoundedCornerShape(2.dp))
                )

                val groupName = face.face_group_id?.let { gid ->
                    groups.find { it.id == gid }?.name
                }

                FaceChip(
                    label = groupName ?: "Who is this?",
                    isAssigned = groupName != null,
                    groups = groups,
                    onAssign = { group -> editViewModel.assignFaceToGroup(face.id, group.id) },
                    onDelete = { editViewModel.deleteFace(face.id) },
                    modifier = Modifier.offset {
                        IntOffset(left, (bottom - top / 4))
                    }
                )
            }
        }
    }

    assignTarget?.let { face ->
        AssignFaceDialog(
            groups = groups,
            onAssign = { group ->
                editViewModel.assignFaceToGroup(face.id, group.id)
                assignTarget = null
            },
            onNewPerson = { name ->
                peopleViewModel.createNewGroup(name) { groupId ->
                    editViewModel.assignFaceToGroup(face.id, groupId)
                }
                assignTarget = null
            },
            onDismiss = { assignTarget = null }
        )
    }
}

@Composable
private fun FaceChip(
    label: String,
    isAssigned: Boolean,
    groups: List<Albums>,
    onAssign: (Albums) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        Row(
            modifier = Modifier
                .background(
                    if (isAssigned) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, color = if (isAssigned)
                MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "✕",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onDelete)
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onAssign(group)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AssignFaceDialog(
    groups: List<Albums>,
    onAssign: (Albums) -> Unit,
    onNewPerson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPersonName by remember { mutableStateOf("") }
    var showNewPerson by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who is this?") },
        text = {
            Column {
                if (groups.isNotEmpty()) {
                    Text("Select person:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    groups.forEach { group ->
                        TextButton(onClick = { onAssign(group) }) {
                            Text(group.name)
                        }
                    }
                }
                if (showNewPerson) {
                    OutlinedTextField(
                        value = newPersonName,
                        onValueChange = { newPersonName = it },
                        label = { Text("New person's name") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (showNewPerson) {
                Button(
                    onClick = { if (newPersonName.isNotBlank()) onNewPerson(newPersonName.trim()) },
                    enabled = newPersonName.isNotBlank()
                ) { Text("Add") }
            } else {
                TextButton(onClick = { showNewPerson = true }) { Text("New person…") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
