package com.metitem.casapic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metitem.casapic.db.Photos
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun PropertiesPanel(
    photo: Photos?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Properties",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (photo == null) {
            Text(
                text = "Select an item to show the properties.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        PropRow("File", photo.file_name)
        PropRow("Path", photo.file_path)
        photo.file_size?.let { PropRow("Size", formatFileSize(it)) }
        if (photo.width != null && photo.height != null) {
            PropRow("Dimensions", "${photo.width} × ${photo.height}")
        }
        photo.capture_date_millis?.let { PropRow("Captured", formatDate(it)) }
        photo.modified_date_millis?.let { PropRow("Modified", formatDate(it)) }

        if (photo.camera_make != null || photo.camera_model != null) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Camera")
            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
            photo.camera_make?.let { PropRow("Make", it) }
            photo.camera_model?.let { PropRow("Model", it) }
            photo.focal_length?.let { PropRow("Focal Length", "%.1fmm".format(it)) }
            photo.f_number?.let { PropRow("Aperture", "f/%.1f".format(it)) }
            photo.iso?.let { PropRow("ISO", it.toString()) }
            photo.exposure_time?.let { PropRow("Exposure", it) }
            photo.flash?.let { PropRow("Flash", if (it != 0L) "Yes" else "No") }
        }

        if (photo.gps_latitude != null || photo.gps_longitude != null) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Location")
            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
            photo.gps_latitude?.let { PropRow("Latitude", "%.6f".format(it)) }
            photo.gps_longitude?.let { PropRow("Longitude", "%.6f".format(it)) }
            photo.gps_altitude?.let { PropRow("Altitude", "%.1fm".format(it)) }
        }

        if (photo.iptc_copyright != null || photo.iptc_author != null || photo.iptc_keywords != null) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("IPTC")
            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
            photo.iptc_copyright?.let { PropRow("Copyright", it) }
            photo.iptc_author?.let { PropRow("Author", it) }
            photo.iptc_headline?.let { PropRow("Headline", it) }
            photo.iptc_keywords?.let { PropRow("Keywords", it.replace(";", ", ")) }
        }

        if (photo.xmp_rating != null || photo.xmp_label != null || photo.xmp_subject != null) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("XMP")
            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
            photo.xmp_rating?.let { PropRow("Rating", "★".repeat(it.toInt().coerceIn(0, 5))) }
            photo.xmp_label?.let { PropRow("Label", it) }
            photo.xmp_subject?.let { PropRow("Subject", it.replace(";", ", ")) }
        }

        photo.description?.let {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Description")
            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        photo.jpeg_quality?.let {
            Spacer(Modifier.height(8.dp))
            PropRow("JPEG Quality", it.toString())
        }
        photo.unique_id?.let { PropRow("Unique ID", it) }
        PropRow("Star Rating", "★".repeat(photo.star_rating.toInt().coerceIn(0, 5)).ifEmpty { "None" })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun formatDate(millis: Long): String = runCatching {
    val instant = Instant.fromEpochMilliseconds(millis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    "%04d-%02d-%02d %02d:%02d".format(
        local.year, local.month, local.day,
        local.hour, local.minute
    )
}.getOrElse { millis.toString() }

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}
