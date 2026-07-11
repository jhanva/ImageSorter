package com.smartfolder.presentation.screens.results

import android.net.Uri
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.GetSuggestionsUseCase
import com.smartfolder.domain.usecase.LoadSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.domain.usecase.UndoMoveUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getSuggestionsUseCase: GetSuggestionsUseCase
    private lateinit var moveImagesUseCase: MoveImagesUseCase
    private lateinit var undoMoveUseCase: UndoMoveUseCase
    private lateinit var folderRepository: FolderRepository
    private lateinit var loadSuggestionsUseCase: LoadSuggestionsUseCase
    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var settingsRepository: FakeSettingsRepository

    private val destinationA = Folder(
        id = 100L,
        uri = mock(Uri::class.java),
        displayName = "Dest A",
        role = FolderRole.DESTINATION
    )
    private val destinationB = Folder(
        id = 101L,
        uri = mock(Uri::class.java),
        displayName = "Dest B",
        role = FolderRole.DESTINATION
    )

    private val suggestionForA = suggestion(
        id = 1L,
        name = "source-a.png",
        score = 0.92f,
        destinationId = destinationA.id
    )
    private val suggestionForB = suggestion(
        id = 2L,
        name = "source-b.png",
        score = 0.88f,
        destinationId = destinationB.id
    )

    @Before
    fun setup() {
        getSuggestionsUseCase = GetSuggestionsUseCase()
        moveImagesUseCase = mock(MoveImagesUseCase::class.java)
        undoMoveUseCase = mock(UndoMoveUseCase::class.java)
        folderRepository = mock(FolderRepository::class.java)
        loadSuggestionsUseCase = mock(LoadSuggestionsUseCase::class.java)
        suggestionRepository = mock(SuggestionRepository::class.java)
        settingsRepository = FakeSettingsRepository()
    }

    private fun viewModel(): ResultsViewModel = ResultsViewModel(
        getSuggestionsUseCase = getSuggestionsUseCase,
        moveImagesUseCase = moveImagesUseCase,
        undoMoveUseCase = undoMoveUseCase,
        folderRepository = folderRepository,
        settingsRepository = settingsRepository,
        loadSuggestionsUseCase = loadSuggestionsUseCase,
        suggestionRepository = suggestionRepository
    )

    @Test
    fun `groups loaded suggestions by suggested destination`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA, suggestionForB))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val viewModel = viewModel()

        val state = awaitState(viewModel) {
            it.destinationSections.size == 2 && it.filteredSuggestions.size == 2
        }

        assertEquals(listOf(destinationA.id, destinationB.id), state.destinationSections.map { it.destination.id })
        assertEquals(listOf(1L), state.destinationSections.first().suggestions.map { it.image.id })
        assertEquals(listOf(2L), state.destinationSections.last().suggestions.map { it.image.id })
    }

    @Test
    fun `move selected groups images by assigned destination`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA, suggestionForB))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))
        `when`(
            moveImagesUseCase.invoke(
                listOf(suggestionForA.image, suggestionForB.image),
                destinationA.uri
            )
        ).thenReturn(
            MoveImagesUseCase.MoveReport(
                moved = 2,
                copiedOnly = 0,
                failed = 0,
                errors = emptyList(),
                movedImageIds = setOf(suggestionForA.image.id, suggestionForB.image.id),
                movedEntries = listOf(
                    MoveImagesUseCase.MovedEntry(suggestionForA.image, mock(Uri::class.java)),
                    MoveImagesUseCase.MovedEntry(suggestionForB.image, mock(Uri::class.java))
                )
            )
        )

        val viewModel = viewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 2 }
        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(2L)
        viewModel.setDestinationOverride(2L, destinationA.id)
        viewModel.moveSelected()

        val state = awaitState(viewModel) { it.filteredSuggestions.isEmpty() && !it.isMoving }
        assertTrue(state.selectedIds.isEmpty())
        assertEquals(2, state.moveSummary?.moved)
        assertEquals(0, state.moveSummary?.failed)
        assertTrue(state.canUndo)
    }

    @Test
    fun `undo last move restores review state`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA))
        val movedUri = mock(Uri::class.java)
        `when`(
            moveImagesUseCase.invoke(listOf(suggestionForA.image), destinationA.uri)
        ).thenReturn(
            MoveImagesUseCase.MoveReport(
                moved = 1,
                copiedOnly = 0,
                failed = 0,
                errors = emptyList(),
                movedImageIds = setOf(suggestionForA.image.id),
                movedEntries = listOf(MoveImagesUseCase.MovedEntry(suggestionForA.image, movedUri))
            )
        )
        `when`(undoMoveUseCase.invoke(anyBatch()))
            .thenReturn(UndoMoveUseCase.UndoReport(restored = 1, failed = 0, errors = emptyList()))

        val viewModel = viewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 1 }
        viewModel.toggleSelection(1L)
        viewModel.moveSelected()
        awaitState(viewModel) { it.canUndo }

        viewModel.undoLastMove()
        val state = awaitState(viewModel) { !it.canUndo && !it.isMoving }

        verify(undoMoveUseCase).invoke(anyBatch())
        assertFalse(state.canUndo)
    }

    @Suppress("UNCHECKED_CAST")
    private fun anyBatch(): UndoMoveUseCase.UndoBatch {
        org.mockito.Mockito.any(UndoMoveUseCase.UndoBatch::class.java)
        return UndoMoveUseCase.UndoBatch(emptyList(), emptyList())
    }

    @Test
    fun `selectHighConfidence selects only assigned suggestions with clear margin`() = runTest(mainDispatcherRule.dispatcher) {
        val confident = suggestion(
            id = 1L, name = "confident.png", score = 0.92f, destinationId = destinationA.id,
            secondBestScore = 0.60f
        )
        val ambiguous = suggestion(
            id = 2L, name = "ambiguous.png", score = 0.95f, destinationId = destinationA.id,
            secondBestScore = 0.93f
        )
        val unassigned = suggestion(
            id = 3L, name = "unassigned.png", score = 0.90f, destinationId = 0L,
            secondBestScore = 0.50f
        )
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(confident, ambiguous, unassigned))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA))

        val viewModel = viewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 3 }
        viewModel.selectHighConfidence()

        val state = awaitState(viewModel) { it.selectedIds.isNotEmpty() }
        assertEquals(setOf(1L), state.selectedIds)
    }

    @Test
    fun `toggleSection collapses and expands a destination group`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA))

        val viewModel = viewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 1 }
        assertTrue(viewModel.uiState.value.collapsedSectionIds.isEmpty())

        viewModel.toggleSection(destinationA.id)
        assertTrue(destinationA.id in viewModel.uiState.value.collapsedSectionIds)

        viewModel.toggleSection(destinationA.id)
        assertFalse(destinationA.id in viewModel.uiState.value.collapsedSectionIds)
    }

    @Test
    fun `selectAllInSection selects every suggestion of that destination only`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA, suggestionForB))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val viewModel = viewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 2 }
        viewModel.selectAllInSection(destinationA.id)

        val state = awaitState(viewModel) { it.selectedIds.isNotEmpty() }
        assertEquals(setOf(1L), state.selectedIds)
    }

    @Test
    fun `changing threshold clears selections that are no longer visible`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA, suggestionForB))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val viewModel = viewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 2 }
        viewModel.toggleSelection(2L)
        viewModel.setThreshold(0.90f)

        val state = awaitState(viewModel) {
            it.filteredSuggestions.map { suggestion -> suggestion.image.id } == listOf(1L)
        }
        assertTrue(state.selectedIds.isEmpty())
        assertFalse(2L in state.selectedIds)
    }

    @Test
    fun `groups unassigned suggestions into manual routing section`() = runTest(mainDispatcherRule.dispatcher) {
        val unassigned = suggestion(
            id = 3L,
            name = "needs-review.png",
            score = 0.42f,
            destinationId = 0L
        )
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(unassigned))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val viewModel = viewModel()

        val state = awaitState(viewModel) { it.destinationSections.size == 1 }

        assertEquals(0L, state.destinationSections.single().destination.id)
        assertEquals(listOf(3L), state.destinationSections.single().suggestions.map { it.image.id })
    }

    private suspend fun awaitState(
        viewModel: ResultsViewModel,
        predicate: (ResultsUiState) -> Boolean
    ): ResultsUiState {
        repeat(100) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            if (predicate(state)) {
                return state
            }
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for state. Current state: ${viewModel.uiState.value}")
    }

    private fun suggestion(
        id: Long,
        name: String,
        score: Float,
        destinationId: Long,
        secondBestScore: Float = score - 0.1f
    ): SuggestionItem {
        return SuggestionItem(
            image = ImageInfo(
                id = id,
                folderId = 10L,
                uri = mock(Uri::class.java),
                displayName = name,
                contentHash = "hash-$id",
                sizeBytes = 100L + id,
                lastModified = 1000L + id
            ),
            suggestedDestinationId = destinationId,
            score = score,
            secondBestScore = secondBestScore,
            centroidScore = score,
            topKScore = score,
            topSimilarImages = emptyList()
        )
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val threshold: Flow<Float> = MutableStateFlow(0.80f)
        override val modelChoice: Flow<ModelChoice> = flowOf(ModelChoice.DEFAULT)
        override val executionProfile: Flow<ExecutionProfile> = flowOf(ExecutionProfile.BALANCED)
        override val darkMode: Flow<Boolean> = flowOf(false)
        override val dynamicColor: Flow<Boolean> = flowOf(false)

        override suspend fun setThreshold(value: Float) = Unit

        override suspend fun setModelChoice(choice: ModelChoice) = Unit

        override suspend fun setExecutionProfile(profile: ExecutionProfile) = Unit

        override suspend fun setDarkMode(enabled: Boolean) = Unit

        override suspend fun setDynamicColor(enabled: Boolean) = Unit
    }
}
