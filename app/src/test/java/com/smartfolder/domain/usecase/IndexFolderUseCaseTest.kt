package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.SafImageFile
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
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
import org.mockito.Mockito.mock
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
        `when`(safManager.listImageFiles(mockUri)).thenReturn(emptyList())

        val results = useCase(folder, ModelChoice.FAST).toList()
        val phases = results.map { it.phase }

        assertEquals(IndexingPhase.LISTING_FILES, phases.first())
        assertEquals(IndexingPhase.COMPLETE, phases.last())
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
        `when`(safManager.listImageFiles(mockUri)).thenReturn(imageFiles)
        `when`(imageRepository.getByUris(listOf("content://test/image.jpg")))
            .thenReturn(emptyList())
        `when`(imageRepository.insertAll(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(listOf(1L))
        `when`(imageRepository.getByFolder(1L)).thenReturn(emptyList())

        val results = useCase(folder, ModelChoice.FAST).toList()
        val phases = results.map { it.phase }

        assertTrue(phases.contains(IndexingPhase.LISTING_FILES))
        assertTrue(phases.contains(IndexingPhase.EMBEDDING))
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
