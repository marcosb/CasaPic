package com.metitem.casapic.viewmodel

import com.metitem.casapic.db.awaitTrue
import com.metitem.casapic.db.createInMemoryDatabase
import com.metitem.casapic.db.insertTestFolder
import com.metitem.casapic.db.insertTestPhoto
import com.metitem.casapic.model.ScanState
import com.metitem.casapic.model.ThumbnailCaption
import com.metitem.casapic.settings.SettingsKeys
import com.metitem.casapic.settings.SettingsRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryViewModelTest {

    private lateinit var vm: LibraryViewModel
    private lateinit var db: com.metitem.casapic.db.CasaPicDatabase

    @BeforeEach
    fun setup() {
        db = createInMemoryDatabase()
        vm = LibraryViewModel(db, SettingsRepository(db))
    }

    @Test
    fun `initial state has no selection and idle scan`() {
        assertTrue(vm.selectedPhotoIds.value.isEmpty())
        assertEquals(ScanState.Idle, vm.scanState.value)
    }

    @Test
    fun `selectFolder clears selection and album`() {
        val folderId = db.insertTestFolder()
        val photoId = db.insertTestPhoto(folderId = folderId)

        vm.handlePhotoClick(photoId, shiftDown = false, ctrlDown = false)
        assertEquals(setOf(photoId), vm.selectedPhotoIds.value)

        vm.selectFolder(folderId)
        assertTrue(vm.selectedPhotoIds.value.isEmpty())
        assertNull(vm.activeAlbumId.value)
        assertEquals(folderId, vm.activeFolderId.value)
    }

    @Test
    fun `selectAlbum clears folder and selection`() {
        val folderId = db.insertTestFolder()
        vm.selectFolder(folderId)

        vm.selectAlbum(42L)

        assertNull(vm.activeFolderId.value)
        assertEquals(42L, vm.activeAlbumId.value)
        assertTrue(vm.selectedPhotoIds.value.isEmpty())
    }

    @Test
    fun `handlePhotoClick plain click selects only that photo`() {
        val p1 = db.insertTestPhoto("/a.jpg")
        val p2 = db.insertTestPhoto("/b.jpg")

        vm.handlePhotoClick(p1, shiftDown = false, ctrlDown = false)
        assertEquals(setOf(p1), vm.selectedPhotoIds.value)

        vm.handlePhotoClick(p2, shiftDown = false, ctrlDown = false)
        assertEquals(setOf(p2), vm.selectedPhotoIds.value)
    }

    @Test
    fun `handlePhotoClick ctrl adds to selection`() {
        val p1 = db.insertTestPhoto("/a.jpg")
        val p2 = db.insertTestPhoto("/b.jpg")

        vm.handlePhotoClick(p1, shiftDown = false, ctrlDown = false)
        vm.handlePhotoClick(p2, shiftDown = false, ctrlDown = true)
        assertEquals(setOf(p1, p2), vm.selectedPhotoIds.value)
    }

    @Test
    fun `handlePhotoClick ctrl on selected photo deselects it`() {
        val p1 = db.insertTestPhoto("/a.jpg")
        vm.handlePhotoClick(p1, shiftDown = false, ctrlDown = false)
        vm.handlePhotoClick(p1, shiftDown = false, ctrlDown = true)
        assertTrue(vm.selectedPhotoIds.value.isEmpty())
    }

    @Test
    fun `clearSelection empties the set`() {
        val p = db.insertTestPhoto()
        vm.handlePhotoClick(p, shiftDown = false, ctrlDown = false)
        vm.clearSelection()
        assertTrue(vm.selectedPhotoIds.value.isEmpty())
    }

    @Test
    fun `toggleStar flips star_rating in DB`() {
        val id = db.insertTestPhoto(starRating = 0L)
        vm.toggleStar(id)
        awaitTrue { db.casaPicQueries.getPhotoById(id).executeAsOne().star_rating == 1L }

        vm.toggleStar(id)
        awaitTrue { db.casaPicQueries.getPhotoById(id).executeAsOne().star_rating == 0L }
    }

    @Test
    fun `hidePhoto sets is_hidden=1`() {
        val id = db.insertTestPhoto(isHidden = 0L)
        vm.hidePhoto(id)
        awaitTrue { db.casaPicQueries.getPhotoById(id).executeAsOne().is_hidden == 1L }
    }

    @Test
    fun `deletePhoto removes DB row`() {
        val id = db.insertTestPhoto()
        vm.deletePhoto(id)
        awaitTrue { db.casaPicQueries.getPhotoById(id).executeAsOneOrNull() == null }
    }

    @Test
    fun `setShowHiddenPhotos updates state`() {
        vm.setShowHiddenPhotos(true)
        assertTrue(vm.showHiddenPhotos.value)
        vm.setShowHiddenPhotos(false)
        assertFalse(vm.showHiddenPhotos.value)
    }

    @Test
    fun `setThumbnailCaption updates state`() {
        vm.setThumbnailCaption(ThumbnailCaption.FILENAME)
        assertEquals(ThumbnailCaption.FILENAME, vm.thumbnailCaption.value)
        vm.setThumbnailCaption(ThumbnailCaption.STARS)
        assertEquals(ThumbnailCaption.STARS, vm.thumbnailCaption.value)
    }

    @Test
    fun `setThumbnailSizeFloat clamps to 0-1`() {
        vm.setThumbnailSizeFloat(1.5f)
        assertEquals(1.0f, vm.thumbnailSizeFloat.value, 0.001f)
        vm.setThumbnailSizeFloat(-0.5f)
        assertEquals(0.0f, vm.thumbnailSizeFloat.value, 0.001f)
    }

    @Test
    fun `thumbnailSizeNormal reflects float threshold`() {
        vm.setThumbnailSizeFloat(0.7f)
        // thumbnailSizeNormal is a derived StateFlow; give the scope a chance to process it
        awaitTrue { vm.thumbnailSizeNormal.value }

        vm.setThumbnailSizeFloat(0.3f)
        awaitTrue { !vm.thumbnailSizeNormal.value }
    }

    @Test
    fun `updateScanState transitions correctly`() {
        vm.updateScanState(ScanState.Scanning(10, 100, "Vacation"))
        assertTrue(vm.scanState.value is ScanState.Scanning)

        vm.updateScanState(ScanState.Done(50, 10, 0))
        assertTrue(vm.scanState.value is ScanState.Done)
    }

    @Test
    fun `cancelScan resets to Idle`() {
        vm.updateScanState(ScanState.Scanning(1, 10, "Folder"))
        vm.cancelScan()
        assertEquals(ScanState.Idle, vm.scanState.value)
    }

    @Test
    fun `setSearch updates searchQuery`() {
        vm.setSearch("vacation")
        assertEquals("vacation", vm.searchQuery.value)
    }

    @Test
    fun `setFolderScanMode updates DB`() {
        val folderId = db.insertTestFolder()
        vm.setFolderScanMode(folderId, 0L)
        awaitTrue {
            db.casaPicQueries.getFolderByPath("/test/folder")
                .executeAsOneOrNull()?.scan_mode == 0L
        }
    }
}
