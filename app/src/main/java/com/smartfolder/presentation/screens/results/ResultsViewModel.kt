package com.smartfolder.presentation.screens.results

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.confidenceMargin
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.GetSuggestionsUseCase
import com.smartfolder.domain.usecase.LoadSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.domain.usecase.UndoMoveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val moveImagesUseCase: MoveImagesUseCase,
    private val undoMoveUseCase: UndoMoveUseCase,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val loadSuggestionsUseCase: LoadSuggestionsUseCase,
    private val suggestionRepository: SuggestionRepository
) : ViewModel() {

    companion object {
        // A suggestion is high confidence when the score clears the threshold
        // comfortably and no other destination is close behind.
        const val HIGH_CONFIDENCE_MIN_SCORE = 0.75f
        const val HIGH_CONFIDENCE_MIN_MARGIN = 0.10f
    }

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private var lastMoveBatch: UndoMoveUseCase.UndoBatch? = null

    init {
        viewModelScope.launch {
            reload()
        }
    }

    fun setThreshold(threshold: Float) {
        viewModelScope.launch {
            settingsRepository.setThreshold(threshold)
            _uiState.value = refreshDerivedState(
                _uiState.value.copy(
                    threshold = threshold,
                    moveSummary = null
                )
            )
        }
    }

    fun toggleSelection(imageId: Long) {
        val selectedIds = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (imageId in selectedIds) {
                selectedIds - imageId
            } else {
                selectedIds + imageId
            }
        )
    }

    fun toggleSection(destinationId: Long) {
        val collapsed = _uiState.value.collapsedSectionIds
        _uiState.value = _uiState.value.copy(
            collapsedSectionIds = if (destinationId in collapsed) {
                collapsed - destinationId
            } else {
                collapsed + destinationId
            }
        )
    }

    fun selectAllInSection(destinationId: Long) {
        val state = _uiState.value
        val sectionIds = state.filteredSuggestions
            .filter { assignedDestinationId(it, state.destinationOverrides) == destinationId }
            .map { it.image.id }
        _uiState.value = state.copy(selectedIds = state.selectedIds + sectionIds)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
    }

    fun selectHighConfidence() {
        val state = _uiState.value
        val confidentIds = state.filteredSuggestions
            .filter { suggestion ->
                assignedDestinationId(suggestion, state.destinationOverrides) != 0L &&
                    suggestion.score >= HIGH_CONFIDENCE_MIN_SCORE &&
                    suggestion.confidenceMargin >= HIGH_CONFIDENCE_MIN_MARGIN
            }
            .map { it.image.id }
        _uiState.value = state.copy(selectedIds = state.selectedIds + confidentIds)
    }

    fun setDestinationOverride(imageId: Long, destinationId: Long) {
        val destinationExists = _uiState.value.destinationFolders.any { it.id == destinationId }
        if (!destinationExists) return

        _uiState.value = refreshDerivedState(
            _uiState.value.copy(
                destinationOverrides = _uiState.value.destinationOverrides + (imageId to destinationId)
            )
        )
    }

    fun moveSelected() {
        val stateSnapshot = _uiState.value
        if (stateSnapshot.selectedIds.isEmpty()) return

        val suggestionsById = stateSnapshot.allSuggestions.associateBy { it.image.id }
        val selectedSuggestions = stateSnapshot.selectedIds.mapNotNull { suggestionsById[it] }
        if (selectedSuggestions.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isMoving = true,
                error = null,
                moveSummary = null
            )

            try {
                val destinationsById = _uiState.value.destinationFolders.associateBy { it.id }
                val groupedSuggestions = selectedSuggestions.groupBy { suggestion ->
                    assignedDestinationId(suggestion, stateSnapshot.destinationOverrides)
                }

                var moved = 0
                var copiedOnly = 0
                var failed = 0
                val movedIds = mutableSetOf<Long>()
                val movedEntries = mutableListOf<MoveImagesUseCase.MovedEntry>()
                val errors = mutableListOf<String>()

                groupedSuggestions.forEach { (destinationId, suggestions) ->
                    val destination = destinationsById[destinationId]
                    if (destination == null) {
                        failed += suggestions.size
                        errors += "Missing destination folder for ${suggestions.size} image(s)."
                        return@forEach
                    }

                    val report = moveImagesUseCase(
                        suggestions.map { it.image },
                        destination.uri
                    )
                    moved += report.moved
                    copiedOnly += report.copiedOnly
                    failed += report.failed
                    movedIds += report.movedImageIds
                    movedEntries += report.movedEntries
                    errors += report.errors
                }

                lastMoveBatch = movedEntries.takeIf { it.isNotEmpty() }?.let { entries ->
                    UndoMoveUseCase.UndoBatch(
                        entries = entries,
                        suggestions = selectedSuggestions
                            .filter { it.image.id in movedIds }
                            .map { toStoredSuggestion(it, stateSnapshot.destinationOverrides) }
                    )
                }

                val remainingSuggestions = _uiState.value.allSuggestions.filterNot { it.image.id in movedIds }
                suggestionRepository.replaceAll(
                    remainingSuggestions.map { suggestion ->
                        toStoredSuggestion(
                            suggestion = suggestion,
                            destinationOverrides = stateSnapshot.destinationOverrides
                        )
                    }
                )

                _uiState.value = refreshDerivedState(
                    _uiState.value.copy(
                        allSuggestions = remainingSuggestions,
                        selectedIds = _uiState.value.selectedIds - movedIds,
                        destinationOverrides = _uiState.value.destinationOverrides.filterKeys { it !in movedIds },
                        isMoving = false,
                        moveSummary = MoveSummary(moved = moved, copiedOnly = copiedOnly, failed = failed),
                        canUndo = lastMoveBatch != null,
                        error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    error = e.message ?: "Could not move selected images."
                )
            }
        }
    }

    fun undoLastMove() {
        val batch = lastMoveBatch ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMoving = true, error = null)
            try {
                val report = undoMoveUseCase(batch)
                lastMoveBatch = null
                reload()
                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    canUndo = false,
                    moveSummary = MoveSummary(
                        moved = 0,
                        copiedOnly = 0,
                        failed = report.failed,
                        restored = report.restored
                    ),
                    error = report.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    error = e.message ?: "Could not undo the last move."
                )
            }
        }
    }

    fun getImageUris(imageIds: Set<Long>): List<Uri> {
        if (imageIds.isEmpty()) return emptyList()
        return _uiState.value.allSuggestions
            .asSequence()
            .filter { it.image.id in imageIds }
            .map { it.image.uri }
            .toList()
    }

    fun getSelectedImageUris(): List<Uri> = getImageUris(_uiState.value.selectedIds)

    fun getUndoImageUris(): List<Uri> = lastMoveBatch?.entries?.map { it.newUri } ?: emptyList()

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(moveSummary = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    private suspend fun reload() {
        val threshold = settingsRepository.threshold.first()
        val destinationFolders = folderRepository.getByRole(FolderRole.DESTINATION).sortedBy { it.id }
        val suggestions = loadSuggestionsUseCase()

        _uiState.value = refreshDerivedState(
            ResultsUiState(
                allSuggestions = suggestions,
                destinationFolders = destinationFolders,
                threshold = threshold,
                canUndo = lastMoveBatch != null
            )
        )
    }

    private fun refreshDerivedState(baseState: ResultsUiState): ResultsUiState {
        val filteredSuggestions = getSuggestionsUseCase(
            suggestions = baseState.allSuggestions,
            threshold = baseState.threshold
        )
        val visibleIds = filteredSuggestions.mapTo(mutableSetOf()) { it.image.id }
        val destinationSections = buildDestinationSections(
            suggestions = filteredSuggestions,
            destinationFolders = baseState.destinationFolders,
            destinationOverrides = baseState.destinationOverrides
        )

        return baseState.copy(
            filteredSuggestions = filteredSuggestions,
            destinationSections = destinationSections,
            selectedIds = baseState.selectedIds.filterTo(linkedSetOf()) { it in visibleIds }
        )
    }

    private fun buildDestinationSections(
        suggestions: List<SuggestionItem>,
        destinationFolders: List<Folder>,
        destinationOverrides: Map<Long, Long>
    ): List<DestinationSuggestionSection> {
        val groupedSuggestions = suggestions.groupBy { suggestion ->
            assignedDestinationId(suggestion, destinationOverrides)
        }
        val knownSections = destinationFolders.mapNotNull { destination ->
            val destinationSuggestions = groupedSuggestions[destination.id].orEmpty()
            if (destinationSuggestions.isEmpty()) {
                null
            } else {
                DestinationSuggestionSection(
                    destination = destination,
                    suggestions = destinationSuggestions.sortedByDescending { it.score }
                )
            }
        }
        val knownDestinationIds = destinationFolders.mapTo(mutableSetOf()) { it.id }
        val fallbackUri = destinationFolders.firstOrNull()?.uri
        val manualRoutingSection = groupedSuggestions[0L]
            ?.takeIf { it.isNotEmpty() && fallbackUri != null }
            ?.let { destinationSuggestions ->
                DestinationSuggestionSection(
                    destination = Folder(
                        id = 0L,
                        uri = fallbackUri ?: return@let null,
                        displayName = "",
                        role = FolderRole.DESTINATION
                    ),
                    suggestions = destinationSuggestions.sortedByDescending { it.score }
                )
            }
        val missingSections = groupedSuggestions
            .filterKeys { it !in knownDestinationIds && it != 0L }
            .toSortedMap()
            .map { (destinationId, destinationSuggestions) ->
                DestinationSuggestionSection(
                    destination = Folder(
                        id = destinationId,
                        uri = fallbackUri ?: return@map null,
                        displayName = "Missing destination $destinationId",
                        role = FolderRole.DESTINATION
                    ),
                    suggestions = destinationSuggestions.sortedByDescending { it.score }
                )
            }.filterNotNull()

        return buildList {
            addAll(knownSections)
            manualRoutingSection?.let(::add)
            addAll(missingSections)
        }
    }

    private fun assignedDestinationId(
        suggestion: SuggestionItem,
        destinationOverrides: Map<Long, Long>
    ): Long {
        return destinationOverrides[suggestion.image.id] ?: suggestion.suggestedDestinationId
    }

    private fun toStoredSuggestion(
        suggestion: SuggestionItem,
        destinationOverrides: Map<Long, Long>
    ): StoredSuggestion {
        return StoredSuggestion(
            imageId = suggestion.image.id,
            destinationFolderId = assignedDestinationId(suggestion, destinationOverrides),
            score = suggestion.score,
            secondBestScore = suggestion.secondBestScore,
            centroidScore = suggestion.centroidScore,
            topKScore = suggestion.topKScore,
            topSimilarIds = suggestion.topSimilarImages.map { it.image.id },
            topSimilarScores = suggestion.topSimilarImages.map { it.score },
            candidateIds = suggestion.candidateIds,
            candidateScores = suggestion.candidateScores,
            createdAt = System.currentTimeMillis()
        )
    }
}
