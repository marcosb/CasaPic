package com.metitem.casapic.viewmodel

import com.metitem.casapic.db.awaitTrue
import com.metitem.casapic.db.createInMemoryDatabase
import com.metitem.casapic.db.insertTestPhoto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlbumsViewModelTest {

    private lateinit var db: com.metitem.casapic.db.CasaPicDatabase
    private lateinit var vm: AlbumsViewModel

    @BeforeEach
    fun setup() {
        db = createInMemoryDatabase()
        vm = AlbumsViewModel(db)
    }

    private fun waitForAlbum(name: String): Long {
        awaitTrue { db.casaPicQueries.getAllAlbums().executeAsList().any { it.name == name } }
        return db.casaPicQueries.getAllAlbums().executeAsList().first { it.name == name }.id
    }

    @Test
    fun `createAlbum inserts album in DB`() {
        vm.createAlbum("Summer 2024")
        awaitTrue { db.casaPicQueries.getAllAlbums().executeAsList().isNotEmpty() }
        assertEquals("Summer 2024", db.casaPicQueries.getAllAlbums().executeAsOne().name)
    }

    @Test
    fun `renameAlbum changes name in DB`() {
        vm.createAlbum("Old Name")
        val albumId = waitForAlbum("Old Name")

        vm.renameAlbum(albumId, "New Name")
        awaitTrue { db.casaPicQueries.getAlbumById(albumId).executeAsOne().name == "New Name" }
    }

    @Test
    fun `deleteAlbum removes row from DB`() {
        vm.createAlbum("Doomed")
        val albumId = waitForAlbum("Doomed")

        vm.deleteAlbum(albumId)
        awaitTrue { db.casaPicQueries.getAllAlbums().executeAsList().isEmpty() }
    }

    @Test
    fun `addPhotoToAlbum increments count`() {
        vm.createAlbum("Test")
        val albumId = waitForAlbum("Test")
        val photoId = db.insertTestPhoto()

        vm.addPhotoToAlbum(albumId, photoId)
        awaitTrue { db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne() == 1L }
    }

    @Test
    fun `removePhotoFromAlbum decrements count`() {
        vm.createAlbum("Test")
        val albumId = waitForAlbum("Test")
        val p1 = db.insertTestPhoto("/p1.jpg")
        val p2 = db.insertTestPhoto("/p2.jpg")

        vm.addPhotoToAlbum(albumId, p1)
        vm.addPhotoToAlbum(albumId, p2)
        awaitTrue { db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne() == 2L }

        vm.removePhotoFromAlbum(albumId, p1)
        awaitTrue { db.casaPicQueries.countPhotosInAlbum(albumId).executeAsOne() == 1L }
    }
}
