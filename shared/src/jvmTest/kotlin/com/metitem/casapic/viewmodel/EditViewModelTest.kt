package com.metitem.casapic.viewmodel

import com.metitem.casapic.db.awaitTrue
import com.metitem.casapic.db.createInMemoryDatabase
import com.metitem.casapic.db.insertTestPhoto
import com.metitem.casapic.metadata.PhotoEdit
import com.metitem.casapic.metadata.RotateEdit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EditViewModelTest {

    @Test
    fun `rotateClockwise inserts rotate-90 proto`() {
        val db = createInMemoryDatabase()
        val photoId = db.insertTestPhoto()
        val vm = EditViewModel(db, photoId)

        vm.rotateClockwise()
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().isNotEmpty() }

        val edits = db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList()
        assertEquals(1, edits.size)
        assertEquals(90, PhotoEdit.ADAPTER.decode(edits[0].edit_proto).rotate?.degrees)
    }

    @Test
    fun `rotateCounterClockwise inserts rotate-minus90 proto`() {
        val db = createInMemoryDatabase()
        val photoId = db.insertTestPhoto()
        val vm = EditViewModel(db, photoId)

        vm.rotateCounterClockwise()
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().isNotEmpty() }

        val decoded = PhotoEdit.ADAPTER.decode(
            db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList()[0].edit_proto
        )
        assertEquals(-90, decoded.rotate?.degrees)
    }

    @Test
    fun `applyIFeelLucky inserts two edits`() {
        val db = createInMemoryDatabase()
        val photoId = db.insertTestPhoto()
        val vm = EditViewModel(db, photoId)

        vm.applyIFeelLucky()
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().size == 2 }
    }

    @Test
    fun `undoAllEdits marks all edits undone`() {
        val db = createInMemoryDatabase()
        val photoId = db.insertTestPhoto()
        val vm = EditViewModel(db, photoId)

        vm.rotateClockwise()
        vm.applySepia()
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().size == 2 }

        vm.undoAllEdits()
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().isEmpty() }
    }

    @Test
    fun `undoEdit marks single edit undone leaving the other`() {
        val db = createInMemoryDatabase()
        val photoId = db.insertTestPhoto()
        val vm = EditViewModel(db, photoId)

        vm.rotateClockwise()
        vm.applySepia()
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().size == 2 }

        val firstEditId = db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList()[0].id
        vm.undoEdit(firstEditId)
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().size == 1 }
    }

    @Test
    fun `confirmCrop stores crop coordinates`() {
        val db = createInMemoryDatabase()
        val photoId = db.insertTestPhoto()
        val vm = EditViewModel(db, photoId)

        vm.confirmCrop(0.1f, 0.2f, 0.9f, 0.8f)
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().isNotEmpty() }

        val crop = PhotoEdit.ADAPTER.decode(
            db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList()[0].edit_proto
        ).crop
        assertNotNull(crop)
        assertEquals(0.1f, crop.left, 0.001f)
        assertEquals(0.2f, crop.top, 0.001f)
        assertEquals(0.9f, crop.right, 0.001f)
        assertEquals(0.8f, crop.bottom, 0.001f)
    }

    @Test
    fun `applyFillLight stores value`() {
        val db = createInMemoryDatabase()
        val photoId = db.insertTestPhoto()
        val vm = EditViewModel(db, photoId)

        vm.applyFillLight(0.75f)
        awaitTrue { db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList().isNotEmpty() }

        val decoded = PhotoEdit.ADAPTER.decode(
            db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList()[0].edit_proto
        )
        assertEquals(0.75f, decoded.fill_light?.value_ ?: 0f, 0.001f)
    }

    @Test
    fun `edit_proto Wire round-trip is lossless`() {
        val original = PhotoEdit(rotate = RotateEdit(degrees = 270))
        val decoded = PhotoEdit.ADAPTER.decode(PhotoEdit.ADAPTER.encode(original))
        assertEquals(270, decoded.rotate?.degrees)
    }

    @Test
    fun `isCropMode toggles correctly`() {
        val db = createInMemoryDatabase()
        val vm = EditViewModel(db, db.insertTestPhoto())

        assertFalse(vm.isCropMode.value)
        vm.enterCropMode()
        assertTrue(vm.isCropMode.value)
        vm.cancelCropMode()
        assertFalse(vm.isCropMode.value)
    }
}
