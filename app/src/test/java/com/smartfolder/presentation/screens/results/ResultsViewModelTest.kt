package com.smartfolder.presentation.screens.results

import android.net.Uri
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.AcceptSuggestionUseCase
import com.smartfolder.domain.usecase.BuildManualSuggestionsUseCase
import com.smartfolder.domain.usecase.GetSuggestionsUseCase
import com.smartfolder.domain.usecase.LoadSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.domain.usecase.RejectSuggestionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private lateinit var acceptSuggestionUseCase: AcceptSuggestionUseCase
    private lateinit var rejectSuggestionUseCase: RejectSuggestionUseCase
    private lateinit var folderRepository: FolderRepository
    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var buildManualSuggestionsUseCase: BuildManualSuggestionsUseCase
    private lateinit var loadSuggestionsUseCase: LoadSuggestionsUseCase
    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var settingsRepository: FakeSettingsRepository

    private val firstSuggestion = suggestion(
        id = 1L,
        name = "first.jpg",
        score = 0.25f,
        contentHash = "duplicate-hash"
    )
    private val secondSuggestion = suggestion(
        id = 2L,
        name = "second.jpg",
        score = 0.10f,
        contentHash = "duplicate-hash",
        sizeBytes = 3_000_000L,
        lastModified = 1_005_000L
    )
    private val thirdSuggestion = suggestion(
        id = 3L,
        name = "raiden-shogun-wallpaper-01.png",
        score = 0.90f,
        sizeBytes = 4_500_000L,
        lastModified = 2_000_000L
    )
    private val fourthSuggestion = suggestion(
        id = 4L,
        name = "raiden_shogun_wallpaper_02.png",
        score = 0.80f,
        sizeBytes = 4_000_000L,
        lastModified = 1_999_500L
    )
    private val allSuggestions = listOf(firstSuggestion, secondSuggestion, thirdSuggestion, fourthSuggestion)

    @Before
    fun setup() {
        getSuggestionsUseCase = mock(GetSuggestionsUseCase::class.java)
        moveImagesUseCase = mock(MoveImagesUseCase::class.java)
        acceptSuggestionUseCase = mock(AcceptSuggestionUseCase::class.java)
        rejectSuggestionUseCase = mock(RejectSuggestionUseCase::class.java)
        folderRepository = mock(FolderRepository::class.java)
        embeddingRepository = mock(EmbeddingRepository::class.java)
        buildManualSuggestionsUseCase = mock(BuildManualSuggestionsUseCase::class.java)
        loadSuggestionsUseCase = mock(LoadSuggestionsUseCase::class.java)
        suggestionRepository = mock(SuggestionRepository::class.java)
        settingsRepository = FakeSettingsRepository(
            threshold = 0.80f,
            manualMode = true
        )
    }

    @Test
    fun `manual mode keeps every loaded suggestion visible`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        val state = awaitState(viewModel) {
            it.manualMode &&
                it.allSuggestions.size == 4 &&
                it.filteredSuggestions.size == 4 &&
                !it.isComputingManualVisualGroups
        }
        assertTrue(state.manualMode)
        assertEquals(4, state.allSuggestions.size)
        assertEquals(4, state.filteredSuggestions.size)
        assertEquals(listOf(3L, 4L, 2L, 1L), state.filteredSuggestions.map { it.image.id })
    }

    @Test
    fun `manual mode supports toggle select all and clear`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.toggleSelection(1L)
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.uiState.value.selectedIds)

        viewModel.selectAllFiltered()
        val fullySelectedState = awaitState(viewModel) { it.selectedIds.size == 4 }
        assertEquals(setOf(1L, 2L, 3L, 4L), fullySelectedState.selectedIds)

        viewModel.clearSelection()
        val clearedState = awaitState(viewModel) { it.selectedIds.isEmpty() }
        assertTrue(clearedState.selectedIds.isEmpty())
    }

    @Test
    fun `manual mode returns uris for selected images`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.toggleSelection(2L)

        val uris = viewModel.getAcceptedImageUris()

        assertEquals(listOf(secondSuggestion.image.uri), uris)
    }

    @Test
    fun `manual mode can filter duplicate groups`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.setManualFilter(ManualReviewFilter.DUPLICATES)
        viewModel.setManualSort(ManualReviewSort.DUPLICATES)
        val state = awaitState(viewModel) {
            it.filteredSuggestions.map { suggestion -> suggestion.image.id }.toSet() == setOf(1L, 2L)
        }
        assertEquals(setOf(1L, 2L), state.filteredSuggestions.map { it.image.id }.toSet())
        assertEquals(1, state.manualVisibleDuplicateGroupCount)
    }

    @Test
    fun `manual mode can filter visual groups when embeddings are similar`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.setManualFilter(ManualReviewFilter.VISUAL_GROUPS)
        viewModel.setManualSort(ManualReviewSort.VISUAL_GROUPS)
        val state = awaitState(viewModel) {
            it.filteredSuggestions.map { suggestion -> suggestion.image.id }.toSet() == setOf(3L, 4L)
        }
        assertEquals(setOf(3L, 4L), state.filteredSuggestions.map { it.image.id }.toSet())
        assertEquals(1, state.manualVisibleVisualGroupCount)
    }

    @Test
    fun `manual mode can pick best image per visible duplicate group`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.setManualFilter(ManualReviewFilter.DUPLICATES)
        viewModel.setManualSort(ManualReviewSort.DUPLICATES)
        awaitState(viewModel) { it.filteredSuggestions.size == 2 }
        viewModel.selectBestInVisibleDuplicateGroups()
        val state = awaitState(viewModel) { it.selectedIds == setOf(2L) }
        assertEquals(setOf(2L), state.selectedIds)
    }

    @Test
    fun `manual mode can pick best image per visible visual group`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.setManualFilter(ManualReviewFilter.VISUAL_GROUPS)
        viewModel.setManualSort(ManualReviewSort.VISUAL_GROUPS)
        awaitState(viewModel) { it.filteredSuggestions.size == 2 }
        viewModel.selectBestInVisibleVisualGroups()
        val state = awaitState(viewModel) { it.selectedIds == setOf(3L) }
        assertEquals(setOf(3L), state.selectedIds)
    }

    @Test
    fun `manual mode trims section selection to current filter`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.toggleSelection(1L)
        advanceUntilIdle()
        viewModel.setManualQuery("raiden")
        val state = awaitState(viewModel) {
            it.filteredSuggestions.map { suggestion -> suggestion.image.id }.toSet() == setOf(3L, 4L)
        }
        assertEquals(setOf(3L, 4L), state.filteredSuggestions.map { it.image.id }.toSet())
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test
    fun `manual mode can move an explicit visual selection to reference folder`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val destinationUri = mock(Uri::class.java)
        val referenceFolder = Folder(
            id = 99L,
            uri = destinationUri,
            displayName = "Reference",
            role = FolderRole.REFERENCE
        )
        `when`(folderRepository.getByRole(FolderRole.REFERENCE)).thenReturn(listOf(referenceFolder))
        `when`(moveImagesUseCase.invoke(listOf(thirdSuggestion.image, fourthSuggestion.image), destinationUri)).thenReturn(
            MoveImagesUseCase.MoveReport(
                moved = 2,
                copiedOnly = 0,
                failed = 0,
                errors = emptyList(),
                movedImageIds = setOf(3L, 4L)
            )
        )

        awaitState(viewModel) { it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups }
        viewModel.moveImagesToReference(setOf(3L, 4L))
        val state = awaitState(viewModel) { it.allSuggestions.map { suggestion -> suggestion.image.id }.toSet() == setOf(1L, 2L) }
        assertEquals(setOf(1L, 2L), state.allSuggestions.map { it.image.id }.toSet())
        assertEquals("Moved to A: 2", state.moveResultMessage)
    }

    @Test
    fun `manual mode rebuilds suggestions when stored cache is empty`() = runTest(mainDispatcherRule.dispatcher) {
        val unsortedFolder = Folder(
            id = 55L,
            uri = mock(Uri::class.java),
            displayName = "Unsorted",
            role = FolderRole.UNSORTED
        )
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(emptyList())
        `when`(folderRepository.getByRole(FolderRole.UNSORTED)).thenReturn(listOf(unsortedFolder))
        `when`(buildManualSuggestionsUseCase.invoke(unsortedFolder)).thenReturn(allSuggestions)
        `when`(getSuggestionsUseCase.invoke(emptyList(), 0.80f)).thenReturn(emptyList())
        `when`(embeddingRepository.getByImageIds(listOf(1L, 2L, 3L, 4L))).thenReturn(
            listOf(
                embedding(1L, floatArrayOf(1f, 0f, 0f)),
                embedding(2L, floatArrayOf(0f, 1f, 0f)),
                embedding(3L, floatArrayOf(0f, 0f, 1f)),
                embedding(4L, floatArrayOf(0f, 0.1f, 0.995f))
            )
        )

        val viewModel = ResultsViewModel(
            getSuggestionsUseCase = getSuggestionsUseCase,
            moveImagesUseCase = moveImagesUseCase,
            acceptSuggestionUseCase = acceptSuggestionUseCase,
            rejectSuggestionUseCase = rejectSuggestionUseCase,
            folderRepository = folderRepository,
            embeddingRepository = embeddingRepository,
            settingsRepository = settingsRepository,
            buildManualSuggestionsUseCase = buildManualSuggestionsUseCase,
            loadSuggestionsUseCase = loadSuggestionsUseCase,
            suggestionRepository = suggestionRepository
        )

        val state = awaitState(viewModel) {
            it.filteredSuggestions.size == 4 && !it.isComputingManualVisualGroups
        }
        assertEquals(4, state.allSuggestions.size)
        assertEquals(4, state.filteredSuggestions.size)
    }

    private suspend fun awaitState(
        viewModel: ResultsViewModel,
        predicate: (ResultsUiState) -> Boolean
    ): ResultsUiState {
        repeat(100) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            if (predicate(state)) {
                Thread.sleep(50)
                mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
                return state
            }
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for state. Current state: ${viewModel.uiState.value}")
    }

    private suspend fun createViewModel(): ResultsViewModel {
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(allSuggestions)
        `when`(getSuggestionsUseCase.invoke(emptyList(), 0.80f)).thenReturn(emptyList())
        `when`(embeddingRepository.getByImageIds(listOf(1L, 2L, 3L, 4L))).thenReturn(
            listOf(
                embedding(1L, floatArrayOf(1f, 0f, 0f)),
                embedding(2L, floatArrayOf(0f, 1f, 0f)),
                embedding(3L, floatArrayOf(0f, 0f, 1f)),
                embedding(4L, floatArrayOf(0f, 0.1f, 0.995f))
            )
        )
        return ResultsViewModel(
            getSuggestionsUseCase = getSuggestionsUseCase,
            moveImagesUseCase = moveImagesUseCase,
            acceptSuggestionUseCase = acceptSuggestionUseCase,
            rejectSuggestionUseCase = rejectSuggestionUseCase,
            folderRepository = folderRepository,
            embeddingRepository = embeddingRepository,
            settingsRepository = settingsRepository,
            buildManualSuggestionsUseCase = buildManualSuggestionsUseCase,
            loadSuggestionsUseCase = loadSuggestionsUseCase,
            suggestionRepository = suggestionRepository
        )
    }

    private fun embedding(imageId: Long, vector: FloatArray): Embedding {
        return Embedding(
            imageId = imageId,
            vector = vector,
            modelName = ModelChoice.FAST.modelFileName
        )
    }

    private fun suggestion(
        id: Long,
        name: String,
        score: Float,
        contentHash: String = "hash-$id",
        sizeBytes: Long = 100L + id,
        lastModified: Long = 1000L + id
    ): SuggestionItem {
        return SuggestionItem(
            image = ImageInfo(
                id = id,
                folderId = 10L,
                uri = mock(Uri::class.java),
                displayName = name,
                contentHash = contentHash,
                sizeBytes = sizeBytes,
                lastModified = lastModified
            ),
            score = score,
            centroidScore = score,
            topKScore = score,
            topSimilarFromA = emptyList()
        )
    }

    private class FakeSettingsRepository(
        threshold: Float,
        manualMode: Boolean
    ) : SettingsRepository {
        override val threshold: Flow<Float> = MutableStateFlow(threshold)
        override val modelChoice: Flow<ModelChoice> = flowOf(ModelChoice.FAST)
        override val executionProfile: Flow<ExecutionProfile> = flowOf(ExecutionProfile.BALANCED)
        override val darkMode: Flow<Boolean> = flowOf(false)
        override val manualMode: Flow<Boolean> = MutableStateFlow(manualMode)

        override suspend fun setThreshold(value: Float) = Unit

        override suspend fun setModelChoice(choice: ModelChoice) = Unit

        override suspend fun setExecutionProfile(profile: ExecutionProfile) = Unit

        override suspend fun setDarkMode(enabled: Boolean) = Unit

        override suspend fun setManualMode(enabled: Boolean) = Unit
    }
}
