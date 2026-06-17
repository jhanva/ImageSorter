package com.smartfolder.data.local.db.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartfolder.data.local.db.AppDatabase
import com.smartfolder.di.DatabaseModule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FolderSuggestionMigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(TEST_DB_NAME)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        dbFile.parentFile?.mkdirs()
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun migrate5To6_renamesFolderRolesAndExpandsSuggestionsSchema() = runTest {
        createVersion5Schema()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .addMigrations(DatabaseModule.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        try {
            val roleCursor = db.openHelper.readableDatabase.query(
                "SELECT id, role FROM folders ORDER BY id"
            )
            roleCursor.use {
                it.moveToFirst()
                assertEquals(1L, it.getLong(0))
                assertEquals("DESTINATION", it.getString(1))
                it.moveToNext()
                assertEquals(2L, it.getLong(0))
                assertEquals("SOURCE", it.getString(1))
            }

            val suggestionCursor = db.openHelper.readableDatabase.query(
                """
                SELECT imageId, destinationFolderId, score, secondBestScore, centroidScore, topKScore,
                       topSimilarIds, topSimilarScores, createdAt
                FROM suggestions
                """.trimIndent()
            )
            suggestionCursor.use {
                it.moveToFirst()
                assertEquals(10L, it.getLong(0))
                assertEquals(0L, it.getLong(1))
                assertEquals(0.91f, it.getFloat(2))
                assertEquals(0f, it.getFloat(3))
                assertEquals(0.82f, it.getFloat(4))
                assertEquals(0.95f, it.getFloat(5))
                assertEquals("1,2,3", it.getString(6))
                assertEquals("0.99,0.97,0.95", it.getString(7))
                assertEquals(1234L, it.getLong(8))
            }
        } finally {
            db.close()
        }
    }

    private fun createVersion5Schema() {
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            sqlite.execSQL("PRAGMA foreign_keys=ON")

            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `folders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `uri` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `imageCount` INTEGER NOT NULL,
                    `indexedCount` INTEGER NOT NULL,
                    `lastIndexedAt` INTEGER
                )
                """.trimIndent()
            )

            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `images` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `folderId` INTEGER NOT NULL,
                    `uri` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `contentHash` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `lastModified` INTEGER NOT NULL,
                    FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_images_folderId` ON `images` (`folderId`)")
            sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_images_uri` ON `images` (`uri`)")

            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `embeddings` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `imageId` INTEGER NOT NULL,
                    `vectorBlob` BLOB NOT NULL,
                    `modelName` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`imageId`) REFERENCES `images`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_embeddings_imageId_modelName` ON `embeddings` (`imageId`, `modelName`)"
            )
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_embeddings_modelName` ON `embeddings` (`modelName`)")

            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `decisions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `imageId` INTEGER NOT NULL,
                    `accepted` INTEGER NOT NULL,
                    `score` REAL NOT NULL,
                    `decidedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`imageId`) REFERENCES `images`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_decisions_imageId` ON `decisions` (`imageId`)")

            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `suggestions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `imageId` INTEGER NOT NULL,
                    `score` REAL NOT NULL,
                    `centroidScore` REAL NOT NULL,
                    `topKScore` REAL NOT NULL,
                    `topSimilarIds` TEXT NOT NULL,
                    `topSimilarScores` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_suggestions_imageId` ON `suggestions` (`imageId`)")

            sqlite.execSQL(
                "INSERT INTO folders (id, uri, displayName, role, imageCount, indexedCount, lastIndexedAt) " +
                    "VALUES (1, 'content://folder/destination', 'Dest', 'REFERENCE', 1, 1, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO folders (id, uri, displayName, role, imageCount, indexedCount, lastIndexedAt) " +
                    "VALUES (2, 'content://folder/source', 'Source', 'UNSORTED', 1, 1, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO images (id, folderId, uri, displayName, contentHash, sizeBytes, lastModified) " +
                    "VALUES (10, 2, 'content://img/10', 'img10.jpg', '100_100', 100, 100)"
            )
            sqlite.execSQL(
                """
                INSERT INTO suggestions (id, imageId, score, centroidScore, topKScore, topSimilarIds, topSimilarScores, createdAt)
                VALUES (100, 10, 0.91, 0.82, 0.95, '1,2,3', '0.99,0.97,0.95', 1234)
                """.trimIndent()
            )

            sqlite.execSQL("PRAGMA user_version=5")
        } finally {
            sqlite.close()
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-folders-suggestions-test.db"
    }
}
