package com.smartfolder.presentation.screens.triage

import android.net.TestUri
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.smartfolder.data.saf.SafImageFile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.usecase.ListSourceImagesUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.domain.usecase.UndoMoveUseCase
import com.smartfolder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class TriageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var folderRepository: FolderRepository
    private lateinit var listSourceImagesUseCase: ListSourceImagesUseCase
    private lateinit var moveImagesUseCase: MoveImagesUseCase
    private lateinit var undoMoveUseCase: UndoMoveUseCase
    private lateinit var moveToTrashUseCase: com.smartfolder.domain.usecase.MoveToTrashUseCase
    private lateinit var positionStore: com.smartfolder.data.local.datastore.TriagePositionStore

    private val sourceFolder = Folder(
        id = 1L,
        uri = TestUri("content://tree/source"),
        displayName = "Descargas",
        role = FolderRole.SOURCE
    )
    private val destinationA = Folder(
        id = 100L,
        uri = TestUri("content://tree/destA"),
        displayName = "Memes",
        role = FolderRole.DESTINATION
    )
    private val destinationB = Folder(
        id = 101L,
        uri = TestUri("content://tree/destB"),
        displayName = "Familia",
        role = FolderRole.DESTINATION
    )

    @Before
    fun setup() {
        folderRepository = mock(FolderRepository::class.java)
        listSourceImagesUseCase = mock(ListSourceImagesUseCase::class.java)
        moveImagesUseCase = mock(MoveImagesUseCase::class.java)
        undoMoveUseCase = mock(UndoMoveUseCase::class.java)
        moveToTrashUseCase = mock(com.smartfolder.domain.usecase.MoveToTrashUseCase::class.java)
        positionStore = mock(com.smartfolder.data.local.datastore.TriagePositionStore::class.java)
    }

    private fun image(id: Long, name: String = "img$id.jpg") = ImageInfo(
        id = id,
        folderId = sourceFolder.id,
        uri = TestUri("content://img/$id"),
        displayName = name,
        contentHash = "",
        sizeBytes = 100L,
        lastModified = 1000L + id
    )

    private suspend fun setupHappyPath(images: List<ImageInfo>) {
        `when`(folderRepository.getById(sourceFolder.id)).thenReturn(sourceFolder)
        `when`(folderRepository.getByRole(FolderRole.DESTINATION))
            .thenReturn(listOf(destinationA, destinationB))
        `when`(listSourceImagesUseCase.invoke(sourceFolder)).thenReturn(images)
    }

    private fun viewModel() = TriageViewModel(
        savedStateHandle = SavedStateHandle(mapOf("folderId" to sourceFolder.id)),
        folderRepository = folderRepository,
        listSourceImagesUseCase = listSourceImagesUseCase,
        moveImagesUseCase = moveImagesUseCase,
        undoMoveUseCase = undoMoveUseCase,
        moveToTrashUseCase = moveToTrashUseCase,
        positionStore = positionStore
    )

    private suspend fun awaitState(
        vm: TriageViewModel,
        predicate: (TriageUiState) -> Boolean
    ): TriageUiState {
        repeat(100) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            val state = vm.uiState.value
            if (predicate(state)) return state
            Thread.sleep(10)
        }
        throw AssertionError("Timed out. Current: ${vm.uiState.value}")
    }

    private fun moveReport(image: ImageInfo, newUri: Uri) = MoveImagesUseCase.MoveReport(
        moved = 1,
        copiedOnly = 0,
        failed = 0,
        errors = emptyList(),
        movedImageIds = setOf(image.id),
        movedEntries = listOf(MoveImagesUseCase.MovedEntry(image, newUri))
    )

    @Test
    fun `loads source folder, destinations and image queue`() = runTest(mainDispatcherRule.dispatcher) {
        setupHappyPath(listOf(image(1L), image(2L)))

        val vm = viewModel()
        val state = awaitState(vm) { !it.isLoading }

        assertEquals("Descargas", state.sourceFolder?.displayName)
        assertEquals(listOf(100L, 101L), state.destinations.map { it.id })
        assertEquals(2, state.totalCount)
        assertEquals(1L, state.current?.id)
    }

    @Test
    fun `moveTo moves current image and advances`() = runTest(mainDispatcherRule.dispatcher) {
        val first = image(1L)
        val second = image(2L)
        setupHappyPath(listOf(first, second))
        val newUri = TestUri("content://destA/img1")
        `when`(moveImagesUseCase.invoke(listOf(first), destinationA.uri))
            .thenReturn(moveReport(first, newUri))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.moveTo(destinationA.id)
        val state = awaitState(vm) { it.movedCount == 1 }

        verify(moveImagesUseCase).invoke(listOf(first), destinationA.uri)
        assertEquals(2L, state.current?.id)
        assertEquals(mapOf(destinationA.id to 1), state.movedByDestination)
        assertTrue(state.canUndo)
    }

    @Test
    fun `skip advances without moving`() = runTest(mainDispatcherRule.dispatcher) {
        setupHappyPath(listOf(image(1L), image(2L)))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.skip()
        val state = awaitState(vm) { it.skippedCount == 1 }

        verify(moveImagesUseCase, org.mockito.Mockito.never()).invoke(anyList(), org.mockito.Mockito.any() ?: TestUri("x"))
        assertEquals(2L, state.current?.id)
    }

    @Test
    fun `undo after move restores image and steps back`() = runTest(mainDispatcherRule.dispatcher) {
        val first = image(1L)
        setupHappyPath(listOf(first, image(2L)))
        val newUri = TestUri("content://destA/img1")
        `when`(moveImagesUseCase.invoke(listOf(first), destinationA.uri))
            .thenReturn(moveReport(first, newUri))
        `when`(undoMoveUseCase.invoke(anyList()))
            .thenReturn(UndoMoveUseCase.UndoReport(restored = 1, failed = 0, errors = emptyList()))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }
        vm.moveTo(destinationA.id)
        awaitState(vm) { it.movedCount == 1 }

        vm.undoLast()
        val state = awaitState(vm) { it.movedCount == 0 }

        assertEquals(1L, state.current?.id)
        assertEquals(emptyMap<Long, Int>(), state.movedByDestination)
    }

    @Test
    fun `undo after skip only steps back`() = runTest(mainDispatcherRule.dispatcher) {
        setupHappyPath(listOf(image(1L), image(2L)))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }
        vm.skip()
        awaitState(vm) { it.skippedCount == 1 }

        vm.undoLast()
        val state = awaitState(vm) { it.skippedCount == 0 }

        assertEquals(1L, state.current?.id)
    }

    @Test
    fun `failed move keeps current image and reports error`() = runTest(mainDispatcherRule.dispatcher) {
        val first = image(1L)
        setupHappyPath(listOf(first))
        `when`(moveImagesUseCase.invoke(listOf(first), destinationA.uri)).thenReturn(
            MoveImagesUseCase.MoveReport(
                moved = 0,
                copiedOnly = 0,
                failed = 1,
                errors = listOf("img1.jpg: write denied"),
                movedImageIds = emptySet()
            )
        )

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.moveTo(destinationA.id)
        val state = awaitState(vm) { it.error != null }

        assertEquals(1L, state.current?.id)
        assertEquals(0, state.movedCount)
    }

    @Test
    fun `deleteCurrent moves image to trash and advances`() = runTest(mainDispatcherRule.dispatcher) {
        val first = image(1L)
        val second = image(2L)
        setupHappyPath(listOf(first, second))
        val trashUri = TestUri("content://trash/img1")
        `when`(moveToTrashUseCase.invoke(first, sourceFolder.uri))
            .thenReturn(Result.success(MoveImagesUseCase.MovedEntry(first, trashUri)))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.deleteCurrent()
        val state = awaitState(vm) { it.deletedCount == 1 }

        assertEquals(2L, state.current?.id)
        assertEquals(0, state.movedCount)
        assertTrue(state.canUndo)
    }

    @Test
    fun `undo after delete restores image and steps back`() = runTest(mainDispatcherRule.dispatcher) {
        val first = image(1L)
        setupHappyPath(listOf(first, image(2L)))
        val trashUri = TestUri("content://trash/img1")
        `when`(moveToTrashUseCase.invoke(first, sourceFolder.uri))
            .thenReturn(Result.success(MoveImagesUseCase.MovedEntry(first, trashUri)))
        `when`(undoMoveUseCase.invoke(anyList()))
            .thenReturn(UndoMoveUseCase.UndoReport(restored = 1, failed = 0, errors = emptyList()))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }
        vm.deleteCurrent()
        awaitState(vm) { it.deletedCount == 1 }

        vm.undoLast()
        val state = awaitState(vm) { it.deletedCount == 0 }

        assertEquals(1L, state.current?.id)
    }

    @Test
    fun `failed trash move keeps current image and reports error`() = runTest(mainDispatcherRule.dispatcher) {
        val first = image(1L)
        setupHappyPath(listOf(first))
        `when`(moveToTrashUseCase.invoke(first, sourceFolder.uri))
            .thenReturn(Result.failure(IllegalStateException("no trash folder")))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.deleteCurrent()
        val state = awaitState(vm) { it.error != null }

        assertEquals(1L, state.current?.id)
        assertEquals(0, state.deletedCount)
    }


    @Test
    fun `navigation moves without recording decisions`() = runTest(mainDispatcherRule.dispatcher) {
        setupHappyPath(listOf(image(1L), image(2L), image(3L)))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.navigateNext()
        var state = awaitState(vm) { it.current?.id == 2L }
        assertEquals(0, state.skippedCount)

        vm.navigatePrevious()
        state = awaitState(vm) { it.current?.id == 1L }
        assertEquals(0, state.skippedCount)
    }

    @Test
    fun `navigation is bounded by the queue`() = runTest(mainDispatcherRule.dispatcher) {
        setupHappyPath(listOf(image(1L)))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.navigatePrevious()
        assertEquals(1L, vm.uiState.value.current?.id)

        vm.navigateNext()
        val state = awaitState(vm) { true }
        assertEquals(1L, state.current?.id)
    }

    @Test
    fun `jumpTo moves to the requested position`() = runTest(mainDispatcherRule.dispatcher) {
        setupHappyPath(listOf(image(1L), image(2L), image(3L), image(4L)))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.jumpTo(2)
        val state = awaitState(vm) { it.current?.id == 3L }
        assertEquals(3L, state.current?.id)
    }

    @Test
    fun `resumes from the saved position`() = runTest(mainDispatcherRule.dispatcher) {
        val images = listOf(image(1L), image(2L), image(3L))
        setupHappyPath(images)
        `when`(positionStore.getLastImageUri(sourceFolder.id))
            .thenReturn(images[2].uri.toString())

        val vm = viewModel()
        val state = awaitState(vm) { !it.isLoading }

        assertEquals(3L, state.current?.id)
    }

    @Test
    fun `falls back to start when saved position no longer exists`() = runTest(mainDispatcherRule.dispatcher) {
        setupHappyPath(listOf(image(1L), image(2L)))
        `when`(positionStore.getLastImageUri(sourceFolder.id))
            .thenReturn("content://img/gone")

        val vm = viewModel()
        val state = awaitState(vm) { !it.isLoading }

        assertEquals(1L, state.current?.id)
    }

    @Test
    fun `session completes when queue is exhausted`() = runTest(mainDispatcherRule.dispatcher) {
        val only = image(1L)
        setupHappyPath(listOf(only))
        `when`(moveImagesUseCase.invoke(listOf(only), destinationA.uri))
            .thenReturn(moveReport(only, TestUri("content://destA/img1")))

        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

        vm.moveTo(destinationA.id)
        val state = awaitState(vm) { it.isComplete }

        assertNull(state.current)
        assertNotNull(state.movedByDestination[destinationA.id])
    }
}
