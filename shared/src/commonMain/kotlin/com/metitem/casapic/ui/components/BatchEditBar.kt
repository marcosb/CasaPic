package com.metitem.casapic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BatchEditBar(
    selectedCount: Int,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onStar: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedCount > 1,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRotateCW, modifier = Modifier.height(32.dp)) {
                Text("↻ CW", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onRotateCCW, modifier = Modifier.height(32.dp)) {
                Text("↺ CCW", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onStar, modifier = Modifier.height(32.dp)) {
                Text("★ Star", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onHide, modifier = Modifier.height(32.dp)) {
                Text("Hide", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onDelete, modifier = Modifier.height(32.dp)) {
                Text(
                    "Delete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = onClearSelection, modifier = Modifier.height(32.dp)) {
                Text("✕", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
