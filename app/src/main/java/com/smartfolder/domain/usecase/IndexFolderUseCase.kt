package com.smartfolder.domain.usecase

import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.IndexingProgress
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.ml.BitmapLoader
import com.smartfolder.ml.EmbeddingNormalizer
import com.smartfolder.ml.ImageEmbedderWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class IndexFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val imageRepository: ImageRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val safManager: SafManager,
    private val bitmapLoader: BitmapLoader,
    private val imageEmbedder: ImageEmbedderWrapper
) {
    operator fun invoke(folder: Folder, modelChoice: ModelChoice): Flow<IndexingProgress> = flow {
        emit(IndexingProgress(phase = IndexingPhase.LISTING_FILES))

        try {
            // Initialize embedder
            imageEmbedder.initialize(modelChoice.modelFileName)

            // List image files from folder
            val imageFiles = safManager.listImageFiles(folder.uri)
            if (imageFiles.isEmpty()) {
                emit(IndexingProgress(phase = IndexingPhase.COMPLETE, total = 0))
                return@flow
            }

            // Register images in database
            val images = imageFiles.map { file ->
                ImageInfo(
                    folderId = folder.id,
                    uri = file.uri,
                    displayName = file.displayName,
                    contentHash = "${file.sizeBytes}_${file.lastModified}",
                    sizeBytes = file.sizeBytes,
                    lastModified = file.lastModified
                )
            }

            // Insert or update images
            for (image in images) {
                val existing = imageRepository.getByUri(image.uri.toString())
                if (existing == null) {
                    imageRepository.insert(image)
                } else if (existing.contentHash != image.contentHash) {
                    imageRepository.update(image.copy(id = existing.id))
                    // Invalidate embedding if content changed
                    embeddingRepository.getByImageId(existing.id)?.let {
                        embeddingRepository.delete(it)
                    }
                }
            }

            // Reload images from DB to get IDs
            val dbImages = imageRepository.getByFolder(folder.id)
            val total = dbImages.size

            emit(IndexingProgress(phase = IndexingPhase.EMBEDDING, total = total))

            var indexed = 0
            for (image in dbImages) {
                // Check if embedding already exists with correct model
                val existingEmbedding = embeddingRepository.getByImageId(image.id)
                if (existingEmbedding != null && existingEmbedding.modelName == modelChoice.modelFileName) {
                    indexed++
                    emit(
                        IndexingProgress(
                            phase = IndexingPhase.EMBEDDING,
                            current = indexed,
                            total = total,
                            currentFileName = image.displayName
                        )
                    )
                    continue
                }

                // Load bitmap and compute embedding
                val bitmap = bitmapLoader.loadForEmbedding(image.uri)
                if (bitmap != null) {
                    val vector = imageEmbedder.embed(bitmap)
                    bitmap.recycle()

                    if (vector != null) {
                        val normalized = EmbeddingNormalizer.normalize(vector)
                        val embedding = Embedding(
                            imageId = image.id,
                            vector = normalized,
                            modelName = modelChoice.modelFileName
                        )
                        // Delete old embedding if exists
                        existingEmbedding?.let { embeddingRepository.delete(it) }
                        embeddingRepository.insert(embedding)
                    }
                }

                indexed++
                emit(
                    IndexingProgress(
                        phase = IndexingPhase.EMBEDDING,
                        current = indexed,
                        total = total,
                        currentFileName = image.displayName
                    )
                )
            }

            // Update folder
            folderRepository.update(
                folder.copy(
                    indexedCount = indexed,
                    imageCount = total,
                    lastIndexedAt = System.currentTimeMillis()
                )
            )

            emit(IndexingProgress(phase = IndexingPhase.COMPLETE, current = total, total = total))
        } catch (e: Exception) {
            emit(
                IndexingProgress(
                    phase = IndexingPhase.ERROR,
                    errorMessage = e.message ?: "Unknown error during indexing"
                )
            )
        }
    }
}
