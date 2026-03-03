package com.smartfolder.di

import com.smartfolder.data.repository.DecisionRepositoryImpl
import com.smartfolder.data.repository.EmbeddingRepositoryImpl
import com.smartfolder.data.repository.FolderRepositoryImpl
import com.smartfolder.data.repository.ImageRepositoryImpl
import com.smartfolder.data.repository.RoomTransactionRunner
import com.smartfolder.data.repository.SettingsRepositoryImpl
import com.smartfolder.data.repository.SuggestionRepositoryImpl
import com.smartfolder.domain.repository.DecisionRepository
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.repository.TransactionRunner
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
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds
    @Singleton
    abstract fun bindEmbeddingRepository(impl: EmbeddingRepositoryImpl): EmbeddingRepository

    @Binds
    @Singleton
    abstract fun bindDecisionRepository(impl: DecisionRepositoryImpl): DecisionRepository

    @Binds
    @Singleton
    abstract fun bindSuggestionRepository(impl: SuggestionRepositoryImpl): SuggestionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
