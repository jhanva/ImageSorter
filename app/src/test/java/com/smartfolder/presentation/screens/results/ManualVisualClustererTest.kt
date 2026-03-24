package com.smartfolder.presentation.screens.results

import android.net.Uri
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.SuggestionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ManualVisualClustererTest {

    @Test
    fun `clusters highly similar embeddings together`() {
        val suggestions = listOf(
            suggestion(1L, "a.png"),
            suggestion(2L, "b.png"),
            suggestion(3L, "c.png")
        )
        val embeddings = listOf(
            embedding(1L, floatArrayOf(1f, 0f, 0f)),
            embedding(2L, floatArrayOf(0.99f, 0.01f, 0f)),
            embedding(3L, floatArrayOf(0f, 1f, 0f))
        ).associateBy { it.imageId }

        val clusterResult = ManualVisualClusterer.clusterSuggestions(suggestions, embeddings)

        assertEquals(clusterResult.visualGroupKeys[1L], clusterResult.visualGroupKeys[2L])
        assertTrue(clusterResult.visualGroupKeys[3L] == null)
    }

    @Test
    fun `groups duplicates when content hash matches`() {
        val suggestions = listOf(
            suggestion(1L, "a.png", contentHash = "same-hash", sizeBytes = 1000L),
            suggestion(2L, "a-copy.png", contentHash = "same-hash", sizeBytes = 1000L),
            suggestion(3L, "c.png", contentHash = "other-hash", sizeBytes = 2000L)
        )
        val embeddings = listOf(
            embedding(1L, floatArrayOf(1f, 0f, 0f)),
            embedding(2L, floatArrayOf(0f, 1f, 0f)),
            embedding(3L, floatArrayOf(0f, 0f, 1f))
        ).associateBy { it.imageId }

        val clusterResult = ManualVisualClusterer.clusterSuggestions(suggestions, embeddings)

        assertEquals(clusterResult.duplicateGroupKeys[1L], clusterResult.duplicateGroupKeys[2L])
        assertTrue(clusterResult.duplicateGroupKeys[3L] == null)
    }

    private fun suggestion(
        id: Long,
        name: String,
        contentHash: String = "hash-$id",
        sizeBytes: Long = 100L
    ): SuggestionItem {
        return SuggestionItem(
            image = ImageInfo(
                id = id,
                folderId = 1L,
                uri = mock(Uri::class.java),
                displayName = name,
                contentHash = contentHash,
                sizeBytes = sizeBytes,
                lastModified = 100L
            ),
            score = 1f,
            centroidScore = 1f,
            topKScore = 1f,
            topSimilarFromA = emptyList()
        )
    }

    private fun embedding(imageId: Long, vector: FloatArray): Embedding {
        return Embedding(
            imageId = imageId,
            vector = vector,
            modelName = "model.tflite"
        )
    }
}
