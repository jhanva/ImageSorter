package com.smartfolder.domain.usecase

import com.smartfolder.domain.repository.DecisionRepository
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.ml.ImageEmbedderWrapper
import javax.inject.Inject

class ClearCacheUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val imageRepository: ImageRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val decisionRepository: DecisionRepository,
    private val imageEmbedder: ImageEmbedderWrapper
) {
    suspend operator fun invoke() {
        imageEmbedder.close()
        decisionRepository.deleteAll()
        folderRepository.deleteAll()
    }
}
