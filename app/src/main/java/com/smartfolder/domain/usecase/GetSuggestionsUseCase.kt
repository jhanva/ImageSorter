package com.smartfolder.domain.usecase

import com.smartfolder.domain.model.SuggestionItem
import javax.inject.Inject

class GetSuggestionsUseCase @Inject constructor() {
    operator fun invoke(
        suggestions: List<SuggestionItem>,
        threshold: Float
    ): List<SuggestionItem> {
        return suggestions
            .filter { it.suggestedDestinationId == 0L || it.score >= threshold }
            .sortedWith(
                compareByDescending<SuggestionItem> { it.suggestedDestinationId != 0L }
                    .thenByDescending { it.score }
            )
    }
}
