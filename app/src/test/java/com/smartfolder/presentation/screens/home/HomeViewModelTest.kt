package com.smartfolder.presentation.screens.home

import android.net.TestUri
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.usecase.SelectFolderUseCase
import com.smartfolder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var selectFolderUseCase: SelectFolderUseCase
    private lateinit var folderRepository: FolderRepository
    private lateinit var mediaStoreFolderProvider: MediaStoreFolderProvider
    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())

    @Before
    fun setup() {
        selectFolderUseCase = mock(SelectFolderUseCase::class.java)
        folderRepository = mock(FolderRepository::class.java)
        mediaStoreFolderProvider = mock(MediaStoreFolderProvider::class.java)
        `when`(mediaStoreFolderProvider.getImageFolders()).thenReturn(emptyList())
        `when`(folderRepository.observeAll()).thenReturn(foldersFlow)
    }

    private fun viewModel() = HomeViewModel(
        selectFolderUseCase = selectFolderUseCase,
        folderRepository = folderRepository,
        mediaStoreFolderProvider = mediaStoreFolderProvider
    )

    private fun folder(id: Long, role: FolderRole) = Folder(
        id = id,
        uri = TestUri("content://tree/$id"),
        displayName = "Folder $id",
        role = role
    )

    private suspend fun awaitState(
        vm: HomeViewModel,
        predicate: (HomeUiState) -> Boolean
    ): HomeUiState {
        repeat(100) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            val state = vm.uiState.value
            if (predicate(state)) return state
            Thread.sleep(10)
        }
        throw AssertionError("Timed out. Current: ${vm.uiState.value}")
    }

    @Test
    fun `groups folders by role and sorts by id`() = runTest(mainDispatcherRule.dispatcher) {
        foldersFlow.value = listOf(
            folder(3L, FolderRole.SOURCE),
            folder(1L, FolderRole.DESTINATION),
            folder(2L, FolderRole.DESTINATION)
        )

        val vm = viewModel()
        val state = awaitState(vm) { it.destinationFolders.isNotEmpty() }

        assertEquals(listOf(1L, 2L), state.destinationFolders.map { it.id })
        assertEquals(listOf(3L), state.sourceFolders.map { it.id })
    }

    @Test
    fun `cannot start triage without both folder roles`() = runTest(mainDispatcherRule.dispatcher) {
        foldersFlow.value = listOf(folder(1L, FolderRole.DESTINATION))

        val vm = viewModel()
        val state = awaitState(vm) { it.destinationFolders.isNotEmpty() }

        assertFalse(state.canStartTriage)
    }

    @Test
    fun `can start triage with at least one destination and one source`() = runTest(mainDispatcherRule.dispatcher) {
        foldersFlow.value = listOf(
            folder(1L, FolderRole.DESTINATION),
            folder(2L, FolderRole.SOURCE)
        )

        val vm = viewModel()
        val state = awaitState(vm) { it.canStartTriage }

        assertTrue(state.canStartTriage)
    }
}
