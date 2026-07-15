package com.smartfolder.presentation.screens.trash

import android.net.TestUri
import androidx.lifecycle.SavedStateHandle
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.usecase.DeleteTrashImageUseCase
import com.smartfolder.domain.usecase.ListTrashImagesUseCase
import com.smartfolder.domain.usecase.RestoreFromTrashUseCase
import com.smartfolder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var folderRepository: FolderRepository
    private lateinit var listTrashImagesUseCase: ListTrashImagesUseCase
    private lateinit var restoreFromTrashUseCase: RestoreFromTrashUseCase
    private lateinit var deleteTrashImageUseCase: DeleteTrashImageUseCase

    private val sourceFolder = Folder(
        id = 1L,
        uri = TestUri("content://tree/source"),
        displayName = "Descargas",
        role = FolderRole.SOURCE
    )

    @Before
    fun setup() {
        folderRepository = mock(FolderRepository::class.java)
        listTrashImagesUseCase = mock(ListTrashImagesUseCase::class.java)
        restoreFromTrashUseCase = mock(RestoreFromTrashUseCase::class.java)
        deleteTrashImageUseCase = mock(DeleteTrashImageUseCase::class.java)
    }

    private fun image(id: Long) = ImageInfo(
        id = id,
        folderId = sourceFolder.id,
        uri = TestUri("content://trash/$id"),
        displayName = "img$id.jpg",
        contentHash = "",
        sizeBytes = 100L,
        lastModified = 1000L + id
    )

    private suspend fun setupLoaded(items: List<ImageInfo>) {
        `when`(folderRepository.getById(sourceFolder.id)).thenReturn(sourceFolder)
        `when`(listTrashImagesUseCase.invoke(sourceFolder)).thenReturn(items)
    }

    private fun viewModel() = TrashViewModel(
        savedStateHandle = SavedStateHandle(mapOf("folderId" to sourceFolder.id)),
        folderRepository = folderRepository,
        listTrashImagesUseCase = listTrashImagesUseCase,
        restoreFromTrashUseCase = restoreFromTrashUseCase,
        deleteTrashImageUseCase = deleteTrashImageUseCase
    )

    private suspend fun awaitState(
        vm: TrashViewModel,
        predicate: (TrashUiState) -> Boolean
    ): TrashUiState {
        repeat(100) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            val state = vm.uiState.value
            if (predicate(state)) return state
            Thread.sleep(10)
        }
        throw AssertionError("Timed out. Current: ${vm.uiState.value}")
    }

    @Test
    fun `loads trash items for the source folder`() = runTest(mainDispatcherRule.dispatcher) {
        setupLoaded(listOf(image(1L), image(2L)))

        val vm = viewModel()
        val state = awaitState(vm) { !it.isLoading }

        assertEquals(2, state.items.size)
        assertEquals("Descargas", state.sourceFolder?.displayName)
    }

    @Test
    fun `restore removes item from list`() = runTest(mainDispatcherRule.dispatcher) {
        val item = image(1L)
        setupLoaded(listOf(item, image(2L)))
        `when`(restoreFromTrashUseCase.invoke(item, sourceFolder)).thenReturn(Result.success(Unit))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.restore(item)
        val state = awaitState(vm) { it.items.size == 1 }

        assertEquals(2L, state.items.single().id)
        assertEquals(1, state.restoredCount)
    }

    @Test
    fun `delete permanently removes item from list`() = runTest(mainDispatcherRule.dispatcher) {
        val item = image(1L)
        setupLoaded(listOf(item))
        `when`(deleteTrashImageUseCase.invoke(item)).thenReturn(Result.success(Unit))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.deletePermanently(item)
        val state = awaitState(vm) { it.items.isEmpty() }

        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `empty trash deletes every item`() = runTest(mainDispatcherRule.dispatcher) {
        val first = image(1L)
        val second = image(2L)
        setupLoaded(listOf(first, second))
        `when`(deleteTrashImageUseCase.invoke(first)).thenReturn(Result.success(Unit))
        `when`(deleteTrashImageUseCase.invoke(second)).thenReturn(Result.success(Unit))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.emptyTrash()
        val state = awaitState(vm) { it.items.isEmpty() && !it.isBusy }

        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `failed restore keeps item and reports error`() = runTest(mainDispatcherRule.dispatcher) {
        val item = image(1L)
        setupLoaded(listOf(item))
        `when`(restoreFromTrashUseCase.invoke(item, sourceFolder))
            .thenReturn(Result.failure(IllegalStateException("gone")))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.restore(item)
        val state = awaitState(vm) { it.error != null }

        assertEquals(1, state.items.size)
    }
}
