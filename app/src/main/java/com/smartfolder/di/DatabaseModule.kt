package com.smartfolder.di

import android.content.Context
import androidx.room.Room
import com.smartfolder.data.local.db.AppDatabase
import com.smartfolder.data.local.db.dao.DecisionDao
import com.smartfolder.data.local.db.dao.EmbeddingDao
import com.smartfolder.data.local.db.dao.FolderDao
import com.smartfolder.data.local.db.dao.ImageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartfolder_db"
        ).build()
    }

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideImageDao(database: AppDatabase): ImageDao = database.imageDao()

    @Provides
    fun provideEmbeddingDao(database: AppDatabase): EmbeddingDao = database.embeddingDao()

    @Provides
    fun provideDecisionDao(database: AppDatabase): DecisionDao = database.decisionDao()
}
