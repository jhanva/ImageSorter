package com.smartfolder.di

import android.content.Context
import com.smartfolder.data.local.datastore.SettingsDataStore
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.data.saf.SafManager
import com.smartfolder.ml.BitmapLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSafManager(@ApplicationContext context: Context): SafManager {
        return SafManager(context)
    }

    @Provides
    @Singleton
    fun provideSafFileOps(@ApplicationContext context: Context): SafFileOps {
        return SafFileOps(context)
    }

    @Provides
    @Singleton
    fun provideBitmapLoader(@ApplicationContext context: Context): BitmapLoader {
        return BitmapLoader(context)
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }
}
