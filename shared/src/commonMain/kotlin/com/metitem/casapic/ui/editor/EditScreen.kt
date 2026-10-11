package com.metitem.casapic.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metitem.casapic.ui.components.HistogramWidget
import com.metitem.casapic.ui.components.PropertiesPanel
import com.metitem.casapic.ui.components.ThumbnailStrip
import com.metitem.casapic.viewmodel.EditViewModel
import com.metitem.casapic.viewmodel.PeopleViewModel

@Composable
fun EditScreen(
    viewModel: EditViewModel,
    onBack: () -> Unit,
    peopleViewModel: PeopleViewModel? = null
) {
    val photo by viewModel.photo.collectAsState()
    val editStack by viewModel.editStack.collectAsState()
    val caption by viewModel.caption.collectAsState()
    val filmstripPhotos by viewModel.filmstripPhotos.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isCropMode by viewModel.isCropMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top nav bar
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back to Library") }
            TextButton(
                onClick = viewModel::navigateToPrev,
                enabled = currentIndex > 0
            ) { Text("◄") }
            TextButton(
                onClick = viewModel::navigateToNext,
                enabled = currentIndex < filmstripPhotos.lastIndex
            ) { Text("►") }
            Spacer(Modifier.weight(1f))
            photo?.let {
                Text(
                    "${currentIndex + 1} / ${filmstripPhotos.size}  •  ${it.file_name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::undoAllEdits) { Text("Undo All") }
        }
        HorizontalDivider()

        if (filmstripPhotos.size > 1) {
            ThumbnailStrip(
                photos = filmstripPhotos,
                currentIndex = currentIndex,
                onPhotoClick = viewModel::navigateTo,
                modifier = Modifier.fillMaxWidth().height(76.dp)
            )
            HorizontalDivider()
        }

        // Main body
        Row(modifier = Modifier.weight(1f)) {
            // Left edit panel
            EditPanelColumn(
                viewModel = viewModel,
                modifier = Modifier.width(220.dp)
            )
            VerticalDivider()

            // Center image canvas with face tag overlay
            val filePath = photo?.file_path
            Box(modifier = Modifier.weight(1f)) {
                if (filePath != null) {
                    SkiaImageCanvas(
                        filePath = filePath,
                        editStack = editStack,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (peopleViewModel != null && !isCropMode) {
                        FaceTagOverlay(
                            editViewModel = viewModel,
                            peopleViewModel = peopleViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isCropMode) {
                        CropOverlay(
                            imageWidth = photo?.width?.toInt() ?: 0,
                            imageHeight = photo?.height?.toInt() ?: 0,
                            onApply = viewModel::confirmCrop,
                            onCancel = viewModel::cancelCropMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No photo selected")
                    }
                }
            }

            VerticalDivider()

            // Right properties panel
            PropertiesPanel(
                photo = photo,
                modifier = Modifier.width(220.dp)
            )
        }

        HorizontalDivider()

        // Caption row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = caption,
                onValueChange = viewModel::updateCaption,
                placeholder = { Text("Add a caption…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(40.dp)
            )
        }
    }
}

@Composable
private fun EditPanelColumn(
    viewModel: EditViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Basic Fixes", "Tuning", "Effects")
    val histogram by viewModel.histogram.collectAsState()

    Column(modifier = modifier.fillMaxHeight()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (selectedTab) {
                0 -> BasicFixesPanel(viewModel)
                1 -> TuningPanel(viewModel)
                2 -> EffectsPanel(viewModel)
            }
        }

        val hist = histogram
        if (hist != null) {
            HorizontalDivider()
            HistogramWidget(
                data = hist,
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
        }
    }
}

@Composable
private fun BasicFixesPanel(viewModel: EditViewModel) {
    val isCropMode by viewModel.isCropMode.collectAsState()

    EditSectionTitle("Auto")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = viewModel::applyIFeelLucky, modifier = Modifier.weight(1f)) {
            Text("I'm Feeling Lucky", style = MaterialTheme.typography.labelSmall)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = viewModel::applyAutoContrast, modifier = Modifier.weight(1f)) {
            Text("Auto Contrast", style = MaterialTheme.typography.labelSmall)
        }
        Button(onClick = viewModel::applyAutoColor, modifier = Modifier.weight(1f)) {
            Text("Auto Color", style = MaterialTheme.typography.labelSmall)
        }
    }

    EditSectionTitle("Rotate")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = viewModel::rotateCounterClockwise, modifier = Modifier.weight(1f)) {
            Text("↺ CCW", style = MaterialTheme.typography.labelSmall)
        }
        Button(onClick = viewModel::rotateClockwise, modifier = Modifier.weight(1f)) {
            Text("↻ CW", style = MaterialTheme.typography.labelSmall)
        }
    }

    EditSectionTitle("Fill Light")
    var fillLight by remember { mutableFloatStateOf(0f) }
    Slider(
        value = fillLight,
        onValueChange = { fillLight = it },
        onValueChangeFinished = { viewModel.applyFillLight(fillLight) },
        valueRange = 0f..1f
    )

    EditSectionTitle("Crop")
    Button(
        onClick = if (isCropMode) viewModel::cancelCropMode else viewModel::enterCropMode,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (isCropMode) "Cancel Crop" else "Crop Photo",
            style = MaterialTheme.typography.labelSmall
        )
    }

    EditSectionTitle("Straighten")
    var straighten by remember { mutableFloatStateOf(0f) }
    Slider(
        value = straighten,
        onValueChange = { straighten = it },
        onValueChangeFinished = { if (straighten != 0f) viewModel.applyStraighten(straighten) },
        valueRange = -45f..45f
    )
    Text("${straighten.toInt()}°", style = MaterialTheme.typography.labelSmall)

    EditSectionTitle("Red Eye")
    Button(onClick = viewModel::applyRedEyeAuto, modifier = Modifier.fillMaxWidth()) {
        Text("Auto Fix Red Eye", style = MaterialTheme.typography.labelSmall)
    }

    EditSectionTitle("Retouch & Text")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
            Text("Retouch", style = MaterialTheme.typography.labelSmall)
        }
        Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
            Text("Text", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TuningPanel(viewModel: EditViewModel) {
    var fillLight by remember { mutableFloatStateOf(0f) }
    var highlights by remember { mutableFloatStateOf(0f) }
    var shadows by remember { mutableFloatStateOf(0f) }
    var colorTemp by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }

    EditSectionTitle("Fill Light")
    Slider(value = fillLight, onValueChange = { fillLight = it },
        onValueChangeFinished = { viewModel.applyFillLight(fillLight) },
        valueRange = 0f..1f)

    EditSectionTitle("Highlights")
    Slider(value = highlights, onValueChange = { highlights = it },
        onValueChangeFinished = { viewModel.applyHighlights(highlights) },
        valueRange = -1f..1f)

    EditSectionTitle("Shadows")
    Slider(value = shadows, onValueChange = { shadows = it },
        onValueChangeFinished = { viewModel.applyShadows(shadows) },
        valueRange = -1f..1f)

    EditSectionTitle("Color Temperature")
    Slider(value = colorTemp, onValueChange = { colorTemp = it },
        onValueChangeFinished = { viewModel.applyColorTemp(colorTemp) },
        valueRange = -1f..1f)

    EditSectionTitle("Saturation")
    Slider(value = saturation, onValueChange = { saturation = it },
        onValueChangeFinished = { viewModel.applySaturation(saturation) },
        valueRange = -1f..1f)
}

@Composable
private fun EffectsPanel(viewModel: EditViewModel) {
    val effects = listOf(
        "Sharpen"       to { viewModel.applySharpness(0.7f) },
        "Film Grain"    to { viewModel.applyFilmGrain() },
        "Tint"          to { viewModel.applyTint(1.0f, 0.75f, 0.5f, 0.35f) },
        "Saturation+"   to { viewModel.applySaturation(0.5f) },
        "Soft Focus"    to { viewModel.applySoftFocus() },
        "Glow"          to { viewModel.applyGlow() },
        "Filtered B&W"  to { viewModel.applyFilteredBw() },
        "Orton-ish"     to { viewModel.applyOrton() },
        "Boost"         to { viewModel.applyBoost() },
        "Polarize"      to { viewModel.applyPolarize() },
        "Lomo"          to { viewModel.applyLomo() },
        "Retrolux"      to { viewModel.applyRetrolux() },
        "Sepia"         to { viewModel.applySepia() },
        "Black & White" to { viewModel.applyBlackAndWhite() },
        "Vignette"      to { viewModel.applyVignette() }
    )

    effects.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { (name, action) ->
                Button(
                    onClick = { action() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EditSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}
