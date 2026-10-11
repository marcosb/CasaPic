package com.metitem.casapic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.model.ScanState

@Composable
fun ProcessingStatusStrip(
    scanState: ScanState,
    detectionQueueSize: Int,
    fingerprintQueueSize: Int,
    onCancelScan: () -> Unit
) {
    val visible = scanState is ScanState.Scanning ||
        detectionQueueSize > 0 || fingerprintQueueSize > 0

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (scanState is ScanState.Scanning) {
                StatusRow(
                    label = "Scanning \"${scanState.folderName}\" — ${scanState.current} / ${scanState.total} photos",
                    progress = if (scanState.total > 0) scanState.current.toFloat() / scanState.total else 0f,
                    showCancel = true,
                    onCancel = onCancelScan
                )
            }
            if (detectionQueueSize > 0) {
                StatusRow(
                    label = "Detecting faces — $detectionQueueSize photos remaining",
                    progress = null,
                    showCancel = false,
                    onCancel = {}
                )
            }
            if (fingerprintQueueSize > 0) {
                StatusRow(
                    label = "Calculating fingerprints — $fingerprintQueueSize photos remaining",
                    progress = null,
                    showCancel = false,
                    onCancel = {}
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    progress: Float?,
    showCancel: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(120.dp).height(6.dp)
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.width(120.dp).height(6.dp))
        }
        if (showCancel) {
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Text("✕", fontSize = 12.sp)
            }
        } else {
            Spacer(Modifier.width(28.dp))
        }
    }
}
