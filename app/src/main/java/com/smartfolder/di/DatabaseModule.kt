package com.smartfolder.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartfolder.data.local.db.AppDatabase
import com.smartfolder.data.local.db.dao.FolderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
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
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_suggestions_imageId` ON `suggestions` (`imageId`)"
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `decisions_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `imageId` INTEGER NOT NULL,
                    `accepted` INTEGER NOT NULL,
                    `score` REAL NOT NULL,
                    `decidedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`imageId`) REFERENCES `images`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `decisions_new` (`id`, `imageId`, `accepted`, `score`, `decidedAt`)
                SELECT d.`id`, d.`imageId`, d.`accepted`, d.`score`, d.`decidedAt`
                FROM `decisions` d
                WHERE d.`id` = (
                    SELECT d2.`id`
                    FROM `decisions` d2
                    WHERE d2.`imageId` = d.`imageId`
                    ORDER BY d2.`decidedAt` DESC, d2.`id` DESC
                    LIMIT 1
                )
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `decisions`")
            db.execSQL("ALTER TABLE `decisions_new` RENAME TO `decisions`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_decisions_imageId` ON `decisions` (`imageId`)"
            )
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `embeddings_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `imageId` INTEGER NOT NULL,
                    `vectorBlob` BLOB NOT NULL,
                    `modelName` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`imageId`) REFERENCES `images`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `embeddings_new` (`id`, `imageId`, `vectorBlob`, `modelName`, `createdAt`)
                SELECT e.`id`, e.`imageId`, e.`vectorBlob`, e.`modelName`, e.`createdAt`
                FROM `embeddings` e
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `embeddings`")
            db.execSQL("ALTER TABLE `embeddings_new` RENAME TO `embeddings`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_embeddings_imageId_modelName` ON `embeddings` (`imageId`, `modelName`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_embeddings_modelName` ON `embeddings` (`modelName`)"
            )
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE folders SET role = 'DESTINATION' WHERE role = 'REFERENCE'")
            db.execSQL("UPDATE folders SET role = 'SOURCE' WHERE role = 'UNSORTED'")

            db.execSQL("ALTER TABLE suggestions RENAME TO suggestions_old")
            db.execSQL(
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
            db.execSQL(
                """
                INSERT INTO `suggestions` (
                    `id`,
                    `imageId`,
                    `destinationFolderId`,
                    `score`,
                    `secondBestScore`,
                    `centroidScore`,
                    `topKScore`,
                    `topSimilarIds`,
                    `topSimilarScores`,
                    `createdAt`
                )
                SELECT
                    `id`,
                    `imageId`,
                    0,
                    `score`,
                    0.0,
                    `centroidScore`,
                    `topKScore`,
                    `topSimilarIds`,
                    `topSimilarScores`,
                    `createdAt`
                FROM `suggestions_old`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `suggestions_old`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_suggestions_imageId` ON `suggestions` (`imageId`)"
            )
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `suggestions` ADD COLUMN `candidateIds` TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE `suggestions` ADD COLUMN `candidateScores` TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `suggestions` ADD COLUMN `reviewStatus` TEXT NOT NULL DEFAULT 'PENDING'"
            )
        }
    }

    // The app dropped ML indexing and suggestions: only folders remain.
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `embeddings`")
            db.execSQL("DROP TABLE IF EXISTS `suggestions`")
            db.execSQL("DROP TABLE IF EXISTS `decisions`")
            db.execSQL("DROP TABLE IF EXISTS `images`")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartfolder_db"
        )
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .build()
    }

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()
}
