package com.metitem.casapic.ui.adjustdate

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

enum class DateAdjustMode {
    SET_SPECIFIC,
    SHIFT_BY_OFFSET
}

@Composable
fun AdjustDateDialog(
    photoCount: Int,
    onAdjust: (newDateMillis: Long?, shiftMillis: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(DateAdjustMode.SET_SPECIFIC) }
    var dateText by remember { mutableStateOf("") }
    var shiftDays by remember { mutableFloatStateOf(0f) }
    var shiftHours by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Adjust Date and Time",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "${if (photoCount == 1) "1 photo" else "$photoCount photos"} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Mode selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == DateAdjustMode.SET_SPECIFIC,
                    onClick = { mode = DateAdjustMode.SET_SPECIFIC }
                )
                Text("Set to specific date/time", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == DateAdjustMode.SHIFT_BY_OFFSET,
                    onClick = { mode = DateAdjustMode.SHIFT_BY_OFFSET }
                )
                Text("Shift by offset", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))

            when (mode) {
                DateAdjustMode.SET_SPECIFIC -> {
                    Text(
                        "Date/Time (YYYY-MM-DD HH:MM:SS)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        placeholder = { Text("e.g. 2024-06-15 14:30:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DateAdjustMode.SHIFT_BY_OFFSET -> {
                    Text(
                        "Days: ${shiftDays.toInt()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = shiftDays,
                        onValueChange = { shiftDays = it },
                        valueRange = -365f..365f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Hours: ${shiftHours.toInt()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = shiftHours,
                        onValueChange = { shiftHours = it },
                        valueRange = -24f..24f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        when (mode) {
                            DateAdjustMode.SET_SPECIFIC -> {
                                val millis = parseDateTime(dateText)
                                if (millis != null) onAdjust(millis, null)
                            }
                            DateAdjustMode.SHIFT_BY_OFFSET -> {
                                val shiftMs = (shiftDays.toLong() * 86_400_000L) +
                                              (shiftHours.toLong() * 3_600_000L)
                                onAdjust(null, shiftMs)
                            }
                        }
                    },
                    enabled = when (mode) {
                        DateAdjustMode.SET_SPECIFIC -> parseDateTime(dateText) != null
                        DateAdjustMode.SHIFT_BY_OFFSET -> shiftDays != 0f || shiftHours != 0f
                    }
                ) { Text("Apply") }
            }
        }
    }
}

private fun parseDateTime(text: String): Long? {
    val trimmed = text.trim()
    // Accept "YYYY-MM-DD HH:MM:SS" or "YYYY-MM-DD"
    val fullPattern = Regex("""(\d{4})-(\d{2})-(\d{2})(?: (\d{2}):(\d{2}):(\d{2}))?""")
    val match = fullPattern.matchEntire(trimmed) ?: return null
    val (year, month, day) = match.destructured.let {
        Triple(
            match.groupValues[1].toIntOrNull() ?: return null,
            match.groupValues[2].toIntOrNull() ?: return null,
            match.groupValues[3].toIntOrNull() ?: return null
        )
    }
    val hour = match.groupValues[4].toIntOrNull() ?: 0
    val min = match.groupValues[5].toIntOrNull() ?: 0
    val sec = match.groupValues[6].toIntOrNull() ?: 0
    if (month < 1 || month > 12 || day < 1 || day > 31) return null
    // Simple epoch millis approximation (no timezone, UTC)
    val daysFromEpoch = daysFromEpoch(year, month, day)
    return daysFromEpoch * 86_400_000L + hour * 3_600_000L + min * 60_000L + sec * 1_000L
}

private fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
    // Zeller-like calculation for days since 1970-01-01
    val y = if (month <= 2) year - 1 else year
    val m = if (month <= 2) month + 12 else month
    val a = y / 100
    val b = 2 - a + a / 4
    val jd = (365.25 * (y + 4716)).toLong() + (30.6001 * (m + 1)).toLong() + day + b - 1524
    return jd - 2440588L // JD of 1970-01-01
}
