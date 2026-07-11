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
class CandidateMigrationTest {

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
    fun migrate6To7_addsEmptyCandidateColumnsToExistingSuggestions() = runTest {
        createVersion6Schema()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .addMigrations(DatabaseModule.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        try {
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT imageId, destinationFolderId, candidateIds, candidateScores FROM suggestions"
            )
            cursor.use {
                it.moveToFirst()
                assertEquals(10L, it.getLong(0))
                assertEquals(1L, it.getLong(1))
                assertEquals("", it.getString(2))
                assertEquals("", it.getString(3))
            }
        } finally {
            db.close()
        }
    }

    private fun createVersion6Schema() {
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
                    `destinationFolderId` INTEGER NOT NULL,
                    `score` REAL NOT NULL,
                    `secondBestScore` REAL NOT NULL,
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
                    "VALUES (1, 'content://folder/destination', 'Dest', 'DESTINATION', 1, 1, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO folders (id, uri, displayName, role, imageCount, indexedCount, lastIndexedAt) " +
                    "VALUES (2, 'content://folder/source', 'Source', 'SOURCE', 1, 1, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO images (id, folderId, uri, displayName, contentHash, sizeBytes, lastModified) " +
                    "VALUES (10, 2, 'content://img/10', 'img10.jpg', '100_100', 100, 100)"
            )
            sqlite.execSQL(
                """
                INSERT INTO suggestions (
                    id, imageId, destinationFolderId, score, secondBestScore,
                    centroidScore, topKScore, topSimilarIds, topSimilarScores, createdAt
                )
                VALUES (100, 10, 1, 0.91, 0.4, 0.82, 0.95, '1,2', '0.99,0.97', 1234)
                """.trimIndent()
            )

            sqlite.execSQL("PRAGMA user_version=6")
        } finally {
            sqlite.close()
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-candidates-test.db"
    }
}
