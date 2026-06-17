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
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getSuggestionsUseCase: GetSuggestionsUseCase
    private lateinit var moveImagesUseCase: MoveImagesUseCase
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
        folderRepository = mock(FolderRepository::class.java)
        loadSuggestionsUseCase = mock(LoadSuggestionsUseCase::class.java)
        suggestionRepository = mock(SuggestionRepository::class.java)
        settingsRepository = FakeSettingsRepository()
    }

    @Test
    fun `groups loaded suggestions by suggested destination`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA, suggestionForB))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val viewModel = ResultsViewModel(
            getSuggestionsUseCase = getSuggestionsUseCase,
            moveImagesUseCase = moveImagesUseCase,
            folderRepository = folderRepository,
            settingsRepository = settingsRepository,
            loadSuggestionsUseCase = loadSuggestionsUseCase,
            suggestionRepository = suggestionRepository
        )

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
                movedImageIds = setOf(suggestionForA.image.id, suggestionForB.image.id)
            )
        )

        val viewModel = ResultsViewModel(
            getSuggestionsUseCase = getSuggestionsUseCase,
            moveImagesUseCase = moveImagesUseCase,
            folderRepository = folderRepository,
            settingsRepository = settingsRepository,
            loadSuggestionsUseCase = loadSuggestionsUseCase,
            suggestionRepository = suggestionRepository
        )

        awaitState(viewModel) { it.filteredSuggestions.size == 2 }
        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(2L)
        viewModel.setDestinationOverride(2L, destinationA.id)
        viewModel.moveSelected()

        val state = awaitState(viewModel) { it.filteredSuggestions.isEmpty() && !it.isMoving }
        assertTrue(state.selectedIds.isEmpty())
        assertEquals("Moved: 2", state.moveResultMessage)
    }

    @Test
    fun `changing threshold clears selections that are no longer visible`() = runTest(mainDispatcherRule.dispatcher) {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(suggestionForA, suggestionForB))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val viewModel = ResultsViewModel(
            getSuggestionsUseCase = getSuggestionsUseCase,
            moveImagesUseCase = moveImagesUseCase,
            folderRepository = folderRepository,
            settingsRepository = settingsRepository,
            loadSuggestionsUseCase = loadSuggestionsUseCase,
            suggestionRepository = suggestionRepository
        )

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

        val viewModel = ResultsViewModel(
            getSuggestionsUseCase = getSuggestionsUseCase,
            moveImagesUseCase = moveImagesUseCase,
            folderRepository = folderRepository,
            settingsRepository = settingsRepository,
            loadSuggestionsUseCase = loadSuggestionsUseCase,
            suggestionRepository = suggestionRepository
        )

        val state = awaitState(viewModel) { it.destinationSections.size == 1 }

        assertEquals("Needs manual routing", state.destinationSections.single().destination.displayName)
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
        destinationId: Long
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
            secondBestScore = score - 0.1f,
            centroidScore = score,
            topKScore = score,
            topSimilarImages = emptyList()
        )
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val threshold: Flow<Float> = MutableStateFlow(0.80f)
        override val modelChoice: Flow<ModelChoice> = flowOf(ModelChoice.FAST)
        override val executionProfile: Flow<ExecutionProfile> = flowOf(ExecutionProfile.BALANCED)
        override val darkMode: Flow<Boolean> = flowOf(false)

        override suspend fun setThreshold(value: Float) = Unit

        override suspend fun setModelChoice(choice: ModelChoice) = Unit

        override suspend fun setExecutionProfile(profile: ExecutionProfile) = Unit

        override suspend fun setDarkMode(enabled: Boolean) = Unit
    }
}
