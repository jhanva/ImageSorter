package com.smartfolder.domain.usecase

import android.net.Uri
import android.net.TestUri
import android.graphics.Bitmap
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafImageFile
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.smartfolder.ml.BitmapLoader
import com.smartfolder.ml.ImageEmbedderWrapper
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class IndexFolderUseCaseTest {

    private lateinit var folderRepository: FolderRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var mediaStoreFolderProvider: MediaStoreFolderProvider
    private lateinit var safManager: SafManager
    private lateinit var bitmapLoader: BitmapLoader
    private lateinit var imageEmbedder: ImageEmbedderWrapper
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: IndexFolderUseCase

    private val mockUri = uri("content://test/folder")

    @Before
    fun setup() {
        folderRepository = mock(FolderRepository::class.java)
        imageRepository = mock(ImageRepository::class.java)
        embeddingRepository = mock(EmbeddingRepository::class.java)
        mediaStoreFolderProvider = mock(MediaStoreFolderProvider::class.java)
        safManager = mock(SafManager::class.java)
        bitmapLoader = mock(BitmapLoader::class.java)
        imageEmbedder = mock(ImageEmbedderWrapper::class.java)
        transactionRunner = object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        useCase = IndexFolderUseCase(
            folderRepository, imageRepository, embeddingRepository,
            mediaStoreFolderProvider, safManager, bitmapLoader, imageEmbedder, transactionRunner
        )
    }

    @Test
    fun `empty folder completes immediately`() = runTest {
        val folder = Folder(1L, mockUri, "Empty", FolderRole.DESTINATION)
        `when`(safManager.listImageFiles(mockUri, true)).thenReturn(emptyList())
        `when`(imageRepository.getByFolder(1L)).thenReturn(emptyList())

        val results = useCase(folder, ModelChoice.FAST).toList()
        val phases = results.map { it.phase }

        assertEquals(IndexingPhase.LISTING_FILES, phases.first())
        assertEquals(IndexingPhase.COMPLETE, phases.last())
        verify(safManager).listImageFiles(mockUri, true)
    }

    @Test
    fun `empty folder removes stale images and resets counters`() = runTest {
        val folder = Folder(
            id = 1L,
            uri = mockUri,
            displayName = "Empty",
            role = FolderRole.DESTINATION,
            imageCount = 3,
            indexedCount = 3
        )
        val staleUri = uri("content://test/stale.jpg")
        val staleImage = ImageInfo(
            id = 10L,
            folderId = 1L,
            uri = staleUri,
            displayName = "stale.jpg",
            contentHash = "100_100",
            sizeBytes = 100L,
            lastModified = 100L
        )

        `when`(safManager.listImageFiles(mockUri, true)).thenReturn(emptyList())
        `when`(imageRepository.getByFolder(1L)).thenReturn(listOf(staleImage), emptyList())
        val capturingFolderRepository = RecordingFolderRepository()
        val localUseCase = IndexFolderUseCase(
            capturingFolderRepository,
            imageRepository,
            embeddingRepository,
            mediaStoreFolderProvider,
            safManager,
            bitmapLoader,
            imageEmbedder,
            transactionRunner
        )

        val results = localUseCase(folder, ModelChoice.FAST).toList()

        assertEquals(IndexingPhase.COMPLETE, results.last().phase)
        verify(imageRepository).deleteByIds(listOf(10L))
        assertEquals(1L, capturingFolderRepository.updatedFolder?.id)
        assertEquals(0, capturingFolderRepository.updatedFolder?.imageCount)
        assertEquals(0, capturingFolderRepository.updatedFolder?.indexedCount)
        assertTrue(capturingFolderRepository.updatedFolder?.lastIndexedAt != null)
    }

    @Test
    fun `folder with images goes through correct phases`() = runTest {
        val folder = Folder(1L, mockUri, "Test", FolderRole.DESTINATION)
        val imageUri = uri("content://test/image.jpg")
        val imageFiles = listOf(
            SafImageFile(imageUri, "test.jpg", 1000L, 100L, "image/jpeg")
        )
        val insertedImage = ImageInfo(
            id = 1L,
            folderId = 1L,
            uri = imageUri,
            displayName = "test.jpg",
            contentHash = "1000_100",
            sizeBytes = 1000L,
            lastModified = 100L
        )

        `when`(safManager.listImageFiles(mockUri, true)).thenReturn(imageFiles)
        `when`(imageRepository.getByUris(listOf("content://test/image.jpg")))
            .thenReturn(emptyList())
        `when`(imageRepository.insertAll(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(listOf(1L))
        `when`(imageRepository.getByFolder(1L)).thenReturn(emptyList(), listOf(insertedImage))
        `when`(embeddingRepository.getByImageIds(listOf(1L))).thenReturn(emptyList())
        `when`(bitmapLoader.loadForEmbedding(imageUri)).thenReturn(null)

        val results = useCase(folder, ModelChoice.FAST).toList()
        val phases = results.map { it.phase }

        assertTrue(phases.contains(IndexingPhase.LISTING_FILES))
        assertTrue(phases.contains(IndexingPhase.EMBEDDING))
        verify(safManager).listImageFiles(mockUri, true)
    }

    @Test
    fun `stale images are removed from db during indexing`() = runTest {
        val folder = Folder(1L, mockUri, "Test", FolderRole.DESTINATION)

        val currentUri = uri("content://test/image.jpg")
        val staleUri = uri("content://test/old.jpg")

        val imageFiles = listOf(
            SafImageFile(currentUri, "image.jpg", 100L, 100L, "image/jpeg")
        )

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
        `when`(embeddingRepository.getByImageIds(listOf(11L))).thenReturn(emptyList())
        `when`(bitmapLoader.loadForEmbedding(currentUri)).thenReturn(null)
        `when`(bitmapLoader.loadForEmbedding(staleUri)).thenReturn(null)

        useCase(folder, ModelChoice.FAST).toList()

        verify(imageRepository).deleteByIds(listOf(10L))
        verify(safManager).listImageFiles(mockUri, true)
    }

    @Test
    fun `indexing one model does not delete cached embeddings from another model`() = runTest {
        val folder = Folder(1L, mockUri, "Test", FolderRole.DESTINATION)
        val imageUri = uri("content://test/image.jpg")
        val bitmap = mock(Bitmap::class.java)

        val imageFiles = listOf(
            SafImageFile(imageUri, "image.jpg", 100L, 100L, "image/jpeg")
        )
        val existingImage = ImageInfo(
            id = 11L,
            folderId = 1L,
            uri = imageUri,
            displayName = "image.jpg",
            contentHash = "100_100",
            sizeBytes = 100L,
            lastModified = 100L
        )
        val preciseEmbedding = Embedding(
            id = 5L,
            imageId = 11L,
            vector = floatArrayOf(0.1f, 0.2f),
            modelName = ModelChoice.PRECISE.modelFileName
        )

        `when`(safManager.listImageFiles(mockUri, true)).thenReturn(imageFiles)
        `when`(imageRepository.getByFolder(1L)).thenReturn(listOf(existingImage), listOf(existingImage))
        `when`(imageRepository.getByUris(listOf("content://test/image.jpg")))
            .thenReturn(listOf(existingImage))
        `when`(bitmapLoader.loadForEmbedding(imageUri)).thenReturn(bitmap)
        `when`(imageEmbedder.embed(bitmap)).thenReturn(floatArrayOf(1f, 0f))
        val recordingEmbeddingRepository = RecordingEmbeddingRepository(listOf(preciseEmbedding))
        val localUseCase = IndexFolderUseCase(
            folderRepository,
            imageRepository,
            recordingEmbeddingRepository,
            mediaStoreFolderProvider,
            safManager,
            bitmapLoader,
            imageEmbedder,
            transactionRunner
        )

        localUseCase(folder, ModelChoice.FAST, ExecutionProfile.BATTERY).toList()

        assertEquals(emptyList<Embedding>(), recordingEmbeddingRepository.deletedEmbeddings)
        assertEquals(11L, recordingEmbeddingRepository.insertedEmbeddings.single().imageId)
        assertEquals(
            ModelChoice.FAST.modelFileName,
            recordingEmbeddingRepository.insertedEmbeddings.single().modelName
        )
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }

    private fun uri(value: String): Uri = TestUri(value)

    private class RecordingFolderRepository : FolderRepository {
        var updatedFolder: Folder? = null

        override fun observeAll(): Flow<List<Folder>> = emptyFlow()

        override suspend fun getById(id: Long): Folder? = null

        override suspend fun getByRole(role: FolderRole): List<Folder> = emptyList()

        override suspend fun getByUri(uri: String): Folder? = null

        override suspend fun insert(folder: Folder): Long = folder.id

        override suspend fun update(folder: Folder) {
            updatedFolder = folder
        }

        override suspend fun delete(folder: Folder) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class RecordingEmbeddingRepository(
        private val existingEmbeddings: List<Embedding>
    ) : EmbeddingRepository {
        val insertedEmbeddings = mutableListOf<Embedding>()
        val deletedEmbeddings = mutableListOf<Embedding>()

        override suspend fun getByImageId(imageId: Long): Embedding? =
            existingEmbeddings.firstOrNull { it.imageId == imageId }

        override suspend fun getByImageIds(imageIds: List<Long>): List<Embedding> =
            existingEmbeddings.filter { it.imageId in imageIds }

        override suspend fun getByFolderAndModel(folderId: Long, modelName: String): List<Embedding> =
            emptyList()

        override suspend fun insert(embedding: Embedding): Long {
            insertedEmbeddings += embedding
            return embedding.id
        }

        override suspend fun insertAll(embeddings: List<Embedding>) {
            insertedEmbeddings += embeddings
        }

        override suspend fun delete(embedding: Embedding) {
            deletedEmbeddings += embedding
        }

        override suspend fun deleteByFolder(folderId: Long) = Unit

        override suspend fun deleteByOtherModel(modelName: String) = Unit

        override suspend fun countByFolderAndModel(folderId: Long, modelName: String): Int = 0
    }
}
