package com.smartfolder.di

import android.content.Context
import com.smartfolder.ml.ImageEmbedderWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    @Provides
    @Singleton
    fun provideImageEmbedderWrapper(@ApplicationContext context: Context): ImageEmbedderWrapper {
        return ImageEmbedderWrapper(context)
    }
}
