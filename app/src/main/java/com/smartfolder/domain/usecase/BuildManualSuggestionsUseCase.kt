package com.smartfolder.domain.usecase

import android.provider.DocumentsContract
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SuggestionRepository
import javax.inject.Inject

class BuildManualSuggestionsUseCase @Inject constructor(
    private val mediaStoreFolderProvider: MediaStoreFolderProvider,
    private val safManager: SafManager,
    private val imageRepository: ImageRepository,
    private val suggestionRepository: SuggestionRepository
) {
    suspend operator fun invoke(unsortedFolder: Folder): List<SuggestionItem> {
        suggestionRepository.deleteAll()

        val files = listImageFiles(unsortedFolder)
        if (files.isEmpty()) return emptyList()

        val candidateImages = files.map { file ->
            ImageInfo(
                folderId = unsortedFolder.id,
                uri = file.uri,
                displayName = file.displayName,
                contentHash = "${file.sizeBytes}_${file.lastModified}",
                sizeBytes = file.sizeBytes,
                lastModified = file.lastModified
            )
        }

        val existingByUri = imageRepository
            .getByUris(candidateImages.map { it.uri.toString() })
            .associateBy { it.uri.toString() }

        val resolved = mutableListOf<ImageInfo>()
        val toInsert = mutableListOf<ImageInfo>()
        val toUpdate = mutableListOf<ImageInfo>()

        candidateImages.forEach { candidate ->
            val existing = existingByUri[candidate.uri.toString()]
            if (existing == null) {
                toInsert.add(candidate)
            } else {
                val updated = candidate.copy(id = existing.id)
                if (existing.contentHash != updated.contentHash || existing.displayName != updated.displayName) {
                    toUpdate.add(updated)
                    resolved.add(updated)
                } else {
                    resolved.add(existing)
                }
            }
        }

        toUpdate.forEach { imageRepository.update(it) }

        if (toInsert.isNotEmpty()) {
            val insertedIds = imageRepository.insertAll(toInsert)
            resolved.addAll(
                toInsert.zip(insertedIds).map { (image, id) -> image.copy(id = id) }
            )
        }

        val suggestions = resolved
            .sortedBy { it.displayName.lowercase() }
            .map { image ->
                SuggestionItem(
                    image = image,
                    score = 1f,
                    centroidScore = 1f,
                    topKScore = 1f,
                    topSimilarFromA = emptyList()
                )
            }

        val createdAt = System.currentTimeMillis()
        val stored = suggestions.map { suggestion ->
            StoredSuggestion(
                imageId = suggestion.image.id,
                score = suggestion.score,
                centroidScore = suggestion.centroidScore,
                topKScore = suggestion.topKScore,
                topSimilarIds = emptyList(),
                topSimilarScores = emptyList(),
                createdAt = createdAt
            )
        }
        suggestionRepository.replaceAll(stored)
        return suggestions
    }

    private fun listImageFiles(folder: Folder) =
        runCatching { DocumentsContract.getTreeDocumentId(folder.uri) }
            .getOrNull()
            ?.let { documentId ->
                mediaStoreFolderProvider.listImageFilesForDocumentId(documentId, recursive = true)
            }
            ?: safManager.listImageFiles(folder.uri, recursive = true)
}
