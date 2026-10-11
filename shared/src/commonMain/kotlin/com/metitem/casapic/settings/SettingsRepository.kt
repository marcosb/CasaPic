package com.metitem.casapic.settings

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.metitem.casapic.db.CasaPicDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsRepository(private val db: CasaPicDatabase) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = MutableStateFlow<Map<String, String>>(SettingsKeys.DEFAULTS)

    init {
        scope.launch {
            db.casaPicQueries.getAllSettings()
                .asFlow()
                .mapToList(Dispatchers.Default)
                .collect { rows ->
                    val merged = SettingsKeys.DEFAULTS.toMutableMap()
                    rows.forEach { merged[it.key] = it.value_ }
                    cache.value = merged
                }
        }
    }

    val allSettings: StateFlow<Map<String, String>> = cache.asStateFlow()

    fun get(key: String): String = cache.value[key] ?: SettingsKeys.DEFAULTS[key] ?: ""

    fun getBoolean(key: String): Boolean = get(key) == "1"

    fun getBoolean(key: String, default: Boolean): Boolean {
        val raw = cache.value[key] ?: SettingsKeys.DEFAULTS[key] ?: return default
        return raw == "1"
    }

    fun getInt(key: String): Int = get(key).toIntOrNull() ?: SettingsKeys.DEFAULTS[key]?.toIntOrNull() ?: 0

    fun getFloat(key: String, default: Float = 0f): Float =
        get(key).toFloatOrNull() ?: SettingsKeys.DEFAULTS[key]?.toFloatOrNull() ?: default

    fun setFloat(key: String, value: Float) = set(key, value.toString())

    fun set(key: String, value: String) {
        db.casaPicQueries.upsertSetting(key, value)
        cache.value = cache.value.toMutableMap().also { it[key] = value }
    }

    fun setBoolean(key: String, value: Boolean) = set(key, if (value) "1" else "0")

    fun setInt(key: String, value: Int) = set(key, value.toString())

    fun getEnabledExtensions(): Set<String> {
        val exts = mutableSetOf(".jpg", ".jpeg")
        if (getBoolean(SettingsKeys.FILETYPES_BMP)) exts += ".bmp"
        if (getBoolean(SettingsKeys.FILETYPES_GIF)) exts += ".gif"
        if (getBoolean(SettingsKeys.FILETYPES_PNG)) exts += ".png"
        if (getBoolean(SettingsKeys.FILETYPES_TGA)) exts += ".tga"
        if (getBoolean(SettingsKeys.FILETYPES_TIFF)) { exts += ".tif"; exts += ".tiff" }
        if (getBoolean(SettingsKeys.FILETYPES_WEBP)) exts += ".webp"
        if (getBoolean(SettingsKeys.FILETYPES_PSD)) exts += ".psd"
        if (getBoolean(SettingsKeys.FILETYPES_RAW)) {
            exts += setOf(".cr2", ".cr3", ".nef", ".arw", ".dng", ".orf", ".rw2", ".pef", ".srw")
        }
        if (getBoolean(SettingsKeys.FILETYPES_MOVIES)) {
            exts += setOf(".avi", ".mpg", ".mpeg", ".asf", ".wmv", ".mp4", ".m4v")
        }
        if (getBoolean(SettingsKeys.FILETYPES_QUICKTIME)) exts += ".mov"
        return exts
    }

    val showHiddenPhotos: StateFlow<Boolean> get() = MutableStateFlow(getBoolean(SettingsKeys.SHOW_HIDDEN_PHOTOS))
    val thumbnailCaption: StateFlow<String> get() = MutableStateFlow(get(SettingsKeys.THUMBNAIL_CAPTION))
    val faceDetectionEnabled: Boolean get() = getBoolean(SettingsKeys.FACE_DETECTION_ENABLED)
    val faceClusterThreshold: Float get() = getInt(SettingsKeys.FACE_CLUSTER_THRESHOLD) / 100f
    val faceSuggestionThreshold: Float get() = getInt(SettingsKeys.FACE_SUGGESTION_THRESHOLD) / 100f
}
