package com.smartfolder.domain.usecase

import com.smartfolder.domain.model.Decision
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.DecisionRepository
import javax.inject.Inject

class RejectSuggestionUseCase @Inject constructor(
    private val decisionRepository: DecisionRepository
) {
    suspend operator fun invoke(suggestion: SuggestionItem) {
        val decision = Decision(
            imageId = suggestion.image.id,
            accepted = false,
            score = suggestion.score
        )
        decisionRepository.insert(decision)
    }
}
