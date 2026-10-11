package com.metitem.casapic.db

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoRepositoryTest {

    private lateinit var db: CasaPicDatabase

    @BeforeEach
    fun setup() {
        db = createInMemoryDatabase()
    }

    @Test
    fun `insertPhoto and queryById round-trips all fields`() {
        val folderId = db.insertTestFolder()
        val photoId = db.insertTestPhoto(
            filePath = "/pics/sunset.jpg",
            fileName = "sunset.jpg",
            folderId = folderId,
            fileSize = 2_048_000L,
            captureDate = 1_700_000_000_000L,
            caption = "Beautiful sunset",
            cameraModel = "Sony A7 IV"
        )

        val photo = db.casaPicQueries.getPhotoById(photoId).executeAsOneOrNull()
        assertNotNull(photo)
        assertEquals("/pics/sunset.jpg", photo.file_path)
        assertEquals("sunset.jpg", photo.file_name)
        assertEquals(folderId, photo.folder_id)
        assertEquals(2_048_000L, photo.file_size)
        assertEquals(1_700_000_000_000L, photo.capture_date_millis)
        assertEquals("Beautiful sunset", photo.caption)
        assertEquals("Sony A7 IV", photo.camera_model)
    }

    @Test
    fun `insertPhoto with duplicate path is ignored (IGNORE semantics)`() {
        db.insertTestPhoto(filePath = "/pics/dup.jpg")
        db.insertTestPhoto(filePath = "/pics/dup.jpg")
        val count = db.casaPicQueries.countPhotosAll(1L).executeAsOne()
        assertEquals(1L, count)
    }

    @Test
    fun `deletePhotoById removes the row`() {
        val id = db.insertTestPhoto()
        db.casaPicQueries.deletePhotoById(id)
        assertNull(db.casaPicQueries.getPhotoById(id).executeAsOneOrNull())
    }

    @Test
    fun `deletePhotoById cascades to photo_edits`() {
        val id = db.insertTestPhoto()
        val proto = byteArrayOf(1, 2, 3)
        db.casaPicQueries.insertPhotoEdit(id, proto, System.currentTimeMillis())
        db.casaPicQueries.deletePhotoById(id)
        val edits = db.casaPicQueries.getActiveEditsForPhoto(id).executeAsList()
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `deletePhotoById cascades to face_detections`() {
        val id = db.insertTestPhoto()
        db.casaPicQueries.insertFaceDetection(id, null, 0.1, 0.1, 0.5, 0.5, 0.9, null, null, null, null, null, 0L)
        db.casaPicQueries.deletePhotoById(id)
        val faces = db.casaPicQueries.getFaceDetectionsForPhoto(id).executeAsList()
        assertTrue(faces.isEmpty())
    }

    @Test
    fun `updatePhotoStar flips star rating`() {
        val id = db.insertTestPhoto(starRating = 0L)
        db.casaPicQueries.updatePhotoStar(1L, id)
        assertEquals(1L, db.casaPicQueries.getPhotoById(id).executeAsOne().star_rating)
        db.casaPicQueries.updatePhotoStar(0L, id)
        assertEquals(0L, db.casaPicQueries.getPhotoById(id).executeAsOne().star_rating)
    }

    @Test
    fun `updatePhotoHidden sets hidden flag`() {
        val id = db.insertTestPhoto(isHidden = 0L)
        db.casaPicQueries.updatePhotoHidden(1L, id)
        assertEquals(1L, db.casaPicQueries.getPhotoById(id).executeAsOne().is_hidden)
    }

    @Test
    fun `getPhotosAll filters hidden when showHidden=0`() {
        val folder = db.insertTestFolder()
        db.insertTestPhoto("/p/visible.jpg", folderId = folder, isHidden = 0L)
        db.insertTestPhoto("/p/hidden.jpg", folderId = folder, isHidden = 1L)

        val visible = db.casaPicQueries.getPhotosAll(0L, 100L, 0L).executeAsList()
        assertEquals(1, visible.size)
        assertEquals("visible.jpg", visible[0].file_name)
    }

    @Test
    fun `getPhotosAll includes hidden when showHidden=1`() {
        val folder = db.insertTestFolder()
        db.insertTestPhoto("/p/visible.jpg", folderId = folder, isHidden = 0L)
        db.insertTestPhoto("/p/hidden.jpg", folderId = folder, isHidden = 1L)

        val all = db.casaPicQueries.getPhotosAll(1L, 100L, 0L).executeAsList()
        assertEquals(2, all.size)
    }

    @Test
    fun `getPhotosForFolder returns only that folder's photos`() {
        val folder1 = db.insertTestFolder("/f1", "Folder 1")
        val folder2 = db.insertTestFolder("/f2", "Folder 2")
        db.insertTestPhoto("/p/a.jpg", folderId = folder1)
        db.insertTestPhoto("/p/b.jpg", folderId = folder1)
        db.insertTestPhoto("/p/c.jpg", folderId = folder2)

        val photos = db.casaPicQueries.getPhotosForFolder(folder1, 0L, 100L, 0L).executeAsList()
        assertEquals(2, photos.size)
        assertTrue(photos.all { it.folder_id == folder1 })
    }

    @Test
    fun `getPhotosForFolder filters hidden correctly`() {
        val folder = db.insertTestFolder()
        db.insertTestPhoto("/p/vis.jpg", folderId = folder, isHidden = 0L)
        db.insertTestPhoto("/p/hid.jpg", folderId = folder, isHidden = 1L)

        val withHidden = db.casaPicQueries.getPhotosForFolder(folder, 1L, 100L, 0L).executeAsList()
        assertEquals(2, withHidden.size)

        val withoutHidden = db.casaPicQueries.getPhotosForFolder(folder, 0L, 100L, 0L).executeAsList()
        assertEquals(1, withoutHidden.size)
    }

    @Test
    fun `searchPhotos matches caption`() {
        db.insertTestPhoto("/p/beach.jpg", caption = "sunny beach day")
        db.insertTestPhoto("/p/mountain.jpg", caption = "alpine hike")

        val results = db.casaPicQueries.searchPhotos("beach", 0L, 100L, 0L).executeAsList()
        assertEquals(1, results.size)
        assertEquals("beach.jpg", results[0].file_name)
    }

    @Test
    fun `searchPhotos matches iptc_keywords`() {
        db.insertTestPhoto("/p/tagged.jpg", iptcKeywords = "vacation;spain;2024")
        db.insertTestPhoto("/p/other.jpg", iptcKeywords = "work;office")

        val results = db.casaPicQueries.searchPhotos("spain", 0L, 100L, 0L).executeAsList()
        assertEquals(1, results.size)
    }

    @Test
    fun `countPhotosAll counts correctly`() {
        assertEquals(0L, db.casaPicQueries.countPhotosAll(0L).executeAsOne())
        db.insertTestPhoto("/p/1.jpg")
        db.insertTestPhoto("/p/2.jpg")
        assertEquals(2L, db.casaPicQueries.countPhotosAll(0L).executeAsOne())
    }

    @Test
    fun `getTotalDiskSizeAll sums file sizes`() {
        db.insertTestPhoto("/p/a.jpg", fileSize = 1000L)
        db.insertTestPhoto("/p/b.jpg", fileSize = 2000L)
        val total = db.casaPicQueries.getTotalDiskSizeAll(0L).executeAsOne()
        assertEquals(3000L, total)
    }

    @Test
    fun `getTotalDiskSizeAll handles null file sizes gracefully`() {
        db.insertTestPhoto("/p/a.jpg", fileSize = null)
        val total = db.casaPicQueries.getTotalDiskSizeAll(0L).executeAsOne()
        assertEquals(0L, total)
    }

    @Test
    fun `getPhotosForFolder paginates correctly`() {
        val folder = db.insertTestFolder()
        repeat(10) { i ->
            db.insertTestPhoto("/p/photo_$i.jpg", folderId = folder, captureDate = i.toLong())
        }

        val page1 = db.casaPicQueries.getPhotosForFolder(folder, 0L, 4L, 0L).executeAsList()
        val page2 = db.casaPicQueries.getPhotosForFolder(folder, 0L, 4L, 4L).executeAsList()
        val page3 = db.casaPicQueries.getPhotosForFolder(folder, 0L, 4L, 8L).executeAsList()

        assertEquals(4, page1.size)
        assertEquals(4, page2.size)
        assertEquals(2, page3.size)
        assertTrue((page1.map { it.id } intersect page2.map { it.id }.toSet()).isEmpty())
    }
}
