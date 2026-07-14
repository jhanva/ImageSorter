package com.smartfolder.domain.usecase

import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.SuggestionSortMode
import com.smartfolder.domain.model.confidenceMargin
import javax.inject.Inject

class GetSuggestionsUseCase @Inject constructor() {
    operator fun invoke(
        suggestions: List<SuggestionItem>,
        threshold: Float,
        sortMode: SuggestionSortMode = SuggestionSortMode.BY_SCORE
    ): List<SuggestionItem> {
        val visible = suggestions.filter { it.suggestedDestinationId == 0L || it.score >= threshold }
        val comparator = when (sortMode) {
            SuggestionSortMode.BY_SCORE ->
                compareByDescending<SuggestionItem> { it.suggestedDestinationId != 0L }
                    .thenByDescending { it.score }
            SuggestionSortMode.BY_UNCERTAINTY ->
                compareByDescending<SuggestionItem> { it.suggestedDestinationId != 0L }
                    .thenBy { it.confidenceMargin }
        }
        return visible.sortedWith(comparator)
    }
}
