package com.smartfolder.presentation.screens.analysis

import android.net.Uri
import android.net.TestUri
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.AnalysisProgress
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.IndexingProgress
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.AnalyzeImagesUseCase
import com.smartfolder.domain.usecase.IndexFolderUseCase
import com.smartfolder.presentation.screens.results.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val destination = folder(1L, FolderRole.DESTINATION, "Dest")
    private val source = folder(2L, FolderRole.SOURCE, "Source")

    @Test
    fun `indexes every folder before analyzing`() = runTest(mainDispatcherRule.dispatcher) {
        val folderRepository = FakeFolderRepository(listOf(destination, source))
        val indexFolderUseCase = mock(IndexFolderUseCase::class.java)
        val analyzeImagesUseCase = mock(AnalyzeImagesUseCase::class.java)

        `when`(indexFolderUseCase.invoke(destination, ModelChoice.DEFAULT, ExecutionProfile.BALANCED))
            .thenReturn(flowOf(IndexingProgress(phase = IndexingPhase.COMPLETE, current = 1, total = 1)))
        `when`(indexFolderUseCase.invoke(source, ModelChoice.DEFAULT, ExecutionProfile.BALANCED))
            .thenReturn(flowOf(IndexingProgress(phase = IndexingPhase.COMPLETE, current = 1, total = 1)))
        `when`(
            analyzeImagesUseCase.invoke(
                listOf(destination),
                listOf(source),
                ModelChoice.DEFAULT,
                0.55f,
                5,
                ExecutionProfile.BALANCED
            )
        ).thenReturn(
            flowOf(
                AnalyzeImagesUseCase.Result(
                    suggestions = emptyList(),
                    progress = AnalysisProgress(phase = AnalysisPhase.COMPLETE, current = 1, total = 1)
                )
            )
        )

        val viewModel = AnalysisViewModel(
            analyzeImagesUseCase = analyzeImagesUseCase,
            indexFolderUseCase = indexFolderUseCase,
            folderRepository = folderRepository,
            settingsRepository = FakeSettingsRepository()
        )

        viewModel.startAnalysis()
        val state = awaitState(viewModel) { it.progress.phase == AnalysisPhase.COMPLETE }

        verify(indexFolderUseCase).invoke(destination, ModelChoice.DEFAULT, ExecutionProfile.BALANCED)
        verify(indexFolderUseCase).invoke(source, ModelChoice.DEFAULT, ExecutionProfile.BALANCED)
        assertEquals(AnalysisPhase.COMPLETE, state.progress.phase)
    }

    @Test
    fun `indexing error aborts the pipeline without analyzing`() = runTest(mainDispatcherRule.dispatcher) {
        val folderRepository = FakeFolderRepository(listOf(destination, source))
        val indexFolderUseCase = mock(IndexFolderUseCase::class.java)
        val analyzeImagesUseCase = mock(AnalyzeImagesUseCase::class.java)

        `when`(indexFolderUseCase.invoke(destination, ModelChoice.DEFAULT, ExecutionProfile.BALANCED))
            .thenReturn(
                flowOf(IndexingProgress(phase = IndexingPhase.ERROR, errorMessage = "Model missing"))
            )

        val viewModel = AnalysisViewModel(
            analyzeImagesUseCase = analyzeImagesUseCase,
            indexFolderUseCase = indexFolderUseCase,
            folderRepository = folderRepository,
            settingsRepository = FakeSettingsRepository()
        )

        viewModel.startAnalysis()
        val state = awaitState(viewModel) { it.error != null }

        assertTrue(state.error!!.contains("Model missing"))
        org.mockito.Mockito.verifyNoInteractions(analyzeImagesUseCase)
    }

    private suspend fun awaitState(
        viewModel: AnalysisViewModel,
        predicate: (AnalysisUiState) -> Boolean
    ): AnalysisUiState {
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

    private fun folder(id: Long, role: FolderRole, name: String): Folder = Folder(
        id = id,
        uri = TestUri("content://folder/$id"),
        displayName = name,
        role = role,
        imageCount = 1,
        indexedCount = 0
    )

    private class FakeFolderRepository(private val folders: List<Folder>) : FolderRepository {
        override fun observeAll(): Flow<List<Folder>> = MutableStateFlow(folders)
        override suspend fun getByRole(role: FolderRole): List<Folder> = folders.filter { it.role == role }
        override suspend fun getById(id: Long): Folder? = folders.firstOrNull { it.id == id }
        override suspend fun getByUri(uri: String): Folder? = null
        override suspend fun insert(folder: Folder): Long = 0L
        override suspend fun update(folder: Folder) = Unit
        override suspend fun delete(folder: Folder) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val threshold: Flow<Float> = flowOf(0.55f)
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
