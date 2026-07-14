package com.smartfolder.presentation.screens.review

import android.net.Uri
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.ReviewStatus
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.LoadSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.presentation.screens.results.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var loadSuggestionsUseCase: LoadSuggestionsUseCase
    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var folderRepository: FolderRepository
    private lateinit var moveImagesUseCase: MoveImagesUseCase

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

    @Before
    fun setup() {
        loadSuggestionsUseCase = mock(LoadSuggestionsUseCase::class.java)
        suggestionRepository = mock(SuggestionRepository::class.java)
        folderRepository = mock(FolderRepository::class.java)
        moveImagesUseCase = mock(MoveImagesUseCase::class.java)
    }

    private fun viewModel() = ReviewViewModel(
        loadSuggestionsUseCase = loadSuggestionsUseCase,
        suggestionRepository = suggestionRepository,
        folderRepository = folderRepository,
        moveImagesUseCase = moveImagesUseCase
    )

    private fun suggestion(
        id: Long,
        score: Float = 0.9f,
        secondBestScore: Float = 0.5f,
        destinationId: Long = destinationA.id,
        reviewStatus: ReviewStatus = ReviewStatus.PENDING,
        candidateIds: List<Long> = listOf(destinationA.id, destinationB.id),
        candidateScores: List<Float> = listOf(score, secondBestScore)
    ) = SuggestionItem(
        image = ImageInfo(id, 1L, mock(Uri::class.java), "img$id.jpg", "h$id", 10L, 1L),
        suggestedDestinationId = destinationId,
        score = score,
        secondBestScore = secondBestScore,
        centroidScore = score,
        topKScore = score,
        topSimilarImages = emptyList(),
        candidateIds = candidateIds,
        candidateScores = candidateScores,
        reviewStatus = reviewStatus
    )

    private suspend fun awaitState(
        viewModel: ReviewViewModel,
        predicate: (ReviewUiState) -> Boolean
    ): ReviewUiState {
        repeat(100) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            if (predicate(state)) return state
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for state. Current: ${viewModel.uiState.value}")
    }

    @Test
    fun `queue contains only pending suggestions ordered by uncertainty`() = runTest(mainDispatcherRule.dispatcher) {
        val clear = suggestion(id = 1L, score = 0.95f, secondBestScore = 0.4f)
        val ambiguous = suggestion(id = 2L, score = 0.9f, secondBestScore = 0.88f)
        val reviewed = suggestion(id = 3L, reviewStatus = ReviewStatus.ACCEPTED)
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(clear, ambiguous, reviewed))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val vm = viewModel()
        val state = awaitState(vm) { !it.isLoading }

        assertEquals(listOf(2L, 1L), state.queue.map { it.image.id })
        assertEquals(2L, state.current?.image?.id)
    }

    @Test
    fun `accept persists decision and advances to next`() = runTest(mainDispatcherRule.dispatcher) {
        val first = suggestion(id = 1L, score = 0.9f, secondBestScore = 0.88f)
        val second = suggestion(id = 2L, score = 0.9f, secondBestScore = 0.4f)
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(first, second))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.accept(destinationB.id)
        val state = awaitState(vm) { it.acceptedCount == 1 }

        verify(suggestionRepository).setReviewStatus(1L, ReviewStatus.ACCEPTED, destinationB.id)
        assertEquals(2L, state.current?.image?.id)
        assertEquals(1, state.acceptedCount)
    }

    @Test
    fun `skip persists decision and advances`() = runTest(mainDispatcherRule.dispatcher) {
        val first = suggestion(id = 1L, score = 0.9f, secondBestScore = 0.88f)
        val second = suggestion(id = 2L, score = 0.9f, secondBestScore = 0.4f)
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(first, second))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.skip()
        val state = awaitState(vm) { it.skippedCount == 1 }

        verify(suggestionRepository).setReviewStatus(1L, ReviewStatus.SKIPPED, null)
        assertEquals(2L, state.current?.image?.id)
    }

    @Test
    fun `completes when queue is exhausted`() = runTest(mainDispatcherRule.dispatcher) {
        val only = suggestion(id = 1L)
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(only))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.accept(destinationA.id)
        val state = awaitState(vm) { it.isComplete }

        assertTrue(state.isComplete)
        assertEquals(mapOf(destinationA.id to 1), state.acceptedByDestination)
    }

    @Test
    fun `undoLast restores previous item as pending`() = runTest(mainDispatcherRule.dispatcher) {
        val first = suggestion(id = 1L, score = 0.9f, secondBestScore = 0.88f)
        val second = suggestion(id = 2L, score = 0.9f, secondBestScore = 0.4f)
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(first, second))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }
        vm.accept(destinationA.id)
        awaitState(vm) { it.acceptedCount == 1 }

        vm.undoLast()
        val state = awaitState(vm) { it.acceptedCount == 0 }

        verify(suggestionRepository).setReviewStatus(1L, ReviewStatus.PENDING, null)
        assertEquals(1L, state.current?.image?.id)
        assertFalse(state.isComplete)
    }

    @Test
    fun `current candidates come from stored candidate destinations`() = runTest(mainDispatcherRule.dispatcher) {
        val item = suggestion(
            id = 1L,
            candidateIds = listOf(destinationB.id, destinationA.id),
            candidateScores = listOf(0.8f, 0.6f)
        )
        `when`(loadSuggestionsUseCase.invoke()).thenReturn(listOf(item))
        `when`(folderRepository.getByRole(FolderRole.DESTINATION)).thenReturn(listOf(destinationA, destinationB))

        val vm = viewModel()
        val state = awaitState(vm) { !it.isLoading }

        assertEquals(
            listOf(destinationB.id to 0.8f, destinationA.id to 0.6f),
            state.currentCandidates.map { it.folder.id to it.score }
        )
    }
}
