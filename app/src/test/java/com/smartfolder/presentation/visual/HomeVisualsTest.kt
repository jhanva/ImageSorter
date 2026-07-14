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
    fun `stage is setup when folders are missing`() {
        val state = HomeUiState()

        val hero = HomeVisuals.buildHeroContent(state)

        assertEquals(HomeHeroStage.SETUP, hero.stage)
        assertEquals(0f, hero.progress, 0.0001f)
        assertFalse(hero.isReady)
    }

    @Test
    fun `stage is ready when both folder groups exist`() {
        val state = HomeUiState(
            destinationFolders = listOf(folder(id = 1, role = FolderRole.DESTINATION)),
            sourceFolders = listOf(folder(id = 2, role = FolderRole.SOURCE)),
            canStartTriage = true
        )

        val hero = HomeVisuals.buildHeroContent(state)

        assertEquals(HomeHeroStage.READY, hero.stage)
        assertEquals(1f, hero.progress, 0.0001f)
        assertTrue(hero.isReady)
    }

    @Test
    fun `progress counts each selected folder group`() {
        val state = HomeUiState(
            destinationFolders = listOf(folder(id = 1, role = FolderRole.DESTINATION))
        )

        val hero = HomeVisuals.buildHeroContent(state)

        assertEquals(1, hero.completedSteps)
        assertEquals(2, hero.totalSteps)
        assertEquals(0.5f, hero.progress, 0.0001f)
    }

    private fun folder(
        id: Long,
        role: FolderRole
    ): Folder {
        return Folder(
            id = id,
            uri = TestUri("content://folder/$id"),
            displayName = "Folder $id",
            role = role
        )
    }
}
