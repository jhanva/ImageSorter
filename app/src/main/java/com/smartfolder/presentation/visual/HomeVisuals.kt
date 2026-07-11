package com.smartfolder.presentation.visual

import com.smartfolder.presentation.screens.home.HomeUiState

enum class HomeHeroStage {
    SETUP,
    INDEXING,
    READY
}

data class HomeHeroContent(
    val stage: HomeHeroStage,
    val completedSteps: Int,
    val totalSteps: Int,
    val progress: Float,
    val isReady: Boolean
)

object HomeVisuals {

    fun buildHeroContent(state: HomeUiState): HomeHeroContent {
        val completedSteps = buildList {
            add(state.destinationFolders.isNotEmpty())
            add(state.destinationFolders.any { it.imageCount > 0 && it.indexedCount >= it.imageCount })
            add(state.sourceFolders.isNotEmpty())
            add(state.sourceFolders.any { it.imageCount > 0 && it.indexedCount >= it.imageCount })
        }.count { it }
        val totalSteps = 4
        val progress = completedSteps.toFloat() / totalSteps.toFloat()

        val stage = when {
            state.isIndexingDestinations || state.isIndexingSources -> HomeHeroStage.INDEXING
            state.canAnalyze -> HomeHeroStage.READY
            else -> HomeHeroStage.SETUP
        }

        return HomeHeroContent(
            stage = stage,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            progress = if (stage == HomeHeroStage.READY) 1f else progress,
            isReady = stage == HomeHeroStage.READY
        )
    }
}
