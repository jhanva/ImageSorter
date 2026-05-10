package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SuggestionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AnalyzeImagesUseCaseTest {

    private lateinit var imageRepository: ImageRepository
    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var useCase: AnalyzeImagesUseCase
    private var capturedSuggestions: List<StoredSuggestion>? = null

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
        suggestionRepository = mock(SuggestionRepository::class.java)
        runBlocking {
            doAnswer { Unit }.`when`(suggestionRepository).deleteAll()
            doAnswer {
                @Suppress("UNCHECKED_CAST")
                capturedSuggestions = it.getArgument(0) as List<StoredSuggestion>
                Unit
            }.`when`(suggestionRepository).replaceAll(anyList())
            `when`(imageRepository.getByIds(emptyList())).thenReturn(emptyList())
        }
        useCase = AnalyzeImagesUseCase(imageRepository, embeddingRepository, suggestionRepository)
    }

    @Test
    fun `empty reference embeddings returns error`() = runTest {
        `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
            .thenReturn(emptyList())

        val results = useCase(refFolder, unsortedFolder, ModelChoice.FAST, 0.8f).toList()
        val lastResult = results.last()

        assertEquals(AnalysisPhase.ERROR, lastResult.progress.phase)
        assertTrue(lastResult.suggestions.isEmpty())
        verify(suggestionRepository).deleteAll()
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
        `when`(imageRepository.getByIds(listOf(2L, 1L))).thenReturn(listOf(unsortedImage, refImage))

        val results = useCase(refFolder, unsortedFolder, ModelChoice.FAST, 0.5f).toList()
        val lastResult = results.last()

        assertEquals(AnalysisPhase.COMPLETE, lastResult.progress.phase)
        assertEquals(1, lastResult.suggestions.size)
        assertEquals(1f, lastResult.suggestions[0].score, 0.01f)
        assertEquals(1, capturedSuggestions?.size)
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

    @Test
    fun `requires support from multiple references for anime style matches`() = runTest {
        val refOne = Embedding(1L, 1L, normalized(1f, 0f, 0f), ModelChoice.FAST.modelFileName)
        val refTwo = Embedding(2L, 2L, normalized(1f, 1f, 0f), ModelChoice.FAST.modelFileName)
        val refThree = Embedding(3L, 3L, normalized(1f, 0f, 1f), ModelChoice.FAST.modelFileName)
        val supportedCandidate = Embedding(
            4L,
            4L,
            normalized(2.4142f, 0.7071f, 0.7071f),
            ModelChoice.FAST.modelFileName
        )
        val oneOffCandidate = Embedding(5L, 5L, normalized(1f, 0f, 0f), ModelChoice.FAST.modelFileName)

        val refImageOne = ImageInfo(1L, 1L, mock(Uri::class.java), "rei-a.png", "hash-1", 1000L, 100L)
        val refImageTwo = ImageInfo(2L, 1L, mock(Uri::class.java), "rei-b.png", "hash-2", 1000L, 100L)
        val refImageThree = ImageInfo(3L, 1L, mock(Uri::class.java), "rei-c.png", "hash-3", 1000L, 100L)
        val supportedImage = ImageInfo(4L, 2L, mock(Uri::class.java), "candidate-supported.png", "hash-4", 1000L, 100L)
        val oneOffImage = ImageInfo(5L, 2L, mock(Uri::class.java), "candidate-one-off.png", "hash-5", 1000L, 100L)

        runBlocking {
            `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
                .thenReturn(listOf(refOne, refTwo, refThree))
            `when`(embeddingRepository.getByFolderAndModel(2L, ModelChoice.FAST.modelFileName))
                .thenReturn(listOf(supportedCandidate, oneOffCandidate))
            doAnswer {
                val ids = it.getArgument<List<Long>>(0)
                listOf(refImageOne, refImageTwo, refImageThree, supportedImage, oneOffImage)
                    .filter { image -> image.id in ids }
            }.`when`(imageRepository).getByIds(anyList())
        }

        val results = useCase(
            referenceFolder = refFolder,
            unsortedFolder = unsortedFolder,
            modelChoice = ModelChoice.FAST,
            threshold = 0.87f,
            topK = 3
        ).toList()
        val lastResult = results.last()

        assertEquals(AnalysisPhase.COMPLETE, lastResult.progress.phase)
        assertEquals(1, lastResult.suggestions.size)
        assertEquals(4L, lastResult.suggestions.single().image.id)
    }

    private fun normalized(vararg values: Float): FloatArray {
        val norm = kotlin.math.sqrt(values.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(values.size) { index -> values[index] / norm }
    }
}
