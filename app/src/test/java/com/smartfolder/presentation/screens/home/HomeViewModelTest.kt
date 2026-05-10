package com.smartfolder.presentation.screens.home

import android.net.Uri
import android.net.TestUri
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageFolderOption
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.IndexFolderUseCase
import com.smartfolder.domain.usecase.SelectFolderUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = com.smartfolder.presentation.screens.results.MainDispatcherRule()

    @Test
    fun `keeps stored folders on startup under media store selection design`() = runTest(mainDispatcherRule.dispatcher) {
        val referenceFolder = folder(1L, FolderRole.REFERENCE, "Reference")
        val unsortedFolder = folder(2L, FolderRole.UNSORTED, "Unsorted")
        val folderRepository = FakeFolderRepository(listOf(referenceFolder, unsortedFolder))
        val embeddingRepository = FakeEmbeddingRepository(
            counts = mapOf(
                referenceFolder.id to 2,
                unsortedFolder.id to 3
            )
        )
        val settingsRepository = FakeSettingsRepository(manualMode = false)
        val selectFolderUseCase = mock(SelectFolderUseCase::class.java)
        val indexFolderUseCase = mock(IndexFolderUseCase::class.java)
        val mediaStoreFolderProvider = mock(MediaStoreFolderProvider::class.java)
        val safManager = mock(SafManager::class.java)

        `when`(mediaStoreFolderProvider.getImageFolders()).thenReturn(emptyList())
        val viewModel = HomeViewModel(
            selectFolderUseCase = selectFolderUseCase,
            indexFolderUseCase = indexFolderUseCase,
            folderRepository = folderRepository,
            settingsRepository = settingsRepository,
            embeddingRepository = embeddingRepository,
            mediaStoreFolderProvider = mediaStoreFolderProvider,
            safManager = safManager
        )

        val state = awaitState(viewModel) {
            it.referenceFolder != null && it.unsortedFolder != null && it.canAnalyze
        }

        assertEquals(referenceFolder, state.referenceFolder)
        assertEquals(unsortedFolder, state.unsortedFolder)
        assertEquals(0, folderRepository.deletedFolders.size)
        verifyNoInteractions(safManager)
    }

    @Test
    fun `home state clears when folders are removed from repository`() = runTest(mainDispatcherRule.dispatcher) {
        val referenceFolder = folder(1L, FolderRole.REFERENCE, "Reference")
        val unsortedFolder = folder(2L, FolderRole.UNSORTED, "Unsorted")
        val folderRepository = FakeFolderRepository(listOf(referenceFolder, unsortedFolder))
        val embeddingRepository = FakeEmbeddingRepository(
            counts = mapOf(
                referenceFolder.id to 1,
                unsortedFolder.id to 1
            )
        )
        val settingsRepository = FakeSettingsRepository(manualMode = false)
        val selectFolderUseCase = mock(SelectFolderUseCase::class.java)
        val indexFolderUseCase = mock(IndexFolderUseCase::class.java)
        val mediaStoreFolderProvider = mock(MediaStoreFolderProvider::class.java)
        val safManager = mock(SafManager::class.java)

        `when`(mediaStoreFolderProvider.getImageFolders()).thenReturn(emptyList())

        val viewModel = HomeViewModel(
            selectFolderUseCase = selectFolderUseCase,
            indexFolderUseCase = indexFolderUseCase,
            folderRepository = folderRepository,
            settingsRepository = settingsRepository,
            embeddingRepository = embeddingRepository,
            mediaStoreFolderProvider = mediaStoreFolderProvider,
            safManager = safManager
        )

        awaitState(viewModel) { it.canAnalyze }
        folderRepository.emit(emptyList())
        embeddingRepository.counts = emptyMap()

        val state = awaitState(viewModel) {
            it.referenceFolder == null && it.unsortedFolder == null && !it.canAnalyze
        }

        assertNull(state.referenceFolder)
        assertNull(state.unsortedFolder)
        assertFalse(state.canAnalyze)
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

    private fun folder(id: Long, role: FolderRole, displayName: String): Folder {
        return Folder(
            id = id,
            uri = uri("content://test/$id"),
            displayName = displayName,
            role = role
        )
    }

    private fun uri(value: String): Uri = TestUri(value)

    private class FakeFolderRepository(initialFolders: List<Folder>) : FolderRepository {
        private val foldersFlow = MutableStateFlow(initialFolders)
        val deletedFolders = mutableListOf<Folder>()

        override fun observeAll(): Flow<List<Folder>> = foldersFlow

        override suspend fun getById(id: Long): Folder? = foldersFlow.value.firstOrNull { it.id == id }

        override suspend fun getByRole(role: FolderRole): List<Folder> =
            foldersFlow.value.filter { it.role == role }

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

    private class FakeEmbeddingRepository(
        var counts: Map<Long, Int>
    ) : EmbeddingRepository {
        override suspend fun getByImageId(imageId: Long): Embedding? = null

        override suspend fun getByImageIds(imageIds: List<Long>): List<Embedding> = emptyList()

        override suspend fun getByFolderAndModel(folderId: Long, modelName: String): List<Embedding> =
            emptyList()

        override suspend fun insert(embedding: Embedding): Long = 0L

        override suspend fun insertAll(embeddings: List<Embedding>) = Unit

        override suspend fun delete(embedding: Embedding) = Unit

        override suspend fun deleteByFolder(folderId: Long) = Unit

        override suspend fun deleteByOtherModel(modelName: String) = Unit

        override suspend fun countByFolderAndModel(folderId: Long, modelName: String): Int =
            counts[folderId] ?: 0
    }

    private class FakeSettingsRepository(
        manualMode: Boolean
    ) : SettingsRepository {
        override val threshold: Flow<Float> = flowOf(0.55f)
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
