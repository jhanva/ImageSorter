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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AnalyzeImagesUseCaseTest {

    private lateinit var imageRepository: ImageRepository
    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var useCase: AnalyzeImagesUseCase
    private val storedSuggestions = mutableListOf<StoredSuggestion>()

    private val destinationA = Folder(
        id = 1L,
        uri = mock(Uri::class.java),
        displayName = "Dest A",
        role = FolderRole.DESTINATION
    )

    private val destinationB = Folder(
        id = 2L,
        uri = mock(Uri::class.java),
        displayName = "Dest B",
        role = FolderRole.DESTINATION
    )

    private val sourceA = Folder(
        id = 10L,
        uri = mock(Uri::class.java),
        displayName = "Source A",
        role = FolderRole.SOURCE
    )

    private val sourceB = Folder(
        id = 11L,
        uri = mock(Uri::class.java),
        displayName = "Source B",
        role = FolderRole.SOURCE
    )

    @Before
    fun setup() {
        imageRepository = mock(ImageRepository::class.java)
        embeddingRepository = mock(EmbeddingRepository::class.java)
        suggestionRepository = mock(SuggestionRepository::class.java)
        storedSuggestions.clear()
        kotlinx.coroutines.runBlocking {
            doAnswer { Unit }.`when`(suggestionRepository).deleteAll()
            doAnswer {
                @Suppress("UNCHECKED_CAST")
                storedSuggestions.addAll(it.getArgument(0) as List<StoredSuggestion>)
                Unit
            }.`when`(suggestionRepository).insertAll(anyList())
            doAnswer {
                @Suppress("UNCHECKED_CAST")
                val newList = it.getArgument(0) as List<StoredSuggestion>
                storedSuggestions.clear()
                storedSuggestions.addAll(newList)
                Unit
            }.`when`(suggestionRepository).replaceAll(anyList())
            doAnswer { storedSuggestions.toList() }.`when`(suggestionRepository).getAll()
            `when`(imageRepository.getByIds(emptyList())).thenReturn(emptyList())
        }
        useCase = AnalyzeImagesUseCase(imageRepository, embeddingRepository, suggestionRepository)
    }

    @Test
    fun `empty destination embeddings returns error`() = runTest {
        `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
            .thenReturn(emptyList())
        `when`(embeddingRepository.getByFolderAndModel(2L, ModelChoice.FAST.modelFileName))
            .thenReturn(emptyList())

        val results = useCase(
            destinationFolders = listOf(destinationA, destinationB),
            sourceFolders = listOf(sourceA),
            modelChoice = ModelChoice.FAST,
            threshold = 0.8f
        ).toList()

        val lastResult = results.last()
        assertEquals(AnalysisPhase.ERROR, lastResult.progress.phase)
        assertTrue(lastResult.suggestions.isEmpty())
        verify(suggestionRepository, never()).deleteAll()
    }

    @Test
    fun `empty source embeddings returns error`() = runTest {
        val destinationEmbedding = Embedding(
            id = 1L,
            imageId = 101L,
            vector = normalized(1f, 0f, 0f),
            modelName = ModelChoice.FAST.modelFileName
        )
        `when`(embeddingRepository.getByFolderAndModel(1L, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(destinationEmbedding))
        `when`(embeddingRepository.countByFolderAndModel(10L, ModelChoice.FAST.modelFileName))
            .thenReturn(0)

        val results = useCase(
            destinationFolders = listOf(destinationA),
            sourceFolders = listOf(sourceA),
            modelChoice = ModelChoice.FAST,
            threshold = 0.8f
        ).toList()

        assertEquals(AnalysisPhase.ERROR, results.last().progress.phase)
    }

    @Test
    fun `missing embeddings for any selected destination folder returns error instead of partial analysis`() = runTest {
        val destinationEmbedding = Embedding(
            id = 1L,
            imageId = 101L,
            vector = normalized(1f, 0f, 0f),
            modelName = ModelChoice.FAST.modelFileName
        )
        val sourceEmbedding = Embedding(
            id = 2L,
            imageId = 301L,
            vector = normalized(1f, 0f, 0f),
            modelName = ModelChoice.FAST.modelFileName
        )

        `when`(embeddingRepository.getByFolderAndModel(destinationA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(destinationEmbedding))
        `when`(embeddingRepository.getByFolderAndModel(destinationB.id, ModelChoice.FAST.modelFileName))
            .thenReturn(emptyList())
        `when`(embeddingRepository.getByFolderAndModel(sourceA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(sourceEmbedding))

        val results = useCase(
            destinationFolders = listOf(destinationA, destinationB),
            sourceFolders = listOf(sourceA),
            modelChoice = ModelChoice.FAST,
            threshold = 0.8f
        ).toList()

        assertEquals(AnalysisPhase.ERROR, results.last().progress.phase)
    }

    @Test
    fun `chooses best destination across multiple destination folders`() = runTest {
        val destinationVectorA = normalized(1f, 0f, 0f)
        val destinationVectorB = normalized(0f, 1f, 0f)
        val sourceVectorA = normalized(0.95f, 0.05f, 0f)
        val sourceVectorB = normalized(0.1f, 0.9f, 0f)

        val destinationEmbeddingA = Embedding(1L, 101L, destinationVectorA, ModelChoice.FAST.modelFileName)
        val destinationEmbeddingB = Embedding(2L, 201L, destinationVectorB, ModelChoice.FAST.modelFileName)
        val sourceEmbeddingA = Embedding(3L, 301L, sourceVectorA, ModelChoice.FAST.modelFileName)
        val sourceEmbeddingB = Embedding(4L, 302L, sourceVectorB, ModelChoice.FAST.modelFileName)

        val imageById = listOf(
            ImageInfo(101L, destinationA.id, mock(Uri::class.java), "dest-a-1.png", "hash-101", 1000L, 100L),
            ImageInfo(201L, destinationB.id, mock(Uri::class.java), "dest-b-1.png", "hash-201", 1000L, 100L),
            ImageInfo(301L, sourceA.id, mock(Uri::class.java), "source-a.png", "hash-301", 1000L, 100L),
            ImageInfo(302L, sourceB.id, mock(Uri::class.java), "source-b.png", "hash-302", 1000L, 100L)
        )

        `when`(embeddingRepository.getByFolderAndModel(destinationA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(destinationEmbeddingA))
        `when`(embeddingRepository.getByFolderAndModel(destinationB.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(destinationEmbeddingB))
        `when`(embeddingRepository.countByFolderAndModel(sourceA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(1)
        `when`(embeddingRepository.countByFolderAndModel(sourceB.id, ModelChoice.FAST.modelFileName))
            .thenReturn(1)
        `when`(embeddingRepository.getByFolderAndModel(sourceA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(sourceEmbeddingA))
        `when`(embeddingRepository.getByFolderAndModel(sourceB.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(sourceEmbeddingB))
        doAnswer {
            val ids = it.getArgument<List<Long>>(0)
            imageById.filter { image -> image.id in ids }
        }.`when`(imageRepository).getByIds(anyList())

        val results = useCase(
            destinationFolders = listOf(destinationA, destinationB),
            sourceFolders = listOf(sourceA, sourceB),
            modelChoice = ModelChoice.FAST,
            threshold = 0.5f
        ).toList()

        val lastResult = results.last()
        assertEquals(AnalysisPhase.COMPLETE, lastResult.progress.phase)
        assertEquals(2, lastResult.suggestions.size)
        assertEquals(destinationA.id, lastResult.suggestions.first { it.image.id == 301L }.suggestedDestinationId)
        assertEquals(destinationB.id, lastResult.suggestions.first { it.image.id == 302L }.suggestedDestinationId)
        verify(suggestionRepository).deleteAll()
        assertEquals(2, storedSuggestions.size)
    }

    @Test
    fun `stores second best score for ambiguity handling`() = runTest {
        val destinationVectorA = normalized(1f, 0f, 0f)
        val destinationVectorB = normalized(0.8f, 0.2f, 0f)
        val sourceVector = normalized(0.9f, 0.1f, 0f)

        `when`(embeddingRepository.getByFolderAndModel(destinationA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(Embedding(1L, 101L, destinationVectorA, ModelChoice.FAST.modelFileName)))
        `when`(embeddingRepository.getByFolderAndModel(destinationB.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(Embedding(2L, 201L, destinationVectorB, ModelChoice.FAST.modelFileName)))
        `when`(embeddingRepository.countByFolderAndModel(sourceA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(1)
        `when`(embeddingRepository.getByFolderAndModel(sourceA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(Embedding(3L, 301L, sourceVector, ModelChoice.FAST.modelFileName)))
        doAnswer {
            val ids = it.getArgument<List<Long>>(0)
            listOf(
                ImageInfo(101L, destinationA.id, mock(Uri::class.java), "dest-a.png", "hash-101", 1000L, 100L),
                ImageInfo(201L, destinationB.id, mock(Uri::class.java), "dest-b.png", "hash-201", 1000L, 100L),
                ImageInfo(301L, sourceA.id, mock(Uri::class.java), "source.png", "hash-301", 1000L, 100L)
            ).filter { image -> image.id in ids }
        }.`when`(imageRepository).getByIds(anyList())

        val results = useCase(
            destinationFolders = listOf(destinationA, destinationB),
            sourceFolders = listOf(sourceA),
            modelChoice = ModelChoice.FAST,
            threshold = 0.5f
        ).toList()

        val suggestion = results.last().suggestions.single()
        assertTrue(suggestion.secondBestScore > 0f)
        assertTrue(suggestion.score >= suggestion.secondBestScore)
    }

    @Test
    fun `keeps low confidence images as unassigned instead of dropping them`() = runTest {
        val destinationVector = normalized(1f, 0f, 0f)
        val sourceVector = normalized(0f, 1f, 0f)

        `when`(embeddingRepository.getByFolderAndModel(destinationA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(Embedding(1L, 101L, destinationVector, ModelChoice.FAST.modelFileName)))
        `when`(embeddingRepository.countByFolderAndModel(sourceA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(1)
        `when`(embeddingRepository.getByFolderAndModel(sourceA.id, ModelChoice.FAST.modelFileName))
            .thenReturn(listOf(Embedding(2L, 301L, sourceVector, ModelChoice.FAST.modelFileName)))
        doAnswer {
            val ids = it.getArgument<List<Long>>(0)
            listOf(
                ImageInfo(101L, destinationA.id, mock(Uri::class.java), "dest-a.png", "hash-101", 1000L, 100L),
                ImageInfo(301L, sourceA.id, mock(Uri::class.java), "source.png", "hash-301", 1000L, 100L)
            ).filter { image -> image.id in ids }
        }.`when`(imageRepository).getByIds(anyList())

        val results = useCase(
            destinationFolders = listOf(destinationA),
            sourceFolders = listOf(sourceA),
            modelChoice = ModelChoice.FAST,
            threshold = 0.9f
        ).toList()

        val suggestion = results.last().suggestions.single()
        assertEquals(0L, suggestion.suggestedDestinationId)
    }

    private fun normalized(vararg values: Float): FloatArray {
        val norm = kotlin.math.sqrt(values.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(values.size) { index -> values[index] / norm }
    }
}
