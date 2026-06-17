package com.smartfolder.presentation.visual

import android.net.TestUri
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.presentation.screens.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVisualsTest {

    @Test
    fun buildHeroContent_returnsSetupState_whenFoldersAreMissing() {
        val state = HomeUiState()

        val hero = HomeVisuals.buildHeroContent(state)

        assertEquals("Build your offline sorter", hero.title)
        assertEquals("Choose destination and source folders, then index them once for local matching.", hero.subtitle)
        assertEquals("Choose folders", hero.primaryActionLabel)
        assertEquals("0 of 4 setup steps complete", hero.progressLabel)
        assertEquals(0f, hero.progress, 0.0001f)
        assertFalse(hero.isReady)
    }

    @Test
    fun buildHeroContent_returnsReadyState_whenAllFoldersAreIndexed() {
        val state = HomeUiState(
            destinationFolders = listOf(folder(id = 1, role = FolderRole.DESTINATION, imageCount = 20, indexedCount = 20)),
            sourceFolders = listOf(folder(id = 2, role = FolderRole.SOURCE, imageCount = 14, indexedCount = 14)),
            canAnalyze = true
        )

        val hero = HomeVisuals.buildHeroContent(state)

        assertEquals("Library ready for routing", hero.title)
        assertEquals("Everything is indexed locally. Run analysis to route your source images into the right destinations.", hero.subtitle)
        assertEquals("Analyze library", hero.primaryActionLabel)
        assertEquals("4 of 4 setup steps complete", hero.progressLabel)
        assertEquals(1f, hero.progress, 0.0001f)
        assertTrue(hero.isReady)
    }

    @Test
    fun buildHeroContent_returnsIndexingState_whenWorkIsInProgress() {
        val state = HomeUiState(
            destinationFolders = listOf(folder(id = 1, role = FolderRole.DESTINATION, imageCount = 20, indexedCount = 20)),
            sourceFolders = listOf(folder(id = 2, role = FolderRole.SOURCE, imageCount = 14, indexedCount = 3)),
            isIndexingSources = true
        )

        val hero = HomeVisuals.buildHeroContent(state)

        assertEquals("Indexing your folders", hero.title)
        assertEquals("Keep the app open while ImageSorter builds local embeddings for the selected library.", hero.subtitle)
        assertEquals("Indexing in progress", hero.primaryActionLabel)
        assertEquals("3 of 4 setup steps complete", hero.progressLabel)
        assertEquals(0.75f, hero.progress, 0.0001f)
        assertFalse(hero.isReady)
    }

    private fun folder(
        id: Long,
        role: FolderRole,
        imageCount: Int,
        indexedCount: Int
    ): Folder {
        return Folder(
            id = id,
            uri = TestUri("content://folder/$id"),
            displayName = "Folder $id",
            role = role,
            imageCount = imageCount,
            indexedCount = indexedCount
        )
    }
}
