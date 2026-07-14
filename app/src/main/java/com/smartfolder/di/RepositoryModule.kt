package com.smartfolder.di

import com.smartfolder.data.repository.FolderRepositoryImpl
import com.smartfolder.data.repository.SettingsRepositoryImpl
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
