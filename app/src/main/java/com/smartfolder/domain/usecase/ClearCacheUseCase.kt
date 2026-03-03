package com.smartfolder.domain.usecase

import com.smartfolder.domain.repository.DecisionRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.repository.TransactionRunner
import com.smartfolder.ml.ImageEmbedderWrapper
import javax.inject.Inject

class ClearCacheUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val decisionRepository: DecisionRepository,
    private val suggestionRepository: SuggestionRepository,
    private val imageEmbedder: ImageEmbedderWrapper,
    private val transactionRunner: TransactionRunner
) {
    suspend operator fun invoke() {
        imageEmbedder.close()
        transactionRunner.runInTransaction {
            decisionRepository.deleteAll()
            suggestionRepository.deleteAll()
            folderRepository.deleteAll() // Cascades to images and embeddings
        }
    }
}
