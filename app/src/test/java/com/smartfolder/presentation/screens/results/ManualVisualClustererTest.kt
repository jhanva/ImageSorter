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

        val clusterMap = ManualVisualClusterer.clusterSuggestions(suggestions, embeddings)

        assertEquals(clusterMap[1L], clusterMap[2L])
        assertTrue(clusterMap[3L] == null)
    }

    private fun suggestion(id: Long, name: String): SuggestionItem {
        return SuggestionItem(
            image = ImageInfo(
                id = id,
                folderId = 1L,
                uri = mock(Uri::class.java),
                displayName = name,
                contentHash = "hash-$id",
                sizeBytes = 100L,
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
