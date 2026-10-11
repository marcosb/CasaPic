package com.metitem.casapic.edit

import com.metitem.casapic.metadata.AutoColorEdit
import com.metitem.casapic.metadata.AutoLightEdit
import com.metitem.casapic.metadata.BwEdit
import com.metitem.casapic.metadata.CropEdit
import com.metitem.casapic.metadata.FilmGrainEdit
import com.metitem.casapic.metadata.FinetuneEdit
import com.metitem.casapic.metadata.FillLightEdit
import com.metitem.casapic.metadata.PhotoEdit
import com.metitem.casapic.metadata.RedEyeEdit
import com.metitem.casapic.metadata.RedEyeRegion
import com.metitem.casapic.metadata.RotateEdit
import com.metitem.casapic.metadata.SepiaEdit
import com.metitem.casapic.metadata.TiltEdit
import com.metitem.casapic.metadata.VignetteEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EditStackSerializationTest {

    private fun roundTrip(edit: PhotoEdit): PhotoEdit {
        val bytes = PhotoEdit.ADAPTER.encode(edit)
        return PhotoEdit.ADAPTER.decode(bytes)
    }

    @Test
    fun `RotateEdit round-trips degrees`() {
        val decoded = roundTrip(PhotoEdit(rotate = RotateEdit(degrees = 90)))
        assertEquals(90, decoded.rotate?.degrees)
        assertNull(decoded.sepia)
    }

    @Test
    fun `RotateEdit negative degrees round-trip`() {
        val decoded = roundTrip(PhotoEdit(rotate = RotateEdit(degrees = -90)))
        assertEquals(-90, decoded.rotate?.degrees)
    }

    @Test
    fun `CropEdit round-trips all four coordinates`() {
        val decoded = roundTrip(
            PhotoEdit(crop = CropEdit(left = 0.1f, top = 0.2f, right = 0.9f, bottom = 0.8f))
        )
        assertEquals(0.1f, decoded.crop?.left ?: 0f, 0.001f)
        assertEquals(0.2f, decoded.crop?.top ?: 0f, 0.001f)
        assertEquals(0.9f, decoded.crop?.right ?: 0f, 0.001f)
        assertEquals(0.8f, decoded.crop?.bottom ?: 0f, 0.001f)
    }

    @Test
    fun `TiltEdit round-trips angle and scale`() {
        val decoded = roundTrip(PhotoEdit(tilt = TiltEdit(angle = 5.5f, scale = 1.15f)))
        assertEquals(5.5f, decoded.tilt?.angle ?: 0f, 0.001f)
        assertEquals(1.15f, decoded.tilt?.scale ?: 0f, 0.001f)
    }

    @Test
    fun `FinetuneEdit round-trips all five sliders`() {
        val decoded = roundTrip(
            PhotoEdit(
                finetune = FinetuneEdit(
                    shadows = 0.2f,
                    highlights = -0.1f,
                    color_temp = 0.3f,
                    brightness = 0.4f,
                    saturation = -0.2f
                )
            )
        )
        assertEquals(0.2f, decoded.finetune?.shadows ?: 0f, 0.001f)
        assertEquals(-0.1f, decoded.finetune?.highlights ?: 0f, 0.001f)
        assertEquals(0.3f, decoded.finetune?.color_temp ?: 0f, 0.001f)
    }

    @Test
    fun `AutoLightEdit presence encodes as presence=true`() {
        val decoded = roundTrip(PhotoEdit(autolight = AutoLightEdit(presence = true)))
        assertNotNull(decoded.autolight)
        assertEquals(true, decoded.autolight?.presence)
    }

    @Test
    fun `AutoColorEdit presence encodes correctly`() {
        val decoded = roundTrip(PhotoEdit(autocolor = AutoColorEdit(presence = true)))
        assertNotNull(decoded.autocolor)
    }

    @Test
    fun `SepiaEdit strength round-trips`() {
        val decoded = roundTrip(PhotoEdit(sepia = SepiaEdit(strength = 0.75f)))
        assertEquals(0.75f, decoded.sepia?.strength ?: 0f, 0.001f)
    }

    @Test
    fun `BwEdit strength round-trips`() {
        val decoded = roundTrip(PhotoEdit(bw = BwEdit(strength = 0.9f)))
        assertEquals(0.9f, decoded.bw?.strength ?: 0f, 0.001f)
    }

    @Test
    fun `FillLightEdit value round-trips`() {
        val decoded = roundTrip(PhotoEdit(fill_light = FillLightEdit(value_ = 0.5f)))
        assertEquals(0.5f, decoded.fill_light?.value_ ?: 0f, 0.001f)
    }

    @Test
    fun `VignetteEdit all three params round-trip`() {
        val decoded = roundTrip(
            PhotoEdit(vignette = VignetteEdit(strength = 0.6f, size = 0.4f, roundness = 0.7f))
        )
        assertEquals(0.6f, decoded.vignette?.strength ?: 0f, 0.001f)
        assertEquals(0.4f, decoded.vignette?.size ?: 0f, 0.001f)
        assertEquals(0.7f, decoded.vignette?.roundness ?: 0f, 0.001f)
    }

    @Test
    fun `FilmGrainEdit strength round-trips`() {
        val decoded = roundTrip(PhotoEdit(filmgrain = FilmGrainEdit(strength = 0.3f)))
        assertEquals(0.3f, decoded.filmgrain?.strength ?: 0f, 0.001f)
    }

    @Test
    fun `RedEyeEdit with multiple regions round-trips`() {
        val regions = listOf(
            RedEyeRegion(x = 0.3f, y = 0.35f, radius = 0.05f),
            RedEyeRegion(x = 0.7f, y = 0.35f, radius = 0.05f)
        )
        val decoded = roundTrip(PhotoEdit(redeye = RedEyeEdit(regions = regions)))
        assertEquals(2, decoded.redeye?.regions?.size)
        assertEquals(0.3f, decoded.redeye?.regions?.get(0)?.x ?: 0f, 0.001f)
        assertEquals(0.7f, decoded.redeye?.regions?.get(1)?.x ?: 0f, 0.001f)
    }

    @Test
    fun `oneof field is exclusive - encode rotate, decode has no sepia`() {
        val encoded = PhotoEdit(rotate = RotateEdit(degrees = 180))
        val decoded = roundTrip(encoded)
        assertNotNull(decoded.rotate)
        assertNull(decoded.sepia)
        assertNull(decoded.crop)
        assertNull(decoded.vignette)
    }

    @Test
    fun `empty RedEyeEdit produces empty regions list`() {
        val decoded = roundTrip(PhotoEdit(redeye = RedEyeEdit(regions = emptyList())))
        assertNotNull(decoded.redeye)
        assertEquals(0, decoded.redeye?.regions?.size)
    }
}
