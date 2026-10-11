package com.metitem.casapic.settings

import com.metitem.casapic.db.createInMemoryDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private lateinit var settings: SettingsRepository

    @BeforeEach
    fun setup() {
        settings = SettingsRepository(createInMemoryDatabase())
    }

    @Test
    fun `get returns default for unknown key`() {
        assertEquals("DATE", settings.get(SettingsKeys.THUMBNAIL_CAPTION))
    }

    @Test
    fun `set persists value and get returns it`() {
        settings.set(SettingsKeys.THUMBNAIL_CAPTION, "FILENAME")
        assertEquals("FILENAME", settings.get(SettingsKeys.THUMBNAIL_CAPTION))
    }

    @Test
    fun `getBoolean returns false for default 0`() {
        assertFalse(settings.getBoolean(SettingsKeys.SHOW_HIDDEN_PHOTOS))
    }

    @Test
    fun `setBoolean true writes 1 and reads back as true`() {
        settings.setBoolean(SettingsKeys.SHOW_HIDDEN_PHOTOS, true)
        assertTrue(settings.getBoolean(SettingsKeys.SHOW_HIDDEN_PHOTOS))
    }

    @Test
    fun `setBoolean false writes 0 and reads back as false`() {
        settings.setBoolean(SettingsKeys.FACE_DETECTION_ENABLED, false)
        assertFalse(settings.getBoolean(SettingsKeys.FACE_DETECTION_ENABLED))
    }

    @Test
    fun `getInt returns default int value`() {
        assertEquals(85, settings.getInt(SettingsKeys.FACE_CLUSTER_THRESHOLD))
    }

    @Test
    fun `setInt persists and getInt returns it`() {
        settings.setInt(SettingsKeys.FACE_CLUSTER_THRESHOLD, 70)
        assertEquals(70, settings.getInt(SettingsKeys.FACE_CLUSTER_THRESHOLD))
    }

    @Test
    fun `getFloat returns default float value`() {
        val v = settings.getFloat(SettingsKeys.THUMBNAIL_SIZE_FLOAT, 0.667f)
        assertEquals(0.667f, v, 0.001f)
    }

    @Test
    fun `setFloat persists and getFloat returns it`() {
        settings.setFloat(SettingsKeys.THUMBNAIL_SIZE_FLOAT, 0.25f)
        val v = settings.getFloat(SettingsKeys.THUMBNAIL_SIZE_FLOAT)
        assertEquals(0.25f, v, 0.001f)
    }

    @Test
    fun `set overwrites previous value`() {
        settings.set(SettingsKeys.THUMBNAIL_CAPTION, "FILENAME")
        settings.set(SettingsKeys.THUMBNAIL_CAPTION, "STARS")
        assertEquals("STARS", settings.get(SettingsKeys.THUMBNAIL_CAPTION))
    }

    @Test
    fun `getEnabledExtensions always includes jpg and jpeg`() {
        val exts = settings.getEnabledExtensions()
        assertTrue(".jpg" in exts)
        assertTrue(".jpeg" in exts)
    }

    @Test
    fun `getEnabledExtensions respects png toggle`() {
        assertFalse(".png" in settings.getEnabledExtensions())
        settings.setBoolean(SettingsKeys.FILETYPES_PNG, true)
        assertTrue(".png" in settings.getEnabledExtensions())
    }

    @Test
    fun `faceDetectionEnabled reflects setting`() {
        assertTrue(settings.faceDetectionEnabled)
        settings.setBoolean(SettingsKeys.FACE_DETECTION_ENABLED, false)
        assertFalse(settings.faceDetectionEnabled)
    }

    @Test
    fun `faceClusterThreshold is normalised to 0-1 range`() {
        settings.setInt(SettingsKeys.FACE_CLUSTER_THRESHOLD, 60)
        assertEquals(0.60f, settings.faceClusterThreshold, 0.001f)
    }
}
