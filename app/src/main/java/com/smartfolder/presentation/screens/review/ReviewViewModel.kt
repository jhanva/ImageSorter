package com.smartfolder.presentation.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ReviewStatus
import com.smartfolder.domain.model.confidenceMargin
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.LoadSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val loadSuggestionsUseCase: LoadSuggestionsUseCase,
    private val suggestionRepository: SuggestionRepository,
    private val folderRepository: FolderRepository,
    private val moveImagesUseCase: MoveImagesUseCase
) : ViewModel() {

    private data class Decision(
        val imageId: Long,
        val status: ReviewStatus,
        val destinationId: Long?
    )

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val decisions = ArrayDeque<Decision>()

    init {
        viewModelScope.launch {
            try {
                val destinations = folderRepository.getByRole(FolderRole.DESTINATION).sortedBy { it.id }
                val pending = loadSuggestionsUseCase()
                    .filter { it.reviewStatus == ReviewStatus.PENDING }
                    .sortedBy { it.confidenceMargin }
                _uiState.value = ReviewUiState(
                    queue = pending,
                    destinationFolders = destinations,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Could not load pending suggestions."
                )
            }
        }
    }

    fun accept(destinationId: Long) {
        val item = _uiState.value.current ?: return
        viewModelScope.launch {
            suggestionRepository.setReviewStatus(item.image.id, ReviewStatus.ACCEPTED, destinationId)
            decisions.addLast(Decision(item.image.id, ReviewStatus.ACCEPTED, destinationId))
            val state = _uiState.value
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                acceptedCount = state.acceptedCount + 1,
                acceptedByDestination = state.acceptedByDestination +
                    (destinationId to (state.acceptedByDestination[destinationId] ?: 0) + 1)
            )
        }
    }

    fun skip() {
        val item = _uiState.value.current ?: return
        viewModelScope.launch {
            suggestionRepository.setReviewStatus(item.image.id, ReviewStatus.SKIPPED, null)
            decisions.addLast(Decision(item.image.id, ReviewStatus.SKIPPED, null))
            val state = _uiState.value
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                skippedCount = state.skippedCount + 1
            )
        }
    }

    fun undoLast() {
        val last = decisions.removeLastOrNull() ?: return
        viewModelScope.launch {
            suggestionRepository.setReviewStatus(last.imageId, ReviewStatus.PENDING, null)
            val state = _uiState.value
            _uiState.value = state.copy(
                currentIndex = (state.currentIndex - 1).coerceAtLeast(0),
                acceptedCount = if (last.status == ReviewStatus.ACCEPTED) {
                    (state.acceptedCount - 1).coerceAtLeast(0)
                } else {
                    state.acceptedCount
                },
                skippedCount = if (last.status == ReviewStatus.SKIPPED) {
                    (state.skippedCount - 1).coerceAtLeast(0)
                } else {
                    state.skippedCount
                },
                acceptedByDestination = if (last.status == ReviewStatus.ACCEPTED && last.destinationId != null) {
                    val previous = state.acceptedByDestination[last.destinationId] ?: 0
                    if (previous <= 1) {
                        state.acceptedByDestination - last.destinationId
                    } else {
                        state.acceptedByDestination + (last.destinationId to previous - 1)
                    }
                } else {
                    state.acceptedByDestination
                }
            )
        }
    }

    fun applyAcceptedMoves() {
        val state = _uiState.value
        if (state.isMoving) return

        val acceptedDecisions = decisions.filter { it.status == ReviewStatus.ACCEPTED }
        if (acceptedDecisions.isEmpty()) return

        val itemsById = state.queue.associateBy { it.image.id }
        val destinationsById = state.destinationFolders.associateBy { it.id }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMoving = true, error = null)
            try {
                var moved = 0
                var copiedOnly = 0
                var failed = 0
                val errors = mutableListOf<String>()

                acceptedDecisions
                    .groupBy { it.destinationId }
                    .forEach { (destinationId, groupDecisions) ->
                        val destination = destinationId?.let { destinationsById[it] }
                        if (destination == null) {
                            failed += groupDecisions.size
                            return@forEach
                        }
                        val images = groupDecisions.mapNotNull { itemsById[it.imageId]?.image }
                        if (images.isEmpty()) return@forEach

                        val report = moveImagesUseCase(images, destination.uri)
                        moved += report.moved
                        copiedOnly += report.copiedOnly
                        failed += report.failed
                        errors += report.errors
                    }

                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    moveSummary = ReviewMoveSummary(moved = moved, copiedOnly = copiedOnly, failed = failed),
                    error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    error = e.message ?: "Could not move the accepted images."
                )
            }
        }
    }

    fun getAcceptedImageUris(): List<android.net.Uri> {
        val itemsById = _uiState.value.queue.associateBy { it.image.id }
        return decisions
            .filter { it.status == ReviewStatus.ACCEPTED }
            .mapNotNull { itemsById[it.imageId]?.image?.uri }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
