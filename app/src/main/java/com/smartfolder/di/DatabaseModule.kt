package com.smartfolder.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartfolder.data.local.db.AppDatabase
import com.smartfolder.data.local.db.dao.DecisionDao
import com.smartfolder.data.local.db.dao.EmbeddingDao
import com.smartfolder.data.local.db.dao.FolderDao
import com.smartfolder.data.local.db.dao.ImageDao
import com.smartfolder.data.local.db.dao.SuggestionDao
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
            .build()
    }

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideImageDao(database: AppDatabase): ImageDao = database.imageDao()

    @Provides
    fun provideEmbeddingDao(database: AppDatabase): EmbeddingDao = database.embeddingDao()

    @Provides
    fun provideDecisionDao(database: AppDatabase): DecisionDao = database.decisionDao()

    @Provides
    fun provideSuggestionDao(database: AppDatabase): SuggestionDao = database.suggestionDao()
}
