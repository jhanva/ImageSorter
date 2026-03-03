package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.SafImageFile
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.TransactionRunner
import com.smartfolder.ml.BitmapLoader
import com.smartfolder.ml.ImageEmbedderWrapper
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class IndexFolderUseCaseTest {

    private lateinit var folderRepository: FolderRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var safManager: SafManager
    private lateinit var bitmapLoader: BitmapLoader
    private lateinit var imageEmbedder: ImageEmbedderWrapper
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: IndexFolderUseCase

    private val mockUri = mock(Uri::class.java)

    @Before
    fun setup() {
        folderRepository = mock(FolderRepository::class.java)
        imageRepository = mock(ImageRepository::class.java)
        embeddingRepository = mock(EmbeddingRepository::class.java)
        safManager = mock(SafManager::class.java)
        bitmapLoader = mock(BitmapLoader::class.java)
        imageEmbedder = mock(ImageEmbedderWrapper::class.java)
        transactionRunner = object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        useCase = IndexFolderUseCase(
            folderRepository, imageRepository, embeddingRepository,
            safManager, bitmapLoader, imageEmbedder, transactionRunner
        )
    }

    @Test
    fun `empty folder completes immediately`() = runTest {
        val folder = Folder(1L, mockUri, "Empty", FolderRole.REFERENCE)
        `when`(safManager.hasPersistedPermission(mockUri)).thenReturn(true)
        `when`(safManager.listImageFiles(mockUri, true)).thenReturn(emptyList())

        val results = useCase(folder, ModelChoice.FAST).toList()
        val phases = results.map { it.phase }

        assertEquals(IndexingPhase.LISTING_FILES, phases.first())
        assertEquals(IndexingPhase.COMPLETE, phases.last())
        verify(safManager).listImageFiles(mockUri, true)
    }

    @Test
    fun `folder with images goes through correct phases`() = runTest {
        val folder = Folder(1L, mockUri, "Test", FolderRole.REFERENCE)
        val imageUri = mock(Uri::class.java)
        `when`(imageUri.toString()).thenReturn("content://test/image.jpg")
        val imageFiles = listOf(
            SafImageFile(imageUri, "test.jpg", 1000L, 100L, "image/jpeg")
        )

        `when`(safManager.hasPersistedPermission(mockUri)).thenReturn(true)
        `when`(safManager.listImageFiles(mockUri, true)).thenReturn(imageFiles)
        `when`(imageRepository.getByUris(listOf("content://test/image.jpg")))
            .thenReturn(emptyList())
        `when`(imageRepository.insertAll(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(listOf(1L))
        `when`(imageRepository.getByFolder(1L)).thenReturn(emptyList())

        val results = useCase(folder, ModelChoice.FAST).toList()
        val phases = results.map { it.phase }

        assertTrue(phases.contains(IndexingPhase.LISTING_FILES))
        assertTrue(phases.contains(IndexingPhase.EMBEDDING))
        verify(safManager).listImageFiles(mockUri, true)
    }

    @Test
    fun `stale images are removed from db during indexing`() = runTest {
        val folder = Folder(1L, mockUri, "Test", FolderRole.REFERENCE)

        val currentUri = mock(Uri::class.java)
        `when`(currentUri.toString()).thenReturn("content://test/image.jpg")
        val staleUri = mock(Uri::class.java)
        `when`(staleUri.toString()).thenReturn("content://test/old.jpg")

        val imageFiles = listOf(
            SafImageFile(currentUri, "image.jpg", 100L, 100L, "image/jpeg")
        )

        `when`(safManager.hasPersistedPermission(mockUri)).thenReturn(true)
        `when`(safManager.listImageFiles(mockUri, true)).thenReturn(imageFiles)

        val existingCurrent = ImageInfo(
            id = 11L,
            folderId = 1L,
            uri = currentUri,
            displayName = "image.jpg",
            contentHash = "100_100",
            sizeBytes = 100L,
            lastModified = 100L
        )
        val existingStale = ImageInfo(
            id = 10L,
            folderId = 1L,
            uri = staleUri,
            displayName = "old.jpg",
            contentHash = "100_100",
            sizeBytes = 100L,
            lastModified = 100L
        )

        `when`(imageRepository.getByFolder(1L)).thenReturn(listOf(existingCurrent, existingStale))
        `when`(imageRepository.getByUris(listOf("content://test/image.jpg")))
            .thenReturn(listOf(existingCurrent))
        `when`(embeddingRepository.getByImageIds(anyList())).thenReturn(emptyList())
        `when`(bitmapLoader.loadForEmbedding(currentUri)).thenReturn(null)
        `when`(bitmapLoader.loadForEmbedding(staleUri)).thenReturn(null)

        useCase(folder, ModelChoice.FAST).toList()

        verify(imageRepository).deleteByIds(listOf(10L))
        verify(safManager).listImageFiles(mockUri, true)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
