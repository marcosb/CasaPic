package com.metitem.casapic.ui.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.metitem.casapic.settings.SettingsKeys
import com.metitem.casapic.settings.SettingsRepository

@Composable
fun OptionsDialog(
    settings: SettingsRepository,
    onDismiss: () -> Unit
) {
    val tabs = listOf(
        "General", "E-Mail", "File Types", "Slideshow",
        "Printing", "Network", "Web Albums", "Name Tags"
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Options",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp)
            ) {
                when (selectedTab) {
                    0 -> GeneralTab(settings)
                    1 -> EmailTab(settings)
                    2 -> FileTypesTab(settings)
                    3 -> SlideshowTab(settings)
                    4 -> PrintingTab(settings)
                    5 -> NetworkTab(settings)
                    6 -> WebAlbumsTab(settings)
                    7 -> NameTagsTab(settings)
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
private fun GeneralTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()

    fun bool(key: String) = all[key] == "1"
    fun str(key: String) = all[key] ?: SettingsKeys.DEFAULTS[key] ?: ""

    OptionsSection("Display") {
        OptionCheckbox("Use special effects", bool(SettingsKeys.UI_SPECIAL_EFFECTS)) {
            settings.setBoolean(SettingsKeys.UI_SPECIAL_EFFECTS, it)
        }
        OptionCheckbox("Show tooltips", bool(SettingsKeys.UI_SHOW_TOOLTIPS)) {
            settings.setBoolean(SettingsKeys.UI_SHOW_TOOLTIPS, it)
        }
        OptionCheckbox("Single-click to exit Edit View", bool(SettingsKeys.UI_SINGLE_CLICK_EXIT_EDIT)) {
            settings.setBoolean(SettingsKeys.UI_SINGLE_CLICK_EXIT_EDIT, it)
        }
    }

    OptionsSection("Files") {
        OptionCheckbox("Detect duplicates while importing", bool(SettingsKeys.FILES_DETECT_DUPLICATES)) {
            settings.setBoolean(SettingsKeys.FILES_DETECT_DUPLICATES, it)
        }
        OptionCheckbox("Delete from disk without confirmation", bool(SettingsKeys.FILES_DELETE_WITHOUT_CONFIRM)) {
            settings.setBoolean(SettingsKeys.FILES_DELETE_WITHOUT_CONFIRM, it)
        }
        OptionCheckbox("Remove from album without confirmation", bool(SettingsKeys.FILES_REMOVE_ALBUM_WITHOUT_CONFIRM)) {
            settings.setBoolean(SettingsKeys.FILES_REMOVE_ALBUM_WITHOUT_CONFIRM, it)
        }
        OptionsLabeledField("Save imported pictures in:", str(SettingsKeys.IMPORT_SAVE_PATH)) {
            settings.set(SettingsKeys.IMPORT_SAVE_PATH, it)
        }
    }

    OptionsSection("Updates") {
        OptionCheckbox("Send anonymous usage stats", bool(SettingsKeys.TELEMETRY_ENABLED)) {
            settings.setBoolean(SettingsKeys.TELEMETRY_ENABLED, it)
        }
    }
}

@Composable
private fun EmailTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()
    fun bool(key: String) = all[key] == "1"
    fun str(key: String) = all[key] ?: SettingsKeys.DEFAULTS[key] ?: ""

    OptionsSection("Resize") {
        val sizePx = (all[SettingsKeys.EMAIL_RESIZE_MULTIPLE_PX]?.toIntOrNull() ?: 480).toFloat()
        Text(
            "Size for multiple pictures: ${sizePx.toInt()} px",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = sizePx,
            onValueChange = { settings.setInt(SettingsKeys.EMAIL_RESIZE_MULTIPLE_PX, it.toInt()) },
            valueRange = 240f..2048f,
            modifier = Modifier.fillMaxWidth()
        )

        OptionCheckbox("Send inline photos and captions", bool(SettingsKeys.EMAIL_INLINE_PHOTOS)) {
            settings.setBoolean(SettingsKeys.EMAIL_INLINE_PHOTOS, it)
        }
    }

    OptionsSection("Movies") {
        val moviesAs = str(SettingsKeys.EMAIL_MOVIES_AS)
        LabeledRadio("Send as first frame", moviesAs == "FIRST_FRAME") {
            settings.set(SettingsKeys.EMAIL_MOVIES_AS, "FIRST_FRAME")
        }
        LabeledRadio("Send as full movie", moviesAs == "FULL_MOVIE") {
            settings.set(SettingsKeys.EMAIL_MOVIES_AS, "FULL_MOVIE")
        }
    }
}

@Composable
private fun FileTypesTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()
    fun bool(key: String) = all[key] == "1"

    OptionsSection("Image Formats") {
        OptionCheckbox("BMP (.bmp)", bool(SettingsKeys.FILETYPES_BMP)) { settings.setBoolean(SettingsKeys.FILETYPES_BMP, it) }
        OptionCheckbox("GIF (.gif)", bool(SettingsKeys.FILETYPES_GIF)) { settings.setBoolean(SettingsKeys.FILETYPES_GIF, it) }
        OptionCheckbox("PNG (.png)", bool(SettingsKeys.FILETYPES_PNG)) { settings.setBoolean(SettingsKeys.FILETYPES_PNG, it) }
        OptionCheckbox("TGA (.tga)", bool(SettingsKeys.FILETYPES_TGA)) { settings.setBoolean(SettingsKeys.FILETYPES_TGA, it) }
        OptionCheckbox("TIFF (.tif / .tiff)", bool(SettingsKeys.FILETYPES_TIFF)) { settings.setBoolean(SettingsKeys.FILETYPES_TIFF, it) }
        OptionCheckbox("WebP (.webp)", bool(SettingsKeys.FILETYPES_WEBP)) { settings.setBoolean(SettingsKeys.FILETYPES_WEBP, it) }
        OptionCheckbox("Photoshop (.psd)", bool(SettingsKeys.FILETYPES_PSD)) { settings.setBoolean(SettingsKeys.FILETYPES_PSD, it) }
        OptionCheckbox("RAW formats (.cr2, .nef, .arw, .dng, …)", bool(SettingsKeys.FILETYPES_RAW)) {
            settings.setBoolean(SettingsKeys.FILETYPES_RAW, it)
        }
    }

    OptionsSection("Video") {
        OptionCheckbox("Movies (.avi, .mpg, .wmv)", bool(SettingsKeys.FILETYPES_MOVIES)) {
            settings.setBoolean(SettingsKeys.FILETYPES_MOVIES, it)
        }
        OptionCheckbox("QuickTime (.mov)", bool(SettingsKeys.FILETYPES_QUICKTIME)) {
            settings.setBoolean(SettingsKeys.FILETYPES_QUICKTIME, it)
        }
    }
}

@Composable
private fun SlideshowTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()
    fun bool(key: String) = all[key] == "1"
    fun str(key: String) = all[key] ?: SettingsKeys.DEFAULTS[key] ?: ""

    OptionsSection("Playback") {
        OptionCheckbox("Loop slideshow", bool(SettingsKeys.SLIDESHOW_LOOP)) {
            settings.setBoolean(SettingsKeys.SLIDESHOW_LOOP, it)
        }
        val delayMs = (all[SettingsKeys.SLIDESHOW_DELAY_MS]?.toIntOrNull() ?: 3000).toFloat()
        Text(
            "Delay between slides: ${(delayMs / 1000).toInt()} seconds",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = delayMs,
            onValueChange = { settings.setInt(SettingsKeys.SLIDESHOW_DELAY_MS, it.toInt()) },
            valueRange = 1000f..30000f,
            modifier = Modifier.fillMaxWidth()
        )
    }

    OptionsSection("Music") {
        OptionCheckbox("Play music tracks", bool(SettingsKeys.SLIDESHOW_MUSIC)) {
            settings.setBoolean(SettingsKeys.SLIDESHOW_MUSIC, it)
        }
        OptionsLabeledField("Music folder:", str(SettingsKeys.SLIDESHOW_MUSIC_FOLDER)) {
            settings.set(SettingsKeys.SLIDESHOW_MUSIC_FOLDER, it)
        }
    }
}

@Composable
private fun PrintingTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()
    fun bool(key: String) = all[key] == "1"
    fun str(key: String) = all[key] ?: SettingsKeys.DEFAULTS[key] ?: ""

    OptionsSection("Quality") {
        OptionCheckbox("Use high quality previews", bool(SettingsKeys.PRINT_HQ_PREVIEW)) {
            settings.setBoolean(SettingsKeys.PRINT_HQ_PREVIEW, it)
        }
        val quality = str(SettingsKeys.PRINT_QUALITY)
        LabeledRadio("Compatible (half-resolution)", quality == "COMPATIBLE") {
            settings.set(SettingsKeys.PRINT_QUALITY, "COMPATIBLE")
        }
        LabeledRadio("High Quality (full-resolution)", quality == "HIGH") {
            settings.set(SettingsKeys.PRINT_QUALITY, "HIGH")
        }
    }

    OptionsSection("Resampler") {
        val resampler = str(SettingsKeys.PRINT_RESAMPLER)
        LabeledRadio("General (Lanczos-3)", resampler == "LANCZOS3") {
            settings.set(SettingsKeys.PRINT_RESAMPLER, "LANCZOS3")
        }
        LabeledRadio("Extra sharp (Lanczos-8)", resampler == "LANCZOS8") {
            settings.set(SettingsKeys.PRINT_RESAMPLER, "LANCZOS8")
        }
    }
}

@Composable
private fun NetworkTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()
    fun str(key: String) = all[key] ?: ""

    OptionsSection("Proxy Settings") {
        OptionsLabeledField("Host:", str(SettingsKeys.NETWORK_PROXY_HOST)) {
            settings.set(SettingsKeys.NETWORK_PROXY_HOST, it)
        }
        OptionsLabeledField("Port:", str(SettingsKeys.NETWORK_PROXY_PORT)) {
            settings.set(SettingsKeys.NETWORK_PROXY_PORT, it)
        }
        OptionsLabeledField("Username:", str(SettingsKeys.NETWORK_PROXY_USER)) {
            settings.set(SettingsKeys.NETWORK_PROXY_USER, it)
        }
        OptionsLabeledField("Password:", str(SettingsKeys.NETWORK_PROXY_PASS)) {
            settings.set(SettingsKeys.NETWORK_PROXY_PASS, it)
        }
    }
}

@Composable
private fun WebAlbumsTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()
    fun bool(key: String) = all[key] == "1"
    fun str(key: String) = all[key] ?: SettingsKeys.DEFAULTS[key] ?: ""

    OptionsSection("Upload") {
        val sizePx = str(SettingsKeys.UPLOAD_SIZE_PX)
        Text("Default upload size", style = MaterialTheme.typography.bodySmall)
        listOf("1600", "2048", "Original").forEach { option ->
            LabeledRadio(option + if (option != "Original") " px" else "", sizePx == option) {
                settings.set(SettingsKeys.UPLOAD_SIZE_PX, option)
            }
        }
        Spacer(Modifier.height(4.dp))
        OptionCheckbox("Upload previews first when syncing", bool(SettingsKeys.UPLOAD_PREVIEW_FIRST)) {
            settings.setBoolean(SettingsKeys.UPLOAD_PREVIEW_FIRST, it)
        }
        OptionCheckbox("Preserve original image quality", bool(SettingsKeys.UPLOAD_PRESERVE_QUALITY)) {
            settings.setBoolean(SettingsKeys.UPLOAD_PRESERVE_QUALITY, it)
        }
        OptionCheckbox("Sync starred photos only", bool(SettingsKeys.UPLOAD_SYNC_STARRED_ONLY)) {
            settings.setBoolean(SettingsKeys.UPLOAD_SYNC_STARRED_ONLY, it)
        }
        OptionCheckbox("Don't confirm every sync", bool(SettingsKeys.UPLOAD_NO_CONFIRM_SYNC)) {
            settings.setBoolean(SettingsKeys.UPLOAD_NO_CONFIRM_SYNC, it)
        }
    }

    OptionsSection("Watermark") {
        val watermarkEnabled = bool(SettingsKeys.UPLOAD_WATERMARK_ENABLED)
        OptionCheckbox("Add watermark for all uploads", watermarkEnabled) {
            settings.setBoolean(SettingsKeys.UPLOAD_WATERMARK_ENABLED, it)
        }
        if (watermarkEnabled) {
            OptionsLabeledField("Watermark text:", str(SettingsKeys.UPLOAD_WATERMARK_TEXT)) {
                settings.set(SettingsKeys.UPLOAD_WATERMARK_TEXT, it)
            }
        }
    }
}

@Composable
private fun NameTagsTab(settings: SettingsRepository) {
    val all by settings.allSettings.collectAsState()
    fun bool(key: String) = all[key] == "1"

    val faceEnabled = bool(SettingsKeys.FACE_DETECTION_ENABLED)
    val suggestionThreshold = (all[SettingsKeys.FACE_SUGGESTION_THRESHOLD]?.toIntOrNull() ?: 85).toFloat()
    val clusterThreshold = (all[SettingsKeys.FACE_CLUSTER_THRESHOLD]?.toIntOrNull() ?: 85).toFloat()

    OptionsSection("Detection") {
        OptionCheckbox("Enable face detection", faceEnabled) {
            settings.setBoolean(SettingsKeys.FACE_DETECTION_ENABLED, it)
        }
        OptionCheckbox(
            "Enable suggestions",
            bool(SettingsKeys.FACE_SUGGESTIONS_ENABLED),
            enabled = faceEnabled
        ) { settings.setBoolean(SettingsKeys.FACE_SUGGESTIONS_ENABLED, it) }
    }

    OptionsSection("Thresholds") {
        Text(
            "Suggestion threshold: ${suggestionThreshold.toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = if (faceEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = suggestionThreshold,
            onValueChange = { settings.setInt(SettingsKeys.FACE_SUGGESTION_THRESHOLD, it.toInt()) },
            valueRange = 0f..100f,
            enabled = faceEnabled,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Cluster threshold: ${clusterThreshold.toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = if (faceEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = clusterThreshold,
            onValueChange = { settings.setInt(SettingsKeys.FACE_CLUSTER_THRESHOLD, it.toInt()) },
            valueRange = 0f..100f,
            enabled = faceEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }

    OptionsSection("Storage") {
        OptionCheckbox(
            "Store name tags in photo (XMP)",
            bool(SettingsKeys.FACE_STORE_IN_PHOTO),
            enabled = faceEnabled
        ) { settings.setBoolean(SettingsKeys.FACE_STORE_IN_PHOTO, it) }
    }
}

// ---- Shared helper composables ----

@Composable
private fun OptionsSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    content()
}

@Composable
private fun OptionCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LabeledRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OptionsLabeledField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
