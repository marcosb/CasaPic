package com.metitem.casapic.viewmodel

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.db.Face_detections
import com.metitem.casapic.db.Photo_edits
import com.metitem.casapic.db.Photos
import com.metitem.casapic.image.computeHistogram
import com.metitem.casapic.metadata.AutoColorEdit
import com.metitem.casapic.metadata.AutoLightEdit
import com.metitem.casapic.metadata.BoostEdit
import com.metitem.casapic.metadata.BwEdit
import com.metitem.casapic.metadata.FilteredBwEdit
import com.metitem.casapic.metadata.OrtonEdit
import com.metitem.casapic.metadata.PolarizeEdit
import com.metitem.casapic.metadata.CropEdit
import com.metitem.casapic.metadata.EnhanceEdit
import com.metitem.casapic.metadata.FilmGrainEdit
import com.metitem.casapic.metadata.FillLightEdit
import com.metitem.casapic.metadata.FinetuneEdit
import com.metitem.casapic.metadata.GlowEdit
import com.metitem.casapic.metadata.HighlightsEdit
import com.metitem.casapic.metadata.LomoEdit
import com.metitem.casapic.metadata.PhotoEdit
import com.metitem.casapic.metadata.RedEyeEdit
import com.metitem.casapic.metadata.RedEyeRegion
import com.metitem.casapic.metadata.RetroluxEdit
import com.metitem.casapic.metadata.RotateEdit
import com.metitem.casapic.metadata.SaturationEdit
import com.metitem.casapic.metadata.SepiaEdit
import com.metitem.casapic.metadata.ShadowsEdit
import com.metitem.casapic.metadata.SharpnessEdit
import com.metitem.casapic.metadata.SoftFocusEdit
import com.metitem.casapic.metadata.TiltEdit
import com.metitem.casapic.metadata.TintEdit
import com.metitem.casapic.metadata.VignetteEdit
import com.metitem.casapic.model.HistogramData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

class EditViewModel(
    private val db: CasaPicDatabase,
    initialPhotoId: Long
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _photoId = MutableStateFlow(initialPhotoId)

    val photo: StateFlow<Photos?> = run {
        val flow = MutableStateFlow<Photos?>(null)
        scope.launch {
            _photoId.collect { id ->
                flow.value = db.casaPicQueries.getPhotoById(id).executeAsOneOrNull()
            }
        }
        flow.asStateFlow()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val editStack: StateFlow<List<Photo_edits>> =
        _photoId.flatMapLatest { id ->
            db.casaPicQueries.getActiveEditsForPhoto(id)
                .asFlow()
                .mapToList(Dispatchers.Default)
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val faceDetections: StateFlow<List<Face_detections>> =
        _photoId.flatMapLatest { id ->
            db.casaPicQueries.getFaceDetectionsForPhoto(id)
                .asFlow()
                .mapToList(Dispatchers.Default)
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val caption: StateFlow<String> = run {
        val flow = MutableStateFlow("")
        scope.launch {
            _photoId.collect { id ->
                flow.value = db.casaPicQueries.getPhotoById(id)
                    .executeAsOneOrNull()?.caption ?: ""
            }
        }
        flow.asStateFlow()
    }

    val histogram: StateFlow<HistogramData?> = run {
        val flow = MutableStateFlow<HistogramData?>(null)
        scope.launch(Dispatchers.Default) {
            combine(photo, editStack) { p, stack -> p to stack }.collect { (p, stack) ->
                flow.value = if (p != null) computeHistogram(p.file_path, stack) else null
            }
        }
        flow.asStateFlow()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filmstripPhotos: StateFlow<List<Photos>> =
        photo.flatMapLatest { p ->
            when {
                p == null -> flowOf(emptyList())
                p.folder_id != null -> db.casaPicQueries
                    .getPhotosForFolder(p.folder_id, 0L, 500L, 0L)
                    .asFlow().mapToList(Dispatchers.Default)
                else -> db.casaPicQueries
                    .getPhotosAll(0L, 500L, 0L)
                    .asFlow().mapToList(Dispatchers.Default)
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val currentIndex: StateFlow<Int> =
        combine(filmstripPhotos, _photoId) { photos, id ->
            photos.indexOfFirst { it.id == id }.coerceAtLeast(0)
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val _isCropMode = MutableStateFlow(false)
    val isCropMode: StateFlow<Boolean> = _isCropMode.asStateFlow()

    fun enterCropMode() { _isCropMode.value = true }
    fun cancelCropMode() { _isCropMode.value = false }
    fun confirmCrop(left: Float, top: Float, right: Float, bottom: Float) {
        applyCrop(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
        _isCropMode.value = false
    }

    fun navigateTo(photoId: Long) {
        _photoId.value = photoId
    }

    fun navigateToPrev() {
        val photos = filmstripPhotos.value
        val idx = currentIndex.value
        if (idx > 0) navigateTo(photos[idx - 1].id)
    }

    fun navigateToNext() {
        val photos = filmstripPhotos.value
        val idx = currentIndex.value
        if (idx < photos.lastIndex) navigateTo(photos[idx + 1].id)
    }

    fun applyEdit(edit: PhotoEdit) {
        scope.launch(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            db.casaPicQueries.insertPhotoEdit(
                photo_id = _photoId.value,
                edit_proto = PhotoEdit.ADAPTER.encode(edit),
                applied_date_millis = now
            )
        }
    }

    fun undoEdit(editId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.markEditUndone(editId)
        }
    }

    fun undoAllEdits() {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.undoAllEditsForPhoto(_photoId.value)
        }
    }

    fun redoEdit(editId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.redoEdit(editId)
        }
    }

    fun updateCaption(text: String) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.updatePhotoCaption(text.ifBlank { null }, _photoId.value)
        }
    }

    fun rotateClockwise() = applyEdit(PhotoEdit(rotate = RotateEdit(degrees = 90)))
    fun rotateCounterClockwise() = applyEdit(PhotoEdit(rotate = RotateEdit(degrees = -90)))
    fun applyAutoContrast() = applyEdit(PhotoEdit(autolight = AutoLightEdit(presence = true)))
    fun applyAutoColor() = applyEdit(PhotoEdit(autocolor = AutoColorEdit(presence = true)))
    fun applyIFeelLucky() {
        applyEdit(PhotoEdit(autolight = AutoLightEdit(presence = true)))
        applyEdit(PhotoEdit(autocolor = AutoColorEdit(presence = true)))
    }

    fun applyCrop(left: Float, top: Float, right: Float, bottom: Float) =
        applyEdit(PhotoEdit(crop = CropEdit(left = left, top = top, right = right, bottom = bottom)))

    fun applyFillLight(value: Float) =
        applyEdit(PhotoEdit(fill_light = FillLightEdit(value_ = value)))

    fun applyHighlights(value: Float) =
        applyEdit(PhotoEdit(highlights = HighlightsEdit(value_ = value)))

    fun applyShadows(value: Float) =
        applyEdit(PhotoEdit(shadows = ShadowsEdit(value_ = value)))

    fun applyColorTemp(value: Float) =
        applyEdit(PhotoEdit(finetune = FinetuneEdit(color_temp = value)))

    fun applySaturation(value: Float) =
        applyEdit(PhotoEdit(saturation = SaturationEdit(value_ = value)))

    fun applySharpness(value: Float) =
        applyEdit(PhotoEdit(sharpness = SharpnessEdit(value_ = value)))

    fun applySepia(strength: Float = 1f) =
        applyEdit(PhotoEdit(sepia = SepiaEdit(strength = strength)))

    fun applyBlackAndWhite(strength: Float = 1f) =
        applyEdit(PhotoEdit(bw = BwEdit(strength = strength)))

    fun applySoftFocus(strength: Float = 0.5f) =
        applyEdit(PhotoEdit(softfocus = SoftFocusEdit(strength = strength)))

    fun applyFilmGrain(strength: Float = 0.5f) =
        applyEdit(PhotoEdit(filmgrain = FilmGrainEdit(strength = strength)))

    fun applyGlow(strength: Float = 0.5f) =
        applyEdit(PhotoEdit(glow = GlowEdit(strength = strength)))

    fun applyVignette(strength: Float = 0.5f) =
        applyEdit(PhotoEdit(vignette = VignetteEdit(strength = strength, size = 0.5f, roundness = 0.5f)))

    fun applyLomo() =
        applyEdit(PhotoEdit(lomo = LomoEdit(saturation = 0.5f, contrast = 0.5f)))

    fun applyStraighten(angle: Float) =
        applyEdit(PhotoEdit(tilt = TiltEdit(angle = angle, scale = 1.15f)))

    fun applyRetrolux(strength: Float = 0.5f) =
        applyEdit(PhotoEdit(retrolux = RetroluxEdit(strength = strength)))

    fun applyTint(r: Float, g: Float, b: Float, strength: Float = 1f) =
        applyEdit(PhotoEdit(tint = TintEdit(r = r, g = g, b = b, strength = strength)))

    fun applyBoost(strength: Float = 0.7f) =
        applyEdit(PhotoEdit(boost = BoostEdit(strength = strength)))

    fun applyFilteredBw(strength: Float = 1f) =
        applyEdit(PhotoEdit(filtered_bw = FilteredBwEdit(strength = strength)))

    fun applyOrton(strength: Float = 0.5f) =
        applyEdit(PhotoEdit(orton = OrtonEdit(strength = strength)))

    fun applyPolarize(strength: Float = 0.7f) =
        applyEdit(PhotoEdit(polarize = PolarizeEdit(strength = strength)))

    fun applyRedEyeAuto() {
        val faces = faceDetections.value
        if (faces.isEmpty()) return
        val regions = faces.flatMap { face ->
            val fw = face.box_right - face.box_left
            val fh = face.box_bottom - face.box_top
            val eyeY = (face.box_top + fh * 0.34).toFloat()
            val radius = (fw * 0.11).toFloat()
            listOf(
                RedEyeRegion(x = (face.box_left + fw * 0.32).toFloat(), y = eyeY, radius = radius),
                RedEyeRegion(x = (face.box_left + fw * 0.68).toFloat(), y = eyeY, radius = radius)
            )
        }
        applyEdit(PhotoEdit(redeye = RedEyeEdit(regions = regions)))
    }

    fun addManualFace(left: Float, top: Float, right: Float, bottom: Float) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.insertFaceDetection(
                photo_id = _photoId.value,
                face_group_id = null,
                box_left = left.toDouble(),
                box_top = top.toDouble(),
                box_right = right.toDouble(),
                box_bottom = bottom.toDouble(),
                confidence = null,
                left_eye_open_prob = null,
                right_eye_open_prob = null,
                smiling_prob = null,
                embedding = null,
                tracking_id = null,
                is_manual = 1L
            )
        }
    }

    fun assignFaceToGroup(detectionId: Long, groupId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.updateFaceGroupId(groupId, detectionId)
        }
    }

    fun deleteFace(detectionId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.deleteFaceDetection(detectionId)
        }
    }

    fun resetFaces() {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.deleteFaceDetectionsForPhoto(_photoId.value)
        }
    }
}
