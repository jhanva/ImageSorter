package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.ImageRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AnalyzeImagesUseCaseTest {

    private lateinit var imageRepository: ImageRepository
    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var useCase: AnalyzeImagesUseCase

    private val refFolder = Folder(
        id = 1L,
        uri = mock(Uri::class.java),
        displayName = "Reference",
        role = FolderRole.REFERENCE
    )

    private val unsortedFolder = Folder(
        id = 2L,
        uri = mock(Uri::class.java),
        displayName = "Unsorted",
        role = FolderRole.UNSORTED
    )

    @Before
    fun setup() {
        imageRepository = mock(ImageRepository::class.java)
        embeddingRepository = mock(EmbeddingRepository::class.java)
        useCase = AnalyzeImagesUseCase(imageRepository, embeddingRepository)
    }

    @Test
    fun `empty reference embeddings returns error`() = runTest {
        `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
            .thenReturn(emptyList())

        val results = useCase(refFolder, unsortedFolder, ModelChoice.FAST, 0.8f).toList()
        val lastResult = results.last()

        assertEquals(AnalysisPhase.ERROR, lastResult.progress.phase)
        assertTrue(lastResult.suggestions.isEmpty())
    }

    @Test
    fun `empty unsorted embeddings returns error`() = runTest {
        val refEmbedding = Embedding(
            id = 1L,
            imageId = 1L,
            vector = floatArrayOf(1f, 0f, 0f),
            modelName = ModelChoice.FAST.modelFileName
        )
        `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(refEmbedding))
        `when`(embeddingRepository.getByFolderAndModel(2L, ModelChoice.FAST.modelFileName))
            .thenReturn(emptyList())

        val results = useCase(refFolder, unsortedFolder, ModelChoice.FAST, 0.8f).toList()
        val lastResult = results.last()

        assertEquals(AnalysisPhase.ERROR, lastResult.progress.phase)
    }

    @Test
    fun `identical vectors produce high score suggestions`() = runTest {
        val vector = floatArrayOf(1f, 0f, 0f)
        val refEmbedding = Embedding(1L, 1L, vector, ModelChoice.FAST.modelFileName)
        val unsortedEmbedding = Embedding(2L, 2L, vector, ModelChoice.FAST.modelFileName)

        val refImage = ImageInfo(1L, 1L, mock(Uri::class.java), "ref.jpg", "hash1", 1000L, 100L)
        val unsortedImage = ImageInfo(2L, 2L, mock(Uri::class.java), "unsorted.jpg", "hash2", 1000L, 100L)

        `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(refEmbedding))
        `when`(embeddingRepository.getByFolderAndModel(2L, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(unsortedEmbedding))
        `when`(imageRepository.getById(1L)).thenReturn(refImage)
        `when`(imageRepository.getById(2L)).thenReturn(unsortedImage)

        val results = useCase(refFolder, unsortedFolder, ModelChoice.FAST, 0.5f).toList()
        val lastResult = results.last()

        assertEquals(AnalysisPhase.COMPLETE, lastResult.progress.phase)
        assertEquals(1, lastResult.suggestions.size)
        assertEquals(1f, lastResult.suggestions[0].score, 0.01f)
    }

    @Test
    fun `orthogonal vectors produce no suggestions at high threshold`() = runTest {
        val refVector = floatArrayOf(1f, 0f, 0f)
        val unsortedVector = floatArrayOf(0f, 1f, 0f)
        val refEmbedding = Embedding(1L, 1L, refVector, ModelChoice.FAST.modelFileName)
        val unsortedEmbedding = Embedding(2L, 2L, unsortedVector, ModelChoice.FAST.modelFileName)

        `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(refEmbedding))
        `when`(embeddingRepository.getByFolderAndModel(2L, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(unsortedEmbedding))

        val results = useCase(refFolder, unsortedFolder, ModelChoice.FAST, 0.8f).toList()
        val lastResult = results.last()

        assertEquals(AnalysisPhase.COMPLETE, lastResult.progress.phase)
        assertTrue(lastResult.suggestions.isEmpty())
    }
}
