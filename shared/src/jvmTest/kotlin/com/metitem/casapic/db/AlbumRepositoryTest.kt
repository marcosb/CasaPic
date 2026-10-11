package com.metitem.casapic.db

import com.metitem.casapic.model.AlbumKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AlbumRepositoryTest {

    private lateinit var db: CasaPicDatabase

    @BeforeEach
    fun setup() {
        db = createInMemoryDatabase()
    }

    private fun insertAlbum(name: String, kind: Long = AlbumKind.ALBUM): Long {
        val now = System.currentTimeMillis()
        db.casaPicQueries.insertAlbum(name, "", now, now, kind)
        return db.casaPicQueries.lastInsertId().executeAsOne()
    }

    @Test
    fun `createAlbum inserts with correct kind`() {
        val id = insertAlbum("Summer 2024")
        val album = db.casaPicQueries.getAlbumById(id).executeAsOneOrNull()
        assertNotNull(album)
        assertEquals("Summer 2024", album.name)
        assertEquals(AlbumKind.ALBUM, album.kind)
    }

    @Test
    fun `getAlbumsByKind returns only matching kind`() {
        insertAlbum("My Album", AlbumKind.ALBUM)
        insertAlbum("Face Group", AlbumKind.FACE_GROUP)

        val albums = db.casaPicQueries.getAlbumsByKind(AlbumKind.ALBUM).executeAsList()
        assertEquals(1, albums.size)
        assertEquals("My Album", albums[0].name)
    }

    @Test
    fun `addPhotoToAlbum and countPhotosInAlbum are consistent`() {
        val albumId = insertAlbum("Test Album")
        val photo1 = db.insertTestPhoto("/p/1.jpg")
        val photo2 = db.insertTestPhoto("/p/2.jpg")

        db.casaPicQueries.insertAlbumPhoto(albumId, photo1, 0L)
        db.casaPicQueries.insertAlbumPhoto(albumId, photo2, 1L)

        assertEquals(2L, db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne())
    }

    @Test
    fun `removeAlbumPhoto decrements count`() {
        val albumId = insertAlbum("Test Album")
        val photo1 = db.insertTestPhoto("/p/1.jpg")
        val photo2 = db.insertTestPhoto("/p/2.jpg")
        db.casaPicQueries.insertAlbumPhoto(albumId, photo1, 0L)
        db.casaPicQueries.insertAlbumPhoto(albumId, photo2, 1L)

        db.casaPicQueries.removeAlbumPhoto(albumId, photo1)
        assertEquals(1L, db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne())
    }

    @Test
    fun `deleteAlbum cascades to album_photos`() {
        val albumId = insertAlbum("Doomed")
        val photoId = db.insertTestPhoto()
        db.casaPicQueries.insertAlbumPhoto(albumId, photoId, 0L)
        db.casaPicQueries.deleteAlbum(albumId)

        val photos = db.casaPicQueries.getPhotosInAlbum(albumId).executeAsList()
        assertTrue(photos.isEmpty())
    }

    @Test
    fun `insertAlbumPhoto is idempotent (INSERT OR IGNORE)`() {
        val albumId = insertAlbum("Test")
        val photoId = db.insertTestPhoto()
        db.casaPicQueries.insertAlbumPhoto(albumId, photoId, 0L)
        db.casaPicQueries.insertAlbumPhoto(albumId, photoId, 0L)
        assertEquals(1L, db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne())
    }

    @Test
    fun `getPhotosInAlbumPaged returns correct page`() {
        val albumId = insertAlbum("Big Album")
        val photoIds = (1..8).map { i ->
            db.insertTestPhoto("/p/photo_$i.jpg").also { id ->
                db.casaPicQueries.insertAlbumPhoto(albumId, id, i.toLong())
            }
        }

        val page1 = db.casaPicQueries.getPhotosInAlbumPaged(albumId, 3L, 0L).executeAsList()
        val page2 = db.casaPicQueries.getPhotosInAlbumPaged(albumId, 3L, 3L).executeAsList()
        val page3 = db.casaPicQueries.getPhotosInAlbumPaged(albumId, 3L, 6L).executeAsList()

        assertEquals(3, page1.size)
        assertEquals(3, page2.size)
        assertEquals(2, page3.size)
    }

    @Test
    fun `getTotalDiskSizeForAlbum sums sizes of album members only`() {
        val albumId = insertAlbum("Sized Album")
        val p1 = db.insertTestPhoto("/p/big.jpg", fileSize = 5000L)
        val p2 = db.insertTestPhoto("/p/small.jpg", fileSize = 1000L)
        val outsider = db.insertTestPhoto("/p/outside.jpg", fileSize = 9999L)

        db.casaPicQueries.insertAlbumPhoto(albumId, p1, 0L)
        db.casaPicQueries.insertAlbumPhoto(albumId, p2, 1L)

        val total = db.casaPicQueries.getTotalDiskSizeForAlbum(albumId).executeAsOne()
        assertEquals(6000L, total)
    }

    @Test
    fun `updateAlbumName persists new name`() {
        val id = insertAlbum("Old Name")
        db.casaPicQueries.updateAlbumName("New Name", System.currentTimeMillis(), id)
        assertEquals("New Name", db.casaPicQueries.getAlbumById(id).executeAsOne().name)
    }
}
