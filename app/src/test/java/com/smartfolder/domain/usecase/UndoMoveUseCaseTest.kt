package com.smartfolder.domain.usecase

import android.net.TestUri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.ImageInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UndoMoveUseCaseTest {

    private lateinit var safFileOps: SafFileOps
    private lateinit var useCase: UndoMoveUseCase

    private val sourceFolderUri = TestUri("content://tree/source")

    @Before
    fun setup() {
        safFileOps = mock(SafFileOps::class.java)
        useCase = UndoMoveUseCase(safFileOps)
    }

    private fun entry(id: Long, name: String): UndoMoveUseCase.UndoEntry {
        val image = ImageInfo(id, 1L, TestUri("content://src/$id"), name, "h$id", 100L, 100L)
        return UndoMoveUseCase.UndoEntry(
            entry = MoveImagesUseCase.MovedEntry(image, TestUri("content://dest/$id")),
            originalFolderUri = sourceFolderUri
        )
    }

    @Test
    fun `restores moved file back to its original folder`() = runTest {
        val undo = entry(1L, "a.jpg")
        val restoredUri = TestUri("content://src/restored/1")
        `when`(safFileOps.moveFile(undo.entry.newUri, sourceFolderUri, "a.jpg"))
            .thenReturn(MoveResult.Moved(restoredUri))

        val report = useCase(listOf(undo))

        assertEquals(1, report.restored)
        assertEquals(0, report.failed)
        assertEquals(restoredUri, report.restoredUris[1L])
    }

    @Test
    fun `failed restore is reported`() = runTest {
        val undo = entry(1L, "a.jpg")
        `when`(safFileOps.moveFile(undo.entry.newUri, sourceFolderUri, "a.jpg"))
            .thenReturn(MoveResult.Failure("gone"))

        val report = useCase(listOf(undo))

        assertEquals(0, report.restored)
        assertEquals(1, report.failed)
        assertEquals(listOf("a.jpg: gone"), report.errors)
    }

    @Test
    fun `copy-only restore counts as restored with warning`() = runTest {
        val undo = entry(1L, "a.jpg")
        val restoredUri = TestUri("content://src/restored/1")
        `when`(safFileOps.moveFile(undo.entry.newUri, sourceFolderUri, "a.jpg"))
            .thenReturn(MoveResult.CopiedOnly(restoredUri, "no delete"))

        val report = useCase(listOf(undo))

        assertEquals(1, report.restored)
        assertEquals(restoredUri, report.restoredUris[1L])
        assertEquals(1, report.errors.size)
    }
}
