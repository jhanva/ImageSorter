package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.repository.TransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UndoMoveUseCaseTest {

    private lateinit var safFileOps: SafFileOps
    private lateinit var imageRepository: ImageRepository
    private lateinit var folderRepository: FolderRepository
    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var useCase: UndoMoveUseCase

    private val insertedImages = mutableListOf<ImageInfo>()
    private val insertedSuggestions = mutableListOf<StoredSuggestion>()

    private val sourceFolderUri = mock(Uri::class.java)
    private val sourceFolder = Folder(
        id = 7L,
        uri = sourceFolderUri,
        displayName = "Source",
        role = FolderRole.SOURCE
    )

    @Before
    fun setup() {
        safFileOps = mock(SafFileOps::class.java)
        imageRepository = mock(ImageRepository::class.java)
        folderRepository = mock(FolderRepository::class.java)
        suggestionRepository = mock(SuggestionRepository::class.java)
        insertedImages.clear()
        insertedSuggestions.clear()
        kotlinx.coroutines.runBlocking {
            doAnswer {
                @Suppress("UNCHECKED_CAST")
                val images = it.getArgument(0) as List<ImageInfo>
                insertedImages.addAll(images)
                images.map { image -> image.id }
            }.`when`(imageRepository).insertAll(anyList())
            doAnswer {
                @Suppress("UNCHECKED_CAST")
                insertedSuggestions.addAll(it.getArgument(0) as List<StoredSuggestion>)
                Unit
            }.`when`(suggestionRepository).insertAll(anyList())
        }
        val transactionRunner = object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        useCase = UndoMoveUseCase(
            safFileOps = safFileOps,
            imageRepository = imageRepository,
            folderRepository = folderRepository,
            suggestionRepository = suggestionRepository,
            transactionRunner = transactionRunner
        )
    }

    @Test
    fun `restores moved file to its original folder and reinserts rows`() = runTest {
        val originalUri = mock(Uri::class.java)
        val movedUri = mock(Uri::class.java)
        val restoredUri = mock(Uri::class.java)
        val image = ImageInfo(5L, sourceFolder.id, originalUri, "img.png", "hash", 100L, 10L)
        val entry = MoveImagesUseCase.MovedEntry(image = image, newUri = movedUri)
        val suggestionSnapshot = storedSuggestion(imageId = 5L)

        `when`(folderRepository.getById(sourceFolder.id)).thenReturn(sourceFolder)
        `when`(safFileOps.moveFile(movedUri, sourceFolderUri, "img.png"))
            .thenReturn(MoveResult.Moved(restoredUri))

        val report = useCase(
            UndoMoveUseCase.UndoBatch(
                entries = listOf(entry),
                suggestions = listOf(suggestionSnapshot)
            )
        )

        assertEquals(1, report.restored)
        assertEquals(0, report.failed)
        assertEquals(listOf(image.copy(uri = restoredUri)), insertedImages)
        assertEquals(listOf(suggestionSnapshot), insertedSuggestions)
    }

    @Test
    fun `failed restore is reported and nothing is reinserted for that image`() = runTest {
        val movedUri = mock(Uri::class.java)
        val image = ImageInfo(5L, sourceFolder.id, mock(Uri::class.java), "img.png", "hash", 100L, 10L)
        val entry = MoveImagesUseCase.MovedEntry(image = image, newUri = movedUri)

        `when`(folderRepository.getById(sourceFolder.id)).thenReturn(sourceFolder)
        `when`(safFileOps.moveFile(movedUri, sourceFolderUri, "img.png"))
            .thenReturn(MoveResult.Failure("gone"))

        val report = useCase(
            UndoMoveUseCase.UndoBatch(
                entries = listOf(entry),
                suggestions = listOf(storedSuggestion(imageId = 5L))
            )
        )

        assertEquals(0, report.restored)
        assertEquals(1, report.failed)
        assertEquals(1, report.errors.size)
        assertTrue(insertedImages.isEmpty())
        assertTrue(insertedSuggestions.isEmpty())
    }

    @Test
    fun `missing original folder is reported as failure`() = runTest {
        val movedUri = mock(Uri::class.java)
        val image = ImageInfo(5L, 99L, mock(Uri::class.java), "img.png", "hash", 100L, 10L)
        val entry = MoveImagesUseCase.MovedEntry(image = image, newUri = movedUri)

        `when`(folderRepository.getById(99L)).thenReturn(null)

        val report = useCase(
            UndoMoveUseCase.UndoBatch(entries = listOf(entry), suggestions = emptyList())
        )

        assertEquals(0, report.restored)
        assertEquals(1, report.failed)
    }

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
}
