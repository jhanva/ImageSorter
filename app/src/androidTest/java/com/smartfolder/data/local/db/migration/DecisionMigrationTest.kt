package com.smartfolder.data.local.db.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartfolder.data.local.db.AppDatabase
import com.smartfolder.data.local.db.entities.DecisionEntity
import com.smartfolder.di.DatabaseModule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DecisionMigrationTest {

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
    fun migrate3To4_deduplicatesDecisionsAndEnforcesUniqueImageId() = runTest {
        createVersion3SchemaWithDuplicateDecisions()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .addMigrations(DatabaseModule.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val decisionDao = db.decisionDao()

            val migrated = decisionDao.getByImageId(10L)
            assertNotNull(migrated)
            assertEquals(true, migrated?.accepted)
            assertEquals(0.9f, migrated?.score ?: 0f, 0.0001f)
            assertEquals(2_000L, migrated?.decidedAt)

            // Unique index + REPLACE should keep one row per imageId.
            decisionDao.insert(
                DecisionEntity(
                    imageId = 10L,
                    accepted = false,
                    score = 0.1f,
                    decidedAt = 3_000L
                )
            )

            val updated = decisionDao.getByImageId(10L)
            assertNotNull(updated)
            assertEquals(false, updated?.accepted)
            assertEquals(0.1f, updated?.score ?: 0f, 0.0001f)
            assertEquals(3_000L, updated?.decidedAt)

            val countCursor = db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM decisions WHERE imageId = 10"
            )
            countCursor.use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    private fun createVersion3SchemaWithDuplicateDecisions() {
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
            sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_embeddings_imageId` ON `embeddings` (`imageId`)")
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
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_decisions_imageId` ON `decisions` (`imageId`)")

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
                    "VALUES (1, 'content://folder', 'Ref', 'REFERENCE', 1, 1, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO images (id, folderId, uri, displayName, contentHash, sizeBytes, lastModified) " +
                    "VALUES (10, 1, 'content://img/10', 'img10.jpg', '100_100', 100, 100)"
            )

            // Legacy duplicate decisions for same imageId.
            sqlite.execSQL(
                "INSERT INTO decisions (id, imageId, accepted, score, decidedAt) VALUES (100, 10, 0, 0.2, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO decisions (id, imageId, accepted, score, decidedAt) VALUES (101, 10, 1, 0.9, 2000)"
            )

            sqlite.execSQL("PRAGMA user_version=3")
        } finally {
            sqlite.close()
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-decisions-test.db"
    }
}
