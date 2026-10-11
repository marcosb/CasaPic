package com.metitem.casapic.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

fun createInMemoryDatabase(): CasaPicDatabase {
    val driver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        schema = CasaPicDatabase.Schema
    )
    // SQLite does not enforce FK constraints by default; must enable explicitly.
    driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    setupFts5AndTriggers(driver)
    return CasaPicDatabase(driver)
}

/** Poll [condition] up to [timeoutMs] ms; throw if it never becomes true. */
fun awaitTrue(timeoutMs: Long = 2000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(20)
    }
    throw AssertionError("Condition not met within ${timeoutMs}ms")
}

private fun setupFts5AndTriggers(driver: app.cash.sqldelight.db.SqlDriver) {
    driver.execute(null, """
        CREATE VIRTUAL TABLE IF NOT EXISTS photos_fts USING fts5(
            file_name, description, caption,
            iptc_keywords, iptc_author, camera_make, camera_model,
            content=photos, content_rowid=id
        )
    """.trimIndent(), 0)
    driver.execute(null, """
        CREATE TRIGGER IF NOT EXISTS photos_fts_after_insert
        AFTER INSERT ON photos BEGIN
            INSERT INTO photos_fts(rowid, file_name, description, caption,
                iptc_keywords, iptc_author, camera_make, camera_model)
            VALUES (new.id, new.file_name, new.description, new.caption,
                new.iptc_keywords, new.iptc_author, new.camera_make, new.camera_model);
        END
    """.trimIndent(), 0)
    driver.execute(null, """
        CREATE TRIGGER IF NOT EXISTS photos_fts_after_delete
        AFTER DELETE ON photos BEGIN
            INSERT INTO photos_fts(photos_fts, rowid, file_name, description, caption,
                iptc_keywords, iptc_author, camera_make, camera_model)
            VALUES ('delete', old.id, old.file_name, old.description, old.caption,
                old.iptc_keywords, old.iptc_author, old.camera_make, old.camera_model);
        END
    """.trimIndent(), 0)
    driver.execute(null, """
        CREATE TRIGGER IF NOT EXISTS photos_fts_after_update
        AFTER UPDATE ON photos BEGIN
            INSERT INTO photos_fts(photos_fts, rowid, file_name, description, caption,
                iptc_keywords, iptc_author, camera_make, camera_model)
            VALUES ('delete', old.id, old.file_name, old.description, old.caption,
                old.iptc_keywords, old.iptc_author, old.camera_make, old.camera_model);
            INSERT INTO photos_fts(rowid, file_name, description, caption,
                iptc_keywords, iptc_author, camera_make, camera_model)
            VALUES (new.id, new.file_name, new.description, new.caption,
                new.iptc_keywords, new.iptc_author, new.camera_make, new.camera_model);
        END
    """.trimIndent(), 0)
}

fun CasaPicDatabase.insertTestFolder(
    path: String = "/test/folder",
    name: String = "Test Folder"
): Long {
    casaPicQueries.insertFolder(path, name, 1L, 1L, System.currentTimeMillis())
    return casaPicQueries.getFolderByPath(path).executeAsOne().id
}

fun CasaPicDatabase.insertTestPhoto(
    filePath: String = "/test/photo.jpg",
    fileName: String = filePath.substringAfterLast("/", "photo.jpg"),
    folderId: Long? = null,
    fileSize: Long? = 1024L,
    starRating: Long = 0L,
    isHidden: Long = 0L,
    captureDate: Long? = System.currentTimeMillis(),
    caption: String? = null,
    iptcKeywords: String? = null,
    cameraModel: String? = null
): Long {
    casaPicQueries.insertPhoto(
        file_path = filePath,
        folder_id = folderId,
        file_name = fileName,
        file_size = fileSize,
        width = 1920L,
        height = 1080L,
        capture_date_millis = captureDate,
        modified_date_millis = System.currentTimeMillis(),
        imported_date_millis = System.currentTimeMillis(),
        camera_make = null,
        camera_model = cameraModel,
        focal_length = null,
        f_number = null,
        iso = null,
        exposure_time = null,
        flash = null,
        gps_latitude = null,
        gps_longitude = null,
        gps_altitude = null,
        orientation = null,
        jpeg_quality = null,
        unique_id = null,
        description = null,
        caption = caption,
        iptc_copyright = null,
        iptc_author = null,
        iptc_headline = null,
        iptc_keywords = iptcKeywords,
        xmp_rating = null,
        xmp_label = null,
        xmp_subject = null,
        star_rating = starRating,
        is_hidden = isHidden,
        block_upload = 0L
    )
    return casaPicQueries.getPhotoIdByPath(filePath).executeAsOne()
}
