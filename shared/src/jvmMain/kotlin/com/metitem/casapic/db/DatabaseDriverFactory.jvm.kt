package com.metitem.casapic.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".casapic")
        dbDir.mkdirs()
        val dbFile = File(dbDir, "casapic.db")
        val isNewDb = !dbFile.exists()
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            properties = Properties().apply { put("foreign_keys", "true") }
        )
        if (isNewDb) {
            CasaPicDatabase.Schema.create(driver)
            setupFts5AndTriggers(driver)
        }
        return driver
    }

    private fun setupFts5AndTriggers(driver: SqlDriver) {
        driver.execute(null, """
            CREATE VIRTUAL TABLE IF NOT EXISTS photos_fts USING fts5(
                file_name,
                description,
                caption,
                iptc_keywords,
                iptc_author,
                camera_make,
                camera_model,
                content=photos,
                content_rowid=id
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
}
