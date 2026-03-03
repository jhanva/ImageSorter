package com.smartfolder.domain.usecase

import com.smartfolder.domain.model.SimilarMatch
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SuggestionRepository
import javax.inject.Inject

class LoadSuggestionsUseCase @Inject constructor(
    private val suggestionRepository: SuggestionRepository,
    private val imageRepository: ImageRepository
) {
    suspend operator fun invoke(): List<SuggestionItem> {
        val stored = suggestionRepository.getAll()
        if (stored.isEmpty()) return emptyList()

        val allIds = buildSet {
            stored.forEach { suggestion ->
                add(suggestion.imageId)
                suggestion.topSimilarIds.forEach { add(it) }
            }
        }.toList()

        val imagesById = imageRepository.getByIds(allIds).associateBy { it.id }

        return stored.mapNotNull { suggestion ->
            val image = imagesById[suggestion.imageId] ?: return@mapNotNull null
            val matches = suggestion.topSimilarIds.zip(suggestion.topSimilarScores)
                .mapNotNull { (id, score) ->
                    imagesById[id]?.let { SimilarMatch(image = it, score = score) }
                }
            SuggestionItem(
                image = image,
                score = suggestion.score,
                centroidScore = suggestion.centroidScore,
                topKScore = suggestion.topKScore,
                topSimilarFromA = matches
            )
        }
    }
}
