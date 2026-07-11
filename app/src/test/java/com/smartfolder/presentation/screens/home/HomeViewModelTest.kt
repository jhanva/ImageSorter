package com.smartfolder.presentation.screens.home

import android.net.Uri
import android.net.TestUri
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.IndexFolderUseCase
import com.smartfolder.domain.usecase.SelectFolderUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = com.smartfolder.presentation.screens.results.MainDispatcherRule()

    @Test
    fun `keeps stored sources and destinations on startup`() = runTest(mainDispatcherRule.dispatcher) {
        val destinationFolder = folder(1L, FolderRole.DESTINATION, "Dest A", indexedCount = 3, imageCount = 3)
        val sourceFolderOne = folder(2L, FolderRole.SOURCE, "Source 1", indexedCount = 4, imageCount = 4)
        val sourceFolderTwo = folder(3L, FolderRole.SOURCE, "Source 2", indexedCount = 2, imageCount = 2)
        val folderRepository = FakeFolderRepository(listOf(destinationFolder, sourceFolderOne, sourceFolderTwo))

        val viewModel = viewModel(folderRepository = folderRepository)

        val state = awaitState(viewModel) {
            it.destinationFolders.size == 1 && it.sourceFolders.size == 2 && it.canAnalyze
        }

        assertEquals(listOf(destinationFolder), state.destinationFolders)
        assertEquals(listOf(sourceFolderOne, sourceFolderTwo), state.sourceFolders)
    }

    @Test
    fun `home state clears when folders are removed from repository`() = runTest(mainDispatcherRule.dispatcher) {
        val destinationFolder = folder(1L, FolderRole.DESTINATION, "Dest A", indexedCount = 1, imageCount = 1)
        val sourceFolder = folder(2L, FolderRole.SOURCE, "Source 1", indexedCount = 1, imageCount = 1)
        val folderRepository = FakeFolderRepository(listOf(destinationFolder, sourceFolder))

        val viewModel = viewModel(folderRepository = folderRepository)

        awaitState(viewModel) { it.canAnalyze }
        folderRepository.emit(emptyList())

        val state = awaitState(viewModel) {
            it.destinationFolders.isEmpty() && it.sourceFolders.isEmpty() && !it.canAnalyze
        }

        assertTrue(state.destinationFolders.isEmpty())
        assertTrue(state.sourceFolders.isEmpty())
        assertFalse(state.canAnalyze)
    }

    @Test
    fun `can analyze with unindexed folders because analysis indexes them first`() = runTest(mainDispatcherRule.dispatcher) {
        val destinationFolder = folder(1L, FolderRole.DESTINATION, "Dest A", indexedCount = 0, imageCount = 0)
        val pendingSource = folder(3L, FolderRole.SOURCE, "Source 2", indexedCount = 0, imageCount = 2)
        val folderRepository = FakeFolderRepository(listOf(destinationFolder, pendingSource))

        val viewModel = viewModel(folderRepository = folderRepository)

        val state = awaitState(viewModel) { it.sourceFolders.size == 1 }
        assertTrue(state.canAnalyze)
    }

    @Test
    fun `cannot analyze without at least one destination and one source`() = runTest(mainDispatcherRule.dispatcher) {
        val destinationFolder = folder(1L, FolderRole.DESTINATION, "Dest A", indexedCount = 3, imageCount = 3)
        val folderRepository = FakeFolderRepository(listOf(destinationFolder))

        val viewModel = viewModel(folderRepository = folderRepository)

        val state = awaitState(viewModel) { it.destinationFolders.size == 1 }
        assertFalse(state.canAnalyze)
    }

    @Test
    fun `exposes pending review count when stored suggestions exist`() = runTest(mainDispatcherRule.dispatcher) {
        val folderRepository = FakeFolderRepository(emptyList())
        val suggestionRepository = FakeSuggestionRepository(
            listOf(storedSuggestion(1L), storedSuggestion(2L))
        )

        val viewModel = viewModel(
            folderRepository = folderRepository,
            suggestionRepository = suggestionRepository
        )

        val state = awaitState(viewModel) { it.pendingReviewCount == 2 }
        assertEquals(2, state.pendingReviewCount)
    }

    private fun viewModel(
        folderRepository: FolderRepository,
        suggestionRepository: SuggestionRepository = FakeSuggestionRepository(emptyList())
    ): HomeViewModel {
        val selectFolderUseCase = mock(SelectFolderUseCase::class.java)
        val indexFolderUseCase = mock(IndexFolderUseCase::class.java)
        val mediaStoreFolderProvider = mock(MediaStoreFolderProvider::class.java)
        `when`(mediaStoreFolderProvider.getImageFolders()).thenReturn(emptyList())
        return HomeViewModel(
            selectFolderUseCase = selectFolderUseCase,
            indexFolderUseCase = indexFolderUseCase,
            folderRepository = folderRepository,
            settingsRepository = FakeSettingsRepository(),
            suggestionRepository = suggestionRepository,
            mediaStoreFolderProvider = mediaStoreFolderProvider
        )
    }

    private suspend fun awaitState(
        viewModel: HomeViewModel,
        predicate: (HomeUiState) -> Boolean
    ): HomeUiState {
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

    private fun folder(
        id: Long,
        role: FolderRole,
        displayName: String,
        indexedCount: Int = 0,
        imageCount: Int = 0
    ): Folder {
        return Folder(
            id = id,
            uri = uri("content://test/$id"),
            displayName = displayName,
            role = role,
            indexedCount = indexedCount,
            imageCount = imageCount
        )
    }

    private fun uri(value: String): Uri = TestUri(value)

    private fun storedSuggestion(imageId: Long): StoredSuggestion = StoredSuggestion(
        imageId = imageId,
        destinationFolderId = 100L,
        score = 0.9f,
        secondBestScore = 0.5f,
        centroidScore = 0.8f,
        topKScore = 0.9f,
        topSimilarIds = emptyList(),
        topSimilarScores = emptyList(),
        candidateIds = emptyList(),
        candidateScores = emptyList(),
        createdAt = 1L
    )

    private class FakeSuggestionRepository(
        private val suggestions: List<StoredSuggestion>
    ) : SuggestionRepository {
        override suspend fun getAll(): List<StoredSuggestion> = suggestions
        override suspend fun insertAll(suggestions: List<StoredSuggestion>) = Unit
        override suspend fun replaceAll(suggestions: List<StoredSuggestion>) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakeFolderRepository(initialFolders: List<Folder>) : FolderRepository {
        private val foldersFlow = MutableStateFlow(initialFolders)
        val deletedFolders = mutableListOf<Folder>()

        override fun observeAll(): Flow<List<Folder>> = foldersFlow

        override suspend fun getByRole(role: FolderRole): List<Folder> =
            foldersFlow.value.filter { it.role == role }

        override suspend fun getById(id: Long): Folder? =
            foldersFlow.value.firstOrNull { it.id == id }

        override suspend fun getByUri(uri: String): Folder? =
            foldersFlow.value.firstOrNull { it.uri.toString() == uri }

        override suspend fun insert(folder: Folder): Long {
            val nextId = (foldersFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
            foldersFlow.value = foldersFlow.value + folder.copy(id = nextId)
            return nextId
        }

        override suspend fun update(folder: Folder) {
            foldersFlow.value = foldersFlow.value.map { current ->
                if (current.id == folder.id) folder else current
            }
        }

        override suspend fun delete(folder: Folder) {
            deletedFolders += folder
            foldersFlow.value = foldersFlow.value.filterNot { it.id == folder.id }
        }

        override suspend fun deleteAll() {
            deletedFolders += foldersFlow.value
            foldersFlow.value = emptyList()
        }

        fun emit(folders: List<Folder>) {
            foldersFlow.value = folders
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val threshold: Flow<Float> = flowOf(0.55f)
        private val modelChoiceFlow = MutableStateFlow(ModelChoice.DEFAULT)
        override val modelChoice: Flow<ModelChoice> = modelChoiceFlow
        override val executionProfile: Flow<ExecutionProfile> = flowOf(ExecutionProfile.BALANCED)
        override val darkMode: Flow<Boolean> = flowOf(false)
        override val dynamicColor: Flow<Boolean> = flowOf(false)

        override suspend fun setThreshold(value: Float) = Unit

        override suspend fun setModelChoice(choice: ModelChoice) {
            modelChoiceFlow.value = choice
        }

        override suspend fun setExecutionProfile(profile: ExecutionProfile) = Unit

        override suspend fun setDarkMode(enabled: Boolean) = Unit

        override suspend fun setDynamicColor(enabled: Boolean) = Unit
    }
}
