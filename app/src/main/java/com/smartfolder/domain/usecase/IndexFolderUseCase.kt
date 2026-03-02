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
import com.smartfolder.domain.repository.TransactionRunner
import com.smartfolder.ml.BitmapLoader
import com.smartfolder.ml.ImageEmbedderWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import javax.inject.Inject

class IndexFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val imageRepository: ImageRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val safManager: SafManager,
    private val bitmapLoader: BitmapLoader,
    private val imageEmbedder: ImageEmbedderWrapper,
    private val transactionRunner: TransactionRunner
) {
    companion object {
        private const val REGISTRATION_BATCH_SIZE = 500
    }

    operator fun invoke(folder: Folder, modelChoice: ModelChoice): Flow<IndexingProgress> = flow {
        emit(IndexingProgress(phase = IndexingPhase.LISTING_FILES))

        try {
            // Verify SAF permission is still valid
            if (!safManager.hasPersistedPermission(folder.uri)) {
                emit(
                    IndexingProgress(
                        phase = IndexingPhase.ERROR,
                        errorMessage = "Folder access was revoked. Please re-select the folder."
                    )
                )
                return@flow
            }

            // Initialize embedder
            imageEmbedder.initialize(modelChoice.modelFileName)

            // List image files from folder (uses fast ContentResolver query)
            val imageFiles = safManager.listImageFiles(folder.uri)
            if (imageFiles.isEmpty()) {
                emit(IndexingProgress(phase = IndexingPhase.COMPLETE, total = 0))
                return@flow
            }

            // Register images in database using batch operations
            registerImagesBatch(folder, imageFiles)

            // Reload images from DB to get IDs
            val dbImages = imageRepository.getByFolder(folder.id)
            val total = dbImages.size

            emit(IndexingProgress(phase = IndexingPhase.EMBEDDING, total = total))

            var indexed = 0
            var failed = 0
            for (image in dbImages) {
                yield() // Allow cancellation between images

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
                    // MediaPipe ImageEmbedder already L2-normalizes with setL2Normalize(true)
                    val vector = imageEmbedder.embed(bitmap)
                    bitmap.recycle()

                    if (vector != null) {
                        val embedding = Embedding(
                            imageId = image.id,
                            vector = vector,
                            modelName = modelChoice.modelFileName
                        )
                        existingEmbedding?.let { embeddingRepository.delete(it) }
                        embeddingRepository.insert(embedding)
                    } else {
                        failed++
                    }
                } else {
                    failed++
                }

                indexed++
                val statusSuffix = if (failed > 0) " ($failed failed)" else ""
                emit(
                    IndexingProgress(
                        phase = IndexingPhase.EMBEDDING,
                        current = indexed,
                        total = total,
                        currentFileName = image.displayName + statusSuffix
                    )
                )
            }

            // Update folder with successful count
            val successCount = indexed - failed
            folderRepository.update(
                folder.copy(
                    indexedCount = successCount,
                    imageCount = total,
                    lastIndexedAt = System.currentTimeMillis()
                )
            )

            val completeMessage = if (failed > 0) "$failed image(s) could not be processed" else ""
            emit(IndexingProgress(
                phase = IndexingPhase.COMPLETE,
                current = total,
                total = total,
                currentFileName = completeMessage
            ))
        } catch (e: Exception) {
            emit(
                IndexingProgress(
                    phase = IndexingPhase.ERROR,
                    errorMessage = e.message ?: "Unknown error during indexing"
                )
            )
        }
    }

    /**
     * Batch image registration: uses bulk URI lookup and batch insert
     * instead of individual getByUri + insert per image (N+1 -> 2 queries per batch).
     */
    private suspend fun registerImagesBatch(
        folder: Folder,
        imageFiles: List<com.smartfolder.data.saf.SafImageFile>
    ) {
        val allImages = imageFiles.map { file ->
            ImageInfo(
                folderId = folder.id,
                uri = file.uri,
                displayName = file.displayName,
                contentHash = "${file.sizeBytes}_${file.lastModified}",
                sizeBytes = file.sizeBytes,
                lastModified = file.lastModified
            )
        }

        // Process in batches to avoid memory pressure
        for (batch in allImages.chunked(REGISTRATION_BATCH_SIZE)) {
            yield()

            transactionRunner.runInTransaction {
                // Bulk fetch existing images by URI
                val uris = batch.map { it.uri.toString() }
                val existing = imageRepository.getByUris(uris)
                val existingByUri = existing.associateBy { it.uri.toString() }

                // Separate new images from existing
                val toInsert = mutableListOf<ImageInfo>()
                for (image in batch) {
                    val uriStr = image.uri.toString()
                    val existingImage = existingByUri[uriStr]
                    if (existingImage == null) {
                        toInsert.add(image)
                    } else if (existingImage.contentHash != image.contentHash) {
                        // Content changed: update image and invalidate embedding
                        imageRepository.update(image.copy(id = existingImage.id))
                        embeddingRepository.getByImageId(existingImage.id)?.let {
                            embeddingRepository.delete(it)
                        }
                    }
                    // If existing and hash matches, skip (already registered)
                }

                // Batch insert new images
                if (toInsert.isNotEmpty()) {
                    imageRepository.insertAll(toInsert)
                }
            }
        }
    }
}
