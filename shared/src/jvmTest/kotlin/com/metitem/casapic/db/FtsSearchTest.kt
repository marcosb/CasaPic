package com.metitem.casapic.db

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FtsSearchTest {

    private lateinit var db: CasaPicDatabase

    @BeforeEach
    fun setup() {
        db = createInMemoryDatabase()
    }

    @Test
    fun `searchPhotos matches file_name`() {
        db.insertTestPhoto("/p/birthday_party.jpg", fileName = "birthday_party.jpg")
        db.insertTestPhoto("/p/work.jpg", fileName = "work.jpg")

        val results = db.casaPicQueries.searchPhotos("birthday", 0L, 100L, 0L).executeAsList()
        assertEquals(1, results.size)
        assertEquals("birthday_party.jpg", results[0].file_name)
    }

    @Test
    fun `searchPhotos is case-insensitive via LIKE`() {
        db.insertTestPhoto("/p/Beach.jpg", fileName = "Beach.jpg", caption = "Beach day")
        val upper = db.casaPicQueries.searchPhotos("BEACH", 0L, 100L, 0L).executeAsList()
        assertEquals(1, upper.size)
    }

    @Test
    fun `searchPhotos matches description`() {
        db.insertTestPhoto("/p/a.jpg", caption = "mountain peak summit")
        db.insertTestPhoto("/p/b.jpg", caption = "seaside beach")

        val results = db.casaPicQueries.searchPhotos("mountain", 0L, 100L, 0L).executeAsList()
        assertEquals(1, results.size)
    }

    @Test
    fun `searchPhotos matches camera_model`() {
        db.insertTestPhoto("/p/canon.jpg", cameraModel = "Canon EOS R5")
        db.insertTestPhoto("/p/sony.jpg", cameraModel = "Sony A7 III")

        val results = db.casaPicQueries.searchPhotos("Canon", 0L, 100L, 0L).executeAsList()
        assertEquals(1, results.size)
    }

    @Test
    fun `searchPhotos matches iptc_keywords`() {
        db.insertTestPhoto("/p/tagged.jpg", iptcKeywords = "travel;portugal;lisbon")
        db.insertTestPhoto("/p/other.jpg", iptcKeywords = "work;meeting")

        val results = db.casaPicQueries.searchPhotos("portugal", 0L, 100L, 0L).executeAsList()
        assertEquals(1, results.size)
    }

    @Test
    fun `searchPhotos returns empty for no match`() {
        db.insertTestPhoto("/p/a.jpg")
        val results = db.casaPicQueries.searchPhotos("xyznonexistent", 0L, 100L, 0L).executeAsList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchPhotos respects hidden filter`() {
        db.insertTestPhoto("/p/vis.jpg", caption = "vacation trip", isHidden = 0L)
        db.insertTestPhoto("/p/hid.jpg", caption = "vacation hidden", isHidden = 1L)

        val withoutHidden = db.casaPicQueries.searchPhotos("vacation", 0L, 100L, 0L).executeAsList()
        assertEquals(1, withoutHidden.size)

        val withHidden = db.casaPicQueries.searchPhotos("vacation", 1L, 100L, 0L).executeAsList()
        assertEquals(2, withHidden.size)
    }

    @Test
    fun `searchPhotosInFolder scopes results to folder`() {
        val folder1 = db.insertTestFolder("/f1", "Folder 1")
        val folder2 = db.insertTestFolder("/f2", "Folder 2")
        db.insertTestPhoto("/p/a.jpg", caption = "sunset view", folderId = folder1)
        db.insertTestPhoto("/p/b.jpg", caption = "sunset view", folderId = folder2)

        val results = db.casaPicQueries.searchPhotosInFolder("sunset", folder1, 0L, 100L, 0L).executeAsList()
        assertEquals(1, results.size)
        assertEquals(folder1, results[0].folder_id)
    }

    @Test
    fun `countSearchPhotos matches searchPhotos count`() {
        db.insertTestPhoto("/p/a.jpg", caption = "blue sky")
        db.insertTestPhoto("/p/b.jpg", caption = "blue ocean")
        db.insertTestPhoto("/p/c.jpg", caption = "green forest")

        val list = db.casaPicQueries.searchPhotos("blue", 0L, 100L, 0L).executeAsList()
        val count = db.casaPicQueries.countSearchPhotos("blue", 0L).executeAsOne()
        assertEquals(list.size.toLong(), count)
    }

    @Test
    fun `deletePhoto removes from FTS index so search no longer finds it`() {
        val id = db.insertTestPhoto("/p/a.jpg", caption = "unique_marker_abc123")

        val before = db.casaPicQueries.searchPhotos("unique_marker_abc123", 0L, 100L, 0L).executeAsList()
        assertEquals(1, before.size)

        db.casaPicQueries.deletePhotoById(id)

        val after = db.casaPicQueries.searchPhotos("unique_marker_abc123", 0L, 100L, 0L).executeAsList()
        assertTrue(after.isEmpty())
    }
}
