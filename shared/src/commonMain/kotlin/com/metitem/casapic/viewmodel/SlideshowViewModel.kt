package com.metitem.casapic.viewmodel

import com.metitem.casapic.db.Photos
import com.metitem.casapic.settings.SettingsKeys
import com.metitem.casapic.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SlideshowViewModel(
    private val photos: List<Photos>,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    val photoCount: Int get() = photos.size

    private var autoAdvanceJob: Job? = null

    fun currentPhoto(): Photos? = photos.getOrNull(_currentIndex.value)

    fun next() {
        val loop = settings.getBoolean(SettingsKeys.SLIDESHOW_LOOP)
        val next = _currentIndex.value + 1
        _currentIndex.value = if (next >= photos.size) {
            if (loop) 0 else _currentIndex.value
        } else next
    }

    fun prev() {
        val prev = _currentIndex.value - 1
        if (prev >= 0) _currentIndex.value = prev
    }

    fun goTo(index: Int) {
        if (index in photos.indices) _currentIndex.value = index
    }

    fun play() {
        _isPlaying.value = true
        val delayMs = settings.getInt(SettingsKeys.SLIDESHOW_DELAY_MS).toLong()
            .coerceAtLeast(500L)
        autoAdvanceJob = scope.launch {
            while (_isPlaying.value) {
                delay(delayMs)
                val loop = settings.getBoolean(SettingsKeys.SLIDESHOW_LOOP)
                val next = _currentIndex.value + 1
                if (next >= photos.size) {
                    if (loop) _currentIndex.value = 0
                    else { _isPlaying.value = false; break }
                } else {
                    _currentIndex.value = next
                }
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        autoAdvanceJob?.cancel()
    }
}
